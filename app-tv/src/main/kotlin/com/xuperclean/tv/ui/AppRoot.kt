package com.xuperclean.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.xuperclean.core.update.UpdateState
import com.xuperclean.core.WatchProgress
import com.xuperclean.core.model.EpisodeInfo
import com.xuperclean.core.model.VodAsset
import com.xuperclean.tv.ui.detail.DetailScreen
import com.xuperclean.tv.ui.player.PlayerPayload
import com.xuperclean.tv.ui.theme.tvCardColors
import com.xuperclean.tv.ui.player.PlayerScreen
import com.xuperclean.tv.ui.settings.SettingsScreen
import com.xuperclean.tv.update.AppUpdates

/**
 * Destinos de primer nivel (IA heredada de la app original, probada).
 * El menú lateral es la navegación; abrir detalle/reproductor es una acción de pila.
 */
sealed class Tab(val label: String) {
    data object Live : Tab("TV en vivo")
    data object Destacados : Tab("Destacados")
    data object Pelicula : Tab("Película")
    data object Series : Tab("Series")
    data object Kids : Tab("Kids")
    data object Anime : Tab("Anime")
    data object Explorar : Tab("Explorar")
    data object Ajustes : Tab("Ajustes")
}

/** Pantallas de pila (por encima de las pestañas). */
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

private val TABS = listOf(
    Tab.Live, Tab.Destacados, Tab.Pelicula, Tab.Series, Tab.Kids, Tab.Anime, Tab.Explorar,
    Tab.Ajustes,
)

private fun iconOf(tab: Tab) = when (tab) {
    Tab.Live -> Icons.Filled.LiveTv
    Tab.Destacados -> Icons.Filled.PlayArrow
    Tab.Pelicula -> Icons.Filled.Movie
    Tab.Series -> Icons.Filled.SmartDisplay
    Tab.Kids -> Icons.Filled.ChildFriendly
    Tab.Anime -> Icons.Filled.Animation
    Tab.Explorar -> Icons.Filled.Search
    Tab.Ajustes -> Icons.Filled.Settings
}

@Composable
fun AppRoot() {
    // Escala de TV: se diseña sobre un lienzo virtual de 1024 dp de ancho (16:9).
    // En cualquier resolución —720p, 1080p, 4K o un panel que reporte otra densidad—
    // texto, iconos, separación y foco crecen o decrecen juntos, así que la interfaz
    // mantiene las mismas proporciones y nunca queda amontonada.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val factor = (maxWidth.value / 1024f).coerceIn(0.5f, 3f)
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * factor, base.fontScale),
        ) {
            TvContent()
        }
    }
}

@Composable
private fun TvContent() {
    var tab by remember { mutableStateOf<Tab>(Tab.Destacados) }
    val stack = remember { mutableStateListOf<Overlay>() }
    val updateState by AppUpdates.current.state.collectAsState()

    LaunchedEffect(Unit) { AppUpdates.current.checkIfIdle() }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    /**
     * El panel lateral siempre es navegación de primer nivel: al elegir una pestaña
     * se cierra cualquier detalle/reproductor apilado. Antes solo cambiaba `tab`
     * por debajo del detalle, así que el usuario seguía viendo la ficha anterior
     * hasta pulsar Atrás.
     */
    fun irATab(destino: Tab) {
        stack.clear()
        tab = destino
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val overlay = stack.lastOrNull()

    if (overlay is Overlay.Player) {
        // el reproductor es a pantalla completa, sin menú
        PlayerScreen(payload = overlay.payload, onClose = { pop() })
        return
    }

    Row(Modifier.fillMaxSize()) {
        SideNav(
            tab = tab,
            onSelect = ::irATab,
            hayActualizacion = updateState is UpdateState.Available,
        )
        // Sin transición entre pantallas: en paneles de 1 GB, animar la pantalla
        // saliente y la entrante a la vez duplica la composición y las descargas de
        // pósters, justo cuando el usuario percibe el cambio como «lento». El cambio
        // directo libera la pantalla anterior y cancela sus cargas de imagen.
        Box(Modifier.fillMaxSize()) {
            when (val state = overlay ?: tab) {
                is Overlay.Detail -> DetailScreen(
                    asset = state.asset,
                    onPlay = { payload -> stack.add(Overlay.Player(payload)) },
                    onOpenAsset = { a -> stack.add(Overlay.Detail(a)) },
                    onClose = { pop() },
                )
                is Overlay.Player -> Unit
                Tab.Live -> LiveScreen(onOpenChannel = { payload -> stack.add(Overlay.Player(payload)) })
                Tab.Destacados -> DestacadosScreen(
                    onOpenAsset = { a -> stack.add(Overlay.Detail(a)) },
                    // «Continuar viendo» reanuda directamente el capítulo guardado (#8).
                    onResume = { p -> stack.add(Overlay.Player(p.toPlayerPayload())) },
                )
                Tab.Pelicula -> CatalogScreen(kind = CatalogKind.Pelicula, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                Tab.Series -> CatalogScreen(kind = CatalogKind.Series, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                Tab.Kids -> CatalogScreen(kind = CatalogKind.Kids, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                Tab.Anime -> CatalogScreen(kind = CatalogKind.Anime, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                Tab.Explorar -> SearchScreen(onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                Tab.Ajustes -> SettingsScreen()
            }
        }
    }
}

/**
 * Menú lateral de navegación (patrón drawer/rail M3 para TV).
 * Selección: contenedor + icono + texto (no solo color); foco: Surface de tv-material.
 */
@Composable
private fun SideNav(tab: Tab, onSelect: (Tab) -> Unit, hayActualizacion: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(240.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TABS.forEach { t ->
            val selected = tab == t
            // tv-material publica su propio LocalContentColor, que material3.Text/Icon no
            // leen: sin color explícito el texto cae a negro sobre el fondo enfocado.
            var enfocado by remember { mutableStateOf(false) }
            val colorTexto = when {
                enfocado -> MaterialTheme.colorScheme.onPrimaryContainer
                selected -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                onClick = { onSelect(t) },
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .onFocusChanged { enfocado = it.isFocused },
                colors = tvCardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Box {
                        Icon(
                            imageVector = iconOf(t),
                            contentDescription = null,
                            tint = colorTexto,
                            modifier = Modifier.size(26.dp),
                        )
                        if (hayActualizacion && t == Tab.Ajustes) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = t.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = colorTexto,
                    )
                }
            }
        }
    }
}