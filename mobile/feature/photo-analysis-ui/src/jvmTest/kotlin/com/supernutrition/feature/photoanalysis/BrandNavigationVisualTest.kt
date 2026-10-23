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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Image as SkiaImage

@OptIn(ExperimentalTestApi::class)
class BrandNavigationVisualTest {
    @Test
    fun `compact header keeps its profile reachable without the legacy badge across widths and text scales`() {
        listOf(Size(375f, 812f) to 1f, Size(320f, 568f) to 1.5f, Size(600f, 800f) to 1f)
            .forEach { (viewport, fontScale) ->
                runSkikoComposeUiTest(size = viewport, density = Density(1f, fontScale)) {
                    setContent {
                        PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {
                            HomeScreen(layoutHomeModel())
                        }
                    }
                    val model = layoutHomeModel()
                    onNodeWithText("Super Nutrition", useUnmergedTree = true).assertDoesNotExist()
                    onNodeWithText(model.dateLabel, useUnmergedTree = true).assertDoesNotExist()
                    onNodeWithText(model.rhythmBadgeLabel, useUnmergedTree = true).assertDoesNotExist()
                    val header = onNodeWithTag(UiAutomationIds.HOME_HEADER.value).fetchSemanticsNode().boundsInRoot
                    val profile = onNodeWithTag(UiAutomationIds.HOME_PROFILE.value).assertIsDisplayed()
                        .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                        .fetchSemanticsNode().boundsInRoot
                    assertEquals(header.right, profile.right, 1f, "Profile is not aligned to the header end")
                    assertTrue(header.left + 40.5f + 16f <= profile.left, "Logo and profile areas overlap")
                    val image = captureToImage()
                    writeScreenshot("header-${viewport.width.toInt()}-$fontScale", image)
                }
            }
    }

    @Test
    fun `compact header follows live profile and font changes without restoring legacy copy`() = runSkikoComposeUiTest(
        size = Size(375f, 180f),
        density = Density(1f),
    ) {
        val fontScale = mutableStateOf(1f)
        val model = mutableStateOf(layoutHomeModel())
        val events = mutableListOf<String>()
        val profile = mutableStateOf<() -> Unit>({ events.add("old profile") })
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.value)) {
                Box(Modifier.fillMaxSize().background(HomeBackgroundColor)) {
                    HomeHeader(model.value, profile.value)
                }
            }
        }
        repeat(3) { stage ->
            onNodeWithText(model.value.rhythmBadgeLabel, useUnmergedTree = true).assertDoesNotExist()
            onNodeWithTag(UiAutomationIds.HOME_PROFILE.value)
                .assertContentDescriptionEquals(
                    model.value.profileActionLabel,
                ).assertHeightIsAtLeast(48.dp).performClick()
            when (stage) {
                0 -> runOnIdle {
                    model.value = model.value.copy(
                        dateLabel = "Friday, Aug 8",
                        rhythmBadgeLabel = "7 day rhythm",
                        profileActionLabel = "Open your profile",
                    )
                }

                1 -> runOnIdle {
                    fontScale.value = 1.5f
                    profile.value = { events.add("new profile") }
                }
            }
        }
        onNodeWithText("Friday, Aug 8").assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.HOME_DATE.value).assertDoesNotExist()
        assertEquals(listOf("old profile", "old profile", "new profile"), events)
        writeScreenshot("header-live-font-change", captureToImage())
    }

    @Test
    fun `compact header mirrors its logo area and profile in an RTL host`() = runSkikoComposeUiTest(
        size = Size(375f, 110f),
        density = Density(1f),
    ) {
        var profileRequests = 0
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize().background(HomeBackgroundColor)) {
                    HomeHeader(layoutHomeModel()) { profileRequests++ }
                }
            }
        }
        onNodeWithText(layoutHomeModel().rhythmBadgeLabel, useUnmergedTree = true).assertDoesNotExist()
        val header = onNodeWithTag(UiAutomationIds.HOME_HEADER.value).fetchSemanticsNode().boundsInRoot
        val profile = onNodeWithTag(UiAutomationIds.HOME_PROFILE.value).assertIsDisplayed()
        val profileBounds = profile.fetchSemanticsNode().boundsInRoot
        assertEquals(header.left, profileBounds.left, 1f, "RTL profile is not at the trailing edge")
        assertTrue(header.right - 40.5f >= profileBounds.right + 16f, "RTL logo area overlaps profile")
        profile.performClick()
        assertEquals(1, profileRequests)
        val image = captureToImage()
        writeScreenshot("header-rtl", image)
    }

    @Test
    fun `tab selection changes icons and label colors while retaining their positions`() = runSkikoComposeUiTest(
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
        labels.forEachIndexed { index, bounds ->
            assertTrue(changedPixels(home, rewards, bounds) > 0, "Tab label color did not change: $bounds")
            assertEquals(
                bounds,
                onNodeWithText(if (index == 0) "Home" else "Rewards", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot,
                "Tab label moved on selection",
            )
            assertTrue(
                changedPixels(home, rewards, tabs[index]) > changedPixels(home, rewards, bounds),
                "Tab icon did not change with its label",
            )
        }
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
                    val screen = onNodeWithTag(UiAutomationIds.REWARDS_SCREEN.value)
                    val bounds = screen.fetchSemanticsNode().boundsInRoot
                    val image = captureToImage()
                    writeScreenshot("rewards-floating-${viewport.width.toInt()}-$fontScale", image)
                    assertEquals(viewport.height - 20f, bounds.bottom, "Rewards ends at an opaque navigation strip")
                    assertEquals(24f, bounds.top, "Host top inset should be applied once")
                    assertEquals(Color(0xFFF6F7F5), image.toPixelMap()[4, viewport.height.toInt() - 24])
                    assertEquals(Color(0xFFF6F7F5), image.toPixelMap()[4, viewport.height.toInt() - 4])
                    val finalReward = onNodeWithTag(UiAutomationIds.rewardsRow(6).value)
                    finalReward.performScrollTo()
                    onNodeWithTag(UiAutomationIds.REWARDS_SCREEN.value).performRewardsScrollToEnd()
                    val card = finalReward.assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
                    assertTrue(card.bottom < camera.top, "Final reward is hidden under navigation: $card and $camera")
                    writeScreenshot("rewards-final-${viewport.width.toInt()}-$fontScale", captureToImage())
                }
            }
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
