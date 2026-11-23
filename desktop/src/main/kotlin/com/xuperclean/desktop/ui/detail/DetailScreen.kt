package com.xuperclean.desktop.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xuperclean.core.SeasonEntry
import com.xuperclean.core.SeriesGrouping
import com.xuperclean.core.model.EpisodeInfo
import com.xuperclean.core.model.VodAsset
import com.xuperclean.desktop.data.XuperRepository
import com.xuperclean.desktop.ui.CatalogHeader
import com.xuperclean.desktop.ui.common.EmptyState
import com.xuperclean.desktop.ui.common.LoadingState
import com.xuperclean.desktop.ui.common.PosterImage
import com.xuperclean.desktop.ui.common.UiState
import com.xuperclean.desktop.ui.common.rememberAsync
import com.xuperclean.desktop.ui.player.PlayerPayload

private val DetailScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.10f),
    0.55f to Color.Black.copy(alpha = 0.45f),
    1f to Color.Black.copy(alpha = 0.92f),
)

@Composable
fun DetailScreen(
    asset: VodAsset,
    onBack: () -> Unit,
    onPlay: (PlayerPayload) -> Unit,
    onOpenAsset: (VodAsset) -> Unit,
) {
    var actual by remember(asset.contentId) { mutableStateOf(asset) }
    val isSeries = actual.isSeries
    val type = if (isSeries) "0" else "1"

    // Temporadas hermanas: el portal las devuelve como assets sueltos en la búsqueda.
    val temporadas = rememberAsync("seasons-${asset.contentId}") {
        if (asset.isSeries) XuperRepository.seasonsFor(asset) else emptyList()
    }

    when (val state = rememberAsync("detail-${actual.contentId}") {
        XuperRepository.itemData(actual.contentId, type)
    }) {
        is UiState.Loading -> LoadingState(Modifier.fillMaxSize(), "Cargando ficha")
        is UiState.Error -> EmptyState("No se pudo cargar la ficha:\n${state.message}", Modifier.fillMaxSize())
        is UiState.Ready -> {
            val full = state.value
            val data = full.toVodAsset().let { base ->
                if (base.posterList.isEmpty()) base.copy(posterList = asset.posterList) else base
            }
            DetailContent(
                asset = data,
                episodios = full.simpleProgramList,
                isSeries = isSeries,
                temporadas = (temporadas as? UiState.Ready)?.value.orEmpty(),
                onSelectSeason = { actual = it },
                onBack = onBack,
                onPlay = onPlay,
                onOpenAsset = onOpenAsset,
            )
        }
    }
}

