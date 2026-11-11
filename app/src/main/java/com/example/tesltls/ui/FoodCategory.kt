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
    val color: Color
) {
    SOUPS("Soups", "Супи", R.drawable.cat_soups, Color(0xFFF6E5C8)),
    BURGERS("Burgers", "Бургери", R.drawable.cat_burgers, Color(0xFFF8DACB)),
    EASTERN("Eastern", "Східна кухня", R.drawable.cat_eastern, Color(0xFFE6EBCD)),
    PIZZA("Pizza", "Піца", R.drawable.cat_pizza, Color(0xFFF5E8B8)),
    SALADS("Salads", "Салати", R.drawable.cat_salads, Color(0xFFDDEAD5)),
    PASTA("Pasta", "Паста", R.drawable.cat_pasta, Color(0xFFF5DFCB)),
    SUSHI("Sushi", "Суші", R.drawable.cat_sushi, Color(0xFFF3DDE1)),
    SEAFOOD("Seafood", "Морська їжа", R.drawable.cat_seafood, Color(0xFFE0EBE9)),
    GRILL("Grill", "Гриль", R.drawable.cat_grill, Color(0xFFEEDDD0)),
    BREAKFAST("Breakfast", "Сніданки", R.drawable.cat_breakfast, Color(0xFFF5EBCB)),
    SANDWICHES("Sandwiches", "Сендвічі", R.drawable.cat_sandwiches, Color(0xFFE7E7CF)),
    DESSERTS("Desserts", "Десерти", R.drawable.cat_desserts, Color(0xFFF1DDEA)),
    BAKERY("Bakery", "Випічка", R.drawable.cat_bakery, Color(0xFFF0E0C8)),
    DRINKS("Drinks", "Напої", R.drawable.cat_drinks, Color(0xFFE1E4F2));

    companion object {
        /** Знаходить категорію за значенням з БД (без урахування регістру). */
        fun fromDb(value: String?): FoodCategory? =
            entries.firstOrNull { it.dbValue.equals(value, ignoreCase = true) }
    }
}
