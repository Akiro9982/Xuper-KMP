package com.xuperclean.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ------------------------------------------------------------------ VOD

@Serializable
public data class VodAsset(
    @SerialName("contentId") val contentId: String,
    val name: String = "",
    /** "1" = película, "0" = serie/teleplay (campo `type` de los ítems de búsqueda). */
    val type: String? = null,
    val alias: String? = null,
    @SerialName("contentType") val contentType: String? = null,
    @SerialName("programType") val programType: String? = null,
    val duration: String? = null,
    val tags: String? = null,
    val description: String? = null,
    val director: String? = null,
    val actorDisplay: String? = null,
    val score: Double? = null,
    val updateTime: String? = null,
    val shelveTime: String? = null,
    val originalCountry: String? = null,
    val releaseTime: String? = null,
    val volumnCount: Int? = null,
    val updateCount: Int? = null,
    val viewPoint: String? = null,
    val moreAudio: Int? = null,
    val moreSubtitle: Int? = null,
    val restricted: String? = null,
    val keyWords: String? = null,
    @SerialName("posterList") val posterList: List<Poster> = emptyList(),
) {
    public val isSeries: Boolean
        get() = programType == "teleplay" || type == "0"
}

@Serializable
public data class EpisodeInfo(
    @SerialName("contentId") val contentId: String,
    val name: String? = null,
    @SerialName("seriesNumber") val seriesNumber: Int? = null,
    val quality: String? = null,
    val duration: String? = null,
    @SerialName("hasPositive") val hasPositive: String? = null,
    @SerialName("hasTrailer") val hasTrailer: String? = null,
    @SerialName("hasTidbits") val hasTidbits: String? = null,
    @SerialName("viewPoint") val viewPoint: String? = null,
    @SerialName("posterList") val posterList: List<Poster> = emptyList(),
)

@Serializable
public data class AssetData(
    @SerialName("contentId") val contentId: String,
    val name: String = "",
    val type: String? = null,
    val alias: String? = null,
    val contentType: String? = null,
    val programType: String? = null,
    val duration: String? = null,
    val tags: String? = null,
    val description: String? = null,
    val director: String? = null,
    val actorDisplay: String? = null,
    val score: Double? = null,
    val originalCountry: String? = null,
    val releaseTime: String? = null,
    val volumnCount: Int? = null,
    val updateCount: Int? = null,
    val viewPoint: String? = null,
    val audioInfo: String? = null,
    val subsInfo: String? = null,
    val restricted: String? = null,
    val keyWords: String? = null,
    val language: String? = null,
    @SerialName("posterList") val posterList: List<Poster> = emptyList(),
    @SerialName("simpleProgramList") val simpleProgramList: List<EpisodeInfo> = emptyList(),
    @SerialName("sameSeasonSeriesList") val sameSeasonSeriesList: List<EpisodeInfo> = emptyList(),
) {
    public fun toVodAsset(): VodAsset = VodAsset(
        contentId = contentId, name = name, type = type, alias = alias, contentType = contentType,
        programType = programType, duration = duration, tags = tags, description = description,
        director = director, actorDisplay = actorDisplay, score = score,
        originalCountry = originalCountry, releaseTime = releaseTime,
        volumnCount = volumnCount, updateCount = updateCount, viewPoint = viewPoint,
        moreAudio = null, moreSubtitle = null, restricted = restricted, keyWords = keyWords,
        posterList = posterList,
    )
}

// ------------------------------------------------------------------ reproducción

@Serializable
public data class LicenseEntry(
    val tag: String? = null,
    val license: String,
)

@Serializable
public data class MovieVariant(
    @SerialName("contentId") val contentId: String,
    val type: String? = null,
    @SerialName("licenseList") val licenseList: List<LicenseEntry> = emptyList(),
    @SerialName("encodeFormat") val encodeFormat: String? = null,
    @SerialName("audioInfo") val audioInfo: String? = null,
    @SerialName("videoFormat") val videoFormat: String? = null,
    val quality: String? = null,
    @SerialName("terminalType") val terminalType: String? = null,
    @SerialName("originalContentId") val originalContentId: String? = null,
)

@Serializable
public data class QualityGroup(
    val quality: String? = null,
    @SerialName("movieList") val movieList: List<MovieVariant> = emptyList(),
)

