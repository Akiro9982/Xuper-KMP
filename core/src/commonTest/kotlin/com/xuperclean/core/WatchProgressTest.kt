package com.xuperclean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Historial local de "Continuar viendo" (#8). */
class WatchProgressTest {

    private fun peli(
        id: String = "p1",
        pos: Long = 60_000,
        dur: Long = 3_600_000,
        at: Long = 1_000,
    ) = WatchProgress(
        contentId = id,
        assetId = id,
        name = "Peli $id",
        type = "1",
        isSeries = false,
        positionMs = pos,
        durationMs = dur,
        updatedAt = at,
    )

    @Test
    fun `solo se reanuda entre 30 segundos y el 95 por ciento`() {
        assertFalse(WatchProgress.isResumable(10_000, 3_600_000))
        assertTrue(WatchProgress.isResumable(60_000, 3_600_000))
        assertFalse(WatchProgress.isResumable(3_500_000, 3_600_000))
        assertFalse(WatchProgress.isResumable(60_000, 0))
    }

    @Test
    fun `upsert reemplaza la misma ficha y ordena por fecha`() {
        val viejo = peli(id = "a", at = 1_000)
        val nuevo = peli(id = "a", pos = 120_000, at = 5_000)
        val otra = peli(id = "b", at = 3_000)
        val lista = WatchHistory.upsert(listOf(viejo, otra), nuevo)
        assertEquals(listOf("a", "b"), lista.map { it.assetId })
        assertEquals(120_000, lista.first().positionMs)
    }

    @Test
    fun `recorta al maximo configurado`() {
        val items = (1..30).map { peli(id = "p$it", at = it.toLong()) }
        assertEquals(24, WatchHistory.upsert(items, peli(id = "x", at = 99), max = 24).size)
    }

    @Test
    fun `codificar y decodificar conserva los campos`() {
        val original = listOf(peli())
        val vuelta = WatchHistory.decode(WatchHistory.encode(original))
        assertEquals(original, vuelta)
        assertEquals(emptyList(), WatchHistory.decode("no-json"))
    }

    @Test
    fun `etiqueta de capitulo y minutos restantes`() {
        val movie = peli(pos = 60_000, dur = 3_600_000)
        assertEquals(null, movie.episodeLabel)
        assertEquals(59, movie.remainingMinutes)

        val ep = movie.copy(
            isSeries = true,
            type = "0",
            episodeNumber = 5,
            seasonNumber = 2,
            positionMs = 1_200_000,
            durationMs = 1_500_000,
        )
        assertEquals("T2 E5", ep.episodeLabel)
        assertEquals(5, ep.remainingMinutes)
        assertEquals("E7", ep.copy(seasonNumber = null, episodeNumber = 7).episodeLabel)
        // La tarjeta abre la ficha de la serie, no la del episodio.
        assertEquals("serie1", ep.copy(assetId = "serie1").toVodAsset().contentId)
    }

    @Test
    fun `una serie reanuda su episodio`() {
        val ep = peli(id = "e5").copy(
            assetId = "serie1",
            isSeries = true,
            type = "0",
            episodeId = "e5",
            episodeNumber = 5,
        )
        val lista = listOf(ep)
        assertEquals("e5", WatchHistory.find(lista, "serie1")?.episodeId)
        assertEquals(5, WatchHistory.findEpisode(lista, "e5")?.episodeNumber)
        assertEquals("serie1", ep.toVodAsset().contentId)
        assertTrue(ep.toVodAsset().isSeries)
    }
}
