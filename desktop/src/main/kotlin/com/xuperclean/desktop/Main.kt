package com.xuperclean.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.xuperclean.desktop.ui.AppRoot
import com.xuperclean.desktop.ui.theme.XuperTheme
import java.awt.Image
import javax.imageio.ImageIO

/** Icono de la ventana (taskbar y alt-tab), empaquetado en resources/icon.png. */
private val iconoVentana: Image? by lazy {
    object {}.javaClass.classLoader?.getResourceAsStream("icon.png")?.use(ImageIO::read)
}

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(1240.dp, 720.dp),
        position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Xuper",
        state = windowState,
    ) {
        LaunchedEffect(Unit) {
            // evita el destello blanco antes del primer frame de Compose
            window.background = java.awt.Color(14, 14, 18)
            window.minimumSize = java.awt.Dimension(640, 420)
            iconoVentana?.let { window.iconImage = it }
        }
        XuperTheme {
            AppRoot(window = window, windowState = windowState)
        }
    }
}
