package com.xuperclean.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.xuperclean.desktop.ui.AppRoot
import com.xuperclean.desktop.ui.theme.XuperTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Xuper",
        state = WindowState(
            size = DpSize(1240.dp, 720.dp),
            position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
        ),
    ) {
        LaunchedEffect(Unit) {
            // evita el destello blanco antes del primer frame de Compose
            window.background = java.awt.Color(14, 14, 18)
            window.minimumSize = java.awt.Dimension(640, 420)
        }
        XuperTheme {
            AppRoot()
        }
    }
}
