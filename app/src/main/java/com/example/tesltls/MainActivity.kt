package com.example.tesltls

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.tesltls.ui.navigation.FoodApp
import com.example.tesltls.ui.theme.TesltlsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Макет світлий -> іконки статус-бару та навігації мають бути темними
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        setContent {
            // Світла тема без dynamic color — інакше системні кольори "поламають" палітру макета
            TesltlsTheme(darkTheme = false, dynamicColor = false) {
                FoodApp()
            }
        }
    }
}
