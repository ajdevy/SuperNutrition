package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EmbeddedScreenRefreshTest {
    @Test
    fun `embedded meal review keeps host choice and dispatches current retake callback after copy refresh`() =
        runSkikoComposeUiTest(
            size = Size(375f, 812f),
            density = Density(1f),
        ) {
            val model = mutableStateOf(cameraReviewFixture())
            val surface = mutableStateOf(
                PhotoAnalysisCaptureSurface(
                    PhotoAnalysisPresenterCopyFixtures.localized(),
                    mealReviewSelection = MealReviewChoices(dishId = "dish-1", componentIds = setOf("photo-salmon")),
                ),
            )
            val events = mutableListOf<String>()
            val onAction = mutableStateOf<(PhotoAnalysisPresenterAction) -> Unit>({ events.add("old retake") })
            surface.value = surface.value.copy(onMealReviewSelectionChanged = { choice ->
                surface.value = surface.value.copy(mealReviewSelection = choice)
            })
            setContent { PhotoAnalysisMealReview(model.value, onAction.value, surface.value) }
            onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).performClick()
            runOnIdle { onAction.value = { events.add("new retake") } }
            onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).performClick()
            runOnIdle {
                surface.value = surface.value.copy(
                    presenterCopy = surface.value.presenterCopy.copy(
                        review = MealReviewCopy(
                            title = "Check your meal",
                            retake = "Photograph again",
                            addToMeal = "Save this meal",
                        ),
                    ),
                )
            }
            onNodeWithText("Check your meal").assertIsDisplayed()
            onNodeWithText("Photograph again").assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(1).value).assertIsSelected()
            runOnIdle {
                model.value = model.value.copy(
                    review = requireNotNull(model.value.review).copy(
                        degradationLabel = "Updated evidence",
                        visibleComponents = listOf(cameraComponentFixture("new", "Banana")),
                    ),
                )
            }
            onNodeWithText("Updated evidence").assertIsDisplayed()
            onNodeWithTag(photoAnalysisVisibleComponentId("photo-salmon").value).assertDoesNotExist()
            onNodeWithTag(photoAnalysisVisibleComponentId("new").value).performClick().assertIsSelected()
            assertEquals(listOf("old retake", "new retake"), events)
        }

    @Test
    fun `research toolbar and photo area follow live slot copy and action refresh`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val actions = mutableStateOf(listOf<PhotoAnalysisPresenterAction>(PhotoAnalysisPresenterAction.TakePhoto))
        val surface = mutableStateOf(PhotoAnalysisCaptureSurface(copy, liveCamera = { Text("First slot") }))
        val review = mutableStateOf<PhotoAnalysisReviewModel?>(null)
        val selected = mutableStateOf(-1)
        val analyzing = mutableStateOf(false)
        val events = mutableListOf<String>()
        val onAction = mutableStateOf<(PhotoAnalysisPresenterAction) -> Unit>({ events.add("old action") })
        setContent {
            Column {
                PhotoAnalysisPocHeader(surface.value)
                PhotoAnalysisPocResearchWarning(surface.value)
                PhotoAnalysisPocPhotoArea(review.value, selected.value, analyzing.value, surface.value)
                PhotoAnalysisPocCaptureActions(actions.value, onAction.value, surface.value)
            }
        }
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).performClick()
        runOnIdle { onAction.value = { events.add("new action") } }
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).performClick()
        runOnIdle {
            actions.value = listOf(PhotoAnalysisPresenterAction.ImportPhoto)
            surface.value = surface.value.copy(
                retainedPhoto = { Text("Second slot") },
                gridHintsEnabled = true,
                presenterCopy = copy.copy(
                    capture = copy.capture.copy(title = "Updated research", researchWarning = "Updated warning"),
                ),
            )
            analyzing.value = true
        }
        onNodeWithText("Updated research").assertIsDisplayed()
        onNodeWithText("Updated warning").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_LOADING.value).assertIsDisplayed()
        runOnIdle {
            analyzing.value = false
            selected.value = 4
            review.value = cameraReviewFixture().review
        }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_GRID_OVERLAY.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.CAPTURE_IMPORT.value).performClick()
        assertEquals(listOf("old action", "new action", "new action"), events)
    }

    @Test
    fun `embedded manual ingredient editor refreshes labels sources and keyboard callback`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val added = mutableListOf<String>()
        val surface = mutableStateOf(PhotoAnalysisCaptureSurface(copy))
        surface.value = surface.value.copy(onManualIngredientInputChanged = { text ->
            surface.value = surface.value.copy(manualIngredientInput = text)
        }, onManualIngredientAdded = { added.add("old add") })
        setContent {
            Column {
                PhotoAnalysisManualIngredientInput(
                    surface.value,
                )
                PhotoAnalysisManualIngredients(surface.value)
            }
        }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_ADD.value).assertIsNotEnabled()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value).performTextReplacement("Carrot")
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value).performImeAction()
        runOnIdle {
            surface.value = surface.value.copy(
                onManualIngredientAdded = { added.add("new add") },
                manualIngredients = listOf(PhotoAnalysisManualIngredientModel("carrot", "Carrot", "Lunch")),
                presenterCopy = copy.copy(capture = copy.capture.copy(manualIngredientAdd = "Save ingredient")),
            )
        }
        onNodeWithText("Save ingredient").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value).performImeAction()
        onNodeWithTag(photoAnalysisVisibleComponentId("manual:carrot").value).assertContentDescriptionEquals(
            "Carrot, ${copy.evidence.manualIngredientSource}, Lunch",
        )
        runOnIdle {
            surface.value = surface.value.copy(
                manualIngredients = listOf(PhotoAnalysisManualIngredientModel("carrot", "Carrot")),
            )
        }
        onNodeWithText(copy.evidence.manualIngredientSource).assertIsDisplayed()
        assertEquals(listOf("old add", "new add"), added)
    }
}
