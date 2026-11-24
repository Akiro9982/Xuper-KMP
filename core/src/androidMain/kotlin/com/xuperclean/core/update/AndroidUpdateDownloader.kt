package com.xuperclean.core.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.xuperclean.core.net.LegacyTls
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Instalador de APK para Android y Android TV.
 *
 * - Descarga en la caché privada de la app (sin permisos de almacenamiento).
 * - Verifica el SHA-256 publicado por GitHub.
 * - Verifica que el APK esté firmado con el mismo certificado que la app instalada.
 * - Abre el instalador del sistema; Android pide permiso de «apps desconocidas» una vez.
 */
public class AndroidUpdateDownloader(
    private val context: Context,
    private val authority: String = "${context.packageName}.fileprovider",
) : UpdateDownloader {

    override suspend fun download(
        update: AppUpdate,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        require(update.asset.downloadUrl.startsWith("https://github.com/")) {
            "La actualización no apunta a GitHub; se canceló por seguridad."
        }
        val carpeta = File(context.cacheDir, "updates").apply { mkdirs() }
        carpeta.listFiles()?.forEach { it.delete() }

        val destino = File(carpeta, "xuper-${update.channel.id}-${update.version.name}.apk")
        descargar(
            url = update.asset.downloadUrl,
            destino = destino,
            tamanoEsperado = update.asset.size,
            sha256Esperado = update.asset.sha256,
            onProgress = onProgress,
        )
        destino.absolutePath
    }

    override suspend fun install(update: AppUpdate, path: String): InstallResult {
        val apk = File(path)
        if (!apk.isFile) return InstallResult.Failed("El APK descargado ya no existe. Vuelve a descargarlo.")

        val paquete = try {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName
        } catch (_: Exception) {
            null
        }
        if (paquete != context.packageName) {
            apk.delete()
            return InstallResult.Failed(
                "El APK descargado no corresponde a esta app. Descarga la versión de tu canal.",
            )
        }

        val firmaOk = try {
            firmaCoincide(apk)
        } catch (_: Exception) {
            false
        }
        if (!firmaOk) {
            apk.delete()
            return InstallResult.Failed(
                "La firma del APK no coincide con la app instalada. Descarga la versión oficial del mismo canal.",
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val ajustes = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(ajustes)
            } catch (e: Exception) {
                return InstallResult.Failed(
                    "No se pudieron abrir los ajustes de instalación: ${e.message ?: "sin detalle"}",
                )
            }
            return InstallResult.NeedsPermission(
                "Permite a Xuper instalar apps desconocidas y vuelve a pulsar «Instalar».",
            )
        }

        return try {
            val uri = FileProvider.getUriForFile(context, authority, apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallResult.Started
        } catch (e: Exception) {
            InstallResult.Failed("No se pudo abrir el instalador: ${e.message ?: "sin detalle"}")
        }
    }

    private fun descargar(
        url: String,
        destino: File,
        tamanoEsperado: Long,
        sha256Esperado: String?,
        onProgress: (Long, Long) -> Unit,
    ) {
        val temporal = File(destino.parentFile, "${destino.name}.part")
        var conexion: HttpURLConnection? = null
        try {
            conexion = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", UpdateConfig.userAgent)
                setRequestProperty("Accept", "application/octet-stream")
            }
            if (conexion is javax.net.ssl.HttpsURLConnection) {
                // Mismo almacén ampliado que el resto de la app: GitHub también funciona
                // en Android antiguo, donde faltan las raíces modernas.
                conexion.sslSocketFactory = LegacyTls.config.socketFactory
            }
            if (conexion.responseCode !in 200..299) {
                throw IllegalStateException("La descarga falló (HTTP ${conexion.responseCode}).")
            }

            val total = conexion.contentLength.takeIf { it > 0 }?.toLong() ?: tamanoEsperado
            val digest = MessageDigest.getInstance("SHA-256")
            var descargado = 0L
            var ultimoAviso = 0L

            conexion.inputStream.use { entrada ->
                temporal.outputStream().use { salida ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val leidos = entrada.read(buffer)
                        if (leidos < 0) break
                        salida.write(buffer, 0, leidos)
                        digest.update(buffer, 0, leidos)
                        descargado += leidos
                        if (descargado - ultimoAviso >= 256 * 1024) {
                            ultimoAviso = descargado
                            onProgress(descargado, total)
                        }
                    }
                }
            }
            onProgress(descargado, total)

            if (sha256Esperado != null && digest.digest().toHex() != sha256Esperado.lowercase()) {
                throw IllegalStateException("El archivo descargado no coincide con el SHA-256 publicado.")
            }
            if (tamanoEsperado > 0 && descargado != tamanoEsperado) {
                throw IllegalStateException("La descarga quedó incompleta ($descargado de $tamanoEsperado bytes).")
            }
            if (!temporal.renameTo(destino)) {
                temporal.copyTo(destino, overwrite = true)
                temporal.delete()
            }
        } catch (e: Exception) {
            temporal.delete()
            throw e
        } finally {
            conexion?.disconnect()
        }
    }

    private fun firmaCoincide(apk: File): Boolean {
        val banderas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val instalada = try {
            context.packageManager.getPackageInfo(context.packageName, banderas)
        } catch (_: Exception) {
            return false
        }
        @Suppress("DEPRECATION")
        val archivo = context.packageManager.getPackageArchiveInfo(apk.absolutePath, banderas) ?: return false
        val firmaInstalada = firmas(instalada)
        val firmaArchivo = firmas(archivo)
        return firmaInstalada.isNotEmpty() && firmaInstalada == firmaArchivo
    }

    private fun firmas(info: PackageInfo): Set<String> {
        @Suppress("DEPRECATION")
        val lista = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return lista.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
}