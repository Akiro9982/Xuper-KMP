package com.xuperclean.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.SelectableChipColors

/**
 * Colores de foco para tarjetas de TV.
 *
 * tv-material enfoca invirtiendo a un contenedor claro; en un tema oscuro eso produce
 * destellos blancos y, si el texto tiene color fijo, lo vuelve ilegible. Aquí el foco
 * usa el violeta de marca (primaryContainer) con texto claro (onPrimaryContainer), así el
 * contraste está garantizado en cualquier tarjeta que use [tvCardColors].
 */
@Composable
fun tvCardColors(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
): ClickableSurfaceColors = ClickableSurfaceDefaults.colors(
    containerColor = containerColor,
    contentColor = contentColor,
    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    pressedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    pressedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

/**
 * Colores de los chips de filtro (categorías, temporadas).
 *
 * tv-material usa por defecto un contenedor blanco al enfocar, lo que en un panel
 * económico produce un destello y deja el texto casi invisible. Aquí el foco y la
 * selección usan el violeta de marca con sus colores "on", igual que las tarjetas.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun tvChipColors(): SelectableChipColors = FilterChipDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    pressedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    pressedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    focusedSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    pressedSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    pressedSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
