package com.supernutrition.feature.photoanalysis

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisGridCellId
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shared production screen is exercised through the Compose UI Test v2 API. */
@OptIn(ExperimentalTestApi::class)
class PhotoAnalysisCaptureScreenComposeTest {
    @Test
    fun `capture screen exposes the shared semantic contract`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        var closeRequested = false
        setContent {
            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel.from(
                    com.supernutrition.domain.photoanalysis.PhotoAnalysisState.Capture,
                    copy,
                ),
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = copy,
                    closeLabel = "Chiudi",
                    onCloseRequested = { closeRequested = true },
                ),
            )
        }

        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value).assertTextEquals(copy.capture.title)
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).assertTextEquals(copy.status.readyToCapture)
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).assertTextEquals(copy.capture.takeMealPhoto)
        onNodeWithTag(UiAutomationIds.CAPTURE_IMPORT.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.CAPTURE_IMPORT.value).assertTextEquals(copy.capture.choosePhoto)
        onNodeWithTag(UiAutomationIds.CAPTURE_CLOSE.value).assertIsDisplayed().performClick()
        assertTrue(closeRequested)
    }

    @Test
    fun `live camera occupies the photo slot before capture actions`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        setContent {
            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel.from(
                    com.supernutrition.domain.photoanalysis.PhotoAnalysisState.Capture,
                    copy,
                ),
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = copy,
                    liveCamera = { Text("Live camera") },
                ),
            )
        }

        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_RETAINED_PHOTO.value).assertIsDisplayed()
        onNodeWithText("Live camera").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `capture screen matches the research poc layout with warning and nine grid hints`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val selected = mutableListOf<Int>()
        setContent {
            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel.from(
                    com.supernutrition.domain.photoanalysis.PhotoAnalysisState.Capture,
                    copy,
                ),
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = copy,
                    gridHintsEnabled = true,
                    onGridCellSelected = selected::add,
                ),
            )
        }

        onNodeWithText(copy.capture.researchWarning).assertIsDisplayed()
        onNodeWithText(copy.capture.gridHintsTitle).assertIsDisplayed()
        (0..8).forEach { index ->
            onNodeWithTag(photoAnalysisGridCellId(index).value).assertIsDisplayed()
        }
        onNodeWithTag(photoAnalysisGridCellId(4).value).performClick()
        assertEquals(listOf(4), selected)
    }

    @Test
    fun `manual ingredient input follows the nine grid buttons and shares the evidence list`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        var addClicks = 0
        setContent {
            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel.from(
                    com.supernutrition.domain.photoanalysis.PhotoAnalysisState.Capture,
                    copy,
                ),
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = copy,
                    gridHintsEnabled = true,
                    manualIngredientInput = "rice",
                    manualIngredients = listOf(
                        PhotoAnalysisManualIngredientModel(
                            canonicalItemId = "food:rice-white-cooked",
                            label = "Riso",
                        ),
                    ),
                    onManualIngredientAdded = { addClicks++ },
                ),
            )
        }

        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_ADD.value)
            .performScrollTo()
            .performClick()

        assertEquals(1, addClicks)
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_COMPONENT_EVIDENCE.value).assertIsDisplayed()
        onNodeWithTag(photoAnalysisVisibleComponentId("manual:food:rice-white-cooked").value).assertIsDisplayed()
        onNodeWithText(copy.evidence.manualIngredientSource).assertIsDisplayed()
    }

    @Test
    fun `screen renders and dispatches every bounded action`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val clicked = mutableListOf<PhotoAnalysisPresenterAction>()
        val model = PhotoAnalysisPresenterModel(
            statusLabel = copy.recovery.photoCaptureFailed,
            state = PhotoAnalysisPresenterState.RECOVERY,
            actions = PhotoAnalysisPresenterAction.entries,
        )
        setContent {
            PhotoAnalysisCaptureScreen(
                model = model,
                onAction = clicked::add,
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = copy,
                    automationIds = PhotoAnalysisCaptureAutomationIds(
                        shutter = UiAutomationId("custom_shutter"),
                        import = UiAutomationId("custom_import"),
                    ),
                    retainedPhoto = { Text("Retained photo") },
                    photoTextContrast = PhotoAnalysisPhotoTextContrast.LIGHT_TEXT,
                ),
            )
        }

        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_RETAINED_PHOTO.value).assertIsDisplayed()
        onNodeWithText("Retained photo").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).performScrollTo().assertIsDisplayed()
        onNodeWithTag("custom_import").performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).performScrollTo().assertIsDisplayed()
        onNodeWithTag("custom_shutter").performScrollTo().performClick()
        onNodeWithTag("custom_import").performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.CAPTURE_OPEN_SETTINGS.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.CAPTURE_CANCEL.value).performScrollTo().performClick()
        onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).performScrollTo().performClick()

        assertEquals(PhotoAnalysisPresenterAction.entries.toList(), clicked.toList())
    }

    @Test
    fun `screen remains a stable status surface when no action is available`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        setContent {
            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel(
                    statusLabel = copy.status.restoringPhotoAnalysis,
                    state = PhotoAnalysisPresenterState.BACKGROUNDED,
                    actions = emptyList(),
                ),
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = copy,
                    photoTextContrast = PhotoAnalysisPhotoTextContrast.DARK_TEXT,
                ),
            )
        }

        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).assertIsDisplayed()
    }

    @Test
    fun `review renders separate dish text and from-photo evidence without selection controls`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val model = reviewModel(copy)
        setContent {
            PhotoAnalysisCaptureScreen(
                model = model,
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(presenterCopy = copy),
            )
        }

        onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.CAPTURE_IMPORT.value).performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MAIN_DISH_PROPOSALS.value).assertIsDisplayed()
        onNodeWithText("Salmone con riso").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_COMPONENT_EVIDENCE.value).assertIsDisplayed()
        onNodeWithTag(photoAnalysisVisibleComponentId("component-rice").value).assertIsDisplayed()
        onNodeWithText("Riso").assertIsDisplayed()
        onNodeWithText(copy.evidence.fromThisPhoto).assertIsDisplayed()
        onNodeWithText("Estimated: 120–150 g").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_COMPOSITION_HINTS.value).assertIsDisplayed()
        onNodeWithText("Pomodoro").assertIsDisplayed()
        onNodeWithText(copy.evidence.compositionHintSource).assertIsDisplayed()
        onNodeWithText(copy.evidence.fromThisPhoto).assertExists()
    }

    @Test
    fun `local visible-food review omits ingredient hints and grams`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val initial = reviewModel(copy)
        val review = requireNotNull(initial.review)
        val model = initial.copy(
            review = review.copy(
                visibleComponents = review.visibleComponents.map { it.copy(mass = null) },
                compositionHints = emptyList(),
            ),
        )

        setContent {
            PhotoAnalysisCaptureScreen(
                model = model,
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(presenterCopy = copy),
            )
        }

        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_COMPONENT_EVIDENCE.value).assertIsDisplayed()
        onNodeWithText("Estimated: 120–150 g").assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_COMPOSITION_HINTS.value).assertDoesNotExist()
        onNodeWithText("Pomodoro").assertDoesNotExist()
    }

    @Test
    fun `review keeps separate photo evidence with the same localized label`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val initial = reviewModel(copy)
        val review = requireNotNull(initial.review)
        val first = review.visibleComponents.single()
        val model = initial.copy(
            review = review.copy(
                visibleComponents = listOf(
                    first,
                    first.copy(componentId = "component-rice-second"),
                ),
            ),
        )

        setContent {
            PhotoAnalysisCaptureScreen(
                model = model,
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(presenterCopy = copy),
            )
        }

        onNodeWithTag(photoAnalysisVisibleComponentId("component-rice").value).assertIsDisplayed()
        onNodeWithTag(photoAnalysisVisibleComponentId("component-rice-second").value).assertIsDisplayed()
    }

    private fun reviewModel(copy: PhotoAnalysisPresenterCopy): PhotoAnalysisPresenterModel =
        PhotoAnalysisPresenterModel(
            statusLabel = copy.status.reviewCandidate,
            state = PhotoAnalysisPresenterState.CANDIDATE_REVIEW,
            actions = listOf(
                PhotoAnalysisPresenterAction.ScanAgain,
                PhotoAnalysisPresenterAction.ImportPhoto,
                PhotoAnalysisPresenterAction.Cancel,
            ),
            review = PhotoAnalysisReviewModel(
                mainDishProposals = listOf(
                    PhotoAnalysisDishProposalModel(
                        candidateId = "dish-salmon",
                        label = "Salmone con riso",
                        rank = 1,
                        confidenceLabel = copy.evidence.highConfidence,
                        provenanceLabel = copy.evidence.localProvenance,
                    ),
                ),
                visibleComponents = listOf(
                    PhotoAnalysisVisibleComponentModel(
                        componentId = "component-rice",
                        label = "Riso",
                        sourceLabel = copy.evidence.fromThisPhoto,
                        confidenceLabel = copy.evidence.highConfidence,
                        ambiguityLabel = copy.evidence.identityClear,
                        visibilityLabel = copy.evidence.fullyVisible,
                        region = PhotoAnalysisVisibleRegionModel(0.1, 0.1, 0.2, 0.2),
                        regionLabel = copy.evidence.regionAvailable,
                        mass = PhotoAnalysisMassModel(
                            PhotoAnalysisMassStatus.ESTIMATED,
                            "Estimated: 120–150 g",
                        ),
                        provenanceLabel = copy.evidence.localProvenance,
                        selectionLabel = copy.evidence.notSelected,
                    ),
                ),
                compositionHints = listOf(
                    PhotoAnalysisDishIngredientHintModel(
                        ingredientId = "ingredient:tomato",
                        label = "Pomodoro",
                        sourceLabel = copy.evidence.compositionHintSource,
                        sourceDishCandidateIds = listOf("dish-salmon"),
                    ),
                ),
            ),
        )

    @Test
    fun `each local wait stage exposes retake import and cancel actions without nutrition output`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val ref = com.supernutrition.domain.photoanalysis.PhotoRef.issue(
            com.supernutrition.domain.photoanalysis.AttemptId("compose-stage-attempt"),
            "compose-stage-photo",
        )
        val requestId = com.supernutrition.domain.photoanalysis.RequestId("compose-stage-request")

        listOf(
            com.supernutrition.domain.photoanalysis.LocalAnalysisStage.FINDING_VISIBLE_FOODS,
            com.supernutrition.domain.photoanalysis.LocalAnalysisStage.IDENTIFYING_DISH,
            com.supernutrition.domain.photoanalysis.LocalAnalysisStage.ESTIMATING_PORTIONS,
            com.supernutrition.domain.photoanalysis.LocalAnalysisStage.CHECKING_RESULTS,
        ).forEach { stage ->
            setContent {
                PhotoAnalysisCaptureScreen(
                    model = PhotoAnalysisPresenterModel.from(
                        com.supernutrition.domain.photoanalysis.PhotoAnalysisState.Analyzing(
                            ref,
                            requestId,
                            stage,
                        ),
                        copy,
                    ),
                    onAction = {},
                    surface = PhotoAnalysisCaptureSurface(presenterCopy = copy),
                )
            }

            onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STAGE.value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.CAPTURE_IMPORT.value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.CAPTURE_CANCEL.value).assertIsDisplayed()
            onNodeWithText("calorie", substring = true, ignoreCase = true).assertDoesNotExist()
            onNodeWithText("nutrition", substring = true, ignoreCase = true).assertDoesNotExist()
        }
    }

    @Test
    fun `research grid exposes nine cells and loading overlay while analysis is active`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val selected = mutableListOf<Int>()
        val ref = com.supernutrition.domain.photoanalysis.PhotoRef.issue(
            com.supernutrition.domain.photoanalysis.AttemptId("compose-grid-attempt"),
            "compose-grid-photo",
        )
        val requestId = com.supernutrition.domain.photoanalysis.RequestId("compose-grid-request")
        setContent {
            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel.from(
                    com.supernutrition.domain.photoanalysis.PhotoAnalysisState.Analyzing(
                        ref,
                        requestId,
                    ),
                    copy,
                ),
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = copy,
                    retainedPhoto = { Text("Retained photo") },
                    gridHintsEnabled = true,
                    onGridCellSelected = selected::add,
                ),
            )
        }

        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_GRID_OVERLAY.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_LOADING.value).assertIsDisplayed()
        (0..8).forEach { index ->
            onNodeWithTag(photoAnalysisGridCellId(index).value).performScrollTo().assertIsDisplayed()
        }
        onNodeWithTag(photoAnalysisGridCellId(7).value).performScrollTo().performClick()
        assertEquals(listOf(7), selected)
    }
}
