package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

/** Synthetic, explicit Figma examples. Never used by a production host or ledger. */
internal fun mealHistoryDesignFixture(): List<MealHistoryMeal> = listOf(
    designMeal(
        "salmon",
        "Salmon grain bowl",
        listOf("Salmon", "quinoa", "broccoli"),
        "13:15",
        "Salmon, quinoa, broccoli +2",
    ),
    designMeal("egg", "Egg toast", listOf("White bread", "egg", "butter"), "08:40"),
    designMeal(
        "chicken",
        "Chicken & vegetables",
        listOf("Chicken", "peppers", "rice"),
        "19:10",
        "Chicken, peppers, rice +1",
    ),
    designMeal("greens", "Green power bowl", listOf("Spinach", "avocado", "chickpeas"), "12:20"),
    designMeal("oats", "Oatmeal with apple", listOf("Oats", "apple", "cinnamon"), "08:30"),
)

private fun designMeal(
    id: String,
    title: String,
    ingredients: List<String>,
    time: String,
    summary: String? = null,
): MealHistoryMeal = MealHistoryMeal(
    id = MealHistoryId(if (id == "salmon" || id == "egg") 20747 else 20746, id),
    title = title,
    ingredients = ingredients,
    metadata = MealHistoryMetadata(time, fixtureHistoryImage(id), summary),
)

private fun fixtureHistoryImage(name: String): ImageBitmap? {
    if (name !in listOf("salmon", "egg", "chicken")) return null
    val bytes = requireNotNull(object {}.javaClass.getResourceAsStream("/meal-history/$name.png")) {
        "Missing explicit Meal history design fixture $name"
    }.use { it.readBytes() }
    return SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}
