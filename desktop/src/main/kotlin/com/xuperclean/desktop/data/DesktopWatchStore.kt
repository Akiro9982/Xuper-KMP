package com.xuperclean.desktop.data

import com.xuperclean.core.WatchHistory
import com.xuperclean.core.WatchProgress
import java.util.prefs.Preferences

/**
 * Historial de "Continuar viendo" local del equipo (#8).
 *
 * Usa las preferencias del usuario de Java (registro en Windows): sin ficheros
 * propios ni permisos, y sobrevive a actualizaciones de la app.
 */
object DesktopWatchStore {

    private val prefs: Preferences =
        Preferences.userRoot().node("com/xuperclean/desktop")

    private const val KEY = "continue_watching"

    fun all(): List<WatchProgress> = WatchHistory.decode(prefs.get(KEY, ""))

    fun resumable(): List<WatchProgress> = WatchHistory.resumable(all())

    fun save(progress: WatchProgress) {
        val lista = WatchHistory.upsert(all(), progress)
        prefs.put(KEY, WatchHistory.encode(lista))
        runCatching { prefs.flush() }
    }

    /** Borra la entrada de la serie (por su clave) o de la película (por id). */
    fun remove(clave: String) {
        val lista = WatchHistory.remove(all(), clave)
        prefs.put(KEY, WatchHistory.encode(lista))
        runCatching { prefs.flush() }
    }

    fun find(assetId: String): WatchProgress? = WatchHistory.find(all(), assetId)

    fun findEpisode(episodeId: String): WatchProgress? = WatchHistory.findEpisode(all(), episodeId)

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
        return prefs.getInt("audio_index_$assetId", -1)
    }

    fun saveAudioIndex(assetId: String, indice: Int) {
        if (assetId.isEmpty()) return
        prefs.putInt("audio_index_$assetId", indice)
        runCatching { prefs.flush() }
    }

    /** Idioma de audio preferido (por defecto latino). */
    var audioLanguage: String
        get() = prefs.get(KEY_AUDIO, com.xuperclean.core.AudioTracks.PREFERENCIA_POR_DEFECTO)
        set(value) {
            prefs.put(KEY_AUDIO, value)
            runCatching { prefs.flush() }
        }

    // ------------------------------------------------- conexión del portal

    private const val KEY_PORTAL = "portal_profile"
    private const val KEY_IDENTITY = "portal_identity"

    /** Valores de conexión aprendidos (build aceptado, clave…), en JSON. */
    fun portalProfile(): String? = prefs.get(KEY_PORTAL, null)

    fun savePortalProfile(json: String) {
        prefs.put(KEY_PORTAL, json)
        runCatching { prefs.flush() }
    }

    fun identity(): String? = prefs.get(KEY_IDENTITY, null)

    fun saveIdentity(json: String) {
        prefs.put(KEY_IDENTITY, json)
        runCatching { prefs.flush() }
    }
}
