package com.xuperclean.core

import com.xuperclean.core.model.Poster
import com.xuperclean.core.model.VodAsset
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Progreso de reproducción local (por dispositivo, nunca se sube al portal).
 *
 * Para series se guarda el episodio concreto: `assetId` es la serie y `contentId`
 * el episodio, de forma que "Continuar viendo" reanuda el capítulo exacto (#8).
 */
@Serializable
public data class WatchProgress(
    /** Id del episodio en series; id del título en películas. */
    val contentId: String,
    /** Id de la ficha que se abre (serie o película). */
    val assetId: String,
    val name: String,
    /** "1" película, "0" serie. */
    val type: String,
    val isSeries: Boolean,
    val posterUrl: String? = null,
    val episodeId: String? = null,
    val episodeNumber: Int? = null,
    val episodeName: String? = null,
    /** Temporada (de `SeriesGrouping.seasonNumber`), solo en series. */
    val seasonNumber: Int? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    public val fraction: Double
        get() = if (durationMs <= 0) 0.0 else positionMs.toDouble() / durationMs

    /** Etiqueta corta para «Continuar viendo»: «T2 E5» o null en películas. */
    public val episodeLabel: String?
        get() = when {
            !isSeries -> null
            episodeNumber == null -> null
            seasonNumber == null -> "E$episodeNumber"
            else -> "T$seasonNumber E$episodeNumber"
        }

    /** Minutos restantes redondeados, para el subtítulo de la tarjeta. */
    public val remainingMinutes: Int
        get() = ((durationMs - positionMs).coerceAtLeast(0L) / 60_000L).toInt()

    /** Ficha mínima para abrir el detalle desde "Continuar viendo". */
    public fun toVodAsset(): VodAsset = VodAsset(
        contentId = assetId,
        name = name,
        type = type,
        programType = if (isSeries) "teleplay" else "movie",
        posterList = posterUrl?.let { listOf(Poster(fileUrl = it, fileType = "poster")) }.orEmpty(),
    )

    public companion object {
        /** Por debajo de 30 s o al 95 % ya no merece la pena reanudar. */
        public fun isResumable(positionMs: Long, durationMs: Long): Boolean {
            if (durationMs <= 0) return false
            val f = positionMs.toDouble() / durationMs
            return positionMs >= 30_000 && f < 0.95
        }
    }
}

/** Codificación y fusión del historial local. */
public object WatchHistory {

    public const val MAX_ITEMS: Int = 24

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializador = ListSerializer(WatchProgress.serializer())

    public fun encode(items: List<WatchProgress>): String = json.encodeToString(serializador, items)

    public fun decode(raw: String?): List<WatchProgress> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializador, raw) }.getOrDefault(emptyList())
    }

    /** Inserta o reemplaza por `assetId`, ordena por fecha y recorta a [MAX_ITEMS]. */
    public fun upsert(
        existing: List<WatchProgress>,
        item: WatchProgress,
        max: Int = MAX_ITEMS,
    ): List<WatchProgress> =
        (existing.filterNot { it.assetId == item.assetId } + item)
            .sortedByDescending { it.updatedAt }
            .take(max)

    /** Solo lo que tiene sentido ofrecer al usuario. */
    public fun resumable(items: List<WatchProgress>): List<WatchProgress> =
        items.filter { WatchProgress.isResumable(it.positionMs, it.durationMs) }
            .sortedByDescending { it.updatedAt }

    /** Progreso de una ficha (serie o película), si existe. */
    public fun find(items: List<WatchProgress>, assetId: String): WatchProgress? =
        items.firstOrNull { it.assetId == assetId }

    /** Progreso de un episodio concreto. */
    public fun findEpisode(items: List<WatchProgress>, episodeId: String): WatchProgress? =
        items.firstOrNull { it.contentId == episodeId }
}
