package com.xuperclean.tv.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Estado asíncrono simple para pantallas (sin ViewModel: menos dependencias y objetos). */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Ready<T>(val value: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

/** Carga un valor asíncrono con la clave dada; sin reintentos automáticos. */
@Composable
fun <T> rememberAsync(
    key: Any?,
    fetch: suspend () -> T,
): UiState<T> {
    var state by remember(key) { mutableStateOf<UiState<T>>(UiState.Loading) }
    LaunchedEffect(key) {
        state = UiState.Loading
        state = try {
            UiState.Ready(fetch())
        } catch (e: Exception) {
            UiState.Error(e.message ?: "Error inesperado")
        }
    }
    return state
}

/**
 * Esqueleto animado de carga (#9): tres tarjetas 16:9 que respiran + texto.
 * Una sola animación infinita; el TV de gama baja no paga por imágenes de relleno.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Cargando") {
    var dots by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dots = dots % 3 + 1
        }
    }
    val pulso = rememberInfiniteTransition(label = "carga")
    val alpha by pulso.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(width = 150.dp, height = 84.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant
                                    .copy(alpha = alpha),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = label + ".".repeat(dots),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
