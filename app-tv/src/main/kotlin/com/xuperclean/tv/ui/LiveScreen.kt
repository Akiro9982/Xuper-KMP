package com.xuperclean.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.xuperclean.core.model.Channel
import com.xuperclean.tv.data.XuperRepository
import com.xuperclean.tv.ui.common.EmptyState
import com.xuperclean.tv.ui.common.LoadingState
import com.xuperclean.tv.ui.common.UiState
import com.xuperclean.tv.ui.common.rememberAsync
import com.xuperclean.tv.ui.player.PlayerPayload
import com.xuperclean.tv.ui.theme.tvCardColors
import com.xuperclean.tv.ui.theme.tvFocusBorder
import com.xuperclean.tv.ui.theme.tvChipColors

/** Normaliza tildes y mayúsculas para que «pelicula» encuentre «Película». */
private val Acentos = Regex("\\p{M}+")

private fun normalizar(texto: String): String =
    java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
        .replace(Acentos, "")
        .lowercase()

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun LiveScreen(onOpenChannel: (PlayerPayload) -> Unit) {
    var categoria by remember { mutableStateOf<Long?>(null) }
    var busqueda by remember { mutableStateOf("") }
    // Categorías reales del portal (#3): 38 en el árbol LIVE.
    val categorias = rememberAsync("live-categories") { XuperRepository.liveCategories() }
    val listaCategorias = (categorias as? UiState.Ready)?.value.orEmpty()

    // Los chips NO se desmontan al cambiar de categoría: si desaparecen, el foco del
    // D-pad cae al rail lateral y la guía queda inalcanzable.
    val chipsFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val chipColors = tvChipColors()
    Column(Modifier.fillMaxSize()) {
        val nombreCat = listaCategorias.firstOrNull { it.id == categoria }?.name
        val cargando = rememberAsync("live-channels-$categoria") {
            val id = categoria
            if (id == null) XuperRepository.liveChannels()
            else XuperRepository.liveChannelsPorColumna(id)
        }
        val canales = (cargando as? UiState.Ready)?.value
        // Filtrado local inmediato: la guía completa ya está en memoria y no se vuelve
        // a consultar el portal por cada tecla.
        val filtrados = remember(canales, busqueda) {
            val q = normalizar(busqueda.trim())
            if (q.isEmpty()) canales.orEmpty()
            else canales.orEmpty().filter { c ->
                normalizar(c.alias.orEmpty()).contains(q) ||
                    normalizar(c.name).contains(q) ||
                    c.channelNumber?.toString()?.contains(q) == true
            }
        }
        CatalogHeader(
            when {
                canales == null && nombreCat == null -> "TV en vivo"
                canales == null -> nombreCat.orEmpty()
                busqueda.isNotBlank() -> "TV en vivo · ${filtrados.size} de ${canales.size} canales"
                nombreCat == null -> "TV en vivo · ${canales.size} canales"
                else -> "$nombreCat · ${canales.size} canales"
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 2.dp)
                .onPreviewKeyEvent { e ->
                    // Desde el campo (o su icono de limpiar) bajar lleva a las categorías.
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                        runCatching { chipsFocus.requestFocus() }.isSuccess
                    } else {
                        false
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                singleLine = true,
                label = { Text("Buscar canal") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                    focusedLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 8.dp),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .focusRequester(chipsFocus)
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
                    colors = chipColors,
                    content = { TvText("Todos") },
                )
            }
            items(listaCategorias, key = { it.id }) { c ->
                FilterChip(
                    selected = categoria == c.id,
                    onClick = { categoria = c.id },
                    colors = chipColors,
                    content = { TvText(c.name ?: "Categoría") },
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
                is UiState.Ready -> if (filtrados.isEmpty()) {
                    EmptyState(
                        "No hay canales que coincidan con \"${busqueda.trim()}\".",
                        Modifier.fillMaxSize(),
                    )
                } else {
                    ChannelGrid(
                        channels = filtrados,
                        gridFocus = gridFocus,
                        onOpenChannel = onOpenChannel,
                    )
                }
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
        columns = GridCells.Adaptive(240.dp),
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
            .fillMaxWidth()
            .height(90.dp)
            .onFocusChanged { enfocado = it.isFocused },
        colors = tvCardColors(),
        border = tvFocusBorder(),
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
