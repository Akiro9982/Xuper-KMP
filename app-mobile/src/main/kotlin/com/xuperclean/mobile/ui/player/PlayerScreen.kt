package com.xuperclean.mobile.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.xuperclean.core.model.EpisodeInfo
import com.xuperclean.core.model.VodAsset
import com.xuperclean.core.SubtitleTrack
import com.xuperclean.mobile.data.VodStream
import com.xuperclean.mobile.data.XuperRepository
import com.xuperclean.mobile.net.NetProvider
import kotlinx.coroutines.delay

/** Qué reproducir (el stream se resuelve al abrir: los tickets caducan). */
sealed interface PlayerPayload {
    val title: String

    data class Live(val channel: com.xuperclean.core.model.Channel) : PlayerPayload {
        override val title: String get() = channel.alias ?: channel.name
    }

    data class Vod(val asset: VodAsset) : PlayerPayload {
        override val title: String get() = asset.name
    }

    data class Episode(val asset: VodAsset, val episode: EpisodeInfo) : PlayerPayload {
        override val title: String get() = episode.name ?: asset.name
    }
}

/**
 * Reproductor táctil (Media3/ExoPlayer):
 * - toque = mostrar/ocultar controles (auto-ocultan a los 4 s)
 * - barra deslizable para buscar (VOD), "EN VIVO" para canales
 * - botón inmersivo (oculta barras del sistema) y cierre
 * - un solo player por pantalla, liberación garantizada
 */
