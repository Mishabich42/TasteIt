package com.example.tesltls.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.tesltls.R

/**
 * Категорії страв у меню.
 *
 * [dbValue] — точне значення в навігації (напр. "Soups", "Burgers").
 * [title] — українська назва для відображення.
 * [searchKeywords] — ключові слова англійською для точності пошуку в TheMealDB.
 * [mealDbCategory] — категорія TheMealDB (якщо є прямий відповідник, напр. "Pasta", "Seafood", "Breakfast", "Dessert").
 * [mealDbArea] — регіон TheMealDB (напр. "Japanese", "Italian").
 */
enum class FoodCategory(
    val dbValue: String,
    val title: String,
    @DrawableRes val icon: Int,
    val color: Color,
    val searchKeywords: List<String>,
    val mealDbCategory: String? = null,
    val mealDbArea: String? = null
) {
    SOUPS(
        dbValue = "Soups",
        title = "Супи",
        icon = R.drawable.cat_soups,
        color = Color(0xFFF6E5C8),
        searchKeywords = listOf("soup", "stew", "chowder", "broth")
    ),
    BURGERS(
        dbValue = "Burgers",
        title = "Бургери",
        icon = R.drawable.cat_burgers,
        color = Color(0xFFF8DACB),
        searchKeywords = listOf("burger", "hamburger", "cheeseburger", "patty", "slider")
    ),
    EASTERN(
        dbValue = "Eastern",
        title = "Східна кухня",
        icon = R.drawable.cat_eastern,
        color = Color(0xFFE6EBCD),
        searchKeywords = listOf("curry", "teriyaki", "ramen", "noodle", "katsu", "tikka", "biryani", "dal"),
        mealDbArea = "Japanese"
    ),
    PIZZA(
        dbValue = "Pizza",
        title = "Піца",
        icon = R.drawable.cat_pizza,
        color = Color(0xFFF5E8B8),
        searchKeywords = listOf("pizza", "calzone", "flammekueche")
    ),
    SALADS(
        dbValue = "Salads",
        title = "Салати",
        icon = R.drawable.cat_salads,
        color = Color(0xFFDDEAD5),
        searchKeywords = listOf("salad", "slaw")
    ),
    PASTA(
        dbValue = "Pasta",
        title = "Паста",
        icon = R.drawable.cat_pasta,
        color = Color(0xFFF5DFCB),
        searchKeywords = listOf("pasta", "spaghetti", "lasagna", "penne", "macaroni", "fettuccine"),
        mealDbCategory = "Pasta"
    ),
    SUSHI(
        dbValue = "Sushi",
        title = "Суші",
        icon = R.drawable.cat_sushi,
        color = Color(0xFFF3DDE1),
        searchKeywords = listOf("sushi", "salmon", "tuna", "teriyaki", "roll", "japanese"),
        mealDbArea = "Japanese"
    ),
    SEAFOOD(
        dbValue = "Seafood",
        title = "Морська їжа",
        icon = R.drawable.cat_seafood,
        color = Color(0xFFE0EBE9),
        searchKeywords = listOf("fish", "salmon", "shrimp", "prawn", "crab", "squid", "cod"),
        mealDbCategory = "Seafood"
    ),
    GRILL(
        dbValue = "Grill",
        title = "Гриль",
        icon = R.drawable.cat_grill,
        color = Color(0xFFEEDDD0),
        searchKeywords = listOf("grill", "bbq", "steak", "roast", "kebab", "kabob", "ribs")
    ),
    BREAKFAST(
        dbValue = "Breakfast",
        title = "Сніданки",
        icon = R.drawable.cat_breakfast,
        color = Color(0xFFF5EBCB),
        searchKeywords = listOf("pancake", "egg", "omelette", "toast", "waffle", "bacon"),
        mealDbCategory = "Breakfast"
    ),
    SANDWICHES(
        dbValue = "Sandwiches",
        title = "Сендвічі",
        icon = R.drawable.cat_sandwiches,
        color = Color(0xFFE7E7CF),
        searchKeywords = listOf("sandwich", "wrap", "taco", "burrito", "panini", "toast")
    ),
    DESSERTS(
        dbValue = "Desserts",
        title = "Десерти",
        icon = R.drawable.cat_desserts,
        color = Color(0xFFF1DDEA),
        searchKeywords = listOf("cake", "pie", "pudding", "tart", "cookie", "chocolate", "dessert"),
        mealDbCategory = "Dessert"
    ),
    BAKERY(
        dbValue = "Bakery",
        title = "Випічка",
        icon = R.drawable.cat_bakery,
        color = Color(0xFFF0E0C8),
        searchKeywords = listOf("bread", "pie", "tart", "bun", "muffin", "pastry", "scone")
    ),
    RANDOM(
        dbValue = "Random",
        title = "Випадково",
        icon = R.drawable.cat_random,
        color = Color(0xFFFFD1DC),
        searchKeywords = emptyList()
    );

    companion object {
        /** Знаходить категорію за значенням з БД чи назвою (без урахування регістру). */
        fun fromDb(value: String?): FoodCategory? =
            entries.firstOrNull {
                it.dbValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true) ||
                    (it.mealDbCategory != null && it.mealDbCategory.equals(value, ignoreCase = true))
            }
    }
}
