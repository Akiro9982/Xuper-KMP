package com.xuperclean.tv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
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
 * Contorno de foco de las tarjetas de contenido.
 *
 * tv-material solo agranda la tarjeta al enfocarla; a distancia eso no basta para
 * saber qué elemento está seleccionado. Este borde lo dibuja el propio Surface
 * (dentro de su escala), así que acompaña al póster y desaparece al perder el foco.
 *
 * @param grosor ancho del trazo en reposo de foco.
 * @param separacion cuánto se separa el trazo del borde de la tarjeta.
 */
@Composable
fun tvFocusBorder(
    grosor: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    forma: Shape = RoundedCornerShape(10.dp),
    separacion: Dp = 2.dp,
): ClickableSurfaceBorder = ClickableSurfaceDefaults.border(
    focusedBorder = Border(
        border = BorderStroke(grosor, color),
        inset = separacion,
        shape = forma,
    ),
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
