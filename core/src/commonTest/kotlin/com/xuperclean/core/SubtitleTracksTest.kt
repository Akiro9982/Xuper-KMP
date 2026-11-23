package com.xuperclean.core

import com.xuperclean.core.model.SubFile
import com.xuperclean.core.model.Subtitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Normalización de subtítulos del portal (iptv-api.md §14: `subtitleList`). */
class SubtitleTracksTest {

    @Test
    fun `prefiere vtt sobre srt y conserva el idioma`() {
        val pistas = SubtitleTracks.from(
            listOf(
                Subtitle(
                    language = "en",
                    file = listOf(
                        SubFile(url = "http://cdn/x.en.srt", fileType = "srt"),
                        SubFile(url = "http://cdn/x.en.vtt", fileType = "vtt"),
                    ),
                ),
            ),
        )
        assertEquals(1, pistas.size)
        assertEquals("Inglés", pistas.single().label)
        assertEquals("text/vtt", pistas.single().mimeType)
        assertTrue(pistas.single().url.endsWith(".vtt"))
    }

    @Test
    fun `infiere el mime por la extension si falta el tipo`() {
        val pistas = SubtitleTracks.from(
            listOf(Subtitle(language = "es", file = listOf(SubFile(url = "http://cdn/x.es.srt")))),
        )
        assertEquals("application/x-subrip", pistas.single().mimeType)
        assertEquals("Español", pistas.single().label)
    }

    @Test
    fun `descarta ficheros sin formato conocido y duplicados`() {
        val pistas = SubtitleTracks.from(
            listOf(
                Subtitle(language = "es", file = listOf(SubFile(url = "http://cdn/raro.bin"))),
                Subtitle(language = "es", file = listOf(SubFile(url = "http://cdn/es.vtt", fileType = "vtt"))),
                Subtitle(language = "es", file = listOf(SubFile(url = "http://cdn/es.vtt", fileType = "vtt"))),
            ),
        )
        assertEquals(1, pistas.size)
    }

    @Test
    fun `idioma desconocido se muestra tal cual`() {
        assertEquals("xx", SubtitleTracks.languageLabel("xx"))
        assertEquals("Desconocido", SubtitleTracks.languageLabel(null))
    }
}
