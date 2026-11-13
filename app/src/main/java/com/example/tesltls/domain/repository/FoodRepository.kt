package com.example.tesltls.domain.repository

import com.example.tesltls.domain.model.Dish
import com.example.tesltls.domain.model.PlaceWithPrice
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/**
 * Контракт доступу до даних застосунку "Food Tinder".
 *
 * Усі методи — `suspend` і безпечні для виклику з головного потоку
 * (мережеві виклики виконані асинхронно).
 *
 * У разі помилки методи кидають [FoodRepositoryException];
 * [kotlinx.coroutines.CancellationException] завжди прокидається далі без змін.
 */
interface FoodRepository {

    /**
     * Повертає випадкову страву з категорії [category], яку користувач [userId]
     * ще НЕ свайпав сьогодні (враховуються і лайки, і дизлайки).
     *
     * @return страву або `null`, якщо в категорії не лишилось доступних страв.
     */
    suspend fun getRandomDish(category: String, userId: String): Dish?

    /**
     * Зберігає свайп: [isLike] = true -> 'like', false -> 'dislike'.
     * Дизлайк одночасно потрапляє в "чорний список" на сьогодні.
     */
    suspend fun insertSwipe(userId: String, dishId: String, isLike: Boolean)

    /**
     * Повертає всі заклади, де подається страва [dishId], разом з ціною
     * (відсортовано за зростанням ціни).
     */
    suspend fun getRestaurantsForDish(dishId: String): List<PlaceWithPrice>

    /**
     * Пошук страв за частиною назви (без урахування регістру).
     * Для порожнього запиту повертає порожній список.
     */
    suspend fun searchDishes(query: String): List<Dish>

    /**
     * Покроковий (прогресивний) пошук страв: повертає потік, який спочатку миттєво
     * емітить первинні результати за 0 мс, а потім оновлює переклад кожної страви по мірі готовності.
     */
    fun searchDishesProgressive(query: String): Flow<List<Dish>>

    /**
     * Повертає страву за id або `null`, якщо такої немає.
     */
    suspend fun getDishById(dishId: String): Dish?

    /**
     * Страви, які користувач [userId] колись свайпнув вправо (like),
     * від найновіших до найстаріших, без повторів.
     */
    suspend fun getLikedDishes(userId: String): List<Dish>

    /**
     * Видаляє вибрані страви [dishIds] з улюблених.
     */
    suspend fun removeLikedDishes(userId: String, dishIds: List<String>)
}

/**
 * Реактивний покроковий пошук для рядка пошуку на екрані.
 *
 * Приймає потік тексту, який вводить користувач, і:
 *  1. чекає [debounceMs] мс після останнього символу;
 *  2. ігнорує повторення того самого запиту;
 *  3. скасовує попередній незавершений запит при новому вводі;
 *  4. НЕГАЙНО видає знайдені страви, оновлюючи переклад кожної окремо в реальному часі.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun FoodRepository.searchDishesFlow(
    queries: Flow<String>,
    debounceMs: Long = 300L
): Flow<Result<List<Dish>>> =
    queries
        .map { it.trim() }
        .debounce(debounceMs)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(Result.success(emptyList()))
            } else {
                searchDishesProgressive(query)
                    .map { Result.success(it) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Result.failure(if (e is FoodRepositoryException) e else FoodRepositoryException.Unknown(e.message.orEmpty(), e)))
                    }
            }
        }
