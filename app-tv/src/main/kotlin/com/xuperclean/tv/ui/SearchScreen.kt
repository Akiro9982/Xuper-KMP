package com.xuperclean.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import androidx.compose.material3.OutlinedTextField
import com.xuperclean.core.model.VodAsset
import com.xuperclean.tv.data.XuperRepository
import com.xuperclean.tv.ui.common.EmptyState
import com.xuperclean.tv.ui.common.LoadingState
import com.xuperclean.tv.ui.common.PosterImage
import com.xuperclean.tv.ui.common.UiState
import com.xuperclean.tv.ui.common.rememberAsync

@Composable
fun SearchScreen(onOpenAsset: (VodAsset) -> Unit) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        CatalogHeader("Explorar")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar películas y series") },
            singleLine = true,
            modifier = Modifier
                .padding(start = 32.dp, end = 32.dp)
                .fillMaxWidth(),
        )

        // Buscar al pulsar enter/center en el teclado (cuando el texto tiene 3+ caracteres
        // y cambia): el buscador del portal es sensible al texto completo.
        androidx.compose.runtime.LaunchedEffect(query) {
            if (query.length >= 3) {
                kotlinx.coroutines.delay(600) // debounce leve
                submitted = query
            }
        }

        if (submitted.isNotEmpty()) {
            when (val state = rememberAsync("search-$submitted") {
                XuperRepository.search(submitted, pageSize = 50)
            }) {
                is UiState.Loading -> LoadingState(Modifier.fillMaxSize().padding(top = 24.dp))
                is UiState.Error -> EmptyState("Error en la búsqueda:\n${state.message}", Modifier.fillMaxSize())
                is UiState.Ready -> {
                    val items = state.value.searchItemList
                        .flatMap { it.itemList }
                        .map { it.toVodAsset() }

                    // Una ficha por serie, uniendo temporadas y títulos alternos por alias (#4).
                    val resultados = com.xuperclean.core.SeriesGrouping.searchResults(items)

                    if (resultados.isEmpty()) {
                        EmptyState("Sin resultados para \"$submitted\"", Modifier.fillMaxSize())
                    } else {
                        SearchGrid(resultados, onOpenAsset)
                    }
                }
            }
        } else {
            EmptyState("Escribe al menos 3 caracteres para buscar.", Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SearchGrid(items: List<com.xuperclean.core.SearchResult>, onOpenAsset: (VodAsset) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.asset.contentId }) { resultado ->
            Surface(
                onClick = { onOpenAsset(resultado.asset) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                Box {
                    PosterImage(
                        asset = resultado.cardAsset,
                        modifier = Modifier.fillMaxSize(),
                        showTitle = true,
                        light = false,
                    )
                }
            }
        }
    }
}
