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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Сервіс перекладу страв та рецептів через Google Gemini API (gemini-3.1-flash-lite)
 * з автоматичним резервним fallback на Google ML Kit Translate.
 */
class GoogleTranslationService {

    private val client = HttpClient(OkHttp)
    private val apiKey: String = BuildConfig.GEMINI_API_KEY

    // Кеш перекладених текстів: key = "${langCode}_$originalText"
    private val translationCache = ConcurrentHashMap<String, String>()

    // Клієнти перекладачів за цільовою мовою (для ML Kit fallback)
    private val translators = ConcurrentHashMap<String, Translator>()
    private val downloadConditions = DownloadConditions.Builder().build()

    /**
     * Перекладає назву та текст рецепту страви [dish] на мову [target].
     * Спочатку намагається використати Gemini API, а при помилці чи відсутності ключа — ML Kit.
     */
    suspend fun translateDish(dish: Dish, target: AppLanguage): Dish {
        if (target == AppLanguage.ENGLISH) return dish

        // 1. Пробуємо Gemini API
        if (apiKey.isNotBlank()) {
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

                return dish.copy(
                    name = translatedName,
                    recipeText = translatedRecipe
                )
            } catch (e: Exception) {
                Log.w("GeminiTranslate", "Gemini failed, falling back to ML Kit: ${e.message}")
            }
        }

        // 2. Fallback на ML Kit Translate
        val translatedName = translate(dish.name, target)
        val translatedRecipe = dish.recipeText?.let {
            if (it.isNotBlank()) translate(it, target) else it
        }

        return dish.copy(
            name = translatedName,
            recipeText = translatedRecipe
        )
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
        return geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()
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
