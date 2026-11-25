package com.xuperclean.core

import com.xuperclean.core.model.EpisodeInfo
import com.xuperclean.core.model.VodAsset
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fusión de las copias de una temporada (el portal publica la misma dos veces). */
class SeasonMergeTest {

    private fun temporada(id: String, nombre: String = "Serie T2") = VodAsset(
        contentId = id,
        name = nombre,
        type = "0",
        programType = "teleplay",
    )

    private fun episodios(vararg numeros: Int): List<EpisodeInfo> = numeros.map { n ->
        EpisodeInfo(contentId = "ep$n", seriesNumber = n)
    }

    @Test
    fun `una copia con mas espanol manda sobre la de mas idiomas`() {
        val corta = SeasonCopy(temporada("corta"), "eng,spa", episodios(1, 2, 3))
        val completa = SeasonCopy(temporada("completa"), "spa,spa,eng", episodios(1, 2))
        val fusion = SeasonMerge.merge(listOf(corta, completa))
        assertEquals(listOf(1, 2, 3), fusion.map { it.episode.seriesNumber })
        assertEquals("completa", fusion[0].season.contentId)
        assertEquals("completa", fusion[1].season.contentId)
        // El episodio 3 solo existe en la copia recortada: se conserva.
        assertEquals("corta", fusion[2].season.contentId)
    }

    @Test
    fun `con el mismo espanol gana la copia con mas idiomas`() {
        val doblada = SeasonCopy(temporada("doblada"), "eng,spa", episodios(1, 2, 3, 4))
        val original = SeasonCopy(temporada("original"), "eng,por,spa", episodios(1, 2))
        val fusion = SeasonMerge.merge(listOf(doblada, original))
        assertEquals(listOf(1, 2, 3, 4), fusion.map { it.episode.seriesNumber })
        assertEquals("original", fusion[0].season.contentId)
        assertEquals("doblada", fusion[2].season.contentId)
    }

    @Test
    fun `no se pierden episodios cuando las copias numeran distinto`() {
        val original = SeasonCopy(temporada("original"), "por,eng,spa", episodios(1, 3, 4))
        val doblada = SeasonCopy(temporada("doblada"), "eng,spa", episodios(1, 2, 3, 4, 5))
        val fusion = SeasonMerge.merge(listOf(doblada, original))
        assertEquals(listOf(1, 2, 3, 4, 5), fusion.map { it.episode.seriesNumber })
        assertEquals(listOf("original", "doblada", "original", "original", "doblada"),
            fusion.map { it.season.contentId })
    }

    @Test
    fun `un episodio sin numero se coloca por su posicion`() {
        val sinNumero = SeasonCopy(
            temporada("rara"),
            "eng,spa",
            listOf(EpisodeInfo(contentId = "a"), EpisodeInfo(contentId = "b")),
        )
        val fusion = SeasonMerge.merge(listOf(sinNumero))
        assertEquals(listOf("a", "b"), fusion.map { it.episode.contentId })
    }

    @Test
    fun `sin episodios no hay nada que fusionar`() {
        assertEquals(0, SeasonMerge.merge(emptyList()).size)
        assertEquals(0, SeasonMerge.merge(listOf(SeasonCopy(temporada("vacia"), "spa", emptyList()))).size)
    }
}
