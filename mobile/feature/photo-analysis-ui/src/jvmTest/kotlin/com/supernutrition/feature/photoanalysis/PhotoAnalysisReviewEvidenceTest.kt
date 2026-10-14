package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class PhotoAnalysisReviewEvidenceTest {
    @Test
    fun `component only review explains unavailable dish suggestions`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val original = cameraReviewFixture()
        val message = "Dish suggestions are unavailable; review the visible foods"
        val review = requireNotNull(original.review).copy(mainDishProposals = emptyList(), degradationLabel = message)
        setContent {
            PhotoAnalysisCaptureScreen(original.copy(review = review), {}, surface())
        }
        onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun `familiar and unfamiliar foods retain all supplied evidence descriptions`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val original = cameraReviewFixture()
        val familiar = evidence("familiar", "Broccoli")
        val unfamiliar = evidence("unfamiliar", "Unfamiliar food")
        val review = requireNotNull(original.review).copy(visibleComponents = listOf(familiar, unfamiliar))
        setContent { PhotoAnalysisCaptureScreen(original.copy(review = review), {}, surface()) }
        listOf(unfamiliar, familiar).forEach { component ->
            onNodeWithTag(photoAnalysisVisibleComponentId(component.componentId).value)
                .assertContentDescriptionEquals(
                    listOfNotNull(
                        component.label,
                        component.sourceLabel,
                        component.confidenceLabel,
                        component.ambiguityLabel,
                        component.visibilityLabel,
                        component.regionLabel,
                        component.mass?.label,
                        component.provenanceLabel,
                    ).joinToString(),
                )
        }
    }

    @Test
    fun `resolved manually added food names retain host labels in evidence and confirmed draft`() =
        runSkikoComposeUiTest(
            size = Size(375f, 812f),
            density = Density(1f),
        ) {
            val copy = PhotoAnalysisPresenterCopyFixtures.localized()
            var confirmation: PhotoAnalysisMealReviewConfirmation? = null
            setContent {
                PhotoAnalysisCaptureScreen(
                    cameraReviewFixture(),
                    {},
                    surface = PhotoAnalysisCaptureSurface(
                        presenterCopy = copy,
                        manualIngredients = listOf(PhotoAnalysisManualIngredientModel("food:carrot", "Carota")),
                        onMealReviewConfirmed = { confirmation = it },
                    ),
                )
            }
            onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(1).value).performClick()
            onNodeWithTag(photoAnalysisVisibleComponentId("manual:food:carrot").value)
                .assertContentDescriptionEquals("Carota, ${copy.evidence.manualIngredientSource}")
            onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).performScrollTo().performClick()
            assertEquals(listOf("Carota"), assertNotNull(confirmation).addedIngredientLabels)
        }

    private fun evidence(id: String, label: String) = cameraComponentFixture(id, label).copy(
        sourceLabel = "Photo evidence",
        confidenceLabel = "Low confidence",
        ambiguityLabel = "Ambiguous",
        visibilityLabel = "Partly visible",
        regionLabel = "Region unknown",
        provenanceLabel = "Local model",
    )

    private fun surface() = PhotoAnalysisCaptureSurface(
        presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
    )
}
