package com.xuperclean.mobile.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferencias de reproducción que sobreviven al cierre de la app.
 *
 * «Modo de vídeo» elige qué decodificadores puede usar el reproductor. En Automático
 * se usa el del fabricante (rápido y con menos batería). Si el aparato se cuelga con
 * ciertos capítulos, la app cambia sola a Software y guarda la decisión aquí; el
 * usuario también puede fijarla a mano desde los ajustes del reproductor.
 */
object PlaybackPrefs {

    enum class ModoVideo {
        /** Decodificador del fabricante si existe; es el más eficiente. */
        AUTO,
        /** Solo decodificadores con aceleración por hardware. */
        HARDWARE,
        /** Solo decodificadores software (los de Android): más lentos, sin cuelgues. */
        SOFTWARE,
    }

    private const val PREFS = "xuper-playback"
    private const val CLAVE_VIDEO = "modo_video"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    var modoVideo: ModoVideo
        get() = runCatching { ModoVideo.valueOf(prefs?.getString(CLAVE_VIDEO, null).orEmpty()) }
            .getOrDefault(ModoVideo.AUTO)
        set(valor) {
            prefs?.edit()?.putString(CLAVE_VIDEO, valor.name)?.apply()
        }
}