package com.xuperclean.desktop.tools

import com.xuperclean.core.StreamBuilder
import com.xuperclean.core.model.Channel
import com.xuperclean.core.model.CdnEntry
import com.xuperclean.core.model.MovieVariant
import com.xuperclean.core.model.SearchItem
import com.xuperclean.desktop.data.XuperRepository
import com.xuperclean.desktop.net.NetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Request

/**
 * Diagnóstico del protocolo contra el portal real — Lote 0 (bugs 1, 2, 3, 4, 5).
 *
 *   A) película de búsqueda: itemData + ticket + variante
 *   B) serie: lista de episodios, path de la app (falla) y path correcto
 *   C) categorías: ¿se pueden paginar las filas del home?
 *   D) directo: CDN por variante + continuidad real de la playlist
 *   E) DNS de los tres hosts implicados (portal, playlist, segmentos)
 *
 * Uso: gradlew :desktop:diagnose
 */

private const val VOD_ROOT = 87026L

fun main() = runBlocking {
    seccion("A) PELÍCULA DESDE BÚSQUEDA") { pelicula("contra el huracan") }
    seccion("B) SERIE DESDE BÚSQUEDA") { serie("un show mas") }
    seccion("C) CATEGORÍAS Y PAGINACIÓN") { categorias() }
    seccion("C2) ¿HAY CATÁLOGO COMPLETO POR BÚSQUEDA?") { catalogo() }
    seccion("D) DIRECTO: CDN Y CONTINUIDAD") { directo() }
    seccion("E) DNS") { dns() }
}

// ---------------------------------------------------------------- A) película

private suspend fun pelicula(consulta: String) {
    val item = buscar(consulta).firstOrNull { it.type == "1" }
        ?: run { println("  sin película para '$consulta'"); return }
    println("  película: ${item.name} (${item.contentId}) tags=${item.tags}")

    val detalle = runCatching { XuperRepository.itemData(item.contentId, "1") }
    detalle.onSuccess { println("  itemData(type=1) OK: ${it.name} score=${it.score} dur=${it.duration}") }
        .onFailure { println("  itemData(type=1) FALLA: ${it.message}") }

    // path real de la app: columnId=0, seriesContentId=""
    probarTicket("película", XuperRepository.startPlayVod(item.contentId, "1"))
}

// ---------------------------------------------------------------- B) serie

private suspend fun serie(consulta: String) {
    val items = buscar(consulta)
    items.groupBy { baseDe(it.name) }.forEach { (base, temporadas) ->
        println("  grupo '${base}': ${temporadas.size} temporada(s)")
        temporadas.forEach { t ->
            println("    id=${t.contentId} name=${t.name} volumn=${t.volumnCount} upd=${t.updateCount}")
        }
    }

    val temporada = items.firstOrNull { it.type == "0" } ?: run { println("  sin serie"); return }
    println("  -> temporada ${temporada.name} (${temporada.contentId})")

    val detalle = XuperRepository.itemData(temporada.contentId, "0")
    println("  episodios=${detalle.simpleProgramList.size} sameSeason=${detalle.sameSeasonSeriesList.size}")
    println("  sameSeasonSeriesList:")
    detalle.sameSeasonSeriesList.take(12).forEach {
        println("    id=${it.contentId} n=${it.seriesNumber} name=${it.name} quality=${it.quality}")
    }
    println("  numeración (contentId, seriesNumber, name):")
    detalle.simpleProgramList.take(30).forEach {
        println("    ${it.contentId}  n=${it.seriesNumber}  ${it.name}")
    }
    val numeros = detalle.simpleProgramList.mapNotNull { it.seriesNumber }
    println("  seriesNumber distintos=${numeros.distinct().size} min=${numeros.minOrNull()} max=${numeros.maxOrNull()}")

    // 1) path que usa hoy el botón de la ficha: type=0, columnId=0, sin temporada
    val malo = runCatching { XuperRepository.startPlayVod(temporada.contentId, "0") }
    malo.onSuccess { println("  [APP] ticket type=0 columnId=0 -> OK, ${it.episodeList.size} episodios") }
        .onFailure { println("  [APP] ticket type=0 columnId=0 -> FALLA: ${it.message}") }

    // 2) path correcto: episodio + temporada + columnId raíz
    detalle.simpleProgramList.take(2).forEach { ep ->
        println("  [CORRECTO] ep n=${ep.seriesNumber} (${ep.contentId})")
        probarTicket(
            "  ",
            XuperRepository.startPlayVod(
                contentId = ep.contentId,
                type = "0",
                columnId = VOD_ROOT,
                seriesContentId = temporada.contentId,
                episodeNumbers = listOf(ep.seriesNumber ?: 1),
            ),
            indent = "    ",
        )
    }
}

