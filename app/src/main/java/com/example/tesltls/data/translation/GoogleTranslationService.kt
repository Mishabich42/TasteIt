package com.example.tesltls.data.translation

import android.util.Log
import com.example.tesltls.BuildConfig
import com.example.tesltls.domain.model.AppLanguage
import com.example.tesltls.domain.model.Dish
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Оптимізований сервіс перекладу страв та рецептів через Google Gemini API (gemini-3.1-flash-lite)
 * з розширеним тайм-аутом, миттєвим кешуванням готових об'єктів страв та ML Kit fallback.
 */
class GoogleTranslationService {

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
            }
        }
    }

    private val apiKey: String = BuildConfig.GEMINI_API_KEY

    // Кеш перекладених об'єктів страв: key = "${langCode}_${dish.id}"
    private val translatedDishCache = ConcurrentHashMap<String, Dish>()

    // Кеш перекладених окремих текстів: key = "${langCode}_$originalText"
    private val translationCache = ConcurrentHashMap<String, String>()

    // Клієнти перекладачів за цільовою мовою (для ML Kit fallback)
    private val translators = ConcurrentHashMap<String, Translator>()
    private val reverseTranslators = ConcurrentHashMap<String, Translator>()
    private val downloadConditions = DownloadConditions.Builder().build()

    /**
     * Перекладає назву та текст рецепту страви [dish] на мову [target].
     * Працює з миттєвим кешуванням об'єкта Dish за 0 ms.
     */
    suspend fun translateDish(dish: Dish, target: AppLanguage): Dish {
        if (target == AppLanguage.ENGLISH) return dish

        val dishCacheKey = "${target.code}_${dish.id}"
        translatedDishCache[dishCacheKey]?.let { return it }

        val resultDish: Dish = if (apiKey.isNotBlank()) {
            try {
                val prompt = """
                    You are a professional culinary translator. Translate the following dish name and recipe instructions into ${target.displayName} (${target.code}).
                    Maintain accurate cooking terminology, exact quantities/measurements, and clear step-by-step formatting. 
                    Return your answer in this exact format with headers:
                    ===NAME===
                    [Translated Name]
                    ===RECIPE===
                    [Translated Recipe Instructions]

                    Original Name: ${dish.name}
                    Original Recipe:
                    ${dish.recipeText ?: ""}
                """.trimIndent()

                val rawResponse = callGeminiApi(prompt)
                val parsed = parseGeminiResponse(rawResponse)

                val translatedName = parsed["name"]?.takeIf { it.isNotBlank() } ?: translate(dish.name, target)
                val translatedRecipe = parsed["recipe"]?.takeIf { it.isNotBlank() } ?: dish.recipeText?.let { if (it.isNotBlank()) translate(it, target) else it }

                val updated = dish.copy(
                    name = translatedName,
                    recipeText = translatedRecipe
                )
                updateYoutubeUrl(updated, translatedName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("GeminiTranslate", "Gemini failed, falling back to ML Kit: ${e.message}")
                fallbackMlKitTranslateDish(dish, target)
            }
        } else {
            fallbackMlKitTranslateDish(dish, target)
        }

        translatedDishCache[dishCacheKey] = resultDish
        return resultDish
    }

    private suspend fun fallbackMlKitTranslateDish(dish: Dish, target: AppLanguage): Dish {
        val translatedName = translate(dish.name, target)
        val translatedRecipe = dish.recipeText?.let {
            if (it.isNotBlank()) translate(it, target) else it
        }

        val updated = dish.copy(
            name = translatedName,
            recipeText = translatedRecipe
        )
        return updateYoutubeUrl(updated, translatedName)
    }

    private fun updateYoutubeUrl(dish: Dish, translatedName: String): Dish {
        val ytQuery = "$translatedName recipe"
        val encodedYt = try {
            URLEncoder.encode(ytQuery, "UTF-8")
        } catch (_: Exception) {
            ytQuery
        }
        val translatedYoutube = if (dish.youtubeUrl.isNullOrBlank() || dish.youtubeUrl.contains("youtube.com/results")) {
            "https://www.youtube.com/results?search_query=$encodedYt"
        } else {
            dish.youtubeUrl
        }
        return dish.copy(youtubeUrl = translatedYoutube)
    }

    /**
     * Перекладає пошуковий запит з мови користувача [source] на англійську для пошуку в TheMealDB
     * виключно через локальний Google ML Kit Translate для миттєвого відгуку.
     */
    suspend fun translateToEnglish(query: String, source: AppLanguage): String {
        val trimmed = query.trim()
        if (trimmed.isBlank() || source == AppLanguage.ENGLISH) {
            return trimmed
        }

        val cacheKey = "TO_EN_${source.code}_$trimmed"
        translationCache[cacheKey]?.let { return it }

        return try {
            val translator = reverseTranslators.getOrPut(source.code) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(source.mlKitCode)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build()
                Translation.getClient(options)
            }
            translator.downloadModelIfNeeded(downloadConditions).await()
            val translated = translator.translate(trimmed).await().trim()
            translationCache[cacheKey] = translated
            translated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("GoogleTranslate", "Помилка перекладу на англійську (${source.code}): ${e.message}")
            trimmed
        }
    }

    /**
     * Перекладає текст з англійської на цільову мову [target] через ML Kit.
     */
    suspend fun translate(text: String, target: AppLanguage): String {
        if (text.isBlank() || target == AppLanguage.ENGLISH) {
            return text
        }

        val cacheKey = "${target.code}_$text"
        translationCache[cacheKey]?.let { return it }

        return try {
            val translator = getOrCreateTranslator(target)
            translator.downloadModelIfNeeded(downloadConditions).await()
            val translated = translator.translate(text).await()
            translationCache[cacheKey] = translated
            translated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("GoogleTranslate", "Помилка перекладу тексту на ${target.code}: ${e.message}")
            text
        }
    }

    private suspend fun callGeminiApi(prompt: String): String {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=$apiKey"
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                )
            )

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(request))
            }

            val responseString = response.bodyAsText()
            val geminiResponse = Json { ignoreUnknownKeys = true }.decodeFromString<GeminiResponse>(responseString)
            geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()
        } catch (e: CancellationException) {
            throw e
        }
    }

    private fun parseGeminiResponse(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val nameIndex = text.indexOf("===NAME===")
        val recipeIndex = text.indexOf("===RECIPE===")

        if (nameIndex != -1 && recipeIndex != -1) {
            val nameText = text.substring(nameIndex + "===NAME===".length, recipeIndex).trim()
            val recipeText = text.substring(recipeIndex + "===RECIPE===".length).trim()
            result["name"] = nameText
            result["recipe"] = recipeText
        } else {
            result["recipe"] = text.trim()
        }
        return result
    }

    private fun getOrCreateTranslator(target: AppLanguage): Translator =
        translators.getOrPut(target.code) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(target.mlKitCode)
                .build()
            Translation.getClient(options)
        }
}

@Serializable
data class GeminiRequest(val contents: List<GeminiContent>)

@Serializable
data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
data class GeminiPart(val text: String)

@Serializable
data class GeminiResponse(val candidates: List<GeminiCandidate>? = null)

@Serializable
data class GeminiCandidate(val content: GeminiContentResponse? = null)

@Serializable
data class GeminiContentResponse(val parts: List<GeminiPartResponse>? = null)

@Serializable
data class GeminiPartResponse(val text: String? = null)
