package com.supernutrition.feature.photoanalysis

import java.util.TimeZone
import java.util.UUID

actual fun currentMealRecordContext(): MealRecordContext {
    val now = System.currentTimeMillis()
    return mealRecordContext(
        now / MILLIS_PER_SECOND,
        TimeZone.getDefault().getOffset(now) / MILLIS_PER_SECOND,
        UUID.randomUUID().toString(),
    )
}

private const val MILLIS_PER_SECOND = 1_000
