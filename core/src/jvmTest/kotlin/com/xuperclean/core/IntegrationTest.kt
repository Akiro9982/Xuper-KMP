package com.xuperclean.core

import com.xuperclean.core.net.HttpException
import com.xuperclean.core.net.JvmHttpEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test de INTEGRACIÓN contra el servicio real (requiere red). Verifica la cadena completa:
 * live (active → slbInfo → getLiveData → m3u8) y VOD (getNextColumns → startPlayVOD → media.ts).
 *
 * Usa el sn del emulador MuMu registrado (uso personal con la propia cuenta).
 */
class IntegrationTest {

    private val config = XuperConfig(sn = "4c3b596ade128c770e86d19424c6f38a")
    private val client = XuperPortalClient(config, JvmHttpEngine())

    @Test
    fun `cadena live completa`() = runTest {
        val account = client.active()
        assertTrue(account.userId.isNotEmpty(), "active: userId vacío")
        assertEquals("masnew", account.portalCode)

        val slb = client.slbInfo(account)
        assertTrue(slb.cdnList.isNotEmpty(), "getSlbInfo: sin CDNs")

        val channels = client.liveData(account)
        assertTrue(channels.size > 500, "getLiveData: solo ${channels.size} canales")

        // construir URL de varios canales hasta dar con uno vivo (los 404 son caídas del CDN
        // de origen, no bloqueos: iptv-api.md §11)
        val cdn = StreamBuilder.pickLiveCdn(slb.cdnList)!!
        val auth = cdn.urlList.first { it.tag == "free" }.url
        val http = JvmHttpEngine()
        var ok = false
        var ultimoEstado = 0
        for (ch in channels.filter { it.liveAddressList.isNotEmpty() }.take(10)) {
            val addr = StreamBuilder.pickLiveAddress(ch)!!
            val url = StreamBuilder.liveM3u8Url(cdn, addr.playCode)
            val headers = StreamBuilder.liveHeaders(auth, addr.license ?: "")
            val r = http.get(url, headers)
            ultimoEstado = r.status
            if (r.status == 200 && r.bodyText.startsWith("#EXTM3U")) {
                ok = true
                break
            }
        }
        assertTrue(ok, "ningún canal de los 10 respondió m3u8 (último HTTP $ultimoEstado)")
    }

    @Test
    fun `cadena VOD completa`() = runTest {
        val account = client.active()

        val home = client.nextColumns(account)
        assertTrue(home.recommendList.isNotEmpty(), "getNextColumns: sin filas")
        val conContenido = home.recommendList.first { it.assetList.isNotEmpty() }
        val asset = conContenido.assetList.first()

        val ticket = client.startPlayVod(
            session = account,
            contentId = asset.contentId,
            type = asset.type ?: "1",
        )
        assertTrue(ticket.episodeList.isNotEmpty(), "startPlayVOD: sin episodios")
        val variant = StreamBuilder.pickVariant(ticket.episodeList[0].variants)!!
        val license = StreamBuilder.pickLicense(variant)
        assertTrue(!license.isNullOrEmpty(), "sin licencia en la variante")

        val cdn = StreamBuilder.pickVodCdn(client.slbInfo(account).cdnList)!!
        val url = StreamBuilder.vodMediaTsUrl(cdn, variant.contentId, "xportCoreTest01", account.userId)

        // descarga parcial: primer mega (igual que hace el player con Range)
        val http = JvmHttpEngine(readTimeoutMs = 30_000)
        val r = http.get(url, mapOf("User-Agent" to "Ranger/4.9.4-17294ac0", "Range" to "bytes=0-1879"))
        assertTrue(r.status == 206 || r.status == 200, "media.ts: HTTP ${r.status}")
        assertEquals(0x47, r.body[0].toInt() and 0xFF, "primer byte no es sync TS")
    }

    @Test
    fun `busqueda y detalle de serie`() = runTest {
        val account = client.active()
        val search = client.searchByName(account, "friends")
        val serie = search.searchItemList
            .flatMap { it.itemList }
            .firstOrNull { it.programType == "teleplay" && (it.volumnCount ?: 0) > 0 }
        assertTrue(serie != null, "busqueda: sin series")

        val detail = client.itemData(account, serie!!.contentId, type = "0")
        assertTrue(detail.simpleProgramList.isNotEmpty(), "serie sin episodios")
        val episodio = detail.simpleProgramList.first()

        val ticket = client.startPlayVod(
            session = account,
            contentId = episodio.contentId,
            type = "0",
            columnId = config.vodRootColumnId,
            seriesContentId = serie.contentId,
            episodeNumbers = listOf(episodio.seriesNumber ?: 1),
        )
        assertTrue(ticket.episodeList.isNotEmpty(), "startPlayVOD serie: sin episodios")
        val variant = ticket.episodeList[0].variants.first()
        assertTrue(variant.licenseList.isNotEmpty())
    }

    @Test
    fun `error de portal produce excepcion tipada`() = runTest {
        val account = client.active()
        // contentId inexistente -> portal100008
        try {
            client.itemData(account, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", type = "1")
            assertTrue(false, "debía fallar")
        } catch (e: XuperException) {
            assertTrue(e is XuperException.Portal || e is XuperException.Kick)
        } catch (e: HttpException) {
            // red: aceptable en este test
        }
    }
}
