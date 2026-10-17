package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Image as SkiaImage

@OptIn(ExperimentalTestApi::class)
class PhotoAnalysisLayoutVisualTest {
    @Test
    fun `home mascot feet stay in front of the rhythm panel across viewport sizes`() {
        listOf(
            Size(375f, 812f) to 1f,
            Size(375f, 932f) to 1f,
            Size(320f, 480f) to 1f,
            Size(320f, 568f) to 1.5f,
            Size(600f, 800f) to 1f,
        ).forEach { (viewport, fontScale) ->
            runSkikoComposeUiTest(size = viewport, density = Density(1f, fontScale)) {
                setContent {
                    PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                        HomeScreen(layoutHomeModel())
                    }
                }
                val mascot = onNodeWithContentDescription("Capybara mascot")
                    .performScrollTo()
                    .fetchSemanticsNode().boundsInRoot
                val panel = onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value)
                    .fetchSemanticsNode().boundsInRoot
                // Preserve the measured overlap while keeping the feet in front.
                val designOverlap = mascot.height * 16.2f / 182.8f
                assertTrue(abs(mascot.center.x - panel.center.x) <= 1f)
                assertTrue(
                    abs(mascot.bottom - panel.top - designOverlap) <= 1f,
                    "Mascot floats above its panel at $viewport/$fontScale: mascot=$mascot panel=$panel",
                )
                val rendered = captureToImage()
                assertFeetInFront(rendered, mascot, panel)
                writeLayoutScreenshot(
                    "home-mascot-${viewport.width.toInt()}x${viewport.height.toInt()}-$fontScale",
                    rendered.asSkiaBitmap(),
                )
            }
        }
    }

    private fun assertFeetInFront(image: ImageBitmap, mascot: Rect, panel: Rect) {
        val pixels = image.toPixelMap()
        // Opaque paw pixels near x=430/830, y=1151 in the original 1254px asset.
        val row = floor(mascot.top + mascot.height * 1151f / 1254f).toInt()
        assertTrue(row >= ceil(panel.top).toInt(), "Paw probes must hang over the panel edge")
        listOf(430f, 830f).forEach { sourceX ->
            val column = (mascot.center.x + mascot.height * (sourceX / 1254f - 0.5f)).toInt()
            assertNotEquals(
                Color(0xFFFAF6F4),
                pixels[column, row],
                "The rhythm panel cuts off the paw at ($column, $row)",
            )
        }
    }

    @Test
    fun `home keeps equal card gutters on a tall phone`() = runSkikoComposeUiTest(
        size = Size(375f, 932f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                HomeScreen(layoutHomeModel())
            }
        }
        val card = onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(card.left - (375f - card.right)) <= 1f, "Home card gutters were $card")
        assertTrue(card.left >= 20f, "Home card was cropped: $card")
        writeLayoutScreenshot("home-tall", captureToImage().asSkiaBitmap())
    }

    @Test
    fun `home lower content is scrollable on a short phone`() = runSkikoComposeUiTest(
        size = Size(320f, 480f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                HomeScreen(layoutHomeModel())
            }
        }
        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        onNodeWithTag(UiAutomationIds.HOME_NEXT_STEP.value).assertIsDisplayed()
        onNodeWithText("Do it").assertIsDisplayed()
        onNodeWithText("Add one leafy green to dinner").assertIsDisplayed()
        val action = onNodeWithText("Do it").fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(action.bottom < camera.top, "Home action overlaps navigation: $action and $camera")
        writeLayoutScreenshot("home-short-scrolled", captureToImage().asSkiaBitmap())
    }

    @Test
    fun `navigation tabs mirror each other around the camera`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { PhotoAnalysisMainBottomBar(layoutBottomBarState(), layoutBottomBarActions()) }
        val home = onNodeWithTag(UiAutomationIds.HOME_TAB.value).fetchSemanticsNode().boundsInRoot
        val rewards = onNodeWithTag(UiAutomationIds.REWARDS_TAB.value).fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(camera.center.x - 187.5f) <= 1f, "Camera was off center: $camera")
        assertTrue(
            abs((camera.center.x - home.center.x) - (rewards.center.x - camera.center.x)) <= 1f,
            "Tab distances differ: home=$home camera=$camera rewards=$rewards",
        )
        assertTrue(abs(home.top - rewards.top) <= 1f)
    }

    @Test
    fun `navigation labels keep clearance inside the pill with larger text`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f, fontScale = 1.5f),
    ) {
        setContent { PhotoAnalysisMainBottomBar(layoutBottomBarState(), layoutBottomBarActions()) }
        val pill = onNodeWithTag(UiAutomationIds.BOTTOM_BAR_PILL.value).fetchSemanticsNode().boundsInRoot
        // The 1388x344 export's flat pill fill spans rows 45..266 at both tab centers.
        val bodyTop = pill.top + pill.height * 45f / 344f
        val bodyBottom = pill.top + pill.height * 267f / 344f
        listOf("Home", "Rewards").forEach { label ->
            val bounds = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(
                bounds.top >= bodyTop + 4f && bounds.bottom <= bodyBottom - 4f,
                "$label label has insufficient clearance inside the pill: label=$bounds body=$bodyTop..$bodyBottom",
            )
        }
        writeLayoutScreenshot("navigation-large-text", captureToImage().asSkiaBitmap())
    }

    @Test
    fun `regular home keeps its next step above navigation`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                HomeScreen(layoutHomeModel())
            }
        }
        onNodeWithText("Add one leafy green to dinner").assertIsDisplayed()
        val action = onNodeWithText("Do it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(action.bottom < camera.top, "Home action overlaps navigation: $action and $camera")
        val rhythm = onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).fetchSemanticsNode().boundsInRoot
        val nextStep = onNodeWithTag(UiAutomationIds.HOME_NEXT_STEP.value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(rhythm.left - nextStep.left) <= 1f && abs(rhythm.right - nextStep.right) <= 1f)
        writeLayoutScreenshot("home", captureToImage().asSkiaBitmap())
    }

    @Test
    fun `rewards cards and title share symmetric gutters`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(1), layoutBottomBarActions()) {
                RewardsScreen(layoutRewardsModel())
            }
        }
        val title = onNodeWithTag(UiAutomationIds.REWARDS_TITLE.value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val card = onNodeWithTag(UiAutomationIds.rewardsRow(0).value).fetchSemanticsNode().boundsInRoot
        val pairedCard = onNodeWithTag(UiAutomationIds.rewardsRow(1).value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(card.left - (375f - pairedCard.right)) <= 1f, "Rewards gutters were $card and $pairedCard")
        assertTrue(abs(card.width - pairedCard.width) <= 1f)
        assertTrue(abs(card.top - pairedCard.top) <= 1f && abs(card.bottom - pairedCard.bottom) <= 1f)
        assertTrue(abs(card.left - title.left) <= 1f, "Title and card do not align: $title and $card")
        assertTrue(abs(card.left - 24f) <= 1f, "Rewards differs from the Home gutter: $card")
        val rendered = captureToImage()
        val pixels = rendered.toPixelMap()
        assertEquals(
            pixels[2, 700],
            pixels[2, 740],
            "Rewards paints a separate background strip behind navigation",
        )
        writeLayoutScreenshot("rewards", rendered.asSkiaBitmap())
    }

    @Test
    fun `final reward clears navigation on a short phone`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(1), layoutBottomBarActions()) {
                RewardsScreen(layoutRewardsModel())
            }
        }
        onNodeWithTag(UiAutomationIds.REWARDS_LIST.value).performScrollToIndex(4)
        val finalCard = onNodeWithTag(UiAutomationIds.rewardsRow(6).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(finalCard.bottom < camera.top, "Final reward overlaps navigation: $finalCard and $camera")
        assertTrue(abs(finalCard.left - (320f - finalCard.right)) <= 1f, "The final odd reward is not balanced")
        onNodeWithTag(UiAutomationIds.rewardsRowState(6).value).assertIsDisplayed()
        writeLayoutScreenshot("rewards-short-scrolled", captureToImage().asSkiaBitmap())
    }

    @Test
    fun `larger Home text flows without hiding actions`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f, fontScale = 1.5f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                HomeScreen(layoutHomeModel())
            }
        }
        onNodeWithTag(UiAutomationIds.HOME_VIEW_GOALS.value).assertIsDisplayed()
        writeLayoutScreenshot("home-large-text", captureToImage().asSkiaBitmap())
        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        onNodeWithText("Your next helpful step").assertIsDisplayed()
        onNodeWithText("Add one leafy green to dinner").assertIsDisplayed()
        val action = onNodeWithText("Do it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(action.bottom < camera.top)
        writeLayoutScreenshot("home-large-text-scrolled", captureToImage().asSkiaBitmap())
    }

    @Test
    fun `metric cards stay aligned with wrapped values and larger text`() {
        val longValue = "32 / 45 minutes of movement today"
        listOf(1f, 1.5f).forEach { fontScale ->
            runSkikoComposeUiTest(size = Size(320f, 720f), density = Density(1f, fontScale)) {
                setContent {
                    PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                        HomeScreen(layoutHomeModel().copy(movement = HomeMetricModel("Movement", longValue)))
                    }
                }
                onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).performScrollTo()
                val bounds = listOf(
                    UiAutomationIds.HOME_METRIC_FIBER,
                    UiAutomationIds.HOME_METRIC_WATER,
                    UiAutomationIds.HOME_METRIC_PROTEIN,
                    UiAutomationIds.HOME_METRIC_MOVEMENT,
                ).map { onNodeWithTag(it.value).fetchSemanticsNode().boundsInRoot }
                onNodeWithText(longValue).assertIsDisplayed()
                if (fontScale == 1f) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText(longValue).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                        it(layouts)
                    }
                    assertTrue(layouts.single().lineCount > 1, "The value must wrap to exercise row sizing")
                    bounds.chunked(2).forEach { (left, right) ->
                        assertTrue(abs(left.top - right.top) <= 1f && abs(left.bottom - right.bottom) <= 1f)
                        assertTrue(left.right < right.left, "Metric cards overlap: $left $right")
                    }
                } else {
                    bounds.zipWithNext().forEach { (above, below) ->
                        assertTrue(above.bottom < below.top, "Large-text metric cards overlap: $above $below")
                        assertTrue(abs(above.left - below.left) <= 1f && abs(above.right - below.right) <= 1f)
                    }
                }
                writeLayoutScreenshot("home-metrics-aligned-$fontScale", captureToImage().asSkiaBitmap())
            }
        }
    }

    @Test
    fun `wide layout centers content and navigation`() = runSkikoComposeUiTest(
        size = Size(600f, 800f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                HomeScreen(layoutHomeModel())
            }
        }
        val card = onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).fetchSemanticsNode().boundsInRoot
        val pill = onNodeWithTag(UiAutomationIds.BOTTOM_BAR_PILL.value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(card.center.x - 300f) <= 1f, "Wide Home was not centered: $card")
        assertTrue(abs(pill.center.x - 300f) <= 1f, "Wide navigation was not centered: $pill")
        assertTrue(card.width <= 430f, "Wide Home card was stretched: $card")
        assertTrue(pill.width <= 430f, "Wide navigation was stretched: $pill")
        writeLayoutScreenshot("home-wide", captureToImage().asSkiaBitmap())
    }

    @Test
    fun `long reward copy remains readable with larger text`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f, fontScale = 1.5f),
    ) {
        val model = layoutRewardsModel().let { rewards ->
            rewards.copy(
                cards = rewards.cards.map { card ->
                    card.copy(
                        title = "A little care for your daily rhythm",
                        description = "Every helpful choice you record is another step " +
                            "toward a routine that works for you.",
                    )
                },
            )
        }
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(1), layoutBottomBarActions()) {
                RewardsScreen(model)
            }
        }
        onNodeWithTag(UiAutomationIds.REWARDS_LIST.value).performScrollToIndex(7)
        val title = onNodeWithTag(UiAutomationIds.rewardsRowTitle(6).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val progress = onNodeWithTag(UiAutomationIds.rewardsRowProgress(6).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val state = onNodeWithTag(UiAutomationIds.rewardsRowState(6).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(title.bottom < progress.top && progress.bottom < state.top)
        assertTrue(state.bottom < camera.top)
        writeLayoutScreenshot("rewards-large-text-scrolled", captureToImage().asSkiaBitmap())
    }

    @Test
    fun `system padding reserves content while the backdrop fills the viewport`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { HomeScreenBackground() }
        val reference = captureToImage().toPixelMap()
        setContent {
            PhotoAnalysisMainLayout(
                state = layoutBottomBarState(),
                actions = layoutBottomBarActions(),
                contentPadding = PaddingValues(top = 24.dp, bottom = 20.dp),
            ) {
                HomeScreen(layoutHomeModel())
            }
        }
        val content = onNodeWithTag(UiAutomationIds.HOME_SCREEN.value).fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(content.top - 24f) <= 1f, "Top inset applied incorrectly: $content")
        assertTrue(
            abs(content.bottom - 792f) <= 1f,
            "Home content must reach the bottom safe edge beneath navigation: $content and $camera",
        )
        onNodeWithTag(UiAutomationIds.HOME_NEXT_STEP.value).performScrollTo()
        val nextStep = onNodeWithTag(UiAutomationIds.HOME_NEXT_STEP.value).fetchSemanticsNode().boundsInRoot
        assertTrue(nextStep.bottom < camera.top)
        val rendered = captureToImage()
        assertEquals(reference[187, 8], rendered.toPixelMap()[187, 8])
        assertEquals(reference[187, 805], rendered.toPixelMap()[187, 805])
        writeLayoutScreenshot("home-with-insets", rendered.asSkiaBitmap())
    }

    @Test
    fun `metric bars retain their relative fill when the viewport narrows`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { HomeScreen(layoutHomeModel()) }
        val regularCard = onNodeWithTag(UiAutomationIds.HOME_METRIC_FIBER.value)
            .fetchSemanticsNode().boundsInRoot
        val regularFill = progressFill(captureToImage(), regularCard, Color(0xFF7CB342))
        setContent {
            Box(Modifier.requiredSize(320.dp, 812.dp)) { HomeScreen(layoutHomeModel()) }
        }
        val narrowCard = onNodeWithTag(UiAutomationIds.HOME_METRIC_FIBER.value)
            .fetchSemanticsNode().boundsInRoot
        val narrowFill = progressFill(captureToImage(), narrowCard, Color(0xFF7CB342))
        assertTrue(
            abs(regularFill - narrowFill) <= 0.03f,
            "Same metric changes its visible progress with width: regular=$regularFill narrow=$narrowFill",
        )
    }

    private fun progressFill(image: ImageBitmap, card: Rect, accent: Color): Float {
        val pixels = image.toPixelMap()
        val accentArgb = accent.toArgb()
        val trackArgb = Color(0xFFE7E4E0).toArgb()
        val columns = floor(card.left).toInt() until ceil(card.right).toInt()
        for (row in floor(card.top).toInt() until ceil(card.bottom).toInt()) {
            val filled = columns.count { pixels[it, row].toArgb() == accentArgb }
            val remaining = columns.count { pixels[it, row].toArgb() == trackArgb }
            if (filled + remaining > card.width / 2f) return filled.toFloat() / (filled + remaining)
        }
        error("No progress track rendered inside $card")
    }

    private fun writeLayoutScreenshot(name: String, bitmap: org.jetbrains.skia.Bitmap) {
        val destination = File("build/reports/layout-design/$name.png")
        destination.parentFile.mkdirs()
        SkiaImage.makeFromBitmap(bitmap).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> destination.writeBytes(data.bytes) }
        }
    }
}
