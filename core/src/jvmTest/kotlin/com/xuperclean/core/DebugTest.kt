package com.xuperclean.core

import com.xuperclean.core.net.JvmHttpEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

/** Diagnóstico: imprime el JSON exacto que se cifra y envía. */
class DebugTest {
    @Test
    fun `imprimir body de getLiveData`() = runTest {
        val config = XuperConfig(sn = "4c3b596ade128c770e86d19424c6f38a")
        val codec = PortalCodec(config)
        val client = XuperPortalClient(config, JvmHttpEngine())
        val account = client.active()
        println("ACTIVE OK userId=${account.userId} portal=${account.portalCode}")

        val slb = client.slbInfo(account)
        println("SLB OK cdns=${slb.cdnList.size}")

        val bean = buildJsonObject {
            put("columnId", config.liveColumnId)
            put("dataVersion", "")
            put("expireTimeStr", "")
            put("pageNum", 1)
            put("pageSize", 3000)
            put("userId", account.userId)
            put("userToken", account.userToken)
        }
        val body = codec.encryptBody(bean, account.portalCode)
        println("BODY_HEX: $body")
        try {
            val data = client.liveData(account)
            println("LIVEDATA OK: ${data.size} canales")
        } catch (e: Exception) {
            println("LIVEDATA ERR: ${e.message}")
        }
    }
}
