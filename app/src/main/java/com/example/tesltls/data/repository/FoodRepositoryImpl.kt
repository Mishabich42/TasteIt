package com.example.tesltls.data.repository

import com.example.tesltls.domain.model.Dish
import com.example.tesltls.domain.model.DishPlace
import com.example.tesltls.domain.model.PlaceWithPrice
import com.example.tesltls.domain.model.SwipeType
import com.example.tesltls.domain.model.UserSwipeInsert
import com.example.tesltls.domain.repository.FoodRepository
import com.example.tesltls.domain.repository.FoodRepositoryException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * Реалізація [FoodRepository] поверх Supabase (плагін Postgrest, supabase-kt 3.x).
 *
 * [SupabaseClient] має бути створений з `install(Postgrest)`.
 *
 * @param clock годинник — параметр, щоб у unit-тестах можна було "заморозити" час.
 * @param timeZoneProvider часовий пояс користувача: межі "сьогодні" рахуються
 *        за локальним часом, а не за UTC (інакше для України "день" зсунувся б на 2–3 год).
 */
class FoodRepositoryImpl(
    private val supabase: SupabaseClient,
    private val clock: Clock = Clock.System,
    private val timeZoneProvider: () -> TimeZone = { TimeZone.currentSystemDefault() }
) : FoodRepository {

    // ───────────────────────── getRandomDish ─────────────────────────

    /**
     * PostgREST не вміє `ORDER BY random()`, тому випадковість робимо в 3 кроки:
     *  1. отримуємо id страв, які користувач свайпнув сьогодні;
     *  2. рахуємо, скільки страв у категорії лишилось доступними (count);
     *  3. беремо одну страву за випадковим зсувом (offset) у стабільно відсортованому списку.
     *
     * Для справжнього `random()` одним запитом див. SQL-функцію `get_random_dish`
     * у файлі sql/supabase_setup.sql.
     */
    override suspend fun getRandomDish(category: String, userId: String): Dish? =
        safeCall("getRandomDish") {
            val excludedIds = getTodaySwipedDishIds(userId)
            val dishesTable = supabase.postgrest[TABLE_DISHES]

            // Крок 2: кількість доступних страв (сам список не завантажуємо — limit(1))
            val availableCount = dishesTable
                .select(Columns.list("id")) {
                    count(Count.EXACT)
                    limit(1)
                    filter { applyAvailableDishFilter(category, excludedIds) }
                }
                .countOrNull() ?: 0L

            // Категорія порожня або користувач вже переглянув усе на сьогодні
            if (availableCount == 0L) return@safeCall null

            // Крок 3: випадковий зсув [0, availableCount)
            val randomOffset = Random.nextLong(availableCount)

            // Сортування за id обов'язкове — без нього offset не має стабільного змісту.
            // Якщо між кроками 2 і 3 дані змінились (рідкісна гонка) — повернеться null.
            dishesTable
                .select {
                    filter { applyAvailableDishFilter(category, excludedIds) }
                    order("id", Order.ASCENDING)
                    range(randomOffset..randomOffset)
                }
                .decodeList<Dish>()
                .firstOrNull()
        }

    /**
     * Повертає унікальні id страв, які користувач свайпнув СЬОГОДНІ
     * (інтервал [00:00 локального дня; 00:00 наступного дня) переведений в UTC-Instant).
     *
     * Увага: PostgREST за замовчуванням віддає максимум 1000 рядків — для свайпів
     * за один день цього більш ніж достатньо.
     */
    private suspend fun getTodaySwipedDishIds(userId: String): List<String> {
        val timeZone = timeZoneProvider()
        val today = clock.todayIn(timeZone)
        val dayStart = today.atStartOfDayIn(timeZone)
        val dayEnd = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)

        return supabase.postgrest[TABLE_USER_SWIPES]
            .select(Columns.list("dish_id")) {
                filter {
                    eq("user_id", userId)
                    gte("created_at", dayStart.toString()) // ISO-8601, напр. 2026-11-08T22:00:00Z
                    lt("created_at", dayEnd.toString())
                    // Щоб ховати лише дизлайкнуті страви, додайте: eq("swipe_type", "dislike")
                }
            }
            .decodeList<SwipedDishId>()
            .map { it.dishId }
            .distinct()
    }

    /** Спільний фільтр для обох запитів (count і вибірка): категорія + виключення вже свайпнутих. */
    private fun PostgrestFilterBuilder.applyAvailableDishFilter(
        category: String,
        excludedIds: List<String>
    ) {
        eq("category", category)
        if (excludedIds.isNotEmpty()) {
            // Рівносильно SQL: id NOT IN (...). Порожній список не передаємо — `in.()` некоректний.
            filterNot("id", FilterOperator.IN, "(${excludedIds.joinToString(",")})")
        }
    }

    // ───────────────────────── insertSwipe ─────────────────────────

    override suspend fun insertSwipe(userId: String, dishId: String, isLike: Boolean) {
        safeCall("insertSwipe") {
            val swipe = UserSwipeInsert(
                userId = userId,
                dishId = dishId,
                swipeType = if (isLike) SwipeType.LIKE else SwipeType.DISLIKE
            )
            // id та created_at генерує БД. Результат вставки нам не потрібен (returning=minimal).
            supabase.postgrest[TABLE_USER_SWIPES].insert(swipe)
        }
    }

    // ───────────────────────── getRestaurantsForDish ─────────────────────────

    /**
     * Один запит із вкладеним join: `dish_places` + пов'язаний рядок із `places`.
     * Зв'язок many-to-one (dish_places.place_id -> places.id), тому `places` приходить
     * як один об'єкт, а не масив.
     */
    override suspend fun getRestaurantsForDish(dishId: String): List<PlaceWithPrice> =
        safeCall("getRestaurantsForDish") {
            supabase.postgrest[TABLE_DISH_PLACES]
                .select(Columns.raw(DISH_PLACES_WITH_PLACE_COLUMNS)) {
                    filter { eq("dish_id", dishId) }
                    order("price", Order.ASCENDING) // спочатку найдешевші
                }
                .decodeList<DishPlace>()
                // place == null можливий, якщо RLS приховав заклад — такі рядки пропускаємо
                .mapNotNull { row -> row.place?.let { PlaceWithPrice(place = it, price = row.price) } }
        }

    // ───────────────────────── searchDishes ─────────────────────────

    /**
     * Пошук через `ILIKE '%запит%'` (регістронезалежний пошук за підрядком).
     * Для швидкості на великих таблицях додайте trigram-індекс (див. sql/supabase_setup.sql);
     * для "нечіткого" пошуку з помилками друку використовуйте pg_trgm similarity() через RPC.
     */
    override suspend fun searchDishes(query: String): List<Dish> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        return safeCall("searchDishes") {
            supabase.postgrest[TABLE_DISHES]
                .select {
                    filter { ilike("name", "%${trimmed.escapeLikePattern()}%") }
                    order("name", Order.ASCENDING)
                    limit(SEARCH_RESULTS_LIMIT)
                }
                .decodeList<Dish>()
        }
    }

    /** Екранує службові символи LIKE (\, %, _), щоб введене користувачем трактувалось буквально. */
    private fun String.escapeLikePattern(): String =
        replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    // ───────────────────────── getDishById ─────────────────────────

    override suspend fun getDishById(dishId: String): Dish? =
        safeCall("getDishById") {
            supabase.postgrest[TABLE_DISHES]
                .select {
                    filter { eq("id", dishId) }
                    limit(1)
                }
                .decodeList<Dish>()
                .firstOrNull()
        }

    // ───────────────────────── getLikedDishes ─────────────────────────

    /**
     * Лайкнуті страви одним запитом: `user_swipes` + вкладений join на `dishes`
     * (зв'язок many-to-one, тому `dishes` приходить одним об'єктом).
     * Сортуємо за часом свайпу (колонку created_at у вибірку брати не обов'язково).
     * Одну страву користувач міг лайкнути кілька разів — прибираємо дублікати.
     */
    override suspend fun getLikedDishes(userId: String): List<Dish> =
        safeCall("getLikedDishes") {
            supabase.postgrest[TABLE_USER_SWIPES]
                .select(Columns.raw(LIKED_DISHES_COLUMNS)) {
                    filter {
                        eq("user_id", userId)
                        eq("swipe_type", "like")
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<LikedDishRow>()
                .mapNotNull { it.dish }
                .distinctBy { it.id }
        }

    // ───────────────────────── Обробка помилок ─────────────────────────

    /**
     * Обгортка для всіх викликів до Supabase.
     *  - [CancellationException] прокидаємо без змін (інакше зламається структурована конкурентність);
     *  - решту винятків перетворюємо на [FoodRepositoryException].
     */
    private suspend fun <T> safeCall(operation: String, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.toRepositoryException(operation)
        }

    private fun Exception.toRepositoryException(operation: String): FoodRepositoryException =
        when (this) {
            // Немає інтернету, DNS, таймаут
            is HttpRequestTimeoutException,
            is HttpRequestException,
            is IOException -> FoodRepositoryException.Network(
                "[$operation] Проблема з мережею. Перевірте з'єднання та спробуйте ще раз.", this
            )

            // Відповідь 4xx/5xx від PostgREST: RLS, неіснуюча колонка, порушення обмежень тощо
            is RestException -> FoodRepositoryException.Database(
                "[$operation] Помилка бази даних: ${message.orEmpty()}", this
            )

            // Поля в JSON не збігаються з нашими моделями
            is SerializationException -> FoodRepositoryException.Serialization(
                "[$operation] Не вдалося обробити відповідь сервера: ${message.orEmpty()}", this
            )

            else -> FoodRepositoryException.Unknown(
                "[$operation] Невідома помилка: ${message.orEmpty()}", this
            )
        }

    // ───────────────────────── Константи ─────────────────────────

    private companion object {
        const val TABLE_DISHES = "dishes"
        const val TABLE_USER_SWIPES = "user_swipes"
        const val TABLE_DISH_PLACES = "dish_places"

        const val SEARCH_RESULTS_LIMIT = 20L

        /** Колонки dish_places + вкладений join на places (один рядок на кожен зв'язок). */
        const val DISH_PLACES_WITH_PLACE_COLUMNS =
            "id,dish_id,place_id,price,places(id,name,address,latitude,longitude,glovo_url,bolt_url)"

        /** Лише вкладена страва: з user_swipes нам потрібен тільки зв'язаний рядок dishes. */
        const val LIKED_DISHES_COLUMNS =
            "dishes(id,name,category,image_url,recipe_text,youtube_url)"
    }
}

/** Мінімальна проєкція `user_swipes` — нам потрібен лише dish_id. */
@Serializable
private data class SwipedDishId(
    @SerialName("dish_id") val dishId: String
)

/** Рядок відповіді getLikedDishes: свайп із вкладеною стравою (ключ = назва таблиці "dishes"). */
@Serializable
private data class LikedDishRow(
    @SerialName("dishes") val dish: Dish? = null
)
