package com.example.tesltls.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tesltls.domain.model.Place
import com.example.tesltls.domain.repository.FoodRepositoryException
import io.github.jan.supabase.exceptions.HttpRequestException
import java.io.IOException
import java.util.Locale

/** Перетворює технічну помилку на зрозуміле користувачу повідомлення. */
fun Throwable.toUserMessage(): String = when (this) {
    is FoodRepositoryException.Network ->
        message?.takeIf { it.isNotBlank() }
            ?: "Немає з'єднання з інтернетом. Перевірте мережу та спробуйте ще раз."
    is FoodRepositoryException.Database ->
        message?.takeIf { it.isNotBlank() }
            ?: "Сервер повернув помилку. Спробуйте трохи пізніше."
    is FoodRepositoryException.Serialization ->
        "Отримано некоректні дані від сервера."
    is HttpRequestException, is IOException ->
        "Немає з'єднання з інтернетом. Перевірте мережу та спробуйте ще раз."
    else -> message?.takeIf { it.isNotBlank() } ?: "Щось пішло не так. Спробуйте ще раз."
}

/** Відкриває посилання (браузер, YouTube, Glovo, Bolt) або показує повідомлення, якщо нема чим. */
fun Context.openUrl(url: String) {
    try {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(this, "Не вдалося відкрити посилання", Toast.LENGTH_SHORT).show()
    }
}

/** Відкриває закладу на карті (Google Maps або будь-який застосунок, що вміє geo:). */
fun Context.openMap(place: Place) {
    val label = Uri.encode(place.name)
    openUrl("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}($label)")
}

/** 180.0 -> "180 ₴", 99.5 -> "99.50 ₴", null -> null. */
fun formatPrice(price: Double?): String? = price?.let {
    if (it % 1.0 == 0.0) "${it.toInt()} ₴" else String.format(Locale.US, "%.2f ₴", it)
}

/**
 * Створює ViewModel, прив'язаний до поточного екрана навігації (NavBackStackEntry).
 * Дозволяє передати у конструктор залежності та аргументи екрана без Hilt/Koin.
 */
@Composable
inline fun <reified VM : ViewModel> entryViewModel(crossinline create: () -> VM): VM =
    viewModel(factory = viewModelFactory { initializer { create() } })
