package com.xuperclean.core

import com.xuperclean.core.crypto.XuperCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Vectores reales obtenidos del cliente NetXus (captura TLS descifrada). */
class IdentityTest {

    @Test
    fun md5CoincideConVectoresConocidos() {
        // Par real snToken -> sn del cliente oficial.
        assertEquals(
            "82f0045fea191c36486157e73add0584",
            IdentityFactory.snFromToken("b01ff330-222f-4d95-a18f-41909ea062dd"),
        )
        assertEquals(
            "b6ef72a334be15e8f277f40fc6e765a5",
            IdentityFactory.snFromToken("b1183e42-03c1-4cbb-855f-0aad76270c11"),
        )
    }

    @Test
    fun md5VectorRFC1321() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5(ByteArray(0)).hex())
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5("abc".encodeToByteArray()).hex())
        assertEquals(
            "d174ab98d277d9f5a5611c2c9f419d9f",
            md5("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".encodeToByteArray()).hex(),
        )
    }

    @Test
    fun claveHexDe24BytesSeUsaCruda() {
        val hex = "e7af1ed7de1ffddd7bd3fe37ebdffde9ef3fe1ae39edfeb8"
        assertTrue(XuperCrypto.deriveKey(hex).contentEquals(XuperCrypto.hexToBytes(hex)))
        // El formato heredado (UUID) sigue dando la misma clave de 24 bytes.
        assertEquals(
            XuperCrypto.deriveKey(hex).hex(),
            XuperCrypto.deriveKey("568e194f-d170-4369-96e8-4a457f641880").hex(),
        )
    }

    @Test
    fun cifradoRedondoConClaveHex() {
        val json = """{"sn":"abc","openNum":600}"""
        val cifrado = XuperCrypto.encryptRequest(json, "e7af1ed7de1ffddd7bd3fe37ebdffde9ef3fe1ae39edfeb8")
        assertEquals(json, XuperCrypto.decryptResponse(cifrado, "e7af1ed7de1ffddd7bd3fe37ebdffde9ef3fe1ae39edfeb8"))
    }
}
