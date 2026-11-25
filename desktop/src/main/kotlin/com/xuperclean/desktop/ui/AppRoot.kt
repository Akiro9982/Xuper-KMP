package com.xuperclean.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.xuperclean.core.WatchProgress
import com.xuperclean.core.model.Channel
import com.xuperclean.core.model.EpisodeInfo
import com.xuperclean.core.model.VodAsset
import com.xuperclean.core.update.UpdateState
import com.xuperclean.desktop.data.DesktopWatchStore
import com.xuperclean.desktop.data.XuperRepository
import com.xuperclean.desktop.ui.detail.DetailScreen
import com.xuperclean.desktop.ui.common.EmptyState
import com.xuperclean.desktop.ui.common.LoadingState
import com.xuperclean.desktop.ui.common.PosterImage
import com.xuperclean.desktop.ui.common.UiState
import com.xuperclean.desktop.ui.common.rememberAsync
import com.xuperclean.desktop.ui.player.PlayerPayload
import com.xuperclean.desktop.ui.player.PlayerScreen
import com.xuperclean.desktop.ui.settings.SettingsScreen
import com.xuperclean.desktop.update.AppUpdates
import coil3.compose.AsyncImage

/** Destinos del menú lateral (IA de la app original). */
sealed class Tab(val label: String) {
    data object Live : Tab("TV en vivo")
    data object Destacados : Tab("Destacados")
    data object Pelicula : Tab("Película")
    data object Series : Tab("Series")
    data object Kids : Tab("Kids")
    data object Anime : Tab("Anime")
    data object Explorar : Tab("Explorar")
    data object Ajustes : Tab("Ajustes")
}

sealed interface Overlay {
    data class Detail(val asset: VodAsset) : Overlay
    data class Player(val payload: PlayerPayload) : Overlay
}

/**
 * «Continuar viendo» reabre el capítulo guardado como episodio, con su temporada y su
 * número reales; si se abriera como película se perderían ambos (#8).
 */
private fun WatchProgress.toPlayerPayload(): PlayerPayload {
    val epId = episodeId
    return if (isSeries && epId != null) {
        PlayerPayload.Episode(
            asset = toVodAsset(),
            episode = EpisodeInfo(
                contentId = epId,
                name = episodeName,
                seriesNumber = episodeNumber,
            ),
            tituloSerie = name,
            temporada = seasonNumber,
            serieKey = aliasKey,
        )
    } else {
        PlayerPayload.Vod(toVodAsset())
    }
}

private val TABS = listOf(
    Tab.Live, Tab.Destacados, Tab.Pelicula, Tab.Series, Tab.Kids, Tab.Anime, Tab.Explorar,
    Tab.Ajustes,
)

private fun iconOf(tab: Tab) = when (tab) {
    Tab.Live -> Icons.Filled.LiveTv
    Tab.Destacados -> Icons.Filled.PlayArrow
    Tab.Pelicula -> Icons.Filled.Movie
    Tab.Series -> Icons.Filled.SmartDisplay
    Tab.Kids -> Icons.Filled.ChildFriendly
    Tab.Anime -> Icons.Filled.Animation
    Tab.Explorar -> Icons.Filled.Search
    Tab.Ajustes -> Icons.Filled.Settings
}

