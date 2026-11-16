package com.example.tesltls.data.repository

import androidx.core.text.HtmlCompat
import com.example.tesltls.data.local.FoodLocalStore
import com.example.tesltls.data.remote.mealdb.MealDbApiClient
import com.example.tesltls.data.remote.mealdb.MealDto
import com.example.tesltls.domain.model.AppLanguage
import com.example.tesltls.domain.model.Dish
import com.example.tesltls.domain.model.Place
import com.example.tesltls.domain.model.PlaceWithPrice
import com.example.tesltls.domain.repository.FoodRepository
import com.example.tesltls.domain.repository.FoodRepositoryException
import com.example.tesltls.ui.FoodCategory
import com.example.tesltls.ui.strings.getAppStrings
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import com.example.tesltls.data.translation.GoogleTranslationService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Реалізація [FoodRepository] поверх TheMealDB API (https://www.themealdb.com/api.php)
 * з покроковим (прогресивним) асинхронним перекладом пошукових результатів.
 */
class FoodRepositoryImpl(
    private val apiClient: MealDbApiClient,
    private val localStore: FoodLocalStore,
    private val translationService: GoogleTranslationService
) : FoodRepository {

    // Кеш оригінальних (неперекладених) страв за id
    private val rawDishesCache = ConcurrentHashMap<String, Dish>()

    // Буфер випадкових страв по категоріях для плавного й швидкого свайпінгу
    private val categoryBuffers = ConcurrentHashMap<String, MutableList<Dish>>()

    // ───────────────────────── getRandomDish ─────────────────────────

    override suspend fun getRandomDish(
        category: String,
        userId: String,
        excludedDishIds: Set<String>
    ): Dish? = safeCall("getRandomDish") {
        val foodCategory = FoodCategory.fromDb(category)

        // Якщо це новий старт сесії (нічого ще не виключено) — очищаємо кешований буфер категорії
        if (excludedDishIds.isEmpty()) {
            categoryBuffers.remove(category)
        }

        // Якщо вибрано категорію "Випадково" (Random)
        if (foodCategory == FoodCategory.RANDOM || category.equals("Random", ignoreCase = true)) {
            val maxAttempts = 20
            var attempts = 0
            while (attempts < maxAttempts) {
                attempts++
                val randomMeal = apiClient.getRandomMeal() ?: continue
                val dish = randomMeal.toDomainDish(categoryFallback = "random")
                if (dish.id !in excludedDishIds) {
                    rawDishesCache[dish.id] = dish
                    return@safeCall dish.applyNameTranslation()
                }
            }
            return@safeCall null
        }

        val buffer = categoryBuffers.getOrPut(category) { mutableListOf() }

        // Шукаємо першу страву в буфері, яку користувач ще не бачив у цій сесії
        while (buffer.isNotEmpty()) {
            val candidate = buffer.removeAt(0)
            if (candidate.id !in excludedDishIds) {
                rawDishesCache[candidate.id] = candidate
                return@safeCall candidate.applyNameTranslation()
            }
        }

        // Якщо буфер вичерпано — шукаємо точно відповідні страви в TheMealDB
        val candidates = mutableListOf<MealDto>()

        // 1. Пошук за категорією TheMealDB
        foodCategory?.mealDbCategory?.let { mealCat ->
            candidates.addAll(apiClient.getMealsByCategory(mealCat))
        }

        // 2. Пошук за регіоном/кухнею TheMealDB
        foodCategory?.mealDbArea?.let { area ->
            candidates.addAll(apiClient.getMealsByArea(area))
        }

        // 3. Пошук за релевантними ключовими словами для вибраної категорії
        val keywords = foodCategory?.searchKeywords ?: listOf(category)
        for (kw in keywords) {
            val found = apiClient.searchMeals(kw)
            candidates.addAll(found)
        }

        // Fallback: якщо нічого не знайдено — шукаємо за загальним параметром category
        if (candidates.isEmpty()) {
            candidates.addAll(apiClient.searchMeals(category))
        }

        val uniqueMeals = candidates.distinctBy { it.idMeal }.shuffled()
        val newDishes = uniqueMeals.map { it.toDomainDish(categoryFallback = category) }

        for (dish in newDishes) {
            rawDishesCache[dish.id] = dish
        }

        // Додаємо в буфер лише ті, які ще НЕ показувалися в цій сесії
        val unseen = newDishes.filter { it.id !in excludedDishIds }
        if (unseen.isNotEmpty()) {
            buffer.addAll(unseen.drop(1))
            val firstDish = unseen.first()
            rawDishesCache[firstDish.id] = firstDish
            return@safeCall firstDish.applyNameTranslation()
        }

        // Якщо всі страви цієї категорії вже були показані в поточній сесії — повертаємо null
        return@safeCall null
    }

    // ───────────────────────── insertSwipe ─────────────────────────

    override suspend fun insertSwipe(userId: String, dishId: String, isLike: Boolean) {
        safeCall("insertSwipe") {
            val dish = rawDishesCache[dishId] ?: getDishById(dishId)
            if (dish != null) {
                localStore.recordSwipe(dish, isLike)
            }
        }
    }

    // ───────────────────────── getRestaurantsForDish ─────────────────────────

    override suspend fun getRestaurantsForDish(dishId: String): List<PlaceWithPrice> =
        safeCall("getRestaurantsForDish") {
            val dish = rawDishesCache[dishId] ?: getDishById(dishId, loadRecipe = false)
            val dishName = dish?.name ?: "Dish"
            val encodedName = try {
                URLEncoder.encode(dishName, "UTF-8")
            } catch (_: Exception) {
                dishName
            }

            val strings = getAppStrings(localStore.selectedLanguage.value)

            val boltUrl = "https://food.bolt.eu/search?query=$encodedName"

            listOf(
                PlaceWithPrice(
                    place = Place(
                        id = "delivery_bolt_$dishId",
                        name = "Bolt Food",
                        address = strings.boltAddress,
                        latitude = 50.4501,
                        longitude = 30.5234,
                        glovoUrl = null,
                        boltUrl = boltUrl
                    ),
                    price = null
                ),
                PlaceWithPrice(
                    place = Place(
                        id = "place_near_$dishId",
                        name = strings.nearbyName,
                        address = strings.nearbyAddress,
                        latitude = 50.4501,
                        longitude = 30.5234,
                        glovoUrl = null,
                        boltUrl = null
                    ),
                    price = null
                )
            )
        }

    // ───────────────────────── searchDishesProgressive ─────────────────────────

    override fun searchDishesProgressive(query: String): Flow<List<Dish>> = channelFlow {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }

        val targetLang = localStore.selectedLanguage.value

        // 1. Автоматично перекладаємо пошуковий запит на англійську для TheMealDB API
        val englishQuery = translationService.translateToEnglish(trimmed, targetLang)

        // 2. Отримуємо результати з TheMealDB API
        var results = apiClient.searchMeals(englishQuery)

        // Фолбек: якщо за англійським перекладом нічого не знайшли, пробуємо оригінальний запит
        if (results.isEmpty() && englishQuery != trimmed) {
            results = apiClient.searchMeals(trimmed)
        }

        val rawDishes = results.map { it.toDomainDish() }
        for (dish in rawDishes) {
            rawDishesCache[dish.id] = dish
        }

        // 3. НЕГАЙНО відображаємо всі знайдені страви на екрані за 0 мс (оригінальні назви)
        val currentDishes = rawDishes.toMutableList()
        send(currentDishes.toList())

        // 4. Поелементно асинхронно перекладаємо ТІЛЬКИ назви кожної страви та відправляємо оновлення в потік
        val mutex = Mutex()
        coroutineScope {
            rawDishes.forEachIndexed { index, dish ->
                launch {
                    try {
                        val translated = dish.applyNameTranslation()
                        mutex.withLock {
                            if (translated != currentDishes[index]) {
                                currentDishes[index] = translated
                                send(currentDishes.toList())
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override suspend fun searchDishes(query: String): List<Dish> =
        searchDishesProgressive(query).last()

    // ───────────────────────── getDishById ─────────────────────────

    override suspend fun getDishById(dishId: String, loadRecipe: Boolean): Dish? {
        rawDishesCache[dishId]?.let { cached ->
            if (loadRecipe) {
                if (cached.recipeText.isNullOrBlank() || cached.recipeText.contains("на сайті автора")) {
                    val fetched = fetchFullMealById(dishId)
                    if (fetched != null) {
                        rawDishesCache[dishId] = fetched
                        return fetched.applyTranslation()
                    }
                }
                return cached.applyTranslation()
            } else {
                return cached.applyNameTranslation()
            }
        }

        // Перевіряємо збережені улюблені
        val fromLiked = localStore.getLikedDishes().firstOrNull { it.id == dishId }
        if (fromLiked != null) {
            if (loadRecipe) {
                if (fromLiked.recipeText.isNullOrBlank() || fromLiked.recipeText.contains("на сайті автора")) {
                    val fetched = fetchFullMealById(dishId)
                    if (fetched != null) {
                        rawDishesCache[dishId] = fetched
                        return fetched.applyTranslation()
                    }
                }
                rawDishesCache[dishId] = fromLiked
                return fromLiked.applyTranslation()
            } else {
                rawDishesCache[dishId] = fromLiked
                return fromLiked.applyNameTranslation()
            }
        }

        // Завантажуємо з TheMealDB API
        return safeCall("getDishById") {
            val fetched = fetchFullMealById(dishId) ?: return@safeCall null
            rawDishesCache[fetched.id] = fetched
            if (loadRecipe) fetched.applyTranslation() else fetched.applyNameTranslation()
        }
    }

    // ───────────────────────── getLikedDishes ─────────────────────────

    override suspend fun getLikedDishes(userId: String): List<Dish> =
        safeCall("getLikedDishes") {
            val liked = localStore.getLikedDishes()
            coroutineScope {
                liked.map { dish ->
                    async {
                        rawDishesCache[dish.id] = dish
                        dish.applyNameTranslation()
                    }
                }.awaitAll()
            }
        }

    override suspend fun removeLikedDishes(userId: String, dishIds: List<String>) {
        safeCall("removeLikedDishes") {
            localStore.removeLikedDishes(dishIds.toSet())
        }
    }

    // ───────────────────────── Допоміжні методи ─────────────────────────

    private suspend fun fetchFullMealById(dishId: String): Dish? {
        val dto = apiClient.getMealById(dishId) ?: return null
        return dto.toDomainDish()
    }

    private fun MealDto.toDomainDish(categoryFallback: String? = null): Dish {
        val categoryName = strCategory?.takeIf { it.isNotBlank() } ?: categoryFallback ?: "food"

        val rawInstructions = strInstructions?.takeIf { it.isNotBlank() }
        val cleanRecipe = cleanHtml(rawInstructions).ifBlank {
            "Покроковий рецепт доступний на сайті автора."
        }

        val youtube = strYoutube?.takeIf { it.isNotBlank() } ?: run {
            val ytQuery = strMeal.replace("+", " ").trim()
            val encodedYt = try {
                URLEncoder.encode("$ytQuery recipe", "UTF-8")
            } catch (_: Exception) {
                strMeal
            }
            "https://www.youtube.com/results?search_query=$encodedYt"
        }

        return Dish(
            id = idMeal,
            name = strMeal,
            category = categoryName,
            imageUrl = strMealThumb,
            recipeText = cleanRecipe,
            youtubeUrl = youtube
        )
    }

    private fun cleanHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("\u00A0", " ")
            .trim()
    }

    private suspend fun Dish.applyNameTranslation(): Dish {
        if (!localStore.autoTranslateEnabled.value) return this
        val targetLang = localStore.selectedLanguage.value
        if (targetLang == AppLanguage.ENGLISH) return this
        return translationService.translateDishName(this, targetLang)
    }

    private suspend fun Dish.applyTranslation(): Dish {
        if (!localStore.autoTranslateEnabled.value) return this
        val targetLang = localStore.selectedLanguage.value
        if (targetLang == AppLanguage.ENGLISH) return this
        return translationService.translateDish(this, targetLang)
    }

    private suspend fun <T> safeCall(operation: String, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FoodRepositoryException) {
            throw e
        } catch (e: Exception) {
            throw FoodRepositoryException.Unknown("Помилка в $operation: ${e.message}", e)
        }
}
