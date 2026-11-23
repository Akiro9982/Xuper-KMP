package com.xuperclean.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuperclean.core.SearchResult
import com.xuperclean.core.SeriesGrouping
import com.xuperclean.core.model.VodAsset
import com.xuperclean.mobile.data.XuperRepository
import com.xuperclean.mobile.ui.common.EmptyState
import com.xuperclean.mobile.ui.common.LoadingState
import com.xuperclean.mobile.ui.common.PosterImage
import com.xuperclean.mobile.ui.common.UiState
import com.xuperclean.mobile.ui.common.rememberAsync

@Composable
fun SearchScreen(onOpenAsset: (VodAsset) -> Unit) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Header("Buscar")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Películas y series") },
            singleLine = true,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        )
        LaunchedEffect(query) {
            if (query.length >= 3) {
                kotlinx.coroutines.delay(500) // debounce
                submitted = query
            }
        }

        if (submitted.isNotEmpty()) {
            when (val state = rememberAsync("search-$submitted") {
                XuperRepository.search(submitted, pageSize = 50)
            }) {
                is UiState.Loading -> LoadingState(Modifier.fillMaxSize().padding(top = 16.dp))
                is UiState.Error -> EmptyState("Error en la búsqueda:\n${state.message}", Modifier.fillMaxSize())
                is UiState.Ready -> {
                    val items = state.value.searchItemList
                        .flatMap { it.itemList }
                        .map { it.toVodAsset() }

                    // Una ficha por serie, uniendo temporadas y títulos alternos por alias (#4).
                    val resultados = SeriesGrouping.searchResults(items)

                    if (resultados.isEmpty()) {
                        EmptyState("Sin resultados para \"$submitted\"", Modifier.fillMaxSize())
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 24.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(resultados, key = { it.asset.contentId }) { resultado ->
                                SearchCard(resultado, onOpenAsset)
                            }
                        }
                    }
                }
            }
        } else {
            EmptyState("Escribe al menos 3 caracteres para buscar.", Modifier.fillMaxSize())
        }
    }
}

/** Póster 2:3 centrado y título debajo, fuera de la imagen: se lee sin degradados. */
@Composable
private fun SearchCard(resultado: SearchResult, onOpenAsset: (VodAsset) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onOpenAsset(resultado.asset) },
    ) {
        PosterImage(
            asset = resultado.asset,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = resultado.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}
