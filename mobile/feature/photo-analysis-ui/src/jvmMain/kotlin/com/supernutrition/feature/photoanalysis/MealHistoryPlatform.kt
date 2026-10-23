package com.supernutrition.feature.photoanalysis

import java.util.TimeZone

internal actual fun currentHistoryReferenceDay(): Int {
    val now = System.currentTimeMillis()
    return historyLocalDay(now / 1_000.0, TimeZone.getDefault().getOffset(now) / 1_000)
}
