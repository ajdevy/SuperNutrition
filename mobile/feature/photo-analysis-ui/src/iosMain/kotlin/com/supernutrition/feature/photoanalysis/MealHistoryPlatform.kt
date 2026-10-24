package com.supernutrition.feature.photoanalysis

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

internal actual fun currentHistoryReferenceDay(): Int {
    val now = NSDate()
    return historyLocalDay(
        now.timeIntervalSinceReferenceDate + 978_307_200.0,
        NSTimeZone.localTimeZone.secondsFromGMTForDate(now).toInt(),
    )
}
