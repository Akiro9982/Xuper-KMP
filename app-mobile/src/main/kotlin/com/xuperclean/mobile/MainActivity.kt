package com.xuperclean.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xuperclean.mobile.data.WatchStore
import com.xuperclean.mobile.ui.AppRoot
import com.xuperclean.mobile.ui.theme.XuperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WatchStore.init(this)
        setContent {
            XuperTheme {
                AppRoot()
            }
        }
    }
}
