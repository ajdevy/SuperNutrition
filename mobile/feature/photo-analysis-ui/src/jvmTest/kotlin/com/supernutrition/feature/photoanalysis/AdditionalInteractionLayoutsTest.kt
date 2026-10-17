package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Image as SkiaImage

@OptIn(ExperimentalTestApi::class)
class AdditionalInteractionLayoutsTest {
    @Test
    fun `large Home brand keeps complete words beside the profile action`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f, 2f),
    ) {
        setContent { HomeScreen(layoutHomeModel()) }
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText("Super Nutrition", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val lines = (0 until layout.lineCount).map {
            layout.layoutInput.text.text
                .substring(layout.getLineStart(it), layout.getLineEnd(it, visibleEnd = true))
                .replace('\u0131', 'i').trim()
        }
        assertTrue(lines.all { it in listOf("Super", "Nutrition", "Super Nutrition") }, "Brand words split: $lines")
        onNodeWithTag(UiAutomationIds.HOME_PROFILE.value).assertIsDisplayed()
    }

    @Test
    fun `large Home metrics keep their standard titles whole`() {
        listOf(Size(360f, 780f) to 2f, Size(280f, 580f) to 1f).forEach { (viewport, fontScale) ->
            runSkikoComposeUiTest(size = viewport, density = Density(1f, fontScale)) {
                setContent {
                    PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                        HomeScreen(layoutHomeModel())
                    }
                }
                listOf("Movement", "Fiber", "Water", "Protein").forEach { label ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText(label, useUnmergedTree = true).performScrollTo()
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    val lines = (0 until layout.lineCount).map {
                        label.substring(layout.getLineStart(it), layout.getLineEnd(it, visibleEnd = true)).trim()
                    }
                    assertEquals(listOf(label), lines, "Metric title splits its word: $label -> $lines")
                    assertFalse(layout.hasVisualOverflow, "Metric title is clipped: $label")
                }
                writeCaseScreenshot(
                    "home-metrics-${viewport.width.toInt()}-$fontScale",
                    captureToImage().asSkiaBitmap(),
                )
            }
        }
    }

    @Test
    fun `short recovery keeps its status outside the close touch target`() = runSkikoComposeUiTest(
        size = Size(320f, 240f),
        density = Density(1f, 2f),
    ) {
        val model = PhotoAnalysisPresenterModel(
            "Camera permission is needed to take a picture of your meal",
            PhotoAnalysisPresenterState.RECOVERY,
            listOf(PhotoAnalysisPresenterAction.ImportPhoto, PhotoAnalysisPresenterAction.OpenSettings),
        )
        setContent { PhotoAnalysisCaptureScreen(model, {}, surface()) }
        val close = onNodeWithTag(UiAutomationIds.CAPTURE_CLOSE.value).fetchSemanticsNode().boundsInRoot
        val status = onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertFalse(close.overlaps(status), "Close covers recovery status: close=$close status=$status")
        onNodeWithTag(UiAutomationIds.CAPTURE_OPEN_SETTINGS.value).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `home care actions keep symmetric touch targets with unequal large labels`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f, 2f),
    ) {
        setContent {
            HomeScreen(layoutHomeModel().copy(viewGoalsLabel = "View my nutrition goals"))
        }
        val add = onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).performScrollTo()
            .assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
        val goals = onNodeWithTag(UiAutomationIds.HOME_VIEW_GOALS.value)
            .assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
        assertTrue(
            abs(add.top - goals.top) <= 1f && abs(add.bottom - goals.bottom) <= 1f,
            "Care action heights differ: add=$add goals=$goals",
        )
        writeCaseScreenshot("home-care-large-text", captureToImage().asSkiaBitmap())
    }

    @Test
    fun `home care actions have adequate touch targets at normal text size`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { HomeScreen(layoutHomeModel()) }
        onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).assertHeightIsAtLeast(48.dp)
        onNodeWithTag(UiAutomationIds.HOME_VIEW_GOALS.value).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `goals dialog and its dismissal survive saved state restoration`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        var registry = SaveableStateRegistry(null) { true }
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                HomeScreen(layoutHomeModel())
            }
        }
        setContent(content)
        onNodeWithTag(UiAutomationIds.HOME_VIEW_GOALS.value).performClick()
        val openState = runOnIdle { registry.performSave() }
        setContent { }
        registry = SaveableStateRegistry(openState) { true }
        setContent(content)
        onNodeWithText("Your goals").assertIsDisplayed()
        onNodeWithText("Done").performClick()
        val dismissedState = runOnIdle { registry.performSave() }
        setContent { }
        registry = SaveableStateRegistry(dismissedState) { true }
        setContent(content)
        onNodeWithText("Your goals").assertDoesNotExist()
    }

    @Test
    fun `three long dish names remain fully measured at double text size`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f, 2f),
    ) {
        val original = cameraReviewFixture()
        val review = requireNotNull(original.review)
        val dishes = review.mainDishProposals.mapIndexed { index, dish ->
            dish.copy(label = "Homemade salmon, vegetables and whole grain rice bowl ${index + 1}")
        }
        setContent {
            PhotoAnalysisCaptureScreen(original.copy(review = review.copy(mainDishProposals = dishes)), {}, surface())
        }
        writeCaseScreenshot("review-large-text", captureToImage().asSkiaBitmap())
        dishes.forEach { dish ->
            onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(dish.rank).value).performScrollTo().performClick()
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(dish.label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertFalse(layouts.single().hasVisualOverflow, "Dish label is clipped: ${dish.label}")
        }
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).assertIsDisplayed()
    }

    @Test
    fun `landscape main screens retain reachable content at double text size`() = runSkikoComposeUiTest(
        size = Size(812f, 375f),
        density = Density(1f, 2f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) { HomeScreen(layoutHomeModel()) }
        }
        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        val action = onNodeWithText("Do it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(action.bottom < camera.top, "Home action overlaps navigation: action=$action camera=$camera")
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(1), layoutBottomBarActions()) {
                RewardsScreen(layoutRewardsModel())
            }
        }
        onNodeWithTag(UiAutomationIds.REWARDS_LIST.value).performScrollToIndex(7)
        val reward = onNodeWithTag(UiAutomationIds.rewardsRowState(6).value)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(reward.bottom < camera.top, "Last reward overlaps navigation: reward=$reward camera=$camera")
    }

    @Test
    fun `manual review rejects whitespace and preserves explicit unicode correction`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val input = mutableStateOf("")
        var confirmed: PhotoAnalysisMealReviewConfirmation? = null
        setContent {
            PhotoAnalysisCaptureScreen(
                cameraReviewFixture(),
                {},
                surface().copy(
                    manualIngredientInput = input.value,
                    onManualIngredientInputChanged = { input.value = it },
                    onMealReviewConfirmed = { confirmed = it },
                ),
            )
        }
        val field = onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_MANUAL_INGREDIENT_INPUT.value)
        field.assertIsDisplayed().performTextInput("  \t  ")
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value).assertIsDisplayed().assertIsNotEnabled()
        val correction = "Овсянка 🥣 — без молока"
        field.assertIsDisplayed().performTextReplacement(correction)
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value)
            .assertIsDisplayed().assertIsEnabled().performClick()
        val value = assertNotNull(confirmed)
        assertEquals(correction, value.note)
        assertEquals(null, value.dishCandidateId)
        assertTrue(value.photoEvidenceComponentIds.isEmpty() && value.addedIngredientLabels.isEmpty())
    }

    @Test
    fun `right to left navigation mirrors slots and keeps their correct actions`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        var homeClicks = 0
        var rewardsClicks = 0
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                PhotoAnalysisMainBottomBar(
                    layoutBottomBarState(),
                    PhotoAnalysisMainBottomBarActions({ homeClicks++ }, { rewardsClicks++ }, {}),
                )
            }
        }
        val home = onNodeWithTag(UiAutomationIds.HOME_TAB.value)
        val rewards = onNodeWithTag(UiAutomationIds.REWARDS_TAB.value)
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(home.fetchSemanticsNode().boundsInRoot.center.x > camera.center.x)
        assertTrue(rewards.fetchSemanticsNode().boundsInRoot.center.x < camera.center.x)
        home.performClick()
        rewards.performClick()
        assertEquals(1, homeClicks)
        assertEquals(1, rewardsClicks)
    }

    private fun writeCaseScreenshot(name: String, bitmap: org.jetbrains.skia.Bitmap) {
        val destination = File("build/reports/additional-interactions/$name.png")
        destination.parentFile.mkdirs()
        SkiaImage.makeFromBitmap(bitmap).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { destination.writeBytes(it.bytes) }
        }
    }

    private fun surface() = PhotoAnalysisCaptureSurface(presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized())
}
