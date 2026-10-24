package com.supernutrition.feature.photoanalysis

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class AndroidHomeClock(val day: Int, val label: String)

/** One instant supplies both the visible date and the current-day meal count on API23+. */
internal fun currentAndroidHomeClock(): AndroidHomeClock {
    val millis = System.currentTimeMillis()
    val day = mealRecordContext(millis / 1000, TimeZone.getDefault().getOffset(millis) / 1000, "clock").localDay
    val label = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(millis))
    return AndroidHomeClock(day, label)
}
