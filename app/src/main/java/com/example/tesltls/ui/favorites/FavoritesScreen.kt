package com.example.tesltls.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.tesltls.R
import com.example.tesltls.data.local.FoodLocalStore
import com.example.tesltls.di.ServiceLocator
import com.example.tesltls.domain.model.Dish
import com.example.tesltls.domain.repository.FoodRepository
import com.example.tesltls.ui.common.toUserMessage
import com.example.tesltls.ui.components.AppIcon
import com.example.tesltls.ui.components.DishRow
import com.example.tesltls.ui.components.LoadingBox
import com.example.tesltls.ui.components.MessageBox
import com.example.tesltls.ui.components.ScreenHeader
import com.example.tesltls.ui.strings.LocalAppStrings
import com.example.tesltls.ui.theme.FoodColors
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

    private val _selectedDishIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedDishIds: StateFlow<Set<String>> = _selectedDishIds.asStateFlow()

    fun toggleSelection(dishId: String) {
        val current = _selectedDishIds.value.toMutableSet()
        if (current.contains(dishId)) {
            current.remove(dishId)
        } else {
            current.add(dishId)
        }
        _selectedDishIds.value = current
    }

    fun selectAll(allDishIds: List<String>) {
        _selectedDishIds.value = allDishIds.toSet()
    }

    fun clearSelection() {
        _selectedDishIds.value = emptySet()
    }

    fun deleteSelectedDishes() {
        val idsToDelete = _selectedDishIds.value.toList()
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch {
            try {
                repository.removeLikedDishes(userId(), idsToDelete)
                clearSelection()
                refresh()
            } catch (_: Exception) {}
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                // 1. Спочатку дістаємо локальні збережені улюблені страви
                val rawLiked = localStore.getLikedDishes()
                if (rawLiked.isEmpty()) {
                    _state.value = FavoritesUiState.Empty
                    return@launch
                }

                // 2. НЕГАЙНО відображаємо список усіх улюблених страв за 0 мс
                val currentDishes = rawLiked.toMutableList()
                _state.value = FavoritesUiState.Content(currentDishes.toList())

                // 3. Для кожної страви ОКРЕМО й асинхронно запускаємо переклад
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
    val selectedDishIds by viewModel.selectedDishIds.collectAsStateWithLifecycle()

    // Оновлюємо список щоразу, коли екран знову стає активним
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val isSelectionMode = selectedDishIds.isNotEmpty()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (isSelectionMode) {
            // ── Панель дій при виборі страв ──
            val contentDishes = (state as? FavoritesUiState.Content)?.dishes.orEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(FoodColors.CardGreen)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FoodColors.SearchField)
                            .clickable { viewModel.clearSelection() },
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcon(R.drawable.ic_close, tint = FoodColors.TextPrimary, size = 18.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Вибрано: ${selectedDishIds.size}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoodColors.TextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Всі",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoodColors.Green,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.selectAll(contentDishes.map { it.id }) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.deleteSelectedDishes() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Видалити",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            ScreenHeader(title = strings.favoritesTitle, subtitle = strings.favoritesSub)
        }

        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            FavoritesUiState.Loading -> LoadingBox()
            FavoritesUiState.Empty -> MessageBox(strings.favoritesEmpty)
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
                    val isSelected = selectedDishIds.contains(dish.id)
                    DishRow(
                        dish = dish,
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleSelection(dish.id)
                            } else {
                                onDishClick(dish.id)
                            }
                        },
                        onLongClick = {
                            viewModel.toggleSelection(dish.id)
                        },
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode
                    )
                }
            }
        }
    }
}
