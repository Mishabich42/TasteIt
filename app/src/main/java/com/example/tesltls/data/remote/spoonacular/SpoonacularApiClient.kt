package com.example.tesltls.data.remote.spoonacular

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
 * HTTP-клієнт для взаємодії зі Spoonacular Food API (https://spoonacular.com/food-api).
 */
class SpoonacularApiClient(
    private val apiKeyProvider: () -> String
) {
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
     * Отримує список випадкових рецептів з можливістю фільтрації за тегом [tag].
     */
    suspend fun getRandomRecipes(tag: String?, number: Int = 10): List<SpoonacularRecipeDto> {
        val apiKey = ensureApiKey()
        val tagParam = if (!tag.isNullOrBlank()) {
            "&include-tags=" + URLEncoder.encode(tag.trim().lowercase(), "UTF-8")
        } else ""

        val url = "$BASE_URL/recipes/random?number=$number$tagParam&apiKey=$apiKey"
        val responseText = executeGet(url, "getRandomRecipes")
        val response = try {
            json.decodeFromString<SpoonacularRandomResponse>(responseText)
        } catch (e: SerializationException) {
            throw FoodRepositoryException.Serialization("Не вдалося розібрати відповідь Spoonacular", e)
        }
        return response.recipes
    }

    /**
     * Пошук рецептів за запитом [query].
     */
    suspend fun searchRecipes(query: String, number: Int = 20): List<SpoonacularRecipeDto> {
        val apiKey = ensureApiKey()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val url = "$BASE_URL/recipes/complexSearch?query=$encodedQuery&number=$number&addRecipeInformation=true&apiKey=$apiKey"
        val responseText = executeGet(url, "searchRecipes")
        val response = try {
            json.decodeFromString<SpoonacularSearchResponse>(responseText)
        } catch (e: SerializationException) {
            throw FoodRepositoryException.Serialization("Не вдалося розібрати відповідь Spoonacular", e)
        }
        return response.results
    }

    /**
     * Отримання повної інформації про страву за її Spoonacular ID.
     */
    suspend fun getRecipeInformation(id: Long): SpoonacularRecipeDto? {
        val apiKey = ensureApiKey()
        val url = "$BASE_URL/recipes/$id/information?apiKey=$apiKey"
        val responseText = executeGet(url, "getRecipeInformation")
        return try {
            json.decodeFromString<SpoonacularRecipeDto>(responseText)
        } catch (e: SerializationException) {
            throw FoodRepositoryException.Serialization("Не вдалося розібрати інформацію про рецепт #$id", e)
        }
    }

    private suspend fun executeGet(url: String, operation: String): String =
        try {
            val response = httpClient.get(url)
            val statusCode = response.status
            val body = response.bodyAsText()

            when (statusCode) {
                HttpStatusCode.OK -> body
                HttpStatusCode.Unauthorized -> throw FoodRepositoryException.Network(
                    "Недійсний Spoonacular API ключ (401). Перевірте SPOONACULAR_API_KEY у local.properties."
                )
                HttpStatusCode.PaymentRequired, HttpStatusCode.TooManyRequests -> throw FoodRepositoryException.Network(
                    "Вичерпано денний ліміт запитів Spoonacular API (402/429)."
                )
                else -> throw FoodRepositoryException.Database(
                    "Помилка сервера Spoonacular ($statusCode): $body"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FoodRepositoryException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            throw FoodRepositoryException.Network("Таймаут з'єднання зі Spoonacular. Перевірте інтернет.", e)
        } catch (e: IOException) {
            throw FoodRepositoryException.Network("Проблема з мережею при зверненні до Spoonacular: ${e.message}", e)
        } catch (e: Exception) {
            throw FoodRepositoryException.Unknown("Невідома помилка під час запиту до Spoonacular ($operation): ${e.message}", e)
        }

    private fun ensureApiKey(): String {
        val key = apiKeyProvider().trim()
        if (key.isBlank()) {
            throw FoodRepositoryException.Network(
                "SPOONACULAR_API_KEY не вказано! Додайте SPOONACULAR_API_KEY=ваш_ключ у файл local.properties та перезапустіть додаток."
            )
        }
        return key
    }

    companion object {
        private const val BASE_URL = "https://api.spoonacular.com"
    }
}
