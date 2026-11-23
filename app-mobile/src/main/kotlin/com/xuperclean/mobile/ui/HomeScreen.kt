package com.xuperclean.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.xuperclean.core.WatchProgress
import com.xuperclean.core.model.NextColumnsData
import com.xuperclean.core.model.VodAsset
import com.xuperclean.mobile.data.WatchStore
import com.xuperclean.mobile.data.XuperRepository
import com.xuperclean.mobile.ui.common.EmptyState
import com.xuperclean.mobile.ui.common.LoadingState
import com.xuperclean.mobile.ui.common.PosterImage
import com.xuperclean.mobile.ui.common.UiState
import com.xuperclean.mobile.ui.common.rememberAsync

private val HeroScrim = Brush.verticalGradient(
    0f to Color.Transparent,
    0.5f to Color.Black.copy(alpha = 0.2f),
    1f to Color.Black.copy(alpha = 0.85f),
)

@Composable
fun HomeScreen(
    onOpenAsset: (VodAsset) -> Unit,
    onResume: (WatchProgress) -> Unit = { onOpenAsset(it.toVodAsset()) },
) {
    when (val state = rememberAsync("home-rows") { XuperRepository.homeRows() }) {
        is UiState.Loading -> LoadingState(Modifier.fillMaxSize())
        is UiState.Error -> EmptyState("No se pudo cargar el catálogo:\n${state.message}", Modifier.fillMaxSize())
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
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (hero != null) {
            item(key = "hero") { HeroCard(hero, onOpenAsset) }
        }
        if (continuar.isNotEmpty()) {
            item(key = "continue") {
                Column(Modifier.padding(top = 14.dp)) {
                    Text(
                        text = "Continuar viendo",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(continuar, key = { it.assetId }) { item ->
                            ContinueCard(item, onResume)
                        }
                    }
                }
            }
        }
        filas.forEach { fila ->
            item(key = "row-${fila.id}") {
                Column(Modifier.padding(top = 14.dp)) {
                    Text(
                        text = fila.name ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(fila.assetList, key = { it.contentId }) { asset ->
                            RowCard(asset, onOpenAsset)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(asset: VodAsset, onOpenAsset: (VodAsset) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp),
    ) {
        PosterImage(asset = asset, modifier = Modifier.fillMaxSize(), light = false)
        Box(Modifier.matchParentSize().background(HeroScrim))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 2,
            )
            val tags = asset.tags
            if (!tags.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tags.replace(",", " · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onOpenAsset(asset) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Ver ahora")
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

    Box(
        Modifier
            .width(180.dp)
            .height(101.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onResume(item) },
    ) {
        PosterImage(
            asset = asset,
            modifier = Modifier.fillMaxSize(),
            showTitle = true,
            light = false,
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

@Composable
private fun RowCard(asset: VodAsset, onOpenAsset: (VodAsset) -> Unit) {
    Box(
        Modifier
            .width(160.dp)
            .height(90.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .clickable { onOpenAsset(asset) },
    ) {
        PosterImage(asset = asset, modifier = Modifier.fillMaxSize(), showTitle = true, light = false)
    }
}
