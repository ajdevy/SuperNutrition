package com.supernutrition.feature.photoanalysis

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

    fun readMeal(): SavedMeal {
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
        return SavedMeal(id, day, time, name, portion, List(count) { readField() }, note, source)
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
