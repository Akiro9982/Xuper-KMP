package com.xuperclean.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** Recuperación de versión contra el portal real (red). */
class RealVersionRecoveryTest {

    @Test
    fun `recupera el build retirado desde uno falso`() = runBlocking {
        val config = XuperConfig(sn = "4c3b596ade128c770e86d19424c6f38a")
            .apply { apkVerHeader = "43405"; versionRecoveryProbes = 200 }
        val cliente = XuperPortalClient(config, com.xuperclean.core.net.JvmHttpEngine())
        assertTrue(cliente.recoverVersion(), "no encontró ningún build aceptado")
        println("build recuperado: ${config.apkVerHeader}")
    }
}

