package com.xuperclean.core

import com.xuperclean.core.model.CdnAuthParams
import com.xuperclean.core.model.CdnEntry
import com.xuperclean.core.model.LiveAddress
import com.xuperclean.core.model.MovieVariant

/**
 * Construcción de URLs y cabeceras de reproducción.
 *
 * LIVE (iptv-api.md §6):  http://<cdn>/live/<playCode>.m3u8 + cabeceras Content-Auth/License.
 * VOD   (iptv-api.md §14.2): http://<cdn>/vod/<playId>_media.ts?<params de sesión>
 *       — MPEG-TS progresivo con Range; token/sign2 del P2SP NO se validan.
 */
public object StreamBuilder {

    // ---------------------------------------------------------------- live

    public fun liveM3u8Url(cdn: CdnEntry, playCode: String): String {
        // SOLO el hostname: la ruta de main_addr (p. ej. /v3/youshi/) no va en la URL
        // del stream (así lo hace el bridge verificado, iptv-api.md §6)
        val stripped = cdn.mainAddr.removePrefix("http://")
        val host = stripped.substringBefore('/')
        return "http://$host/live/$playCode.m3u8"
    }

    public fun liveHeaders(cdnAuthUrl: String, license: String): Map<String, String> = mapOf(
        "App" to "com.android.msandroid",
        "App-Version" to "49902",
        "User-Agent" to "Ranger/4.9.4-17294ac0",
        "Content-Auth" to cdnAuthUrl,
        "Content-License" to license,
    )

    /**
     * Elige el mejor CDN para live: preferir cdn_type "4" (CloudFlare) con el mismo tag
     * de la variante; si no, el primero con tag "live".
     */
    public fun pickLiveCdn(cdns: List<CdnEntry>, variantTag: String? = null): CdnEntry? =
        liveCdnCandidates(cdns, variantTag).firstOrNull()

    /**
     * Todos los CDN de directo en orden de preferencia, sin duplicados.
     *
     * El portal publica un CDN `cdnType=1` que devuelve 403 y dos `cdnType=4` que
     * funcionan (verificado). Puntúa más el 4 y el que tenga el tag de la variante.
     */
    public fun liveCdnCandidates(cdns: List<CdnEntry>, variantTag: String? = null): List<CdnEntry> {
        val tag = variantTag ?: "free"
        return cdns
            .filter { it.tag == "live" && it.urlList.isNotEmpty() }
            .distinctBy { it.mainAddr }
            .sortedByDescending { cdn ->
                (if (cdn.cdnType == "4") 2 else 0) +
                    (if (cdn.urlList.any { it.tag == tag }) 1 else 0)
            }
    }

    public fun pickLiveAddress(channel: com.xuperclean.core.model.Channel): LiveAddress? =
        channel.liveAddressList.firstOrNull { it.tag == "free" }
            ?: channel.liveAddressList.firstOrNull()

    // ---------------------------------------------------------------- VOD

    /**
     * URL del stream VOD (descarga progresiva TS con Range).
     *
     * [cdn] debe ser una entrada con tag "vod" del getSlbInfo; los parámetros de sesión
     * (dev_id, user_id, expired, client_ip) salen de su url_list.
     *
     * [transId] es libre (lo genera el cliente).
     */
    public fun vodMediaTsUrl(cdn: CdnEntry, playId: String, transId: String, authUserId: String): String {
        val host = cdn.mainAddr.removePrefix("http://").trimEnd('/')
        val auth = CdnAuthParams(cdn.urlList.firstOrNull()?.url ?: "")
        return buildString {
            append("http://").append(host)
            append("/vod/").append(playId).append("_media.ts?")
            append("dev_id=").append(auth["dev_id"] ?: "")
            append("&user_id=").append(auth["user_id"] ?: "")
            append("&trans_id=").append(transId)
            append("&expired=").append(auth["expired"] ?: "")
            append("&app_id=").append(auth["app_id"] ?: "com.android.msandroid")
            append("&app_ver=").append(auth["app_ver"] ?: "49902")
            append("&host=").append(host)
            append("&media_code=").append(playId)
            append("&auth_id=").append(auth["auth_id"] ?: "${authUserId}_com.android.msandroid__0")
            append("&client_ip=").append(auth["client_ip"] ?: "")
        }
    }

    /** Cabeceras para el stream VOD: solo el User-Agent Ranger es necesario. */
    public fun vodHeaders(): Map<String, String> = mapOf(
        "User-Agent" to "Ranger/4.9.4-17294ac0",
    )

    /** Elige el mejor CDN VOD: preferir sign_type "cfl" (CloudFlare) / link "cf". */
    public fun pickVodCdn(cdns: List<CdnEntry>): CdnEntry? = vodCdnCandidates(cdns).firstOrNull()

    /** Todos los CDN VOD utilizables, sin duplicados (el primero es el preferido). */
    public fun vodCdnCandidates(cdns: List<CdnEntry>): List<CdnEntry> =
        cdns.filter { it.tag == "vod" && it.urlList.isNotEmpty() }
            .distinctBy { it.mainAddr }
            .sortedByDescending { cdn -> if (cdn.urlList.any { it.url.contains("link=cf") }) 1 else 0 }

    /** Variante de reproducción preferida: h264 antes que h265 en gama baja, mejor calidad. */
    public fun pickVariant(variants: List<MovieVariant>, preferH264: Boolean = false): MovieVariant? {
        if (variants.isEmpty()) return null
        val byQuality = variants.sortedByDescending { qualityRank(it.quality) }
        if (preferH264) {
            byQuality.firstOrNull { it.encodeFormat == "h264" }?.let { return it }
        }
        return byQuality.firstOrNull() ?: variants.first()
    }

    /** Licencia (Content-License) de una variante, preferendo tag free. */
    public fun pickLicense(variant: MovieVariant): String? =
        variant.licenseList.firstOrNull { it.tag == "free" }?.license
            ?: variant.licenseList.firstOrNull()?.license

    private fun qualityRank(q: String?): Int = when (q) {
        "1080p" -> 4
        "720p" -> 3
        "480p" -> 2
        else -> 1
    }
}