@Serializable
public data class SubFile(
    val url: String,
    val fileType: String? = null,
)

@Serializable
public data class Subtitle(
    val language: String? = null,
    val file: List<SubFile> = emptyList(),
)

@Serializable
public data class EpisodeTicket(
    @SerialName("episodeNumber") val episodeNumber: Int? = null,
    @SerialName("programContentId") val programContentId: String? = null,
    @SerialName("totalMovieList") val totalMovieList: List<QualityGroup> = emptyList(),
    @SerialName("subtitleList") val subtitleList: List<Subtitle> = emptyList(),
) {
    /** Todas las variantes de vídeo del episodio (todas las calidades). */
    public val variants: List<MovieVariant>
        get() = totalMovieList.flatMap { it.movieList }
}

@Serializable
public data class PlayTicket(
    @SerialName("episodeList") val episodeList: List<EpisodeTicket> = emptyList(),
    val name: String? = null,
    @SerialName("vodFreeCount") val vodFreeCount: Int? = null,
    @SerialName("vodFreeFlag") val vodFreeFlag: String? = null,
    @SerialName("seriesFlag") val seriesFlag: String? = null,
)

// ------------------------------------------------------------------ columnas / home VOD

@Serializable
public data class ColumnInfo(
    val id: Long,
    @SerialName("parentId") val parentId: Long? = null,
    val name: String? = null,
    val type: String? = null,
    val alias: String? = null,
    val code: String? = null,
    val free: String? = null,
    @SerialName("specialFlag") val specialFlag: String? = null,
    val sequence: Int? = null,
    val style: String? = null,
    @SerialName("posterList") val posterList: List<Poster> = emptyList(),
)

@Serializable
public data class ColumnContents(
    @SerialName("totalSize") val totalSize: Int = 0,
    @SerialName("childColumnList") val childColumnList: List<ColumnInfo> = emptyList(),
    @SerialName("contentList") val contentList: List<VodAsset> = emptyList(),
    @SerialName("expireTimeStr") val expireTimeStr: String? = null,
)

@Serializable
public data class ColumnRow(
    val id: Long,
    val name: String? = null,
    val alias: String? = null,
    val columnId: Long? = null,
    val type: String? = null,
    val free: String? = null,
    val code: String? = null,
    @SerialName("assetList") val assetList: List<VodAsset> = emptyList(),
    @SerialName("posterList") val posterList: List<Poster> = emptyList(),
)

@Serializable
public data class NextColumnsData(
    @SerialName("recommendList") val recommendList: List<ColumnRow> = emptyList(),
    val version: String? = null,
    @SerialName("totalSize") val totalSize: Int? = null,
)

// ------------------------------------------------------------------ búsqueda

@Serializable
public data class SearchItem(
    val contentId: String,
    val name: String = "",
    val type: String? = null,
    val programType: String? = null,
    val duration: String? = null,
    val tags: String? = null,
    val description: String? = null,
    val director: String? = null,
    val volumnCount: Int? = null,
    val updateCount: Int? = null,
    val updateTime: String? = null,
    val actorDisplay: String? = null,
    val originalCountry: String? = null,
    val releaseTime: String? = null,
    val moreAudio: Int? = null,
    val moreSubtitle: Int? = null,
    val contentType: String? = null,
    val alias: String? = null,
    val score: Double? = null,
    val viewPoint: String? = null,
    @SerialName("posterList") val posterList: List<Poster> = emptyList(),
) {
    public fun toVodAsset(): VodAsset = VodAsset(
        contentId = contentId, name = name, type = type, alias = alias, contentType = contentType,
        programType = programType, duration = duration, tags = tags, description = description,
        director = director, actorDisplay = actorDisplay, score = score,
        updateTime = updateTime, originalCountry = originalCountry, releaseTime = releaseTime,
        volumnCount = volumnCount, updateCount = updateCount, viewPoint = viewPoint,
        moreAudio = moreAudio, moreSubtitle = moreSubtitle, posterList = posterList,
    )
}

@Serializable
public data class SearchGroup(
    @SerialName("programType") val programType: String? = null,
    @SerialName("itemList") val itemList: List<SearchItem> = emptyList(),
)

@Serializable
public data class SearchData(
    @SerialName("totalSize") val totalSize: Int = 0,
    @SerialName("searchItemList") val searchItemList: List<SearchGroup> = emptyList(),
)
