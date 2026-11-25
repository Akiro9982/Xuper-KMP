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

    private fun capitulo(
        nombre: String,
        temporada: Int?,
        episodio: Int,
        copia: String,
        at: Long = 1_000,
        nombreEpisodio: String? = null,
    ) = WatchProgress(
        contentId = "ep$copia",
        assetId = copia,
        name = nombre,
        type = "0",
        isSeries = true,
        episodeId = "ep$copia",
        episodeNumber = episodio,
        episodeName = nombreEpisodio,
        seasonNumber = temporada,
        positionMs = 60_000,
        durationMs = 1_360_000,
        updatedAt = at,
    )

    @Test
    fun `una serie tiene una sola entrada aunque cambie de temporada`() {
        val t1 = capitulo("Star vs. las fuerzas del mal", 1, 1, "copiaT1", at = 1_000)
        val t2 = capitulo("Star vs. las fuerzas del mal", 2, 1, "copiaT2", at = 5_000)
        val lista = WatchHistory.upsert(listOf(t1), t2)
        assertEquals(1, lista.size)
        assertEquals(2, lista.single().seasonNumber)
        assertEquals("T2 E1", lista.single().episodeLabel)
    }

    @Test
    fun `al leer se quita la tarjeta repetida`() {
        // Versiones anteriores guardaban una entrada por temporada publicada.
        val t1 = capitulo("Star vs. las fuerzas del mal", 1, 1, "c1", at = 1_000)
        val t2 = capitulo("Star vs. las fuerzas del mal T2", 2, 1, "c2", at = 5_000)
        val lista = WatchHistory.decode(WatchHistory.encode(listOf(t1, t2)))
        assertEquals(1, lista.size)
        assertEquals(2, lista.single().seasonNumber)
    }

    @Test
    fun `el doblaje y el original son la misma entrada`() {
        // El portal publica la misma serie con dos títulos; «Continuar viendo» debe
        // quedarse con una sola tarjeta aunque el nombre cambie de idioma.
        val original = capitulo("Star vs. the Forces of Evil", 2, 1, "copiaEn", at = 1_000)
        val doblada = capitulo("Star vs. las fuerzas del mal", 2, 1, "copiaEs", at = 9_000)
            .copy(aliasKey = "star vs. the forces of evil")
        val lista = WatchHistory.upsert(listOf(original), doblada)
        assertEquals(1, lista.size)
        assertEquals("Star vs. las fuerzas del mal", lista.single().name)
        assertEquals("T2 E1", lista.single().episodeLabel)
    }

    @Test
    fun `series distintas no se mezclan`() {
        val star = capitulo("Star vs. las fuerzas del mal", 2, 1, "a").copy(aliasKey = "star vs. the forces of evil")
        val otra = capitulo("Otra serie", 1, 1, "b", at = 9_000).copy(aliasKey = "another show")
        assertEquals(2, WatchHistory.upsert(listOf(star), otra).size)
    }

    @Test
    fun `repara la temporada guardada a partir del nombre del episodio`() {
        // Entrada de una versión anterior: el nombre del asset no llevaba la temporada,
        // así que se guardó T1 aunque el capítulo fuera de la T2.
        val vieja = capitulo(
            nombre = "Star vs. the Forces of Evil",
            temporada = 1,
            episodio = 1,
            copia = "12E524B2",
            nombreEpisodio = "Star vs. the Forces of Evil Season 2 - 1",
        )
        val arreglada = WatchHistory.decode(WatchHistory.encode(listOf(vieja))).single()
        assertEquals(2, arreglada.seasonNumber)
        assertEquals("T2 E1", arreglada.episodeLabel)
    }

    @Test
    fun `la tarjeta quita el sufijo de temporada del nombre`() {
        val vieja = capitulo(
            nombre = "Star vs. las fuerzas del mal T2",
            temporada = 2,
            episodio = 1,
            copia = "copiaT2",
        )
        assertEquals(
            "Star vs. las fuerzas del mal",
            WatchHistory.decode(WatchHistory.encode(listOf(vieja))).single().name,
        )
    }

    @Test
    fun `se borra la serie completa desde cualquier copia`() {
        val entrada = capitulo("Star vs. las fuerzas del mal", 2, 1, "copiaT2")
        assertEquals(emptyList(), WatchHistory.remove(listOf(entrada), "copiaT2"))
        assertEquals(emptyList(), WatchHistory.remove(listOf(entrada), entrada.clave))
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
