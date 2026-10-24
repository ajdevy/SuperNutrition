package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.domain.nutrition.GroundedNutritionResult
import com.supernutrition.domain.nutrition.RecordedMealEvidence
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
    private var recordedMeals: List<RecordedMealEvidence> = emptyList()
    private val supersededMeals = mutableSetOf<Pair<String, Int>>()
    private val _confirmedFoods = MutableStateFlow<List<ConfirmedConsumedFood>>(emptyList())
    private val _snapshot = MutableStateFlow(evaluate(emptyList()))
    val ledger = ConsumedFoodLedger(
        initialEntries = initialFoods,
        store = ledgerStore,
        onChanged = ::publish,
    )

    init {
        publish(ledger.entries.value)
    }

    val snapshot: StateFlow<RewardsSnapshot> = _snapshot.asStateFlow()
    val confirmedFoods: StateFlow<List<ConfirmedConsumedFood>> = _confirmedFoods.asStateFlow()

    /** Recording dates count toward habits; names never imply nutrient evidence. */
    fun updateRecordedMeals(meals: List<SavedMeal>) {
        refreshDiaryProjection(meals, removed = null)
    }

    /** Supersession lasts for this session even after an authored correction is deleted. */
    internal fun refreshDiaryProjection(meals: List<SavedMeal>, removed: SavedMeal?) {
        supersededMeals += meals.map { it.id to it.localDay }
        if (removed != null) supersededMeals += removed.id to removed.localDay
        recordedMeals = meals.map { RecordedMealEvidence(it.id, it.localDay) }.distinct()
        publish(ledger.entries.value)
    }

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

    /** One projection supplies every consumer; immutable source records remain in the ledger. */
    private fun publish(foods: List<ConfirmedConsumedFood>) {
        val visible = foods.filterNot { (it.mealId to it.localDay) in supersededMeals }
        _snapshot.value = evaluate(visible)
        _confirmedFoods.value = visible
    }

    private fun evaluate(foods: List<ConfirmedConsumedFood>): RewardsSnapshot = RewardsEvaluator.evaluate(
        RewardsInput(
            confirmedFoods = foods,
            asOfDay = localDay(),
            recordedMeals = recordedMeals,
        ),
    )
}
