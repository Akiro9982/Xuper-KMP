package com.xuperclean.mobile.diag

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnóstico de reproducción que funciona también en compilaciones release.
 *
 * Las últimas líneas viven siempre en memoria: cuando la imagen se congela en un
 * móvil sin cable a mano, el usuario puede copiar el informe desde el propio
 * reproductor y enviarlo tal cual, sin conectarse a ningún equipo.
 *
 * Ficheros de la carpeta externa privada de la app
 * (/sdcard/Android/data/<paquete>/files):
 *
 *   diag -> traza detallada por segundo también en logcat.
 *   sw   -> fuerza decodificadores software (descarta los del fabricante).
 *
 * Sin esos ficheros no se escribe en logcat, pero el historial en memoria sí se
 * mantiene: el informe siempre está disponible.
 */
object Diag {
    const val TAG = "XuperPlayer"

    /** Unas diez horas de eventos, o ~8 minutos de traza por segundo. */
    private const val MAX_LINEAS = 600

    @Volatile
    var verboso: Boolean = false
        private set

    @Volatile
    var forzarSoftware: Boolean = false
        private set

    /** Se relee como mucho cada 3 s: la pantalla se recompone con frecuencia. */
    private var ultimaLectura = 0L
    private var ultimoEstado = ""
    private var aplicacion: Context? = null
    private val historial = ArrayDeque<String>()
    private val reloj = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun init(context: Context) {
        aplicacion = context.applicationContext
        val ahora = SystemClock.elapsedRealtime()
        if (ahora - ultimaLectura < 3_000) return
        ultimaLectura = ahora
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        verboso = File(dir, "diag").exists()
        forzarSoftware = File(dir, "sw").exists()
        val estado = "verboso=$verboso, software=$forzarSoftware"
        if (estado != ultimoEstado) {
            ultimoEstado = estado
            Log.i(TAG, "Diagnóstico activo ($estado) en $dir")
        }
    }

    /** Evento relevante: se guarda en el informe y se escribe en logcat. */
    fun i(mensaje: String) {
        registrar(mensaje)
        Log.i(TAG, mensaje)
    }

    fun w(mensaje: String, error: Throwable? = null) {
        registrar("⚠ $mensaje")
        Log.w(TAG, mensaje, error)
    }

    /** Traza por segundo: entra en el informe siempre; en logcat, solo con «diag». */
    fun v(mensaje: String) {
        registrar(mensaje)
        if (verboso) Log.i(TAG, mensaje)
    }

    private fun registrar(mensaje: String) {
        val marca = synchronized(reloj) { reloj.format(Date()) }
        val t = SystemClock.elapsedRealtime() / 1000
        synchronized(historial) {
            historial.addLast("$marca (${t}s) $mensaje")
            while (historial.size > MAX_LINEAS) historial.removeFirst()
        }
    }

    /** Informe completo listo para copiar o compartir. */
    fun informe(encabezado: String): String = buildString {
        appendLine("Xuper · informe de reproducción")
        val equipo = listOf(
            "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        ).joinToString(" · ")
        appendLine("Dispositivo: $equipo")
        appendLine("App: ${version()}")
        appendLine(encabezado)
        val copia = synchronized(historial) { historial.toList() }
        appendLine("--- traza (${copia.size} líneas) ---")
        copia.forEach { appendLine(it) }
    }

    private fun version(): String {
        val ctx = aplicacion ?: return "?"
        return runCatching {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        }.getOrDefault("?")
    }

    /** Deja el informe en disco para poder recuperarlo aunque la app se cierre. */
    fun guardar(texto: String) {
        val ctx = aplicacion ?: return
        runCatching {
            File(ctx.filesDir, "informe-reproduccion.txt").writeText(texto)
        }
        runCatching {
            ctx.getExternalFilesDir(null)?.let { dir ->
                File(dir, "informe-reproduccion.txt").writeText(texto)
            }
        }
    }

    /**
     * Vuelca las pilas de los hilos implicados en decodificar/pintar cuando la imagen
     * se detiene. Un hilo bloqueado en nativo delata al decodificador; uno esperando
     * datos delata a la red.
     */
    fun volcarHilos(motivo: String) {
        val interesantes = listOf("ExoPlayer", "Codec", "codec", "Surface", "Render", "main", "c2")
        val pilas = Thread.getAllStackTraces().entries
            .filter { entrada -> interesantes.any { entrada.key.name.contains(it, ignoreCase = true) } }
            .joinToString("\n") { entrada ->
                val hilo = entrada.key
                buildString {
                    append("«").append(hilo.name).append("» ").append(hilo.state)
                    entrada.value.take(20).forEach { linea -> append("\n    ").append(linea) }
                }
            }
        var resto = pilas
        var parte = 0
        while (resto.isNotEmpty()) {
            val trozo = resto.take(2000)
            Log.w(TAG, "PILAS $motivo (parte ${parte++})\n$trozo")
            resto = resto.drop(trozo.length)
        }
    }
}