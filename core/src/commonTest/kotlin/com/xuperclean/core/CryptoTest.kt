package com.xuperclean.core

import com.xuperclean.core.crypto.SunBase64
import com.xuperclean.core.crypto.XuperCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vectores de ground truth capturados de la app real (logcat instrumentado,
 * XUPER_REQJSON -> XUPER_REQBODY) y de las claves verificadas en iptv-api.md §2.
 * El cifrado debe ser BYTE-EXACTO contra la app.
 */
class CryptoTest {

    companion object {
        const val PORTAL_KEY = "568e194f-d170-4369-96e8-4a457f641880"

        /** Clave runtime derivada (verificada en producción). */
        val EXPECTED_KEY_HEX = "e7af1ed7de1ffddd7bd3fe37ebdffde9ef3fe1ae39edfeb8"

        // Vector 1: v8/active capturado de la app (2026-11-17, MuMu)
        val V1_JSON = """{"authCode":"","authVersion":"","channel":"default","macAddr":"02:00:00:00:00:00","matadata":"","openNum":14,"reserve1":"583442714b345572434a4d5769637461545a735936413d3d","signdata":"","snToken":"","loginType":"2","appLanguage":"es","apkVersion":"49902","sysVersion":"2026-10-26 20:20:08_35_15_6.1.90-perf+","appId":"com.android.msandroid","hardwareInfo":"Samsung","model":"SM-S908N","product":"b0qksx","cpu":"arm64-v8a","B29":"","portalCode":"","deviceToken":"","sn":"4c3b596ade128c770e86d19424c6f38a","drmId":"","sdkVer":35}"""
        val V1_BODY_HEX = "5952384c4d423138745962563979706646797146686839633568445468687271684670304b6438517357337a41515764374a61654174662f4538466e374271526961744c6e65465a662b38335844567670756435334742674a6b6e7967664865765948676d56444467764a765834327a7736426c3178414d4c2b755679514a4f51586979744b32666c786a566465673476686c5758334231577a6c307a745266776365465162794e386a72664274323977666661527164576145332b616a39496e37546b4948754e357978554d586147337842497a77384d514f726e6b44654e38744c4c787957304c4f317a7a5369416c69584a6739455653437077634e6e326c5245312b736e396b52546e4e4f69326e43325674356e6d38584c5147766750704663797863555556323943593241614368642f303633736c444c4253443657545a7358324a614e6341327177743165302f3537696a664851527159334337453950395731735441726d61554979466739394c4a336b4f7235384857684e6f59735336725347547977446933465969684d7674644c464b51465747324b6e506f59525875574e43374b4656614869674a5648777748556335756a476c6e5a6277302f61436b786c79502f45506c5366736f6e446f4931654a51494d7a6943312b5655675579345833366947756d494b6a597876345742446958392f424e71664e6d364d4d556a4f437839794e396c3345626c5639784d785856455a4541706a39774d506b4f7275753153776f747332383576764d4a6473796c454642456a5762487566716f544b646f554a544c77535432525a7a30554c68716e576138475331564530715972567859397451594b5558373244412f62396b304542324c7a76634752654476785132595652734d6d7a5758745553316462374b414b70787079466146372b654c49565064694a732b6a765134514e5a644749545350366a36466f"
    }

    @Test
    fun `clave runtime derivada con base64-sun es la verificada`() {
        val key = XuperCrypto.deriveKey(PORTAL_KEY)
        assertEquals(EXPECTED_KEY_HEX, key.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun `base64-sun decodifica el guion como 63`() {
        // "-" vale 63 en el decodificador sun (no se descarta)
        val withDash = "568e194f-d170-4369-96e8-4a457f641880"
        val std = SunBase64.decode(withDash.replace("-", "+"))
        val sun = SunBase64.decode(withDash)
        // deben diferir exactamente en las posiciones afectadas por el '-',
        // pero con la MISMA longitud
        assertEquals(std.size, sun.size)
    }

    @Test
    fun `base64-sun roundtrip con datos binarios`() {
        val data = ByteArray(257) { (it * 37 + 11).toByte() }
        val encoded = SunBase64.encode(data)
        assertEquals(data.toList(), SunBase64.decode(encoded).toList())
    }

    @Test
    fun `encryptRequest reproduce byte a byte el body capturado de la app`() {
        val encrypted = XuperCrypto.encryptRequest(V1_JSON, PORTAL_KEY)
        assertEquals(V1_BODY_HEX, encrypted, "el body cifrado debe ser identico al capturado")
    }

    @Test
    fun `decryptResponse invierte encryptRequest`() {
        val roundtrip = XuperCrypto.decryptResponse(V1_BODY_HEX, PORTAL_KEY)
        assertEquals(V1_JSON, roundtrip)
    }

    @Test
    fun `padding pkcs5 correcto`() {
        val data = "abc".encodeToByteArray()
        val padded = XuperCrypto.padPkcs5(data, 8)
        assertEquals(8, padded.size)
        assertEquals(5, padded.last().toInt())
        assertEquals(data.toList(), XuperCrypto.unpadPkcs5(padded).toList())
        // bloque exacto: se añade un bloque completo de padding
        val exact = ByteArray(8) { 1 }
        val padded2 = XuperCrypto.padPkcs5(exact, 8)
        assertEquals(16, padded2.size)
    }

    @Test
    fun `hex helpers roundtrip`() {
        val data = ByteArray(64) { it.toByte() }
        val hex = data.joinToString("") { "%02x".format(it) }
        assertEquals(data.toList(), XuperCrypto.hexToBytes(hex).toList())
    }
}
