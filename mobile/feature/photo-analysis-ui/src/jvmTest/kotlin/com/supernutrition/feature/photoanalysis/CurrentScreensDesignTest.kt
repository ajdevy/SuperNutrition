package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.RewardId
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Image as SkiaImage

/** Explicit Figma reference fixtures only; these quantities never enter the production host. */
@OptIn(ExperimentalTestApi::class, ExperimentalTextApi::class)
class CurrentScreensDesignTest {
    @Test
    fun `Home reference fixture matches current 375 by 812 composition`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val model = currentDesignHomeFixture()
        setContent {
            PhotoAnalysisMainLayout(
                layoutBottomBarState(),
                layoutBottomBarActions(),
                contentPadding = PaddingValues(top = 44.dp, bottom = 34.dp),
            ) { HomeScreen(model) }
        }
        waitUntil(timeoutMillis = 5_000) { onNodeWithText(model.rhythmSubtitle, useUnmergedTree = true).fontLoaded() }
        waitForIdle()
        writeCurrentScreenshot("home-375x812", captureToImage().asSkiaBitmap())
        val metrics = onNodeWithTag(UiAutomationIds.HOME_SUMMARY.value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(metrics.left - 16f) < 1f && abs(metrics.right - 359f) < 1f)
        assertTrue(abs(metrics.top - 408f) < 3f, "Current metrics top is ${metrics.top}, expected 408")
        val header = onNodeWithTag(UiAutomationIds.HOME_HEADER.value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(header.top - 62f) < 2f, "Header hit frame starts at ${header.top}, expected 62")
    }

    @Test
    fun `Rewards reference fixture uses current seven-card visual composition`() = runSkikoComposeUiTest(
        size = Size(375f, 830f),
        density = Density(1f),
    ) {
        val model = currentDesignRewardsFixture()
        setContent {
            PhotoAnalysisMainLayout(
                layoutBottomBarState(1),
                layoutBottomBarActions(),
                contentPadding = PaddingValues(top = 44.dp, bottom = 34.dp),
            ) { RewardsScreen(model) }
        }
        waitUntil(timeoutMillis = 5_000) { onNodeWithTag(UiAutomationIds.REWARDS_TITLE.value).fontLoaded() }
        waitForIdle()
        writeCurrentScreenshot("rewards-375x830", captureToImage().asSkiaBitmap())
        val first = onNodeWithTag(UiAutomationIds.rewardsRow(0).value).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(first.left - 16f) < 1f && abs(first.width - 150f) < 1f)
        assertTrue(abs(first.height - 164f) < 2f, "Reference reward height is ${first.height}")
        assertTrue(abs(first.top - 300.3f) < 4f, "Reference reward top is ${first.top}")
    }

    private fun SemanticsNodeInteraction.fontLoaded(): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val family = layouts.single().layoutInput.style.fontFamily
        return family is FontListFontFamily && family.fonts.none { it is SystemFont }
    }
}

internal fun currentDesignHomeFixture() = layoutHomeModel().copy(
    careQuestion = "Add a portion of greens\nfor the next meal",
    rhythmTitle = "Daily essentials",
    rhythmSubtitle = "Your progress today",
    fiber = HomeMetricModel("Fiber", "12 / 25 g", 12f / 25f),
    colors = HomeMetricModel("Colors", "1 / 3", 1f / 3f),
    protein = HomeMetricModel("Protein", "50 / 90 g", 50f / 90f),
    fats = HomeMetricModel("Fats", "48 / 70 g", 48f / 70f),
)

internal fun currentDesignRewardsFixture() = layoutRewardsModel().copy(
    cards = listOf(
        Triple(RewardId.PROTEIN_ALL_DAY, "Protein all day", "Protein in 2 meals today"),
        Triple(RewardId.FATS_PRESENT, "Fats on track", "Fat was in your food today"),
        Triple(RewardId.FIBER_PRESENT, "Fiber on track", "1 of 2 meals"),
        Triple(RewardId.COLORS_OF_DAY, "Colors of the day", "1 of 3 colors"),
        Triple(RewardId.SEVEN_DAYS_RECORDED, "7 days in a row", "Entries 7 days in a row"),
        Triple(RewardId.THIRTY_PRODUCTS, "30 foods", "18 of 30 foods this week"),
        Triple(RewardId.FERMENTED_THREE_DAYS, "Living shelf", "Fermented foods on 2 of 3 days"),
    ).mapIndexed { index, (id, title, description) ->
        val earned = index in listOf(0, 1, 4)
        RewardsCardModel(
            id,
            title,
            description,
            listOf("2 of 2", "1 of 1", "1 of 2", "1 of 3", "7 of 7", "18 of 30", "2 of 3")[index],
            if (earned) "Earned" else "In progress",
            earned,
            if (earned) 1f else listOf(1f, 1f, .5f, 1f / 3f, 1f, .6f, 2f / 3f)[index],
        )
    },
)

private fun writeCurrentScreenshot(name: String, bitmap: org.jetbrains.skia.Bitmap) {
    val destination = File("build/reports/current-design/$name.png")
    destination.parentFile.mkdirs()
    SkiaImage.makeFromBitmap(bitmap).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> destination.writeBytes(data.bytes) }
    }
}
