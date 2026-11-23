package com.xuperclean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortalProfileTest {

    @Test
    fun parsea_json_valido() {
        val p = PortalProfile.parse("""{"apkVer":"50001","note":"nuevo"}""")
        assertEquals("50001", p?.apkVer)
        assertNull(p?.portalKey)
    }

    @Test
    fun json_invalido_devuelve_null() {
        assertNull(PortalProfile.parse("no soy json"))
    }

    @Test
    fun aplica_solo_los_campos_presentes() {
        val config = XuperConfig(sn = "x")
        PortalProfile(apkVer = "60000").applyTo(config)
        assertEquals("60000", config.apkVerHeader)
        assertEquals("e7af1ed7de1ffddd7bd3fe37ebdffde9ef3fe1ae39edfeb8", config.portalKey)
    }

    @Test
    fun no_aplica_campos_vacios() {
        val config = XuperConfig(sn = "x")
        PortalProfile(apkVer = "  ", hosts = emptyList()).applyTo(config)
        assertEquals("43404", config.apkVerHeader)
        assertEquals(3, config.portalHosts.size)
    }

    @Test
    fun mezcla_conserva_lo_anterior() {
        val previo = PortalProfile(apkVer = "50001", portalKey = "clave")
        val nuevo = PortalProfile(reserve1 = "abc")
        val mezcla = nuevo.mergedWith(previo)
        assertEquals("50001", mezcla.apkVer)
        assertEquals("clave", mezcla.portalKey)
        assertEquals("abc", mezcla.reserve1)
    }

    @Test
    fun el_anterior_puede_ser_null() {
        assertEquals("1", PortalProfile(apkVer = "1").mergedWith(null).apkVer)
    }

    @Test
    fun solo_cuenta_como_util_si_trae_algo() {
        assertFalse(PortalProfile(note = "x").updatesAnything())
        assertTrue(PortalProfile(apkVer = "1").updatesAnything())
    }

    @Test
    fun ida_y_vuelta_json() {
        val p = PortalProfile(apkVer = "1", hosts = listOf("a.com"))
        assertEquals(p, PortalProfile.parse(PortalProfile.encode(p)))
    }
}
