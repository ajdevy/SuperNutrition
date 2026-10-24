package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.RewardId

data class RewardDetailsScreenCopy(
    val back: String = "Back",
    val inProgress: String = "In progress",
    val howItWorks: String = "How it works",
    val whatCounts: String = "What counts",
    val noFoods: String = "No confirmed foods yet",
    val noFoodsDescription: String = "Nutrition and food groups aren't available for your saved meals yet.",
    val exampleDescription: String = "Examples are guidance. Only confirmed food groups contribute to these rewards.",
    val cards: Map<RewardId, RewardDetailCopy> = RewardId.entries.associateWith(::rewardDetailCopy),
    val noRecordings: String = "No recordings yet",
    val noRecordingsDescription: String = "Add a meal to begin a recording streak.",
    val recordingDescription: String = "Saved diary entries and confirmed food records both count. " +
        "Nutrition data is not required for this reward.",
) {
    init {
        require(cards.keys.containsAll(RewardId.entries)) { "Reward detail copy must define every reward" }
    }
}
