package com.xuperclean.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xuperclean.core.model.Channel
import com.xuperclean.core.model.VodAsset
import com.xuperclean.mobile.data.XuperRepository
import com.xuperclean.mobile.ui.common.EmptyState
import com.xuperclean.mobile.ui.common.LoadingState
import com.xuperclean.mobile.ui.common.PosterImage
import com.xuperclean.mobile.ui.common.UiState
import com.xuperclean.mobile.ui.common.rememberAsync
import com.xuperclean.mobile.ui.player.PlayerPayload

/** Secciones de catálogo: filtros reales sobre el pool del home (type/tags del portal). */
enum class CatalogKind(val title: String, val matches: (VodAsset) -> Boolean) {
    Pelicula("Películas", { it.type == "1" || it.programType.equals("movie", ignoreCase = true) }),
    Series("Series", { it.isSeries }),
}

/** Compara contra los tags del portal, el único clasificador real del catálogo free. */
internal fun VodAsset.hasAnyTag(vararg wanted: String): Boolean {
    val list = tags?.lowercase()?.split(',')?.map { it.trim() } ?: return false
    return wanted.any { w -> list.any { it == w } }
}

@Composable
fun CatalogScreen(kind: CatalogKind, onOpenAsset: (VodAsset) -> Unit) {
    when (val state = rememberAsync("catalog-${kind.name}") {
        XuperRepository.homeAssets()
    }) {
        is UiState.Loading -> LoadingState(Modifier.fillMaxSize())
        is UiState.Error -> EmptyState("No se pudo cargar:\n${state.message}", Modifier.fillMaxSize())
        is UiState.Ready -> CatalogGrid(kind, state.value.filter(kind.matches), onOpenAsset)
    }
}

@Composable
private fun CatalogGrid(kind: CatalogKind, assets: List<VodAsset>, onOpenAsset: (VodAsset) -> Unit) {
    if (assets.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            Header(kind.title)
            EmptyState("Todavía no hay contenido disponible en esta sección.", Modifier.fillMaxSize())
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Header(kind.title)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(assets, key = { it.contentId }) { asset ->
                GridCard(asset, onOpenAsset)
            }
        }
    }
}

@Composable
internal fun Header(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp),
    )
}

@Composable
private fun GridCard(asset: VodAsset, onOpenAsset: (VodAsset) -> Unit) {
    androidx.compose.material3.Card(
        onClick = { onOpenAsset(asset) },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        PosterImage(asset = asset, modifier = Modifier.fillMaxSize(), showTitle = true, light = false)
    }
}

@Composable
fun LiveScreen(onOpenChannel: (PlayerPayload) -> Unit) {
    var categoria by remember { mutableStateOf<Long?>(null) }
    val categorias = rememberAsync("live-categories") { XuperRepository.liveCategories() }
    val listaCategorias = (categorias as? UiState.Ready)?.value.orEmpty()

    when (val state = rememberAsync("live-channels-$categoria") {
        val id = categoria
        if (id == null) XuperRepository.liveChannels()
        else XuperRepository.liveChannelsPorColumna(id)
    }) {
        is UiState.Loading -> LoadingState(Modifier.fillMaxSize())
        is UiState.Error -> EmptyState("No se pudo cargar la guía:\n${state.message}", Modifier.fillMaxSize())
        is UiState.Ready -> LiveGrid(
            channels = state.value,
            categorias = listaCategorias,
            categoria = categoria,
            onCategoria = { categoria = it },
            onOpenChannel = onOpenChannel,
        )
    }
}

@Composable
private fun LiveGrid(
    channels: List<Channel>,
    categorias: List<com.xuperclean.core.model.ColumnInfo>,
    categoria: Long?,
    onCategoria: (Long?) -> Unit,
    onOpenChannel: (PlayerPayload) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        val nombreCat = categorias.firstOrNull { it.id == categoria }?.name
        Header(if (nombreCat == null) "TV en vivo · ${channels.size} canales" else "$nombreCat · ${channels.size}")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            item("all") {
                FilterChip(
                    selected = categoria == null,
                    onClick = { onCategoria(null) },
                    label = { Text("Todos") },
                )
            }
            items(categorias, key = { it.id }) { c ->
                FilterChip(
                    selected = categoria == c.id,
                    onClick = { onCategoria(c.id) },
                    label = { Text(c.name ?: "Categoría") },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(channels, key = { it.channelCode }) { channel ->
                ChannelCard(channel) {
                    // La URL se resuelve al pulsar (los tickets caducan)
                    onOpenChannel(PlayerPayload.Live(channel))
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.4f),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "${channel.channelNumber ?: "·"}  ${channel.alias ?: channel.name}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}
