package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.RecoveryReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CameraScreenRefreshTest {
    @Test
    fun `camera transitions preserve the correct preview and dispatch current recovery actions`() =
        runSkikoComposeUiTest(
            size = Size(375f, 812f),
            density = Density(1f),
        ) {
            val copy = PhotoAnalysisPresenterCopyFixtures.localized()
            val model = mutableStateOf(PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Capture, copy))
            val surface = mutableStateOf(PhotoAnalysisCaptureSurface(copy, liveCamera = { Text("Live preview") }))
            val modifier = mutableStateOf<Modifier>(Modifier)
            val events = mutableListOf<Pair<String, PhotoAnalysisPresenterAction>>()
            val onAction = mutableStateOf<(PhotoAnalysisPresenterAction) -> Unit>({ events.add("old" to it) })
            setContent { PhotoAnalysisCaptureScreen(model.value, onAction.value, surface.value, modifier.value) }
            onNodeWithText("Live preview").assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).performClick()
            runOnIdle { modifier.value = Modifier.requiredSize(320.dp, 320.dp) }
            onNodeWithTag(UiAutomationIds.CAPTURE_IMPORT.value).assertIsDisplayed().performClick()
            runOnIdle {
                surface.value = surface.value.copy(retainedPhoto = { Text("Retained preview") })
                model.value = model.value.copy(state = PhotoAnalysisPresenterState.PREPARING, statusLabel = "Preparing")
            }
            onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_LOADING.value).assertIsDisplayed()
            onNodeWithText("Retained preview").assertIsDisplayed()
            runOnIdle {
                model.value = model.value.copy(
                    state = PhotoAnalysisPresenterState.ANALYZING,
                    statusLabel = "Analyzing",
                )
            }
            onNodeWithText("Analyzing").assertIsDisplayed()
            runOnIdle {
                onAction.value = { events.add("new" to it) }
                model.value = model.value.copy(
                    state = PhotoAnalysisPresenterState.RECOVERY,
                    statusLabel = "Try again",
                    actions = PhotoAnalysisPresenterAction.entries + PhotoAnalysisPresenterAction.SelectGridCell(4),
                )
                modifier.value = Modifier
            }
            listOf(
                UiAutomationIds.CAPTURE_SHUTTER,
                UiAutomationIds.CAPTURE_IMPORT,
                UiAutomationIds.CAPTURE_OPEN_SETTINGS,
                UiAutomationIds.CAPTURE_CANCEL,
                UiAutomationIds.CAPTURE_SCAN_AGAIN,
            ).forEach { id ->
                onNodeWithTag(id.value).performScrollTo().performClick()
            }
            assertEquals(
                listOf(
                    "old" to PhotoAnalysisPresenterAction.TakePhoto,
                    "old" to PhotoAnalysisPresenterAction.ImportPhoto,
                ) +
                    PhotoAnalysisPresenterAction.entries.map { "new" to it },
                events,
            )
        }

    @Test
    fun `backgrounded preview stays hidden and recapture drops retained pixels`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val model = mutableStateOf(PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Capture, copy))
        val surface = mutableStateOf(
            PhotoAnalysisCaptureSurface(
                copy,
                retainedPhoto = { Text("Retained preview") },
                liveCamera = { Text("Live preview") },
            ),
        )
        setContent { PhotoAnalysisCaptureScreen(model.value, {}, surface.value) }
        runOnIdle {
            model.value = model.value.copy(
                state = PhotoAnalysisPresenterState.BACKGROUNDED,
                actions = emptyList(),
            )
        }
        onNodeWithText("Retained preview").assertDoesNotExist()
        onNodeWithText("Live preview").assertDoesNotExist()
        runOnIdle { model.value = model.value.copy(state = PhotoAnalysisPresenterState.READY) }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_CUSTOM_MEAL.value).assertIsDisplayed()
        runOnIdle {
            model.value = PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Capture, copy)
            surface.value = surface.value.copy(retainedPhoto = null, liveCamera = null)
        }
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).assertIsDisplayed()
        onNodeWithText("Retained preview").assertDoesNotExist()
    }

    @Test
    fun `review refreshes host selections and confirms only the latest evidence and note`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val model = mutableStateOf(cameraReviewFixture())
        val confirmations = mutableListOf<PhotoAnalysisMealReviewConfirmation>()
        val surface = mutableStateOf(
            PhotoAnalysisCaptureSurface(
                PhotoAnalysisPresenterCopyFixtures.localized(),
                retainedPhoto = { Text("Meal photo") },
            ),
        )
        setContent { PhotoAnalysisCaptureScreen(model.value, {}, surface.value) }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).assertIsNotEnabled()
        onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(1).value).performClick().assertIsSelected()
        runOnIdle { selectLowDish(model, surface, confirmations) }
        onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(4).value).assertIsSelected()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).performClick()
        assertEquals("Low proposal", confirmations.single().dishLabel)
        runOnIdle {
            surface.value = surface.value.copy(
                mealReviewSelection = MealReviewChoices(customMeal = true),
                onManualIngredientInputChanged = { value ->
                    surface.value = surface.value.copy(manualIngredientInput = value)
                },
                manualIngredientUnresolved = listOf("Unknown ingredient"),
                manualIngredients = listOf(PhotoAnalysisManualIngredientModel("carrot", "Carrot", "Lunch")),
            )
        }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_ADD.value).assertIsDisplayed().performClick()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value).assertIsFocused()
            .performTextReplacement("  no salt  ")
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value).performImeAction()
        onNodeWithText("Unknown ingredient").performScrollTo().assertIsDisplayed()
        onNodeWithTag(photoAnalysisVisibleComponentId("manual:carrot").value).assertIsDisplayed()
        onNodeWithTag(
            UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value,
        ).assertIsDisplayed().assertIsEnabled().performTouchInput { click(center) }
        assertEquals("no salt", confirmations.last().note)
        assertEquals(listOf("Carrot"), confirmations.last().addedIngredientLabels)
        runOnIdle { model.value = model.value.copy(review = null) }
        onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(4).value).assertDoesNotExist()
        runOnIdle {
            val sticker = FoodStickers.first()
            surface.value = surface.value.copy(
                manualIngredients = emptyList(),
                manualIngredientInput = "",
                mealReviewSelection = MealReviewChoices(pantryIds = setOf(sticker.id)),
                presenterCopy = surface.value.presenterCopy.copy(
                    review = MealReviewCopy(
                        pantryIngredientLabels = mapOf(sticker.id to "Translated food"),
                    ),
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value)
            .assertIsDisplayed().assertIsEnabled().performTouchInput { click(center) }
        assertEquals(listOf("Translated food"), confirmations.last().addedIngredientLabels)
        assertEquals(FoodStickers.first().label, MealReviewCopy().pantryLabel(FoodStickers.first()))
    }

    @Test
    fun `state flow refreshes capture permission recovery without replacing its composition`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val state = MutableStateFlow<PhotoAnalysisState>(PhotoAnalysisState.Capture)
        val clicked = mutableListOf<PhotoAnalysisPresenterAction>()
        setContent { PhotoAnalysisCaptureContent(state, clicked::add, PhotoAnalysisCaptureSurface(copy)) }
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).assertIsDisplayed()
        runOnIdle { state.value = PhotoAnalysisState.Recovery(RecoveryReason.PERMISSION_DENIED) }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.CAPTURE_OPEN_SETTINGS.value).performClick()
        assertEquals<List<PhotoAnalysisPresenterAction>>(listOf(PhotoAnalysisPresenterAction.OpenSettings), clicked)
        runOnIdle { state.value = PhotoAnalysisState.Capture }
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).assertIsDisplayed()
    }

    private fun selectLowDish(
        model: MutableState<PhotoAnalysisPresenterModel>,
        surface: MutableState<PhotoAnalysisCaptureSurface>,
        confirmations: MutableList<PhotoAnalysisMealReviewConfirmation>,
    ) {
        val review = requireNotNull(model.value.review)
        model.value = model.value.copy(
            review = review.copy(
                mainDishProposals = review.mainDishProposals + PhotoAnalysisDishProposalModel(
                    "dish-low",
                    "Low proposal",
                    4,
                    "Low confidence",
                    "On device",
                    ConfidenceBand.LOW,
                ),
                degradationLabel = "Review these foods",
            ),
        )
        surface.value = surface.value.copy(
            mealReviewSelection = MealReviewChoices(dishId = "dish-low"),
            onMealReviewConfirmed = confirmations::add,
        )
    }
}
