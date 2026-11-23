package com.xuperclean.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults

/**
 * Colores de foco para tarjetas de TV.
 *
 * tv-material enfoca invirtiendo a un contenedor claro; en un tema oscuro eso produce
 * destellos blancos y, si el texto tiene color fijo, lo vuelve ilegible. Aquí el foco
 * usa el rojo de marca (primaryContainer) con texto claro (onPrimaryContainer), así el
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
