package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReviewBoundaryTest {
    @Test
    fun `capture actions remain disjoint in a short window with safe area padding`() = runSkikoComposeUiTest(
        size = Size(320f, 240f),
        density = Density(1f),
    ) {
        setContent {
            Box(
                Modifier.fillMaxSize().padding(vertical = 24.dp),
            ) {
                PhotoAnalysisCaptureScreen(
                    PhotoAnalysisPresenterModel("Ready", PhotoAnalysisPresenterState.CAPTURE, emptyList()),
                    {},
                    PhotoAnalysisCaptureSurface(presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized()),
                )
            }
        }
        val close = onNodeWithTag(UiAutomationIds.CAPTURE_CLOSE.value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val gallery = onNodeWithTag(UiAutomationIds.CAPTURE_IMPORT.value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val shutter = onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertFalse(close.overlaps(gallery), "Close overlaps gallery: $close and $gallery")
        assertFalse(close.overlaps(shutter), "Close overlaps shutter: $close and $shutter")
        assertFalse(gallery.overlaps(shutter), "Gallery overlaps shutter: $gallery and $shutter")
    }

    @Test
    fun `translated review footer grows for large text without clipping its labels`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f, fontScale = 1.5f),
    ) {
        val original = PhotoAnalysisPresenterCopyFixtures.localized()
        val labels = listOf("Сделать новое фото", "Добавить выбранные продукты")
        setContent {
            PhotoAnalysisCaptureScreen(
                cameraReviewFixture(),
                {},
                PhotoAnalysisCaptureSurface(
                    presenterCopy = original.copy(
                        review = original.review.copy(retake = labels[0], addToMeal = labels[1]),
                    ),
                ),
            )
        }
        labels.forEach { label ->
            val text = onNodeWithText(label, useUnmergedTree = true).performScrollTo()
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertFalse(
                layouts.single().didOverflowHeight,
                "Footer clips $label: size=${layouts.single().size}, lines=${layouts.single().lineCount}, " +
                    "bounds=${text.fetchSemanticsNode().boundsInRoot}",
            )
            assertFalse(layouts.single().didOverflowWidth, "Footer clips its translated label: $label")
        }
        onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `unselected review has a visible close that invokes only its host callback`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f),
    ) {
        val actions = mutableListOf<PhotoAnalysisPresenterAction>()
        var closeCount = 0
        setContent {
            PhotoAnalysisCaptureScreen(
                cameraReviewFixture(),
                actions::add,
                PhotoAnalysisCaptureSurface(
                    presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                    onCloseRequested = { closeCount++ },
                ),
            )
        }
        val close = onNodeWithTag(UiAutomationIds.CAPTURE_CLOSE.value).assertIsDisplayed()
        val closeBounds = close.fetchSemanticsNode().boundsInRoot
        val title = onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).fetchSemanticsNode().boundsInRoot
        assertTrue(closeBounds.right <= title.left || closeBounds.bottom <= title.top)
        close.performClick()
        assertEquals(1, closeCount)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `unknown photo food shows a checkmark only while selected`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f, fontScale = 1.5f),
    ) {
        val component = cameraComponentFixture("unknown-composite", "Homemade banana bread")
        val original = cameraReviewFixture()
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        setContent {
            PhotoAnalysisCaptureScreen(
                original.copy(review = requireNotNull(original.review).copy(visibleComponents = listOf(component))),
                {},
                PhotoAnalysisCaptureSurface(presenterCopy = copy),
            )
        }
        val tile = onNodeWithTag(photoAnalysisVisibleComponentId(component.componentId).value).performScrollTo()
        tile.assertIsNotSelected().performClick().assertIsSelected()
        onNodeWithContentDescription(copy.evidence.selected, useUnmergedTree = true).assertIsDisplayed()
        tile.performClick().assertIsNotSelected()
        onNodeWithContentDescription(copy.evidence.selected, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `earned rewards remain scrollable in a very short window`() = runSkikoComposeUiTest(
        size = Size(320f, 240f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(1), layoutBottomBarActions()) {
                RewardsScreen(layoutRewardsModel().copy(earnedSummary = "1 of 7", earnedSummaryLabel = "Earned"))
            }
        }
        val finalProgressId = UiAutomationIds.rewardsRowProgress(6).value
        val list = onNodeWithTag(UiAutomationIds.REWARDS_LIST.value)
        val viewport = list.fetchSemanticsNode().boundsInRoot
        assertTrue(viewport.height >= 48f, "Rewards has no usable scrolling viewport: $viewport")
        list.performScrollToNode(hasTestTag(finalProgressId))
        onNodeWithTag(finalProgressId).performScrollTo().assertIsDisplayed()
    }
}
