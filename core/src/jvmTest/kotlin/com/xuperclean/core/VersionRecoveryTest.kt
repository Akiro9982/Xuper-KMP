package com.xuperclean.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Recuperación de la puerta de versión del portal (`portal200001`): sondeo de builds en
 * ambos sentidos y persistencia del build aceptado. Usa `runBlocking` porque los topes son
 * de tiempo real.
 */
class VersionRecoveryTest {

    /**
     * Motor falso que rechaza todo build salvo los de [aceptados]. Cuenta las sondas para
     * comprobar en qué dirección se busca.
     */
    private class FakeEngine(var aceptados: Set<String>) : com.xuperclean.core.net.HttpEngine {
        val sondeos = mutableListOf<String>()

        override suspend fun post(
            url: String,
            headers: Map<String, String>,
            body: ByteArray,
        ): com.xuperclean.core.net.HttpResult {
            val build = headers["apkVer"] ?: ""
            sondeos += build
            return if (build in aceptados) {
                com.xuperclean.core.net.HttpResult(
                    200, emptyMap(), """{"returnCode":"0","errorMessage":"ok"}""".encodeToByteArray(),
                )
            } else {
                com.xuperclean.core.net.HttpResult(
                    200, emptyMap(),
                    """{"returnCode":"portal200001","errorMessage":"ok"}""".encodeToByteArray(),
                )
            }
        }

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
        ): com.xuperclean.core.net.HttpResult = error("no GET esperado")
    }

    @Test
    fun `adopta el primer build aceptado por encima y lo persiste`() = runBlocking {
        val config = XuperConfig(sn = "S").apply { apkVerHeader = "100"; versionRecoveryProbes = 64 }
        val cliente = XuperPortalClient(config, FakeEngine(setOf("103")))
        val guardados = mutableListOf<PortalProfile>()
        cliente.onProfileChanged = { guardados += it }

        assertTrue(cliente.recoverVersion())
        assertEquals("103", config.apkVerHeader)
        assertEquals("103", guardados.last().apkVer)
    }

    @Test
    fun `tambien encuentra el build si quedo por debajo`() = runBlocking {
        val config = XuperConfig(sn = "S").apply { apkVerHeader = "100"; versionRecoveryProbes = 64 }
        val cliente = XuperPortalClient(config, FakeEngine(setOf("97")))

        assertTrue(cliente.recoverVersion())
        assertEquals("97", config.apkVerHeader)
    }

    @Test
    fun `devuelve false y conserva el build si no encuentra nada`() = runBlocking {
        val config = XuperConfig(sn = "S").apply { apkVerHeader = "100"; versionRecoveryProbes = 16 }
        val cliente = XuperPortalClient(config, FakeEngine(emptySet()))

        assertFalse(cliente.recoverVersion())
        assertEquals("100", config.apkVerHeader)
        assertTrue("app nueva" in XuperException.VersionRetired("portal200001").message!!)
    }

    // ------------------------------------------------------------ Vía C: rotación de clave

    /**
     * Motor que solo acepta la combinación build×clave [claveValida]: descifra el body con
     * cada clave candidata para saber con cuál se cifró (así se simula el rechazo del
     * servidor cuando la `PORTAL_KEY` rotó). [perfil] se sirve por GET.
     */
    private class FakeRotacion(
        private val claves: List<String>,
        private val buildAceptado: String,
        private val claveValida: String,
        private val perfil: String?,
    ) : com.xuperclean.core.net.HttpEngine {
        val sondeos = mutableListOf<String>()
        var perfilPedidos = 0

        override suspend fun post(
            url: String,
            headers: Map<String, String>,
            body: ByteArray,
        ): com.xuperclean.core.net.HttpResult {
            val build = headers["apkVer"] ?: ""
            sondeos += build
            val texto = body.decodeToString()
            val usada = claves.firstOrNull { clave ->
                runCatching {
                    val json = com.xuperclean.core.crypto.XuperCrypto.decryptResponse(texto, clave)
                    json.startsWith("{") && "\"sn\"" in json
                }.getOrDefault(false)
            }
            val aceptado = build == buildAceptado && usada == claveValida
            val codigo = if (aceptado) """{"returnCode":"0","errorMessage":"ok"}"""
            else """{"returnCode":"portal200001","errorMessage":"ok"}"""
            return com.xuperclean.core.net.HttpResult(200, emptyMap(), codigo.encodeToByteArray())
        }

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
        ): com.xuperclean.core.net.HttpResult {
            perfilPedidos++
            val p = perfil
                ?: return com.xuperclean.core.net.HttpResult(404, emptyMap(), ByteArray(0))
            return com.xuperclean.core.net.HttpResult(200, emptyMap(), p.encodeToByteArray())
        }
    }

    @Test
    fun `recupera la clave rotada desde el perfil remoto`() = runBlocking {
        val config = XuperConfig(sn = "S").apply {
            apkVerHeader = "100"
            portalKey = "claveVieja"
            profileUrl = "https://perfil.test/p.json"
            versionRecoveryShortProbes = 16
            versionRecoveryProbes = 16
        }
        // El servidor ya no acepta (100, claveVieja): solo (100, claveNueva).
        val motor = FakeRotacion(
            claves = listOf("claveVieja", "claveNueva"),
            buildAceptado = "100",
            claveValida = "claveNueva",
            perfil = """{"apkVer":"100","portalKey":"claveNueva"}""",
        )
        val cliente = XuperPortalClient(config, motor)
        val guardados = mutableListOf<PortalProfile>()
        cliente.onProfileChanged = { guardados += it }

        assertTrue(cliente.recoverConnection())
        assertEquals("claveNueva", config.portalKey)
        assertEquals("100", config.apkVerHeader)
        assertEquals("claveNueva", guardados.last().portalKey)
        assertTrue(motor.perfilPedidos >= 1, "no consultó el perfil remoto")
    }

    @Test
    fun `sin perfil remoto usa el sondeo corto de builds`() = runBlocking {
        val config = XuperConfig(sn = "S").apply {
            apkVerHeader = "100"
            portalKey = "claveVieja"
            versionRecoveryShortProbes = 64
        }
        val motor = FakeRotacion(
            claves = listOf("claveVieja"),
            buildAceptado = "103",
            claveValida = "claveVieja",
            perfil = null,
        )
        val cliente = XuperPortalClient(config, motor)

        assertTrue(cliente.recoverConnection())
        assertEquals("103", config.apkVerHeader)
        assertEquals(0, motor.perfilPedidos, "no debe pedir perfil si profileUrl está vacío")
    }

    @Test
    fun `si nada se verifica se conserva la clave original`() = runBlocking {
        val config = XuperConfig(sn = "S").apply {
            apkVerHeader = "100"
            portalKey = "claveVieja"
            profileUrl = "https://perfil.test/p.json"
            versionRecoveryShortProbes = 16
            versionRecoveryProbes = 16
        }
        // El perfil trae una clave que tampoco sirve y ningún build de los sondeados vale.
        val motor = FakeRotacion(
            claves = listOf("claveVieja", "claveMala"),
            buildAceptado = "99999",
            claveValida = "claveMala",
            perfil = """{"portalKey":"claveMala"}""",
        )
        val cliente = XuperPortalClient(config, motor)
        val guardados = mutableListOf<PortalProfile>()
        cliente.onProfileChanged = { guardados += it }

        assertFalse(cliente.recoverConnection())
        assertEquals("claveVieja", config.portalKey, "una clave del perfil sin verificar no se adopta")
        assertEquals("100", config.apkVerHeader)
        assertTrue(guardados.isEmpty(), "no se persiste nada si no se verificó")
    }

    @Test
    fun `tras un fallo reciente no se repiten los barridos`() = runBlocking {
        val config = XuperConfig(sn = "S").apply {
            apkVerHeader = "100"
            versionRecoveryShortProbes = 16
            versionRecoveryProbes = 16
        }
        val motor = FakeRotacion(
            claves = listOf("claveVieja"),
            buildAceptado = "99999",
            claveValida = "claveVieja",
            perfil = null,
        )
        val cliente = XuperPortalClient(config, motor)

        assertFalse(cliente.recoverConnection())
        val trasPrimerFallo = motor.sondeos.size
        assertTrue(trasPrimerFallo > 0)

        assertFalse(cliente.recoverConnection())
        assertEquals(
            trasPrimerFallo,
            motor.sondeos.size,
            "el cooldown debe abortar sin volver a sondear",
        )
    }
}