// ---------------------------------------------------------------- C) categorías

private suspend fun categorias() {
    // 1) ¿cada fila del home se puede pedir por su columnCode con paginación?
    val filas = XuperRepository.homeRows().recommendList
    filas.take(4).forEach { fila ->
        val code = fila.code ?: return@forEach
        runCatching {
            val pag2 = XuperRepository.nextColumnsPorCodigo(code, pageNum = 1, pageSize = 30)
            val n = pag2.recommendList.sumOf { it.assetList.size }
            println("  fila '${fila.name}' code=$code -> filas=${pag2.recommendList.size} assets=$n total=${pag2.totalSize}")
        }.onFailure { println("  fila '${fila.name}' code=$code -> ERROR ${it.message}") }
    }

    // 2) ¿el home crece con pageSize?
    listOf(10, 50, 200).forEach { size ->
        runCatching {
            val r = XuperRepository.nextColumnsPorCodigo("masnew_featured", 1, size)
            val n = r.recommendList.sumOf { it.assetList.size }
            println("  masnew_featured pageSize=$size -> filas=${r.recommendList.size} assets=$n total=${r.totalSize}")
        }.onFailure { println("  masnew_featured pageSize=$size -> ERROR ${it.message}") }
    }

    // 3) columnas VOD "free": ¿vacías de verdad?
    listOf(87027L, 87028L, 87029L).forEach { id ->
        runCatching {
            val c = XuperRepository.columnTree(id)
            println("  columnContents $id -> total=${c.totalSize} contenido=${c.contentList.size} hijos=${c.childColumnList.size}")
            c.contentList.take(3).forEach { println("     ${it.name} type=${it.type} tags=${it.tags}") }
        }.onFailure { println("  columnContents $id -> ERROR ${it.message}") }
    }

    // 4) columnas LIVE: ¿se puede pedir el contenido de una categoría?
    runCatching {
        val cats = XuperRepository.liveCategories()
        println("  LIVE árbol: ${cats.size} categorías")
        cats.take(10).forEach { println("     ${it.id} · ${it.name}") }
    }.onFailure { println("  LIVE árbol ERROR ${it.message}") }
    runCatching {
        val c = XuperRepository.columnTree(76206)
        println("  LIVE Deportes total=${c.totalSize} contenido=${c.contentList.size} hijos=${c.childColumnList.size}")
        println("     canales vía getLiveData: ${XuperRepository.liveChannelsPorColumna(76206).size}")
    }.onFailure { println("  LIVE Deportes ERROR ${it.message}") }
}

// ---------------------------------------------------------------- C2) catálogo

private suspend fun catalogo() {
    listOf("" to 100, "a" to 100, "e" to 100, "la" to 100).forEach { (q, size) ->
        runCatching {
            val r = XuperRepository.search(q, pageSize = size)
            val items = r.searchItemList.flatMap { it.itemList }
            println("  search('$q', pageSize=$size): grupos=${r.searchItemList.size} items=${items.size} " +
                "series=${items.count { it.type == "0" }} pelis=${items.count { it.type == "1" }}")
            items.take(5).forEach { println("     ${it.name} (type=${it.type})") }
        }.onFailure { println("  search('$q', pageSize=$size) -> ERROR ${it.message}") }
    }

    // ¿pagina de verdad?
    runCatching {
        val p1 = XuperRepository.search("a", pageNum = 1, pageSize = 50).searchItemList.flatMap { it.itemList }
        val p2 = XuperRepository.search("a", pageNum = 2, pageSize = 50).searchItemList.flatMap { it.itemList }
        val solapan = p1.map { it.contentId }.intersect(p2.map { it.contentId }.toSet()).size
        println("  paginación 'a': p1=${p1.size} p2=${p2.size} solapan=$solapan")
    }.onFailure { println("  paginación -> ERROR ${it.message}") }
}

