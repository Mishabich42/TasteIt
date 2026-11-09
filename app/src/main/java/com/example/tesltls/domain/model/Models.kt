package com.example.tesltls.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Моделі даних для таблиць Supabase (PostgreSQL).
 *
 * Примітка: для простоти ці @Serializable-моделі використовуються і як доменні сутності.
 * Якщо захочете суворіше розділення шарів — додайте окремі DTO та мапери DTO -> Domain.
 *
 * Припущення щодо схеми:
 *  - id / user_id / dish_id / place_id мають тип uuid і серіалізуються як String;
 *  - latitude / longitude у таблиці places мають обмеження NOT NULL;
 *  - решта необов'язкових колонок (image_url, recipe_text, ...) можуть бути NULL.
 */

// ───────────────────────── Тип свайпу ─────────────────────────

/** Тип свайпу. У БД зберігається як текст: 'like' або 'dislike'. */
@Serializable
enum class SwipeType {
    @SerialName("like") LIKE,
    @SerialName("dislike") DISLIKE
}

// ───────────────────────── dishes ─────────────────────────

@Serializable
data class Dish(
    val id: String,
    val name: String,
    val category: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("recipe_text") val recipeText: String? = null,
    @SerialName("youtube_url") val youtubeUrl: String? = null
)

// ───────────────────────── places ─────────────────────────

@Serializable
data class Place(
    val id: String,
    val name: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("glovo_url") val glovoUrl: String? = null,
    @SerialName("bolt_url") val boltUrl: String? = null
)

// ───────────────────────── dish_places ─────────────────────────

/**
 * Рядок таблиці `dish_places` (зв'язок "страва ↔ заклад" з ціною).
 *
 * Поле [place] заповнюється, коли запит містить вкладену вибірку (join), наприклад:
 * `select("id, dish_id, place_id, price, places(id, name, ...)")`.
 * Ключ у JSON-відповіді PostgREST збігається з назвою пов'язаної таблиці — "places",
 * тому тут `@SerialName("places")`. Якщо join не робився — поле буде null.
 */
@Serializable
data class DishPlace(
    val id: String,
    @SerialName("dish_id") val dishId: String,
    @SerialName("place_id") val placeId: String,
    val price: Double? = null,
    @SerialName("places") val place: Place? = null
)

/** Зручна модель для UI: заклад + ціна цієї страви в ньому. */
data class PlaceWithPrice(
    val place: Place,
    val price: Double?
)

// ───────────────────────── user_swipes ─────────────────────────

/** Повний рядок таблиці `user_swipes` (для читання). */
@Serializable
data class UserSwipe(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("dish_id") val dishId: String,
    @SerialName("swipe_type") val swipeType: SwipeType,
    @SerialName("created_at") val createdAt: Instant
)

/**
 * Модель для вставки нового свайпу.
 * Поля `id` та `created_at` навмисно відсутні — їх генерує сама БД
 * (gen_random_uuid() та now()), тож клієнт не може підробити час свайпу.
 */
@Serializable
data class UserSwipeInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("dish_id") val dishId: String,
    @SerialName("swipe_type") val swipeType: SwipeType
)
