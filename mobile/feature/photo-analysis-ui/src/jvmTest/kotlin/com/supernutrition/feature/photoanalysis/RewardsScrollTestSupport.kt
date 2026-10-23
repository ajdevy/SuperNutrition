package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import kotlin.test.assertEquals

/** Scrolls the regular Rewards canvas to its actual end, beyond the floating controls. */
internal fun SemanticsNodeInteraction.performRewardsScrollToEnd(): SemanticsNodeInteraction = apply {
    val viewport = if (fetchSemanticsNode().config.contains(SemanticsProperties.VerticalScrollAxisRange)) {
        this
    } else {
        onChildren().filterToOne(hasScrollAction())
    }
    val before = viewport.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
    viewport.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
        scrollBy(0f, before.maxValue() - before.value())
    }
    val after = viewport.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
    assertEquals(after.maxValue(), after.value(), "Rewards did not reach the end of its canvas")
}

/**
 * Reveal through the actual two-axis controls. The generic test driver clips its target to the
 * horizontal viewport before scrolling its vertical ancestor, losing an off-screen text rectangle.
 * Read raw layout coordinates for the vertical step, retaining all rendered-text assertions.
 */
internal fun SemanticsNodeInteraction.revealRewardText(
    target: SemanticsNodeInteraction,
    navigationClearance: androidx.compose.ui.unit.Dp = MainBottomBarCanvasHeight,
): SemanticsNodeInteraction {
    val viewport = if (fetchSemanticsNode().config.contains(SemanticsProperties.VerticalScrollAxisRange)) {
        this
    } else {
        onChildren().filterToOne(hasScrollAction())
    }
    repeat(2) {
        target.performScrollTo()
        val targetNode = target.fetchSemanticsNode()
        val coordinates = targetNode.layoutInfo.coordinates
        val origin = coordinates.positionInRoot()
        val frame = viewport.fetchSemanticsNode().boundsInRoot
        val clearance = with(targetNode.layoutInfo.density) { navigationClearance.toPx() }
        val availableBottom = frame.bottom - clearance
        val delta = when {
            origin.y < frame.top -> origin.y - frame.top
            origin.y + coordinates.size.height > availableBottom -> origin.y + coordinates.size.height - availableBottom
            else -> 0f
        }
        if (delta != 0f) viewport.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, delta) }
    }
    return target
}
