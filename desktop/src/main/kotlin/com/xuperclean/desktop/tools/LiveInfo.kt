package com.xuperclean.desktop.tools

import com.xuperclean.desktop.data.XuperRepository
import kotlinx.coroutines.runBlocking

/** Inspección rápida del catálogo en vivo (campos disponibles por canal). */
fun main() = runBlocking {
    val canales = XuperRepository.liveChannels()
    println("canales: ${canales.size}")
    canales.take(8).forEach { c ->
        println(
            "num=${c.channelNumber} code=${c.channelCode} name=${c.alias ?: c.name} " +
                "quality=${c.quality} posterUrl=${c.posterUrl} posters=${c.posterList.size} " +
                "tags=${c.tags}",
        )
    }
    val conPoster = canales.count { !it.posterUrl.isNullOrBlank() }
    val conLista = canales.count { it.posterList.isNotEmpty() }
    println("con posterUrl: $conPoster · con posterList: $conLista")
}