@Composable
fun PlayerScreen(payload: PlayerPayload, onClose: () -> Unit) {
    val context = LocalContext.current
    var resolviendo by remember(payload) { mutableStateOf(true) }
    var errorMsg by remember(payload) { mutableStateOf<String?>(null) }
    var streamUrl by remember(payload) { mutableStateOf<String?>(null) }
    // Cabeceras de sesión por fuente. El data source las copia al crearse, así que la
    // fábrica las lee en cada `createDataSource()`: cambiar de fuente no recrea el
    // reproductor (no se pierde la posición) y las cabeceras siguen siendo válidas.
    val cabeceras = remember(payload) { java.util.HashMap<String, String>() }
    val fuenteDeDatos = remember(payload) {
        object : androidx.media3.datasource.DataSource.Factory {
            override fun createDataSource(): androidx.media3.datasource.DataSource =
                OkHttpDataSource.Factory(NetProvider.okHttpClient)
                    .setDefaultRequestProperties(cabeceras)
                    .createDataSource()
        }
    }
    var isLive by remember(payload) { mutableStateOf(false) }
    var fuentes by remember(payload) { mutableStateOf<List<VodStream>>(emptyList()) }
    var fuenteActual by remember(payload) { mutableIntStateOf(0) }
    var audios by remember(payload) {
        mutableStateOf<List<com.xuperclean.core.AudioTrack>>(emptyList())
    }
    var idiomaAudio by remember(payload) {
        mutableStateOf(com.xuperclean.mobile.data.WatchStore.audioLanguage)
    }
    var subtitulos by remember(payload) { mutableStateOf<List<SubtitleTrack>>(emptyList()) }
    var pistaSub by remember(payload) { mutableStateOf<String?>(null) }
    var escalaSub by remember(payload) { mutableFloatStateOf(1f) }
    var verAjustes by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var reanudarEn by remember(payload) { mutableLongStateOf(0L) }
    var posicionActual by remember { mutableLongStateOf(0L) }
    var duracionActual by remember { mutableLongStateOf(0L) }
    var episodioActual by remember(payload) {
        mutableStateOf<com.xuperclean.core.model.EpisodeInfo?>(null)
    }
    var ultimoGuardado by remember(payload) { mutableLongStateOf(0L) }

    /** Guarda el avance local (solo VOD, nunca live) (#8). */
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
            com.xuperclean.mobile.data.WatchStore.save(p)
        } else if (posMs.toDouble() / durMs >= 0.95) {
            com.xuperclean.mobile.data.WatchStore.remove(asset.contentId)
        }
    }
    var alternativas by remember(payload) {
        mutableStateOf<List<Pair<String, Map<String, String>>>>(emptyList())
    }

    /** Fija URL + cabeceras sin recrear el reproductor. */
    fun usarFuente(url: String, headers: Map<String, String>) {
        cabeceras.clear()
        cabeceras.putAll(headers)
        streamUrl = url
    }

    fun usarSiguiente(): Boolean {
        val siguiente = alternativas.firstOrNull() ?: return false
        alternativas = alternativas.drop(1)
        // Un fallo de CDN no debe reiniciar la película: se retoma donde iba (#1).
        if (!isLive) reanudarEn = posicionActual
        usarFuente(siguiente.first, siguiente.second)
        fuenteActual = fuentes.indexOfFirst { it.url == siguiente.first }.coerceAtLeast(0)
        return true
    }

    fun aplicarFuente(indice: Int) {
        val fuente = fuentes.getOrNull(indice) ?: return
        reanudarEn = posicionActual
        usarFuente(fuente.url, fuente.headers)
        fuenteActual = indice
        alternativas = fuentes.filterIndexed { i, _ -> i != indice }.map { it.url to it.headers }
    }

    LaunchedEffect(payload) {
        try {
            when (val kind = payload) {
                is PlayerPayload.Live -> {
                    val opciones = XuperRepository.liveStreams(kind.channel)
                    val primera = opciones.firstOrNull()
                    if (primera == null) errorMsg = "Canal no disponible"
                    else {
                        usarFuente(primera.url, primera.headers)
                        alternativas = opciones.drop(1).map { it.url to it.headers }
                        isLive = true
                    }
                }
                is PlayerPayload.Vod -> {
                    // Una serie nunca se reproduce por el path de película (#2).
                    val saved = com.xuperclean.mobile.data.WatchStore.find(kind.asset.contentId)
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
                    if (saved != null) reanudarEn = saved.positionMs
                    val opciones = XuperRepository.vodStreams(ticket, payload.title)
                    val primera = opciones.firstOrNull()
                    if (primera == null) errorMsg = "Contenido no disponible"
                    else {
                        fuentes = opciones
                        audios = XuperRepository.audio(ticket)
                        subtitulos = XuperRepository.subtitles(ticket)
                        usarFuente(primera.url, primera.headers)
                        alternativas = opciones.drop(1).map { it.url to it.headers }
                    }
                }
                is PlayerPayload.Episode -> {
                    episodioActual = kind.episode
                    val saved = com.xuperclean.mobile.data.WatchStore
                        .findEpisode(kind.episode.contentId)
                    if (saved != null) reanudarEn = saved.positionMs
                    val ticket = XuperRepository.startPlayVod(
                        contentId = kind.episode.contentId,
                        type = "0",
                        columnId = 87026, // columna raíz VOD (iptv-api.md §14)
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
                        usarFuente(primera.url, primera.headers)
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

    val player = remember(payload) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(fuenteDeDatos)
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // Buffer profundo: los CDN del portal entregan segmentos irregulares (#1).
                    .setBufferDurationsMs(
                        15_000, // mínimo antes de reproducir
                        30_000, // máximo en memoria
                        2_500,  // arranque
                        5_000,  // tras un rebuffer
                    )
                    .build()
            )
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        // Corte de CDN: pasar a la siguiente fuente antes de rendirse.
                        if (!usarSiguiente()) {
                            errorMsg = "El stream se detuvo o no está disponible."
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        // Reanudar cuando el timeline ya existe: en MPEG-TS progresivo un
                        // seek antes de preparar se descarta (#8).
                        if (playbackState == Player.STATE_READY && reanudarEn > 0L) {
                            seekTo(reanudarEn)
                            reanudarEn = 0L
                        }
                    }
                })
            }
    }

    DisposableEffect(player) {
        onDispose {
            guardarProgreso(posicionActual, duracionActual)
            player.stop()
            player.release()
        }
    }

    LaunchedEffect(player, streamUrl) {
        val url = streamUrl ?: return@LaunchedEffect
        val item = MediaItem.Builder()
            .setUri(url)
            .apply {
                if (subtitulos.isNotEmpty()) {
                    setSubtitleConfigurations(
                        subtitulos.map { t ->
                            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(t.url))
                                .setMimeType(t.mimeType)
                                .setLanguage(t.language)
                                .setLabel(t.label)
                                .build()
                        },
                    )
                }
            }
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
        // Intento inmediato (si el timeline ya se conoce) y refuerzo al llegar a READY.
        if (reanudarEn > 0L) player.seekTo(reanudarEn)
    }

    // Subtítulos: apagados o idioma preferido elegido en ajustes. Se reaplica al cambiar
    // de fuente porque `setMediaItem` vuelve a resolver las pistas.
    LaunchedEffect(player, pistaSub, streamUrl) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, pistaSub == null)
            .setPreferredTextLanguage(pistaSub.orEmpty())
            .build()
    }

    // Audio: el mismo stream trae varias pistas y el contenedor no marca ninguna como
    // predeterminada, así que la preferencia (por defecto latino) se aplica siempre.
    LaunchedEffect(player, idiomaAudio, streamUrl) {
        if (audios.isEmpty()) return@LaunchedEffect
        val cadena = com.xuperclean.core.AudioTracks.preferenceChain(idiomaAudio)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
            .setPreferredAudioLanguages(*cadena.toTypedArray())
            .build()
    }

    // Tamaño de subtítulos (100 % = 16 sp).
    LaunchedEffect(playerView, escalaSub) {
        playerView?.subtitleView?.setFixedTextSize(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            16f * escalaSub,
        )
    }

    // estado de reproducción (para controles)
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var inmersivo by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    fun touchControls() { controlsVisible = true; interactionTick++ }

    LaunchedEffect(player) {
        while (true) {
            delay(500)
            positionMs = player.currentPosition
            posicionActual = positionMs
            durationMs = player.duration.coerceAtLeast(0L)
            duracionActual = durationMs
            isPlaying = player.isPlaying
            if (!isLive && durationMs > 0 &&
                kotlin.math.abs(positionMs - ultimoGuardado) >= 15_000
            ) {
                ultimoGuardado = positionMs
                guardarProgreso(positionMs, durationMs)
            }
            if (!dragging && durationMs > 0) {
                sliderPos = positionMs.toFloat() / durationMs
            }
        }
    }

    LaunchedEffect(controlsVisible, interactionTick, isPlaying, dragging, verAjustes, errorMsg) {
        if (controlsVisible && isPlaying && !dragging && !verAjustes && errorMsg == null) {
            delay(4000)
            controlsVisible = false
        }
        if (!isPlaying || verAjustes || errorMsg != null) controlsVisible = true
    }

    BackHandler {
        if (inmersivo) inmersivo = false else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (streamUrl != null) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        playerView = this
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
            // Capa transparente por encima de la SurfaceView: un toque alterna los
            // controles y un doble toque salta ±10 s. Sin ella, la vista nativa
            // consume los gestos y el reproductor parece no responder.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(player, isLive) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible; interactionTick++ },
                            onDoubleTap = { point ->
                                touchControls()
                                if (!isLive && player.duration > 0) {
                                    val delta = if (point.x < size.width / 2) -10_000L else 10_000L
                                    player.seekTo(
                                        (player.currentPosition + delta)
                                            .coerceIn(0L, player.duration),
                                    )
                                }
                            },
                        )
                    },
            )
        } else if (resolviendo) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                Text("Conectando…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No se pudo reproducir",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    errorMsg ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Si el origen falla con la URL ya fijada, el PlayerView queda en negro (#1).
        if (streamUrl != null && errorMsg != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No se pudo reproducir",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    errorMsg ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Atrás para volver",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xE6100B19), Color(0xFF100B19))))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = payload.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { touchControls(); verAjustes = true }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Ajustes de reproducción",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = { touchControls(); inmersivo = !inmersivo }) {
                        Icon(
                            if (inmersivo) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = if (inmersivo) "Salir de pantalla completa" else "Pantalla completa",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
                // «EN VIVO» depende del CONTENIDO, no de la duración: al cambiar de
                // fuente o al recrear el motor la duración pasa por 0 y antes eso
                // convertía cualquier VOD en un directo.
                if (isLive) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.error,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "● EN VIVO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Slider(
                        value = sliderPos,
                        onValueChange = {
                            touchControls()
                            dragging = true
                            sliderPos = it
                        },
                        onValueChangeFinished = {
                            dragging = false
                            player.seekTo((sliderPos * durationMs).toLong())
                        },
                        enabled = durationMs > 0,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        Text(
                            formatTime(positionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        Spacer(Modifier.size(0.dp))
                        Text(
                            if (durationMs > 0) formatTime(durationMs) else "--:--",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.CenterVertically).padding(start = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                if (!isLive) IconButton(
                    enabled = durationMs > 0,
                    onClick = { touchControls(); player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) },
                ) {
                    Icon(Icons.Filled.Replay10, "Retroceder 10 segundos", tint = MaterialTheme.colorScheme.primary)
                }
                androidx.compose.material3.FilledIconButton(onClick = {
                    touchControls()
                    if (player.isPlaying) player.pause() else player.play()
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
                if (!isLive) IconButton(
                    enabled = durationMs > 0,
                    onClick = { touchControls(); player.seekTo((player.currentPosition + 10_000).coerceAtMost(durationMs)) },
                ) {
                    Icon(Icons.Filled.Forward10, "Avanzar 10 segundos", tint = MaterialTheme.colorScheme.primary)
                }
                }
            }
        }
    }

    if (verAjustes) {
        PlayerSettingsDialog(
            audios = audios,
            idiomaAudio = idiomaAudio,
            subtitulos = subtitulos,
            pistaSub = pistaSub,
            escalaSub = escalaSub,
            onAudio = {
                idiomaAudio = it
                com.xuperclean.mobile.data.WatchStore.audioLanguage = it
            },
            onSubtitulo = { pistaSub = it },
            onEscala = { escalaSub = it },
            onDismiss = { verAjustes = false },
        )
    }

    // Restaurar la ventana al salir, incluso si se cierra en modo inmersivo.
    DisposableEffect(context) {
        val window = (context as? android.app.Activity)?.window
        val hadKeepScreenOn = window?.attributes?.flags?.and(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (window != null) {
                if (!hadKeepScreenOn) window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
                androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // inmersivo: ocultar barras del sistema
    LaunchedEffect(inmersivo) {
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, !inmersivo)
        val c = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        if (inmersivo) {
            c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Ajustes en reproducción: audio, subtítulos y tamaño del texto. */
@Composable
private fun PlayerSettingsDialog(
    audios: List<com.xuperclean.core.AudioTrack>,
    idiomaAudio: String,
    subtitulos: List<SubtitleTrack>,
    pistaSub: String?,
    escalaSub: Float,
    onAudio: (String) -> Unit,
    onSubtitulo: (String?) -> Unit,
    onEscala: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reproducción") },
        text = {
            Column(
                Modifier.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (audios.size > 1) {
                    Text("Audio", style = MaterialTheme.typography.titleSmall)
                    // Chips en una fila desplazable: el diálogo no crece sin límite.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        audios.forEach { pista ->
                            androidx.compose.material3.FilterChip(
                                selected = pista.code == idiomaAudio,
                                onClick = { onAudio(pista.code) },
                                label = { Text(pista.label) },
                            )
                        }
                    }
                }
                if (subtitulos.isNotEmpty()) {
                    Text("Subtítulos", style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = pistaSub == null,
                            onClick = { onSubtitulo(null) },
                            label = { Text("Desactivados") },
                        )
                        subtitulos.forEach { t ->
                            androidx.compose.material3.FilterChip(
                                selected = pistaSub == t.language,
                                onClick = { onSubtitulo(t.language) },
                                label = { Text(t.label) },
                            )
                        }
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
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}
