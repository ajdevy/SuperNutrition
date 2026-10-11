package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.RewardId
import com.supernutrition.domain.nutrition.RewardStatus
import com.supernutrition.domain.nutrition.RewardsSnapshot

data class RewardsCardCopy(val title: String, val description: String)

data class RewardsVisualCardCopy(val rewardId: RewardId, val title: String, val subtitle: String)

data class RewardsPresenterCopy(
    val title: String,
    val earnedLabel: String,
    val readyLabel: String,
    val progressTemplate: String,
    val cards: Map<RewardId, RewardsCardCopy>,
    val levelTitle: String,
    val levelSummary: String,
    val earnedSectionTitle: String,
    val earnedSeeAllLabel: String,
    val inProgressSectionTitle: String,
    val backLabel: String,
    val visualCards: List<RewardsVisualCardCopy>,
) {
    init {
        require(title.isNotBlank())
        require(earnedLabel.isNotBlank())
        require(readyLabel.isNotBlank())
        require(progressTemplate.contains("{current}"))
        require(progressTemplate.contains("{target}"))
        require(cards.keys.containsAll(RewardId.entries)) {
            "Rewards copy must define every reward card"
        }
        require(cards.values.all { it.title.isNotBlank() && it.description.isNotBlank() })
        require(levelTitle.isNotBlank())
        require(levelSummary.isNotBlank())
        require(earnedSectionTitle.isNotBlank())
        require(earnedSeeAllLabel.isNotBlank())
        require(inProgressSectionTitle.isNotBlank())
        require(backLabel.isNotBlank())
        require(visualCards.size == 10)
        require(visualCards.all { it.title.isNotBlank() && it.subtitle.isNotBlank() })
    }
}

data class RewardsCardModel(
    val id: RewardId,
    val title: String,
    val description: String,
    val progressLabel: String,
    val stateLabel: String,
    val isEarned: Boolean,
    val progressFraction: Float = 0f,
)

data class RewardsPresenterModel(
    val title: String,
    val cards: List<RewardsCardModel>,
    val levelTitle: String = "",
    val levelSummary: String = "",
    val earnedSectionTitle: String = "",
    val earnedSeeAllLabel: String = "",
    val inProgressSectionTitle: String = "",
    val backLabel: String = "",
    val visualCards: List<RewardsVisualCardCopy> = emptyList(),
) {
    companion object {
        fun from(snapshot: RewardsSnapshot, copy: RewardsPresenterCopy): RewardsPresenterModel = RewardsPresenterModel(
            title = copy.title,
            cards = snapshot.cards.map { status -> status.toModel(copy) },
            levelTitle = copy.levelTitle,
            levelSummary = copy.levelSummary,
            earnedSectionTitle = copy.earnedSectionTitle,
            earnedSeeAllLabel = copy.earnedSeeAllLabel,
            inProgressSectionTitle = copy.inProgressSectionTitle,
            backLabel = copy.backLabel,
            visualCards = copy.visualCards,
        )

        private fun RewardStatus.toModel(copy: RewardsPresenterCopy): RewardsCardModel {
            val cardCopy = requireNotNull(copy.cards[id]) { "Missing copy for $id" }
            return RewardsCardModel(
                id = id,
                title = cardCopy.title,
                description = cardCopy.description,
                progressLabel = copy.progressTemplate
                    .replace("{current}", progress.current.toString())
                    .replace("{target}", progress.target.toString()),
                stateLabel = if (isEarned) copy.earnedLabel else copy.readyLabel,
                isEarned = isEarned,
                progressFraction = (progress.current.toFloat() / progress.target).coerceIn(0f, 1f),
            )
        }
    }
}