// ---------------------------------------------------------------- D) directo
private suspend fun directo() {
    val canales = XuperRepository.liveChannels()
    val slb = XuperRepository.slbInfo()
    val liveCdns = slb.cdnList.filter { it.tag == "live" }
    println("  CDNs live=${liveCdns.size}: " + liveCdns.joinToString { "${it.cdnType}@${host(it)}" })

    canales.filter { (it.alias ?: it.name).uppercase() in setOf("ECDF", "GEN", "WIN SPORTS COL HD") }
        .take(2)
        .forEach { ch ->
            println()
            println("  canal ${ch.channelNumber} ${ch.alias ?: ch.name}")
            ch.liveAddressList.forEach { a ->
                val cdn = liveCdns.firstOrNull { it.cdnType == a.cdnType?.toString() }
                val auth = cdn?.urlList?.firstOrNull { it.tag == (a.tag ?: "free") }?.url
                    ?: cdn?.urlList?.firstOrNull()?.url
                if (cdn == null || auth == null) {
                    println("    variante cdn=${a.cdnType} sin CDN/auth")
                    return@forEach
                }
                val url = StreamBuilder.liveM3u8Url(cdn, a.playCode)
                val r = medirPlaylist(url, StreamBuilder.liveHeaders(auth, a.license ?: ""))
                println("    cdn=${a.cdnType} ${host(cdn)} -> $r")
            }
        }

    // continuidad: ¿la ventana avanza en tiempo real o da saltos? (en cada CDN sano)
    val ch = canales.firstOrNull { (it.alias ?: it.name).equals("ECDF", true) } ?: return
    val addr = ch.liveAddressList.firstOrNull { it.cdnType?.toString() == "4" } ?: ch.liveAddressList.first()

    liveCdns.filter { it.cdnType == "4" }.forEach { cdn ->
        val auth = cdn.urlList.firstOrNull { it.tag == (addr.tag ?: "free") }?.url
            ?: cdn.urlList.firstOrNull()?.url ?: return@forEach
        val headers = StreamBuilder.liveHeaders(auth, addr.license ?: "")
        val url = StreamBuilder.liveM3u8Url(cdn, addr.playCode)

        println()
        println("  continuidad HLS en ${host(cdn)}, 8 lecturas cada 3 s:")
        var anterior: Triple<Long, Long, Long>? = null
        var huecos = 0
        var estancada = 0
        val t0 = System.currentTimeMillis()
        repeat(8) { i ->
            val info = leerVentana(url, headers)
            val ahora = System.currentTimeMillis()
            if (info == null) {
                println("    [$i] sin playlist")
            } else {
                val (seq, rd) = info
                val detalle = anterior?.let { (_, pr, pw) ->
                    val dRd = rd - pr
                    val dWall = ahora - pw
                    when {
                        dRd > dWall + 3000 -> { huecos++; "  << HUECO de ${dRd - dWall}ms" }
                        dRd < dWall - 3000 -> { estancada++; "  << ESTANCADA (${dWall - dRd}ms)" }
                        else -> ""
                    }.let { "Δrd=${dRd}ms Δwall=${dWall}ms$it" }
                } ?: ""
                println("    [$i] t=${(ahora - t0) / 1000}s seq=$seq rd=$rd $detalle")
                anterior = Triple(seq, rd, ahora)
            }
            delay(3000)
        }
        println("    => huecos=$huecos estancada=$estancada de 7 intervalos")
    }

    // ¿cuántos canales solo tienen la variante del CDN muerto (1)?
    val solo1 = canales.count { c -> c.liveAddressList.isNotEmpty() && c.liveAddressList.all { it.cdnType?.toString() == "1" } }
    val con4 = canales.count { c -> c.liveAddressList.any { it.cdnType?.toString() == "4" } }
    println()
    println("  canales totales=${canales.size} con variante cdn=4: $con4 · solo cdn=1 (muerto): $solo1")

    // descarga de 10 segmentos consecutivos
    println()
    println("  descarga de 10 segmentos consecutivos:")
    val cdnSeg = liveCdns.first { it.cdnType == "4" }
    val authSeg = cdnSeg.urlList.firstOrNull { it.tag == (addr.tag ?: "free") }?.url
        ?: cdnSeg.urlList.first().url
    val urlSeg = StreamBuilder.liveM3u8Url(cdnSeg, addr.playCode)
    val hdrSeg = StreamBuilder.liveHeaders(authSeg, addr.license ?: "")
    val ventana = leerSegmentos(urlSeg, hdrSeg)
    ventana.take(10).forEachIndexed { i, seg ->
        val t = System.currentTimeMillis()
        val r = runCatching {
            NetProvider.okHttpClient.newCall(Request.Builder().url(seg).apply { hdrSeg.forEach { (k, v) -> header(k, v) } }.build())
                .execute().use { resp -> resp.code to (resp.body?.bytes()?.size ?: 0) }
        }.getOrElse { -1 to 0 }
        println("    seg[$i] ${System.currentTimeMillis() - t}ms http=${r.first} bytes=${r.second}")
    }
}

