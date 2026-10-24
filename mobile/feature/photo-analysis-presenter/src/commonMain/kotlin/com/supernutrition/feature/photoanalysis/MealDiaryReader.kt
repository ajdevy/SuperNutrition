package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.CatalogMealBinding
import com.supernutrition.domain.nutrition.NutritionCatalogIdentity
import com.supernutrition.domain.nutrition.NutritionCatalogSnapshot

internal class MealDiaryDecodingException(val reason: MealDiaryCodecError) : Exception(reason.name)

internal fun failMealDiary(reason: MealDiaryCodecError): Nothing = throw MealDiaryDecodingException(reason)

internal class MealDiaryReader(private val encoded: String, start: Int) {
    private var position = start

    fun isFinished(): Boolean = position == encoded.length

    fun readNumber(): Int {
        val end = encoded.indexOf(':', position)
        if (end == -1) failMealDiary(MealDiaryCodecError.MALFORMED)
        val value = encoded.substring(position, end)
        val number = value.toIntOrNull()
        if (number == null || number < 0 || number.toString() != value) failMealDiary(MealDiaryCodecError.MALFORMED)
        position = end + 1
        return number
    }

    fun readMeal(version: Int): SavedMeal {
        val id = readField()
        val day = readField().toIntOrNull() ?: failMealDiary(MealDiaryCodecError.INVALID_RECORD)
        val time = readField()
        val name = readField()
        val portion = readPortion()
        val sourceName = readField()
        val source = SavedMealSource.entries.firstOrNull { it.name == sourceName }
            ?: failMealDiary(MealDiaryCodecError.INVALID_RECORD)
        val note = readField()
        val count = readField().toIntOrNull() ?: failMealDiary(MealDiaryCodecError.INVALID_RECORD)
        if (count !in 0..MealDraftLimits.INGREDIENTS_MAX_COUNT) failMealDiary(MealDiaryCodecError.INVALID_RECORD)
        val ingredients = List(count) { readField() }
        val binding = if (version == 2) readBinding() else null
        return SavedMeal(id, day, time, name, portion, ingredients, note, source, binding)
    }

    private fun readBinding(): CatalogMealBinding? = when (readField()) {
        "0" -> null

        "1" -> try {
            val catalogId = readBindingField(MealCatalogBindingLimits.IDENTIFIER_MAX_LENGTH)
            val version = readBindingField(MealCatalogBindingLimits.IDENTIFIER_MAX_LENGTH)
            val country = readBindingField(MealCatalogBindingLimits.COUNTRY_MAX_LENGTH)
            val digest = readBindingField(MealCatalogBindingLimits.DIGEST_LENGTH)
            val canonicalId = readBindingField(MealCatalogBindingLimits.IDENTIFIER_MAX_LENGTH)
            CatalogMealBinding(
                NutritionCatalogSnapshot(NutritionCatalogIdentity(catalogId, version), country, digest),
                canonicalId,
            )
        } catch (_: IllegalArgumentException) {
            failMealDiary(MealDiaryCodecError.INVALID_RECORD)
        }

        else -> failMealDiary(MealDiaryCodecError.INVALID_RECORD)
    }

    private fun readBindingField(maxLength: Int): String = readField().also {
        if (it.length > maxLength) failMealDiary(MealDiaryCodecError.INVALID_RECORD)
    }

    private fun readPortion(): Double? {
        val value = readField()
        if (value.isEmpty()) return null
        return value.toDoubleOrNull() ?: failMealDiary(MealDiaryCodecError.INVALID_RECORD)
    }

    private fun readField(): String {
        val length = readNumber()
        if (length > encoded.length - position) failMealDiary(MealDiaryCodecError.MALFORMED)
        val field = encoded.substring(position, position + length)
        position += length
        return field
    }
}
