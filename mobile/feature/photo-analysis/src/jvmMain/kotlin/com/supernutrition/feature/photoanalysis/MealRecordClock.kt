package com.supernutrition.feature.photoanalysis

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

actual fun currentMealRecordContext(): MealRecordContext {
    val now = Instant.now()
    return mealRecordContext(
        now.epochSecond,
        ZoneId.systemDefault().rules.getOffset(now).totalSeconds,
        UUID.randomUUID().toString(),
    )
}
