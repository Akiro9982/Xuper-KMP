package com.xuperclean.core

import com.xuperclean.core.model.VodAsset

/**
 * Agrupación de series por título base **y alias**.
 *
 * El portal no tiene un modelo "serie → temporadas": **cada temporada es un asset
 * independiente** y el número va en el nombre (`Un Show Más T1`, `Un show más T2`).
 * Además la misma serie puede venir partida entre dos títulos: unas temporadas con el
 * nombre doblado (`Historias corrientes`) y otras con el original (`Un Show Más`), pero
 * siempre con el **mismo `alias`** (`Regular Show S1` / `Regular Show Season2`).
 *
 * Por eso cada asset aporta dos tokens (nombre base y alias base) y se unen entre sí:
 * `Un Show Más T1` y `Historias corrientes T3` acaban en el mismo grupo porque ambos
 * comparten el token `regular show`.
 *
 * Este agrupador es la única fuente de verdad para:
 *  - la búsqueda (#4): una ficha por serie, no una por temporada ni por título alterno;
 *  - la ficha de detalle (#5): selector de temporada con todas las variantes;
 *  - el catálogo (#3): agrupar los assets del home por serie.
 */
public object SeriesGrouping {

    private val Acentos = mapOf(
        'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a', 'ã' to 'a',
        'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
        'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o', 'õ' to 'o',
        'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u',
        'ñ' to 'n', 'ç' to 'c',
    )

    /**
     * Temporada detectada al final del título: `T2`, `S2`, `Season2`, `Temporada 2`,
     * `Temp.2`. En el portal conviven todas esas variantes.
     */
    private val Temporada = Regex("""(?:\s+|^)(?:temporada|temp\.?|season|t|s)\s*(\d{1,2})$""")

    /** El mismo sufijo pero sobre el título original (conserva mayúsculas y acentos). */
    private val TemporadaOriginal = Regex(
        """(?:\s+|^)(?:temporada|temp\.?|season|t|s)\s*(\d{1,2})$""",
        RegexOption.IGNORE_CASE,
    )

    /** Minúsculas, sin acentos y con espacios colapsados. */
    public fun normalize(text: String): String = buildString(text.length) {
        text.lowercase().forEach { c ->
            append(Acentos[c] ?: c)
        }
    }.replace(Regex("""\s+"""), " ").trim()

    /** Número de temporada del nombre; 1 si no lleva sufijo. */
    public fun seasonNumber(title: String): Int =
        Temporada.find(normalize(title))?.groupValues?.get(1)?.toIntOrNull() ?: 1

    /**
     * Título base: quita el sufijo de temporada y normaliza.
     * `Un Show Más T1` → `un show mas` · `Regular Show Season2` → `regular show`
     */
    public fun baseName(title: String): String {
        val normalizado = normalize(title)
        val match = Temporada.find(normalizado) ?: return normalizado
        return normalizado.substring(0, match.range.first).trim().ifEmpty { normalizado }
    }

    /**
     * Título original sin el sufijo de temporada, para consultar el buscador y para
     * pintar la tarjeta. `Un Show Más T1` → `Un Show Más`.
     */
    public fun searchTitle(title: String): String {
        val limpio = title.trim()
        val match = TemporadaOriginal.find(limpio) ?: return limpio
        return limpio.substring(0, match.range.first).trim().ifEmpty { limpio }
    }

    /**
     * Agrupa una lista de series (type="0" / teleplay) por título base o alias.
     * Conserva el orden de aparición y ordena las temporadas por número.
     */
    public fun group(series: List<VodAsset>): List<SeriesGroup> {
        val assets = series.filter { it.isSeries }
        if (assets.isEmpty()) return emptyList()

        // Cada asset conecta su nombre base con su alias base (si lo tiene). Un asset
        // sin alias sigue entrando por el nombre, así que no se pierde nada.
        val tokens = ArrayList<List<String>>(assets.size)
        val union = UnionFind()
        assets.forEach { asset ->
            val propios = buildList {
                add(baseName(asset.name))
                asset.alias?.takeIf { it.isNotBlank() }?.let { add(baseName(it)) }
            }.filter { it.isNotEmpty() }.distinct()
            propios.forEach { union.add(it) }
            propios.drop(1).forEach { union.union(propios.first(), it) }
            tokens += propios
        }

        val porRaiz = LinkedHashMap<String, MutableList<VodAsset>>()
        assets.forEachIndexed { index, asset ->
            val raiz = tokens[index].firstOrNull()?.let { union.find(it) } ?: asset.contentId
            porRaiz.getOrPut(raiz) { mutableListOf() }.add(asset)
        }

        return porRaiz.map { (_, grupo) ->
            val temporadas = grupo
                .map { SeasonEntry(asset = it, number = seasonNumber(it.name)) }
                // Dos variantes pueden publicar la misma temporada (T2 duplicada): se
                // conserva la primera, que ya viene ordenada por el portal.
                .sortedBy { it.number }
                .distinctBy { it.number }
            SeriesGroup(
                baseName = baseName(grupo.first().name),
                seasons = temporadas,
            )
        }.sortedBy { it.baseName }
    }

