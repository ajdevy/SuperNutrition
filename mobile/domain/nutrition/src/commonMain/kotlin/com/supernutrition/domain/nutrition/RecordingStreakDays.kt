package com.supernutrition.domain.nutrition

internal const val RECORDING_STREAK_TARGET_DAYS = 7

/** Credited current suffix, newest first; today remains available to continue yesterday's run. */
fun recordingStreakDays(recordingDays: Set<Int>, asOfDay: Int): List<Int> {
    require(asOfDay >= 0) { "Reward reference day must not be negative" }
    var day = if (asOfDay in recordingDays) asOfDay else asOfDay - 1
    val credited = mutableListOf<Int>()
    while (day >= 0 && credited.size < RECORDING_STREAK_TARGET_DAYS && day in recordingDays) {
        credited += day
        day--
    }
    return credited
}
