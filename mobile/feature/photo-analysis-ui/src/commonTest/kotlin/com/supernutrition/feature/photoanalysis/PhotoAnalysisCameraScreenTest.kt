package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PhotoAnalysisCameraScreenTest {
    @Test
    fun `camera artwork controls route capture import and close`() = runComposeUiTest {
        val actions = mutableListOf<PhotoAnalysisPresenterAction>()
        var closed = false
        setContent {
            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel("Ready", PhotoAnalysisPresenterState.CAPTURE, emptyList()),
                onAction = actions::add,
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                    liveCamera = { Text("Camera pixels") },
                    onCloseRequested = { closed = true },
                ),
            )
        }
        onNodeWithText("Camera pixels").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).assertIsDisplayed().performClick()
        onNodeWithTag(UiAutomationIds.CAPTURE_IMPORT.value).assertIsDisplayed().performClick()
        onNodeWithTag(UiAutomationIds.CAPTURE_CLOSE.value).performClick()
        assertEquals(listOf(PhotoAnalysisPresenterAction.TakePhoto, PhotoAnalysisPresenterAction.ImportPhoto), actions)
        assertTrue(closed)
    }

    @Test
    fun `review starts unselected and confirms only explicit photo evidence and manually entered food text`() =
        runComposeUiTest {
            var confirmed: PhotoAnalysisMealReviewConfirmation? = null
            val input = mutableStateOf("")
            val model = cameraReviewFixture()
            setContent {
                PhotoAnalysisCaptureScreen(
                    model,
                    {},
                    modifier = Modifier.requiredSize(375.dp, 600.dp),
                    surface = PhotoAnalysisCaptureSurface(
                        presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                        manualIngredientInput = input.value,
                        onManualIngredientInputChanged = { input.value = it },
                        onMealReviewConfirmed = { confirmed = it },
                    ),
                )
            }
            val dish = onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(1).value)
            dish.assertIsNotSelected().performClick().assertIsSelected()
            val salmon = onNodeWithTag(photoAnalysisVisibleComponentId("photo-salmon").value)
            salmon.performScrollTo().assertIsNotSelected().performClick().assertIsSelected()
            salmon.performClick().assertIsNotSelected().performClick().assertIsSelected()
            onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_ADD.value).performScrollTo().performClick()
            onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value).performTextInput("Carrot")
            onNodeWithTag(
                UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value,
            ).performScrollTo().assertIsEnabled().performClick()
            val value = assertNotNull(confirmed)
            assertEquals("dish-1", value.dishCandidateId)
            assertEquals(listOf("photo-salmon"), value.photoEvidenceComponentIds)
            assertEquals(emptyList(), value.addedIngredientLabels)
            assertEquals("Carrot", value.note)
        }

    @Test
    fun `custom meal requires content and preserves editable free text`() = runComposeUiTest {
        val input = mutableStateOf("")
        var confirmed: PhotoAnalysisMealReviewConfirmation? = null
        setContent {
            PhotoAnalysisCaptureScreen(
                cameraReviewFixture(),
                {},
                modifier = Modifier.requiredSize(375.dp, 600.dp),
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                    manualIngredientInput = input.value,
                    onManualIngredientInputChanged = { input.value = it },
                    onManualIngredientAdded = { input.value = "" },
                    onMealReviewConfirmed = { confirmed = it },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_CUSTOM_MEAL.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).performScrollTo().assertIsNotEnabled()
        val note = onNodeWithTag(
            UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value,
        ).performScrollTo()
        note.performTextInput("Rice, less sauce")
        note.performImeAction()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).performScrollTo().performClick()
        assertEquals("Rice, less sauce", input.value)
        assertEquals("Rice, less sauce", assertNotNull(confirmed).note)
        assertEquals(null, confirmed.dishCandidateId)
    }

    @Test
    fun `duplicate and unfamiliar evidence remains separately selectable and retake is reachable`() = runComposeUiTest {
        val actions = mutableListOf<PhotoAnalysisPresenterAction>()
        val fixture = cameraReviewFixture()
        val review = assertNotNull(fixture.review)
        val model = fixture.copy(
            review = review.copy(
                visibleComponents = review.visibleComponents + cameraComponentFixture("second-salmon", "Salmon") +
                    cameraComponentFixture("photo-mushroom", "Mushroom"),
            ),
        )
        setContent {
            PhotoAnalysisCaptureScreen(
                model,
                actions::add,
                modifier = Modifier.requiredSize(
                    375.dp,
                    600.dp,
                ),
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                ),
            )
        }
        onNodeWithTag(
            photoAnalysisVisibleComponentId("second-salmon").value,
        ).performScrollTo().performClick().assertIsSelected()
        onNodeWithTag(
            photoAnalysisVisibleComponentId("photo-mushroom").value,
        ).performScrollTo().performClick().assertIsSelected()
        onNodeWithTag(photoAnalysisVisibleComponentId("photo-salmon").value).assertIsNotSelected()
        onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).performScrollTo().performClick()
        assertEquals<List<PhotoAnalysisPresenterAction>>(listOf(PhotoAnalysisPresenterAction.ScanAgain), actions)
    }

    @Test
    fun `retake returns to camera pixels even when a host still has its previous preview`() = runComposeUiTest {
        val model = mutableStateOf(cameraReviewFixture())
        setContent {
            PhotoAnalysisCaptureScreen(
                model.value,
                onAction = {
                    model.value = PhotoAnalysisPresenterModel(
                        "Ready",
                        PhotoAnalysisPresenterState.CAPTURE,
                        emptyList(),
                    )
                },
                modifier = Modifier.requiredSize(375.dp, 600.dp),
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                    liveCamera = { Text("New camera pixels") },
                    retainedPhoto = { Text("Previous photo") },
                ),
            )
        }
        onNodeWithText("Previous photo").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).performScrollTo().performClick()
        onNodeWithText("Previous photo").assertDoesNotExist()
        onNodeWithText("New camera pixels").assertIsDisplayed()
    }

    @Test
    fun `loading and permission recovery preserve actionable feedback`() = runComposeUiTest {
        val actions = mutableListOf<PhotoAnalysisPresenterAction>()
        val model = mutableStateOf(
            PhotoAnalysisPresenterModel(
                "Finding foods",
                PhotoAnalysisPresenterState.ANALYZING,
                listOf(PhotoAnalysisPresenterAction.ScanAgain),
            ),
        )
        setContent {
            PhotoAnalysisCaptureScreen(
                model.value,
                actions::add,
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_LOADING.value).assertIsDisplayed()
        onNodeWithText("Finding foods").assertIsDisplayed()
        model.value = PhotoAnalysisPresenterModel(
            "Permission required",
            PhotoAnalysisPresenterState.RECOVERY,
            listOf(PhotoAnalysisPresenterAction.OpenSettings),
        )
        onNodeWithTag(UiAutomationIds.CAPTURE_OPEN_SETTINGS.value).performClick()
        assertEquals<List<PhotoAnalysisPresenterAction>>(listOf(PhotoAnalysisPresenterAction.OpenSettings), actions)
    }
}

internal fun cameraReviewFixture() = PhotoAnalysisPresenterModel(
    statusLabel = "Review meal",
    state = PhotoAnalysisPresenterState.CANDIDATE_REVIEW,
    actions = emptyList(),
    review = PhotoAnalysisReviewModel(
        mainDishProposals = listOf(
            "Salmon grain bowl",
            "Green power bowl",
            "Salmon poke bowl",
        ).mapIndexed { index, label ->
            PhotoAnalysisDishProposalModel("dish-${index + 1}", label, index + 1, "High confidence", "On device")
        },
        visibleComponents = listOf("Salmon", "Broccoli", "Avocado", "Leafy greens").map { label ->
            cameraComponentFixture("photo-${label.lowercase().replace(' ', '-')}", label)
        },
    ),
)

internal fun cameraComponentFixture(id: String, label: String) = PhotoAnalysisVisibleComponentModel(
    componentId = id, label = label, sourceLabel = "From this photo", confidenceLabel = "High confidence",
    ambiguityLabel = "Clear", visibilityLabel = "Visible", region = null, regionLabel = "No region", mass = null,
    provenanceLabel = "On device", selectionLabel = "Not selected",
)
