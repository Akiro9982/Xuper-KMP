package com.xuperclean.core

import com.xuperclean.core.crypto.XuperCrypto
import com.xuperclean.core.net.HttpResult
import com.xuperclean.core.net.HttpEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * SessionManager con motor HTTP falso: sin red. Verifica refresco preventivo,
 * recuperación de kick y debounce.
 */
class SessionManagerTest {

    companion object {
        const val KEY = "568e194f-d170-4369-96e8-4a457f641880"

        fun enc(data: JsonObject): String = XuperCrypto.encryptRequest(data.toString(), KEY)

        fun activeJson(token: String): String = enc(buildJsonObject {
            put("userId", "111")
            put("userToken", token)
            put("hasPay", "0")
            put("userIdentity", "1")
            put("portalCodeList", Json.parseToJsonElement("""[{"portalCode":"masnew","type":"1"}]"""))
        })

        fun slbJson(): String = enc(buildJsonObject {
            put("cdn_list", Json.parseToJsonElement("""[{"main_addr":"http://cdn.test","cdn_type":"4","tag":"live","url_list":[{"tag":"free","url":"a=1"}]}]"""))
            put("play_params", "")
        })

        fun root(data: String): String =
            """{"returnCode":"0","errorMessage":"成功","data":"$data"}"""
    }

    /** Motor falso: cuenta llamadas active y puede devolver kick configurable. */
    private class FakeEngine(var activeCalls: Int = 0, var kickActive: Boolean = false) : HttpEngine {
        val slbCalls = intArrayOf(0)
        override suspend fun post(url: String, headers: Map<String, String>, body: ByteArray): HttpResult {
            return when {
                "active" in url -> {
                    activeCalls++
                    if (kickActive) HttpResult(200, emptyMap(),
                        """{"returnCode":"aaa100080","errorMessage":"snToken已经失效"}""".encodeToByteArray())
                    else HttpResult(200, emptyMap(), root(activeJson("tok-$activeCalls")).encodeToByteArray())
                }
                "getSlbInfo" in url -> {
                    slbCalls[0]++
                    HttpResult(200, emptyMap(), root(slbJson()).encodeToByteArray())
                }
                else -> fail("endpoint inesperado: $url")
            }
        }
        override suspend fun get(url: String, headers: Map<String, String>): HttpResult = fail("no GET esperado")
    }

    private fun manager(engine: FakeEngine, now: LongArray, config: XuperConfig = XuperConfig(sn = "S")) =
        SessionManager(XuperPortalClient(config, engine), config, nowMs = { now[0] })

    @Test
    fun `ensureValid refresca solo cuando la sesion es antigua`() = runTest {
        val engine = FakeEngine()
        val now = longArrayOf(1_000_000)
        val sm = manager(engine, now)

        val s1 = sm.ensureValid()
        assertEquals(1, engine.activeCalls)
        assertEquals("tok-1", s1.account.userToken)

        // fresca: no refresca
        now[0] += 5 * 60_000
        assertEquals(s1, sm.ensureValid())
        assertEquals(1, engine.activeCalls)

        // antigua (>30 min): refresca
        now[0] += 26 * 60_000
        val s2 = sm.ensureValid()
        assertEquals(2, engine.activeCalls)
        assertEquals("tok-2", s2.account.userToken)
    }

    @Test
    fun `kick refresca y reintenta una vez`() = runTest {
        val engine = FakeEngine()
        val now = longArrayOf(1_000_000)
        val sm = manager(engine, now)
        sm.ensureValid()
        assertEquals(1, engine.activeCalls)

        // el block falla con Kick simulado -> el manager refresca (active #2) y reintenta
        var intentos = 0
        val r = sm.withKickRetry { s ->
            intentos++
            if (intentos == 1) throw XuperException.Kick("409", "sesión sustituida")
            s.account.userToken
        }
        assertEquals("tok-2", r)
        assertEquals(2, intentos)
        assertEquals(2, engine.activeCalls)
    }

    @Test
    fun `debounce de kick reiterado`() = runTest {
        val engine = FakeEngine()
        val now = longArrayOf(1_000_000)
        val sm = manager(engine, now)
        sm.ensureValid()

        // primer kick: recuperado
        var intentos = 0
        sm.withKickRetry { s ->
            intentos++
            if (intentos == 1) throw XuperException.Kick("409", "kick1")
            "ok"
        }
        assertEquals(2, engine.activeCalls)

        // kick inmediato (dentro de los 60 s de debounce): debe lanzar IllegalArgumentException
        try {
            sm.withKickRetry { throw XuperException.Kick("409", "kick2") }
            fail("debounce debía lanzar")
        } catch (e: IllegalArgumentException) {
            assertTrue("kick reiterado" in e.message!!)
        }

        // pasado el debounce: recupera de nuevo
        now[0] += 61_000
        var intentos2 = 0
        sm.withKickRetry { s ->
            intentos2++
            if (intentos2 == 1) throw XuperException.Kick("409", "kick3")
            "ok2"
        }
        assertEquals(3, engine.activeCalls)
    }
}
