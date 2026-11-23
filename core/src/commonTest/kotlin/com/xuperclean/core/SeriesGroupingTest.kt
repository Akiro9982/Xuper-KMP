package com.xuperclean.core

import com.xuperclean.core.model.VodAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Agrupación de temporadas tal y como las devuelve el portal (nombre con sufijo T1/T2 y alias). */
class SeriesGroupingTest {

    private fun serie(
        nombre: String,
        id: String = nombre,
        tipo: String = "0",
        alias: String? = null,
    ) = VodAsset(
        contentId = id,
        name = nombre,
        type = tipo,
        programType = if (tipo == "0") "teleplay" else "movie",
        alias = alias,
    )

    @Test
    fun `detecta la temporada del sufijo`() {
        assertEquals(1, SeriesGrouping.seasonNumber("Un Show Más T1"))
        assertEquals(2, SeriesGrouping.seasonNumber("Un show más T2"))
        assertEquals(3, SeriesGrouping.seasonNumber("Otra Serie Temporada 3"))
        assertEquals(1, SeriesGrouping.seasonNumber("Chi.: Chikyuu no Undou ni Tsuite"))
        assertEquals(1, SeriesGrouping.seasonNumber("Un show más: la película"))
    }

    @Test
    fun `detecta las variantes temp y season del portal`() {
        assertEquals(2, SeriesGrouping.seasonNumber("Master of None Temp.2"))
        assertEquals(1, SeriesGrouping.seasonNumber("Underwear Temp.1"))
        assertEquals(2, SeriesGrouping.seasonNumber("Historias corrientes T2"))
        assertEquals(6, SeriesGrouping.seasonNumber("Regular Show Season6"))
        assertEquals("master of none", SeriesGrouping.baseName("Master of None Temp.2"))
        assertEquals("Regular Show", SeriesGrouping.searchTitle("Regular Show Season2"))
    }

    @Test
    fun `el titulo base ignora mayusculas acentos y sufijo`() {
        assertEquals("un show mas", SeriesGrouping.baseName("Un Show Más T1"))
        assertEquals("un show mas", SeriesGrouping.baseName("Un show más T2"))
        assertEquals("un show mas: las cintas perdidas", SeriesGrouping.baseName("Un Show Más: Las Cintas Perdidas T1"))
    }

    @Test
    fun `agrupa dos temporadas en una sola serie`() {
        val grupos = SeriesGrouping.group(
            listOf(
                serie("Un show más T2", "t2"),
                serie("Un Show Más T1", "t1"),
            ),
        )
        assertEquals(1, grupos.size)
        assertEquals("un show mas", grupos.first().baseName)
        assertEquals(listOf(1, 2), grupos.first().seasons.map { it.number })
        assertEquals(listOf("t1", "t2"), grupos.first().seasons.map { it.asset.contentId })
        assertTrue(grupos.first().isMultiSeason)
    }

    @Test
    fun `une los titulos alternos y sus temporadas por alias`() {
        // Caso real del portal: T1/T2 salen como «Un Show Más» y T3/T4 como
        // «Historias corrientes», pero todas comparten el alias «Regular Show».
        val grupos = SeriesGrouping.group(
            listOf(
                serie("Un Show Más T1", "a1", alias = "Regular Show S1"),
                serie("Un show más T2", "a2", alias = "Regular Show S2"),
                serie("Historias corrientes T3", "b3", alias = "Regular Show Season3"),
                serie("Historias corrientes T4", "b4", alias = "Regular Show Season4"),
            ),
        )
        assertEquals(1, grupos.size)
        assertEquals(listOf(1, 2, 3, 4), grupos.single().seasons.map { it.number })
        assertEquals("Un Show Más", grupos.single().displayName)
    }

    @Test
    fun `una temporada publicada con dos nombres no se repite`() {
        val grupos = SeriesGrouping.group(
            listOf(
                serie("Un show más T2", "a2", alias = "Regular Show S2"),
                serie("Historias corrientes T2", "b2", alias = "Regular Show Season2"),
            ),
        )
        assertEquals(1, grupos.size)
        assertEquals(1, grupos.single().seasons.size)
        assertEquals("a2", grupos.single().seasons.single().asset.contentId)
    }

    @Test
    fun `un spin off no se mezcla con la serie base`() {
        val grupos = SeriesGrouping.group(
            listOf(
                serie("Un Show Más T1", "t1", alias = "Regular Show S1"),
                serie(
                    "Un Show Más: Las Cintas Perdidas T1",
                    "spin",
                    alias = "Regular Show: The Lost Tapes S1",
                ),
            ),
        )
        assertEquals(2, grupos.size)
        assertFalse(grupos.any { it.seasons.size > 1 })
    }

    @Test
    fun `una pelicula no entra en el agrupado de series`() {
        val grupos = SeriesGrouping.group(
            listOf(
                serie("Un show más T2", "t2"),
                serie("Un show más: la película", "peli", tipo = "1"),
            ),
        )
        assertEquals(1, grupos.size)
        assertEquals("t2", grupos.single().cover.contentId)
    }

    @Test
    fun `temporadas de una serie concreta incluyen la actual`() {
        val actual = serie("Un show más T2", "t2")
        val temporadas = SeriesGrouping.seasonsOf(
            listOf(serie("Un Show Más T1", "t1"), serie("Otra Cosa T1", "otra")),
            actual,
        )
        assertEquals(listOf(1, 2), temporadas.map { it.number })
        assertEquals("t2", temporadas.last().asset.contentId)
    }

    @Test
    fun `seasonsOf encuentra la serie por su titulo alterno`() {
        val actual = serie("Historias corrientes T3", "b3", alias = "Regular Show Season3")
        val temporadas = SeriesGrouping.seasonsOf(
            listOf(
                serie("Un Show Más T1", "a1", alias = "Regular Show S1"),
                serie("Historias corrientes T3", "b3", alias = "Regular Show Season3"),
            ),
            actual,
        )
        assertEquals(listOf(1, 3), temporadas.map { it.number })
    }

    @Test
    fun `la busqueda devuelve una ficha por serie con titulo limpio`() {
        val resultados = SeriesGrouping.searchResults(
            listOf(
                serie("Un show más T2", "a2", alias = "Regular Show S2"),
                serie("Un Show Más T1", "a1", alias = "Regular Show S1"),
                serie("Historias corrientes T3", "b3", alias = "Regular Show Season3"),
                serie("Un show más: la película", "peli", tipo = "1"),
            ),
        )
        assertEquals(2, resultados.size)
        val serieUnica = resultados.first { it.asset.isSeries }
        assertEquals("Un Show Más", serieUnica.title)
        assertEquals("a1", serieUnica.asset.contentId)
        assertEquals("Un Show Más", serieUnica.cardAsset.name)
    }

    @Test
    fun `sin sufijo de temporada se asume una sola temporada`() {
        val grupos = SeriesGrouping.group(listOf(serie("Chi.: Chikyuu no Undou ni Tsuite", "chi")))
        assertEquals(1, grupos.single().seasons.single().number)
    }
}
