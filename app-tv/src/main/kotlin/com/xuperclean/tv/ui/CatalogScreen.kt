package com.xuperclean.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import com.xuperclean.core.model.VodAsset
import com.xuperclean.tv.data.XuperRepository
import com.xuperclean.tv.ui.common.EmptyState
import com.xuperclean.tv.ui.common.LoadingState
import com.xuperclean.tv.ui.common.PosterImage
import com.xuperclean.tv.ui.common.UiState
import com.xuperclean.tv.ui.common.rememberAsync
import com.xuperclean.tv.ui.theme.tvFocusBorder

/** Secciones de catálogo: filtros reales sobre el pool del home (type/tags del portal). */
enum class CatalogKind(val title: String, val matches: (VodAsset) -> Boolean) {
    Pelicula("Película", { it.type == "1" || it.programType.equals("movie", ignoreCase = true) }),
    Series("Series", { it.isSeries }),
    Kids("Kids", { it.hasAnyTag("family", "kids", "infantil", "cartoon") }),
    Anime("Anime", { it.hasAnyTag("anime", "animation") }),
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
private fun CatalogGrid(
    kind: CatalogKind,
    assets: List<VodAsset>,
    onOpenAsset: (VodAsset) -> Unit,
) {
    if (assets.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            CatalogHeader(kind.title)
            EmptyState(
                "Todavía no hay contenido disponible en esta sección.",
                Modifier.fillMaxSize(),
            )
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        CatalogHeader(kind.title)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(assets, key = { it.contentId }) { asset ->
                GridCard(asset, onOpenAsset)
            }
        }
    }
}

@Composable
internal fun CatalogHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 16.dp),
    )
}

@Composable
private fun GridCard(asset: VodAsset, onOpenAsset: (VodAsset) -> Unit) {
    Surface(
        onClick = { onOpenAsset(asset) },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        border = tvFocusBorder(),
    ) {
        Box {
            PosterImage(asset = asset, modifier = Modifier.fillMaxSize(), showTitle = true, light = true)
        }
    }
}
