package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.graphics.ImageBitmap
import com.supernutrition.domain.nutrition.ConfirmedConsumedFood

/** Epoch day and opaque meal ID jointly identify a confirmed meal. */
data class MealHistoryId(val localDay: Int, val mealId: String)

/** Optional presentation metadata, absent from the confirmed-food ledger. */
data class MealHistoryMetadata(
    val timeLabel: String? = null,
    val image: ImageBitmap? = null,
    val ingredientSummary: String? = null,
)

/** Names are grounded catalog labels, never labels inferred from an opaque ID. */
data class MealHistoryMeal(
    val id: MealHistoryId,
    val title: String,
    val ingredients: List<String>,
    val metadata: MealHistoryMetadata = MealHistoryMetadata(),
)

/** One adapter supplies both the Profile count and the dedicated history. */
fun confirmedMealHistory(foods: List<ConfirmedConsumedFood>): List<MealHistoryMeal> = foods
    .groupBy { MealHistoryId(it.localDay, it.mealId) }
    .map { (id, entries) ->
        val names = entries.map { it.displayName }
        MealHistoryMeal(id, names.first(), names)
    }
    // Kotlin's stable sort retains the ledger's insertion order within each day.
    .sortedByDescending { it.id.localDay }

fun searchMealHistory(meals: List<MealHistoryMeal>, query: String): List<MealHistoryMeal> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return meals
    val words = buildString(trimmed.length) {
        trimmed.forEach { append(if (it.isWhitespace()) ' ' else it) }
    }.split(' ').filter { it.isNotEmpty() }
    return meals.filter { meal ->
        val names = listOf(meal.title) + meal.ingredients
        words.all { word -> names.any { it.contains(word, ignoreCase = true) } }
    }
}
