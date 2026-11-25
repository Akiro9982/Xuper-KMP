package com.xuperclean.mobile.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
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
import androidx.media3.common.Format
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.xuperclean.core.model.EpisodeInfo
import com.xuperclean.core.model.VodAsset
import com.xuperclean.core.SubtitleTrack
import com.xuperclean.mobile.data.PlaybackPrefs
import com.xuperclean.mobile.data.VodStream
import com.xuperclean.mobile.diag.Diag
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

    data class Episode(
        val asset: VodAsset,
        val episode: EpisodeInfo,
        /** Nombre visible de la serie; el episodio puede venir de una copia con otro título. */
        val tituloSerie: String? = null,
        /** Temporada según la ficha; el asset de la copia no siempre la lleva en el nombre. */
        val temporada: Int? = null,
        /** Título original de la serie: una sola tarjeta en «Continuar viendo». */
        val serieKey: String? = null,
    ) : PlayerPayload {
        override val title: String
            get() = buildString {
                append(tituloSerie ?: com.xuperclean.core.SeriesGrouping.searchTitle(asset.name))
                append(" · T")
                append(
                    temporada
                        ?: com.xuperclean.core.SeriesGrouping.seasonNumber(asset.name),
                )
                episode.seriesNumber?.takeIf { it > 0 }?.let { append(" · E").append(it) }
            }
    }
}

