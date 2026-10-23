package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Image as SkiaImage

@OptIn(ExperimentalTestApi::class, ExperimentalTextApi::class)
class MealHistoryLayoutTest {
    @Test
    fun `synthetic design fixture follows 375 by 812 geometry`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val meals = mealHistoryDesignFixture()
        setContent {
            Box(Modifier.fillMaxSize().background(ProfileBackground).padding(top = 44.dp, bottom = 34.dp)) {
                MealHistoryScreen(meals, {}, referenceDay = 20747)
            }
        }
        waitUntil(timeoutMillis = 5_000) { onNodeWithText("Meal history").hasLoadedHistoryFont() }
        waitForIdle()
        val search = onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SEARCH.value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(search.top - 104f) < 1f, "Search starts at ${search.top}")
        assertTrue(abs(search.height - 48f) < 1f, "Search is ${search.height} high")
        assertTrue(abs(search.left - 16f) < 1f && abs(search.right - 359f) < 1f)
        val first = onNodeWithTag(UiAutomationIds.mealHistoryRow(0).value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(first.top - 198f) < 1f, "First card starts at ${first.top}")
        assertTrue(abs(first.height - 77f) < 1f, "First card is ${first.height} high")
        val yesterday = onNodeWithText("Yesterday · Oct 20").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(yesterday.top - 368f) < 1f, "Yesterday starts at ${yesterday.top}")
        onNodeWithText("Today · Oct 21").assertIsDisplayed()
        onNodeWithText("Yesterday · Oct 20").assertIsDisplayed()
        onNodeWithText("Oatmeal with apple").assertIsDisplayed()
        writeHistoryScreenshot("meal-history-375x812", captureToImage().asSkiaBitmap())
        onNodeWithTag(UiAutomationIds.mealHistoryRow(0).value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SUMMARY.value).assertIsDisplayed()
        onNodeWithText(MealHistoryCopy().photoUnavailable).assertDoesNotExist()
    }

    @Test
    fun `search clear action preserves normal field height`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { MealHistoryScreen(mealHistoryDesignFixture(), {}, referenceDay = 20747) }
        waitUntil(timeoutMillis = 5_000) { onNodeWithText("Meal history").hasLoadedHistoryFont() }
        waitForIdle()
        val search = onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SEARCH.value)
        val originalHeight = search.fetchSemanticsNode().boundsInRoot.height
        search.performTextReplacement("egg")
        onNodeWithText(MealHistoryCopy().clearSearch).assertIsDisplayed()
        val editedHeight = search.fetchSemanticsNode().boundsInRoot.height
        assertTrue(abs(editedHeight - originalHeight) < 1f, "Search changes from $originalHeight to $editedHeight")
    }

    @Test
    fun `history at 200 percent wraps readable text and keeps all cards reachable`() = runSkikoComposeUiTest(
        size = Size(360f, 780f),
        density = Density(1f, fontScale = 2f),
    ) {
        val meals = mealHistoryDesignFixture()
        setContent {
            Box(Modifier.fillMaxSize().background(ProfileBackground).padding(top = 24.dp, bottom = 24.dp)) {
                MealHistoryScreen(meals, {}, referenceDay = 20747)
            }
        }
        waitUntil(timeoutMillis = 5_000) { onNodeWithText("Meal history").hasLoadedHistoryFont() }
        waitForIdle()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_CLOSE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SEARCH.value).assertIsDisplayed()
        writeHistoryScreenshot("meal-history-360x780-200-top", captureToImage().asSkiaBitmap())
        meals.forEach { meal ->
            onNodeWithTag(UiAutomationIds.MEAL_HISTORY_LIST.value).performScrollToNode(hasText(meal.title))
            onNodeWithText(meal.title, useUnmergedTree = true).assertIsDisplayed().assertReadableHomeText(2f)
            val subtitle = meal.metadata.ingredientSummary ?: meal.ingredients.joinToString(", ")
            onNodeWithText(subtitle, useUnmergedTree = true).assertIsDisplayed().assertReadableHomeText(2f)
        }
        writeHistoryScreenshot("meal-history-360x780-200-bottom", captureToImage().asSkiaBitmap())
        onNodeWithTag(UiAutomationIds.mealHistoryRow(4).value).performClick()
        onNodeWithTag(UiAutomationIds.MEAL_HISTORY_SUMMARY.value).assertIsDisplayed()
    }

    @Test
    fun `small history text meets normal text contrast on its surfaces`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { MealHistoryScreen(mealHistoryDesignFixture(), {}, referenceDay = 20747) }
        val surfaces = listOf(
            "Today · Oct 21" to ProfileBackground,
            "13:15" to Color.White,
            "Salmon, quinoa, broccoli +2" to Color.White,
            MealHistoryCopy().search to Color.White,
        )
        surfaces.forEach { (label, background) ->
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val foreground = layouts.single().layoutInput.style.color.luminance()
            val backdrop = background.luminance()
            val ratio = (maxOf(foreground, backdrop) + 0.05f) / (minOf(foreground, backdrop) + 0.05f)
            assertTrue(ratio >= 4.5f, "'$label' contrast is $ratio, below 4.5")
        }
    }

    private fun SemanticsNodeInteraction.hasLoadedHistoryFont(): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val family = layouts.singleOrNull()?.layoutInput?.style?.fontFamily as? FontListFontFamily ?: return false
        return family.isNotEmpty() && family.none { it is SystemFont }
    }

    private fun writeHistoryScreenshot(name: String, bitmap: org.jetbrains.skia.Bitmap) {
        val destination = File("build/reports/meal-history-design/$name.png")
        destination.parentFile.mkdirs()
        SkiaImage.makeFromBitmap(bitmap).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> destination.writeBytes(data.bytes) }
        }
    }
}
