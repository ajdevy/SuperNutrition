package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.domain.nutrition.GroundedNutritionResult
import com.supernutrition.domain.nutrition.RewardFoodAttributes
import com.supernutrition.domain.nutrition.RewardsEvaluator
import com.supernutrition.domain.nutrition.RewardsInput
import com.supernutrition.domain.nutrition.RewardsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retained Rewards state backed by the confirmed-food ledger. */
class RewardsFeature(
    private val localDay: () -> Int,
    initialFoods: List<ConfirmedConsumedFood> = emptyList(),
    ledgerStore: ConsumedFoodLedgerStore = InMemoryConsumedFoodLedgerStore(),
) {
    private val _snapshot = MutableStateFlow(evaluate(emptyList()))
    val ledger = ConsumedFoodLedger(
        initialEntries = initialFoods,
        store = ledgerStore,
        onChanged = { foods -> _snapshot.value = evaluate(foods) },
    )

    init {
        _snapshot.value = evaluate(ledger.entries.value)
    }

    val snapshot: StateFlow<RewardsSnapshot> = _snapshot.asStateFlow()
    val confirmedFoods: StateFlow<List<ConfirmedConsumedFood>> = ledger.entries

    fun recordConfirmedFood(food: ConfirmedConsumedFood) {
        ledger.recordConfirmedFood(food)
    }

    /** Explicit bridge for a future confirm-and-add-to-day flow. */
    fun recordGroundedFood(
        entryId: String,
        localDay: Int,
        mealId: String,
        grounded: GroundedNutritionResult,
        attributes: RewardFoodAttributes,
    ) {
        recordConfirmedFood(
            ConfirmedConsumedFood.fromGroundedResult(
                entryId = entryId,
                localDay = localDay,
                mealId = mealId,
                grounded = grounded,
                attributes = attributes,
            ),
        )
    }

    fun removeConfirmedFood(entryId: String): Boolean = ledger.removeConfirmedFood(entryId)

    fun replaceConfirmedFoods(foods: List<ConfirmedConsumedFood>) {
        ledger.replaceConfirmedFoods(foods)
    }

    private fun evaluate(foods: List<ConfirmedConsumedFood>): RewardsSnapshot = RewardsEvaluator.evaluate(
        RewardsInput(
            confirmedFoods = foods,
            asOfDay = localDay(),
        ),
    )
}
