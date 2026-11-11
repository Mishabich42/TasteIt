package com.example.tesltls.data.remote.spoonacular

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модель рецепта зі Spoonacular API.
 * Охоплює відповіді /recipes/random, /recipes/complexSearch та /recipes/{id}/information.
 */
@Serializable
data class SpoonacularRecipeDto(
    val id: Long,
    val title: String = "",
    val image: String? = null,
    val summary: String? = null,
    val instructions: String? = null,
    val sourceUrl: String? = null,
    val spoonacularSourceUrl: String? = null,
    val dishTypes: List<String> = emptyList(),
    val cuisines: List<String> = emptyList(),
    val readyInMinutes: Int? = null,
    val servings: Int? = null,
    val pricePerServing: Double? = null
)

@Serializable
data class SpoonacularRandomResponse(
    val recipes: List<SpoonacularRecipeDto> = emptyList()
)

@Serializable
data class SpoonacularSearchResponse(
    val results: List<SpoonacularRecipeDto> = emptyList(),
    val offset: Int = 0,
    val number: Int = 0,
    val totalResults: Int = 0
)
