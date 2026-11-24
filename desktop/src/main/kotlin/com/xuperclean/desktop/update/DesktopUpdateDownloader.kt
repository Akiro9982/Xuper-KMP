package com.xuperclean.desktop.update

import com.xuperclean.core.update.AppUpdate
import com.xuperclean.core.update.InstallResult
import com.xuperclean.core.update.UpdateConfig
import com.xuperclean.core.update.UpdateDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Actualizador de escritorio (MSI por usuario).
 *
 * - Descarga en %LOCALAPPDATA%\Xuper\updates (sin permisos de administrador).
 * - Verifica el SHA-256 publicado por GitHub antes de ejecutar nada.
 * - El MSI se instala con msiexec /passive en modo «por usuario».
 * - Un script .cmd espera a que Xuper cierre para no bloquear el ejecutable,
 *   instala y vuelve a abrir la app.
 */
class DesktopUpdateDownloader(
    private val updateDir: Path = carpetaPorDefecto(),
) : UpdateDownloader {

    override suspend fun download(
        update: AppUpdate,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        require(update.asset.downloadUrl.startsWith("https://github.com/")) {
            "La actualización no apunta a GitHub; se canceló por seguridad."
        }

        Files.createDirectories(updateDir)
        limpiarDescargasAnteriores()

        val destino = updateDir.resolve("xuper-desktop-${update.version.name}.msi")
        val temporal = updateDir.resolve("${destino.fileName}.part")
        Files.deleteIfExists(temporal)

        val conexion = (URI(update.asset.downloadUrl).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", UpdateConfig.userAgent)
            setRequestProperty("Accept", "application/octet-stream")
        }

        try {
            if (conexion.responseCode !in 200..299) {
                throw IllegalStateException("La descarga falló (HTTP ${conexion.responseCode}).")
            }

            val total = conexion.contentLengthLong.takeIf { it > 0 } ?: update.asset.size
            val digest = MessageDigest.getInstance("SHA-256")
            var descargado = 0L
            var ultimoAviso = 0L

            conexion.inputStream.use { entrada ->
                Files.newOutputStream(temporal).use { salida ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val leidos = entrada.read(buffer)
                        if (leidos < 0) break
                        salida.write(buffer, 0, leidos)
                        digest.update(buffer, 0, leidos)
                        descargado += leidos
                        if (descargado - ultimoAviso >= 512 * 1024) {
                            ultimoAviso = descargado
                            onProgress(descargado, total)
                        }
                    }
                }
            }
            onProgress(descargado, total)

            val shaEsperado = requireNotNull(update.asset.sha256) {
                "El release de escritorio no publica SHA-256; se canceló por seguridad."
            }
            if (digest.digest().toHex() != shaEsperado.lowercase()) {
                throw IllegalStateException("El archivo descargado no coincide con el SHA-256 publicado.")
            }
            if (update.asset.size > 0 && descargado != update.asset.size) {
                throw IllegalStateException("La descarga quedó incompleta ($descargado de ${update.asset.size} bytes).")
            }

            try {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING)
            }
            destino.toAbsolutePath().toString()
        } catch (e: Exception) {
            Files.deleteIfExists(temporal)
            throw e
        } finally {
            conexion.disconnect()
        }
    }

    override suspend fun install(update: AppUpdate, path: String): InstallResult =
        withContext(Dispatchers.IO) {
            val msi = runCatching { Paths.get(path) }.getOrNull()
                ?: return@withContext InstallResult.Failed("La ruta del MSI no es válida.")
            if (!Files.isRegularFile(msi)) {
                return@withContext InstallResult.Failed("El MSI descargado ya no existe. Vuelve a descargarlo.")
            }

            val exe = ejecutableInstalado()
                ?: return@withContext InstallResult.Failed(
                    "La instalación automática solo está disponible en Xuper instalado con el MSI. " +
                        "En modo desarrollo, descarga el MSI desde GitHub Releases.",
                )

            try {
                val script = updateDir.resolve("install-update.cmd")
                val log = updateDir.resolve("install.log")
                Files.writeString(
                    script,
                    scriptDeInstalacion(
                        pid = ProcessHandle.current().pid(),
                        msi = msi.toAbsolutePath(),
                        exe = exe.toAbsolutePath(),
                        log = log.toAbsolutePath(),
                    ),
                    StandardCharsets.UTF_8,
                )
                // /min crea una consola propia: el script sobrevive al cierre de la app
                // (con /b compartiría la consola del proceso y podría morir con él).
                val comando = "\"${script.toAbsolutePath()}\""
                ProcessBuilder("cmd.exe", "/c", "start", "", "/min", comando).start()
                InstallResult.Started
            } catch (e: Exception) {
                InstallResult.Failed("No se pudo preparar el instalador: ${e.message ?: "sin detalle"}")
            }
        }

    /** `jpackage.app-path` solo existe en la app empaquetada; en desarrollo no se instala. */
    private fun ejecutableInstalado(): Path? {
        val ruta = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() } ?: return null
        var exe = runCatching { Paths.get(ruta) }.getOrNull() ?: return null
        if (Files.isDirectory(exe)) exe = exe.resolve("Xuper.exe")
        return exe.takeIf { Files.isRegularFile(it) }
    }

    private fun limpiarDescargasAnteriores() {
        runCatching {
            Files.newDirectoryStream(updateDir).use { entradas ->
                entradas.forEach { archivo ->
                    val nombre = archivo.fileName.toString()
                    if (nombre.endsWith(".msi") || nombre.endsWith(".part")) {
                        Files.deleteIfExists(archivo)
                    }
                }
            }
        }
    }

    private fun scriptDeInstalacion(pid: Long, msi: Path, exe: Path, log: Path): String =
        listOf(
            "@echo off",
            "chcp 65001 >NUL",
            "setlocal",
            "set \"PID=$pid\"",
            "set \"MSI=$msi\"",
            "set \"EXE=$exe\"",
            "set \"LOG=$log\"",
            ":esperar",
            "tasklist /FI \"PID eq %PID%\" /NH 2>NUL | find \"%PID%\" >NUL",
            "if not errorlevel 1 (",
            "  ping -n 2 127.0.0.1 >NUL",
            "  goto esperar",
            ")",
            "ping -n 3 127.0.0.1 >NUL",
            "start \"\" /wait msiexec /i \"%MSI%\" /passive /norestart /L*v \"%LOG%\"",
            "if exist \"%EXE%\" start \"\" \"%EXE%\"",
            "del \"%~f0\" >NUL 2>&1",
        ).joinToString("\r\n", postfix = "\r\n")

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }

    companion object {
        private fun carpetaPorDefecto(): Path {
            val local = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                ?: Paths.get(System.getProperty("user.home"), "AppData", "Local").toString()
            return Paths.get(local, "Xuper", "updates")
        }
    }
}