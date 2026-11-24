package com.xuperclean.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import androidx.tv.material3.Button
import com.xuperclean.core.WatchProgress
import com.xuperclean.core.model.NextColumnsData
import com.xuperclean.core.model.VodAsset
import com.xuperclean.tv.data.WatchStore
import com.xuperclean.tv.data.XuperRepository
import com.xuperclean.tv.ui.common.EmptyState
import com.xuperclean.tv.ui.common.LoadingState
import com.xuperclean.tv.ui.common.PosterImage
import com.xuperclean.tv.ui.common.UiState
import com.xuperclean.tv.ui.common.rememberAsync
import com.xuperclean.tv.ui.theme.tvFocusBorder

/** Degradado del hero (precalculado, un solo objeto). */
private val HeroScrim = Brush.verticalGradient(
    0f to Color.Transparent,
    0.5f to Color.Black.copy(alpha = 0.25f),
    1f to Color.Black.copy(alpha = 0.80f),
)

@Composable
fun DestacadosScreen(
    onOpenAsset: (VodAsset) -> Unit,
    onResume: (WatchProgress) -> Unit = { onOpenAsset(it.toVodAsset()) },
) {
    when (val state = rememberAsync("home-rows") { XuperRepository.homeRows() }) {
        is UiState.Loading -> LoadingState(Modifier.fillMaxSize())
        is UiState.Error -> EmptyState(
            "No se pudo cargar el catálogo:\n${state.message}", Modifier.fillMaxSize())
        is UiState.Ready -> HomeContent(state.value, onOpenAsset, onResume)
    }
}

@Composable
private fun HomeContent(
    data: NextColumnsData,
    onOpenAsset: (VodAsset) -> Unit,
    onResume: (WatchProgress) -> Unit,
) {
    val filas = data.recommendList.filter { it.assetList.isNotEmpty() }
    val hero = filas.firstOrNull()?.assetList?.firstOrNull()
    val continuar = remember { WatchStore.resumable() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (hero != null) {
            item(key = "hero") {
                HeroCard(asset = hero, onOpenAsset = onOpenAsset)
            }
        }
        if (continuar.isNotEmpty()) {
            item(key = "continue") {
                Column {
                    Text(
                        text = "Continuar viendo",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 32.dp, bottom = 10.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(continuar, key = { it.assetId }) { item ->
                            ContinueCard(item = item, onResume = onResume)
                        }
                    }
                }
            }
        }
        filas.forEach { fila ->
            item(key = "row-${fila.id}") {
                Column {
                    Text(
                        text = fila.name ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 32.dp, bottom = 10.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(fila.assetList, key = { it.contentId }) { asset ->
                            RowCard(asset = asset, onOpenAsset = onOpenAsset)
                        }
                    }
                }
            }
        }
        item(key = "spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HeroCard(asset: VodAsset, onOpenAsset: (VodAsset) -> Unit) {
    Surface(
        onClick = { onOpenAsset(asset) },
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
            .height(300.dp),
        border = tvFocusBorder(grosor = 4.dp),
    ) {
        Box {
            PosterImage(asset = asset, modifier = Modifier.fillMaxSize(), light = false)
            Box(Modifier.matchParentSize().background(HeroScrim))
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        maxLines = 2,
                    )
                    val tags = asset.tags
                    if (!tags.isNullOrEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = tags.replace(",", " · "),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = { onOpenAsset(asset) }) {
                    Text("Ver ahora")
                }
            }
        }
    }
}

/** Tarjeta de «Continuar viendo»: póster, capítulo y barra de avance (#8). */
@Composable
private fun ContinueCard(item: WatchProgress, onResume: (WatchProgress) -> Unit) {
    val asset = remember(item) { item.toVodAsset() }
    val subtitulo = listOfNotNull(
        item.episodeLabel,
        if (item.remainingMinutes > 0) "${item.remainingMinutes} min" else null,
    ).joinToString(" · ").ifBlank { null }

    Surface(
        onClick = { onResume(item) },
        modifier = Modifier
            .height(150.dp)
            .width(266.dp),
        border = tvFocusBorder(),
    ) {
        Box {
            PosterImage(
                asset = asset,
                modifier = Modifier.fillMaxSize(),
                showTitle = true,
                light = true,
                subtitle = subtitulo,
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.28f)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(item.fraction.toFloat().takeIf { it > 0f } ?: 0.02f)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** Tarjeta de fila: tamaño fijo (16:9) para no medir imágenes enormes. */
@Composable
private fun RowCard(asset: VodAsset, onOpenAsset: (VodAsset) -> Unit) {
    Surface(
        onClick = { onOpenAsset(asset) },
        modifier = Modifier
            .height(150.dp)
            .width(266.dp),
        border = tvFocusBorder(),
    ) {
        PosterImage(asset = asset, modifier = Modifier.fillMaxSize(), showTitle = true, light = true)
    }
}
