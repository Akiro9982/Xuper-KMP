package com.xuperclean.desktop.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import java.awt.Canvas
import java.awt.Color

/**
 * Superficie pesada (AWT Canvas) dentro de Compose: mpv renderiza aquí por HWND.
 *
 * `onCanvas` se invoca UNA vez, cuando el Canvas ya es displayable (peer creado)
 * y se puede obtener su HWND.
 */
@Composable
fun MpvSurface(
    modifier: Modifier = Modifier,
    onCanvas: (Canvas) -> Unit,
) {
    var canvasRef by remember { mutableStateOf<Canvas?>(null) }
    var entregado by remember { mutableStateOf(false) }

    SwingPanel(
        factory = {
            Canvas().apply { background = Color.BLACK }
        },
        modifier = modifier,
        update = { canvasRef = it },
    )

    LaunchedEffect(Unit) {
        while (!entregado) {
            val c = canvasRef
            if (c != null && c.isDisplayable) {
                entregado = true
                onCanvas(c)
            } else {
                kotlinx.coroutines.delay(50)
            }
        }
    }
}