@Composable
private fun DetailContent(
    asset: VodAsset,
    episodios: List<EpisodeInfo>,
    isSeries: Boolean,
    temporadas: List<SeasonEntry>,
    onSelectSeason: (VodAsset) -> Unit,
    onBack: () -> Unit,
    onPlay: (PlayerPayload) -> Unit,
    onOpenAsset: (VodAsset) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("header") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            ) {
                PosterImage(asset = asset, modifier = Modifier.fillMaxSize(), light = false)
                Box(Modifier.matchParentSize().background(DetailScrim))
                FilledIconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                    )
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(32.dp),
                ) {
                    Text(
                        asset.name,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    MetaLine(asset)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val primero = episodios.firstOrNull()
                        Button(
                            onClick = {
                                if (isSeries) {
                                    primero?.let { onPlay(PlayerPayload.Episode(asset, it)) }
                                } else {
                                    onPlay(PlayerPayload.Vod(asset))
                                }
                            },
                            enabled = !isSeries || primero != null,
                        ) {
                            Text(
                                if (isSeries) {
                                    "Reproducir T${SeriesGrouping.seasonNumber(asset.name)} " +
                                        "E${primero?.seriesNumber ?: 1}"
                                } else {
                                    "Reproducir"
                                },
                            )
                        }
                    }
                }
            }
        }

        if (isSeries && temporadas.size > 1) {
            item("temporadas") {
                Column {
                    Text(
                        "Temporadas",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 32.dp, bottom = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(temporadas, key = { it.asset.contentId }) { temporada ->
                            FilterChip(
                                selected = temporada.asset.contentId == asset.contentId,
                                onClick = { onSelectSeason(temporada.asset) },
                                label = { Text(temporada.label) },
                            )
                        }
                    }
                }
            }
        }

        val sinopsis = asset.description
        if (!sinopsis.isNullOrEmpty()) {
            item("sinopsis") {
                Column(Modifier.padding(horizontal = 32.dp)) {
                    Text("Sinopsis", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(sinopsis, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (isSeries && episodios.isNotEmpty()) {
            item("episodes-header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Episodios",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        listOfNotNull(
                            temporadas.firstOrNull { it.asset.contentId == asset.contentId }?.label,
                            "${episodios.size} episodios",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item("episodes") {
                EpisodeGrid(episodios) { indice, ep -> EpisodeCard(asset, ep, indice, onPlay) }
            }
        }

        item("relacionados") { RelatedSection(asset, onOpenAsset) }
    }
}

@Composable
private fun MetaLine(asset: VodAsset) {
    val partes = buildList {
        asset.score?.let { add("%.1f".format(it)) }
        asset.releaseTime?.take(4)?.let { add(it) }
        asset.duration?.takeIf { it.isNotBlank() }?.let { add(it) }
        asset.originalCountry?.let { add(it) }
    }
    if (partes.isNotEmpty()) {
        Text(
            partes.joinToString("  ·  "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/**
 * Rejilla de episodios que crece en vertical: el ancho disponible decide cuántas tarjetas
 * caben por fila, así la temporada completa queda a la vista y la página se desplaza.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeGrid(
    episodios: List<EpisodeInfo>,
    content: @Composable (indice: Int, ep: EpisodeInfo) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
    ) {
        val separacion = 12.dp
        val columnas = ((maxWidth + separacion) / (220.dp + separacion)).toInt().coerceAtLeast(2)
        val ancho = (maxWidth - separacion * (columnas - 1)) / columnas
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(separacion),
            verticalArrangement = Arrangement.spacedBy(separacion),
            maxItemsInEachRow = columnas,
        ) {
            episodios.forEachIndexed { indice, ep ->
                Box(Modifier.width(ancho)) { content(indice + 1, ep) }
            }
        }
    }
}

/**
 * Tarjeta de episodio: el portal solo da un número fiable (`seriesNumber`) y un nombre
 * crudo tipo `Serie T1_1`; se muestra únicamente «Episodio N».
 */
@Composable
private fun EpisodeCard(
    asset: VodAsset,
    ep: EpisodeInfo,
    indice: Int,
    onPlay: (PlayerPayload) -> Unit,
) {
    val numero = ep.seriesNumber?.takeIf { it > 0 } ?: indice
    Card(
        onClick = { onPlay(PlayerPayload.Episode(asset, ep)) },
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Episodio",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                numero.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RelatedSection(asset: VodAsset, onOpenAsset: (VodAsset) -> Unit) {
    when (val state = rememberAsync("related-${asset.contentId}") {
        XuperRepository.related(asset.contentId)
    }) {
        is UiState.Ready -> {
            val items = state.value.searchItemList.flatMap { it.itemList }
                .map { it.toVodAsset() }
                .filter { it.contentId != asset.contentId }
                .take(12)
            if (items.isNotEmpty()) {
                CatalogHeader("Quizás te guste")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items, key = { it.contentId }) { rel ->
                        Card(
                            onClick = { onOpenAsset(rel) },
                            modifier = Modifier.width(220.dp).height(124.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Box { PosterImage(asset = rel, modifier = Modifier.fillMaxSize(), showTitle = true) }
                        }
                    }
                }
            }
        }
        else -> Unit
    }
}
