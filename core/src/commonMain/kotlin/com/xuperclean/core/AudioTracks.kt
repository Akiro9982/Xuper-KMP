package com.xuperclean.core

import com.xuperclean.core.model.MovieVariant

/**
 * Pista de audio disponible en un título VOD.
 *
 * El portal no publica pistas separadas: `MovieVariant.audioInfo` es una lista ISO de los
 * idiomas que trae el mismo `_media.ts` (`spa,eng`, `spa,spa,eng`, `eng,lat`). Aquí se
 * deduplica y se traduce a una etiqueta legible; el reproductor elige la pista real.
 */
public data class AudioTrack(
    /** Código canónico (`spa`, `lat`, `eng`…). */
    val code: String,
    /** Nombre para la UI («Latino», «Español», «Inglés»…). */
    val label: String,
)

/**
 * Idiomas de audio del VOD.
 *
 * `lat` (latino) se distingue de `spa` (español de España): el contenedor suele etiquetar
 * las dos como `spa`, así que «latino» se resuelve prefiriendo la primera pista `spa`
 * (verificado con `ffprobe`, iptv-api.md §15.9).
 */
public object AudioTracks {

    /** Código de audio latino que usa el portal. */
    public const val LATINO: String = "lat"

    /** Código de audio español de España. */
    public const val ESPANOL: String = "spa"

    /** Preferencia guardada por defecto (pedido del usuario). */
    public const val PREFERENCIA_POR_DEFECTO: String = LATINO

    private val Nombres: Map<String, String> = mapOf(
        "lat" to "Latino",
        "spa" to "Español",
        "eng" to "Inglés",
        "por" to "Portugués",
        "fra" to "Francés",
        "deu" to "Alemán",
        "ita" to "Italiano",
        "jpn" to "Japonés",
        "kor" to "Coreano",
        "zho" to "Chino",
        "hin" to "Hindi",
        "rus" to "Ruso",
        "ara" to "Árabe",
        "tur" to "Turco",
        "pol" to "Polaco",
        "nld" to "Neerlandés",
        "swe" to "Sueco",
    )

    private val Alias: Map<String, String> = mapOf(
        "es" to ESPANOL,
        "es-es" to ESPANOL,
        "es-mx" to LATINO,
        "es-419" to LATINO,
        "en" to "eng",
        "pt" to "por",
        "pt-br" to "por",
        "fr" to "fra",
        "de" to "deu",
        "it" to "ita",
        "ja" to "jpn",
        "ko" to "kor",
        "zh" to "zho",
        "hi" to "hin",
        "ru" to "rus",
        "ar" to "ara",
    )

    /** Código canónico en minúsculas: `es-MX` → `lat`, `en` → `eng`, `SPA` → `spa`. */
    public fun normalize(code: String?): String? {
        val limpio = code?.trim()?.lowercase()?.replace('_', '-') ?: return null
        if (limpio.isEmpty()) return null
        return Alias[limpio] ?: limpio
    }

    /** Nombre legible de un código; el código tal cual si no se conoce. */
    public fun label(code: String?): String {
        val canon = normalize(code) ?: return "Desconocido"
        return Nombres[canon] ?: code!!.trim()
    }

    /** Idiomas deduplicados de un `audioInfo` (`spa,spa,eng` → Español, Inglés). */
    public fun from(audioInfo: String?): List<AudioTrack> {
        if (audioInfo.isNullOrBlank()) return emptyList()
        return audioInfo.split(',', ';', '/', '|')
            .mapNotNull(::normalize)
            .distinct()
            .map { AudioTrack(code = it, label = label(it)) }
    }

    /** Idiomas deduplicados de todas las variantes de un episodio/ticket. */
    public fun fromVariants(variants: List<MovieVariant>): List<AudioTrack> =
        variants
            .asSequence()
            .flatMap { (it.audioInfo ?: "").split(',', ';', '/', '|').asSequence() }
            .mapNotNull(::normalize)
            .distinct()
            .map { AudioTrack(code = it, label = label(it)) }
            .toList()

    /**
     * Cadena de códigos que el reproductor debe aceptar para una preferencia.
     *
     * El contenedor etiqueta el latino como `spa`, así que la preferencia «latino» acepta
     * `lat` y luego `spa`. Sin preferencia se usa la primera pista del título.
     */
    public fun preferenceChain(preferred: String?): List<String> = when (normalize(preferred)) {
        null, LATINO -> listOf("lat", "spa")
        ESPANOL -> listOf("spa", "es")
        "eng" -> listOf("eng", "en")
        "por" -> listOf("por", "pt")
        else -> listOf(normalize(preferred)!!)
    }

    /**
     * Elige la pista que corresponde a [preferred] entre las disponibles, con vuelta a la
     * primera del título si no hay coincidencia. Null si el título no declara idiomas.
     */
    public fun pick(available: List<AudioTrack>, preferred: String?): AudioTrack? {
        if (available.isEmpty()) return null
        val cadena = preferenceChain(preferred)
        return cadena.firstNotNullOfOrNull { buscado ->
            available.firstOrNull { it.code == buscado }
        } ?: available.first()
    }

    /** Elige dentro de la lista siguiendo un orden de preferencias del usuario. */
    public fun pickOf(available: List<AudioTrack>, preferences: List<String>): AudioTrack? {
        if (available.isEmpty()) return null
        for (pref in preferences) {
            val canon = normalize(pref) ?: continue
            available.firstOrNull { it.code == canon }?.let { return it }
        }
        return available.first()
    }

    /** ¿Merece la pena mostrar el selector? Solo con dos o más idiomas distintos. */
    public fun hasChoice(available: List<AudioTrack>): Boolean = available.size > 1
}
