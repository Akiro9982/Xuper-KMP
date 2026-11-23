package com.xuperclean.core

import com.xuperclean.core.model.NextColumnsData
import com.xuperclean.core.model.PlayTicket
import com.xuperclean.core.model.SearchData
import com.xuperclean.core.model.SlbInfo
import com.xuperclean.core.model.ActiveData
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parsing de respuestas REALES capturadas (fase 0-1) con los modelos del core. */
class ModelParsingTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `active data con portalCodeList`() {
        val raw = """{"userId":"986694098","userToken":"8f51d162-6c47-453e-8ec1-e678b49ada04","customer":"M-2","bindMail":"0","hasPay":"0","restrictedStatus":"0","userIdentity":"1","hasPwd":"0","appModel":"0","accountType":"1","activeTime":"2026-11-11 03:28:24","invitedStatus":"0","bindMobile":"0","bindGoogle":"1","authInfoList":[],"nowTime":"2026-11-17 20:19:46","getFreeAuthFlag":"yes","getFreeAuthDays":"3","hasFreeAuth":"0","vodFreeCount":0,"tips":"Membresía ha vencido. Porfa renueva a tiempo.","type":"2","remainingDays":0,"expRemainingDays":0,"availableTime":28800,"jwtToken":"eyJx","portalCodeList":[{"portalCode":"masnew","type":"1"}]}"""
        val a = json.decodeFromString(ActiveData.serializer(), raw)
        assertEquals("986694098", a.userId)
        assertEquals("masnew", a.portalCode)
        assertEquals("M-2", a.customer)
        assertEquals("0", a.hasPay)
        assertEquals(28800L, a.availableTime)
    }

    @Test
    fun `slb info con cdn_list vod y live`() {
        val raw = """{"error_code":"0","cdn_list":[{"main_addr":"http://yuwc.swzablvpm.com","spared_addr":"http://yuwc.swzablvpm.com","cdn_type":"1","tag":"vod","serial_number":3,"cdn_id":"vod_in-cdn_03","gslb_params":"a=1","url_list":[{"tag":"free","url":"sign_type=cfl&link=cf&main_addr=http://yuwc.swzablvpm.com&user_id=986694098&dev_id=4c3b596ade128c770e86d19424c6f38a&client_ip=181.119.67.34&expired=1794963759&token=8D1FCDAF672C9FD0D81383C11F68367C"}]},{"main_addr":"http://niguof.vynbszicd.com/v3/youshi/","cdn_type":"4","tag":"live","url_list":[{"tag":"free","url":"session_id=Wj9cLlVpR72a&main_addr=http://niguof.vynbszicd.com/v3/youshi/&link=cf&sign_type=cfl&expired=1794961186&tag=free&token=C913E45B1F5C2A5A706EBBD85AADE700"}]}],"hasProjection":"yes","invalidTime":"14400","expireTimeStr":"1794961186","nowTime":"2026-11-17 20:19:46","play_params":"exp=0&max_cartons_1min=2"}"""
        val s = json.decodeFromString(SlbInfo.serializer(), raw)
        assertEquals(2, s.cdnList.size)
        val vod = s.cdnList.first { it.tag == "vod" }
        assertEquals("http://yuwc.swzablvpm.com", vod.mainAddr)
        assertEquals("1794963759", com.xuperclean.core.model.CdnAuthParams(vod.urlList[0].url)["expired"])
    }

    @Test
    fun `getNextColumns con filas y assets del home VOD`() {
        val raw = """{"recommendList":[{"id":78983,"name":"Ceremonia de iniciación","columnId":78983,"assetList":[{"contentId":"6B7E1A23B4CC4B069FE9C9BC94286A93","name":"Pledge","type":"1","duration":"01:17:00","programType":"movie","alias":"Pledge","director":"Daniel Robbins","score":5.4,"updateTime":"2025-10-11 08:02:35","shelveTime":"2026-11-16 10:54:49","tags":"Horror,Mystery,Thriller","actorDisplay":"Zachery Byrd,Phillip Andre Botello,Zack Weiner","description":"Un grupo de estudiantes","moreAudio":1,"moreSubtitle":0,"contentType":"2","posterList":[{"name":"P","fileUrl":"https://x/1.png","size":"1920*1080","fileType":"poster"}]}]},{"id":76293,"name":"2026 Peliculas teatrales","assetList":[]}],"totalSize":10,"version":"9E7317904D045CBE93DFBF6438A18701"}"""
        val n = json.decodeFromString(NextColumnsData.serializer(), raw)
        assertEquals(2, n.recommendList.size)
        val pledge = n.recommendList[0].assetList[0]
        assertEquals("Pledge", pledge.name)
        assertEquals("movie", pledge.programType)
        assertEquals(5.4, pledge.score)
    }

    @Test
    fun `startPlayVOD ticket con variantes licencias y subtitulos`() {
        val raw = """{"episodeList":[{"totalMovieList":[{"movieList":[{"contentId":"17CD9D9FA32648FDAED6A92301BDBB3D","type":"1","licenseList":[{"tag":"free","license":"app_id=com.android.msandroid&tag=free&scheme=md5-01&media_code=17CD9D9FA32648FDAED6A92301BDBB3D&expired=1795034102&token=8DFC084961C4986574C9AE1EB95D2FC9"}],"encodeFormat":"h265","audioInfo":"spa,eng","terminalType":"4","originalContentId":"84675354055D4E1C9E1BD96972401DBB","videoFormat":"ts","quality":"720p"}],"quality":"720p"}],"episodeNumber":1,"programContentId":"6B7E1A23B4CC4B069FE9C9BC94286A93","subtitleList":[{"language":"es","file":[{"url":"http://vpnwqk.k9ijj2.com/public/subs/520e.srt","fileType":"srt"}]}]}],"vodFreeCount":0,"vodFreeFlag":"no","seriesFlag":"2","name":"Pledge"}"""
        val t = json.decodeFromString(PlayTicket.serializer(), raw)
        assertEquals(1, t.episodeList.size)
        val v = t.episodeList[0].variants.single()
        assertEquals("17CD9D9FA32648FDAED6A92301BDBB3D", v.contentId)
        assertEquals("h265", v.encodeFormat)
        assertEquals("720p", v.quality)
        assertTrue(t.name == "Pledge")
        assertEquals("http://vpnwqk.k9ijj2.com/public/subs/520e.srt", t.episodeList[0].subtitleList[0].file[0].url)
    }

    @Test
    fun `busqueda con grupos por programType`() {
        val raw = """{"totalSize":4,"searchItemList":[{"programType":"others","itemList":[{"type":"1","contentId":"6B7E1A23B4CC4B069FE9C9BC94286A93","name":"Pledge","programType":"movie","contentType":"2","score":5.4}]},{"programType":"teleplay","itemList":[{"type":"0","contentId":"51CC89EB48D40F56F4A8CD7571598A90","name":"Friends T10","programType":"teleplay","volumnCount":18,"updateCount":17}]}]}"""
        val s = json.decodeFromString(SearchData.serializer(), raw)
        assertEquals(2, s.searchItemList.size)
        val serie = s.searchItemList[1].itemList[0]
        assertEquals(18, serie.volumnCount)
        assertTrue(serie.toVodAsset().isSeries)
    }
}
