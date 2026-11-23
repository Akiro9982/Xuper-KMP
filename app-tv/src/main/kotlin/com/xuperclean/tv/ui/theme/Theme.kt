package com.xuperclean.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tema Material 3 (dark) para TV.
 *
 * Tokens semánticos: las pantallas usan MaterialTheme.colorScheme.*, nunca hex sueltos.
 * Identidad: rojo Xuper como primary (escaso: solo acciones/foco), superficies neutras
 * oscuras para contraste y bajo consumo en paneles.
 */

// ---- tokens base (generados a mano, esquema dark M3 con primario rojo Xuper) ----
private val Primary = Color(0xFFFFB4AB)
private val OnPrimary = Color(0xFF690005)
private val PrimaryContainer = Color(0xFF93000A)
private val OnPrimaryContainer = Color(0xFFFFDAD6)

private val Secondary = Color(0xFFE7BDB6)
private val OnSecondary = Color(0xFF442925)
private val SecondaryContainer = Color(0xFF5D3F3B)
private val OnSecondaryContainer = Color(0xFFFFDAD4)

private val Tertiary = Color(0xFFD8C4A0)
private val OnTertiary = Color(0xFF3C2F16)
private val TertiaryContainer = Color(0xFF55452B)
private val OnTertiaryContainer = Color(0xFFF5DFBB)

private val Surface = Color(0xFF0E0E12)
private val SurfaceDim = Color(0xFF090910)
private val SurfaceContainerLowest = Color(0xFF090910)
private val SurfaceContainerLow = Color(0xFF16161B)
private val SurfaceContainer = Color(0xFF1A1A20)
private val SurfaceContainerHigh = Color(0xFF24242B)
private val SurfaceContainerHighest = Color(0xFF2F2F36)
private val OnSurface = Color(0xFFE5E1E6)
private val OnSurfaceVariant = Color(0xFFA9A3AC)
private val Outline = Color(0xFF73737E)
private val OutlineVariant = Color(0xFF2F2F36)

private val Error = Color(0xFFFFB4AB)
private val OnError = Color(0xFF690005)
private val ErrorContainer = Color(0xFF93000A)
private val OnErrorContainer = Color(0xFFFFDAD6)

private val XuperDarkScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    surfaceDim = SurfaceDim,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

// ---- tipografía: jerarquía compacta para distancias de TV, sin fuentes descargadas ----
private val XuperTypography = Typography(
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 48.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

// ---- formas contenidas: nada de radios maximalistas ----
private val XuperShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun XuperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = XuperDarkScheme,
        typography = XuperTypography,
        shapes = XuperShapes,
        content = content,
    )
}

/**
 * Tema de los componentes tv-material (Surface/Button): mismos tokens que M3.
 * tv-material 1.1.0: border/borderVariant en vez de outline; sin surfaceContainer*
 * (el tono de superficie se deriva de surface por elevación).
 */
@Composable
fun XuperTvTheme(content: @Composable () -> Unit) {
    androidx.tv.material3.MaterialTheme(
        colorScheme = androidx.tv.material3.darkColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            primaryContainer = PrimaryContainer,
            onPrimaryContainer = OnPrimaryContainer,
            secondary = Secondary,
            onSecondary = OnSecondary,
            secondaryContainer = SecondaryContainer,
            onSecondaryContainer = OnSecondaryContainer,
            tertiary = Tertiary,
            onTertiary = OnTertiary,
            tertiaryContainer = TertiaryContainer,
            onTertiaryContainer = OnTertiaryContainer,
            background = Surface,
            onBackground = OnSurface,
            surface = Surface,
            onSurface = OnSurface,
            surfaceVariant = SurfaceContainerHigh,
            onSurfaceVariant = OnSurfaceVariant,
            error = Error,
            onError = OnError,
            errorContainer = ErrorContainer,
            onErrorContainer = OnErrorContainer,
            border = Outline,
            borderVariant = OutlineVariant,
            scrim = Color.Black.copy(alpha = 0.6f),
        ),
        content = content,
    )
}
