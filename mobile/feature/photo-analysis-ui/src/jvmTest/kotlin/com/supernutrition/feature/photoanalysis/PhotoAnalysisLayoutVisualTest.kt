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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.RewardId
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Image as SkiaImage

@OptIn(ExperimentalTestApi::class)
class PhotoAnalysisLayoutVisualTest {
    @Test
    fun `home mascot stays visible and separated from the metrics across viewport sizes`() {
        listOf(
            Size(375f, 812f) to 1f,
            Size(375f, 932f) to 1f,
            Size(320f, 480f) to 1f,
            Size(320f, 568f) to 1.5f,
            Size(600f, 800f) to 1f,
        ).forEach { (viewport, fontScale) ->
            runSkikoComposeUiTest(size = viewport, density = Density(1f, fontScale)) {
                setContent { HomeScreenBackground() }
                val backdrop = captureToImage()
                setContent {
                    PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                        HomeScreen(layoutHomeModel())
                    }
                }
                val mascotNode = onNodeWithContentDescription("Capybara mascot").performScrollTo()
                val mascot = mascotNode.getUnclippedBoundsInRoot()
                val panel = onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value)
                    .getUnclippedBoundsInRoot()
                assertTrue(
                    abs((mascot.left.value + mascot.right.value - panel.left.value - panel.right.value) / 2f) <= 1f,
                )
                assertTrue(
                    abs(panel.top.value - mascot.bottom.value - 18.5f) <= 1f,
                    "Mascot and metrics lose the design spacing at $viewport/$fontScale: mascot=$mascot panel=$panel",
                )
                val rendered = captureToImage()
                assertMascotVisible(rendered, backdrop, mascotNode.fetchSemanticsNode().boundsInRoot)
                writeLayoutScreenshot(
                    "home-mascot-${viewport.width.toInt()}x${viewport.height.toInt()}-$fontScale",
                    rendered.asSkiaBitmap(),
                )
            }
        }
    }

    private fun assertMascotVisible(image: ImageBitmap, backdrop: ImageBitmap, mascot: Rect) {
        val pixels = image.toPixelMap()
        val background = backdrop.toPixelMap()
        val changed = (ceil(mascot.top).toInt() until floor(mascot.bottom).toInt()).sumOf { row ->
            (ceil(mascot.left).toInt() until floor(mascot.right).toInt()).count { column ->
                pixels[column, row] != background[column, row]
            }
        }
        assertTrue(changed > 100, "Mascot artwork is missing from its measured slot: $mascot")
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
        assertTrue(abs(card.left - 16f) <= 1f, "Home card differs from the 16dp Figma gutter: $card")
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
        val metric = onNodeWithTag(UiAutomationIds.HOME_METRIC_FATS.value).assertIsDisplayed()
        val action = metric.fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(action.bottom < camera.top, "Home action overlaps navigation: $action and $camera")
        writeLayoutScreenshot("home-short-scrolled", captureToImage().asSkiaBitmap())
        metric.performClick()
        onNodeWithText("Your goals").assertIsDisplayed()
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
        val bodyTop = pill.top
        val bodyBottom = pill.bottom
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
    fun `regular home keeps its metrics above navigation with aligned gutters`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                HomeScreen(layoutHomeModel())
            }
        }
        val action = onNodeWithTag(
            UiAutomationIds.HOME_METRIC_FATS.value,
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(action.bottom < camera.top, "Home action overlaps navigation: $action and $camera")
        val rhythm = onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).fetchSemanticsNode().boundsInRoot
        val header = onNodeWithTag(UiAutomationIds.HOME_HEADER.value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(rhythm.left - header.left) <= 1f && abs(rhythm.right - header.right) <= 1f)
        onNodeWithTag(UiAutomationIds.HOME_NEXT_STEP.value).assertDoesNotExist()
        writeLayoutScreenshot("home", captureToImage().asSkiaBitmap())
    }

    @Test
    fun `rewards header and actual cards keep the figma carousel geometry`() = runSkikoComposeUiTest(
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
        val section = onNodeWithTag(UiAutomationIds.REWARDS_EARNED_SECTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(title.center.x - 187.5f) <= 1f, "Rewards title is not centered: $title")
        assertTrue(abs(pairedCard.left - card.right - 10f) <= 1f, "Rewards card spacing was $card and $pairedCard")
        assertTrue(abs(card.width - pairedCard.width) <= 1f)
        assertTrue(abs(card.width - 150f) <= 1f, "Rewards card differs from the 150dp Figma width: $card")
        assertTrue(abs(card.top - pairedCard.top) <= 1f && abs(card.bottom - pairedCard.bottom) <= 1f)
        assertTrue(abs(card.left - section.left) <= 1f, "Section and card do not align: $section and $card")
        assertTrue(abs(card.left - 16f) <= 1f, "Rewards differs from the Figma gutter: $card")
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
        onNodeWithTag(UiAutomationIds.rewardsRow(6).value).performScrollTo()
        onNodeWithTag(UiAutomationIds.REWARDS_SCREEN.value).performRewardsScrollToEnd()
        val finalCard = onNodeWithTag(UiAutomationIds.rewardsRow(6).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(finalCard.bottom < camera.top, "Final reward overlaps navigation: $finalCard and $camera")
        val pairedFinalCard = onNodeWithTag(UiAutomationIds.rewardsRow(5).value).getUnclippedBoundsInRoot()
        assertTrue(abs(finalCard.top - pairedFinalCard.top.value) <= 1f, "Weekly reward carousel is not aligned")
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
        onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).assertIsDisplayed()
        onNodeWithText(layoutHomeModel().careQuestion, useUnmergedTree = true).assertReadableHomeText(1.5f)
        writeLayoutScreenshot("home-large-text", captureToImage().asSkiaBitmap())
        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        val metric = onNodeWithTag(UiAutomationIds.HOME_METRIC_FATS.value).assertIsDisplayed()
        val action = metric.fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(action.bottom < camera.top)
        writeLayoutScreenshot("home-large-text-scrolled", captureToImage().asSkiaBitmap())
        metric.performClick()
        onNodeWithText("Your goals").assertIsDisplayed()
    }

    @Test
    fun `metric rows retain equal card heights when values wrap`() = runSkikoComposeUiTest(
        size = Size(320f, 568f),
        density = Density(1f),
    ) {
        val fatsValue = "32 / 45 grams of healthy fats today"
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                HomeScreen(layoutHomeModel().copy(fats = HomeMetricModel("Fats", fatsValue)))
            }
        }
        onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).performScrollTo()
        val fiber = onNodeWithTag(UiAutomationIds.HOME_METRIC_FIBER.value).fetchSemanticsNode().boundsInRoot
        val colors = onNodeWithTag(UiAutomationIds.HOME_METRIC_COLORS.value).fetchSemanticsNode().boundsInRoot
        val protein = onNodeWithTag(UiAutomationIds.HOME_METRIC_PROTEIN.value).fetchSemanticsNode().boundsInRoot
        val fats = onNodeWithTag(UiAutomationIds.HOME_METRIC_FATS.value).fetchSemanticsNode().boundsInRoot
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(fatsValue, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().lineCount > 1, "The value must wrap to exercise equal row heights")
        writeLayoutScreenshot("home-metrics-wrapped-row", captureToImage().asSkiaBitmap())
        assertTrue(abs(fiber.top - colors.top) <= 1f && abs(fiber.bottom - colors.bottom) <= 1f)
        assertTrue(
            abs(protein.top - fats.top) <= 1f && abs(protein.bottom - fats.bottom) <= 1f,
            "Wrapped metric cards have different heights: protein=$protein fats=$fats",
        )
    }

    @Test
    fun `metric cards stay aligned with wrapped values and larger text`() {
        val longValue = "32 / 45 grams of healthy fats today"
        listOf(1f, 1.5f).forEach { fontScale ->
            runSkikoComposeUiTest(size = Size(320f, 720f), density = Density(1f, fontScale)) {
                setContent {
                    PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                        HomeScreen(layoutHomeModel().copy(fats = HomeMetricModel("Fats", longValue)))
                    }
                }
                onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).performScrollTo()
                val bounds = listOf(
                    UiAutomationIds.HOME_METRIC_FIBER,
                    UiAutomationIds.HOME_METRIC_COLORS,
                    UiAutomationIds.HOME_METRIC_PROTEIN,
                    UiAutomationIds.HOME_METRIC_FATS,
                ).map { onNodeWithTag(it.value).fetchSemanticsNode().boundsInRoot }
                val value = onNodeWithText(longValue, useUnmergedTree = true).assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                value.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val text = layouts.single()
                val valueBounds = value.fetchSemanticsNode().boundsInRoot
                assertTrue(text.lineCount > 1, "The value must wrap to exercise row sizing")
                assertTrue(text.multiParagraph.height <= text.size.height + 1f, "The wrapped value is clipped")
                assertTrue(valueBounds.height >= text.size.height - 1f, "The visible value is clipped")
                assertTrue(!text.multiParagraph.didExceedMaxLines, "The wrapped value loses lines")
                assertEquals(longValue.length, text.getLineEnd(text.lineCount - 1), "The value is incomplete")
                assertEquals(fontScale, text.layoutInput.density.fontScale, "The value overrides the text scale")
                repeat(text.lineCount) { line ->
                    assertTrue(!text.isLineEllipsized(line), "The wrapped value is truncated")
                    assertTrue(text.getLineRight(line) - text.getLineLeft(line) <= text.size.width + 1f)
                }
                val columns = if (abs(bounds[0].top - bounds[1].top) <= 1f) 2 else 1
                val rows = bounds.chunked(columns)
                rows.forEach { row ->
                    row.forEach { card ->
                        assertTrue(abs(card.top - row.first().top) <= 1f)
                        assertTrue(abs(card.bottom - row.first().bottom) <= 1f, "Metric row heights differ")
                    }
                    row.zipWithNext().forEach { (left, right) ->
                        assertTrue(left.right < right.left, "Metric cards overlap: $left $right")
                    }
                }
                rows.zipWithNext().forEach { (above, below) ->
                    assertTrue(above.maxOf { it.bottom } < below.minOf { it.top }, "Metric rows overlap")
                    assertTrue(abs(above.first().left - below.first().left) <= 1f)
                    assertTrue(abs(above.last().right - below.last().right) <= 1f)
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
                cards = rewards.cards.mapIndexed { index, card ->
                    card.copy(
                        title = "A little care for your daily rhythm",
                        description = "Helpful choice ${index + 1}",
                    )
                },
            )
        }
        setContent {
            PhotoAnalysisMainLayout(layoutBottomBarState(1), layoutBottomBarActions()) {
                RewardsScreen(model)
            }
        }
        onNodeWithTag(UiAutomationIds.rewardsRow(6).value).performScrollTo()
        onNodeWithTag(UiAutomationIds.REWARDS_SCREEN.value).performRewardsScrollToEnd()
        val card = onNodeWithTag(UiAutomationIds.rewardsRow(6).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val title = onNodeWithTag(UiAutomationIds.rewardsRowTitle(6).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val progress = onNodeWithTag(UiAutomationIds.rewardsRowProgress(6).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val state = onNodeWithTag(UiAutomationIds.rewardsRowState(6).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        assertTrue(
            title.top >= card.top && title.bottom <= card.bottom,
            "Title leaves its reward card: $title in $card",
        )
        assertTrue(
            progress.top >= card.top && progress.bottom <= card.bottom,
            "Progress leaves its reward card: $progress in $card",
        )
        assertTrue(
            state.top >= card.top && state.bottom <= card.bottom,
            "State leaves its reward card: $state in $card",
        )
        assertTrue(card.bottom < camera.top, "Scrolled reward overlaps navigation: $card and $camera")
        onNodeWithTag(UiAutomationIds.rewardsRowTitle(6).value, useUnmergedTree = true)
            .assertCompleteRewardsText(model.cards.first { it.id == RewardId.FERMENTED_THREE_DAYS }.title)
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
        onNodeWithTag(UiAutomationIds.HOME_METRIC_FATS.value).performScrollTo()
        val finalMetric = onNodeWithTag(UiAutomationIds.HOME_METRIC_FATS.value).fetchSemanticsNode().boundsInRoot
        assertTrue(finalMetric.bottom < camera.top)
        val rendered = captureToImage()
        assertBackdropColorNear(reference[187, 8], rendered.toPixelMap()[187, 8])
        assertBackdropColorNear(reference[187, 805], rendered.toPixelMap()[187, 805])
        writeLayoutScreenshot("home-with-insets", rendered.asSkiaBitmap())
    }

    @Test
    fun `metric bars retain their relative fill when the viewport narrows`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val model = layoutHomeModel().copy(fiber = HomeMetricModel("Fiber", "12 / 25 g", 12f / 25f))
        setContent { HomeScreen(model) }
        val regularCard = onNodeWithTag(UiAutomationIds.HOME_METRIC_FIBER.value)
            .fetchSemanticsNode().boundsInRoot
        val regularFill = progressFill(captureToImage(), regularCard, Color(0xFF7AB462))
        setContent {
            Box(Modifier.requiredSize(320.dp, 812.dp)) { HomeScreen(model) }
        }
        val narrowCard = onNodeWithTag(UiAutomationIds.HOME_METRIC_FIBER.value)
            .fetchSemanticsNode().boundsInRoot
        val narrowFill = progressFill(captureToImage(), narrowCard, Color(0xFF7AB462))
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

    private fun assertBackdropColorNear(expected: Color, actual: Color) {
        val expectedArgb = expected.toArgb()
        val actualArgb = actual.toArgb()
        // A re-encoded image can interpolate by one 8-bit level at a scaled edge.
        listOf(0, 8, 16, 24).forEach { shift ->
            val expectedChannel = (expectedArgb ushr shift) and 0xFF
            val actualChannel = (actualArgb ushr shift) and 0xFF
            assertTrue(
                abs(expectedChannel - actualChannel) <= 2,
                "Backdrop changed beyond 2/255 per channel: expected=$expected actual=$actual",
            )
        }
    }

    private fun writeLayoutScreenshot(name: String, bitmap: org.jetbrains.skia.Bitmap) {
        val destination = File("build/reports/layout-design/$name.png")
        destination.parentFile.mkdirs()
        SkiaImage.makeFromBitmap(bitmap).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> destination.writeBytes(data.bytes) }
        }
    }
}
