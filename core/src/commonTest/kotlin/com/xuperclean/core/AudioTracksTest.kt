package com.xuperclean.core

import com.xuperclean.core.model.MovieVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioTracksTest {

    @Test
    fun normaliza_alias_y_mayusculas() {
        assertEquals("spa", AudioTracks.normalize(" SPA "))
        assertEquals("lat", AudioTracks.normalize("es-MX"))
        assertEquals("eng", AudioTracks.normalize("EN"))
        assertNull(AudioTracks.normalize("  "))
    }

    @Test
    fun respeta_orden_de_preferencias_del_usuario() {
        val disponibles = AudioTracks.from("spa,eng")
        assertEquals("eng", AudioTracks.pickOf(disponibles, listOf("lat", "eng", "spa"))?.code)
    }

    @Test
    fun etiqueta_codigo_desconocido_tal_cual() {
        assertEquals("xyz", AudioTracks.label("xyz"))
        assertEquals("Desconocido", AudioTracks.label(null))
    }

    @Test
    fun deduplica_codigos_repetidos() {
        val pistas = AudioTracks.from("spa,spa,eng")
        assertEquals(listOf("spa", "eng"), pistas.map { it.code })
    }

    @Test
    fun etiqueta_latino_y_espanol_por_separado() {
        assertEquals("Latino", AudioTracks.label("lat"))
        assertEquals("Español", AudioTracks.label("spa"))
        assertEquals("Inglés", AudioTracks.label("eng"))
    }

    @Test
    fun audio_vacio_no_ofrece_nada() {
        assertTrue(AudioTracks.from(null).isEmpty())
        assertTrue(AudioTracks.from("").isEmpty())
        assertFalse(AudioTracks.hasChoice(emptyList()))
    }

    @Test
    fun latino_prefiere_pista_spa_del_contenedor() {
        // El contenedor etiqueta el latino como `spa`, así que debe caer en esa pista.
        assertEquals(listOf("lat", "spa"), AudioTracks.preferenceChain("lat"))
        val disponibles = AudioTracks.from("spa,eng")
        assertEquals("spa", AudioTracks.pick(disponibles, "lat")?.code)
    }

    @Test
    fun preferencia_explicita_de_otro_idioma() {
        val disponibles = AudioTracks.from("spa,eng")
        assertEquals("eng", AudioTracks.pick(disponibles, "eng")?.code)
    }

    @Test
    fun idioma_no_disponible_cae_a_la_primera() {
        val disponibles = AudioTracks.from("spa,eng")
        assertEquals("spa", AudioTracks.pick(disponibles, "por")?.code)
    }

    @Test
    fun solo_hay_selector_con_dos_idiomas() {
        assertFalse(AudioTracks.hasChoice(AudioTracks.from("spa,spa")))
        assertTrue(AudioTracks.hasChoice(AudioTracks.from("spa,eng")))
    }

    @Test
    fun recoge_idiomas_de_las_variantes() {
        val variantes = listOf(
            MovieVariant(contentId = "a", audioInfo = "eng,lat"),
            MovieVariant(contentId = "b", audioInfo = null),
        )
        assertEquals(listOf("eng", "lat"), AudioTracks.fromVariants(variantes).map { it.code })
        assertNull(AudioTracks.fromVariants(emptyList()).firstOrNull())
    }
}
