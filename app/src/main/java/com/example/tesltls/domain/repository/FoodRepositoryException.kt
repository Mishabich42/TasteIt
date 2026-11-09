package com.example.tesltls.domain.repository

/**
 * Єдиний тип помилок репозиторію.
 *
 * Репозиторій ловить усі технічні винятки (Ktor, Supabase, kotlinx.serialization)
 * і перетворює їх на ці типи, щоб ViewModel / UI не залежали від деталей мережевого шару
 * та могли показати користувачеві зрозуміле повідомлення.
 */
sealed class FoodRepositoryException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** Немає інтернету, таймаут або інша помилка мережі. */
    class Network(message: String, cause: Throwable? = null) :
        FoodRepositoryException(message, cause)

    /** Supabase / PostgREST повернув помилку (RLS, неправильний запит, 4xx/5xx тощо). */
    class Database(message: String, cause: Throwable? = null) :
        FoodRepositoryException(message, cause)

    /** Відповідь сервера не вдалося розібрати (розбіжність між схемою БД і моделями). */
    class Serialization(message: String, cause: Throwable? = null) :
        FoodRepositoryException(message, cause)

    /** Будь-яка інша непередбачена помилка. */
    class Unknown(message: String, cause: Throwable? = null) :
        FoodRepositoryException(message, cause)
}
