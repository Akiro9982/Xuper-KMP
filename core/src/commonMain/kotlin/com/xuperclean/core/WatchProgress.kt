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
    /**
     * Título original normalizado (el `alias` del portal), solo en series. Identifica la
     * serie aunque cada temporada se publique con otro doblaje: «Star vs. the Forces of
     * Evil» y «Star vs. las fuerzas del mal» son la misma ficha.
     */
    val aliasKey: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    public val fraction: Double
        get() = if (durationMs <= 0) 0.0 else positionMs.toDouble() / durationMs

    /**
     * Identidad de la ficha para el historial: **una entrada por serie**, no una por
     * temporada publicada. La clave es el título base, que no cambia al pasar de `T1`
     * a `T2` ni al elegir otra copia de la misma temporada. Las películas van por id.
     */
    public val clave: String
        get() = if (isSeries) SeriesGrouping.baseName(name) else assetId

    /**
     * Repara entradas guardadas por versiones anteriores: quita `T2`/`Season 2` del
     * nombre visible y deduce la temporada del nombre del capítulo, porque el del
     * asset no siempre la lleva (`Star vs. the Forces of Evil Season 2 - 1`).
     */
    public fun normalized(): WatchProgress {
        if (!isSeries) return this
        val temporada = SeriesGrouping.seasonOrNull(name)
            ?: SeriesGrouping.seasonFromEpisode(episodeName)
            ?: seasonNumber
        return copy(
            name = SeriesGrouping.searchTitle(name),
            seasonNumber = temporada,
        )
    }

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
        return runCatching { json.decodeFromString(serializador, raw) }
            .getOrDefault(emptyList())
            .map { it.normalized() }
            .let { unicos(it) }
    }

    /**
     * Deja **una tarjeta por ficha**, la más reciente. Las versiones anteriores podían
     * guardar la misma serie dos veces (una por temporada o por doblaje) y así se
     * arregla al leer, sin esperar a volver a reproducir.
     */
    private fun unicos(items: List<WatchProgress>): List<WatchProgress> {
        val result = mutableListOf<WatchProgress>()
        items.sortedByDescending { it.updatedAt }.forEach { item ->
            if (result.none { mismaFicha(it, item) }) result += item
        }
        return result
    }

    /**
     * Inserta o reemplaza la entrada de la **misma serie** (o película), ordena por
     * fecha y recorta a [MAX_ITEMS]. Así ver el capítulo 1 de la T2 actualiza la
     * tarjeta que ya había de la T1 en lugar de crear una segunda.
     */
    public fun upsert(
        existing: List<WatchProgress>,
        item: WatchProgress,
        max: Int = MAX_ITEMS,
    ): List<WatchProgress> =
        (existing.filterNot { mismaFicha(it, item) } + item)
            .sortedByDescending { it.updatedAt }
            .take(max)

    /**
     * ¿Dos entradas son la misma ficha? Basta con que compartan el título visible o el
     * original, porque el portal publica la misma serie con y sin doblar.
     */
    private fun mismaFicha(a: WatchProgress, b: WatchProgress): Boolean {
        val clavesA = listOfNotNull(a.clave, a.aliasKey)
        val clavesB = listOfNotNull(b.clave, b.aliasKey)
        return clavesA.any { it.isNotEmpty() && it in clavesB }
    }

    /** Solo lo que tiene sentido ofrecer al usuario. */
    public fun resumable(items: List<WatchProgress>): List<WatchProgress> =
        items.filter { WatchProgress.isResumable(it.positionMs, it.durationMs) }
            .sortedByDescending { it.updatedAt }

    /** Progreso de una ficha (serie o película), si existe. */
    public fun find(items: List<WatchProgress>, assetId: String): WatchProgress? =
        items.firstOrNull { it.assetId == assetId }

    /** Quita la entrada de una serie por cualquiera de sus copias o por su clave. */
    public fun remove(items: List<WatchProgress>, clave: String): List<WatchProgress> =
        items.filterNot { it.clave == clave || it.aliasKey == clave || it.assetId == clave }

    /** Progreso de un episodio concreto. */
    public fun findEpisode(items: List<WatchProgress>, episodeId: String): WatchProgress? =
        items.firstOrNull { it.contentId == episodeId }
}
