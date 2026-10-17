package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisGridCellId
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Shared components also need to refresh correctly when embedded directly by a host. */
@OptIn(ExperimentalTestApi::class)
class ComponentRefreshTest {
    @Test
    fun `host header refresh preserves unchanged rhythm metrics`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val header = mutableStateOf("Before refresh")
        val model = mutableStateOf(layoutHomeModel())
        val compact = mutableStateOf(false)
        setContent {
            Column {
                Text(header.value)
                HomeRhythmCard(model.value, compact.value)
            }
        }
        onNodeWithText("12 / 25 g").assertIsDisplayed()
        runOnIdle { header.value = "After refresh" }
        onNodeWithText("After refresh").assertIsDisplayed()
        onNodeWithText("12 / 25 g").assertIsDisplayed()
        onNodeWithText("1.2 / 2 L").assertIsDisplayed()
        runOnIdle { compact.value = true }
        onNodeWithText("72 / 90 g").assertIsDisplayed()
    }

    @Test
    fun `rhythm and reward summary refresh independently of their screen containers`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val home = mutableStateOf(layoutHomeModel())
        val compact = mutableStateOf(false)
        val rewards = mutableStateOf(layoutRewardsModel().copy(earnedSummary = "1 of 7", earnedSummaryLabel = "Earned"))
        setContent {
            Column {
                HomeRhythmCard(home.value, compact.value)
                RewardsHeader(rewards.value)
            }
        }
        onNodeWithText("1 of 7").assertIsDisplayed()
        runOnIdle { compact.value = true }
        onNodeWithText("12 / 25 g").assertIsDisplayed()
        runOnIdle {
            home.value = home.value.copy(rhythmSubtitle = "Updated day", fiber = HomeMetricModel("Fiber", "25 g"))
            rewards.value = rewards.value.copy(earnedSummary = "2 of 7", earnedSummaryLabel = "Two rewards earned")
        }
        onNodeWithText("Updated day").assertIsDisplayed()
        onNodeWithText("25 g").assertIsDisplayed()
        onNodeWithText("2 of 7").assertIsDisplayed()
        onNodeWithText("Two rewards earned").assertIsDisplayed()
    }

    @Test
    fun `review action labels grow equally and dispatch replacement callbacks`() = runSkikoComposeUiTest(
        size = Size(320f, 200f),
        density = Density(1f),
    ) {
        val retake = mutableStateOf(ReviewButtonSpec("Retake", UiAutomationIds.CAPTURE_SCAN_AGAIN))
        val add = mutableStateOf(ReviewButtonSpec("Add", UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL, filled = true))
        val clicked = mutableListOf<String>()
        val onRetake = mutableStateOf<() -> Unit>({ clicked.add("old retake") })
        val onAdd = mutableStateOf<() -> Unit>({ clicked.add("old add") })
        setContent { ReviewActions(retake.value, add.value, onRetake.value, onAdd.value) }
        onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).performClick()
        runOnIdle {
            retake.value = retake.value.copy(label = "Take another photograph")
            add.value = add.value.copy(label = "Save this meal to your day", enabled = false)
        }
        val left = onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).fetchSemanticsNode().boundsInRoot
        val right = onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).fetchSemanticsNode().boundsInRoot
        assertEquals(left.height, right.height)
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).assertIsNotEnabled()
        runOnIdle { onRetake.value = { clicked.add("new retake") } }
        onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).performClick()
        runOnIdle {
            onAdd.value = { clicked.add("new add") }
            add.value = add.value.copy(enabled = true)
        }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).performClick()
        assertEquals(listOf("old retake", "new retake", "new add"), clicked)
    }

    @Test
    fun `grid redraws a changed prompt and current region labels over the same photo`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val review = mutableStateOf<PhotoAnalysisReviewModel?>(null)
        val selected = mutableStateOf(-1)
        val surface = mutableStateOf(PhotoAnalysisCaptureSurface(PhotoAnalysisPresenterCopyFixtures.localized()))
        val clicked = mutableListOf<Int>()
        val onSelect = mutableStateOf<(Int) -> Unit>({
            selected.value = it
            clicked.add(it)
        })
        setContent {
            Column {
                Box(Modifier.height(240.dp).background(Color.Black)) {
                    PhotoAnalysisGridOverlay(review.value, selected.value, surface.value)
                }
                PhotoAnalysisGridHints(selected.value, onSelect.value, surface.value)
            }
        }
        val before = captureToImage().toPixelMap()[180, 120]
        onNodeWithTag(photoAnalysisGridCellId(4).value).performClick()
        assertNotEquals(before, captureToImage().toPixelMap()[180, 120])
        runOnIdle {
            val component = cameraComponentFixture("region", "Region food").copy(
                region = PhotoAnalysisVisibleRegionModel(0.1, 0.1, 0.2, 0.2),
            )
            review.value = PhotoAnalysisReviewModel(emptyList(), listOf(component))
            surface.value = surface.value.copy(photoTextContrast = PhotoAnalysisPhotoTextContrast.DARK_TEXT)
        }
        onNodeWithText("Region food").assertIsDisplayed()
        runOnIdle {
            onSelect.value = {
                selected.value = it
                clicked.add(it + 10)
            }
        }
        onNodeWithTag(photoAnalysisGridCellId(8).value).performClick()
        assertEquals(listOf(4, 18), clicked)
        runOnIdle { review.value = requireNotNull(review.value).copy(visibleComponents = emptyList()) }
        onNodeWithText("Region food").assertDoesNotExist()
    }

    @Test
    fun `native camera surface follows preview and callback replacements`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val model = mutableStateOf(
            PhotoAnalysisPresenterModel("Ready", PhotoAnalysisPresenterState.CAPTURE, emptyList()),
        )
        val surface = mutableStateOf(PhotoAnalysisCaptureSurface(copy, liveCamera = { Text("First preview") }))
        val modifier = mutableStateOf<Modifier>(Modifier)
        val events = mutableListOf<String>()
        val onAction = mutableStateOf<(PhotoAnalysisPresenterAction) -> Unit>({ events.add("old action") })
        setContent { PhotoAnalysisCameraScreen(model.value, onAction.value, surface.value, modifier.value) }
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).performClick()
        runOnIdle { onAction.value = { events.add("new action") } }
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).performClick()
        runOnIdle { surface.value = surface.value.copy(liveCamera = { Text("Second preview") }) }
        onNodeWithText("Second preview").assertIsDisplayed()
        runOnIdle { modifier.value = Modifier.requiredSize(320.dp, 568.dp) }
        onNodeWithTag(UiAutomationIds.CAPTURE_IMPORT.value).assertIsDisplayed()
        runOnIdle {
            model.value = model.value.copy(
                state = PhotoAnalysisPresenterState.PREPARING,
                statusLabel = "Working",
            )
        }
        onNodeWithText("Working").assertIsDisplayed()
        runOnIdle { model.value = model.value.copy(statusLabel = "Still working") }
        onNodeWithText("Still working").assertIsDisplayed()
        assertEquals(listOf("old action", "new action"), events)
    }

    @Test
    fun `photo palette replaces evidence and callbacks without keeping old selectable foods`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val review = mutableStateOf<PhotoAnalysisReviewModel?>(null)
        val choices = mutableStateOf(MealReviewChoices())
        val surface = mutableStateOf(PhotoAnalysisCaptureSurface(copy))
        val added = mutableListOf<String>()
        val onAdd = mutableStateOf<() -> Unit>({ added.add("old add") })
        val onChoices = mutableStateOf<(MealReviewChoices) -> Unit>({ choices.value = it })
        setContent {
            PhotoAnalysisFoodPalette(
                review.value,
                surface.value,
                choices.value,
                onChoices.value,
                onAdd.value,
            )
        }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_ADD.value).performClick()
        runOnIdle {
            review.value = PhotoAnalysisReviewModel(
                emptyList(),
                listOf(cameraComponentFixture("banana", "Banana")),
            )
        }
        onNodeWithTag(photoAnalysisVisibleComponentId("banana").value).performClick().assertIsSelected()
        runOnIdle {
            review.value = requireNotNull(
                review.value,
            ).copy(visibleComponents = listOf(cameraComponentFixture("unknown", "Unfamiliar food")))
            surface.value = surface.value.copy(
                presenterCopy = copy.copy(evidence = copy.evidence.copy(selected = "Picked")),
            )
        }
        onNodeWithTag(photoAnalysisVisibleComponentId("banana").value).assertDoesNotExist()
        runOnIdle {
            onChoices.value = {
                choices.value = it
                added.add(
                    "new selection",
                )
            }
            onAdd.value = { added.add("new add") }
        }
        onNodeWithTag(photoAnalysisVisibleComponentId("unknown").value).performClick().assertIsSelected()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_ADD.value).performClick()
        assertTrue("unknown" in choices.value.componentIds)
        assertEquals(listOf("old add", "new selection", "new add"), added)
    }
}