/**
 * Reproductor táctil (Media3/ExoPlayer):
 * - toque = mostrar/ocultar controles (auto-ocultan a los 4 s)
 * - barra deslizable para buscar (VOD), "EN VIVO" para canales
 * - botón inmersivo (oculta barras del sistema) y cierre
 * - un solo player por pantalla, liberación garantizada
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(payload: PlayerPayload, onClose: () -> Unit) {
    val context = LocalContext.current
    Diag.init(context.applicationContext)
    PlaybackPrefs.init(context)
    var resolviendo by remember(payload) { mutableStateOf(true) }
    var errorMsg by remember(payload) { mutableStateOf<String?>(null) }
    var streamUrl by remember(payload) { mutableStateOf<String?>(null) }
    // Cabeceras de sesión por fuente. El data source las copia al crearse, así que la
    // fábrica las lee en cada `createDataSource()`: cambiar de fuente no recrea el
    // reproductor (no se pierde la posición) y las cabeceras siguen siendo válidas.
    val cabeceras = remember(payload) { java.util.HashMap<String, String>() }
    // Bytes reales descargados: permite distinguir un corte de red de uno del decodificador.
    val bytesDescargados = remember(payload) { AtomicLong(0L) }
    val fuenteDeDatos = remember(payload) {
        object : androidx.media3.datasource.DataSource.Factory {
            override fun createDataSource(): androidx.media3.datasource.DataSource =
                ContadorDatos(
                    OkHttpDataSource.Factory(NetProvider.okHttpClient)
                        .setDefaultRequestProperties(cabeceras)
                        .createDataSource(),
                    bytesDescargados,
                )
        }
    }
    var isLive by remember(payload) { mutableStateOf(false) }
    var fuentes by remember(payload) { mutableStateOf<List<VodStream>>(emptyList()) }
    var fuenteActual by remember(payload) { mutableIntStateOf(0) }
    // Pistas de audio reales del contenedor. El portal solo publica códigos ISO por
    // pista (`audioInfo`), así que la lista buena sale del propio stream al abrirlo.
    var pistasAudio by remember(payload) { mutableStateOf<List<PistaAudio>>(emptyList()) }
    // Códigos ISO en el MISMO orden que las pistas del contenedor (sin deduplicar).
    var codigosAudio by remember(payload) { mutableStateOf<List<String>>(emptyList()) }
    // Idiomas deducidos del `audioInfo`: respaldo cuando el extractor no publica las
    // pistas (el contenedor puede traer una sola), para no quedarse sin selector.
    var audios by remember(payload) {
        mutableStateOf<List<com.xuperclean.core.AudioTrack>>(emptyList())
    }
    var idiomaAudio by remember(payload) {
        mutableStateOf(com.xuperclean.mobile.data.WatchStore.audioLanguage)
    }
    val idContenido = remember(payload) {
        when (val k = payload) {
            is PlayerPayload.Vod -> k.asset.contentId
            is PlayerPayload.Episode -> k.asset.contentId
            is PlayerPayload.Live -> ""
        }
    }
    // Pista elegida a mano (-1 = automática). Se recuerda por título: cuando el
    // contenedor trae dos pistas `spa` no hay etiqueta que distinga latino de castellano.
    var audioElegido by remember(payload) {
        mutableIntStateOf(com.xuperclean.mobile.data.WatchStore.audioIndex(idContenido))
    }
    var subtitulos by remember(payload) { mutableStateOf<List<SubtitleTrack>>(emptyList()) }
    var pistaSub by remember(payload) { mutableStateOf<String?>(null) }
    var escalaSub by remember(payload) { mutableFloatStateOf(1f) }
    var verAjustes by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    // Modo de vídeo y generación del motor: cambiarlos recrea el ExoPlayer con otro
    // juego de decodificadores, conservando la posición (ver «vigilante de imagen»).
    var modoVideo by remember(payload) { mutableStateOf(PlaybackPrefs.modoVideo) }
    var generacion by remember(payload) { mutableIntStateOf(0) }
    var recuperando by remember(payload) { mutableStateOf(false) }
    var reanudarEn by remember(payload) { mutableLongStateOf(0L) }
    var posicionActual by remember { mutableLongStateOf(0L) }
    // Última posición con contenido: al cambiar de fuente el player vuelve a 0 y no se
    // debe confundir con el inicio de la película (#1).
    var ultimaPosicionValida by remember { mutableLongStateOf(0L) }
    var duracionActual by remember { mutableLongStateOf(0L) }
    var episodioActual by remember(payload) {
        mutableStateOf<com.xuperclean.core.model.EpisodeInfo?>(null)
    }
    var ultimoGuardado by remember(payload) { mutableLongStateOf(0L) }
    // Recuperación automática ante cortes de red o del decodificador (#12).
    var reintentando by remember(payload) { mutableIntStateOf(0) }
    var reintento by remember(payload) { mutableIntStateOf(0) }
    var esperaReintento by remember(payload) { mutableLongStateOf(0L) }
    var reintentoAgotado by remember(payload) { mutableStateOf(false) }

    /** Guarda el avance local (solo VOD, nunca live) (#8). */
    fun guardarProgreso(posMs: Long, durMs: Long) {
        if (isLive || durMs <= 0) return
        val asset = when (val k = payload) {
            is PlayerPayload.Vod -> k.asset
            is PlayerPayload.Episode -> k.asset
            is PlayerPayload.Live -> return
        }
        val ep = episodioActual
        val capitulo = payload as? PlayerPayload.Episode
        val p = com.xuperclean.core.WatchProgress(
            contentId = ep?.contentId ?: asset.contentId,
            assetId = asset.contentId,
            name = capitulo?.tituloSerie ?: asset.name,
            type = if (asset.isSeries) "0" else "1",
            isSeries = asset.isSeries,
            posterUrl = asset.posterList.firstOrNull()?.fileUrl,
            episodeId = ep?.contentId,
            episodeNumber = ep?.seriesNumber,
            episodeName = ep?.name,
            seasonNumber = when {
                !asset.isSeries -> null
                // La ficha ya sabe en qué temporada está: no se adivina por el nombre
                // del asset, que en la copia original puede no llevarla.
                capitulo?.temporada != null -> capitulo.temporada
                else -> com.xuperclean.core.SeriesGrouping.seasonNumber(asset.name)
            },
            aliasKey = capitulo?.serieKey
                ?: asset.alias?.takeIf { it.isNotBlank() }
                    ?.let { com.xuperclean.core.SeriesGrouping.baseName(it) },
            positionMs = posMs,
            durationMs = durMs,
            updatedAt = System.currentTimeMillis(),
        )
        if (com.xuperclean.core.WatchProgress.isResumable(posMs, durMs)) {
            com.xuperclean.mobile.data.WatchStore.save(p)
        } else if (posMs.toDouble() / durMs >= 0.95) {
            com.xuperclean.mobile.data.WatchStore.remove(p.clave)
        }
    }
    var alternativas by remember(payload) {
        mutableStateOf<List<Pair<String, Map<String, String>>>>(emptyList())
    }

    /** Fija URL + cabeceras sin recrear el reproductor. */
    fun usarFuente(url: String, headers: Map<String, String>) {
        cabeceras.clear()
        cabeceras.putAll(headers)
        // Al cambiar de fuente el motor resuelve pistas nuevas y los índices de audio
        // viejos dejan de valer: se vuelven a leer del stream y se reaplica la elección.
        pistasAudio = emptyList()
        streamUrl = url
    }

    fun usarSiguiente(): Boolean {
        val siguiente = alternativas.firstOrNull() ?: return false
        alternativas = alternativas.drop(1)
        // Fuente nueva: sin espera ni aviso de los reintentos de la anterior.
        reintentando = 0
        esperaReintento = 0L
        reintentoAgotado = false
        errorMsg = null
        // Un fallo de CDN no debe reiniciar la película: se retoma donde iba (#1).
        if (!isLive) reanudarEn = maxOf(posicionActual, ultimaPosicionValida)
        usarFuente(siguiente.first, siguiente.second)
        fuenteActual = fuentes.indexOfFirst { it.url == siguiente.first }.coerceAtLeast(0)
        return true
    }

    fun aplicarFuente(indice: Int) {
        val fuente = fuentes.getOrNull(indice) ?: return
        reanudarEn = maxOf(posicionActual, ultimaPosicionValida)
        usarFuente(fuente.url, fuente.headers)
        fuenteActual = indice
        alternativas = fuentes.filterIndexed { i, _ -> i != indice }.map { it.url to it.headers }
        reintentando = 0
        esperaReintento = 0L
        reintentoAgotado = false
        errorMsg = null
    }


    /** Reintento manual desde el aviso de error: no hace falta salir del reproductor. */
    fun reintentarAhora() {
        if (streamUrl == null) return
        reintentando = 0
        reintentoAgotado = false
        errorMsg = null
        esperaReintento = 0L
        if (!isLive) reanudarEn = maxOf(posicionActual, ultimaPosicionValida)
        reintento++
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
                        codigosAudio = codigosDeAudio(opciones.firstOrNull()?.variant?.audioInfo)
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
                        codigosAudio = codigosDeAudio(opciones.firstOrNull()?.variant?.audioInfo)
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

    val diagnostico = remember(payload) { DiagnosticoListener() }

    val player = remember(payload, modoVideo, generacion) {
        // Fallback de decodificadores: si el del fabricante falla, Media3 prueba otro
        // en lugar de dejar la imagen en negro. El fichero `sw` fuerza software.
        //
        // El modo elegido (o el que decide el vigilante de imagen) cambia la lista de
        // decodificadores; al ser clave de `remember`, el motor se reconstruye con la
        // nueva selección y se retoma la posición guardada en `reanudarEn`.
        fun solo(acelerados: Boolean): MediaCodecSelector =
            MediaCodecSelector { tipo, seguro, tunel ->
                MediaCodecSelector.DEFAULT.getDecoderInfos(tipo, seguro, tunel)
                    .filter { it.hardwareAccelerated == acelerados }
                    .ifEmpty { MediaCodecSelector.DEFAULT.getDecoderInfos(tipo, seguro, tunel) }
            }
        val selector = when {
            Diag.forzarSoftware || modoVideo == PlaybackPrefs.ModoVideo.SOFTWARE -> solo(false)
            modoVideo == PlaybackPrefs.ModoVideo.HARDWARE -> solo(true)
            else -> MediaCodecSelector.DEFAULT
        }
        Diag.i("Motor de vídeo: modo=$modoVideo generación=$generacion")
        val renderizadores = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(selector)
        ExoPlayer.Builder(context, renderizadores)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(fuenteDeDatos)
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // Buffer profundo: los CDN del portal entregan segmentos irregulares (#1).
                    .setBufferDurationsMs(
                        15_000, // mínimo antes de reproducir
                        120_000, // máximo en memoria: banda de búfer visible y margen ante cortes
                        2_500,  // arranque
                        5_000,  // tras un rebuffer
                    )
                    .build()
            )
            .build()
            .apply {
                addAnalyticsListener(diagnostico)
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Diag.w(
                            "Error de reproducción ${error.errorCode} en ${currentPosition} ms: " +
                                error.message,
                            error,
                        )
                        // Corte de CDN: pasar a la siguiente fuente antes de rendirse.
                        if (usarSiguiente()) return
                        // Fallo transitorio (red caída, tiempo de espera agotado, decodificador
                        // reiniciado): se reintenta solo, conservando la posición. Antes, un corte
                        // de wifi dejaba el reproductor en IDLE para siempre.
                        val codigo = error.errorCode
                        val transitorio = codigo in 2_000..2_008 ||
                            codigo == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                            codigo == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                            codigo == PlaybackException.ERROR_CODE_UNSPECIFIED
                        if (transitorio && reintentando < 12) {
                            reintentando++
                            errorMsg = null
                            reintentoAgotado = false
                            if (!isLive) reanudarEn = maxOf(posicionActual, ultimaPosicionValida)
                            // 2, 4, 8, 16 y luego 30 s entre intentos.
                            esperaReintento =
                                (2_000L shl (reintentando - 1).coerceAtMost(4))
                                    .coerceAtMost(30_000L)
                            reintento++
                        } else {
                            errorMsg = "El stream se detuvo o no está disponible."
                            reintentoAgotado = true
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        // Reanudar cuando el timeline ya existe: en MPEG-TS progresivo un
                        // seek antes de preparar se descarta (#8).
                        if (playbackState == Player.STATE_READY && reanudarEn > 0L) {
                            seekTo(reanudarEn)
                            reanudarEn = 0L
                        }
                        if (playbackState == Player.STATE_READY) {
                            reintentando = 0
                            reintentoAgotado = false
                        }
                    }
                })
            }
    }

    /**
     * Índice de audio de la preferencia guardada, leído del `audioInfo` del título.
     *
     * El portal publica los códigos en el mismo orden que las pistas del `_media.ts`,
     * así que la posición es la única información fiable para el latino. Sin
     * coincidencia se usa la primera pista, igual que la app original.
     */
    fun indiceAudioAuto(): Int {
        if (pistasAudio.isEmpty()) return 0
        val cadena = com.xuperclean.core.AudioTracks.preferenceChain(
            com.xuperclean.mobile.data.WatchStore.audioLanguage,
        )
        for (codigo in cadena) {
            val i = codigosAudio.indexOf(codigo)
            if (i >= 0 && i < pistasAudio.size) return i
        }
        return 0
    }

    /** Idioma que corresponde a la preferencia guardada dentro de `audios`. */
    fun idiomaAuto(): String? = com.xuperclean.core.AudioTracks
        .pick(audios, com.xuperclean.mobile.data.WatchStore.audioLanguage)?.code

    /**
     * Fija una pista de audio concreta (grupo de Media3 + índice dentro del grupo).
     *
     * Con dos pistas `spa` (latino y castellano) Media3 las ve iguales y desempata por
     * calidad, así que puede quedarse con la de España. La app original reproduce la
     * primera del `audioInfo`; aquí se replica y, si el usuario elige otra, se respeta.
     *
     * El extractor de TS publica un grupo por PID, de un solo índice, así que la pista
     * se identifica por el grupo y no por una posición dentro de él.
     */
    fun aplicarPistaAudio(pista: PistaAudio) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO)
            .addOverride(
                androidx.media3.common.TrackSelectionOverride(
                    pista.grupo,
                    listOf(pista.indice),
                )
            )
            .build()
        Diag.i(
            "Audio: pista «${pista.etiqueta}» (${pista.posicion + 1}/${pistasAudio.size}, " +
                "grupo de ${pista.grupo.length}, audioInfo=${codigosAudio.joinToString(",")})"
        )
    }

    /**
     * Respaldo por idioma: se usa cuando el contenedor solo deja ver una pista y el
     * portal sí declara varios idiomas en `audioInfo`. Es la selección que había antes.
     */
    fun aplicarIdioma(codigo: String) {
        idiomaAudio = codigo
        com.xuperclean.mobile.data.WatchStore.audioLanguage = codigo
        val cadena = com.xuperclean.core.AudioTracks.preferenceChain(codigo)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO)
            .setPreferredAudioLanguages(*cadena.toTypedArray())
            .build()
        Diag.i("Audio: idioma «$codigo» (cadena=${cadena.joinToString(",")})")
    }
    // --- Vigilante de imagen ------------------------------------------------
    // El audio y el vídeo usan decodificadores distintos. Cuando la imagen se para y el
    // sonido continúa, el atasco está en el vídeo (o en fotogramas que llegan tarde y
    // Media3 descarta hasta dejar el último pintado), no en la red: sin datos, ExoPlayer
    // pasaría a BUFFERING y también callaría el audio.
    //
    // Antes se esperaban 5 s y se retrocedía 1 s: la imagen volvía, pero el vídeo se
    // atascaba otra vez al rato porque nada cambiaba de fondo. Ahora se detecta en ~1,2 s
    // y se escala en cuatro pasos, del más barato al más contundente. Si el aparato no
    // puede con el capítulo, se cambia de fuente (calidad más ligera) o de decodificador
    // en lugar de repetir el mismo parche.
    val vigilante = remember(payload) { VigilanteImagen() }
    val ultimoFotogramaMs = remember(player) { AtomicLong(0L) }
    val latidoVideo = remember(player) {
        VideoFrameMetadataListener { _, _, _, _ ->
            val ahora = android.os.SystemClock.elapsedRealtime()
            if (ultimoFotogramaMs.getAndSet(ahora) == 0L) {
                android.util.Log.i("XuperPlayer", "Primer fotograma pintado")
            }
        }
    }
    DisposableEffect(player) {
        player.setVideoFrameMetadataListener(latidoVideo)
        onDispose { player.clearVideoFrameMetadataListener(latidoVideo) }
    }

    /** Motor nuevo con los mismos ajustes, retomando la posición actual. */
    fun reconstruirMotor(motivo: String) {
        val pos = player.currentPosition
        Diag.w("Vídeo: reconstruyendo el motor en $pos ms ($motivo)")
        reanudarEn = maxOf(pos, ultimaPosicionValida)
        generacion++
    }

    /**
     * Pasa a los decodificadores software (los de Android). No vuelve sola al del
     * fabricante: si el software no da abasto, la prueba de rendimiento lo deshace.
     */
    fun cambiarDecodificador(): Boolean {
        if (modoVideo == PlaybackPrefs.ModoVideo.SOFTWARE || vigilante.softwareDescartado) {
            return false
        }
        Diag.w("Vídeo: cambiando a los decodificadores software")
        reanudarEn = maxOf(player.currentPosition, ultimaPosicionValida)
        vigilante.iniciarPruebaSoftware(
            android.os.SystemClock.elapsedRealtime(),
            diagnostico.descartados,
            automatica = true,
        )
        modoVideo = PlaybackPrefs.ModoVideo.SOFTWARE
        PlaybackPrefs.modoVideo = PlaybackPrefs.ModoVideo.SOFTWARE
        return true
    }

    /** Fuente de menor resolución que la actual, conservando la posición. */
    fun usarFuenteMasLigera(): Boolean {
        if (fuentes.size < 2) return false
        val actual = calidadDe(fuentes.getOrNull(fuenteActual))
        val indice = fuentes.indices
            .filter { it != fuenteActual }
            .mapNotNull { i -> calidadDe(fuentes[i])?.let { c -> i to c } }
            .filter { (_, calidad) -> actual == null || calidad < actual }
            .maxByOrNull { (_, calidad) -> calidad }
            ?.first
            ?: return false
        Diag.w(
            "Vídeo: la fuente es demasiado pesada; cambiando a ${fuentes[indice].label}",
        )
        aplicarFuente(indice)
        return true
    }

    /** Cambia a una fuente más ligera, una sola vez por sesión de reproducción. */
    fun intentarFuenteLigera(): Boolean {
        if (vigilante.fuenteCambiada) return false
        if (!usarFuenteMasLigera()) return false
        vigilante.fuenteCambiada = true
        return true
    }

    /**
     * Escalones de recuperación. El orden sale de la evidencia de campo: en el móvil
     * real, retroceder, rearmar la superficie y reconstruir el motor NO arreglaban el
     * cuelgue; lo arreglaba el decodificador software. Por eso el software va segundo y
     * los remedios que ya fallaron quedan al final.
     */
    fun aplicarPaso(paso: Int, pos: Long) {
        recuperando = true
        when (paso) {
            0 -> {
                Diag.w("Vídeo: imagen congelada en $pos ms con audio activo; retrocediendo 1 s")
                vigilante.ultimoSalto = android.os.SystemClock.elapsedRealtime()
                player.seekTo((pos - 1_000L).coerceAtLeast(0L))
            }
            1 -> {
                if (cambiarDecodificador()) {
                    Toast.makeText(
                        context,
                        "Imagen congelada: paso a decodificación software.",
                        Toast.LENGTH_LONG,
                    ).show()
                } else if (!intentarFuenteLigera()) {
                    reconstruirMotor("el software ya está descartado")
                }
            }
            2 -> {
                if (!intentarFuenteLigera()) {
                    reconstruirMotor("sin fuentes más ligeras")
                }
            }
            else -> {
                Diag.w("Vídeo: último recurso; reconstruyendo el motor")
                reconstruirMotor("último recurso")
                recuperando = false
            }
        }
    }

    LaunchedEffect(player, streamUrl, isLive, generacion) {
        if (isLive) return@LaunchedEffect
        val arranque = android.os.SystemClock.elapsedRealtime()
        var posicionPrevia = -1L
        var bytesPrevios = bytesDescargados.get()
        var instanteBytes = arranque
        var tasaBps = 0L
        while (true) {
            delay(500)
            val ahora = android.os.SystemClock.elapsedRealtime()
            val bytesAhora = bytesDescargados.get()
            val transcurrido = (ahora - instanteBytes).coerceAtLeast(1L)
            if (bytesAhora > bytesPrevios) {
                tasaBps = (bytesAhora - bytesPrevios) * 1_000 / transcurrido
                bytesPrevios = bytesAhora
                instanteBytes = ahora
            }
            val pos = player.currentPosition
            val salto = pos - posicionPrevia
            if (posicionPrevia >= 0L && (salto < -1_500L || salto > 5_000L)) {
                // Salto del usuario (barra o doble toque): no es un atasco.
                vigilante.ultimoSalto = ahora
            }
            posicionPrevia = pos
            val duracion = player.duration
            val estado = player.playbackState
            val cargando = player.isLoading
            val bufDelante = player.bufferedPosition - pos
            val ultimo = ultimoFotogramaMs.get()
            // Sin fotograma todavía se mide desde que arrancó este motor, no desde 0.
            val sinFotograma = if (ultimo > 0L) ahora - ultimo else ahora - arranque
            val alFinal = duracion > 0L && pos >= duracion - 5_000L
            // Sin pista de vídeo (contenido solo de audio) no hay imagen que vigilar.
            val hayVideo = ultimo > 0L || player.videoSize.width > 0
            val enMarcha = hayVideo && player.isPlaying && estado == Player.STATE_READY &&
                !cargando && !alFinal && ahora - arranque > 4_000L
            val imagenViva = ultimo > 0L && sinFotograma <= 1_500L
            val congelada = enMarcha && bufDelante > 3_000L && sinFotograma > 1_200L &&
                ahora - vigilante.ultimoSalto > 2_000L
            Diag.v(
                "pos=$pos dur=$duracion buf=$bufDelante estado=${nombreEstado(estado)} " +
                    "playing=${player.isPlaying} loading=$cargando " +
                    "dec=${diagnostico.decodificador ?: "-"} bajados=${bytesAhora / 1024}KB " +
                    "tasa=${tasaBps / 1024}KB/s descartados=${diagnostico.descartados} " +
                    "underrun=${diagnostico.underruns} frameHace=$sinFotograma " +
                    "paso=${vigilante.paso} fuente=$fuenteActual/${fuentes.size}",
            )
            // Prueba del decodificador software: si no mantiene el ritmo, se deshace en
            // vez de dejar la imagen a tirones (peor que el cuelgue que se quería evitar).
            if (vigilante.pruebaHasta > 0L && ahora >= vigilante.pruebaHasta) {
                val descartes = diagnostico.descartados - vigilante.descartesPrueba
                vigilante.pruebaHasta = 0L
                if (descartes > 200 && vigilante.pruebaAutomatica) {
                    Diag.w(
                        "Vídeo: el decodificador software no da abasto " +
                            "($descartes fotogramas descartados en 25 s); volviendo al del fabricante",
                    )
                    vigilante.softwareDescartado = true
                    reanudarEn = maxOf(pos, ultimaPosicionValida)
                    modoVideo = PlaybackPrefs.ModoVideo.AUTO
                    PlaybackPrefs.modoVideo = PlaybackPrefs.ModoVideo.AUTO
                    Toast.makeText(
                        context,
                        "El decodificador software no da abasto: vuelvo a Automático.",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    Diag.i("Vídeo: el decodificador software mantiene el ritmo")
                }
            }
            if (congelada) {
                if (!vigilante.enCurso) {
                    vigilante.registrarEpisodio()
                    Diag.w(
                        "Vídeo: imagen congelada en $pos ms con audio activo " +
                            "(búfer +${bufDelante / 1000}s, dec=${diagnostico.decodificador}, " +
                            "descartados=${diagnostico.descartados}, " +
                            "episodios=${vigilante.episodios})",
                    )
                    Diag.volcarHilos("imagen congelada en $pos ms")
                    vigilante.enCurso = true
                    vigilante.paso = vigilante.pasoInicial()
                    vigilante.ultimaAccion = ahora
                    aplicarPaso(vigilante.paso, pos)
                } else if (vigilante.paso < 3 && ahora - vigilante.ultimaAccion >= ESPERA_PASO) {
                    vigilante.paso++
                    vigilante.ultimaAccion = ahora
                    aplicarPaso(vigilante.paso, pos)
                }
            } else if (vigilante.enCurso && imagenViva &&
                ahora - vigilante.ultimaAccion >= 1_500L
            ) {
                Diag.i("Vídeo: imagen recuperada en $pos ms (paso ${vigilante.paso})")
                vigilante.enCurso = false
                vigilante.paso = -1
                recuperando = false
            } else if (vigilante.enCurso && !player.playWhenReady) {
                // El usuario pausó: no hay imagen que recuperar y el aviso no puede
                // quedarse colgado en pantalla. `playWhenReady` distingue una pausa real
                // de un búfer momentáneo durante una reconstrucción.
                vigilante.enCurso = false
                vigilante.paso = -1
                recuperando = false
            }
        }
    }

    /** Texto que el usuario puede pegar en el chat sin conectar el móvil a nada. */
    fun copiarInforme() {
        val informe = Diag.informe(
            encabezado = buildString {
                appendLine("Título: ${payload.title}")
                appendLine(
                    "Fuente: ${fuentes.getOrNull(fuenteActual)?.label ?: "-"} " +
                        "(${fuenteActual + 1}/${fuentes.size})",
                )
                appendLine("Modo de vídeo: $modoVideo")
                appendLine("Decodificador: ${diagnostico.decodificador ?: "-"}")
                appendLine(
                    "Audio: ${pistasAudio.getOrNull(
                        if (audioElegido >= 0) audioElegido else indiceAudioAuto(),
                    )?.etiqueta ?: idiomaAudio} de ${pistasAudio.size} pista(s) " +
                        "(audioInfo=${codigosAudio.joinToString(",")}, idiomas=${audios.joinToString(",") { it.code }})",
                )
                appendLine("Posición: ${player.currentPosition} de ${player.duration} ms")
                appendLine(
                    "Búfer por delante: ${player.bufferedPosition - player.currentPosition} ms",
                )
                appendLine(
                    "Descartados: ${diagnostico.descartados} · underruns: ${diagnostico.underruns}",
                )
                appendLine(
                    "Congelamientos atendidos: ${vigilante.episodios} " +
                        "(paso ${vigilante.paso}, ${if (vigilante.enCurso) "en curso" else "resuelto"})",
                )
            },
        )
        Diag.guardar(informe)
        runCatching {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("Informe Xuper", informe))
        }
        Toast.makeText(context, "Informe copiado: pégalo en el chat.", Toast.LENGTH_LONG).show()
        Diag.i("Informe de reproducción copiado (${informe.length} caracteres)")
    }

    DisposableEffect(player) {
        onDispose {
            Diag.i("Reproductor liberado en ${player.currentPosition} ms")
            guardarProgreso(posicionActual, duracionActual)
            player.stop()
            player.release()
        }
    }

    LaunchedEffect(player, streamUrl, reintento) {
        val url = streamUrl ?: return@LaunchedEffect
        if (reintento > 0 && esperaReintento > 0L) {
            Diag.i("Reintento ${reintentando} de la fuente en ${esperaReintento} ms")
            delay(esperaReintento)
        }
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

    // Audio: se fija por ÍNDICE en cuanto el contenedor publica sus pistas. Elegir por
    // idioma no basta: dos pistas `spa` (latino y castellano) empatan para Media3, que
    // desempata por calidad y puede quedarse con la de España. El índice respeta la
    // preferencia guardada y, si el usuario eligió otra pista, su elección.
    LaunchedEffect(player, streamUrl, pistasAudio, audioElegido, audios) {
        if (pistasAudio.isEmpty()) return@LaunchedEffect
        // Con una sola pista visible manda el respaldo por idioma (ver abajo).
        if (pistasAudio.size <= 1 && audios.size > 1) return@LaunchedEffect
        val posicion = if (audioElegido >= 0) {
            audioElegido.coerceIn(0, pistasAudio.lastIndex)
        } else {
            indiceAudioAuto()
        }
        pistasAudio.getOrNull(posicion)?.let { aplicarPistaAudio(it) }
    }

    // Respaldo: si el contenedor solo publica una pista (o el aparato no soporta las
    // demás), se elige por idioma con lo que declara el `audioInfo`. Antes esto era el
    // único comportamiento; se conserva para que el selector no desaparezca nunca.
    LaunchedEffect(player, streamUrl, audios, idiomaAudio) {
        if (pistasAudio.size > 1 || audios.size <= 1) return@LaunchedEffect
        aplicarIdioma(idiomaAudio)
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
    // Tramo ya descargado (búfer) para pintarlo en gris en la barra.
    var bufferedMs by remember { mutableLongStateOf(0L) }
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
            if (positionMs > 0L) ultimaPosicionValida = positionMs
            durationMs = player.duration.coerceAtLeast(0L)
            duracionActual = durationMs
            // Las pistas de audio aparecen en cuanto el extractor lee el PMT del TS.
            val nuevas = pistasDeAudio(player, codigosAudio)
            if (nuevas != pistasAudio) {
                pistasAudio = nuevas
                Diag.i(
                    "Audio: ${nuevas.size} pista(s) en el archivo " +
                        "[${nuevas.joinToString(", ") { it.etiqueta }}] " +
                        "audioInfo=${codigosAudio.joinToString(",")}"
                )
            }
            bufferedMs = player.bufferedPosition.coerceAtLeast(positionMs)
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
        Diag.i("Atrás pulsado (inmersivo=$inmersivo)")
        if (inmersivo) inmersivo = false else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // El gesto vive en la CAJA PADRE, no en una capa hermana: así los botones
            // del panel siempre reciben el toque y sólo el vídeo lo usa para ocultar
            // o mostrar los controles (#19).
            .pointerInput(player, isLive) {
                detectTapGestures(
                    onTap = { _ ->
                        controlsVisible = !controlsVisible
                        interactionTick++
                    },
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
    ) {
        if (streamUrl != null) {
            AndroidView(
                factory = {
                    Diag.i("PlayerView creada (factoría de AndroidView)")
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
            DisposableEffect(playerView) {
                val superficie = playerView?.videoSurfaceView as? SurfaceView
                val escucha = object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Diag.i("Superficie creada: ${holder.surface}")
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        Diag.i("Superficie cambiada: formato=$format ${width}x$height")
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Diag.w("Superficie destruida")
                    }
                }
                superficie?.holder?.addCallback(escucha)
                onDispose { superficie?.holder?.removeCallback(escucha) }
            }

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
                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.OutlinedButton(onClick = { reintentarAhora() }) {
                    Text("Reintentar")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Atrás para volver",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }

        // Reintento automático en curso: se avisa sin tapar el vídeo.
        if (reintentando > 0 && errorMsg == null) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    "Recuperando la conexión… (intento $reintentando)",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Recuperación de imagen en curso: se avisa arriba, sin tapar el vídeo, para que
        // se vea que la app está reaccionando y no colgada.
        if (recuperando && errorMsg == null) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
                    // Se puede tocar: abre los ajustes, donde está el decodificador y el
                    // informe. La opción no debe quedar escondida justo cuando hace falta.
                    .clickable { touchControls(); verAjustes = true }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "Ajustando la imagen… (toca para ajustes)",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Ojo: con AnimatedVisibility + slideInVertically el panel quedaba pintado unos
        // 50 dp por encima de su zona táctil, así que los botones «no respondían» (#19).
        // Ahora sólo se anima la opacidad: dibujo y toques siempre coinciden.
        val opacidadControles by animateFloatAsState(
            targetValue = if (controlsVisible) 1f else 0f,
            animationSpec = tween(220),
            label = "controles",
        )
        if (controlsVisible || opacidadControles > 0.01f) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = opacidadControles }
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
                        track = { estado ->
                            PistaConBuffer(
                                fraccionReproducida = estado.value,
                                fraccionBuffer = if (durationMs > 0) {
                                    bufferedMs.toFloat() / durationMs
                                } else 0f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
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
            pistasAudio = pistasAudio,
            audioElegido = audioElegido,
            audioAuto = indiceAudioAuto(),
            audios = audios,
            idiomaAuto = idiomaAuto(),
            subtitulos = subtitulos,
            pistaSub = pistaSub,
            escalaSub = escalaSub,
            modoVideo = modoVideo,
            onAudio = { pista ->
                audioElegido = pista.posicion
                com.xuperclean.mobile.data.WatchStore.saveAudioIndex(idContenido, pista.posicion)
                aplicarPistaAudio(pista)
            },
            onIdioma = { codigo -> aplicarIdioma(codigo) },
            onSubtitulo = { pistaSub = it },
            onEscala = { escalaSub = it },
            onModoVideo = { nuevo ->
                if (nuevo != modoVideo) {
                    Diag.i("Vídeo: modo elegido a mano: $nuevo")
                    reanudarEn = maxOf(player.currentPosition, ultimaPosicionValida)
                    modoVideo = nuevo
                    PlaybackPrefs.modoVideo = nuevo
                    if (nuevo == PlaybackPrefs.ModoVideo.SOFTWARE) {
                        // Se comprueba igual que si lo hubiera elegido la app: en un
                        // aparato lento el software puede no dar abasto.
                        vigilante.iniciarPruebaSoftware(
                            android.os.SystemClock.elapsedRealtime(),
                            diagnostico.descartados,
                            automatica = false,
                        )
                    }
                }
            },
            onCopiarInforme = { copiarInforme() },
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

/**
 * Pista de la barra de avance con el tramo ya descargado.
 *
 * El gris representa lo que está en memoria (se puede avanzar sin cortes) y el
 * violeta lo reproducido; así se ve de un vistazo cuánto hay por delante.
 */
@Composable
private fun PistaConBuffer(
    fraccionReproducida: Float,
    fraccionBuffer: Float,
    modifier: Modifier = Modifier,
) {
    val primario = MaterialTheme.colorScheme.primary
    val grisBuffer = Color(0xFFB3B3B3)
    val pista = Color.White.copy(alpha = 0.22f)
    val f = fraccionReproducida.coerceIn(0f, 1f)
    val fb = fraccionBuffer.coerceIn(0f, 1f).coerceAtLeast(f)
    Box(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(pista),
    ) {
        Box(Modifier.fillMaxWidth(fb).fillMaxHeight().background(grisBuffer))
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(primario))
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
    pistasAudio: List<PistaAudio>,
    audioElegido: Int,
    audioAuto: Int,
    audios: List<com.xuperclean.core.AudioTrack>,
    idiomaAuto: String?,
    subtitulos: List<SubtitleTrack>,
    pistaSub: String?,
    escalaSub: Float,
    modoVideo: PlaybackPrefs.ModoVideo,
    onAudio: (PistaAudio) -> Unit,
    onIdioma: (String) -> Unit,
    onSubtitulo: (String?) -> Unit,
    onEscala: (Float) -> Unit,
    onModoVideo: (PlaybackPrefs.ModoVideo) -> Unit,
    onCopiarInforme: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reproducción") },
        text = {
            Column(
                Modifier
                    // El contenido puede no caber en pantallas pequeñas; sin esto el
                    // botón del informe quedaba fuera de la vista y era invisible.
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Decodificador primero: es la palanca que evita los cuelgues de imagen
                // del decodificador del fabricante y no debe quedar fuera de la vista.
                Text("Vídeo", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        PlaybackPrefs.ModoVideo.AUTO to "Automático",
                        PlaybackPrefs.ModoVideo.HARDWARE to "Hardware",
                        PlaybackPrefs.ModoVideo.SOFTWARE to "Software",
                    ).forEach { (modo, etiqueta) ->
                        androidx.compose.material3.FilterChip(
                            selected = modoVideo == modo,
                            onClick = { onModoVideo(modo) },
                            label = { Text(etiqueta) },
                        )
                    }
                }
                // Audio: una entrada por PISTA real del contenedor. Con dos pistas `spa`
                // aparecen las dos, algo imposible con la lista de idiomas deducida.
                // Si el contenedor aún no ha publicado ninguna, se usan los idiomas que
                // declara el portal.
                val porPista = pistasAudio.isNotEmpty()
                if (porPista || audios.size > 1) {
                    Text("Audio", style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (porPista) {
                            val activa = if (audioElegido >= 0) audioElegido else audioAuto
                            pistasAudio.forEach { pista ->
                                androidx.compose.material3.FilterChip(
                                    selected = activa == pista.posicion,
                                    onClick = { onAudio(pista) },
                                    label = { Text(pista.etiqueta) },
                                )
                            }
                        } else {
                            val activo = idiomaAuto.orEmpty()
                            audios.forEach { idioma ->
                                androidx.compose.material3.FilterChip(
                                    selected = activo == idioma.code,
                                    onClick = { onIdioma(idioma.code) },
                                    label = { Text(idioma.label) },
                                )
                            }
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
                }

                androidx.compose.material3.OutlinedButton(
                    onClick = onCopiarInforme,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copiar informe de reproducción")
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

private fun nombreEstado(estado: Int): String = when (estado) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> "DESCONOCIDO($estado)"
}

/**
 * Traza de Media3: decodificador elegido, cargas de red, descartes y errores.
 * Escribe solo con el diagnóstico activo; en producción no genera ruido.
 */
private class DiagnosticoListener : AnalyticsListener {
    @Volatile var decodificador: String? = null
    @Volatile var descargados: Long = 0L
    @Volatile var descartados: Int = 0
    @Volatile var underruns: Int = 0
    @Volatile var ultimaTasaBps: Long = 0L

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        decodificador = decoderName
        Diag.i("Vídeo: decodificador «$decoderName» listo en $initializationDurationMs ms")
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        Diag.i(
            "Vídeo: formato ${format.width}x${format.height} ${format.sampleMimeType} " +
                "sar=${format.pixelWidthHeightRatio} fps=${format.frameRate} reutilización=$decoderReuseEvaluation",
        )
    }

    override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
        Diag.i("Vídeo: tamaño ${videoSize.width}x${videoSize.height} relación=${videoSize.pixelWidthHeightRatio}")
    }

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        surface: Any,
        renderedFirstFrameTimestampMs: Long,
    ) {
        Diag.i("Vídeo: primer fotograma pintado en $surface a los $renderedFirstFrameTimestampMs ms")
    }

    override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, error: Exception) {
        Diag.w("Vídeo: ERROR del decodificador: ${error.javaClass.simpleName}: ${error.message}", error)
    }

    override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, error: Exception) {
        Diag.w("Audio: ERROR del decodificador: ${error.javaClass.simpleName}: ${error.message}", error)
    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long,
    ) {
        descartados += droppedFrames
        if (Diag.verboso) Diag.i("Vídeo: +$droppedFrames descartados (total $descartados) en $elapsedMs ms")
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializationDurationMs: Long,
    ) {
        Diag.i("Audio: decodificador «$decoderName» listo en $initializationDurationMs ms")
    }

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long,
    ) {
        underruns++
        Diag.w("Audio: underrun #$underruns (búfer=$bufferSize B, $bufferSizeMs ms, espera=$elapsedSinceLastFeedMs ms)")
    }

    override fun onLoadStarted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) {
        val uri = loadEventInfo.uri
        Diag.i("Red: abriendo ${uri.host}${uri.path} tipo=${mediaLoadData.trackType}")
    }

    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) {
        val bytes = loadEventInfo.bytesLoaded
        val ms = loadEventInfo.loadDurationMs.coerceAtLeast(1L)
        descargados += bytes
        ultimaTasaBps = bytes * 8000L / ms
        Diag.i("Red: carga completa $bytes B en $ms ms (${ultimaTasaBps / 1024} KB/s)")
    }

    override fun onLoadCanceled(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) {
        Diag.w("Red: carga cancelada tras ${loadEventInfo.bytesLoaded} B en ${loadEventInfo.loadDurationMs} ms")
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean,
    ) {
        Diag.w(
            "Red: ERROR de carga tras ${loadEventInfo.bytesLoaded} B en ${loadEventInfo.loadDurationMs} ms",
            error,
        )
    }

    override fun onDownstreamFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        mediaLoadData: MediaLoadData,
    ) {
        val formato = mediaLoadData.trackFormat
        Diag.i("Pista: tipo=${mediaLoadData.trackType} ${formato?.sampleMimeType} ${formato?.width}x${formato?.height}")
    }

    override fun onLoadingChanged(eventTime: AnalyticsListener.EventTime, isLoading: Boolean) {
        Diag.i("Estado: cargando=$isLoading")
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, playbackState: Int) {
        Diag.i("Estado: ${nombreEstado(playbackState)} en ${eventTime.currentPlaybackPositionMs} ms")
    }

    override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
        Diag.i("Estado: isPlaying=$isPlaying en ${eventTime.currentPlaybackPositionMs} ms")
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        Diag.w("ERROR de reproducción ${error.errorCodeName}: ${error.message}", error)
    }
}

/** Espera entre escalones de recuperación de imagen. */
private const val ESPERA_PASO = 2_500L

/**
 * Memoria del vigilante de imagen.
 *
 * Vive fuera del motor a propósito: una reconstrucción del reproductor (paso 2) no debe
 * borrar lo aprendido, porque volver a empezar por el paso más barato es justo lo que
 * producía ciclos de congelación y reanudación cada minuto.
 */
private class VigilanteImagen {
    var enCurso = false
    var paso = -1
    var ultimaAccion = 0L
    var ultimoSalto = 0L

    /** Congelamientos atendidos dentro de la ventana actual. */
    var episodios = 0
    private var ventana = 0L

    var fuenteCambiada = false

    /** El decodificador software ya se probó y no daba abasto. */
    var softwareDescartado = false

    /** Fin de la prueba de rendimiento del decodificador software (0 = sin prueba). */
    var pruebaHasta = 0L
    var descartesPrueba = 0

    /** Solo se revierte lo que eligió la app: una elección del usuario se respeta. */
    var pruebaAutomatica = false

    /** Empieza a medir si el decodificador software mantiene el ritmo. */
    fun iniciarPruebaSoftware(ahora: Long, descartes: Int, automatica: Boolean) {
        pruebaHasta = ahora + 25_000L
        descartesPrueba = descartes
        pruebaAutomatica = automatica
    }

    fun registrarEpisodio() {
        val ahora = android.os.SystemClock.elapsedRealtime()
        if (ahora - ventana > VENTANA) {
            ventana = ahora
            episodios = 0
        }
        episodios++
    }

    /**
     * Primer paso de un congelamiento nuevo. Si ya se han atendido dos en cinco minutos
     * el problema no es puntual: se va directo al decodificador software, que es lo que
     * arregló el caso en el aparato real, en vez de repetir el retroceso.
     */
    fun pasoInicial(): Int = if (episodios >= 2) 1 else 0

    private companion object {
        const val VENTANA = 300_000L
    }
}

/** «1080p» → 1080; null si la etiqueta de la fuente no trae resolución. */
private fun calidadDe(fuente: VodStream?): Int? {
    val variante = fuente?.variant ?: return null
    val texto = listOfNotNull(variante.quality, variante.videoFormat, fuente.label)
        .joinToString(" ")
    return Regex("(\\d{3,4})").findAll(texto)
        .mapNotNull { coincidencia -> coincidencia.groupValues[1].toIntOrNull() }
        .maxOrNull()
        ?.takeIf { it in 144..4320 }
}
/**
 * Una pista de audio real del contenedor.
 *
 * Media3 publica un grupo por PID de audio del TS, casi siempre de un solo índice, así
 * que la pista se identifica por [grupo] + [indice] y se ordena con [posicion].
 */
private data class PistaAudio(
    val grupo: androidx.media3.common.TrackGroup,
    val indice: Int,
    val posicion: Int,
    val etiqueta: String,
)

/** Códigos del `audioInfo` en orden y SIN deduplicar: cada uno es una pista del TS. */
private fun codigosDeAudio(audioInfo: String?): List<String> {
    if (audioInfo.isNullOrBlank()) return emptyList()
    return audioInfo.split(',', ';', '/', '|')
        .mapNotNull { com.xuperclean.core.AudioTracks.normalize(it) }
}

/**
 * Pistas de audio que el extractor encontró en el `_media.ts`.
 *
 * El contenedor etiqueta las pistas `spa`/`eng`, pero cuando hay dos `spa` (latino y
 * castellano) no las distingue. La etiqueta se completa con la POSICIÓN dentro del
 * `audioInfo`, que es el mismo orden del PMT y lo único que permite separarlas.
 */
private fun pistasDeAudio(player: Player, codigos: List<String>): List<PistaAudio> {
    val grupos = player.currentTracks.groups.filter {
        it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO
    }
    if (grupos.isEmpty()) return emptyList()
    val repetidos = mutableMapOf<String, Int>()
    val lista = ArrayList<PistaAudio>()
    for (grupo in grupos) {
        for (i in 0 until grupo.length) {
            if (!grupo.isTrackSupported(i)) continue
            val formato = grupo.getTrackFormat(i)
            val posicion = lista.size
            val base = codigos.getOrNull(posicion)
                ?.let { com.xuperclean.core.AudioTracks.label(it) }
                ?: formato.language?.let { com.xuperclean.core.AudioTracks.label(it) }
                ?: formato.label
                ?: "Pista ${posicion + 1}"
            val veces = (repetidos[base] ?: 0) + 1
            repetidos[base] = veces
            lista += PistaAudio(
                grupo = grupo.mediaTrackGroup,
                indice = i,
                posicion = posicion,
                etiqueta = if (veces > 1) "$base $veces" else base,
            )
        }
    }
    return lista
}
