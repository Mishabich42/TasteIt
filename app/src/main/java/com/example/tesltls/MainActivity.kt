package com.example.tesltls

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.tesltls.di.ServiceLocator
import com.example.tesltls.ui.theme.TesltlsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {

            LaunchedEffect(Unit) {
                try {
                    val userId = ServiceLocator.ensureUserId()
                    val repo = ServiceLocator.foodRepository
                    Log.d("FoodTinder", "Пошук: ${repo.searchDishes("борщ")}")
                    Log.d("FoodTinder", "Випадкова: ${repo.getRandomDish("Soups", userId)}")
                } catch (e: Exception) {
                    Log.e("FoodTinder", "Помилка: ${e.message}", e)
                }
            }

            TesltlsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TesltlsTheme {
        Greeting("Android")
    }
}