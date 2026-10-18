package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import kotlin.test.assertEquals

/** Scrolls the regular Rewards canvas to its actual end, beyond the floating controls. */
internal fun SemanticsNodeInteraction.performRewardsScrollToEnd(): SemanticsNodeInteraction = apply {
    val before = fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
    performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, before.maxValue() - before.value()) }
    val after = fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
    assertEquals(after.maxValue(), after.value(), "Rewards did not reach the end of its canvas")
}
