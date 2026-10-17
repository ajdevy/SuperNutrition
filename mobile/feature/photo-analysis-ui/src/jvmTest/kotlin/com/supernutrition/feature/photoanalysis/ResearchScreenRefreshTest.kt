package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisGridCellId
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ResearchScreenRefreshTest {
    @Test
    fun `research grid follows photo and evidence refresh and clears its selection on recapture`() =
        runSkikoComposeUiTest(
            size = Size(375f, 812f),
            density = Density(1f),
        ) {
            val copy = PhotoAnalysisPresenterCopyFixtures.localized()
            val model = mutableStateOf(PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Capture, copy))
            val selected = mutableListOf<Int>()
            val surface = mutableStateOf(researchSurface(copy, selected::add))
            val modifier = mutableStateOf<Modifier>(Modifier)
            setContent { PhotoAnalysisCaptureScreen(model.value, {}, surface.value, modifier.value) }
            onNodeWithText("Research live camera").assertIsDisplayed()
            onNodeWithTag(photoAnalysisGridCellId(0).value).performScrollTo().performClick()
            runOnIdle {
                surface.value = surface.value.copy(
                    retainedPhoto = { Text("Research retained photo") },
                    retainedPhotoAspectRatio = 1f,
                    photoTextContrast = PhotoAnalysisPhotoTextContrast.DARK_TEXT,
                )
                model.value = model.value.copy(state = PhotoAnalysisPresenterState.ANALYZING, statusLabel = "Working")
            }
            onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_LOADING.value).performScrollTo().assertIsDisplayed()
            runOnIdle {
                model.value = model.value.copy(
                    state = PhotoAnalysisPresenterState.CANDIDATE_REVIEW,
                    review = PhotoAnalysisReviewModel(emptyList(), emptyList()),
                )
            }
            onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_GRID_OVERLAY.value).performScrollTo().assertIsDisplayed()
            runOnIdle {
                model.value = model.value.copy(review = researchReview())
                surface.value = surface.value.copy(photoTextContrast = PhotoAnalysisPhotoTextContrast.LIGHT_TEXT)
            }
            onNodeWithTag(photoAnalysisVisibleComponentId("photo-salmon").value).performScrollTo().assertIsDisplayed()
            onNodeWithText("120 g").assertIsDisplayed()
            onNodeWithText("Rice hint").performScrollTo().assertIsDisplayed()
            runOnIdle {
                surface.value = surface.value.copy(onGridCellSelected = null)
                modifier.value = Modifier.requiredSize(320.dp, 568.dp)
            }
            onNodeWithTag(photoAnalysisGridCellId(8).value).performScrollTo().performClick()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, copy.capture.selectedState))
            assertEquals(listOf(0), selected)
            runOnIdle { model.value = model.value.copy(state = PhotoAnalysisPresenterState.BACKGROUNDED) }
            onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_GRID_OVERLAY.value).assertDoesNotExist()
            onNodeWithTag(photoAnalysisGridCellId(8).value).assertDoesNotExist()
            runOnIdle {
                model.value = PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Capture, copy)
                surface.value = surface.value.copy(retainedPhoto = null, liveCamera = null, photoTextContrast = null)
            }
            onNodeWithTag(photoAnalysisGridCellId(8).value).performScrollTo()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        copy.capture.notSelectedState,
                    ),
                )
        }

    @Test
    fun `manual research input keeps current callback and text source after results arrive`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val model = mutableStateOf(PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Capture, copy))
        val events = mutableListOf<String>()
        val surface = mutableStateOf(PhotoAnalysisCaptureSurface(copy, researchLayout = true, gridHintsEnabled = true))
        surface.value = surface.value.copy(onManualIngredientInputChanged = { text ->
            surface.value = surface.value.copy(manualIngredientInput = text)
        }, onManualIngredientAdded = { events.add("old add") })
        setContent { PhotoAnalysisCaptureScreen(model.value, {}, surface.value) }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_ADD.value).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value).performScrollTo()
            .performTextReplacement("rice")
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value).performImeAction()
        assertEquals(listOf("old add"), events)
        runOnIdle {
            surface.value = surface.value.copy(
                onManualIngredientAdded = { events.add("new add") },
                manualIngredientUnresolved = listOf("Unresolved food"),
                manualIngredients = listOf(PhotoAnalysisManualIngredientModel("rice", "Rice", "Dinner")),
            )
        }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_ADD.value).performScrollTo().performClick()
        onNodeWithText("Unresolved food").assertIsDisplayed()
        onNodeWithTag(photoAnalysisVisibleComponentId("manual:rice").value).performScrollTo().assertIsDisplayed()
        onNodeWithText("${copy.evidence.manualIngredientSource}: Dinner").assertIsDisplayed()
        runOnIdle {
            model.value = model.value.copy(
                state = PhotoAnalysisPresenterState.CANDIDATE_REVIEW,
                review = PhotoAnalysisReviewModel(emptyList(), emptyList()),
            )
        }
        onNodeWithTag(photoAnalysisVisibleComponentId("manual:rice").value).performScrollTo().assertIsDisplayed()
        runOnIdle {
            surface.value = surface.value.copy(
                manualIngredients = listOf(
                    PhotoAnalysisManualIngredientModel("rice", "Rice"),
                ),
                manualIngredientUnresolved = emptyList(),
            )
        }
        onNodeWithText(copy.evidence.manualIngredientSource).performScrollTo().assertIsDisplayed()
        runOnIdle { surface.value = surface.value.copy(manualIngredients = emptyList()) }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_COMPONENT_EVIDENCE.value).assertDoesNotExist()
        assertEquals(listOf("old add", "new add"), events)
    }

    @Test
    fun `research recovery drops old callbacks and ignores grid actions in the capture toolbar`() =
        runSkikoComposeUiTest(
            size = Size(375f, 812f),
            density = Density(1f),
        ) {
            val copy = PhotoAnalysisPresenterCopyFixtures.localized()
            val model = mutableStateOf(PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Capture, copy))
            val events = mutableListOf<PhotoAnalysisPresenterAction>()
            val close = mutableListOf<String>()
            val surface = mutableStateOf(
                PhotoAnalysisCaptureSurface(
                    copy,
                    researchLayout = true,
                    onCloseRequested = { close.add("old close") },
                ),
            )
            val onAction = mutableStateOf<(PhotoAnalysisPresenterAction) -> Unit>({ events.add(it) })
            setContent { PhotoAnalysisCaptureScreen(model.value, onAction.value, surface.value) }
            onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).performScrollTo().performClick()
            runOnIdle {
                model.value = model.value.copy(
                    state = PhotoAnalysisPresenterState.RECOVERY,
                    statusLabel = "Recovery",
                    actions = listOf(
                        PhotoAnalysisPresenterAction.Cancel,
                        PhotoAnalysisPresenterAction.SelectGridCell(2),
                    ),
                )
                surface.value = surface.value.copy(
                    closeLabel = "Close research",
                    onCloseRequested = { close.add("new close") },
                )
                onAction.value = {
                    events.add(it)
                    events.add(PhotoAnalysisPresenterAction.ScanAgain)
                }
            }
            onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).assertDoesNotExist()
            onNodeWithTag(UiAutomationIds.CAPTURE_CANCEL.value).performScrollTo().performClick()
            onNodeWithTag(UiAutomationIds.CAPTURE_CLOSE.value).performScrollTo().performClick()
            onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).performScrollTo().assertTextEquals("Recovery")
            assertEquals(
                listOf(
                    PhotoAnalysisPresenterAction.TakePhoto,
                    PhotoAnalysisPresenterAction.Cancel,
                    PhotoAnalysisPresenterAction.ScanAgain,
                ),
                events,
            )
            assertEquals(listOf("new close"), close)
        }

    private fun researchSurface(copy: PhotoAnalysisPresenterCopy, onGridCellSelected: (Int) -> Unit) =
        PhotoAnalysisCaptureSurface(
            copy,
            researchLayout = true,
            gridHintsEnabled = true,
            liveCamera = { Text("Research live camera") },
            onGridCellSelected = onGridCellSelected,
        )

    private fun researchReview(): PhotoAnalysisReviewModel {
        val fixture = requireNotNull(cameraReviewFixture().review)
        return fixture.copy(
            visibleComponents = fixture.visibleComponents.mapIndexed { index, component ->
                if (index == 0) {
                    component.copy(
                        region = PhotoAnalysisVisibleRegionModel(0.1, 0.2, 0.3, 0.2),
                        mass = PhotoAnalysisMassModel(PhotoAnalysisMassStatus.ESTIMATED, "120 g"),
                    )
                } else {
                    component
                }
            },
            compositionHints = listOf(
                PhotoAnalysisDishIngredientHintModel("rice", "Rice hint", "Dish hint", listOf("dish-1")),
            ),
            degradationLabel = "Partial suggestions",
        )
    }
}
