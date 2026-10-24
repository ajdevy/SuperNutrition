package com.supernutrition.feature.photoanalysis

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import kotlin.random.Random

actual fun currentMealRecordContext(): MealRecordContext {
    val now = NSDate()
    return mealRecordContext(
        (now.timeIntervalSinceReferenceDate + APPLE_REFERENCE_EPOCH_SECONDS).toLong(),
        NSTimeZone.localTimeZone.secondsFromGMTForDate(now).toInt(),
        Random.nextBytes(MEAL_ID_BYTES).joinToString("") { byte -> byte.toUByte().toString(16).padStart(2, '0') },
    )
}

private const val APPLE_REFERENCE_EPOCH_SECONDS = 978_307_200.0
private const val MEAL_ID_BYTES = 16
