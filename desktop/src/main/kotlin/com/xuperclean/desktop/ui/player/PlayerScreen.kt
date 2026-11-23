package com.xuperclean.desktop.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.xuperclean.core.SubtitleTrack
import com.xuperclean.core.model.VodAsset
import com.xuperclean.desktop.data.VodStream
import com.xuperclean.desktop.data.XuperRepository
import com.xuperclean.desktop.player.AjusteImagen
import com.xuperclean.desktop.player.MpvPlayer
import com.xuperclean.desktop.player.MpvSurface
import com.xuperclean.desktop.ui.theme.XuperTheme
import kotlin.math.roundToInt

import androidx.compose.animation.Animatable
import androidx.compose.animation.tween

/** Qué reproducir (mismo contrato que móvil). */
sealed interface PlayerPayload {    val title: String

    data class Live(val channel: com.xuperclean.core.model.Channel) : PlayerPayload {
        override val title: String get() = channel.alias ?: channel.name
    }

    data class Vod(val asset: VodAsset) : PlayerPayload {
        override val title: String get() = asset.name
    }

    data class Episode(
        val asset: VodAsset,
        val episode: com.xuperclean.core.model.EpisodeInfo,
    ) : PlayerPayload {
        override val title: String get() = episode.name ?: asset.name
    }
}

/**
 * Reproductor de escritorio: mpv embebido (HWND) + controles Compose debajo del vídeo.
 *
 * El vídeo es un componente pesado AWT, así que Compose no puede pintar encima:
 * la barra de control vive fuera del área de vídeo (barra superior + barra inferior).
 *
 * Teclado: espacio = pausa · ←/→ = ±10 s (VOD) · Esc = volver.
 */
