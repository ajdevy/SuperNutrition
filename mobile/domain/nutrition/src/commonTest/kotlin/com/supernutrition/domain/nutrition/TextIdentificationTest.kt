package com.supernutrition.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextIdentificationTest {
    private val floor = CatalogSearch(SeedCatalog.records)

    @Test
    fun `an unavailable engine still resolves the meal through the floor`() {
        // The property the whole design turns on. UJ-4 puts Noah offline on a
        // low-end device, so the journey that most needs typed identification is
        // the one least able to run a model. If an unavailable engine ends the
        // journey, the feature is useless exactly where it was meant to help.
        val identification = TextIdentification(
            floor = floor,
            engine = { TextEngineOutcome.Unavailable(TextEngineState.UNSUPPORTED) },
        )

        val outcome = identification.identify("boiled egg")

        assertEquals(
            TextIdentificationOutcome.Floor(listOf("food:egg-boiled")),
            outcome,
        )
    }

    @Test
    fun `a ready engine returns one item group per detected item`() {
        val identification = TextIdentification(
            floor = floor,
            engine = {
                TextEngineOutcome.Segmented(
                    listOf(
                        DetectedItem("food:egg-boiled", TextSpan(0, 15)),
                        DetectedItem("food:bread-rye", TextSpan(20, 40)),
                    ),
                )
            },
        )

        val outcome = identification.identify("two boiled eggs and a slice of rye toast")

        assertEquals(
            TextIdentificationOutcome.Items(
                listOf(
                    DetectedItem("food:egg-boiled", TextSpan(0, 15)),
                    DetectedItem("food:bread-rye", TextSpan(20, 40)),
                ),
            ),
            outcome,
        )
    }

    @Test
    fun `an abstaining engine falls back to the floor rather than dead-ending`() {
        val identification = TextIdentification(
            floor = floor,
            engine = { TextEngineOutcome.Abstained("description-unsegmentable") },
        )

        val outcome = identification.identify("rye bread")

        assertEquals(TextIdentificationOutcome.Floor(listOf("food:bread-rye")), outcome)
    }

    @Test
    fun `a span that does not index the description is rejected, not shown`() {
        // A span pointing at the wrong words is worse than no span: the user is
        // shown provenance that lies about which text produced the item.
        val identification = TextIdentification(
            floor = floor,
            engine = {
                TextEngineOutcome.Segmented(
                    listOf(DetectedItem("food:egg-boiled", TextSpan(0, 999))),
                )
            },
        )

        val outcome = identification.identify("boiled egg")

        assertTrue(
            outcome is TextIdentificationOutcome.Floor,
            "an out-of-range span must degrade to the floor, not surface a false span",
        )
    }

    @Test
    fun `an empty description never reaches the engine`() {
        var engineCalled = false
        val identification = TextIdentification(
            floor = floor,
            engine = {
                engineCalled = true
                TextEngineOutcome.Abstained("unreachable")
            },
        )

        val outcome = identification.identify("   ")

        assertEquals(TextIdentificationOutcome.Empty, outcome)
        assertTrue(!engineCalled, "an empty description is not worth a model invocation")
    }
}
