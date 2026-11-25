package com.xuperclean.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xuperclean.core.WatchProgress
import com.xuperclean.core.model.EpisodeInfo
import com.xuperclean.core.model.VodAsset
import com.xuperclean.core.update.UpdateState
import com.xuperclean.mobile.update.AppUpdates
import com.xuperclean.mobile.ui.detail.DetailScreen
import com.xuperclean.mobile.ui.player.PlayerPayload
import com.xuperclean.mobile.ui.player.PlayerScreen
import com.xuperclean.mobile.ui.settings.SettingsScreen

/** Destinos de primer nivel (barra inferior). */
sealed class Tab(val label: String) {
    data object Inicio : Tab("Inicio")
    data object Live : Tab("TV")
    data object Pelicula : Tab("Película")
    data object Series : Tab("Series")
    data object Explorar : Tab("Buscar")
    data object Ajustes : Tab("Ajustes")
}

sealed interface Overlay {
    data class Detail(val asset: VodAsset) : Overlay
    data class Player(val payload: PlayerPayload) : Overlay
}

/**
 * «Continuar viendo» reabre el capítulo guardado como episodio, con su temporada y su
 * número reales; si se abriera como película se perderían ambos (#8).
 */
private fun WatchProgress.toPlayerPayload(): PlayerPayload {
    val epId = episodeId
    return if (isSeries && epId != null) {
        PlayerPayload.Episode(
            asset = toVodAsset(),
            episode = EpisodeInfo(
                contentId = epId,
                name = episodeName,
                seriesNumber = episodeNumber,
            ),
            tituloSerie = name,
            temporada = seasonNumber,
            serieKey = aliasKey,
        )
    } else {
        PlayerPayload.Vod(toVodAsset())
    }
}

private val TABS = listOf(Tab.Inicio, Tab.Live, Tab.Pelicula, Tab.Series, Tab.Explorar, Tab.Ajustes)

private fun iconOf(tab: Tab) = when (tab) {
    Tab.Inicio -> Icons.Filled.Home
    Tab.Live -> Icons.Filled.LiveTv
    Tab.Pelicula -> Icons.Filled.Movie
    Tab.Series -> Icons.Filled.SmartDisplay
    Tab.Explorar -> Icons.Filled.Search
    Tab.Ajustes -> Icons.Filled.Settings
}

@Composable
fun AppRoot() {
    var tab by remember { mutableStateOf<Tab>(Tab.Inicio) }
    val stack = remember { mutableStateListOf<Overlay>() }
    val updateState by AppUpdates.current.state.collectAsState()

    LaunchedEffect(Unit) { AppUpdates.current.checkIfIdle() }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    val overlay = stack.lastOrNull()

    if (overlay is Overlay.Player) {
        // Reproductor inmersivo: sin barra, con su propio botón de cierre
        // El propio reproductor gestiona «atrás»: primero sale de pantalla completa y
        // sólo después cierra (#19). Un BackHandler aquí registrado después del suyo
        // se quedaba con la tecla y salía del vídeo sin respetar la pantalla completa.
        PlayerScreen(payload = overlay.payload, onClose = { pop() })
        return
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            tab = t
                            stack.clear() // una pestaña siempre lleva al nivel superior
                        },
                        icon = {
                            Box {
                                Icon(iconOf(t), contentDescription = t.label)
                                if (t == Tab.Ajustes && updateState is UpdateState.Available) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.error, CircleShape),
                                    )
                                }
                            }
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { inner ->
        AnimatedContent(
            targetState = overlay ?: tab,
            transitionSpec = {
                (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.99f, animationSpec = tween(180)))
                    .togetherWith(fadeOut(animationSpec = tween(140)))
            },
            label = "content",
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) { state ->
            when (state) {
                is Overlay.Detail -> DetailScreen(
                    asset = state.asset,
                    onPlay = { payload -> stack.add(Overlay.Player(payload)) },
                    onOpenAsset = { a -> stack.add(Overlay.Detail(a)) },
                )
                is Overlay.Player -> Unit
                Tab.Inicio -> HomeScreen(
                    onOpenAsset = { a -> stack.add(Overlay.Detail(a)) },
                    // «Continuar viendo» reanuda directamente el capítulo guardado (#8).
                    onResume = { p -> stack.add(Overlay.Player(p.toPlayerPayload())) },
                )
                Tab.Live -> LiveScreen(onOpenChannel = { payload -> stack.add(Overlay.Player(payload)) })
                Tab.Pelicula -> CatalogScreen(CatalogKind.Pelicula, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                Tab.Series -> CatalogScreen(CatalogKind.Series, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                Tab.Explorar -> SearchScreen(onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                Tab.Ajustes -> SettingsScreen()
            }
        }
    }
}