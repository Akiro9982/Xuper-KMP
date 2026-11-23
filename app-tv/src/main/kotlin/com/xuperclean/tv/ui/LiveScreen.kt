package com.xuperclean.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.xuperclean.core.model.Channel
import com.xuperclean.core.model.ColumnInfo
import com.xuperclean.tv.data.XuperRepository
import com.xuperclean.tv.ui.common.EmptyState
import com.xuperclean.tv.ui.common.LoadingState
import com.xuperclean.tv.ui.common.UiState
import com.xuperclean.tv.ui.common.rememberAsync
import com.xuperclean.tv.ui.player.PlayerPayload
import com.xuperclean.tv.ui.theme.tvCardColors

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun LiveScreen(onOpenChannel: (PlayerPayload) -> Unit) {
    var categoria by remember { mutableStateOf<Long?>(null) }
    // Categorías reales del portal (#3): 38 en el árbol LIVE.
    val categorias = rememberAsync("live-categories") { XuperRepository.liveCategories() }
    val listaCategorias = (categorias as? UiState.Ready)?.value.orEmpty()

    // Los chips NO se desmontan al cambiar de categoría: si desaparecen, el foco del
    // D-pad cae al rail lateral y la guía queda inalcanzable.
    val gridFocus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        val nombreCat = listaCategorias.firstOrNull { it.id == categoria }?.name
        val cargando = rememberAsync("live-channels-$categoria") {
            val id = categoria
            if (id == null) XuperRepository.liveChannels()
            else XuperRepository.liveChannelsPorColumna(id)
        }
        val canales = (cargando as? UiState.Ready)?.value
        CatalogHeader(
            when {
                canales != null && nombreCat == null -> "TV en vivo · ${canales.size} canales"
                canales != null -> "$nombreCat · ${canales.size} canales"
                nombreCat == null -> "TV en vivo"
                else -> nombreCat
            },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(bottom = 12.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                        // La rejilla puede no existir todavía (cargando/error): sin este
                        // guardia, requestFocus sobre un FocusRequester sin adjuntar lanza.
                        runCatching { gridFocus.requestFocus() }.isSuccess
                    } else {
                        false
                    }
                },
        ) {
            item("all") {
                FilterChip(
                    selected = categoria == null,
                    onClick = { categoria = null },
                    content = { androidx.compose.material3.Text("Todos") },
                )
            }
            items(listaCategorias, key = { it.id }) { c ->
                FilterChip(
                    selected = categoria == c.id,
                    onClick = { categoria = c.id },
                    content = { androidx.compose.material3.Text(c.name ?: "Categoría") },
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (val state = cargando) {
                is UiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is UiState.Error -> EmptyState(
                    "No se pudo cargar la guía:\n${state.message}",
                    Modifier.fillMaxSize(),
                )
                is UiState.Ready -> ChannelGrid(
                    channels = state.value,
                    gridFocus = gridFocus,
                    onOpenChannel = onOpenChannel,
                )
            }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelGrid(
    channels: List<Channel>,
    gridFocus: FocusRequester,
    onOpenChannel: (PlayerPayload) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(280.dp),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(gridFocus),
    ) {
        items(channels, key = { it.channelCode }) { channel ->
            ChannelCard(channel) {
                // La URL se resuelve al pulsar (los tickets caducan); el reproductor
                // muestra "Conectando…" mientras tanto.
                onOpenChannel(PlayerPayload.Live(channel))
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    // tv-material publica su propio LocalContentColor y material3.Text no lo lee: con el
    // contenedor de foco violeta el texto caería a negro y desaparecería.
    var enfocado by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(280.dp)
            .height(90.dp)
            .onFocusChanged { enfocado = it.isFocused },
        colors = tvCardColors(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val logo = channel.posterUrl
            if (!logo.isNullOrBlank()) {
                AsyncImage(
                    model = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(56.dp)
                        .height(56.dp),
                )
            }
            androidx.compose.material3.Text(
                text = "${channel.channelNumber ?: "·"}  ${channel.alias ?: channel.name}",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = if (enfocado) {
                    androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
            )
        }
    }
}
