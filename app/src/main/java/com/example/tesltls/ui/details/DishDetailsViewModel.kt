package com.example.tesltls.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tesltls.domain.model.Dish
import com.example.tesltls.domain.model.Place
import com.example.tesltls.domain.model.PlaceWithPrice
import com.example.tesltls.domain.repository.FoodRepository
import com.example.tesltls.ui.common.toUserMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DishDetailsState(
    val isLoading: Boolean = true,
    val dish: Dish? = null,
    val places: List<PlaceWithPrice> = emptyList(),
    val error: String? = null
)

/** Сервіси доставки, посилання на які зберігаються в таблиці places. */
enum class DeliveryService(val title: String) {
    BOLT("Bolt Food")
}

/** Посилання на страву у вибраному сервісі доставки (null/порожнє = немає). */
fun Place.deliveryUrl(service: DeliveryService): String? =
    when (service) {
        DeliveryService.BOLT -> boltUrl
    }?.takeIf { it.isNotBlank() }

/**
 * Один ViewModel для екранів "Меню дій", "Рецепт" і "Заклади поруч":
 * вантажить страву та заклади, де її подають, паралельно.
 */
class DishDetailsViewModel(
    private val repository: FoodRepository,
    private val dishId: String
) : ViewModel() {

    private val _state = MutableStateFlow(DishDetailsState())
    val state: StateFlow<DishDetailsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DishDetailsState(isLoading = true)
            try {
                coroutineScope {
                    val dishRequest = async { repository.getDishById(dishId) }
                    val placesRequest = async { repository.getRestaurantsForDish(dishId) }
                    val dish = dishRequest.await()
                    val places = placesRequest.await()
                    _state.value = DishDetailsState(
                        isLoading = false,
                        dish = dish,
                        places = places,
                        error = if (dish == null) "Страву не знайдено" else null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = DishDetailsState(isLoading = false, error = e.toUserMessage())
            }
        }
    }
}
