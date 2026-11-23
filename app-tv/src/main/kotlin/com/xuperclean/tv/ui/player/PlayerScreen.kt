package com.xuperclean.tv.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface as TvSurface
import com.xuperclean.core.SubtitleTrack
import com.xuperclean.core.model.EpisodeInfo
import com.xuperclean.core.model.VodAsset
import com.xuperclean.tv.data.VodStream
import com.xuperclean.tv.data.XuperRepository
import com.xuperclean.tv.net.NetProvider
import com.xuperclean.tv.ui.common.LoadingState
import kotlinx.coroutines.delay

/**
 * Qué reproducir: la resolución del stream es diferida al abrir el player (los tickets
 * de sesión caducan; se pide el stream fresco en ese momento).
 */
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
 * Reproductor a pantalla completa (Media3/ExoPlayer).
 *
 * Gama baja: SurfaceView directo (PlayerView), overlay solo al interactuar (se oculta a
 * los 3,5 s), un solo reproductor por pantalla, liberación garantizada al salir.
 *
 * D-pad: centro = play/pausa · izquierda/derecha = ±10 s (solo VOD) · atrás = salir.
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(payload: PlayerPayload, onClose: () -> Unit) {
    val context = LocalContext.current
    var resolviendo by remember(payload) { mutableStateOf(true) }
    var errorMsg by remember(payload) { mutableStateOf<String?>(null) }
    var streamUrl by remember(payload) { mutableStateOf<String?>(null) }
    var isLive by remember(payload) { mutableStateOf(false) }
    // Cabeceras de sesión por fuente. El data source las copia al crearse, así que la
    // fábrica las lee en cada createDataSource(): cambiar de fuente no recrea el
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

    var alternativas by remember(payload) {
        mutableStateOf<List<Pair<String, Map<String, String>>>>(emptyList())
    }
    var fuentes by remember(payload) { mutableStateOf<List<VodStream>>(emptyList()) }
    var fuenteActual by remember(payload) { mutableIntStateOf(0) }
    var audios by remember(payload) {
        mutableStateOf<List<com.xuperclean.core.AudioTrack>>(emptyList())
    }
    var idiomaAudio by remember(payload) {
        mutableStateOf(com.xuperclean.tv.data.WatchStore.audioLanguage)
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
            com.xuperclean.tv.data.WatchStore.save(p)
        } else if (posMs.toDouble() / durMs >= 0.95) {
            com.xuperclean.tv.data.WatchStore.remove(asset.contentId)
        }
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

    // 1) resolver el stream (live: m3u8 + cabeceras; VOD: ticket + _media.ts)
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
                    val saved = com.xuperclean.tv.data.WatchStore.find(kind.asset.contentId)
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
                    val saved = com.xuperclean.tv.data.WatchStore
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

    // 2) el player se crea UNA vez, con la fábrica HTTP que lleva las cabeceras de sesión.
    //    Las cabeceras se conocen antes de crear el player porque el item se adjunta después.
    var pendingUrl by remember(payload) { mutableStateOf<String?>(null) }
    LaunchedEffect(streamUrl) {
        // pequeña transición: primero cabeceras, luego item
        pendingUrl = streamUrl
    }

    val player = remember(payload) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                // OkHttpDataSource: mismo pool + DNS resiliente DoH que el resto de la app
                DefaultMediaSourceFactory(fuenteDeDatos)
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // Buffer profundo: los CDN del portal entregan segmentos irregulares (#1).
                    .setBufferDurationsMs(15_000, 30_000, 2_500, 5_000)
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

    LaunchedEffect(player, pendingUrl) {
        val url = pendingUrl ?: return@LaunchedEffect
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

    // Se reaplica al cambiar de fuente: setMediaItem vuelve a resolver las pistas.
    LaunchedEffect(player, pistaSub, pendingUrl) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, pistaSub == null)
            .setPreferredTextLanguage(pistaSub.orEmpty())
            .build()
    }

    // Audio: el mismo stream trae varias pistas y el contenedor no marca ninguna como
    // predeterminada, así que la preferencia (por defecto latino) se aplica siempre.
    LaunchedEffect(player, idiomaAudio, pendingUrl) {
        if (audios.isEmpty()) return@LaunchedEffect
        val cadena = com.xuperclean.core.AudioTracks.preferenceChain(idiomaAudio)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
            .setPreferredAudioLanguages(*cadena.toTypedArray())
            .build()
    }

    // En TV se lee a distancia: base 22 sp (el 100 % del ajuste).
    LaunchedEffect(playerView, escalaSub) {
        playerView?.subtitleView?.setFixedTextSize(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            22f * escalaSub,
        )
    }

    // 3) overlay y progreso
    var overlayVisible by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    // Encuadre del vídeo y zona del mando que tiene el indicador de foco.
    var ajusteImagen by remember { mutableStateOf(AjusteImagen.AJUSTAR) }
    var zonaOverlay by remember {
        mutableStateOf(if (isLive) ZonaOverlay.AJUSTES else ZonaOverlay.PROGRESO)
    }

    LaunchedEffect(player) {
        while (true) {
            delay(500)
            positionMs = player.currentPosition.coerceAtLeast(0L)
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
        }
    }

    // Autoocultado durante la reproducción (también en directo). Al pausar, abrir
    // ajustes o navegar por los botones, el overlay permanece visible.
    LaunchedEffect(overlayVisible, overlayTick, zonaOverlay, isPlaying, verAjustes, errorMsg, isLive) {
        if (!isPlaying || verAjustes || errorMsg != null) {
            overlayVisible = true
            return@LaunchedEffect
        }
        if (!overlayVisible) return@LaunchedEffect
        delay(6000)
        if (zonaOverlay == ZonaOverlay.PROGRESO || isLive) overlayVisible = false
    }

    // En directo no hay barra que enfocar: el mando empieza en «Ajustes».
    LaunchedEffect(isLive) {
        if (isLive) zonaOverlay = ZonaOverlay.AJUSTES
    }

    fun showOverlay() {
        overlayVisible = true
        overlayTick++
    }

    // El foco vive en el Box del reproductor para que el D-pad controle la reproducción.
    val playerFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(playerFocus) { playerFocus.requestFocus() }
    LaunchedEffect(verAjustes) { if (!verAjustes) playerFocus.requestFocus() }

    BackHandler {
        when {
            verAjustes -> verAjustes = false
            overlayVisible && isPlaying -> overlayVisible = false
            else -> onClose()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                val abierto = overlayVisible
                when (e.key) {
                    // Arriba/abajo mueven el foco entre la barra y los botones: así se
                    // ve claramente sobre qué control está el usuario.
                    Key.DirectionUp -> {
                        if (!abierto || !isLive) {
                            showOverlay()
                            zonaOverlay = ZonaOverlay.AJUSTES
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        showOverlay()
                        if (!isLive) zonaOverlay = ZonaOverlay.PROGRESO
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!abierto) {
                            showOverlay()
                            if (!isLive) zonaOverlay = ZonaOverlay.PROGRESO
                            if (durationMs > 0) player.seekTo(player.currentPosition - 10_000)
                        } else when (zonaOverlay) {
                            ZonaOverlay.AJUSTES -> zonaOverlay = ZonaOverlay.IMAGEN
                            ZonaOverlay.IMAGEN -> Unit
                            ZonaOverlay.PROGRESO ->
                                if (durationMs > 0) player.seekTo(player.currentPosition - 10_000)
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (!abierto) {
                            showOverlay()
                            if (!isLive) zonaOverlay = ZonaOverlay.PROGRESO
                            if (durationMs > 0) player.seekTo(player.currentPosition + 10_000)
                        } else when (zonaOverlay) {
                            ZonaOverlay.IMAGEN -> zonaOverlay = ZonaOverlay.AJUSTES
                            ZonaOverlay.AJUSTES -> Unit
                            ZonaOverlay.PROGRESO ->
                                if (durationMs > 0) player.seekTo(player.currentPosition + 10_000)
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                        if (!abierto) {
                            showOverlay()
                            if (!isLive) zonaOverlay = ZonaOverlay.PROGRESO
                            if (player.isPlaying) player.pause() else player.play()
                        } else when (zonaOverlay) {
                            ZonaOverlay.AJUSTES -> verAjustes = true
                            ZonaOverlay.IMAGEN -> {
                                val siguiente = ajusteImagen.siguiente()
                                ajusteImagen = siguiente
                                playerView?.aplicarImagen(siguiente)
                            }
                            ZonaOverlay.PROGRESO ->
                                if (player.isPlaying) player.pause() else player.play()
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        if (streamUrl != null) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = false
                        // El PlayerView no debe robar el foco del D-pad: si se queda con
                        // él, los controles Compose (pausa/seek/ajustes) no reciben teclas.
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
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
        } else if (resolviendo) {
            LoadingState(Modifier.align(Alignment.Center), "Conectando")
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

        // Si el origen falla con la URL ya fijada, el PlayerView queda en negro: se avisa
        // aquí para que el usuario sepa que puede volver o probar otra fuente (#1).
        if (streamUrl != null && errorMsg != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
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
                Spacer(Modifier.height(12.dp))
                Text(
                    "Atrás para volver",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xE6100B19), Color(0xFF100B19))))
                    .padding(horizontal = 32.dp, vertical = 18.dp),
            ) {
                // Fila superior: título y acciones compactas alineadas a la derecha.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = payload.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                    BotonOverlay(
                        enfocado = zonaOverlay == ZonaOverlay.IMAGEN,
                        onClick = {
                            zonaOverlay = ZonaOverlay.IMAGEN
                            val siguiente = ajusteImagen.siguiente()
                            ajusteImagen = siguiente
                            playerView?.aplicarImagen(siguiente)
                        },
                        icono = Icons.Filled.AspectRatio,
                        descripcion = "Ajuste de imagen: ${ajusteImagen.etiqueta}",
                        etiqueta = ajusteImagen.etiqueta,
                    )
                    Spacer(Modifier.width(10.dp))
                    BotonOverlay(
                        enfocado = zonaOverlay == ZonaOverlay.AJUSTES,
                        onClick = {
                            zonaOverlay = ZonaOverlay.AJUSTES
                            verAjustes = true
                        },
                        icono = Icons.Filled.Settings,
                        descripcion = "Ajustes de reproducción",
                        etiqueta = null,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (isLive) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.error,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "● EN VIVO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    BarraProgreso(
                        fraccion = if (durationMs > 0) {
                            positionMs.toFloat() / durationMs
                        } else 0f,
                        enfocada = zonaOverlay == ZonaOverlay.PROGRESO,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "${formatTime(positionMs)} / " +
                                if (durationMs > 0) formatTime(durationMs) else "--:--",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (zonaOverlay == ZonaOverlay.PROGRESO) {
                                if (isPlaying) "OK pausar · ◀ ▶ buscar · ↑ ajustes" else "OK reproducir · ◀ ▶ buscar"
                            } else {
                                "OK elegir · ↓ barra"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }

    if (verAjustes) {
        TvSettingsOverlay(
            audios = audios,
            idiomaAudio = idiomaAudio,
            subtitulos = subtitulos,
            pistaSub = pistaSub,
            escalaSub = escalaSub,
            ajusteImagen = ajusteImagen,
            onAudio = {
                idiomaAudio = it
                com.xuperclean.tv.data.WatchStore.audioLanguage = it
            },
            onSubtitulo = { pistaSub = it },
            onEscala = { escalaSub = it },
            onAjusteImagen = {
                ajusteImagen = it
                playerView?.aplicarImagen(it)
            },
            onDismiss = { verAjustes = false },
        )
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Ajustes de reproducción navegables con el mando: audio, subtítulos e imagen. */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSettingsOverlay(
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
    val primerFoco = remember { androidx.compose.ui.focus.FocusRequester() }
    val settingsColors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        focusedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { primerFoco.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.6f)
                .fillMaxHeight(0.94f)
                // Panel opaco: sin él el vídeo se ve a través y los textos pierden legibilidad.
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                )
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Reproducción",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            // Título y «Cerrar» fijos; solo la lista se desplaza con el foco.
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            if (audios.size > 1) {
                Text("Audio", style = MaterialTheme.typography.titleSmall, color = Color.White)
                audios.forEachIndexed { i, pista ->
                    val enfocar = if (i == 0) Modifier.focusRequester(primerFoco) else Modifier
                    TvSurface(
                        onClick = { onAudio(pista.code) },
                        colors = settingsColors,
                        modifier = enfocar.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (pista.code == idiomaAudio) "● ${pista.label}" else pista.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (pista.code == idiomaAudio) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            // Encuadre del vídeo: los modos habituales de cualquier reproductor.
            Text("Imagen", style = MaterialTheme.typography.titleSmall, color = Color.White)
            AjusteImagen.entries.forEachIndexed { i, modo ->
                // Si no hay idiomas que listar, el foco inicial cae aquí.
                val enfocar = if (i == 0 && audios.size <= 1) Modifier.focusRequester(primerFoco) else Modifier
                TvSurface(
                    onClick = { onAjusteImagen(modo) },
                    colors = settingsColors,
                    modifier = enfocar.fillMaxWidth(),
                ) {
                    Text(
                        text = if (modo == ajusteImagen) "● ${modo.etiqueta}" else modo.etiqueta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (modo == ajusteImagen) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            if (subtitulos.isNotEmpty()) {
                Text("Subtítulos", style = MaterialTheme.typography.titleSmall, color = Color.White)
                TvSurface(onClick = { onSubtitulo(null) }, colors = settingsColors, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (pistaSub == null) "● Desactivados" else "Desactivados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (pistaSub == null) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                subtitulos.forEach { t ->
                    TvSurface(
                        onClick = { onSubtitulo(t.language) },
                        colors = settingsColors,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (pistaSub == t.language) "● ${t.label}" else t.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (pistaSub == t.language) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvButton(onClick = { onEscala((escalaSub - 0.1f).coerceAtLeast(0.7f)) }) {
                        Text("A−")
                    }
                    Text(
                        "Tamaño ${(escalaSub * 100).toInt()} %",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    TvButton(onClick = { onEscala((escalaSub + 0.1f).coerceAtMost(1.8f)) }) {
                        Text("A+")
                    }
                }
            } else {
                Text(
                    "Este título no incluye subtítulos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
                Spacer(Modifier.height(8.dp))
            }
            TvButton(onClick = onDismiss) { Text("Cerrar") }
        }
    }
}

/** Zona del overlay que tiene el indicador de foco del mando. */
private enum class ZonaOverlay { PROGRESO, IMAGEN, AJUSTES }

/** Modos de encuadre del vídeo, de los habituales en un reproductor. */
private enum class AjusteImagen(val etiqueta: String) {
    AJUSTAR("Ajustar"),
    PANTALLA_COMPLETA("Pantalla completa"),
    ESTIRAR("Estirar"),
    DIECISEIS_NUEVE("16:9"),
    CUATRO_TRES("4:3");

    fun siguiente(): AjusteImagen = entries[(ordinal + 1) % entries.size]
}

/** Aplica el encuadre al PlayerView (incluido el formato forzado 16:9 / 4:3). */
private fun PlayerView.aplicarImagen(modo: AjusteImagen) {
    resizeMode = when (modo) {
        AjusteImagen.AJUSTAR,
        AjusteImagen.DIECISEIS_NUEVE,
        AjusteImagen.CUATRO_TRES -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        AjusteImagen.PANTALLA_COMPLETA -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        AjusteImagen.ESTIRAR -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }
    // `exo_content_frame` es el AspectRatioFrameLayout interno de PlayerView.
    val frame = findViewById<android.view.View>(androidx.media3.ui.R.id.exo_content_frame)
        as? AspectRatioFrameLayout
    frame?.setAspectRatio(
        when (modo) {
            AjusteImagen.DIECISEIS_NUEVE -> 16f / 9f
            AjusteImagen.CUATRO_TRES -> 4f / 3f
            else -> 0f // 0 = usar el formato del vídeo
        }
    )
}

/** Botón compacto con foco violeta de alto contraste, visible a distancia. */
@Composable
private fun BotonOverlay(
    enfocado: Boolean,
    onClick: () -> Unit,
    icono: ImageVector,
    descripcion: String,
    etiqueta: String?,
) {
    val contenedor by animateColorAsState(
        targetValue = if (enfocado) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.White.copy(alpha = 0.10f)
        },
        label = "boton-fondo",
    )
    val contenido by animateColorAsState(
        targetValue = if (enfocado) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            Color.White
        },
        label = "boton-contenido",
    )
    val alto by animateDpAsState(if (enfocado) 42.dp else 38.dp, label = "boton-alto")
    Row(
        modifier = Modifier
            .height(alto)
            .background(contenedor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = if (etiqueta == null) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icono,
            contentDescription = descripcion,
            tint = contenido,
            modifier = Modifier.size(20.dp),
        )
        if (etiqueta != null) {
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelLarge,
                color = contenido,
                maxLines = 1,
            )
        }
    }
}

/**
 * Barra de progreso con indicador de foco: al estar seleccionada se engrosa, se
 * enmarca con el color primario y muestra un pulgar en la posición actual.
 */
@Composable
private fun BarraProgreso(
    fraccion: Float,
    enfocada: Boolean,
    modifier: Modifier = Modifier,
) {
    val primario = MaterialTheme.colorScheme.primary
    val grosor by animateDpAsState(if (enfocada) 8.dp else 4.dp, label = "barra-grosor")
    val pista = Color.White.copy(alpha = if (enfocada) 0.34f else 0.20f)
    val f = fraccion.coerceIn(0f, 1f)
    Canvas(modifier.height(24.dp)) {
        val alto = grosor.toPx()
        val y = (size.height - alto) / 2f
        val radio = alto / 2f
        if (enfocada) {
            drawRoundRect(
                color = primario.copy(alpha = 0.40f),
                topLeft = Offset(0f, y - 4.dp.toPx()),
                size = Size(size.width, alto + 8.dp.toPx()),
                cornerRadius = CornerRadius(radio + 4.dp.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        drawRoundRect(
            color = pista,
            topLeft = Offset(0f, y),
            size = Size(size.width, alto),
            cornerRadius = CornerRadius(radio),
        )
        if (f > 0f) {
            drawRoundRect(
                color = primario,
                topLeft = Offset(0f, y),
                size = Size(size.width * f, alto),
                cornerRadius = CornerRadius(radio),
            )
        }
        if (enfocada) {
            drawCircle(color = primario, radius = 7.dp.toPx(), center = Offset(size.width * f, size.height / 2f))
            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(size.width * f, size.height / 2f))
        }
    }
}
