package com.supernutrition.feature.photoanalysis

/** Actual local recording context, independent of photo metadata and inferred meal content. */
data class MealRecordContext(val id: String, val localDay: Int, val timeLabel: String)

expect fun currentMealRecordContext(): MealRecordContext

internal fun mealRecordContext(epochSeconds: Long, offsetSeconds: Int, id: String): MealRecordContext {
    val localSeconds = epochSeconds + offsetSeconds
    val day = localSeconds.floorDiv(SECONDS_PER_DAY)
    val secondsOfDay = localSeconds - day * SECONDS_PER_DAY
    val hour = (secondsOfDay / SECONDS_PER_HOUR).toString().padStart(2, '0')
    val minute = ((secondsOfDay % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE).toString().padStart(2, '0')
    return MealRecordContext(id, day.toInt(), "$hour:$minute")
}

private const val SECONDS_PER_DAY = 86_400L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L
