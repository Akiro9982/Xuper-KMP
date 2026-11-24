package com.xuperclean.mobile.diag

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * Diagnóstico de reproducción que también funciona en compilaciones release.
 *
 * Se activa con ficheros en la carpeta externa privada de la app
 * (/sdcard/Android/data/com.xuperclean.mobile/files):
 *
 *   diag -> traza detallada por segundo (posición, búfer, estado, decodificador, red).
 *   sw   -> fuerza decodificadores software (descarta los del fabricante).
 *
 * Sin esos ficheros no registra nada: la compilación de producción se comporta igual.
 */
object Diag {
    const val TAG = "XuperPlayer"

    @Volatile
    var verboso: Boolean = false
        private set

    @Volatile
    var forzarSoftware: Boolean = false
        private set

    /** Se relee como mucho cada 3 s: la pantalla se recompone con frecuencia. */
    private var ultimaLectura = 0L
    private var ultimoEstado = ""

    fun init(context: Context) {
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

    fun i(mensaje: String) {
        Log.i(TAG, mensaje)
    }

    fun w(mensaje: String, error: Throwable? = null) {
        Log.w(TAG, mensaje, error)
    }

    fun v(mensaje: String) {
        if (verboso) Log.i(TAG, mensaje)
    }

    /**
     * Vuelca las pilas de los hilos implicados en decodificar/pintar cuando la imagen
     * se detiene. Un hilo bloqueado en nativo delata al decodificador; uno esperando
     * datos delata a la red.
     */
    fun volcarHilos(motivo: String) {
        if (!verboso) return
        val interesantes = listOf("ExoPlayer", "Codec", "codec", "Surface", "Render", "main", "Vulkan", "c2")
        val pilas = Thread.getAllStackTraces().entries
            .filter { entrada -> interesantes.any { entrada.key.name.contains(it, ignoreCase = true) } }
            .joinToString("\n") { entrada ->
                val hilo = entrada.key
                buildString {
                    append("«").append(hilo.name).append("» ").append(hilo.state)
                    entrada.value.take(24).forEach { linea -> append("\n    ").append(linea) }
                }
            }
        var resto = pilas
        var parte = 0
        while (resto.isNotEmpty()) {
            val trozo = resto.take(2500)
            Log.w(TAG, "PILAS $motivo (parte ${parte++})\n$trozo")
            resto = resto.drop(trozo.length)
        }
    }
}