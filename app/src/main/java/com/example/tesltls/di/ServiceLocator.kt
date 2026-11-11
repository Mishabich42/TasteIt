package com.example.tesltls.di

import android.annotation.SuppressLint
import android.content.Context
import com.example.tesltls.BuildConfig
import com.example.tesltls.data.local.FoodLocalStore
import com.example.tesltls.data.remote.spoonacular.SpoonacularApiClient
import com.example.tesltls.data.repository.FoodRepositoryImpl
import com.example.tesltls.data.translation.GoogleTranslationService
import com.example.tesltls.domain.repository.FoodRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Єдине місце створення залежностей застосунку.
 * Працює на базі Spoonacular Food API (https://spoonacular.com/food-api)
 * та локального сховища для збереження свайпів і улюблених страв.
 */
object ServiceLocator {

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var applicationContext: Context? = null

    /**
     * Ініціалізація з Application або Activity Context для збереження улюблених та свайпів.
     */
    fun init(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
        }
    }

    val localStore: FoodLocalStore by lazy {
        FoodLocalStore { applicationContext }
    }

    val translationService: GoogleTranslationService by lazy {
        GoogleTranslationService()
    }

    val spoonacularApiClient: SpoonacularApiClient by lazy {
        SpoonacularApiClient { BuildConfig.SPOONACULAR_API_KEY }
    }

    val foodRepository: FoodRepository by lazy {
        FoodRepositoryImpl(
            apiClient = spoonacularApiClient,
            localStore = localStore,
            translationService = translationService
        )
    }

    /**
     * Повертає стабільний ідентифікатор користувача для збереження свайпів та улюбленого.
     */
    suspend fun ensureUserId(): String =
        localStore.getOrCreateUserId()

    // Залишено для зворотної сумісності, якщо знадобиться
    val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://example.supabase.co" },
            supabaseKey = BuildConfig.SUPABASE_KEY.ifBlank { "dummy" }
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
