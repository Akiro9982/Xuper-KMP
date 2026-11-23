package com.xuperclean.core

import com.xuperclean.core.model.CdnEntry
import com.xuperclean.core.model.CdnUrl
import com.xuperclean.core.model.Channel
import com.xuperclean.core.model.LiveAddress
import com.xuperclean.core.model.MovieVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Tests de construcción de URLs de stream (formatos verificados en iptv-api.md §6/§14). */
class StreamBuilderTest {

    private val cdnLive = CdnEntry(
        mainAddr = "http://niguof.vynbszicd.com/v3/youshi/",
        cdnType = "4",
        tag = "live",
        urlList = listOf(
            CdnUrl(tag = "free", url = "session_id=Wj9cLlVpR72a&user_id=986694098&app_ver=49902&app_id=com.android.msandroid&main_addr=http://niguof.vynbszicd.com/v3/youshi/&link=cf&sign_type=cfl&dev_id=24c6f38a&expired=1794961186&tag=free&check_play_ip=true&token=C913E45B1F5C2A5A706EBBD85AADE700"),
        ),
    )

    private val cdnVod = CdnEntry(
        mainAddr = "http://yuwc.swzablvpm.com",
        cdnType = "1",
        tag = "vod",
        urlList = listOf(
            CdnUrl(tag = "free", url = "sign_type=cfl&link=cf&ctrl_type=account&main_addr=http://yuwc.swzablvpm.com&user_id=986694098&dev_id=4c3b596ade128c770e86d19424c6f38a&client_ip=181.119.67.34&app_id=com.android.msandroid&app_ver=49902&auth_id=986694098_com.android.msandroid__0&expired=1794966524"),
        ),
    )

    @Test
    fun `url live con ruta en main_addr`() {
        // el bridge verificado usa solo el hostname; la ruta de main_addr no va en la URL
        val url = StreamBuilder.liveM3u8Url(cdnLive, "cyx_57E0DD1930DEC346A")
        assertEquals("http://niguof.vynbszicd.com/live/cyx_57E0DD1930DEC346A.m3u8", url)
    }

    @Test
    fun `url live sin ruta en main_addr`() {
        val cdn = cdnLive.copy(mainAddr = "http://aluve.rdgqkfxio.com")
        val url = StreamBuilder.liveM3u8Url(cdn, "cyx_auftv")
        assertEquals("http://aluve.rdgqkfxio.com/live/cyx_auftv.m3u8", url)
    }

    @Test
    fun `cabeceras live con Content-Auth y Content-License`() {
        val lic = "app_id=com.android.msandroid&tag=free&scheme=md5-01&media_code=cyx_x&expired=1&token=AB"
        val headers = StreamBuilder.liveHeaders(cdnLive.urlList[0].url, lic)
        assertEquals(cdnLive.urlList[0].url, headers["Content-Auth"])
        assertEquals(lic, headers["Content-License"])
        assertEquals("Ranger/4.9.4-17294ac0", headers["User-Agent"])
    }

    @Test
    fun `url vod media_ts con params de sesion`() {
        val url = StreamBuilder.vodMediaTsUrl(cdnVod, "17CD9D9FA32648FDAED6A92301BDBB3D", "XuperCleanTest01", "986694098")
        assertTrue(url.startsWith("http://yuwc.swzablvpm.com/vod/17CD9D9FA32648FDAED6A92301BDBB3D_media.ts?"))
        assertTrue(url.contains("dev_id=4c3b596ade128c770e86d19424c6f38a"))
        assertTrue(url.contains("user_id=986694098"))
        assertTrue(url.contains("trans_id=XuperCleanTest01"))
        assertTrue(url.contains("expired=1794966524"))
        assertTrue(url.contains("media_code=17CD9D9FA32648FDAED6A92301BDBB3D"))
        assertTrue(url.contains("auth_id=986694098_com.android.msandroid__0"))
        assertTrue(url.contains("client_ip=181.119.67.34"))
        // sin token/sign2: el CDN no los valida
        assertTrue(!url.contains("token="))
    }

    @Test
    fun `seleccion de variante prefiere h264 si se pide`() {
        val variants = listOf(
            MovieVariant(contentId = "A", encodeFormat = "h265", quality = "1080p"),
            MovieVariant(contentId = "B", encodeFormat = "h264", quality = "720p"),
        )
        assertEquals("A", StreamBuilder.pickVariant(variants)?.contentId)
        assertEquals("B", StreamBuilder.pickVariant(variants, preferH264 = true)?.contentId)
    }

    @Test
    fun `seleccion de cdn vod prefiere link cf`() {
        val otro = cdnVod.copy(mainAddr = "http://uefy.15jnujlk.com", urlList = listOf(
            CdnUrl(tag = "free", url = "sign_type=cs&link=icdn&main_addr=uefy.15jnujlk.com&user_id=986694098&dev_id=D&client_ip=IP&app_id=A&app_ver=49902&auth_id=U_com.android.msandroid__0&expired=1")
        ))
        assertEquals(cdnVod.mainAddr, StreamBuilder.pickVodCdn(listOf(otro, cdnVod))?.mainAddr)
    }

    @Test
    fun `canales live con el cdn sano primero y sin duplicados`() {
        val muerto = cdnLive.copy(mainAddr = "http://auuy2136a.xh09je1qy.com", cdnType = "1")
        val otro = cdnLive.copy(mainAddr = "http://aluve.rdgqkfxio.com", cdnType = "4")
        val candidatos = StreamBuilder.liveCdnCandidates(listOf(muerto, cdnLive, otro, cdnLive))
        assertEquals(3, candidatos.size)
        assertEquals("4", candidatos.first().cdnType)
        assertEquals("1", candidatos.last().cdnType)
    }

    @Test
    fun `eleccion de canal y licencia`() {
        val ch = Channel(
            channelCode = "cyx_x",
            name = "Test",
            liveAddressList = listOf(
                LiveAddress(playCode = "p1", tag = "free", license = "L1"),
                LiveAddress(playCode = "p2", tag = "pay", license = "L2"),
            ),
        )
        val addr = StreamBuilder.pickLiveAddress(ch)
        assertNotNull(addr)
        assertEquals("p1", addr.playCode)
    }
}