private suspend fun leerVentana(url: String, headers: Map<String, String>): Pair<Long, Long>? {
    val txt = runCatching {
        NetProvider.okHttpClient.newCall(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build())
            .execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrNull() ?: return null
    val seq = Regex("MEDIA-SEQUENCE:(\\d+)").find(txt)?.groupValues?.get(1)?.toLongOrNull() ?: return null
    val rd = Regex("rd=(\\d+)").findAll(txt).lastOrNull()?.groupValues?.get(1)?.toLongOrNull() ?: return null
    return seq to rd
}

private suspend fun leerSegmentos(url: String, headers: Map<String, String>): List<String> = runCatching {
    NetProvider.okHttpClient.newCall(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build())
        .execute().use { r -> r.body?.string()?.lineSequence()?.filter { it.startsWith("http") }?.toList() ?: emptyList() }
}.getOrDefault(emptyList())

private fun medirPlaylist(url: String, headers: Map<String, String>): String {
    val t0 = System.currentTimeMillis()
    val texto = runCatching {
        NetProvider.okHttpClient.newCall(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build())
            .execute().use { r -> if (!r.isSuccessful) return "HTTP ${r.code}"; r.body?.string() ?: return "cuerpo vacío" }
    }.getOrElse { return "ERROR ${it.message}" }
    val ms = System.currentTimeMillis() - t0
    val segs = texto.lineSequence().filter { it.startsWith("http") }.take(4).toList()
    if (segs.isEmpty()) return "playlist sin segmentos (${ms}ms)"
    val tiempos = segs.map { s ->
        val t = System.currentTimeMillis()
        val ok = runCatching {
            NetProvider.okHttpClient.newCall(Request.Builder().url(s).build()).execute().use { r ->
                r.isSuccessful && (r.body?.bytes()?.size ?: 0) > 1000
            }
        }.getOrDefault(false)
        (System.currentTimeMillis() - t) to ok
    }
    return "playlist ${ms}ms ok=${tiempos.count { it.second }}/${segs.size} peor=${tiempos.maxOf { it.first }}ms"
}

// ---------------------------------------------------------------- E) DNS

private fun dns() {
    val hosts = listOf(
        "portal" to "msandroid-*.com",
        "playlist" to "niguof.vynbszicd.com",
        "segmentos" to "ccbab.faolmbghu.com",
    )
    hosts.forEach { (etiqueta, h) ->
        if (h.contains("*")) return@forEach
        repeat(2) { i ->
            val t = System.currentTimeMillis()
            val ips = runCatching { NetProvider.dns.lookup(h).map { it.hostAddress } }.getOrElse { listOf("ERROR ${it.message}") }
            println("  [$i] $etiqueta $h -> ${System.currentTimeMillis() - t}ms $ips")
        }
    }
}

// ---------------------------------------------------------------- helpers

private suspend fun probarTicket(etiqueta: String, ticket: com.xuperclean.core.model.PlayTicket, indent: String = "  ") {
    println("${indent}ticket $etiqueta: episodeList=${ticket.episodeList.size} freeFlag=${ticket.vodFreeFlag} freeCount=${ticket.vodFreeCount}")
    val cdn = StreamBuilder.pickVodCdn(XuperRepository.slbInfo().cdnList)
    if (cdn == null) { println("${indent}sin CDN VOD"); return }
    ticket.episodeList.forEach { ep ->
        println("${indent}  episodio ${ep.episodeNumber}: variantes=${ep.variants.size} subtítulos=${ep.subtitleList.map { s -> s.language }}")
        ep.variants.forEach { v -> println("$indent    ${probarVariante(v, cdn)}") }
    }
}

private fun probarVariante(v: MovieVariant, cdn: CdnEntry): String {
    val license = StreamBuilder.pickLicense(v) ?: ""
    val url = StreamBuilder.vodMediaTsUrl(cdn, v.contentId, "diag${System.nanoTime()}", "")
    val req = Request.Builder()
        .url(url)
        .header("User-Agent", "Ranger/4.9.4-17294ac0")
        .header("Range", "bytes=0-262143")
        .apply { if (license.isNotEmpty()) header("Content-License", license) }
        .build()
    val etiqueta = "${v.quality}/${v.encodeFormat}/${v.videoFormat} audio=${v.audioInfo} lic=${license.isNotEmpty()}"
    return runCatching {
        NetProvider.okHttpClient.newCall(req).execute().use { r ->
            val bytes = r.body?.bytes() ?: ByteArray(0)
            val sync = bytes.firstOrNull()?.toInt()?.and(0xFF)
            "$etiqueta -> ${r.code} ${r.header("Content-Type")} ${bytes.size}B sync=$sync"
        }
    }.getOrElse { "$etiqueta -> ERROR ${it.message}" }
}

private suspend fun buscar(consulta: String): List<SearchItem> =
    XuperRepository.search(consulta).searchItemList.flatMap { it.itemList }

private fun baseDe(nombre: String): String =
    nombre.replace(Regex("\\s+[Tt]\\d+\\s*$"), "").trim()

private fun host(cdn: CdnEntry): String = cdn.mainAddr.removePrefix("http://").substringBefore('/')

private fun seccion(titulo: String, bloque: suspend () -> Unit) {
    runBlocking {
        println()
        println("=== $titulo ===")
        runCatching { bloque() }.onFailure { println("  SECCIÓN FALLÓ: ${it.message}") }
    }
}
