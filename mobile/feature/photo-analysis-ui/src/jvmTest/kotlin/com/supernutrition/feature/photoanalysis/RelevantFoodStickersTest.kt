package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import com.supernutrition.domain.inference.ConfidenceBand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class RelevantFoodStickersTest {
    @Test
    fun `standalone reanalysis clears stale photo selections before showing different evidence`() =
        runSkikoComposeUiTest(size = Size(375f, 812f), density = Density(1f)) {
            val model = androidx.compose.runtime.mutableStateOf(cameraReviewFixture())
            setContent {
                PhotoAnalysisCaptureScreen(
                    model.value,
                    {},
                    PhotoAnalysisCaptureSurface(presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized()),
                )
            }
            listOf(PhotoAnalysisPresenterState.READY, PhotoAnalysisPresenterState.ANALYZING).forEach { phase ->
                runOnIdle { model.value = cameraReviewFixture() }
                onNodeWithTag(photoAnalysisVisibleComponentId("photo-salmon").value).performClick()
                onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).assertIsEnabled()
                runOnIdle { model.value = PhotoAnalysisPresenterModel("Analyzing", phase, emptyList()) }
                waitForIdle()
                runOnIdle {
                    val fresh = cameraReviewFixture()
                    model.value = fresh.copy(
                        review = assertNotNull(fresh.review).copy(
                            visibleComponents = listOf(cameraComponentFixture("new-photo-banana", "Banana")),
                        ),
                    )
                }
                onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).assertIsNotEnabled()
            }
        }

    @Test
    fun `selected original artwork has an explicit visible checkmark in addition to its outline`() =
        runSkikoComposeUiTest(size = Size(375f, 812f), density = Density(1f)) {
            val copy = PhotoAnalysisPresenterCopyFixtures.localized()
            setContent {
                PhotoAnalysisCaptureScreen(
                    cameraReviewFixture(),
                    {},
                    PhotoAnalysisCaptureSurface(presenterCopy = copy),
                )
            }
            listOf("salmon", "broccoli", "avocado", "leafy-greens").forEach { food ->
                val id = photoAnalysisVisibleComponentId("photo-$food").value
                onNodeWithTag(id).performScrollTo().performClick().assertIsSelected()
                onNode(
                    hasContentDescription(copy.evidence.selected) and hasAnyAncestor(hasTestTag(id)),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
            }
        }

    @Test
    fun `all shipped local visible food labels have truthful matching illustrations`() {
        listOf("apple", "banana", "broccoli", "carrot", "orange").forEach { label ->
            assertEquals(label, assertNotNull(foodStickerFor(label), "No illustration for $label").id)
        }
        listOf("banana bread", "pineapple juice", "orange juice", "carrot cake", "unknown food").forEach { label ->
            assertNull(foodStickerFor(label), "Do not invent an illustration for $label")
        }
    }

    @Test
    fun `food illustration lookup requires the complete identity in English and Russian`() {
        val actualFoods = mapOf(
            " TOMATO " to "tomato",
            "помидоры" to "tomato",
            "Курица" to "chicken",
            "pineapples" to "pineapple",
            "АНАНАС" to "pineapple",
            "nuts" to "nuts",
            "орехи" to "nuts",
            "рыба" to "fish",
            "молоко" to "milk",
            "strawberries" to "strawberry",
            "чечевица" to "lentils",
        )
        actualFoods.forEach { (label, identity) ->
            assertEquals(identity, assertNotNull(foodStickerFor(label)).id)
        }
        listOf(
            "tomato soup",
            "chicken pie",
            "pineapple juice",
            "nut butter",
            "суп из помидоров",
            "куриный пирог",
            "ананасовый сок",
            "ореховая паста",
            "каша на овсяном молоке",
            "oat milk porridge",
            "unidentified food",
        ).forEach { label ->
            assertNull(foodStickerFor(label), "A compound or unknown food must retain its actual text: $label")
        }
    }

    @Test
    fun `banana review shows only actual photo choices and respects supplied selection`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val component = cameraComponentFixture("banana-in-photo", "Banana")
        val original = cameraReviewFixture()
        val review = requireNotNull(original.review).copy(visibleComponents = listOf(component))
        var selection: PhotoAnalysisMealReviewSelection? = null
        setContent {
            PhotoAnalysisCaptureScreen(
                original.copy(review = review),
                {},
                PhotoAnalysisCaptureSurface(
                    presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                    mealReviewSelection = PhotoAnalysisMealReviewSelection(componentIds = setOf(component.componentId)),
                    onMealReviewSelectionChanged = { selection = it },
                ),
            )
        }
        onNodeWithTag(photoAnalysisVisibleComponentId(component.componentId).value)
            .performScrollTo().assertIsSelected().performClick()
        assertEquals(emptySet(), assertNotNull(selection).componentIds)
        listOf("salmon", "broccoli", "avocado", "carrot", "quinoa", "salt").forEach { unrelated ->
            onNodeWithTag(UiAutomationIds.photoAnalysisPantryChoice(unrelated).value).assertDoesNotExist()
        }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_ADD.value)
            .performScrollTo().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `unknown detected food retains an adequate selectable text target`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f, fontScale = 1.5f),
    ) {
        val component = cameraComponentFixture("not-in-illustrations", "Homemade banana bread")
        val original = cameraReviewFixture()
        val review = requireNotNull(original.review).copy(visibleComponents = listOf(component))
        setContent {
            PhotoAnalysisCaptureScreen(
                original.copy(review = review),
                {},
                PhotoAnalysisCaptureSurface(presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized()),
            )
        }
        onNodeWithTag(photoAnalysisVisibleComponentId(component.componentId).value)
            .performScrollTo().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .performClick().assertIsSelected()
    }

    @Test
    fun `only supported dish suggestions show while a selected low confidence dish remains correctable`() =
        runSkikoComposeUiTest(size = Size(375f, 812f), density = Density(1f)) {
            val original = cameraReviewFixture()
            val review = requireNotNull(original.review)
            val proposals = review.mainDishProposals.mapIndexed { index, dish ->
                dish.copy(confidenceBand = if (index == 2) ConfidenceBand.MEDIUM else ConfidenceBand.LOW)
            }
            var changedSelection: PhotoAnalysisMealReviewSelection? = null
            setContent {
                PhotoAnalysisCaptureScreen(
                    original.copy(review = review.copy(mainDishProposals = proposals)),
                    {},
                    PhotoAnalysisCaptureSurface(
                        presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                        mealReviewSelection = PhotoAnalysisMealReviewSelection(dishId = proposals[1].candidateId),
                        onMealReviewSelectionChanged = { changedSelection = it },
                    ),
                )
            }
            onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(1).value).assertDoesNotExist()
            onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(2).value).assertIsSelected()
            onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(3).value).assertIsNotSelected().performClick()
            assertEquals(proposals[2].candidateId, assertNotNull(changedSelection).dishId)
        }

    @Test
    fun `selected banana confirms as photo evidence without selecting a dish or custom meal`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val original = cameraReviewFixture()
        val component = cameraComponentFixture("banana-only", "Banana")
        val review = requireNotNull(original.review).copy(
            mainDishProposals = emptyList(),
            visibleComponents = listOf(component),
        )
        var confirmation: PhotoAnalysisMealReviewConfirmation? = null
        setContent {
            PhotoAnalysisCaptureScreen(
                original.copy(review = review),
                {},
                PhotoAnalysisCaptureSurface(
                    presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                    onMealReviewConfirmed = { confirmation = it },
                ),
            )
        }
        onNodeWithTag(photoAnalysisVisibleComponentId(component.componentId).value).performClick().assertIsSelected()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_CUSTOM_MEAL.value).assertIsNotSelected()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value)
            .assertIsDisplayed().assertIsEnabled().performClick()
        val actual = assertNotNull(confirmation)
        assertEquals(listOf(component.componentId), actual.photoEvidenceComponentIds)
        assertNull(actual.dishCandidateId)
        assertEquals(emptyList(), actual.addedIngredientLabels)
    }
}
