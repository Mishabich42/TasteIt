package com.example.tesltls.di

import com.example.tesltls.BuildConfig
import com.example.tesltls.data.repository.FoodRepositoryImpl
import com.example.tesltls.domain.repository.FoodRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Найпростіший "DI": єдине місце, де створюються залежності застосунку.
 * Коли проєкт підрісне — замініть на Koin або Hilt.
 *
 * SUPABASE_URL та SUPABASE_KEY беруться з BuildConfig (див. local.properties + build.gradle.kts).
 * У застосунок можна класти ЛИШЕ публічний (anon / publishable) ключ — ніколи service_role.
 */
object ServiceLocator {

    val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    val foodRepository: FoodRepository by lazy { FoodRepositoryImpl(supabase) }

    /**
     * Повертає id поточного користувача. Якщо користувач ще не входив —
     * виконує анонімний вхід (його потрібно увімкнути в Supabase:
     * Authentication -> Sign In / Providers -> Allow anonymous sign-ins).
     * Сесія зберігається, тому наступні запуски застосунку підхоплять того ж користувача.
     */
    suspend fun ensureUserId(): String {
        supabase.auth.awaitInitialization() // чекаємо, поки підтягнеться збережена сесія
        if (supabase.auth.currentUserOrNull() == null) {
            supabase.auth.signInAnonymously()
        }
        return supabase.auth.currentUserOrNull()?.id
            ?: error("Не вдалося отримати користувача після входу")
    }
}
