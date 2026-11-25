package com.xuperclean.mobile.ui.detail

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.xuperclean.core.SeasonEpisode
import com.xuperclean.core.SeriesGrouping
import com.xuperclean.core.model.VodAsset
import com.xuperclean.mobile.data.XuperRepository
import com.xuperclean.mobile.ui.Header
import com.xuperclean.mobile.ui.common.EmptyState
import com.xuperclean.mobile.ui.common.LoadingState
import com.xuperclean.mobile.ui.common.PosterImage
import com.xuperclean.mobile.ui.common.UiState
import com.xuperclean.mobile.ui.common.rememberAsync
import com.xuperclean.mobile.ui.player.PlayerPayload

private val DetailScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.10f),
    0.55f to Color.Black.copy(alpha = 0.45f),
    1f to Color.Black.copy(alpha = 0.92f),
)

@Composable
fun DetailScreen(
    asset: VodAsset,
    onPlay: (PlayerPayload) -> Unit,
    onOpenAsset: (VodAsset) -> Unit,
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
                if (base.posterList.isEmpty()) base.copy(posterList = asset.posterList) else base
            }
            val listaTemporadas = (temporadas as? UiState.Ready)?.value.orEmpty()
            // Copias de la temporada abierta: la entrada del grupo y sus variantes.
            val copias = remember(listaTemporadas, actual.contentId) {
                listaTemporadas.firstOrNull { entrada ->
                    entrada.asset.contentId == actual.contentId ||
                        entrada.variantes.any { it.contentId == actual.contentId }
                }.let { entrada ->
                    entrada?.let { listOf(it.asset) + it.variantes }.orEmpty()
                        .filter { it.contentId != actual.contentId }
                }
            }
            val fusion = rememberAsync(
                "episodes-${actual.contentId}-${copias.joinToString(",") { it.contentId }}",
            ) {
                XuperRepository.episodiosDeTemporada(full, copias)
            }
            // Mientras se fusionan las copias se pinta la temporada abierta tal cual: el
            // recuento solo puede crecer, así que no hay saltos ni episodios que se pierdan.
            val episodios = (fusion as? UiState.Ready)?.value
                ?: full.simpleProgramList.map { SeasonEpisode(it, actual) }
            DetailContent(
                asset = data,
                episodios = episodios,
                isSeries = isSeries,
                temporadas = listaTemporadas,
                onSelectSeason = { actual = it },
                onPlay = onPlay,
                onOpenAsset = onOpenAsset,
            )
        }
    }
}

@Composable
private fun DetailContent(
    asset: VodAsset,
    episodios: List<SeasonEpisode>,
    isSeries: Boolean,
    temporadas: List<SeasonEntry>,
    onSelectSeason: (VodAsset) -> Unit,
    onPlay: (PlayerPayload) -> Unit,
    onOpenAsset: (VodAsset) -> Unit,
) {
    // El episodio puede venir de la copia original (nombre en otro idioma): el
    // reproductor muestra el nombre de la temporada que ve el usuario.
    // Cada capítulo vive en una copia concreta (la doblada o la original), así que la
    // temporada y el nombre visible se toman de la ficha y no del nombre del asset.
    // El alias identifica la serie aunque cada temporada cambie de doblaje.
    val serieKey = remember(temporadas) {
        temporadas.firstNotNullOfOrNull { entrada ->
            entrada.asset.alias?.takeIf { it.isNotBlank() }
        }?.let { SeriesGrouping.baseName(it) }
    }
    val temporadaPorCopia = remember(temporadas) {
        buildMap {
            temporadas.forEach { entrada ->
                put(entrada.asset.contentId, entrada)
                entrada.variantes.forEach { put(it.contentId, entrada) }
            }
        }
    }
    // La ficha manda: la copia abierta puede ser la original (nombre en otro idioma) y
    // no llevar la temporada en el nombre.
    val entradaActual = temporadaPorCopia[asset.contentId]
    val nombreVisible = SeriesGrouping.searchTitle(entradaActual?.asset?.name ?: asset.name)
    val temporadaActual = entradaActual?.number
        ?: SeriesGrouping.seasonOrNull(asset.name)
    val reproducir: (PlayerPayload) -> Unit = { destino ->
        onPlay(
            when (destino) {
                is PlayerPayload.Episode -> {
                    val entrada = temporadaPorCopia[destino.asset.contentId]
                    destino.copy(
                        tituloSerie = entrada?.let { SeriesGrouping.searchTitle(it.asset.name) }
                            ?: nombreVisible,
                        temporada = entrada?.number,
                        serieKey = serieKey,
                    )
                }
                else -> destino
            },
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val separacionEp = 8.dp
    val columnasEp = ((maxWidth - 32.dp + separacionEp) / (104.dp + separacionEp)).toInt().coerceAtLeast(3)
    val anchoEp = (maxWidth - 32.dp - separacionEp * (columnasEp - 1)) / columnasEp
    val filasEp = episodios.chunked(columnasEp)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp),
            ) {
                PosterImage(asset = asset, modifier = Modifier.fillMaxSize(), light = false)
                Box(Modifier.matchParentSize().background(DetailScrim))
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                ) {
                    Text(
                        text = nombreVisible,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(6.dp))
                    MetaLine(asset)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val primero = episodios.firstOrNull()
                        Button(
                            onClick = {
                                if (isSeries) {
                                    primero?.let {
                                        reproducir(PlayerPayload.Episode(it.season, it.episode))
                                    }
                                } else {
                                    onPlay(PlayerPayload.Vod(asset))
                                }
                            },
                            enabled = !isSeries || primero != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                if (isSeries) {
                                    "Reproducir T${temporadaActual ?: 1} " +
                                        "E${primero?.episode?.seriesNumber ?: 1}"
                                } else {
                                    "Reproducir"
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
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Sinopsis",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        sinopsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (isSeries && temporadas.size > 1) {
            item(key = "temporadas") {
                Column {
                    Header("Temporadas")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(temporadas, key = { it.asset.contentId }) { temporada ->
                            FilterChip(
                                selected = temporada.number == (temporadaActual ?: 1),
                                onClick = { onSelectSeason(temporada.asset) },
                                label = { Text(temporada.label) },
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
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Episodios",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "T${temporadaActual ?: 1} · ${episodios.size} episodios",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(filasEp, key = { fila, _ -> "ep-row-$fila" }) { fila, eps ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(separacionEp),
                ) {
                    eps.forEachIndexed { columna, ep ->
                        Box(Modifier.width(anchoEp)) {
                            EpisodeCard(ep, fila * columnasEp + columna + 1, reproducir)
                        }
                    }
                }
            }
        }

        item(key = "relacionados") { RelatedSection(asset, onOpenAsset) }
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
@Composable
private fun EpisodeCard(
    ep: SeasonEpisode,
    indice: Int,
    onPlay: (PlayerPayload) -> Unit,
) {
    val numero = ep.episode.seriesNumber?.takeIf { it > 0 } ?: indice
    androidx.compose.material3.Card(
        onClick = { onPlay(PlayerPayload.Episode(ep.season, ep.episode)) },
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
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

/** "Quizás te guste" (best-effort: sin estado visible si falla). */
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
                Header("Quizás te guste")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.contentId }) { rel ->
                        androidx.compose.material3.Card(
                            onClick = { onOpenAsset(rel) },
                            modifier = Modifier.width(160.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(90.dp),
                            ) {
                                PosterImage(asset = rel, modifier = Modifier.fillMaxSize(), showTitle = true, light = false)
                            }
                        }
                    }
                }
            }
        }
        else -> Unit
    }
}
