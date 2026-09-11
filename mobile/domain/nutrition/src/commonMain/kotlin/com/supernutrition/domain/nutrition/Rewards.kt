package com.supernutrition.domain.nutrition

import com.supernutrition.domain.inference.requireNonBlankValue

/** The four catalog-defined groups used by the daily fat card. */
enum class RewardFatGroup {
    NUTS_AND_SEEDS,
    OILS,
    AVOCADO_AND_OLIVES,
    FATTY_FISH,
}

/** The six catalog-defined groups used by the daily fiber card. */
enum class RewardFiberGroup {
    VEGETABLES,
    FRUIT,
    LEGUMES,
    WHOLE_GRAINS,
    NUTS_AND_SEEDS,
    BERRIES,
}

/** The four visible color groups used by the daily color card. */
enum class RewardFoodColor {
    RED,
    ORANGE,
    YELLOW,
    GREEN,
}

/**
 * Catalog-owned facts required by reward rules. These are classification facts,
 * not inferences made by the reward feature.
 */
data class RewardFoodAttributes(
    val hasProtein: Boolean,
    val fatGroup: RewardFatGroup? = null,
    val fiberGroup: RewardFiberGroup? = null,
    val color: RewardFoodColor? = null,
    val isFermented: Boolean = false,
    val isHerbOrSpice: Boolean = false,
)

/**
 * A food entry that is safe for reward evaluation because it came from a
 * confirmed, catalog-grounded nutrition result.
 *
 * The private constructor prevents a raw photo candidate or free-form label
 * from being passed directly to the reward evaluator.
 */
class ConfirmedConsumedFood private constructor(
    val entryId: String,
    val localDay: Int,
    val mealId: String,
    val grounded: GroundedNutritionResult,
    val attributes: RewardFoodAttributes,
) {
    val canonicalFoodId: String
        get() = grounded.record.canonicalItemId

    val displayName: String
        get() = grounded.record.displayName

    init {
        requireNonBlankValue(entryId, "Consumed food entry ID must not be blank")
        require(localDay >= 0) { "Consumed food local day must not be negative" }
        requireNonBlankValue(mealId, "Consumed food meal ID must not be blank")
    }

    companion object {
        fun fromGroundedResult(
            entryId: String,
            localDay: Int,
            mealId: String,
            grounded: GroundedNutritionResult,
            attributes: RewardFoodAttributes,
        ): ConfirmedConsumedFood = ConfirmedConsumedFood(
            entryId = entryId,
            localDay = localDay,
            mealId = mealId,
            grounded = grounded,
            attributes = attributes,
        )
    }
}

data class RewardsInput(val confirmedFoods: List<ConfirmedConsumedFood>, val asOfDay: Int) {
    init {
        require(asOfDay >= 0) { "Reward reference day must not be negative" }
    }
}

enum class RewardId {
    PROTEIN_ALL_DAY,
    FATS_PRESENT,
    FIBER_PRESENT,
    COLORS_OF_DAY,
    THIRTY_PRODUCTS,
    FERMENTED_THREE_DAYS,
    SEVEN_DAYS_RECORDED,
}

data class RewardProgress(val current: Int, val target: Int) {
    init {
        require(current >= 0) { "Reward progress cannot be negative" }
        require(target > 0) { "Reward target must be positive" }
    }

    val isComplete: Boolean
        get() = current >= target
}

data class RewardStatus(val id: RewardId, val progress: RewardProgress, val isEarned: Boolean = progress.isComplete)

data class RewardsSnapshot(val asOfDay: Int, val cards: List<RewardStatus>) {
    init {
        require(cards.map(RewardStatus::id).distinct().size == cards.size) {
            "Reward snapshot cannot contain duplicate cards"
        }
    }
}

/** Pure, deterministic evaluator for the reward card brief. */
object RewardsEvaluator {
    private const val WEEK_LENGTH_DAYS = 7
    private const val HERBS_AND_SPICES_WEEKLY_LIMIT = 5

    fun evaluate(input: RewardsInput): RewardsSnapshot {
        val today = input.confirmedFoods.filter { it.localDay == input.asOfDay }
        val week = input.confirmedFoods.filter { it.localDay in weekDays(input.asOfDay) }
        return RewardsSnapshot(
            asOfDay = input.asOfDay,
            cards = dailyCards(today) + weeklyCards(week),
        )
    }

    private fun dailyCards(today: List<ConfirmedConsumedFood>): List<RewardStatus> = listOf(
        proteinStatus(today),
        RewardStatus(
            id = RewardId.FATS_PRESENT,
            progress = RewardProgress(current = if (today.any { it.attributes.fatGroup != null }) 1 else 0, target = 1),
        ),
        RewardStatus(
            id = RewardId.FIBER_PRESENT,
            progress = RewardProgress(
                current = today.groupBy(ConfirmedConsumedFood::mealId)
                    .count { (_, meal) -> meal.any { it.attributes.fiberGroup != null } },
                target = 2,
            ),
        ),
        RewardStatus(
            id = RewardId.COLORS_OF_DAY,
            progress = RewardProgress(current = today.mapNotNull { it.attributes.color }.distinct().size, target = 3),
        ),
    )

    private fun weeklyCards(week: List<ConfirmedConsumedFood>): List<RewardStatus> = listOf(
        RewardStatus(
            id = RewardId.THIRTY_PRODUCTS,
            progress = RewardProgress(current = countWeeklyProducts(week), target = 30),
        ),
        RewardStatus(
            id = RewardId.FERMENTED_THREE_DAYS,
            progress = RewardProgress(
                current = week.filter { it.attributes.isFermented }
                    .map(ConfirmedConsumedFood::localDay)
                    .distinct()
                    .size,
                target = 3,
            ),
        ),
        RewardStatus(
            id = RewardId.SEVEN_DAYS_RECORDED,
            progress = RewardProgress(
                current = week.map(ConfirmedConsumedFood::localDay).distinct().size,
                target = WEEK_LENGTH_DAYS,
            ),
        ),
    )

    private fun proteinStatus(today: List<ConfirmedConsumedFood>): RewardStatus {
        val meals = today.groupBy(ConfirmedConsumedFood::mealId)
        val proteinMeals = proteinMealCount(meals)
        return RewardStatus(
            id = RewardId.PROTEIN_ALL_DAY,
            progress = RewardProgress(current = proteinMeals, target = meals.size.coerceAtLeast(2)),
            isEarned = meals.size >= 2 && proteinMeals == meals.size,
        )
    }

    private fun weekDays(asOfDay: Int): IntRange = (asOfDay - (WEEK_LENGTH_DAYS - 1)).coerceAtLeast(0)..asOfDay

    private fun proteinMealCount(mealsToday: Map<String, List<ConfirmedConsumedFood>>): Int =
        mealsToday.values.count { meal -> meal.any { it.attributes.hasProtein } }

    private fun countWeeklyProducts(week: List<ConfirmedConsumedFood>): Int {
        val uniqueFoods = week.distinctBy(ConfirmedConsumedFood::canonicalFoodId)
        val ordinaryFoods = uniqueFoods.count { !it.attributes.isHerbOrSpice }
        val herbAndSpiceFoods = uniqueFoods.count { it.attributes.isHerbOrSpice }
        return ordinaryFoods + herbAndSpiceFoods.coerceAtMost(HERBS_AND_SPICES_WEEKLY_LIMIT)
    }
}
