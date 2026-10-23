package com.supernutrition.feature.photoanalysis

import platform.Foundation.NSDate

internal actual fun currentHistoryReferenceDay(): Int = historyLocalDay(
    NSDate().timeIntervalSinceReferenceDate + 978_307_200.0,
    // Match the current Apple ledger's UTC epoch day, including relative labels near midnight.
    0,
)