    /**
     * Resultados de búsqueda listos para pintar: una ficha por serie (con todas las
     * temporadas encontradas unificadas) y una por película. El `title` ya no lleva
     * el sufijo `T1`/`S2`, porque la ficha representa la serie completa.
     *
     * Mantiene el orden en que el portal devolvió los resultados.
     */
    public fun searchResults(items: List<VodAsset>): List<SearchResult> {
        val unicos = items.distinctBy { it.contentId }
        if (unicos.isEmpty()) return emptyList()

        val grupos = group(unicos)
        val grupoPorId = HashMap<String, SeriesGroup>(unicos.size)
        grupos.forEach { grupo ->
            grupo.seasons.forEach { grupoPorId[it.asset.contentId] = grupo }
        }

        val devueltos = HashSet<String>()
        val salida = ArrayList<SearchResult>(unicos.size)
        unicos.forEach { asset ->
            if (!asset.isSeries) {
                if (devueltos.add("m:${asset.contentId}")) {
                    salida += SearchResult(asset = asset, title = asset.name)
                }
            } else {
                val grupo = grupoPorId[asset.contentId]
                if (grupo == null) {
                    if (devueltos.add("s:${asset.contentId}")) {
                        salida += SearchResult(asset = asset, title = searchTitle(asset.name))
                    }
                } else if (devueltos.add("g:${grupo.key}")) {
                    salida += SearchResult(asset = grupo.cover, title = grupo.displayName)
                }
            }
        }
        return salida
    }

    /** Todas las temporadas de una serie concreta, incluida la actual. */
    public fun seasonsOf(series: List<VodAsset>, asset: VodAsset): List<SeasonEntry> {
        val todos = (series + asset).distinctBy { it.contentId }
        return group(todos)
            .firstOrNull { grupo -> grupo.seasons.any { it.asset.contentId == asset.contentId } }
            ?.seasons
            .orEmpty()
    }
}

/** Un resultado de búsqueda: ficha única y título sin sufijo de temporada. */
public data class SearchResult(
    val asset: VodAsset,
    val title: String,
) {
    /** Asset con el nombre limpio para las tarjetas que pintan el título sobre la imagen. */
    public val cardAsset: VodAsset
        get() = if (title == asset.name) asset else asset.copy(name = title)
}

/** Una temporada dentro de una serie. */
public data class SeasonEntry(
    val asset: VodAsset,
    val number: Int,
) {
    public val label: String get() = "T$number"
}

/** Serie agrupada (una o más temporadas). */
public data class SeriesGroup(
    val baseName: String,
    val seasons: List<SeasonEntry>,
) {
    public val key: String get() = "s:$baseName"
    public val cover: VodAsset get() = seasons.first().asset
    public val isMultiSeason: Boolean get() = seasons.size > 1

    /** Nombre visible de la serie, sin `T1`/`S1`/`Season 1`. */
    public val displayName: String get() = SeriesGrouping.searchTitle(seasons.first().asset.name)
}

/** Unión simple de tokens (nombre base y alias) para no repetir la misma serie. */
private class UnionFind {

    private val padre = HashMap<String, String>()

    fun add(token: String) {
        padre.getOrPut(token) { token }
    }

    fun find(token: String): String {
        var raiz = token
        while (padre[raiz] != raiz) raiz = padre.getValue(raiz)
        // Compresión de caminos: la búsqueda queda casi O(1) en las siguientes pasadas.
        var actual = token
        while (padre[actual] != actual) {
            val siguiente = padre.getValue(actual)
            padre[actual] = raiz
            actual = siguiente
        }
        return raiz
    }

    fun union(a: String, b: String) {
        val raizA = find(a)
        val raizB = find(b)
        if (raizA != raizB) padre[raizB] = raizA
    }
}
