package com.supernutrition.feature.photoanalysis

object MealDiaryLimits {
    const val ID_MAX_LENGTH = 256
    const val TIME_MAX_LENGTH = 32
    const val MEALS_MAX_COUNT = 10_000
    const val ENCODED_MAX_LENGTH = 4 * 1024 * 1024
}

enum class MealDiaryCodecError { MALFORMED, UNSUPPORTED_VERSION, INVALID_RECORD, DUPLICATE_ID, TOO_LARGE }

sealed interface MealDiaryDecodeResult {
    data class Success(val meals: List<SavedMeal>) : MealDiaryDecodeResult
    data class Failure(val reason: MealDiaryCodecError) : MealDiaryDecodeResult
}

/** Both versions use UTF-16 length-prefixed fields; version two appends an optional source identity. */
object MealDiaryCodec {
    /** Caller mistakes fail before returning a value suitable for replacing stored data. */
    fun encode(meals: List<SavedMeal>): String {
        require(meals.size <= MealDiaryLimits.MEALS_MAX_COUNT) { "Meal diary exceeds its record limit" }
        val ids = mutableSetOf<String>()
        val encoded = StringBuilder("SNMD2:${meals.size}:")
        meals.forEach { meal ->
            require(isValidSavedMeal(meal)) { "Meal diary contains an invalid record" }
            require(ids.add(meal.id)) { "Meal diary contains a duplicate record ID" }
            encoded.appendMeal(meal)
            require(encoded.length <= MealDiaryLimits.ENCODED_MAX_LENGTH) { "Meal diary exceeds its size limit" }
        }
        return encoded.toString()
    }

    /** A corrupt document returns one failure; no subset of records is restored. */
    fun decode(encoded: String): MealDiaryDecodeResult = try {
        if (encoded.length > MealDiaryLimits.ENCODED_MAX_LENGTH) failMealDiary(MealDiaryCodecError.TOO_LARGE)
        if (!encoded.startsWith("SNMD")) failMealDiary(MealDiaryCodecError.MALFORMED)
        val reader = MealDiaryReader(encoded, start = 4)
        val version = reader.readNumber()
        if (version !in 1..2) failMealDiary(MealDiaryCodecError.UNSUPPORTED_VERSION)
        val count = reader.readNumber()
        if (count > MealDiaryLimits.MEALS_MAX_COUNT) failMealDiary(MealDiaryCodecError.TOO_LARGE)
        val ids = mutableSetOf<String>()
        val meals = List(count) {
            val meal = reader.readMeal(version)
            if (!isValidSavedMeal(meal)) failMealDiary(MealDiaryCodecError.INVALID_RECORD)
            if (!ids.add(meal.id)) failMealDiary(MealDiaryCodecError.DUPLICATE_ID)
            meal
        }
        if (!reader.isFinished()) failMealDiary(MealDiaryCodecError.MALFORMED)
        MealDiaryDecodeResult.Success(meals)
    } catch (failure: MealDiaryDecodingException) {
        MealDiaryDecodeResult.Failure(failure.reason)
    }

    private fun StringBuilder.appendMeal(meal: SavedMeal) {
        listOf(
            meal.id,
            meal.localDay.toString(),
            meal.timeLabel,
            meal.name,
            meal.portionGrams?.toString().orEmpty(),
            meal.source.name,
            meal.note,
            meal.ingredients.size.toString(),
        ).forEach { appendField(it) }
        meal.ingredients.forEach { appendField(it) }
        val binding = meal.catalogBinding
        appendField(if (binding == null) "0" else "1")
        if (binding != null) {
            listOf(
                binding.snapshot.identity.catalogId,
                binding.snapshot.identity.version,
                binding.snapshot.catalogCountry,
                binding.snapshot.contentDigest,
                binding.canonicalItemId,
            ).forEach { appendField(it) }
        }
    }

    private fun StringBuilder.appendField(value: String) {
        append(value.length).append(':').append(value)
    }
}

internal fun isValidSavedMeal(meal: SavedMeal): Boolean =
    meal.id.isNotBlank() && meal.id.length <= MealDiaryLimits.ID_MAX_LENGTH && meal.localDay >= 0 &&
        meal.timeLabel.isNotBlank() && meal.timeLabel.length <= MealDiaryLimits.TIME_MAX_LENGTH &&
        meal.name.isNotBlank() && meal.name.length <= MealDraftLimits.NAME_MAX_LENGTH &&
        validSavedPortion(meal.portionGrams) &&
        meal.note.length <= MealDraftLimits.NOTE_MAX_LENGTH && validSavedIngredients(meal.ingredients) &&
        validSavedBinding(meal)

private fun validSavedBinding(meal: SavedMeal): Boolean = meal.catalogBinding?.let {
    isBoundedCatalogBinding(it) && meal.portionGrams != null && meal.ingredients.isEmpty()
} ?: true

private fun validSavedPortion(grams: Double?): Boolean = grams == null || (grams.isFinite() && grams > 0.0)

private fun validSavedIngredients(ingredients: List<String>): Boolean =
    mealIngredientErrors(ingredients, structuredIngredientLength(ingredients)).isEmpty()
