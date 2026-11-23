package com.xuperclean.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Surface
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import kotlinx.coroutines.launch
import com.xuperclean.core.SeasonEntry
import com.xuperclean.core.SeriesGrouping
import com.xuperclean.core.model.EpisodeInfo
import com.xuperclean.core.model.VodAsset
import com.xuperclean.tv.data.XuperRepository
import com.xuperclean.tv.ui.CatalogHeader
import com.xuperclean.tv.ui.common.EmptyState
import com.xuperclean.tv.ui.common.LoadingState
import com.xuperclean.tv.ui.common.PosterImage
import com.xuperclean.tv.ui.common.UiState
import com.xuperclean.tv.ui.common.rememberAsync
import com.xuperclean.tv.ui.player.PlayerPayload
import com.xuperclean.tv.ui.theme.tvCardColors
import com.xuperclean.tv.ui.theme.tvChipColors

private val DetailScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.15f),
    0.55f to Color.Black.copy(alpha = 0.45f),
    1f to Color.Black.copy(alpha = 0.92f),
)

@Composable
fun DetailScreen(
    asset: VodAsset,
    onPlay: (PlayerPayload) -> Unit,
    onOpenAsset: (VodAsset) -> Unit,
    onClose: () -> Unit,
) {
    var actual by remember(asset.contentId) { mutableStateOf(asset) }
    val isSeries = actual.isSeries
    val type = if (isSeries) "0" else "1"

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
                // completar con lo que ya sabíamos del catálogo (posters, tags)
                if (base.posterList.isEmpty()) base.copy(posterList = asset.posterList) else base
            }
            DetailContent(
                asset = data,
                episodios = full.simpleProgramList,
                isSeries = isSeries,
                temporadas = (temporadas as? UiState.Ready)?.value.orEmpty(),
                onSelectSeason = { actual = it },
                onPlay = onPlay,
                onOpenAsset = onOpenAsset,
            )
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailContent(
    asset: VodAsset,
    episodios: List<EpisodeInfo>,
    isSeries: Boolean,
    temporadas: List<SeasonEntry>,
    onSelectSeason: (VodAsset) -> Unit,
    onPlay: (PlayerPayload) -> Unit,
    onOpenAsset: (VodAsset) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val separacionEp = 12.dp
    val columnasEp = ((maxWidth - 64.dp + separacionEp) / (130.dp + separacionEp)).toInt().coerceAtLeast(3)
    val anchoEp = (maxWidth - 64.dp - separacionEp * (columnasEp - 1)) / columnasEp
    val filasEp = episodios.chunked(columnasEp)
    val chipColors = tvChipColors()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            ) {
                PosterImage(asset = asset, modifier = Modifier.fillMaxSize(), light = false)
                Box(Modifier.matchParentSize().background(DetailScrim))
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(32.dp),
                ) {
                    Text(
                        text = asset.name,
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
            item(key = "temporadas") {
                Column {
                    CatalogHeader("Temporadas")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(temporadas, key = { it.asset.contentId }) { temporada ->
                            FilterChip(
                                selected = temporada.asset.contentId == asset.contentId,
                                onClick = { onSelectSeason(temporada.asset) },
                                colors = chipColors,
                                content = { TvText(temporada.label) },
                            )
                        }
                    }
                }
            }
        }

        if (isSeries && episodios.isNotEmpty()) {
            item(key = "episodes-header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CatalogHeader("Episodios")
                    Text(
                        listOfNotNull(
                            temporadas.firstOrNull { it.asset.contentId == asset.contentId }?.label,
                            "${episodios.size} episodios",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            itemsIndexed(filasEp, key = { fila, _ -> "ep-row-$fila" }) { fila, eps ->
                // Al enfocar cualquier tarjeta se asegura de que la fila quede a la vista,
                // también al subir con el D-pad desde filas inferiores.
                val bring = remember(fila) { BringIntoViewRequester() }
                val alcance = rememberCoroutineScope()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bring)
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(separacionEp),
                ) {
                    eps.forEachIndexed { columna, ep ->
                        Box(Modifier.width(anchoEp)) {
                            EpisodeCard(
                                asset = asset,
                                ep = ep,
                                indice = fila * columnasEp + columna + 1,
                                onPlay = onPlay,
                                modifier = Modifier.onFocusChanged { foco ->
                                    if (foco.isFocused) {
                                        alcance.launch { bring.bringIntoView() }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        val sinopsis = asset.description
        if (!sinopsis.isNullOrEmpty()) {
            item(key = "sinopsis") {
                Column(Modifier.padding(horizontal = 32.dp)) {
                    Text(
                        "Sinopsis",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        sinopsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "relacionados") {
            RelatedRow(asset, onOpenAsset, onPlay)
        }
    }
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
            text = partes.joinToString("  ·  "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/**
 * Tarjeta de episodio: el portal solo da un número fiable (`seriesNumber`) y un nombre
 * crudo tipo `Serie T1_1`; se muestra únicamente «Episodio N».
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeCard(
    asset: VodAsset,
    ep: EpisodeInfo,
    indice: Int,
    onPlay: (PlayerPayload) -> Unit,
    modifier: Modifier = Modifier,
) {
    val numero = ep.seriesNumber?.takeIf { it > 0 } ?: indice
    Surface(
        onClick = {
            onPlay(PlayerPayload.Episode(asset, ep))
        },
        colors = tvCardColors(),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp),
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

/** "Quizás te guste": searchByContent con el contentId del título. */
@Composable
private fun RelatedRow(asset: VodAsset, onOpenAsset: (VodAsset) -> Unit, onPlay: (PlayerPayload) -> Unit) {
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
                        Surface(
                            onClick = { onOpenAsset(rel) },
                            modifier = Modifier
                                .width(220.dp)
                                .height(124.dp),
                        ) {
                            PosterImage(asset = rel, modifier = Modifier.fillMaxSize(), showTitle = true, light = true)
                        }
                    }
                }
            }
        }
        else -> Unit // relacionados son best-effort: sin estado visible
    }
}
