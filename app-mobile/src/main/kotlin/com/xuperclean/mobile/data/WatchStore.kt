package com.xuperclean.mobile.data

import android.content.Context
import android.content.SharedPreferences
import com.xuperclean.core.WatchHistory
import com.xuperclean.core.WatchProgress

/**
 * Historial de "Continuar viendo" local del dispositivo (#8).
 *
 * SharedPreferences: sin base de datos ni dependencias; una sola lectura al abrir
 * el inicio y escrituras puntuales al salir del reproductor.
 */
object WatchStore {

    private const val PREFS = "xuper-watch"
    private const val KEY = "continue_watching"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    private fun leer(): List<WatchProgress> =
        WatchHistory.decode(prefs?.getString(KEY, null))

    fun resumable(): List<WatchProgress> = WatchHistory.resumable(leer())

    fun save(progress: WatchProgress) {
        val p = prefs ?: return
        val lista = WatchHistory.upsert(leer(), progress)
        p.edit().putString(KEY, WatchHistory.encode(lista)).apply()
    }

    /** Borra la entrada de la serie (por su clave) o de la película (por id). */
    fun remove(clave: String) {
        val p = prefs ?: return
        val lista = WatchHistory.remove(leer(), clave)
        p.edit().putString(KEY, WatchHistory.encode(lista)).apply()
    }

    fun find(assetId: String): WatchProgress? = WatchHistory.find(leer(), assetId)

    fun findEpisode(episodeId: String): WatchProgress? =
        WatchHistory.findEpisode(leer(), episodeId)

    // ------------------------------------------------- preferencias de reproducción

    private const val KEY_AUDIO = "audio_language"

    /**
     * Pista de audio elegida a mano para un título (-1 = automática).
     *
     * Hace falta porque el contenedor puede traer dos pistas `spa` (latino y
     * castellano) sin nada que las distinga: solo la posición de la pista permite
     * recordar cuál suena como el usuario quiere.
     */
    fun audioIndex(assetId: String): Int {
        if (assetId.isEmpty()) return -1
        return prefs?.getInt("audio_index_$assetId", -1) ?: -1
    }

    fun saveAudioIndex(assetId: String, indice: Int) {
        if (assetId.isEmpty()) return
        prefs?.edit()?.putInt("audio_index_$assetId", indice)?.apply()
    }

    /** Idioma de audio preferido (por defecto latino). */
    var audioLanguage: String
        get() = prefs?.getString(KEY_AUDIO, com.xuperclean.core.AudioTracks.PREFERENCIA_POR_DEFECTO)
            ?: com.xuperclean.core.AudioTracks.PREFERENCIA_POR_DEFECTO
        set(value) {
            prefs?.edit()?.putString(KEY_AUDIO, value)?.apply()
        }

    // ------------------------------------------------- conexión del portal

    private const val KEY_PORTAL = "portal_profile"
private const val KEY_IDENTITY = "portal_identity"

    /** Valores de conexión aprendidos (build aceptado, clave…), en JSON. */
    fun portalProfile(): String? = prefs?.getString(KEY_PORTAL, null)

    fun savePortalProfile(json: String) {
        prefs?.edit()?.putString(KEY_PORTAL, json)?.apply()
    }

    fun identity(): String? = prefs?.getString(KEY_IDENTITY, null)

    fun saveIdentity(json: String) {
        prefs?.edit()?.putString(KEY_IDENTITY, json)?.apply()
    }
}
