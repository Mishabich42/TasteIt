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
import com.example.tesltls.domain.model.Dish
import com.example.tesltls.domain.repository.FoodRepository
import com.example.tesltls.ui.common.toUserMessage
import com.example.tesltls.ui.components.DishRow
import com.example.tesltls.ui.components.LoadingBox
import com.example.tesltls.ui.components.MessageBox
import com.example.tesltls.ui.components.ScreenHeader
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
    private val userId: suspend () -> String
) : ViewModel() {

    private val _state = MutableStateFlow<FavoritesUiState>(FavoritesUiState.Loading)
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            // Якщо список уже показаний — оновлюємо тихо, без спінера
            if (_state.value !is FavoritesUiState.Content) _state.value = FavoritesUiState.Loading
            try {
                val dishes = repository.getLikedDishes(userId())
                _state.value =
                    if (dishes.isEmpty()) FavoritesUiState.Empty else FavoritesUiState.Content(dishes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = FavoritesUiState.Error(e.toUserMessage())
            }
        }
    }
}

// ───────────────────────── Екран ─────────────────────────

@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel, onDishClick: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Оновлюємо список щоразу, коли екран знову стає активним
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader(title = "Улюблене", subtitle = "Страви, які ви вже обрали")
        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            FavoritesUiState.Loading -> LoadingBox()
            FavoritesUiState.Empty -> MessageBox(
                "Тут з'являться страви, які ви свайпнете вправо. Почніть з будь-якої категорії!"
            )
            is FavoritesUiState.Error -> MessageBox(
                message = s.message,
                actionLabel = "Спробувати ще раз",
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
