package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeCarePromptLayoutTest {
    @Test
    fun `care button icons and labels stay centered at regular and larger text sizes`() {
        listOf(Size(375f, 812f) to 1f, Size(320f, 568f) to 1.5f).forEach { (viewport, fontScale) ->
            runSkikoComposeUiTest(size = viewport, density = Density(1f, fontScale)) {
                setContent {
                    PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                        HomeScreen(layoutHomeModel())
                    }
                }
                listOf(
                    UiAutomationIds.HOME_ADD_FOOD to "Add a meal",
                    UiAutomationIds.HOME_VIEW_GOALS to "View goals",
                ).forEach { (id, label) ->
                    val button = onNodeWithTag(id.value).assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    val textNode = onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                    val text = textNode.fetchSemanticsNode().boundsInRoot
                    val layouts = mutableListOf<TextLayoutResult>()
                    textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    if (fontScale > 1f && label == "Add a meal") {
                        assertTrue(layout.lineCount > 1, "The larger-text case must exercise label wrapping")
                    }
                    assertCenteredCareButton(label, button, text, layout)
                }
            }
        }
    }

    private fun assertCenteredCareButton(label: String, button: Rect, text: Rect, layout: TextLayoutResult) {
        assertTrue(!layout.didOverflowHeight, "$label is clipped vertically")
        // The existing icon badge is 20dp wide, followed by its 4dp label gap.
        val groupLeft = text.left - 24f
        val groupRight = text.right
        val groupCenter = (groupLeft + groupRight) / 2f
        assertTrue(
            abs(groupCenter - button.center.x) <= 1f,
            "$label has unequal side space: group=[$groupLeft, $groupRight], button=$button",
        )
        repeat(layout.lineCount) { line ->
            val left = layout.getLineLeft(line)
            val right = layout.getLineRight(line)
            val lineWidth = right - left
            assertTrue(lineWidth <= text.width + 1f, "$label line $line is clipped horizontally")
            if (layout.lineCount == 1) {
                assertTrue(text.width <= lineWidth + 1f, "$label reserves empty space beside its text")
            }
            val lineCenter = (left + right) / 2f
            assertTrue(
                abs(lineCenter - layout.multiParagraph.width / 2f) <= 1f,
                "$label line $line is not centered",
            )
        }
    }
}
