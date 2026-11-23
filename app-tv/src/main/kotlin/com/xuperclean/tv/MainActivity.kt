package com.xuperclean.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xuperclean.tv.data.WatchStore
import com.xuperclean.tv.ui.AppRoot
import com.xuperclean.tv.ui.theme.XuperTheme
import com.xuperclean.tv.ui.theme.XuperTvTheme

/**
 * Actividad única de la app TV. Todo el flujo es Compose con foco D-pad.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WatchStore.init(this)
        setContent {
            XuperTheme {
                XuperTvTheme {
                    AppRoot()
                }
            }
        }
    }
}
