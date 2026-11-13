package com.example.tesltls

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tesltls.di.ServiceLocator
import com.example.tesltls.ui.navigation.FoodApp
import com.example.tesltls.ui.theme.TesltlsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ServiceLocator.init(this)

        setContent {
            val isDarkTheme by ServiceLocator.localStore.darkThemeEnabled.collectAsStateWithLifecycle(initialValue = false)

            enableEdgeToEdge(
                statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            )

            TesltlsTheme(darkTheme = isDarkTheme, dynamicColor = false) {
                FoodApp()
            }
        }
    }
}
