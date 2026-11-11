package com.example.tesltls.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.tesltls.R

/**
 * Категорії страв у меню.
 *
 * [dbValue] — точне значення колонки `dishes.category` у Supabase (англійською).
 * Саме воно передається в `FoodRepository.getRandomDish(category, ...)`.
 * [title] — підпис, який бачить користувач.
 */
enum class FoodCategory(
    val dbValue: String,
    val title: String,
    @DrawableRes val icon: Int,
    val color: Color,
    val spoonacularTag: String
) {
    SOUPS("Soups", "Супи", R.drawable.cat_soups, Color(0xFFF6E5C8), "soup"),
    BURGERS("Burgers", "Бургери", R.drawable.cat_burgers, Color(0xFFF8DACB), "burger"),
    EASTERN("Eastern", "Східна кухня", R.drawable.cat_eastern, Color(0xFFE6EBCD), "asian"),
    PIZZA("Pizza", "Піца", R.drawable.cat_pizza, Color(0xFFF5E8B8), "pizza"),
    SALADS("Salads", "Салати", R.drawable.cat_salads, Color(0xFFDDEAD5), "salad"),
    PASTA("Pasta", "Паста", R.drawable.cat_pasta, Color(0xFFF5DFCB), "pasta"),
    SUSHI("Sushi", "Суші", R.drawable.cat_sushi, Color(0xFFF3DDE1), "japanese"),
    SEAFOOD("Seafood", "Морська їжа", R.drawable.cat_seafood, Color(0xFFE0EBE9), "seafood"),
    GRILL("Grill", "Гриль", R.drawable.cat_grill, Color(0xFFEEDDD0), "bbq"),
    BREAKFAST("Breakfast", "Сніданки", R.drawable.cat_breakfast, Color(0xFFF5EBCB), "breakfast"),
    SANDWICHES("Sandwiches", "Сендвічі", R.drawable.cat_sandwiches, Color(0xFFE7E7CF), "sandwich"),
    DESSERTS("Desserts", "Десерти", R.drawable.cat_desserts, Color(0xFFF1DDEA), "dessert"),
    BAKERY("Bakery", "Випічка", R.drawable.cat_bakery, Color(0xFFF0E0C8), "bread"),
    DRINKS("Drinks", "Напої", R.drawable.cat_drinks, Color(0xFFE1E4F2), "beverage");

    companion object {
        /** Знаходить категорію за значенням з БД чи тегом (без урахування регістру). */
        fun fromDb(value: String?): FoodCategory? =
            entries.firstOrNull {
                it.dbValue.equals(value, ignoreCase = true) ||
                    it.spoonacularTag.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }

        /** Повертає Spoonacular-тег для заданого ключа категорії або сам ключ у нижньому регістрі. */
        fun toSpoonacularTag(categoryKey: String): String =
            fromDb(categoryKey)?.spoonacularTag ?: categoryKey.lowercase()
    }
}
