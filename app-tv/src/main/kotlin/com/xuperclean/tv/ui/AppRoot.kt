package com.xuperclean.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.xuperclean.core.model.VodAsset
import com.xuperclean.tv.ui.detail.DetailScreen
import com.xuperclean.tv.ui.player.PlayerPayload
import com.xuperclean.tv.ui.theme.tvCardColors
import com.xuperclean.tv.ui.player.PlayerScreen

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
}

/** Pantallas de pila (por encima de las pestañas). */
sealed interface Overlay {
    data class Detail(val asset: VodAsset) : Overlay
    data class Player(val payload: PlayerPayload) : Overlay
}

private val TABS = listOf(
    Tab.Live, Tab.Destacados, Tab.Pelicula, Tab.Series, Tab.Kids, Tab.Anime, Tab.Explorar,
)

private fun iconOf(tab: Tab) = when (tab) {
    Tab.Live -> Icons.Filled.LiveTv
    Tab.Destacados -> Icons.Filled.PlayArrow
    Tab.Pelicula -> Icons.Filled.Movie
    Tab.Series -> Icons.Filled.SmartDisplay
    Tab.Kids -> Icons.Filled.ChildFriendly
    Tab.Anime -> Icons.Filled.Animation
    Tab.Explorar -> Icons.Filled.Search
}

@Composable
fun AppRoot() {
    var tab by remember { mutableStateOf<Tab>(Tab.Destacados) }
    val stack = remember { mutableStateListOf<Overlay>() }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val overlay = stack.lastOrNull()

    if (overlay is Overlay.Player) {
        // el reproductor es a pantalla completa, sin menú
        PlayerScreen(payload = overlay.payload, onClose = { pop() })
        return
    }

    Row(Modifier.fillMaxSize()) {
        SideNav(tab = tab, onSelect = { tab = it })
        AnimatedContent(
            targetState = overlay ?: tab,
            transitionSpec = {
                // motion leve y funcional: fundido + escala mínima (200 ms)
                (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.985f, animationSpec = tween(200)))
                    .togetherWith(fadeOut(animationSpec = tween(150)))
            },
            label = "content",
            modifier = Modifier.fillMaxSize(),
        ) { state ->
            when (state) {
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
                    onResume = { p -> stack.add(Overlay.Player(PlayerPayload.Vod(p.toVodAsset()))) },
                )
                Tab.Pelicula -> CatalogScreen(kind = CatalogKind.Pelicula, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                Tab.Series -> CatalogScreen(kind = CatalogKind.Series, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                Tab.Kids -> CatalogScreen(kind = CatalogKind.Kids, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                Tab.Anime -> CatalogScreen(kind = CatalogKind.Anime, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                Tab.Explorar -> SearchScreen(onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
            }
        }
    }
}

/**
 * Menú lateral de navegación (patrón drawer/rail M3 para TV).
 * Selección: contenedor + icono + texto (no solo color); foco: Surface de tv-material.
 */
@Composable
private fun SideNav(tab: Tab, onSelect: (Tab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(210.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    .padding(horizontal = 16.dp)
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
                    modifier = Modifier.padding(horizontal = 14.dp),
                ) {
                    Icon(
                        imageVector = iconOf(t),
                        contentDescription = null,
                        tint = colorTexto,
                    )
                    Spacer(Modifier.width(12.dp))
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
