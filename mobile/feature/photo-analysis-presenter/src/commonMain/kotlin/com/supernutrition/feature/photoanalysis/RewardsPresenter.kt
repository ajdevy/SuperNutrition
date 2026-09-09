package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.RewardId
import com.supernutrition.domain.nutrition.RewardStatus
import com.supernutrition.domain.nutrition.RewardsSnapshot

data class RewardsCardCopy(val title: String, val description: String)

data class RewardsPresenterCopy(
    val title: String,
    val earnedLabel: String,
    val readyLabel: String,
    val progressTemplate: String,
    val cards: Map<RewardId, RewardsCardCopy>,
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
    }
}

data class RewardsCardModel(
    val id: RewardId,
    val title: String,
    val description: String,
    val progressLabel: String,
    val stateLabel: String,
    val isEarned: Boolean,
)

data class RewardsPresenterModel(val title: String, val cards: List<RewardsCardModel>) {
    companion object {
        fun from(snapshot: RewardsSnapshot, copy: RewardsPresenterCopy): RewardsPresenterModel = RewardsPresenterModel(
            title = copy.title,
            cards = snapshot.cards.map { status -> status.toModel(copy) },
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
            )
        }
    }
}
