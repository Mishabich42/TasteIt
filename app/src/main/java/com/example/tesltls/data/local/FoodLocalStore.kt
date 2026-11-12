package com.example.tesltls.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.tesltls.domain.model.AppLanguage
import com.example.tesltls.domain.model.Dish
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Локальне сховище для свайпів, улюблених страв, мови та ідентифікатора користувача.
 */
class FoodLocalStore(
    contextProvider: () -> Context?
) {
    private val getContext = contextProvider

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val prefs: SharedPreferences?
        get() = getContext()?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // In-memory fallback якщо Context ще не ініціалізовано
    private val memSwipedToday = mutableSetOf<String>()
    private var memSwipedDate: String = ""
    private val memLikedDishes = mutableListOf<Dish>()
    private var memUserId: String? = null

    private val _selectedLanguage = MutableStateFlow(loadInitialLanguage())
    val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage.asStateFlow()

    private val _autoTranslateEnabled = MutableStateFlow(loadInitialAutoTranslate())
    val autoTranslateEnabled: StateFlow<Boolean> = _autoTranslateEnabled.asStateFlow()

    private fun loadInitialLanguage(): AppLanguage {
        val code = prefs?.getString(KEY_LANGUAGE, null)
        return AppLanguage.fromCode(code)
    }

    private fun loadInitialAutoTranslate(): Boolean {
        return prefs?.getBoolean(KEY_AUTO_TRANSLATE, true) ?: true
    }

    fun setLanguage(language: AppLanguage) {
        prefs?.edit()?.putString(KEY_LANGUAGE, language.code)?.apply()
        _selectedLanguage.value = language
    }

    fun setAutoTranslate(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_TRANSLATE, enabled)?.apply()
        _autoTranslateEnabled.value = enabled
    }

    /**
     * Повертає стабільний UUID користувача.
     */
    fun getOrCreateUserId(): String {
        prefs?.getString(KEY_USER_ID, null)?.let { return it }
        memUserId?.let { return it }

        val newId = UUID.randomUUID().toString()
        prefs?.edit()?.putString(KEY_USER_ID, newId)?.apply()
        memUserId = newId
        return newId
    }

    /**
     * Повертає список id страв, свайпнутих сьогодні.
     */
    fun getTodaySwipedDishIds(timeZone: TimeZone = TimeZone.currentSystemDefault()): Set<String> {
        val todayStr = Clock.System.todayIn(timeZone).toString()
        val p = prefs
        if (p != null) {
            val savedDate = p.getString(KEY_SWIPE_DATE, null)
            if (savedDate != todayStr) {
                // Новий день — очищаємо список свайпів на сьогодні
                p.edit()
                    .putString(KEY_SWIPE_DATE, todayStr)
                    .putStringSet(KEY_SWIPED_TODAY_IDS, emptySet())
                    .apply()
                return emptySet()
            }
            return p.getStringSet(KEY_SWIPED_TODAY_IDS, emptySet()) ?: emptySet()
        } else {
            if (memSwipedDate != todayStr) {
                memSwipedDate = todayStr
                memSwipedToday.clear()
            }
            return memSwipedToday.toSet()
        }
    }

    /**
     * Записує свайп. Якщо [isLike] = true, додає [dish] до збережених улюблених.
     */
    fun recordSwipe(dish: Dish, isLike: Boolean, timeZone: TimeZone = TimeZone.currentSystemDefault()) {
        val todayStr = Clock.System.todayIn(timeZone).toString()
        val dishId = dish.id

        val p = prefs
        if (p != null) {
            val currentSaved = getTodaySwipedDishIds(timeZone).toMutableSet()
            currentSaved.add(dishId)
            p.edit()
                .putString(KEY_SWIPE_DATE, todayStr)
                .putStringSet(KEY_SWIPED_TODAY_IDS, currentSaved)
                .apply()

            if (isLike) {
                val liked = getLikedDishes().toMutableList()
                liked.removeAll { it.id == dishId }
                liked.add(0, dish) // на початок списку
                saveLikedDishes(liked)
            }
        } else {
            if (memSwipedDate != todayStr) {
                memSwipedDate = todayStr
                memSwipedToday.clear()
            }
            memSwipedToday.add(dishId)
            if (isLike) {
                memLikedDishes.removeAll { it.id == dishId }
                memLikedDishes.add(0, dish)
            }
        }
    }

    fun getLikedDishes(): List<Dish> {
        val p = prefs ?: return memLikedDishes.toList()
        val rawJson = p.getString(KEY_LIKED_DISHES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Dish>>(rawJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveLikedDishes(dishes: List<Dish>) {
        val p = prefs ?: return
        try {
            val rawJson = json.encodeToString(dishes)
            p.edit().putString(KEY_LIKED_DISHES, rawJson).apply()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val PREFS_NAME = "food_tinder_mealdb_prefs"
        private const val KEY_USER_ID = "pref_user_id"
        private const val KEY_SWIPE_DATE = "pref_swipe_date"
        private const val KEY_SWIPED_TODAY_IDS = "pref_swiped_today_ids"
        private const val KEY_LIKED_DISHES = "pref_liked_dishes_json"
        private const val KEY_LANGUAGE = "pref_selected_language"
        private const val KEY_AUTO_TRANSLATE = "pref_auto_translate_enabled"
    }
}
