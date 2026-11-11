package com.example.tesltls.data.repository

import androidx.core.text.HtmlCompat
import com.example.tesltls.data.local.FoodLocalStore
import com.example.tesltls.data.remote.spoonacular.SpoonacularApiClient
import com.example.tesltls.data.remote.spoonacular.SpoonacularRecipeDto
import com.example.tesltls.data.translation.GoogleTranslationService
import com.example.tesltls.domain.model.AppLanguage
import com.example.tesltls.domain.model.Dish
import com.example.tesltls.domain.model.Place
import com.example.tesltls.domain.model.PlaceWithPrice
import com.example.tesltls.domain.repository.FoodRepository
import com.example.tesltls.domain.repository.FoodRepositoryException
import com.example.tesltls.ui.FoodCategory
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

import com.example.tesltls.ui.strings.getAppStrings

/**
 * Реалізація [FoodRepository] поверх Spoonacular Food API (https://spoonacular.com/food-api)
 * з автоматичним перекладом через Google Translate.
 */
class FoodRepositoryImpl(
    private val apiClient: SpoonacularApiClient,
    private val localStore: FoodLocalStore,
    private val translationService: GoogleTranslationService
) : FoodRepository {

    // Кеш оригінальних (неперекладених) страв за id
    private val rawDishesCache = ConcurrentHashMap<String, Dish>()

    // Буфер випадкових страв по категоріях для плавного й швидкого свайпінгу
    private val categoryBuffers = ConcurrentHashMap<String, MutableList<Dish>>()

    // ───────────────────────── getRandomDish ─────────────────────────

    override suspend fun getRandomDish(category: String, userId: String): Dish? =
        safeCall("getRandomDish") {
            val tag = FoodCategory.toSpoonacularTag(category)
            val swipedIds = localStore.getTodaySwipedDishIds()

            val buffer = categoryBuffers.getOrPut(tag) { mutableListOf() }

            // Шукаємо першу страву в буфері, яку користувач ще не свайпав сьогодні
            while (buffer.isNotEmpty()) {
                val candidate = buffer.removeAt(0)
                if (candidate.id !in swipedIds) {
                    rawDishesCache[candidate.id] = candidate
                    return@safeCall candidate.applyTranslation()
                }
            }

            // Якщо буфер вичерпано — завантажуємо нову порцію страв зі Spoonacular
            val fetchedDtos = apiClient.getRandomRecipes(tag = tag, number = 8)
            val newDishes = fetchedDtos.map { it.toDomainDish(categoryFallback = category) }

            for (dish in newDishes) {
                rawDishesCache[dish.id] = dish
            }

            // Додаємо в буфер лише не свайпнуті
            val unswiped = newDishes.filter { it.id !in swipedIds }
            if (unswiped.isNotEmpty()) {
                buffer.addAll(unswiped.drop(1))
                val firstDish = unswiped.first()
                return@safeCall firstDish.applyTranslation()
            }

            // Якщо всі виявилися свайпнутими — повертаємо першу доступну або null
            newDishes.firstOrNull()?.applyTranslation()
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
            val dish = getDishById(dishId)
            val dishName = dish?.name ?: "Dish"
            val encodedName = try {
                URLEncoder.encode(dishName, "UTF-8")
            } catch (_: Exception) {
                dishName
            }

            val strings = getAppStrings(localStore.selectedLanguage.value)

            listOf(
                PlaceWithPrice(
                    place = Place(
                        id = "delivery_glovo_$dishId",
                        name = "Glovo",
                        address = strings.glovoAddress,
                        latitude = 50.4501,
                        longitude = 30.5234,
                        glovoUrl = "https://glovoapp.com/ua/uk/kyiv/search/?query=$encodedName",
                        boltUrl = null
                    ),
                    price = 180.0
                ),
                PlaceWithPrice(
                    place = Place(
                        id = "delivery_bolt_$dishId",
                        name = "Bolt Food",
                        address = strings.boltAddress,
                        latitude = 50.4501,
                        longitude = 30.5234,
                        glovoUrl = null,
                        boltUrl = "https://food.bolt.eu/uk-UA"
                    ),
                    price = 210.0
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

    // ───────────────────────── searchDishes ─────────────────────────

    override suspend fun searchDishes(query: String): List<Dish> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        return safeCall("searchDishes") {
            val results = apiClient.searchRecipes(trimmed, number = 20)
            val dishes = results.map { it.toDomainDish() }
            dishes.map { dish ->
                rawDishesCache[dish.id] = dish
                dish.applyTranslation()
            }
        }
    }

    // ───────────────────────── getDishById ─────────────────────────

    override suspend fun getDishById(dishId: String): Dish? {
        rawDishesCache[dishId]?.let { return it.applyTranslation() }

        // Перевіряємо збережені улюблені
        val fromLiked = localStore.getLikedDishes().firstOrNull { it.id == dishId }
        if (fromLiked != null) {
            rawDishesCache[dishId] = fromLiked
            return fromLiked.applyTranslation()
        }

        // Завантажуємо зі Spoonacular API за числовим ID
        val numericId = dishId.toLongOrNull() ?: return null
        return safeCall("getDishById") {
            val dto = apiClient.getRecipeInformation(numericId) ?: return@safeCall null
            val dish = dto.toDomainDish()
            rawDishesCache[dish.id] = dish
            dish.applyTranslation()
        }
    }

    // ───────────────────────── getLikedDishes ─────────────────────────

    override suspend fun getLikedDishes(userId: String): List<Dish> =
        safeCall("getLikedDishes") {
            val liked = localStore.getLikedDishes()
            liked.map { dish ->
                rawDishesCache[dish.id] = dish
                dish.applyTranslation()
            }
        }

    // ───────────────────────── Допоміжні методи ─────────────────────────

    private fun SpoonacularRecipeDto.toDomainDish(categoryFallback: String? = null): Dish {
        val categoryName = dishTypes.firstOrNull() ?: categoryFallback ?: "food"

        val rawInstructions = instructions?.takeIf { it.isNotBlank() }
            ?: summary?.takeIf { it.isNotBlank() }

        val cleanRecipe = cleanHtml(rawInstructions).ifBlank {
            "Покроковий рецепт доступний на сайті автора."
        }

        val ytQuery = title.replace("+", " ").trim()
        val encodedYt = try {
            URLEncoder.encode("$ytQuery recipe", "UTF-8")
        } catch (_: Exception) {
            title
        }
        val youtube = "https://www.youtube.com/results?search_query=$encodedYt"

        return Dish(
            id = id.toString(),
            name = title,
            category = categoryName,
            imageUrl = image,
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
