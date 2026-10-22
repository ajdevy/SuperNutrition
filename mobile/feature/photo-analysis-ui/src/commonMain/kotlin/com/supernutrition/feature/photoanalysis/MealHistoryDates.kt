package com.supernutrition.feature.photoanalysis

import kotlin.math.floor

/** Clock is native; date conversion remains common so epoch-day labels cannot shift zones. */
internal expect fun currentHistoryReferenceDay(): Int

internal fun historyLocalDay(epochSeconds: Double, offsetSeconds: Int): Int =
    floor((epochSeconds + offsetSeconds) / 86_400.0).toInt()

/**
 * Gregorian conversion adapted from Howard Hinnant's civil_from_days formulas (public domain).
 * Source: https://howardhinnant.github.io/date_algorithms.html#civil_from_days
 * Supported range is bounded before arithmetic; stored epoch days never pass through a time zone.
 */
internal fun historyCalendarDate(epochDay: Int): HistoryCalendarDate? {
    if (epochDay !in 0..2_932_896) return null
    val shifted = epochDay.toLong() + 719_468
    val era = shifted / 146_097
    val dayOfEra = shifted - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    var year = yearOfEra + era * 400
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthIndex = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthIndex + 2) / 5 + 1
    val month = monthIndex + if (monthIndex < 10) 3 else -9
    if (month <= 2) year++
    return HistoryCalendarDate(year.toInt(), month.toInt(), day.toInt())
}

internal data class HistoryCalendarDate(val year: Int, val month: Int, val day: Int)

internal fun mealHistoryDayLabel(day: Int, referenceDay: Int, copy: MealHistoryCopy): String {
    val date = historyCalendarDate(day) ?: return copy.dateUnavailable
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val monthDay = "${months[date.month - 1]} ${date.day}"
    val calendarDate = if (date.year == historyCalendarDate(referenceDay)?.year) monthDay else "$monthDay, ${date.year}"
    return when {
        day == referenceDay -> "${copy.today} · $calendarDate"
        day.toLong() == referenceDay.toLong() - 1 -> "${copy.yesterday} · $calendarDate"
        else -> calendarDate
    }
}
