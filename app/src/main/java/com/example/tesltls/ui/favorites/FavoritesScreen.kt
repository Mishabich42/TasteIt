package com.example.tesltls.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.tesltls.data.local.FoodLocalStore
import com.example.tesltls.di.ServiceLocator
import com.example.tesltls.domain.model.Dish
import com.example.tesltls.domain.repository.FoodRepository
import com.example.tesltls.ui.common.toUserMessage
import com.example.tesltls.ui.components.DishRow
import com.example.tesltls.ui.components.LoadingBox
import com.example.tesltls.ui.components.MessageBox
import com.example.tesltls.ui.components.ScreenHeader
import com.example.tesltls.ui.strings.LocalAppStrings
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ───────────────────────── ViewModel ─────────────────────────

sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState
    data object Empty : FavoritesUiState
    data class Content(val dishes: List<Dish>) : FavoritesUiState
    data class Error(val message: String) : FavoritesUiState
}

/** "Улюблене" = страви, які користувач колись свайпнув вправо (like). */
class FavoritesViewModel(
    private val repository: FoodRepository,
    private val localStore: FoodLocalStore = ServiceLocator.localStore,
    private val userId: suspend () -> String
) : ViewModel() {

    private val _state = MutableStateFlow<FavoritesUiState>(FavoritesUiState.Loading)
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                // 1. Спочатку дістаємо локальні збережені улюблені страви
                val rawLiked = localStore.getLikedDishes()
                if (rawLiked.isEmpty()) {
                    _state.value = FavoritesUiState.Empty
                    return@launch
                }

                // 2. НЕГАЙНО відображаємо список усіх улюблених страв за 0 мс (без блокування на завантаження/переклад)
                val currentDishes = rawLiked.toMutableList()
                _state.value = FavoritesUiState.Content(currentDishes.toList())

                // 3. Для кожної страви ОКРЕМО й асинхронно запускаємо переклад
                // Як тільки будь-яка страва перекладеться, її назва миттєво оновлюється у списку
                rawLiked.forEachIndexed { index, dish ->
                    launch {
                        try {
                            val translated = repository.getDishById(dish.id) ?: dish
                            if (translated != currentDishes[index]) {
                                currentDishes[index] = translated
                                _state.value = FavoritesUiState.Content(currentDishes.toList())
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value !is FavoritesUiState.Content) {
                    _state.value = FavoritesUiState.Error(e.toUserMessage())
                }
            }
        }
    }
}

// ───────────────────────── Екран ─────────────────────────

@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel, onDishClick: (String) -> Unit) {
    val strings = LocalAppStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Оновлюємо список щоразу, коли екран знову стає активним
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader(title = strings.favoritesTitle, subtitle = strings.favoritesSub)
        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            FavoritesUiState.Loading -> LoadingBox()
            FavoritesUiState.Empty -> MessageBox(
                strings.favoritesEmpty
            )
            is FavoritesUiState.Error -> MessageBox(
                message = s.message,
                actionLabel = strings.tryAgain,
                onAction = viewModel::refresh
            )
            is FavoritesUiState.Content -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(s.dishes, key = { it.id }) { dish ->
                    DishRow(dish = dish, onClick = { onDishClick(dish.id) })
                }
            }
        }
    }
}
