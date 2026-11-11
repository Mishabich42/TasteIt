package com.example.tesltls.data.translation

import android.util.Log
import com.example.tesltls.domain.model.AppLanguage
import com.example.tesltls.domain.model.Dish
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Сервіс перекладу на базі офіційного Google Translate API (Google ML Kit Translate).
 * Забезпечує миттєвий переклад страв та рецептів на обрану мову з кешуванням.
 */
class GoogleTranslationService {

    // Кеш перекладених текстів: key = "${langCode}_$originalText"
    private val translationCache = ConcurrentHashMap<String, String>()

    // Клієнти перекладачів за цільовою мовою
    private val translators = ConcurrentHashMap<String, Translator>()

    private val downloadConditions = DownloadConditions.Builder().build()

    /**
     * Перекладає текст з англійської на цільову мову [target].
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
            text // Безпечний fallback на оригінал
        }
    }

    /**
     * Перекладає назву та текст рецепту страви [dish] на мову [target].
     */
    suspend fun translateDish(dish: Dish, target: AppLanguage): Dish {
        if (target == AppLanguage.ENGLISH) return dish

        val translatedName = translate(dish.name, target)
        val translatedRecipe = dish.recipeText?.let {
            if (it.isNotBlank()) translate(it, target) else it
        }

        return dish.copy(
            name = translatedName,
            recipeText = translatedRecipe
        )
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
