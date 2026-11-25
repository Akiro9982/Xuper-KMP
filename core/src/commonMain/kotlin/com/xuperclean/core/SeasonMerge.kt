package com.xuperclean.core

import com.xuperclean.core.model.EpisodeInfo
import com.xuperclean.core.model.VodAsset

/**
 * Copia de una temporada tal como la publica el portal.
 *
 * El portal no tiene un modelo «serie → temporadas → episodios»: cada temporada es un
 * asset y la MISMA temporada puede estar publicada varias veces (una con el título
 * original y todas las pistas de audio, otra con el título doblado y un subconjunto de
 * episodios y de idiomas). Esta clase reúne lo que hace falta para fusionarlas.
 */
public data class SeasonCopy(
    val asset: VodAsset,
    /** `audioInfo` que declara el portal para la temporada (`eng,spa`, `por,spa,eng`…). */
    val audioInfo: String?,
    val episodes: List<EpisodeInfo>,
)

/** Episodio de una temporada junto con el asset del portal al que pertenece. */
public data class SeasonEpisode(
    val episode: EpisodeInfo,
    /** Temporada que publica este episodio: se usa como `seriesContentId` al reproducir. */
    val season: VodAsset,
)

/**
 * Fusión de las copias de una misma temporada en una sola lista de episodios.
 *
 * El portal publica la misma temporada dos veces con distinto nombre y distinto doblaje:
 * la copia «original» suele traer más idiomas (y las dos pistas en español) pero le faltan
 * episodios; la copia doblada trae la temporada completa pero recortada de audio. Ninguna
 * de las dos es la buena por sí sola, así que se combinan:
 *
 *  - **un episodio por número** (`seriesNumber`), tomado de la copia con más audio;
 *  - los números que solo publica una copia se conservan, para no perder episodios.
 *
 * Con eso la ficha muestra una sola temporada, con todos los episodios y, en cada uno,
 * todas las pistas de audio que existen en el portal.
 */
public object SeasonMerge {

    /** Códigos ISO de audio de un `audioInfo`, en orden y sin deduplicar. */
    public fun codes(audioInfo: String?): List<String> {
        if (audioInfo.isNullOrBlank()) return emptyList()
        return audioInfo.split(',', ';', '/', '|').mapNotNull(AudioTracks::normalize)
    }

    /**
     * Ordena las copias de la temporada de mejor a peor audio: primero la que declara más
     * pistas en español (latino y de España), después la que trae más idiomas distintos y,
     * por último, la que publica más episodios. El orden de llegada desempata.
     */
    public fun rank(copies: List<SeasonCopy>): List<SeasonCopy> {
        fun espanoles(copia: SeasonCopy): Int = codes(copia.audioInfo)
            .count { it == AudioTracks.ESPANOL || it == AudioTracks.LATINO }
        return copies.sortedWith(
            compareByDescending<SeasonCopy> { espanoles(it) }
                .thenByDescending { codes(it.audioInfo).distinct().size }
                .thenByDescending { it.episodes.size },
        )
    }

    /**
     * Una sola lista de episodios a partir de todas las copias de la temporada.
     *
     * Se recorre primero la copia con mejor audio: si dos copias publican el mismo número,
     * se queda su episodio. Los episodios sin número (`seriesNumber` nulo) se colocan por
     * su posición dentro de la copia.
     */
    public fun merge(copies: List<SeasonCopy>): List<SeasonEpisode> {
        val ordenadas = rank(copies.filter { it.episodes.isNotEmpty() })
        if (ordenadas.isEmpty()) return emptyList()

        val porNumero = LinkedHashMap<Int, SeasonEpisode>()
        for (copia in ordenadas) {
            copia.episodes.forEachIndexed { indice, episodio ->
                val numero = episodio.seriesNumber?.takeIf { it > 0 } ?: (indice + 1)
                porNumero.getOrPut(numero) { SeasonEpisode(episode = episodio, season = copia.asset) }
            }
        }
        return porNumero.entries.sortedBy { it.key }.map { it.value }
    }
}