@Composable
fun AppRoot(
    window: java.awt.Window,
    windowState: androidx.compose.ui.window.WindowState,
    onExit: () -> Unit,
) {
    var tab by remember { mutableStateOf<Tab>(Tab.Destacados) }
    val stack = remember { mutableStateListOf<Overlay>() }
    val updateState by AppUpdates.current.state.collectAsState()

    LaunchedEffect(Unit) { AppUpdates.current.checkIfIdle() }

    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    val overlay = stack.lastOrNull()

    if (overlay is Overlay.Player) {
        PlayerScreen(payload = overlay.payload, window = window, windowState = windowState, onClose = { pop() })
        return
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val compacto = maxWidth < 1020.dp
        Row(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            SideNav(
                tab = tab,
                compacto = compacto,
                hayActualizacion = updateState is UpdateState.Available,
                onSelect = { tab = it; stack.clear() },
            )
            AnimatedContent(
                targetState = overlay ?: tab,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.99f, animationSpec = tween(180)))
                        .togetherWith(fadeOut(tween(140)))
                },
                label = "content",
                modifier = Modifier.fillMaxSize(),
            ) { state ->
                when (state) {
                    is Overlay.Detail -> DetailScreen(
                        asset = state.asset,
                        onBack = { pop() },
                        onPlay = { p -> stack.add(Overlay.Player(p)) },
                        onOpenAsset = { a -> stack.add(Overlay.Detail(a)) },
                    )
                    is Overlay.Player -> Unit
                    Tab.Live -> LiveScreen(onOpenChannel = { p -> stack.add(Overlay.Player(p)) })
                    Tab.Destacados -> DestacadosScreen(
                        onOpenAsset = { a -> stack.add(Overlay.Detail(a)) },
                        // «Continuar viendo» reanuda el capítulo guardado (#8).
                        onResume = { p -> stack.add(Overlay.Player(p.toPlayerPayload())) },
                    )
                    Tab.Pelicula -> CatalogScreen(CatalogKind.Pelicula, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                    Tab.Series -> CatalogScreen(CatalogKind.Series, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                    Tab.Kids -> CatalogScreen(CatalogKind.Kids, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                    Tab.Anime -> CatalogScreen(CatalogKind.Anime, onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                    Tab.Explorar -> SearchScreen(onOpenAsset = { a -> stack.add(Overlay.Detail(a)) })
                    Tab.Ajustes -> SettingsScreen(onExit = onExit)
                }
            }
        }
    }
}

@Composable
private fun SideNav(
    tab: Tab,
    compacto: Boolean,
    hayActualizacion: Boolean,
    onSelect: (Tab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (compacto) 76.dp else 210.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = if (compacto) 10.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = if (compacto) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        TABS.forEach { t ->
            val selected = tab == t
            Surface(
                onClick = { onSelect(t) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (compacto) Arrangement.Center else Arrangement.Start,
                    modifier = Modifier.padding(horizontal = if (compacto) 0.dp else 14.dp),
                ) {
                    Box {
                        Icon(
                            iconOf(t),
                            contentDescription = t.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (hayActualizacion && t == Tab.Ajustes) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                            )
                        }
                    }
                    if (!compacto) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            t.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ secciones

enum class CatalogKind(val title: String, val matches: (VodAsset) -> Boolean) {
    Pelicula("Películas", { it.type == "1" || it.programType.equals("movie", ignoreCase = true) }),
    Series("Series", { it.isSeries }),
    Kids("Kids", { it.hasAnyTag("family", "kids", "infantil", "cartoon") }),
    Anime("Anime", { it.hasAnyTag("anime", "animation") }),
}

/** Compara contra los tags del portal, que es el único clasificador real del catálogo free. */
private fun VodAsset.hasAnyTag(vararg wanted: String): Boolean {
    val list = tags?.lowercase()?.split(',')?.map { it.trim() } ?: return false
    return wanted.any { w -> list.any { it == w } }
}

@Composable
internal fun CatalogHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 12.dp),
    )
}

private val HeroScrim = androidx.compose.ui.graphics.Brush.verticalGradient(
    0f to Color.Transparent,
    0.5f to Color.Black.copy(alpha = 0.25f),
    1f to Color.Black.copy(alpha = 0.85f),
)

@Composable
fun DestacadosScreen(
    onOpenAsset: (VodAsset) -> Unit,
    onResume: (WatchProgress) -> Unit = { onOpenAsset(it.toVodAsset()) },
) {
    when (val state = rememberAsync("home-rows") { XuperRepository.homeRows() }) {
        is UiState.Loading -> LoadingState(Modifier.fillMaxSize())
        is UiState.Error -> EmptyState("No se pudo cargar el catálogo:\n${state.message}", Modifier.fillMaxSize())
        is UiState.Ready -> {
            val filas = state.value.recommendList.filter { it.assetList.isNotEmpty() }
            val hero = filas.firstOrNull()?.assetList?.firstOrNull()
            val continuar = remember { DesktopWatchStore.resumable() }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (hero != null) item("hero") {
                    Box(
                        Modifier
                            .padding(horizontal = 32.dp)
                            .fillMaxWidth()
                            .height(300.dp),
                    ) {
                        PosterImage(asset = hero, modifier = Modifier.fillMaxSize(), light = false)
                        Box(Modifier.matchParentSize().background(HeroScrim))
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(24.dp),
                        ) {
                            Text(hero.name, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 2)
                            val tags = hero.tags
                            if (!tags.isNullOrEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(tags.replace(",", " · "), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { onOpenAsset(hero) }) { Text("Ver ahora") }
                        }
                    }
                }
                if (continuar.isNotEmpty()) {
                    item("continue") {
                        Column {
                            Text(
                                "Continuar viendo",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 32.dp, bottom = 10.dp),
                            )
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(continuar, key = { it.assetId }) { item ->
                                    ContinueCard(item, onResume)
                                }
                            }
                        }
                    }
                }
                filas.forEach { fila ->
                    item("row-${fila.id}") {
                        Column {
                            Text(
                                fila.name ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 32.dp, bottom = 10.dp),
                            )
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(fila.assetList, key = { it.contentId }) { asset ->
                                    Card(
                                        onClick = { onOpenAsset(asset) },
                                        modifier = Modifier.width(266.dp).height(150.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    ) {
                                        Box { PosterImage(asset = asset, modifier = Modifier.fillMaxSize(), showTitle = true, light = false) }
                                    }
                                }
                            }
                        }
                    }
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

    Card(
        onClick = { onResume(item) },
        modifier = Modifier.width(266.dp).height(150.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box {
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
}

@Composable
fun CatalogScreen(kind: CatalogKind, onOpenAsset: (VodAsset) -> Unit) {
    when (val state = rememberAsync("catalog-${kind.name}") { XuperRepository.homeAssets() }) {
        is UiState.Loading -> LoadingState(Modifier.fillMaxSize())
        is UiState.Error -> EmptyState("No se pudo cargar:\n${state.message}", Modifier.fillMaxSize())
        is UiState.Ready -> {
            val assets = remember(state.value, kind) { state.value.filter(kind.matches) }
            Column(Modifier.fillMaxSize()) {
                CatalogHeader(if (assets.isEmpty()) kind.title else "${kind.title} · ${assets.size}")
                if (assets.isEmpty()) {
                    EmptyState(
                        "Tu plan no incluye títulos de ${kind.title.lowercase()} por ahora.\n" +
                            "Prueba en Destacados o en la búsqueda.",
                        Modifier.fillMaxSize(),
                    )
                } else {
                    AssetGrid(assets, onOpenAsset)
                }
            }
        }
    }
}

@Composable
internal fun AssetGrid(
    assets: List<VodAsset>,
    onOpenAsset: (VodAsset) -> Unit,
    subtitleOf: (VodAsset) -> String? = { null },
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(210.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 32.dp, end = 32.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(assets, key = { it.contentId }) { asset ->
            Card(
                onClick = { onOpenAsset(asset) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Box {
                    PosterImage(
                        asset = asset,
                        modifier = Modifier.fillMaxSize(),
                        showTitle = true,
                        light = false, // tarjeta 16:9 → usar el backdrop "stage", no el póster 2:3
                        subtitle = subtitleOf(asset),
                    )
                }
            }
        }
    }
}

@Composable
fun LiveScreen(onOpenChannel: (PlayerPayload) -> Unit) {
    var filtro by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf<Long?>(null) }

    // Categorías reales del portal (#3): 38 en el árbol LIVE.
    val categorias = rememberAsync("live-categories") { XuperRepository.liveCategories() }
    val listaCategorias = (categorias as? UiState.Ready)?.value.orEmpty()

    val clave = "live-channels-$categoria"
    val estado = rememberAsync(clave) {
        val id = categoria
        if (id == null) XuperRepository.liveChannels()
        else XuperRepository.liveChannelsPorColumna(id)
    }

    when (estado) {
        is UiState.Loading -> LoadingState(Modifier.fillMaxSize())
        is UiState.Error -> EmptyState("No se pudo cargar la guía:\n${estado.message}", Modifier.fillMaxSize())
        is UiState.Ready -> {
            val canales = remember(estado.value, filtro) {
                val q = filtro.trim().lowercase()
                if (q.isEmpty()) estado.value
                else estado.value.filter { c ->
                    (c.alias ?: c.name).lowercase().contains(q) ||
                        c.channelNumber?.toString() == q ||
                        (c.channelNumber?.toString()?.startsWith(q) == true)
                }
            }
            Column(Modifier.fillMaxSize()) {
                val nombreCat = listaCategorias.firstOrNull { it.id == categoria }?.name
                CatalogHeader(
                    if (nombreCat == null) "TV en vivo · ${canales.size} canales"
                    else "$nombreCat · ${canales.size} canales",
                )
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    item("all") {
                        androidx.compose.material3.FilterChip(
                            selected = categoria == null,
                            onClick = { categoria = null },
                            label = { Text("Todos") },
                        )
                    }
                    items(listaCategorias, key = { it.id }) { c ->
                        androidx.compose.material3.FilterChip(
                            selected = categoria == c.id,
                            onClick = { categoria = c.id },
                            label = { Text(c.name ?: "Categoría") },
                        )
                    }
                }
                OutlinedTextField(
                    value = filtro,
                    onValueChange = { filtro = it },
                    label = { Text("Filtrar por nombre o número") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 12.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(280.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 32.dp, end = 32.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(canales, key = { it.channelCode }) { ch ->
                        ChannelCard(ch) { onOpenChannel(PlayerPayload.Live(ch)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(ch: Channel, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                val logo = ch.posterUrl
                if (!logo.isNullOrBlank()) {
                    AsyncImage(
                        model = logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                    )
                } else {
                    Text(
                        "${ch.channelNumber ?: "·"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    ch.alias ?: ch.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Text(
                    "Canal ${ch.channelNumber ?: "·"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val etiqueta = qualityLabel(ch.quality)
            if (etiqueta != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        etiqueta,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** `quality`: 1 SD · 2 HD · 3 FHD · 4 4K (ver iptv-api.md §5). */
private fun qualityLabel(quality: String?): String? = when (quality) {
    "2" -> "HD"
    "3" -> "FHD"
    "4" -> "4K"
    else -> null
}

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
                .padding(horizontal = 32.dp)
                .fillMaxWidth(),
        )
        LaunchedEffect(query) {
            if (query.length >= 3) {
                kotlinx.coroutines.delay(500)
                submitted = query
            }
        }
        if (submitted.isNotEmpty()) {
            when (val state = rememberAsync("search-$submitted") {
                XuperRepository.search(submitted, pageSize = 50)
            }) {
                is UiState.Loading -> LoadingState(Modifier.fillMaxSize().padding(top = 20.dp))
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
                        AssetGrid(resultados.map { it.cardAsset }, onOpenAsset)
                    }
                }
            }
        } else {
            EmptyState("Escribe al menos 3 caracteres para buscar.", Modifier.fillMaxSize())
        }
    }
}