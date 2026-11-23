package com.xuperclean.desktop.tools

/** Utilidad de desarrollo: imprime una URL VOD real para pruebas manuales de mpv. */
fun main(): Unit = kotlinx.coroutines.runBlocking {
    val repo = com.xuperclean.desktop.data.XuperRepository
    val home = repo.homeRows()
    val asset = home.recommendList.first { it.assetList.isNotEmpty() }.assetList.first()
    println("TITULO=" + asset.name)
    val ticket = repo.startPlayVod(contentId = asset.contentId, type = asset.type ?: "1")
    val variant = ticket.episodeList.first().variants.first()
    val stream = repo.vodStream(variant, asset.name)
    println("URL=" + (stream?.url ?: "SIN STREAM"))
    val headers = stream?.headers ?: emptyMap()
    println("HEADERS=" + headers.entries.joinToString(",") { "${it.key}: ${it.value}" })
}
