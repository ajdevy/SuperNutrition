package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.RewardId
import com.supernutrition.domain.nutrition.RewardProgress
import com.supernutrition.domain.nutrition.RewardStatus
import com.supernutrition.domain.nutrition.RewardsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class RewardsPresenterTest {
    @Test
    fun `presenter keeps evaluator order and maps every card`() {
        val model = RewardsPresenterModel.from(snapshot(), copy())

        assertEquals(RewardId.entries, model.cards.map(RewardsCardModel::id))
        assertEquals(7, model.cards.size)
        assertEquals("Added to your rewards", model.cards.first().stateLabel)
        assertEquals("2 of 2", model.cards.first().progressLabel)
        assertEquals("Ready when you are", model.cards[1].stateLabel)
    }

    @Test
    fun `copy remains positive and complete`() {
        val copy = copy()

        assertTrue(copy.earnedLabel.contains("rewards", ignoreCase = true))
        assertTrue(copy.readyLabel.contains("ready", ignoreCase = true))
        assertEquals(RewardId.entries.toSet(), copy.cards.keys)
        assertTrue(
            copy.cards.values.none { card ->
                listOf("fail", "bad", "guilt", "shame", "streak", "lost")
                    .any { word ->
                        card.title.contains(word, ignoreCase = true) ||
                            card.description.contains(word, ignoreCase = true)
                    }
            },
        )
    }

    @Test
    fun `copy validation rejects incomplete copy values`() {
        assertFails { copy(title = "") }
        assertFails { copy(earnedLabel = "") }
        assertFails { copy(readyLabel = "") }
        assertFails { copy(progressTemplate = "{current}") }
        assertFails { copy(progressTemplate = "{target}") }
        assertFails { copy(cards = copy().cards - RewardId.PROTEIN_ALL_DAY) }
        val cardsWithBlankTitle = copy().cards + mapOf(
            RewardId.PROTEIN_ALL_DAY to RewardsCardCopy("", "A positive food choice."),
        )
        assertFails {
            copy(cards = cardsWithBlankTitle)
        }
    }

    @Test
    fun `model reports a missing card copy if a mutable map changes after validation`() {
        val cards = copy().cards.toMutableMap()
        val mutableCopy = copy(cards = cards)
        cards.remove(RewardId.PROTEIN_ALL_DAY)

        assertFails { RewardsPresenterModel.from(snapshot(), mutableCopy) }
    }

    private fun snapshot() = RewardsSnapshot(
        asOfDay = 10,
        cards = RewardId.entries.mapIndexed { index, id ->
            RewardStatus(
                id = id,
                progress = RewardProgress(current = if (index == 0) 2 else 0, target = if (index == 0) 2 else 1),
                isEarned = index == 0,
            )
        },
    )

    private fun copy(
        title: String = "Rewards",
        earnedLabel: String = "Added to your rewards",
        readyLabel: String = "Ready when you are",
        progressTemplate: String = "{current} of {target}",
        cards: Map<RewardId, RewardsCardCopy> = RewardId.entries.associateWith { id ->
            RewardsCardCopy(
                title = id.name.lowercase().replace('_', ' '),
                description = "A positive food choice you recorded.",
            )
        },
    ) = RewardsPresenterCopy(
        title = title,
        earnedLabel = earnedLabel,
        readyLabel = readyLabel,
        progressTemplate = progressTemplate,
        cards = cards,
        levelTitle = "Level 4 • Garden Guide",
        levelSummary = "7 rewards earned · 320 pts to next",
        earnedSectionTitle = "Earned",
        earnedSeeAllLabel = "See all 7",
        inProgressSectionTitle = "In progress",
        backLabel = "Back",
        visualCards = listOf(
            RewardsVisualCardCopy(RewardId.FIBER_PRESENT, "Fiber Friend", "Fiber goal · 3 days"),
            RewardsVisualCardCopy(RewardId.PROTEIN_ALL_DAY, "Protein Pal", "Protein · 5 meals"),
            RewardsVisualCardCopy(RewardId.SEVEN_DAYS_RECORDED, "Rhythm Keeper", "7 days in a row"),
            RewardsVisualCardCopy(RewardId.FATS_PRESENT, "Good Fats", "Fats goal · 3 days"),
            RewardsVisualCardCopy(RewardId.FERMENTED_THREE_DAYS, "Culture Keeper", "Culture · 3 days"),
            RewardsVisualCardCopy(RewardId.THIRTY_PRODUCTS, "Veggie Explorer", "30 different foods"),
            RewardsVisualCardCopy(RewardId.COLORS_OF_DAY, "Rainbow Plate", "Colors goal · 5 colors"),
            RewardsVisualCardCopy(RewardId.PROTEIN_ALL_DAY, "Protein Pro", "Protein goal · 5 meals"),
            RewardsVisualCardCopy(RewardId.SEVEN_DAYS_RECORDED, "Rhythm Master", "Rhythm goal · 7 days"),
            RewardsVisualCardCopy(RewardId.FIBER_PRESENT, "Fiber Legend", "Fiber goal · 3 days"),
        ),
    )
}
