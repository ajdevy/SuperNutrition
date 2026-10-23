package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PhotoReviewReadabilityTest {
    @Test
    fun `small review text and the note placeholder stay readable over a white photo`() = runSkikoComposeUiTest(
        size = Size(1125f, 2436f),
        density = Density(3f),
    ) {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        setContent {
            PhotoAnalysisCaptureScreen(
                cameraReviewFixture(),
                {},
                PhotoAnalysisCaptureSurface(
                    presenterCopy = copy,
                    retainedPhoto = { Box(Modifier.fillMaxSize().background(Color.White)) },
                ),
            )
        }
        val bitmap = captureToImage()
        listOf(copy.review.subtitle, copy.review.anythingElse).forEach { label ->
            assertReadable(bitmap, onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun `progress text stays readable over a white camera preview`() = runSkikoComposeUiTest(
        size = Size(1125f, 2436f),
        density = Density(3f),
    ) {
        setContent {
            PhotoAnalysisCaptureScreen(
                PhotoAnalysisPresenterModel("Finding foods", PhotoAnalysisPresenterState.ANALYZING, emptyList()),
                {},
                PhotoAnalysisCaptureSurface(
                    presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                    liveCamera = { Box(Modifier.fillMaxSize().background(Color.White)) },
                ),
            )
        }
        assertReadable(captureToImage(), onNodeWithText("Finding foods").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun `small reward title is readable on its white card`() = runSkikoComposeUiTest(
        size = Size(1083f, 2346f),
        density = Density(3f),
    ) {
        setContent { RewardsScreen(layoutRewardsModel()) }
        assertReadable(
            captureToImage(),
            onNodeWithTag(UiAutomationIds.rewardsRowTitle(0).value).fetchSemanticsNode().boundsInRoot,
        )
    }

    @Test
    fun `home guidance and metric labels stay readable on their surfaces`() = runSkikoComposeUiTest(
        size = Size(1125f, 3000f),
        density = Density(3f),
    ) {
        setContent { HomeScreen(layoutHomeModel()) }
        val bitmap = captureToImage()
        listOf(layoutHomeModel().careQuestion, layoutHomeModel().rhythmSubtitle).forEach { label ->
            assertReadable(bitmap, onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun `home metric target and unit are readable without relying on the darker value`() = runSkikoComposeUiTest(
        size = Size(1125f, 3000f),
        density = Density(3f),
    ) {
        val model = layoutHomeModel()
        setContent { HomeScreen(model) }
        val node = onNodeWithText(model.fiber.value, useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val suffixStart = model.fiber.value.indexOf(" / ") + 3
        val glyphs = (suffixStart until model.fiber.value.length).map { layouts.single().getBoundingBox(it) }
        val textBounds = node.fetchSemanticsNode().boundsInRoot
        assertReadable(
            captureToImage(),
            Rect(
                textBounds.left + glyphs.minOf { it.left },
                textBounds.top + glyphs.minOf { it.top },
                textBounds.left + glyphs.maxOf { it.right },
                textBounds.top + glyphs.maxOf { it.bottom },
            ),
        )
    }
}

private fun assertReadable(bitmap: ImageBitmap, bounds: Rect) {
    val pixels = bitmap.toPixelMap()
    val luminances = buildList {
        for (y in floor(bounds.top).toInt() until ceil(bounds.bottom).toInt()) {
            for (x in floor(bounds.left).toInt() until ceil(bounds.right).toInt()) {
                add(pixels[x, y].luminance())
            }
        }
    }
    val ratio = (luminances.max() + .05f) / (luminances.min() + .05f)
    assertTrue(ratio >= 4.5f, "Rendered text contrast was $ratio at $bounds")
}
