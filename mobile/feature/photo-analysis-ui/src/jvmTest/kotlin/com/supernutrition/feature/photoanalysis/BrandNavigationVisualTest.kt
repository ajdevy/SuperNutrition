package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
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
class BrandNavigationVisualTest {
    @Test
    fun `sprout replaces the first i dot across header widths and font scales`() {
        listOf(Size(375f, 812f) to 1f, Size(320f, 568f) to 1.5f, Size(600f, 800f) to 1f)
            .forEach { (viewport, fontScale) ->
                runSkikoComposeUiTest(size = viewport, density = Density(1f, fontScale)) {
                    setContent {
                        PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                            HomeScreen(layoutHomeModel())
                        }
                    }
                    onNodeWithContentDescription("Super Nutrition").assertIsDisplayed()
                    val text = onNodeWithText("Super Nutrition", useUnmergedTree = true)
                    val layouts = mutableListOf<TextLayoutResult>()
                    text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    val image = captureToImage()
                    writeScreenshot("brand-${viewport.width.toInt()}-$fontScale", image)
                    assertSprout(image, layout, text.fetchSemanticsNode().boundsInRoot, fontScale)
                }
            }
    }

    @Test
    fun `brand follows live font changes and survives a header refresh`() = runSkikoComposeUiTest(
        size = Size(375f, 180f),
        density = Density(1f),
    ) {
        val fontScale = mutableStateOf(1f)
        val model = mutableStateOf(layoutHomeModel())
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.value)) {
                Box(Modifier.fillMaxSize().background(HomeBackgroundColor)) { HomeHeader(model.value) {} }
            }
        }
        repeat(3) { stage ->
            val text = onNodeWithText("Super Nutrition", useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertSprout(captureToImage(), layouts.single(), text.fetchSemanticsNode().boundsInRoot, fontScale.value)
            when (stage) {
                0 -> runOnIdle { model.value = model.value.copy(dateLabel = "Friday, Aug 8") }
                1 -> runOnIdle { fontScale.value = 1.5f }
            }
        }
        onNodeWithText("Friday, Aug 8").assertIsDisplayed()
        writeScreenshot("brand-live-font-change", captureToImage())
    }

    @Test
    fun `sprout stays over the first i in an RTL host`() = runSkikoComposeUiTest(
        size = Size(375f, 110f),
        density = Density(1f),
    ) {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize().background(HomeBackgroundColor)) { HomeHeader(layoutHomeModel()) {} }
            }
        }
        val text = onNodeWithText("Super Nutrition", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val image = captureToImage()
        writeScreenshot("brand-rtl", image)
        assertSprout(image, layouts.single(), text.fetchSemanticsNode().boundsInRoot, 1f)
    }

    @Test
    fun `tab selection changes icons while retaining label pixels`() = runSkikoComposeUiTest(
        size = Size(375f, 100f),
        density = Density(1f),
    ) {
        val selected = mutableStateOf(0)
        setContent {
            Box(Modifier.fillMaxSize().background(RewardsPaper)) {
                PhotoAnalysisMainBottomBar(
                    state = layoutBottomBarState(selected.value),
                    actions = PhotoAnalysisMainBottomBarActions(
                        onHomeSelected = { selected.value = 0 },
                        onRewardsSelected = { selected.value = 1 },
                        onCameraRequested = {},
                    ),
                )
            }
        }
        val labels = listOf("Home", "Rewards").map {
            onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        val tabs = listOf(UiAutomationIds.HOME_TAB, UiAutomationIds.REWARDS_TAB).map {
            onNodeWithTag(it.value).fetchSemanticsNode().boundsInRoot
        }
        val home = captureToImage()
        writeScreenshot("navigation-home", home)
        onNodeWithTag(UiAutomationIds.REWARDS_TAB.value).performClick().assertIsSelected()
        val rewards = captureToImage()
        writeScreenshot("navigation-rewards", rewards)
        labels.forEach { assertEquals(0, changedPixels(home, rewards, it), "Tab label recolors on selection: $it") }
        tabs.forEach { assertTrue(changedPixels(home, rewards, it) > 0, "Icon tint did not change: $it") }
        onNodeWithTag(UiAutomationIds.HOME_TAB.value).performClick().assertIsSelected()
        val restored = captureToImage()
        labels.forEach { assertEquals(0, changedPixels(home, restored, it)) }
    }

    @Test
    fun `tab touch surface remains transparent while pressed`() = runSkikoComposeUiTest(
        size = Size(375f, 100f),
        density = Density(1f),
    ) {
        setContent {
            Box(Modifier.fillMaxSize().background(RewardsPaper)) {
                PhotoAnalysisMainBottomBar(layoutBottomBarState(), layoutBottomBarActions())
            }
        }
        val tab = onNodeWithTag(UiAutomationIds.HOME_TAB.value)
        val bounds = tab.fetchSemanticsNode().boundsInRoot
        val before = captureToImage()
        tab.performTouchInput { down(center) }
        mainClock.advanceTimeBy(400)
        val held = captureToImage()
        tab.performTouchInput { up() }
        assertEquals(0, changedPixels(before, held, bounds), "Holding the tab paints its rectangular touch area")
    }

    @Test
    fun `Rewards fills behind the floating bar and final card clears navigation`() {
        listOf(Size(375f, 812f) to 1f, Size(320f, 568f) to 1f, Size(320f, 568f) to 1.5f)
            .forEach { (viewport, fontScale) ->
                runSkikoComposeUiTest(size = viewport, density = Density(1f, fontScale)) {
                    setContent {
                        PhotoAnalysisMainLayout(
                            state = layoutBottomBarState(1),
                            actions = layoutBottomBarActions(),
                            contentPadding = PaddingValues(top = 24.dp, bottom = 20.dp),
                        ) { RewardsScreen(layoutRewardsModel()) }
                    }
                    val list = onNodeWithTag(UiAutomationIds.REWARDS_LIST.value)
                    val bounds = list.fetchSemanticsNode().boundsInRoot
                    val image = captureToImage()
                    writeScreenshot("rewards-floating-${viewport.width.toInt()}-$fontScale", image)
                    assertEquals(viewport.height - 20f, bounds.bottom, "Rewards ends at an opaque navigation strip")
                    assertEquals(24f, bounds.top, "Host top inset should be applied once")
                    assertEquals(RewardsPaper, image.toPixelMap()[4, viewport.height.toInt() - 24])
                    assertEquals(RewardsPaper, image.toPixelMap()[4, viewport.height.toInt() - 4])
                    list.performScrollToIndex(if (fontScale > 1.2f) 7 else 4)
                    val card = onNodeWithTag(UiAutomationIds.rewardsRow(6).value).assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
                    assertTrue(card.bottom < camera.top, "Final reward is hidden under navigation: $card and $camera")
                    writeScreenshot("rewards-final-${viewport.width.toInt()}-$fontScale", captureToImage())
                }
            }
    }

    private fun assertSprout(image: ImageBitmap, layout: TextLayoutResult, bounds: Rect, fontScale: Float) {
        assertEquals('\u0131', layout.layoutInput.text.text[10], "The ordinary first-i dot remains")
        assertEquals('i', layout.layoutInput.text.text[12], "The second i should retain its dot")
        val glyph = layout.getBoundingBox(10).translate(bounds.topLeft)
        val pixels = image.toPixelMap()
        val accent = buildList {
            val top = floor(glyph.top).toInt().coerceAtLeast(0)
            val bottom = ceil(glyph.bottom).toInt().coerceAtMost(pixels.height)
            val left = floor(glyph.left - 6f).toInt().coerceAtLeast(0)
            val right = ceil(glyph.right + 6f).toInt().coerceAtMost(pixels.width)
            for (y in top until bottom) {
                for (x in left until right) {
                    val color = pixels[x, y]
                    if (color.red in 0.2f..0.3f && color.green in 0.36f..0.48f && color.blue < 0.1f) {
                        add(x.toFloat())
                    }
                }
            }
        }
        assertTrue(accent.isNotEmpty(), "No sprout rendered above the first i: $glyph/$fontScale")
        assertTrue(
            abs(accent.average().toFloat() - glyph.center.x) <= 3f * fontScale,
            "Sprout is beside the first i: glyph=$glyph accent=$accent fontScale=$fontScale",
        )
    }

    private fun changedPixels(before: ImageBitmap, after: ImageBitmap, bounds: Rect): Int {
        val left = before.toPixelMap()
        val right = after.toPixelMap()
        return (floor(bounds.top).toInt() until ceil(bounds.bottom).toInt()).sumOf { y ->
            (floor(bounds.left).toInt() until ceil(bounds.right).toInt()).count { x -> left[x, y] != right[x, y] }
        }
    }

    private fun writeScreenshot(name: String, bitmap: ImageBitmap) {
        val destination = File("build/reports/brand-navigation/$name.png")
        destination.parentFile.mkdirs()
        SkiaImage.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> destination.writeBytes(data.bytes) }
        }
    }
}
