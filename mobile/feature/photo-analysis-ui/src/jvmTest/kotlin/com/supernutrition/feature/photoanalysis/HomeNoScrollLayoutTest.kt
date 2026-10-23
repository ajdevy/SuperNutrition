package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeNoScrollLayoutTest {
    @Test
    fun `native phone shows the whole Home first frame without scrolling`() = assertFittingHome(
        viewport = Size(360f, 780f),
        safeTop = 68f,
        safeBottom = 48f,
    )

    @Test
    fun `tall phone uses spare space for meaningful artwork without scrolling`() = assertFittingHome(
        viewport = Size(412f, 915f),
        safeTop = 68f,
        safeBottom = 48f,
    )

    @Test
    fun `wide viewport fits complete Home at double text size`() = assertFittingHome(
        viewport = Size(800f, 1280f),
        safeTop = 68f,
        safeBottom = 48f,
        fontScale = 2f,
    )

    @Test
    fun `tall phone fits complete Home at enlarged text size`() = assertFittingHome(
        viewport = Size(412f, 915f),
        safeTop = 68f,
        safeBottom = 48f,
        fontScale = 1.3f,
    )

    @Test
    fun `short phone preserves readable overflow instead of claiming a fit`() = assertReadableOverflow(
        viewport = Size(320f, 568f),
        fontScale = 1f,
    )

    @Test
    fun `double text keeps its natural height when the phone cannot fit`() = assertReadableOverflow(
        viewport = Size(360f, 780f),
        fontScale = 2f,
    )

    private fun assertFittingHome(viewport: Size, safeTop: Float, safeBottom: Float, fontScale: Float = 1f) =
        runSkikoComposeUiTest(
            size = viewport,
            density = Density(1f, fontScale),
        ) {
            val model = layoutHomeModel()
            var addFoodRequests = 0
            setContent {
                PhotoAnalysisMainLayout(
                    layoutBottomBarState(),
                    layoutBottomBarActions(),
                    contentPadding = PaddingValues(top = safeTop.dp, bottom = safeBottom.dp),
                ) { HomeScreen(model, onAddFoodRequested = { addFoodRequests++ }) }
            }

            val content = onNodeWithTag(UiAutomationIds.HOME_SCREEN.value).fetchSemanticsNode().boundsInRoot
            val controlsTop = listOf(
                UiAutomationIds.CAMERA_ACTION,
                UiAutomationIds.HOME_TAB,
                UiAutomationIds.REWARDS_TAB,
            ).minOf { onNodeWithTag(it.value).fetchSemanticsNode().boundsInRoot.top }
            val nextStep = onNodeWithTag(UiAutomationIds.HOME_METRIC_FATS.value).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertEquals(safeTop, content.top, 1f)
            assertEquals(viewport.height - safeBottom, content.bottom, 1f)
            assertTrue(
                nextStep.bottom <= controlsTop - 8f,
                "Initial Home card overlaps navigation at $viewport: $nextStep, controls top=$controlsTop",
            )
            onAllNodes(hasScrollAction()).fetchSemanticsNodes().forEach { scroll ->
                val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
                assertEquals(0f, range.maxValue(), 1f, "Fitting Home still requires scrolling at $viewport")
                assertEquals(0f, range.value(), 1f, "The fitting check must use the initial frame")
            }

            requiredHomeText(model).forEach { label ->
                onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                    .assertInitialText(fontScale, content, controlsTop)
            }
            listOf(
                UiAutomationIds.HOME_ADD_FOOD,
                UiAutomationIds.HOME_METRIC_FIBER,
                UiAutomationIds.HOME_PROFILE,
            ).forEach {
                val button = onNodeWithTag(it.value).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(button.height >= 48f && button.width >= 48f, "Home action lost its touch frame: $it $button")
            }
            val mascot = onNodeWithContentDescription(model.mascotDescription).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val summary = onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).fetchSemanticsNode().boundsInRoot
            assertTrue(mascot.height >= 100f, "Fitting shrinks the mascot below meaningful artwork: $mascot")
            assertTrue(mascot.bottom < summary.top, "The mascot overlaps the new metrics panel")

            onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).performClick()
            assertEquals(1, addFoodRequests)
        }

    private fun SemanticsNodeInteraction.assertInitialText(fontScale: Float, content: Rect, controlsTop: Float) {
        val bounds = fetchSemanticsNode().boundsInRoot
        val layout = assertReadableHomeText(fontScale)
        val label = layout.layoutInput.text
        assertTrue(
            bounds.top >= content.top && bounds.bottom <= controlsTop - 8f,
            "Unsafe text: $label $bounds",
        )
        assertTrue(bounds.height >= layout.size.height - 1f, "Initial frame clips text: $label $bounds")
    }

    private fun assertReadableOverflow(viewport: Size, fontScale: Float) = runSkikoComposeUiTest(
        size = viewport,
        density = Density(1f, fontScale),
    ) {
        val model = layoutHomeModel()
        var addFoodRequests = 0
        setContent {
            PhotoAnalysisMainLayout(
                layoutBottomBarState(),
                layoutBottomBarActions(),
                contentPadding = PaddingValues(top = 68.dp, bottom = 48.dp),
            ) { HomeScreen(model, onAddFoodRequested = { addFoodRequests++ }) }
        }
        val scroll = onNode(hasScrollAction())
        val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.maxValue() > 0f, "A no-fit Home was squeezed into a false pass at $viewport/$fontScale")
        assertEquals(0f, range.value(), 1f, "Readable overflow must start at the original top")
        requiredHomeText(model).forEach { label ->
            onNodeWithText(label, useUnmergedTree = true).assertReadableHomeText(fontScale)
        }
        val careLayouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(model.careQuestion, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(careLayouts) }
        assertEquals(14.sp, careLayouts.single().layoutInput.style.fontSize, "No-fit Home reduces its text size")

        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        val controlsTop = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot.top
        val nextStep = onNodeWithTag(UiAutomationIds.HOME_METRIC_FATS.value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(nextStep.bottom <= controlsTop - 8f, "Readable overflow loses the final card: $nextStep")
        // The final current metric remains reachable; the old next-step action no longer exists.
        assertTrue(nextStep.height >= 48f)
    }

    private fun requiredHomeText(model: HomeScreenModel) = listOf(
        model.careQuestion,
        model.rhythmTitle,
        model.rhythmSubtitle,
        model.fiber.label,
        model.fiber.value,
        model.colors.label,
        model.colors.value,
        model.protein.label,
        model.protein.value,
        model.fats.label,
        model.fats.value,
    ).distinct()
}
