package com.xuperclean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** El perfil remoto es la vía de auto-recuperación: debe poder cambiar clave y registro. */
class PerfilRemotoTest {

    @Test
    fun elPerfilPorDefectoApuntaAPages() {
        val config = XuperConfig(sn = "x")
        assertTrue(config.profileUrl.startsWith("https://") && config.profileUrl.endsWith(".json"))
        assertEquals(
            "https://akiro9982.github.io/xuper-profile/profile.json",
            config.profileUrl,
        )
    }

    @Test
    fun elPerfilAplicaClaveBuildYRegistro() {
        val config = XuperConfig(sn = "x")
        val perfil = PortalProfile.parse(
            """{"apkVer":"44000","portalKey":"aaaa","registrationKey":"bbbb","reserve1":"cccc"}""",
        )!!
        perfil.applyTo(config)
        assertEquals("44000", config.apkVerHeader)
        assertEquals("aaaa", config.portalKey)
        assertEquals("bbbb", config.registrationKey)
        assertEquals("cccc", config.reserve1)
        assertTrue(perfil.updatesAnything())
    }

    @Test
    fun elPerfilVacioNoPisaNada() {
        val config = XuperConfig(sn = "x")
        val antes = config.portalKey to config.apkVerHeader
        PortalProfile.parse("{}")!!.applyTo(config)
        assertEquals(antes, config.portalKey to config.apkVerHeader)
    }
}
