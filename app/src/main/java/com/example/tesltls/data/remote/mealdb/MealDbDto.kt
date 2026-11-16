package com.example.tesltls.data.remote.mealdb

import kotlinx.serialization.Serializable

/**
 * Модель рецепта з TheMealDB API (https://www.themealdb.com/api.php).
 */
@Serializable
data class MealDto(
    val idMeal: String,
    val strMeal: String = "",
    val strDrinkAlternate: String? = null,
    val strCategory: String? = null,
    val strArea: String? = null,
    val strInstructions: String? = null,
    val strMealThumb: String? = null,
    val strTags: String? = null,
    val strYoutube: String? = null,
    val strSource: String? = null
)

@Serializable
data class MealDbResponse(
    val meals: List<MealDto>? = null
)
