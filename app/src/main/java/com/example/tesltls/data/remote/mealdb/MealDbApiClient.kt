package com.example.tesltls.data.remote.mealdb

import com.example.tesltls.domain.repository.FoodRepositoryException
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * HTTP-клієнт для взаємодії з TheMealDB API (https://www.themealdb.com/api.php).
 */
class MealDbApiClient {
    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(20, TimeUnit.SECONDS)
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Отримує список рецептів для категорії [category] (напр. "Seafood", "Beef", "Pasta").
     */
    suspend fun getMealsByCategory(category: String): List<MealDto> {
        val encodedCategory = URLEncoder.encode(category.trim(), "UTF-8")
        val url = "$BASE_URL/filter.php?c=$encodedCategory"
        val responseText = executeGet(url, "getMealsByCategory")
        val response = try {
            json.decodeFromString<MealDbResponse>(responseText)
        } catch (e: SerializationException) {
            throw FoodRepositoryException.Serialization("Не вдалося розібрати відповідь TheMealDB", e)
        }
        return response.meals.orEmpty()
    }

    /**
     * Отримує список рецептів для регіону/кухні [area] (напр. "Japanese", "Chinese", "Italian").
     */
    suspend fun getMealsByArea(area: String): List<MealDto> {
        val encodedArea = URLEncoder.encode(area.trim(), "UTF-8")
        val url = "$BASE_URL/filter.php?a=$encodedArea"
        val responseText = executeGet(url, "getMealsByArea")
        val response = try {
            json.decodeFromString<MealDbResponse>(responseText)
        } catch (e: SerializationException) {
            throw FoodRepositoryException.Serialization("Не вдалося розібрати відповідь TheMealDB", e)
        }
        return response.meals.orEmpty()
    }

    /**
     * Отримує випадковий рецепт.
     */
    suspend fun getRandomMeal(): MealDto? {
        val url = "$BASE_URL/random.php"
        val responseText = executeGet(url, "getRandomMeal")
        val response = try {
            json.decodeFromString<MealDbResponse>(responseText)
        } catch (e: SerializationException) {
            throw FoodRepositoryException.Serialization("Не вдалося розібрати відповідь TheMealDB", e)
        }
        return response.meals?.firstOrNull()
    }

    /**
     * Пошук рецептів за запитом [query].
     */
    suspend fun searchMeals(query: String): List<MealDto> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val url = "$BASE_URL/search.php?s=$encodedQuery"
        val responseText = executeGet(url, "searchMeals")
        val response = try {
            json.decodeFromString<MealDbResponse>(responseText)
        } catch (e: SerializationException) {
            throw FoodRepositoryException.Serialization("Не вдалося розібрати відповідь TheMealDB", e)
        }
        return response.meals.orEmpty()
    }

    /**
     * Отримання повної інформації про страву за її TheMealDB ID.
     */
    suspend fun getMealById(id: String): MealDto? {
        val encodedId = URLEncoder.encode(id.trim(), "UTF-8")
        val url = "$BASE_URL/lookup.php?i=$encodedId"
        val responseText = executeGet(url, "getMealById")
        val response = try {
            json.decodeFromString<MealDbResponse>(responseText)
        } catch (e: SerializationException) {
            throw FoodRepositoryException.Serialization("Не вдалося розібрати інформацію про рецепт #$id", e)
        }
        return response.meals?.firstOrNull()
    }

    private suspend fun executeGet(url: String, operation: String): String =
        try {
            val response = httpClient.get(url)
            val statusCode = response.status
            val body = response.bodyAsText()

            when (statusCode) {
                HttpStatusCode.OK -> body
                else -> throw FoodRepositoryException.Database(
                    "Помилка сервера TheMealDB ($statusCode): $body"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FoodRepositoryException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            throw FoodRepositoryException.Network("Таймаут з'єднання з TheMealDB. Перевірте інтернет.", e)
        } catch (e: IOException) {
            throw FoodRepositoryException.Network("Проблема з мережею при зверненні до TheMealDB: ${e.message}", e)
        } catch (e: Exception) {
            throw FoodRepositoryException.Unknown("Невідома помилка під час запиту до TheMealDB ($operation): ${e.message}", e)
        }

    companion object {
        private const val BASE_URL = "https://www.themealdb.com/api/json/v1/1"
    }
}
