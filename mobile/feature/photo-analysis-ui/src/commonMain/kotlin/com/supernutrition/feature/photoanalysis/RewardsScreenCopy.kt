package com.supernutrition.feature.photoanalysis

/** Complete host-localized templates; progress and earned state come from the actual cards. */
data class RewardsScreenCopy(
    val daily: String = "Daily",
    val weekly: String = "Weekly",
    val countTemplate: String = "{earned} of {total}",
    val todaySummaryTemplate: String = "Today: {earned} of {total} rewards",
    val weekSummaryTemplate: String = "This week: {earned} of {total} rewards",
)
