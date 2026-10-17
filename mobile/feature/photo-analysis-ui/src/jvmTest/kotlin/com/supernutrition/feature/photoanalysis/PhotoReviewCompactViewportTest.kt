package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PhotoReviewCompactViewportTest {
    @Test
    fun `fractional density preserves the minimum action viewport in pixels`() = runSkikoComposeUiTest(
        size = Size(430f, 763f),
        density = Density(1.34375f),
    ) {
        val height = mutableStateOf(568.dp)
        setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                Box(Modifier.fillMaxWidth().height(height.value)) {
                    PhotoAnalysisCaptureScreen(
                        cameraReviewFixture(),
                        {},
                        PhotoAnalysisCaptureSurface(
                            presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                            manualIngredientInput = "apple",
                        ),
                    )
                }
            }
        }
        val field = onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value)
        field.performClick().assertIsFocused()
        runOnIdle { height.value = (134f / 1.34375f).dp }
        field.assertIsFocused().assertTextEquals("apple").assertIsDisplayed()
        listOf(UiAutomationIds.CAPTURE_SCAN_AGAIN, UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL).forEach { id ->
            val action = onNodeWithTag(id.value).performScrollTo().assertIsDisplayed()
            assertTrue(action.fetchSemanticsNode().boundsInRoot.height >= 65f, "$id loses one pixel of its target")
        }
    }

    @Test
    fun `tiny positive viewport keeps the focused note and actions reachable`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f),
    ) {
        val height = mutableStateOf(568.dp)
        setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                Box(Modifier.fillMaxWidth().height(height.value)) {
                    PhotoAnalysisCaptureScreen(
                        cameraReviewFixture(),
                        {},
                        PhotoAnalysisCaptureSurface(
                            presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                            manualIngredientInput = "apple",
                        ),
                    )
                }
            }
        }
        val field = onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value)
        field.performClick().assertIsFocused()
        runOnIdle { height.value = 54.dp }
        field.assertIsFocused().assertTextEquals("apple").assertIsDisplayed()
        listOf(UiAutomationIds.CAPTURE_SCAN_AGAIN, UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL).forEach { id ->
            val action = onNodeWithTag(id.value).performScrollTo().assertIsDisplayed()
            assertTrue(action.fetchSemanticsNode().boundsInRoot.height >= 48f, "$id loses its touch viewport")
        }
    }

    @Test
    fun `large text keyboard keeps the complete note below Close`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f, 2f),
    ) {
        val height = mutableStateOf(568.dp)
        setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                Box(Modifier.fillMaxWidth().height(height.value)) {
                    PhotoAnalysisCaptureScreen(
                        cameraReviewFixture(),
                        {},
                        PhotoAnalysisCaptureSurface(
                            presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                            manualIngredientInput = "apple",
                        ),
                    )
                }
            }
        }
        val field = onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value)
        field.performClick().assertIsFocused()
        runOnIdle { height.value = 206.dp }
        field.assertIsFocused().assertTextEquals("apple").assertIsDisplayed()
        val frame = checkNotNull(field.fetchSemanticsNode().layoutInfo.parentInfo)
        val visible = frame.coordinates.boundsInRoot()
        val close = onNodeWithTag(UiAutomationIds.CAPTURE_CLOSE.value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(visible.top >= close.bottom, "Close overlaps the note frame: $close / $visible")
        assertTrue(
            visible.bottom <= 206f && abs(visible.height - frame.height) <= 1f,
            "Note frame is partially clipped: height=${frame.height}, bounds=$visible",
        )
        listOf(UiAutomationIds.CAPTURE_SCAN_AGAIN, UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL).forEach { id ->
            val action = onNodeWithTag(id.value).performScrollTo().assertIsDisplayed()
            assertTrue(action.fetchSemanticsNode().boundsInRoot.height >= 48f, "$id loses its touch viewport")
        }
    }

    @Test
    fun `keyboard resize reveals the focused review footer`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f),
    ) {
        val height = mutableStateOf(568.dp)
        setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                Box(Modifier.fillMaxWidth().height(height.value)) {
                    PhotoAnalysisCaptureScreen(
                        cameraReviewFixture(),
                        {},
                        PhotoAnalysisCaptureSurface(
                            presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                            manualIngredientInput = "apple",
                        ),
                    )
                }
            }
        }
        val field = onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value)
        field.performClick().assertIsFocused()
        listOf(206.dp, 568.dp, 206.dp).forEach { availableHeight ->
            runOnIdle { height.value = availableHeight }
            field.assertIsFocused().assertTextEquals("apple").assertIsDisplayed()
            val visible = field.fetchSemanticsNode().boundsInRoot
            val full = field.getUnclippedBoundsInRoot()
            assertTrue(
                abs((full.bottom - full.top).value - visible.height) <= 1f,
                "Note text is clipped: $full / $visible",
            )
            listOf(UiAutomationIds.CAPTURE_SCAN_AGAIN, UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL).forEach { id ->
                val action = onNodeWithTag(id.value).assertIsDisplayed()
                val bounds = action.fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.bottom <= availableHeight.value && bounds.height >= 48f, "$id is clipped: $bounds")
            }
        }
    }

    @Test
    fun `short viewport reveals the entire note frame`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f),
    ) {
        val height = mutableStateOf(568.dp)
        setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                Box(Modifier.fillMaxWidth().height(height.value)) {
                    PhotoAnalysisCaptureScreen(
                        cameraReviewFixture(),
                        {},
                        PhotoAnalysisCaptureSurface(
                            presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                            manualIngredientInput = "apple",
                        ),
                    )
                }
            }
        }
        val field = onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value)
        field.performClick().assertIsFocused()
        runOnIdle { height.value = 100.dp }
        field.assertIsFocused().assertTextEquals("apple").assertIsDisplayed()
        val frame = checkNotNull(field.fetchSemanticsNode().layoutInfo.parentInfo)
        val visible = frame.coordinates.boundsInRoot()
        assertTrue(frame.height >= 45, "Expected the note frame: height=${frame.height}, bounds=$visible")
        assertTrue(
            visible.top >= 0f && visible.bottom <= 100f && abs(visible.height - frame.height) <= 1f,
            "The note frame is clipped: height=${frame.height}, bounds=$visible",
        )
    }

    @Test
    fun `oversized footer reveals the focused note and keeps actions scroll reachable`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f, 2f),
    ) {
        val height = mutableStateOf(568.dp)
        setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                Box(Modifier.fillMaxWidth().height(height.value)) {
                    PhotoAnalysisCaptureScreen(
                        cameraReviewFixture(),
                        {},
                        PhotoAnalysisCaptureSurface(
                            presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                            manualIngredientInput = "apple",
                        ),
                    )
                }
            }
        }
        val field = onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value)
        field.performClick().assertIsFocused()
        runOnIdle { height.value = 100.dp }
        field.assertIsFocused().assertTextEquals("apple").assertIsDisplayed()
        listOf(UiAutomationIds.CAPTURE_SCAN_AGAIN, UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL).forEach { id ->
            val action = onNodeWithTag(id.value).performScrollTo().assertIsDisplayed()
            assertTrue(action.fetchSemanticsNode().boundsInRoot.height >= 48f, "$id loses its touch viewport")
        }
    }

    @Test
    fun `resizing a focused review preserves the manual field and text`() = runSkikoComposeUiTest(
        size = Size(360f, 780f),
        density = Density(1f),
    ) {
        val height = mutableStateOf(780.dp)
        val input = mutableStateOf("apple")
        setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                Box(Modifier.fillMaxWidth().height(height.value)) {
                    PhotoAnalysisCaptureScreen(
                        cameraReviewFixture(),
                        {},
                        PhotoAnalysisCaptureSurface(
                            presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                            manualIngredientInput = input.value,
                            onManualIngredientInputChanged = { input.value = it },
                        ),
                    )
                }
            }
        }
        val field = onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value)
        field.performClick().assertIsFocused()
        listOf(414.dp, 170.dp, 414.dp, 780.dp).forEach { availableHeight ->
            runOnIdle { height.value = availableHeight }
            field.assertIsFocused().assertTextEquals("apple")
        }
    }

    @Test
    fun `extremely short review keeps choices and actions reachable`() {
        listOf(Size(360f, 170f) to 1f, Size(320f, 200f) to 2f).forEach { (viewport, fontScale) ->
            runSkikoComposeUiTest(size = viewport, density = Density(1f, fontScale)) {
                setContent {
                    PhotoAnalysisCaptureScreen(
                        cameraReviewFixture(),
                        {},
                        PhotoAnalysisCaptureSurface(presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized()),
                    )
                }
                listOf(
                    UiAutomationIds.PHOTO_ANALYSIS_CUSTOM_MEAL,
                    UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT,
                    UiAutomationIds.CAPTURE_SCAN_AGAIN,
                    UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL,
                ).forEach { id -> onNodeWithTag(id.value).performScrollTo().assertIsDisplayed() }
                onNodeWithTag(UiAutomationIds.CAPTURE_CLOSE.value).assertIsDisplayed()
            }
        }
    }

    @Test
    fun `short review viewport keeps manual input and footer actions fully visible`() {
        listOf(1f, 2f).forEach { fontScale ->
            runSkikoComposeUiTest(size = Size(360f, 414f), density = Density(1f, fontScale)) {
                setContent {
                    PhotoAnalysisCaptureScreen(
                        cameraReviewFixture(),
                        {},
                        PhotoAnalysisCaptureSurface(
                            presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                            manualIngredientInput = "apple",
                        ),
                    )
                }
                onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value).performClick()
                listOf(
                    UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT,
                    UiAutomationIds.CAPTURE_SCAN_AGAIN,
                    UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL,
                ).forEach { id ->
                    val node = onNodeWithTag(id.value).assertIsDisplayed().assertIsEnabled()
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    val full = node.getUnclippedBoundsInRoot()
                    assertTrue(bounds.top >= 0f && bounds.bottom <= 414f, "$id is outside the usable viewport: $bounds")
                    assertTrue(
                        abs((full.bottom - full.top).value - bounds.height) <= 1f,
                        "$id is partially clipped: $full / $bounds",
                    )
                    if (id != UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT) {
                        assertTrue(bounds.height >= 48f, "$id is clipped: $bounds")
                    }
                }
                onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_CUSTOM_MEAL.value).performScrollTo().assertIsDisplayed()
                onNodeWithTag(UiAutomationIds.CAPTURE_CLOSE.value).assertIsDisplayed()
            }
        }
    }
}
