package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The two numeric guards that reject a value for either of two reasons.
 *
 * `require(x.isFinite() && x > 0.0)` fails in two distinct ways, and the suite
 * only ever drove one of them. A guard tested through a single failure mode can
 * lose the other half of its condition without any test noticing — and the half
 * left untested here is the non-finite one, which is precisely how a NaN
 * produced upstream would reach a nutrient total.
 */
class GroundedNutritionCatalogNumericGuardTest {

    private fun rejects(block: () -> Unit) = assertFailsWith<IllegalArgumentException> { block() }

    @Test
    fun aServingRejectsNonFiniteAmountsSeparatelyFromNonPositiveOnes() {
        rejects { NutritionServing(amount = Double.NaN, unit = "g", grams = 100.0) }
        rejects { NutritionServing(amount = Double.POSITIVE_INFINITY, unit = "g", grams = 100.0) }
        rejects { NutritionServing(amount = 0.0, unit = "g", grams = 100.0) }
        rejects { NutritionServing(amount = -1.0, unit = "g", grams = 100.0) }

        NutritionServing(amount = 1.0, unit = "g", grams = 100.0)
    }

    @Test
    fun aServingRejectsNonFiniteGramsSeparatelyFromNonPositiveOnes() {
        rejects { NutritionServing(amount = 1.0, unit = "g", grams = Double.NaN) }
        rejects { NutritionServing(amount = 1.0, unit = "g", grams = Double.NEGATIVE_INFINITY) }
        rejects { NutritionServing(amount = 1.0, unit = "g", grams = 0.0) }
        rejects { NutritionServing(amount = 1.0, unit = "g", grams = -0.5) }
    }

    @Test
    fun aNutrientProfileRejectsNonFiniteValuesSeparatelyFromNegativeOnes() {
        // Non-finite and negative are different defects: a NaN comes from a bad
        // division upstream, a negative from a bad transcription. Both must be
        // refused, and each has its own branch.
        rejects { profile(energyKcal = Double.NaN) }
        rejects { profile(energyKcal = Double.POSITIVE_INFINITY) }
        rejects { profile(energyKcal = -1.0) }

        rejects { profile(proteinGrams = Double.NaN) }
        rejects { profile(proteinGrams = -0.1) }

        profile()
    }

    /**
     * Varies one nutrient at a time; the rest stay at a plausible chicken-breast
     * profile so a rejection can only come from the field under test.
     */
    private fun profile(energyKcal: Double = 165.0, proteinGrams: Double = 31.0) = CatalogNutrientProfile(
        energyKcal = energyKcal,
        proteinGrams = proteinGrams,
        carbohydrateGrams = 0.0,
        fatGrams = 3.6,
        fiberGrams = 0.0,
        sodiumMilligrams = 74.0,
    )
}
