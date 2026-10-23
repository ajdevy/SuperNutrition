package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PhotoAnalysisCameraEdgeCasesTest {
    @Test
    fun `pointer on visible broccoli selects only its actual photo component`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { PhotoAnalysisCaptureScreen(cameraReviewFixture(), {}, cameraSurface()) }
        onNodeWithTag(photoAnalysisVisibleComponentId("photo-broccoli").value)
            .performTouchInput { click(center) }
        onNodeWithTag(photoAnalysisVisibleComponentId("photo-broccoli").value).assertIsSelected()
        onNodeWithTag(photoAnalysisVisibleComponentId("photo-salmon").value).assertIsNotSelected()
        onNodeWithTag(UiAutomationIds.photoAnalysisPantryChoice("carrot").value).assertDoesNotExist()
    }

    @Test
    fun `dragging from a visible sticker scrolls without selecting it`() = runSkikoComposeUiTest(
        size = Size(375f, 600f),
        density = Density(1f),
    ) {
        val original = cameraReviewFixture()
        val review = requireNotNull(original.review)
        val extraFoods = listOf(
            "Quinoa",
            "Carrot",
            "Avocado",
            "Salmon",
            "Broccoli",
            "Salt",
            "Edamame",
            "Tahini",
            "Cucumber",
        )
            .mapIndexed { index, label -> cameraComponentFixture("fixture-extra-$index", label) }
        setContent {
            PhotoAnalysisCaptureScreen(
                original.copy(
                    review = review.copy(
                        visibleComponents = review.visibleComponents + extraFoods,
                    ),
                ),
                {},
                cameraSurface(),
            )
        }
        val food = onNodeWithTag(photoAnalysisVisibleComponentId("photo-broccoli").value)
        food.performScrollTo()
        val scroll = onNode(hasScrollAction())
        val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        val before = range.value()
        val origin = scroll.fetchSemanticsNode().boundsInRoot.topLeft
        val start = food.fetchSemanticsNode().boundsInRoot.center - origin
        scroll.performTouchInput { swipe(start, start - Offset(0f, 80f), 300) }
        assertTrue(range.value() > before, "A sticker drag must reach the review scroll container")
        food.assertIsNotSelected()
    }

    @Test
    fun `backgrounding preserves explicit review selections`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val original = cameraReviewFixture()
        val model = mutableStateOf(original)
        setContent { PhotoAnalysisCaptureScreen(model.value, {}, cameraSurface()) }
        val dishId = UiAutomationIds.photoAnalysisDishChoice(1).value
        val foodId = photoAnalysisVisibleComponentId("photo-broccoli").value
        onNodeWithTag(dishId).performClick()
        onNodeWithTag(foodId).performClick()
        runOnIdle { model.value = original.copy(state = PhotoAnalysisPresenterState.BACKGROUNDED, review = null) }
        onNodeWithTag(dishId).assertDoesNotExist()
        runOnIdle { model.value = original }
        onNodeWithTag(dishId).assertIsSelected()
        onNodeWithTag(foodId).assertIsSelected()
    }

    @Test
    fun `saveable restoration preserves explicit review selections`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        var registry = SaveableStateRegistry(null) { true }
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                PhotoAnalysisCaptureScreen(cameraReviewFixture(), {}, cameraSurface())
            }
        }
        setContent(content)
        val dishId = UiAutomationIds.photoAnalysisDishChoice(1).value
        val foodId = photoAnalysisVisibleComponentId("photo-broccoli").value
        onNodeWithTag(dishId).performClick()
        onNodeWithTag(foodId).performClick()
        val saved = runOnIdle { registry.performSave() }
        setContent { }
        registry = SaveableStateRegistry(saved) { true }
        setContent(content)
        onNodeWithTag(dishId).assertIsSelected()
        onNodeWithTag(foodId).assertIsSelected()
    }

    @Test
    fun `all unfamiliar evidence remains reachable on a regular phone`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val original = cameraReviewFixture()
        val review = requireNotNull(original.review)
        val model = original.copy(
            review = review.copy(
                visibleComponents = review.visibleComponents + (1..20).map {
                    cameraComponentFixture("extra-$it", "Unfamiliar food $it")
                },
            ),
        )
        setContent { PhotoAnalysisCaptureScreen(model, {}, cameraSurface()) }
        onNodeWithTag(photoAnalysisVisibleComponentId("extra-20").value)
            .performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).assertIsDisplayed()
    }

    @Test
    fun `dish labels grow without clipping with larger text`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f, fontScale = 1.5f),
    ) {
        val original = cameraReviewFixture()
        val review = requireNotNull(original.review)
        val label = "A homemade salmon and leafy green grain bowl"
        val model = original.copy(
            review = review.copy(mainDishProposals = listOf(review.mainDishProposals.first().copy(label = label))),
        )
        setContent { PhotoAnalysisCaptureScreen(model, {}, cameraSurface()) }
        onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(1).value).performClick()
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertFalse(layouts.single().didOverflowHeight, "The dish label overflows its measured card")
    }

    @Test
    fun `recovery actions remain reachable on a short viewport with larger text`() = runSkikoComposeUiTest(
        size = Size(320f, 240f),
        density = Density(1f, fontScale = 1.5f),
    ) {
        val model = PhotoAnalysisPresenterModel(
            "Camera permission is needed to take a picture of your meal",
            PhotoAnalysisPresenterState.RECOVERY,
            listOf(
                PhotoAnalysisPresenterAction.ScanAgain,
                PhotoAnalysisPresenterAction.ImportPhoto,
                PhotoAnalysisPresenterAction.OpenSettings,
            ),
        )
        setContent { PhotoAnalysisCaptureScreen(model, {}, cameraSurface()) }
        onNodeWithTag(UiAutomationIds.CAPTURE_OPEN_SETTINGS.value).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `camera close keeps an adequate touch target around its artwork`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisCaptureScreen(
                PhotoAnalysisPresenterModel("Ready", PhotoAnalysisPresenterState.CAPTURE, emptyList()),
                {},
                cameraSurface(),
            )
        }
        onNodeWithTag(UiAutomationIds.CAPTURE_CLOSE.value)
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    private fun cameraSurface() = PhotoAnalysisCaptureSurface(
        presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
    )
}
