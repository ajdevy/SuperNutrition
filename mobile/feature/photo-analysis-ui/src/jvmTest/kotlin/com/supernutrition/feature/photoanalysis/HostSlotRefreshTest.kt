package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.RecoveryReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HostSlotRefreshTest {
    @Test
    fun `unrelated host header refresh preserves an embedded meal choice`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val header = mutableStateOf("First header")
        val cameraHost = mutableStateOf(true)
        val model = mutableStateOf(cameraReviewFixture())
        val surface = mutableStateOf(
            PhotoAnalysisCaptureSurface(
                PhotoAnalysisPresenterCopyFixtures.localized(),
                mealReviewSelection = MealReviewChoices(dishId = "dish-1", componentIds = setOf("photo-salmon")),
            ),
        )
        val action = mutableStateOf<(PhotoAnalysisPresenterAction) -> Unit>({})
        val modifier = mutableStateOf<Modifier>(Modifier.requiredSize(375.dp, 740.dp))
        setContent {
            Column {
                Text(header.value)
                if (cameraHost.value) {
                    PhotoAnalysisCameraScreen(
                        model.value,
                        action.value,
                        surface.value,
                        modifier.value,
                    )
                } else {
                    PhotoAnalysisMealReview(model.value, action.value, surface.value)
                }
            }
        }
        runOnIdle { header.value = "Updated header" }
        onNodeWithText("Updated header").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(1).value).assertIsDisplayed()
        runOnIdle { cameraHost.value = false }
        runOnIdle { header.value = "Updated embedded header" }
        onNodeWithText("Updated embedded header").assertIsDisplayed()
        onNodeWithTag(photoAnalysisVisibleComponentId("photo-salmon").value).assertIsSelected()
    }

    @Test
    fun `host header refresh preserves main shell and action layout without recreating callbacks`() =
        runSkikoComposeUiTest(
            size = Size(375f, 812f),
            density = Density(1f),
        ) {
            val header = mutableStateOf("First header")
            val state = mutableStateOf(layoutBottomBarState())
            val actions = mutableStateOf(layoutBottomBarActions())
            val padding = mutableStateOf(PaddingValues())
            val modifier = mutableStateOf<Modifier>(Modifier)
            val clicked = mutableListOf<String>()
            val retake = mutableStateOf(ReviewButtonSpec("Retake", UiAutomationIds.CAPTURE_SCAN_AGAIN))
            val add = mutableStateOf(ReviewButtonSpec("Add", UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL))
            val onRetake = mutableStateOf<() -> Unit>({ clicked.add("retake") })
            val onAdd = mutableStateOf<() -> Unit>({ clicked.add("add") })
            val content = mutableStateOf<@Composable () -> Unit>({ Text("Host content") })
            setContent {
                Column {
                    Text(header.value)
                    ReviewActions(retake.value, add.value, onRetake.value, onAdd.value)
                    PhotoAnalysisMainLayout(state.value, actions.value, modifier.value, padding.value, content.value)
                }
            }
            runOnIdle { header.value = "Updated header" }
            onNodeWithText("Updated header").assertIsDisplayed()
            onNodeWithText("Host content").assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).performClick()
            onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).performClick()
            onNodeWithTag(UiAutomationIds.HOME_TAB.value).assertIsSelected()
            assertEquals(listOf("retake", "add"), clicked)
        }

    @Test
    fun `care buttons and loading status update independently within a native host`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val home = mutableStateOf(layoutHomeModel())
        val width = mutableStateOf(375.dp)
        val loading = mutableStateOf(
            PhotoAnalysisPresenterModel("Working", PhotoAnalysisPresenterState.ANALYZING, emptyList()),
        )
        val events = mutableListOf<String>()
        val add = mutableStateOf<() -> Unit>({ events.add("old add") })
        val goals = mutableStateOf<() -> Unit>({ events.add("old goals") })
        setContent {
            Column(Modifier.width(width.value)) {
                HomeCarePrompt(home.value, add.value, goals.value)
                PhotoAnalysisLoadingOverlay(loading.value)
            }
        }
        onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).performClick()
        runOnIdle { goals.value = { events.add("new goals") } }
        onNodeWithTag(UiAutomationIds.HOME_VIEW_GOALS.value).performClick()
        runOnIdle { add.value = { events.add("new add") } }
        onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).performClick()
        runOnIdle { width.value = 320.dp }
        runOnIdle { home.value = home.value.copy(careQuestion = "Updated question", addFoodLabel = "Add lunch") }
        onNodeWithText("Updated question").assertIsDisplayed()
        onNodeWithText("Add lunch").assertIsDisplayed()
        onNodeWithText("Working").assertIsDisplayed()
        runOnIdle { loading.value = loading.value.copy(statusLabel = "Checking results") }
        onNodeWithText("Checking results").assertIsDisplayed()
        assertEquals(listOf("old add", "new goals", "new add"), events)
    }

    @Test
    fun `research host replaces its callbacks copy and bounds without keeping old actions`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val model = mutableStateOf(PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Capture, copy))
        val surface = mutableStateOf(PhotoAnalysisCaptureSurface(copy))
        val modifier = mutableStateOf<Modifier>(Modifier)
        val events = mutableListOf<String>()
        val onAction = mutableStateOf<(PhotoAnalysisPresenterAction) -> Unit>({ events.add("old") })
        setContent { PhotoAnalysisResearchCaptureScreen(model.value, onAction.value, surface.value, modifier.value) }
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).performClick()
        runOnIdle { onAction.value = { events.add("new") } }
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).performClick()
        runOnIdle {
            surface.value = surface.value.copy(
                presenterCopy = copy.copy(capture = copy.capture.copy(title = "New title")),
            )
        }
        onNodeWithText("New title").assertIsDisplayed()
        runOnIdle { modifier.value = Modifier.requiredSize(320.dp, 568.dp) }
        onNodeWithTag(UiAutomationIds.CAPTURE_IMPORT.value).assertIsDisplayed()
        runOnIdle {
            model.value = model.value.copy(
                state = PhotoAnalysisPresenterState.RECOVERY,
                statusLabel = "Try again",
                actions = listOf(PhotoAnalysisPresenterAction.Cancel),
            )
        }
        onNodeWithTag(UiAutomationIds.CAPTURE_CANCEL.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).assertTextEquals("Try again")
        assertEquals(listOf("old", "new", "new"), events)
    }

    @Test
    fun `replacement state flow stops the old session from changing the capture screen`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val old = MutableStateFlow<PhotoAnalysisState>(PhotoAnalysisState.Capture)
        val flow = mutableStateOf(old)
        val modifier = mutableStateOf<Modifier>(Modifier)
        val surface = mutableStateOf(PhotoAnalysisCaptureSurface(copy))
        val clicked = mutableListOf<String>()
        val action = mutableStateOf<(PhotoAnalysisPresenterAction) -> Unit>({ clicked.add("old") })
        setContent { PhotoAnalysisCaptureContent(flow.value, action.value, surface.value, modifier.value) }
        runOnIdle { action.value = { clicked.add("new") } }
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).performClick()
        runOnIdle { flow.value = MutableStateFlow(PhotoAnalysisState.Recovery(RecoveryReason.PERMISSION_DENIED)) }
        onNodeWithTag(
            UiAutomationIds.PHOTO_ANALYSIS_STATUS.value,
        ).assertTextEquals(copy.recovery.cameraPermissionRequired)
        runOnIdle { old.value = PhotoAnalysisState.Recovery(RecoveryReason.ANALYSIS_FAILED) }
        onNodeWithTag(
            UiAutomationIds.PHOTO_ANALYSIS_STATUS.value,
        ).assertTextEquals(copy.recovery.cameraPermissionRequired)
        runOnIdle { surface.value = surface.value.copy(closeLabel = "Close current session") }
        runOnIdle { modifier.value = Modifier.requiredSize(320.dp, 568.dp) }
        onNodeWithTag(UiAutomationIds.CAPTURE_OPEN_SETTINGS.value).performClick()
        assertEquals(listOf("new", "new"), clicked)
    }

    @Test
    fun `main shell replaces host content while preserving the selected navigation tab`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val state = mutableStateOf(layoutBottomBarState())
        val content = mutableStateOf<@Composable () -> Unit>({ Text("First host content") })
        val padding = mutableStateOf(PaddingValues())
        setContent {
            PhotoAnalysisMainLayout(
                state.value,
                layoutBottomBarActions(),
                contentPadding = padding.value,
                content = content.value,
            )
        }
        onNodeWithText("First host content").assertIsDisplayed()
        runOnIdle { content.value = { Text("Replacement content") } }
        onNodeWithText("First host content").assertDoesNotExist()
        onNodeWithText("Replacement content").assertIsDisplayed()
        runOnIdle { state.value = state.value.copy(selectedTab = 1) }
        runOnIdle { padding.value = PaddingValues(bottom = 20.dp) }
        onNodeWithText("Replacement content").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.REWARDS_TAB.value).assertIsDisplayed()
    }

    @Test
    fun `manual evidence rows replace a source without keeping stale accessibility copy`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val surface = mutableStateOf(
            PhotoAnalysisCaptureSurface(
                copy,
                manualIngredients = listOf(
                    PhotoAnalysisManualIngredientModel("rice", "Rice", "Dinner"),
                ),
            ),
        )
        setContent { PhotoAnalysisManualIngredientRows(surface.value) }
        onNodeWithText("${copy.evidence.manualIngredientSource}: Dinner").assertIsDisplayed()
        runOnIdle {
            surface.value = surface.value.copy(
                manualIngredients = listOf(PhotoAnalysisManualIngredientModel("rice", "Rice")),
            )
        }
        onNodeWithText(copy.evidence.manualIngredientSource).assertIsDisplayed()
        onNodeWithTag(photoAnalysisVisibleComponentId("manual:rice").value).assertIsDisplayed()
        runOnIdle { surface.value = surface.value.copy(manualIngredients = emptyList()) }
        onNodeWithTag(photoAnalysisVisibleComponentId("manual:rice").value).assertDoesNotExist()
    }

    @Test
    fun `reward card keeps progress and semantics paired when its row index changes`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val index = mutableStateOf(0)
        val card = mutableStateOf(layoutRewardsModel().cards.first())
        val modifier = mutableStateOf<Modifier>(Modifier)
        setContent { RewardsCard(index.value, card.value, modifier.value) }
        onNodeWithTag(UiAutomationIds.rewardsRowProgress(0).value).assertTextEquals("2 of 2")
        runOnIdle { index.value = 1 }
        onNodeWithTag(UiAutomationIds.rewardsRowProgress(0).value).assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.rewardsRowProgress(1).value).assertTextEquals("2 of 2")
        runOnIdle { card.value = card.value.copy(isEarned = false, progressLabel = "0 of 2", stateLabel = "Ready") }
        onNodeWithTag(UiAutomationIds.rewardsRowProgress(1).value).assertTextEquals("0 of 2")
        onNodeWithTag(UiAutomationIds.rewardsRowState(1).value).assertTextEquals("Ready")
        runOnIdle { modifier.value = Modifier.requiredSize(200.dp, 300.dp) }
        onNodeWithTag(UiAutomationIds.rewardsRowState(1).value).assertIsDisplayed()
    }

    @Test
    fun `grid text contrast retains theme defaults and provides both photo contrast variants`() {
        val surface = PhotoAnalysisCaptureSurface(PhotoAnalysisPresenterCopyFixtures.localized())
        assertEquals(androidx.compose.ui.text.TextStyle.Default, surface.overlayTextStyle())
        assertEquals(
            androidx.compose.ui.graphics.Color.White,
            surface.copy(photoTextContrast = PhotoAnalysisPhotoTextContrast.LIGHT_TEXT).overlayTextStyle().color,
        )
        assertEquals(
            androidx.compose.ui.graphics.Color.Black,
            surface.copy(photoTextContrast = PhotoAnalysisPhotoTextContrast.DARK_TEXT).overlayTextStyle().color,
        )
    }
}
