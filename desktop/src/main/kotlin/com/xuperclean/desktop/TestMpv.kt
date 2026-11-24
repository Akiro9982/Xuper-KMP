package com.xuperclean.desktop

import com.xuperclean.desktop.player.MpvPlayer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Prueba aislada del reproductor: reproduce un VOD real en una ventana AWT,
 * verifica por estado (script Lua) que mpv decodifica, aplica seek y pausa.
 */
fun main() {
    val mpvPath = MpvPlayer.locate()
    println("mpv: $mpvPath")
    if (mpvPath == null) {
        System.err.println("mpv no encontrado")
        kotlin.system.exitProcess(1)
    }

    val frame = java.awt.Frame("test mpv").apply {
        size = java.awt.Dimension(960, 540)
        isVisible = true
    }
    Thread.sleep(1000)

    val canvas = java.awt.Canvas()
    frame.add(canvas)
    frame.revalidate()
    Thread.sleep(800)

    val hwnd = MpvPlayer.hwndOf(canvas)
    println("HWND: 0x${java.lang.Long.toHexString(hwnd)}")

    val url = kotlinx.coroutines.runBlocking {
        val repo = com.xuperclean.desktop.data.XuperRepository
        val home = repo.homeRows()
        val asset = home.recommendList.first { it.assetList.isNotEmpty() }.assetList.first()
        println("titulo: ${asset.name}")
        val ticket = repo.startPlayVod(contentId = asset.contentId, type = asset.type ?: "1")
        val variant = ticket.episodeList.first().variants.first()
        val stream = repo.vodStream(variant, asset.name)
        stream?.url ?: error("sin stream")
    }
    println("url: ${url.take(65)}...")

    var pos = 0.0
    var dur = 0.0
    var pausado = false
    var cache = 0.0
    val cargado = CountDownLatch(1)
    val finalizado = CountDownLatch(1)

    val player = MpvPlayer(mpvPath, object : MpvPlayer.HostWindow {
        override fun hwnd(): Long = hwnd
    }).apply {
        onProgress = { p, d -> pos = p; if (d > 0) dur = d }
        onPausedChange = { pausado = it }
        onBuffer = { cache = it }
        onLoaded = { cargado.countDown() }
        onEnd = { finalizado.countDown() }
    }

    player.start(url, mapOf("User-Agent" to "Ranger/4.9.4-17294ac0"), "test")

    println("cargado: ${cargado.await(20, TimeUnit.SECONDS)}")

    Thread.sleep(5000)
    val pos1 = pos
    println("pos1: $pos1  dur: $dur")

    Thread.sleep(2500)
    println("pos2: $pos  avance: ${"%.2f".format(pos - pos1)} s")
    println("buffer (fin descargado): ${"%.2f".format(cache)} s")

    println("seek -> 120 s")
    player.seekTo(120.0)
    Thread.sleep(2000)
    println("pos tras seek: $pos")

    println("pausa")
    player.togglePause()
    Thread.sleep(1200)
    val posPausa = pos
    println("pausado: $pausado pos: $posPausa")
    Thread.sleep(1200)
    println("pos en pausa tras 1.2 s: $pos (delta ${"%.2f".format(pos - posPausa)})")

    player.togglePause()
    Thread.sleep(1200)
    println("reanudado: pausado=$pausado pos=$pos")

    player.stop()
    Thread.sleep(300)
    frame.dispose()

    println("PRUEBA MPV OK")
    kotlin.system.exitProcess(0)
}