@Composable
fun PlayerScreen(payload: PlayerPayload, onClose: () -> Unit) {
    var resolviendo by remember(payload) { mutableStateOf(true) }
    var errorMsg by remember(payload) { mutableStateOf<String?>(null) }
    var streamUrl by remember(payload) { mutableStateOf<String?>(null) }
    var streamHeaders by remember(payload) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLive by remember(payload) { mutableStateOf(false) }
    var alternativas by remember(payload) { mutableStateOf<List<Pair<String, Map<String, String>>>>(emptyList()) }
    var fuentes by remember(payload) { mutableStateOf<List<VodStream>>(emptyList()) }
    var fuenteActual by remember(payload) { mutableIntStateOf(0) }
    var audios by remember(payload) {
        mutableStateOf<List<com.xuperclean.core.AudioTrack>>(emptyList())
    }
    var idiomaAudio by remember(payload) {
        mutableStateOf(com.xuperclean.desktop.data.DesktopWatchStore.audioLanguage)
    }
    var subtitulos by remember(payload) { mutableStateOf<List<SubtitleTrack>>(emptyList()) }
    var pistaSub by remember(payload) { mutableStateOf<String?>(null) }
    var escalaSub by remember(payload) { mutableStateOf(1f) }
    var verAjustes by remember { mutableStateOf(false) }
    var reanudarEn by remember(payload) { mutableStateOf(0.0) }
    var episodioActual by remember(payload) {
        mutableStateOf<com.xuperclean.core.model.EpisodeInfo?>(null)
    }
    var ultimoGuardado by remember(payload) { mutableStateOf(0.0) }

    /** Guarda el avance local (solo VOD, nunca live). */
    fun guardarProgreso(posMs: Long, durMs: Long) {
        if (isLive || durMs <= 0) return
        val asset = when (val k = payload) {
            is PlayerPayload.Vod -> k.asset
            is PlayerPayload.Episode -> k.asset
            is PlayerPayload.Live -> return
        }
        val ep = episodioActual
        val p = com.xuperclean.core.WatchProgress(
            contentId = ep?.contentId ?: asset.contentId,
            assetId = asset.contentId,
            name = asset.name,
            type = if (asset.isSeries) "0" else "1",
            isSeries = asset.isSeries,
            posterUrl = asset.posterList.firstOrNull()?.fileUrl,
            episodeId = ep?.contentId,
            episodeNumber = ep?.seriesNumber,
            episodeName = ep?.name,
            seasonNumber = if (asset.isSeries) {
                com.xuperclean.core.SeriesGrouping.seasonNumber(asset.name)
            } else null,
            positionMs = posMs,
            durationMs = durMs,
            updatedAt = System.currentTimeMillis(),
        )
        if (com.xuperclean.core.WatchProgress.isResumable(posMs, durMs)) {
            com.xuperclean.desktop.data.DesktopWatchStore.save(p)
        } else if (posMs.toDouble() / durMs >= 0.95) {
            com.xuperclean.desktop.data.DesktopWatchStore.remove(asset.contentId)
        }
    }

    var posicion by remember(payload) { mutableStateOf(0.0) }
    var duracion by remember(payload) { mutableStateOf(0.0) }
    var pausado by remember(payload) { mutableStateOf(false) }
    var arrastrando by remember(payload) { mutableStateOf(false) }
    var volumen by remember { mutableStateOf(100) }
    var ajusteImagen by remember { mutableStateOf(AjusteImagen.AJUSTAR) }

    LaunchedEffect(payload) {
        try {
            when (val kind = payload) {
                is PlayerPayload.Live -> {
                    val opciones = XuperRepository.liveStreams(kind.channel)
                    val primera = opciones.firstOrNull()
                    if (primera == null) errorMsg = "Canal no disponible"
                    else {
                        streamUrl = primera.url
                        streamHeaders = primera.headers
                        alternativas = opciones.drop(1).map { it.url to it.headers }
                        isLive = true
                    }
                }
                is PlayerPayload.Vod -> {
                    // Una serie nunca se reproduce por el path de película (#2).
                    val saved = com.xuperclean.desktop.data.DesktopWatchStore.find(kind.asset.contentId)
                    val ticket = if (kind.asset.isSeries) {
                        val full = XuperRepository.itemData(kind.asset.contentId, "0")
                        // Reanudar el capítulo guardado si sigue existiendo (#8).
                        val primero = saved?.episodeId
                            ?.let { id -> full.simpleProgramList.firstOrNull { it.contentId == id } }
                            ?: full.simpleProgramList.firstOrNull()
                            ?: error("La serie no tiene episodios")
                        episodioActual = primero
                        XuperRepository.startPlayVod(
                            contentId = primero.contentId,
                            type = "0",
                            columnId = 87026,
                            seriesContentId = kind.asset.contentId,
                            episodeNumbers = listOf(primero.seriesNumber ?: 1),
                        )
                    } else {
                        XuperRepository.startPlayVod(kind.asset.contentId, kind.asset.type ?: "1")
                    }
                    if (saved != null) reanudarEn = saved.positionMs / 1000.0
                    val opciones = XuperRepository.vodStreams(ticket, payload.title)
                    val primera = opciones.firstOrNull()
                    if (primera == null) errorMsg = "Contenido no disponible"
                    else {
                        fuentes = opciones
                        audios = XuperRepository.audio(ticket)
                        subtitulos = XuperRepository.subtitles(ticket)
                        streamUrl = primera.url
                        streamHeaders = primera.headers
                        alternativas = opciones.drop(1).map { it.url to it.headers }
                    }
                }
                is PlayerPayload.Episode -> {
                    episodioActual = kind.episode
                    val saved = com.xuperclean.desktop.data.DesktopWatchStore
                        .findEpisode(kind.episode.contentId)
                    if (saved != null) reanudarEn = saved.positionMs / 1000.0
                    val ticket = XuperRepository.startPlayVod(
                        contentId = kind.episode.contentId,
                        type = "0",
                        columnId = 87026,
                        seriesContentId = kind.asset.contentId,
                        episodeNumbers = listOf(kind.episode.seriesNumber ?: 1),
                    )
                    val opciones = XuperRepository.vodStreams(ticket, payload.title)
                    val primera = opciones.firstOrNull()
                    if (primera == null) errorMsg = "Episodio no disponible"
                    else {
                        fuentes = opciones
                        audios = XuperRepository.audio(ticket)
                        subtitulos = XuperRepository.subtitles(ticket)
                        streamUrl = primera.url
                        streamHeaders = primera.headers
                        alternativas = opciones.drop(1).map { it.url to it.headers }
                    }
                }
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "Error resolviendo el stream"
        } finally {
            resolviendo = false
        }
    }

    val mpvIntentos = remember { mutableIntStateOf(0) }
    val mpvPath = remember(mpvIntentos.intValue) { MpvPlayer.locate() }
    var mpv by remember { mutableStateOf<MpvPlayer?>(null) }
    var hwnd by remember { mutableStateOf(0L) }
    var terminado by remember(payload) { mutableStateOf(false) }

    /** Cambia el encuadre sin reiniciar la reproducción. */
    fun cambiarImagen(modo: AjusteImagen) {
        ajusteImagen = modo
        mpv?.aplicarImagen(modo)
    }

    /** Aplica la pista de subtítulo elegida (o la apaga) y el tamaño. */
    fun aplicarSubtitulo(p: MpvPlayer) {
        val track = subtitulos.firstOrNull { it.language == pistaSub }
        if (track == null) p.disableSubtitles()
        else p.setSubtitle(track.url, track.language, track.label)
        p.setSubtitleScale(escalaSub)
    }

    /** Aplica el idioma de audio elegido; el contenedor trae todas las pistas. */
    fun aplicarAudio(p: MpvPlayer, codigo: String) {
        p.setAudioLanguage(com.xuperclean.core.AudioTracks.preferenceChain(codigo))
    }

    /** Cambia de variante conservando la posición (#7). */
    fun aplicarFuente(indice: Int) {
        val fuente = fuentes.getOrNull(indice) ?: return
        reanudarEn = posicion
        mpv?.stop()
        mpv = null
        terminado = false
        resolviendo = true
        errorMsg = null
        streamUrl = fuente.url
        streamHeaders = fuente.headers
        fuenteActual = indice
        alternativas = fuentes.filterIndexed { i, _ -> i != indice }.map { it.url to it.headers }
    }

    DisposableEffect(payload) {
        onDispose {
            guardarProgreso((posicion * 1000).toLong(), (duracion * 1000).toLong())
            mpv?.stop()
        }
    }

    // arrancar mpv cuando haya URL + HWND + binario
    LaunchedEffect(streamUrl, hwnd, mpvPath) {
        if (streamUrl == null || hwnd == 0L || mpvPath == null || terminado) return@LaunchedEffect
        if (mpv?.isRunning == true) return@LaunchedEffect
        val player = MpvPlayer(mpvPath, object : MpvPlayer.HostWindow {
            override fun hwnd(): Long = hwnd
        })
        // El IPC de mpv puede tardar un instante en aceptar órdenes tras cargar:
        // reintentar el salto en segundo plano en vez de perder la posición.
        fun seekConReintento(p: MpvPlayer, objetivo: Double) {
            if (p.seekTo(objetivo)) return
            Thread {
                repeat(12) {
                    try { Thread.sleep(250) } catch (_: InterruptedException) { return@Thread }
                    if (!p.isRunning) return@Thread
                    if (p.seekTo(objetivo)) return@Thread
                }
            }.apply { isDaemon = true; name = "mpv-seek-retry" }.start()
        }
        player.onProgress = { pos, dur ->
            if (!arrastrando) posicion = pos
            if (dur > 0) duracion = dur
            // Autoguardado cada 15 s para no perder el avance si se cierra de golpe.
            if (!isLive && dur > 0 && kotlin.math.abs(pos - ultimoGuardado) >= 15.0) {
                ultimoGuardado = pos
                guardarProgreso((pos * 1000).toLong(), (dur * 1000).toLong())
            }
        }
        player.onPausedChange = { pausado = it }
        player.onLoaded = {
            resolviendo = false
            val objetivo = reanudarEn
            if (objetivo > 0.0) {
                seekConReintento(player, objetivo)
                reanudarEn = 0.0
            }
            aplicarSubtitulo(player)
            aplicarAudio(player, idiomaAudio)
        }
        fun tryNext(): Boolean {
            val siguiente = alternativas.firstOrNull() ?: return false
            alternativas = alternativas.drop(1)
            player.stop()
            mpv = null
            // Un fallo de CDN no debe reiniciar la película: se retoma donde iba (#1).
            // Si la nueva fuente murió antes de cargar (posición 0), se conserva el
            // punto de reanudación que ya teníamos.
            if (!isLive && posicion > 0.0) reanudarEn = posicion
            // La fuente en reproducción puede no ser la elegida: reflejarlo en ajustes.
            val indice = fuentes.indexOfFirst { it.url == siguiente.first }
            if (indice >= 0) fuenteActual = indice
            streamUrl = siguiente.first
            streamHeaders = siguiente.second
            resolviendo = true
            errorMsg = null
            return true
        }

        player.onEnd = {
            // En directo un EOF suele ser un corte del CDN: probar la siguiente fuente.
            if (isLive && tryNext()) {
                // reintentando
            } else {
                terminado = true
                onClose()
            }
        }
        player.onError = {
            if (!tryNext()) {
                terminado = true
                resolviendo = false
                errorMsg = "El stream se detuvo o no está disponible."
            }
        }
        val desde = reanudarEn
        player.start(
            streamUrl!!,
            streamHeaders,
            payload.title,
            desde,
            ajusteImagen,
            com.xuperclean.core.AudioTracks.preferenceChain(idiomaAudio),
        )
        player.setVolume(volumen)
        mpv = player
    }

    // El teclado solo llega a onPreviewKeyEvent si el foco está dentro de este subárbol:
    // el reproductor se queda con el foco al abrirse.
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(foco)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Spacebar, Key.Enter -> { mpv?.togglePause(); true }
                    Key.DirectionLeft -> { if (!isLive) mpv?.seekBy(-10); true }
                    Key.DirectionRight -> { if (!isLive) mpv?.seekBy(10); true }
                    Key.Escape -> { onClose(); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        // --- barra superior
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                payload.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (audios.size > 1 || subtitulos.isNotEmpty()) {
                IconButton(onClick = { verAjustes = true }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Ajustes de reproducción",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (isLive) {
                Text(
                    "● EN VIVO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }

        // --- vídeo (componente pesado: sin Compose encima)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val sinVideo = streamUrl == null || mpvPath == null || errorMsg != null

            if (sinVideo) {
                // Sin superficie pesada todavía: aquí el estado sí se puede dibujar
                if (errorMsg != null) {
                    val mensaje = errorMsg ?: ""
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No se pudo reproducir",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            mensaje,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                } else if (!resolviendo && mpvPath == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Falta el reproductor mpv",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Windows usa mpv como motor de vídeo y no se incluye en la app " +
                                "por su tamaño. Descárgalo y coloca mpv.exe en esta carpeta:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 40.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            MpvPlayer.rutaSugerida(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                runCatching {
                                    java.awt.Desktop.getDesktop().browse(
                                        java.net.URI("https://mpv.io/installation/"),
                                    )
                                }
                            }) { Text("Descargar mpv") }
                            TextButton(onClick = { mpvIntentos.intValue++ }) {
                                Text("Ya está instalado, reintentar")
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Conectando…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                MpvSurface(
                    modifier = Modifier.fillMaxSize(),
                    onCanvas = { canvas -> hwnd = MpvPlayer.hwndOf(canvas) },
                )
            }
        }

        // --- barra inferior de control
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledIconButton(onClick = { mpv?.togglePause() }) {
                Icon(
                    if (pausado) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (pausado) "Reproducir" else "Pausar",
                )
            }

            if (!isLive && duracion > 0) {
                Text(
                    formatearTiempo(posicion),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = posicion.toFloat().coerceIn(0f, duracion.toFloat()),
                    onValueChange = { arrastrando = true; posicion = it.toDouble() },
                    onValueChangeFinished = {
                        val destino = posicion
                        arrastrando = false
                        mpv?.seekTo(destino)
                    },
                    valueRange = 0f..duracion.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatearTiempo(duracion),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    if (isLive) "TV en vivo" else "Cargando…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            // Encuadre del vídeo: cicla Ajustar → Pantalla completa → Estirar → 16:9 → 4:3.
            TextButton(onClick = { cambiarImagen(ajusteImagen.siguiente()) }) {
                Icon(
                    Icons.Filled.AspectRatio,
                    contentDescription = "Ajuste de imagen",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(ajusteImagen.etiqueta, style = MaterialTheme.typography.labelMedium)
            }

            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = volumen.toFloat(),
                onValueChange = {
                    volumen = it.roundToInt()
                    mpv?.setVolume(volumen)
                },
                valueRange = 0f..100f,
                modifier = Modifier.width(110.dp),
            )
        }
    }

    if (verAjustes) {
        DesktopSettingsDialog(
            audios = audios,
            idiomaAudio = idiomaAudio,
            subtitulos = subtitulos,
            pistaSub = pistaSub,
            escalaSub = escalaSub,
            ajusteImagen = ajusteImagen,
            onAudio = {
                idiomaAudio = it
                com.xuperclean.desktop.data.DesktopWatchStore.audioLanguage = it
                mpv?.let { p -> aplicarAudio(p, it) }
            },
            onSubtitulo = {
                pistaSub = it
                mpv?.let { p -> aplicarSubtitulo(p) }
            },
            onEscala = {
                escalaSub = it
                mpv?.setSubtitleScale(it)
            },
            onAjusteImagen = { cambiarImagen(it) },
            onDismiss = { verAjustes = false },
        )
    }
}

private fun formatearTiempo(segundos: Double): String {
    if (segundos <= 0 || segundos.isNaN() || segundos.isInfinite()) return "00:00"
    val total = segundos.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Ajustes en reproducción: audio, subtítulos, tamaño e imagen. */
@Composable
private fun DesktopSettingsDialog(
    audios: List<com.xuperclean.core.AudioTrack>,
    idiomaAudio: String,
    subtitulos: List<SubtitleTrack>,
    pistaSub: String?,
    escalaSub: Float,
    ajusteImagen: AjusteImagen,
    onAudio: (String) -> Unit,
    onSubtitulo: (String?) -> Unit,
    onEscala: (Float) -> Unit,
    onAjusteImagen: (AjusteImagen) -> Unit,
    onDismiss: () -> Unit,
) {
    // El vídeo mpv es un componente pesado AWT: cualquier capa Compose dibujada
    // dentro de la ventana principal queda tapada por el vídeo. Por eso los
    // ajustes viven en una ventana propia (hija de la principal) con ComposePanel.
    val audiosS = rememberUpdatedState(audios)
    val idiomaAudioS = rememberUpdatedState(idiomaAudio)
    val subtitulosS = rememberUpdatedState(subtitulos)
    val pistaSubS = rememberUpdatedState(pistaSub)
    val escalaSubS = rememberUpdatedState(escalaSub)
    val ajusteImagenS = rememberUpdatedState(ajusteImagen)
    val onAudioS = rememberUpdatedState(onAudio)
    val onSubtituloS = rememberUpdatedState(onSubtitulo)
    val onEscalaS = rememberUpdatedState(onEscala)
    val onAjusteImagenS = rememberUpdatedState(onAjusteImagen)
    val onDismissS = rememberUpdatedState(onDismiss)

    val altoInicial = remember {
        240 + (if (audios.size > 1) 24 + audios.size * 48 else 0) +
            (if (subtitulos.isNotEmpty()) 120 + subtitulos.size * 48 else 40) +
            24 + AjusteImagen.entries.size * 48
    }.coerceIn(320, 640)

    DisposableEffect(Unit) {
        val owner = java.awt.Window.getWindows()
            .filterIsInstance<java.awt.Frame>()
            .firstOrNull { it.isVisible && it.title == "Xuper" }

        val panel = androidx.compose.ui.awt.ComposePanel().apply {
            preferredSize = java.awt.Dimension(544, altoInicial)
            setContent {
                XuperTheme {
                    SettingsCard(
                        audios = audiosS.value,
                        idiomaAudio = idiomaAudioS.value,
                        subtitulos = subtitulosS.value,
                        pistaSub = pistaSubS.value,
                        escalaSub = escalaSubS.value,
                        ajusteImagen = ajusteImagenS.value,
                        onAudio = { onAudioS.value(it) },
                        onSubtitulo = { onSubtituloS.value(it) },
                        onEscala = { onEscalaS.value(it) },
                        onAjusteImagen = { onAjusteImagenS.value(it) },
                        onDismiss = { onDismissS.value() },
                    )
                }
            }
        }

        val dialog = javax.swing.JDialog(owner, "Reproducción", false).apply {
            isUndecorated = true
            contentPane = panel
            defaultCloseOperation = javax.swing.WindowConstants.DISPOSE_ON_CLOSE
            addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowClosing(e: java.awt.event.WindowEvent?) {
                    onDismissS.value()
                }
            })
            rootPane.registerKeyboardAction(
                { onDismissS.value() },
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW,
            )
            pack()
            if (owner != null) {
                setLocation(
                    owner.x + (owner.width - width) / 2,
                    owner.y + (owner.height - height) / 2,
                )
            } else {
                setLocationRelativeTo(null)
            }
            isVisible = true
        }
        onDispose { dialog.dispose() }
    }
}

/** Contenido del panel de ajustes (compartido por la ventana). */
@Composable
private fun SettingsCard(
    audios: List<com.xuperclean.core.AudioTrack>,
    idiomaAudio: String,
    subtitulos: List<SubtitleTrack>,
    pistaSub: String?,
    escalaSub: Float,
    ajusteImagen: AjusteImagen,
    onAudio: (String) -> Unit,
    onSubtitulo: (String?) -> Unit,
    onEscala: (Float) -> Unit,
    onAjusteImagen: (AjusteImagen) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text("Reproducción", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (audios.size > 1) {
                    Text("Audio", style = MaterialTheme.typography.titleSmall)
                    audios.forEach { pista ->
                        androidx.compose.material3.FilterChip(
                            selected = pista.code == idiomaAudio,
                            onClick = { onAudio(pista.code) },
                            label = { Text(pista.label) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // Encuadre del vídeo: los modos habituales de cualquier reproductor.
                Text("Imagen", style = MaterialTheme.typography.titleSmall)
                AjusteImagen.entries.forEach { modo ->
                    androidx.compose.material3.FilterChip(
                        selected = modo == ajusteImagen,
                        onClick = { onAjusteImagen(modo) },
                        label = { Text(modo.etiqueta) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (subtitulos.isNotEmpty()) {
                    Text("Subtítulos", style = MaterialTheme.typography.titleSmall)
                    androidx.compose.material3.FilterChip(
                        selected = pistaSub == null,
                        onClick = { onSubtitulo(null) },
                        label = { Text("Desactivados") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    subtitulos.forEach { t ->
                        androidx.compose.material3.FilterChip(
                            selected = pistaSub == t.language,
                            onClick = { onSubtitulo(t.language) },
                            label = { Text(t.label) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        "Tamaño · ${(escalaSub * 100).toInt()} %",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Slider(
                        value = escalaSub,
                        onValueChange = onEscala,
                        valueRange = 0.7f..1.8f,
                        steps = 10,
                    )
                } else {
                    Text(
                        "Este título no incluye subtítulos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    }
}
