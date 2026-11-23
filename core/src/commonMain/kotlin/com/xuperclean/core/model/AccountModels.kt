package com.xuperclean.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modelos del portal Xuper. Campos observados en las respuestas reales
 * (iptv-api.md §3-§6 y §14). Todo con ignoreUnknownKeys para tolerar
 * campos nuevos del backend.
 */

// ------------------------------------------------------------------ cuenta

@Serializable
public data class PortalCodeEntry(
    val portalCode: String,
    val type: String? = null,
)

@Serializable
public data class ActiveData(
    val userId: String,
    val userToken: String,
    val customer: String? = null,
    @SerialName("hasPay") val hasPay: String? = null,
    val userIdentity: String? = null,
    val activeTime: String? = null,
    val vodFreeCount: Int? = null,
    val vodFreeFlag: String? = null,
    val availableTime: Long? = null,
    val remainingDays: Int? = null,
    val tips: String? = null,
    val jwtToken: String? = null,
    @SerialName("portalCodeList") val portalCodeList: List<PortalCodeEntry> = emptyList(),
) {
    /** Portal principal de la sesión (el que debe ir en todas las llamadas posteriores). */
    public val portalCode: String
        get() = portalCodeList.firstOrNull()?.portalCode ?: ""
}

// ------------------------------------------------------------------ CDN / SLB

@Serializable
public data class CdnUrl(
    val tag: String? = null,
    val url: String,
)

@Serializable
public data class CdnEntry(
    @SerialName("main_addr") val mainAddr: String = "",
    @SerialName("spared_addr") val sparedAddr: String? = null,
    @SerialName("cdn_type") val cdnType: String? = null,
    val tag: String? = null,
    @SerialName("serial_number") val serialNumber: Int? = null,
    @SerialName("cdn_id") val cdnId: String? = null,
    @SerialName("url_list") val urlList: List<CdnUrl> = emptyList(),
    @SerialName("gslb_params") val gslbParams: String? = null,
)

@Serializable
public data class SlbInfo(
    @SerialName("cdn_list") val cdnList: List<CdnEntry> = emptyList(),
    @SerialName("play_params") val playParams: String? = null,
    @SerialName("hasProjection") val hasProjection: String? = null,
    @SerialName("invalidTime") val invalidTime: String? = null,
    @SerialName("expireTimeStr") val expireTimeStr: String? = null,
)

/** Params clave=valor de la cadena de auth de un CDN (url_list[].url). */
public class CdnAuthParams(public val raw: String) : Map<String, String> by parse(raw) {
    public companion object {
        private fun parse(raw: String): Map<String, String> =
            raw.split("&").mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
            }.toMap()
    }
}

// ------------------------------------------------------------------ live

@Serializable
public data class LiveAddress(
    @SerialName("playCode") val playCode: String,
    val license: String? = null,
    val quality: String? = null,
    @SerialName("cdnType") val cdnType: String? = null,
    val tag: String? = null,
    @SerialName("AVFormat") val avFormat: String? = null,
)

@Serializable
public data class Poster(
    val name: String? = null,
    val fileUrl: String? = null,
    val size: String? = null,
    val fileType: String? = null,
)

@Serializable
public data class Channel(
    @SerialName("channelCode") val channelCode: String,
    val name: String = "",
    @SerialName("alias") val alias: String? = null,
    @SerialName("channelNumber") val channelNumber: Int? = null,
    val quality: String? = null,
    val restricted: String? = null,
    val posterUrl: String? = null,
    @SerialName("supportVideoType") val supportVideoType: String? = null,
    @SerialName("supportBusiness") val supportBusiness: String? = null,
    val tags: String? = null,
    @SerialName("liveAddressList") val liveAddressList: List<LiveAddress> = emptyList(),
    @SerialName("posterList") val posterList: List<Poster> = emptyList(),
)

@Serializable
public data class LiveData(
    @SerialName("channelList") val channelList: List<Channel> = emptyList(),
    @SerialName("dataVersion") val dataVersion: String? = null,
    @SerialName("totalSize") val totalSize: Int? = null,
)
