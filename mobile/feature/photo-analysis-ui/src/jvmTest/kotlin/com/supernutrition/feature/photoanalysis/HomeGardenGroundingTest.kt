package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeGardenGroundingTest {
    @Test
    fun `glass without measured garden bounds still fills its own offset canvas`() = runSkikoComposeUiTest(
        size = Size(412f, 915f),
        density = Density(1f),
    ) {
        setContent {
            CompositionLocalProvider(LocalHomeBackdrop provides null) {
                Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                    HomeGlassBackdrop(Modifier.offset(80.dp, 420.dp).size(240.dp, 200.dp))
                }
            }
        }
        val pixels = captureToImage().toPixelMap()
        listOf(125 to 465, 205 to 520, 275 to 575).forEach { (x, y) ->
            assertTrue(pixels[x, y] != Color.Magenta, "Missing scene bounds expose the canvas at ($x,$y)")
        }
    }

    @Test
    fun `tall native phone has sand beside the paws instead of distant sky`() = runSkikoComposeUiTest(
        size = Size(412f, 915f),
        density = Density(1f),
    ) {
        setContent {
            PhotoAnalysisMainLayout(
                layoutBottomBarState(),
                layoutBottomBarActions(),
                contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
            ) { HomeScreen(layoutHomeModel()) }
        }
        val mascot = onNodeWithContentDescription("Capybara mascot").getUnclippedBoundsInRoot()
        val mascotHeight = (mascot.bottom - mascot.top).value
        val spriteWidth = mascotHeight * 241f / 325f
        val contact = mascot.top.value + mascotHeight * 319f / 325f
        val pixels = captureToImage().toPixelMap()
        // Both samples are outside the sprite and 16dp above its paws. The current
        // garden's sandy floor is warm; its distant sky/mountains are blue/green.
        val samples = listOf(-0.6f, 0.6f).flatMap { side ->
            (-2..2).map { delta ->
                pixels[(206f + spriteWidth * side).toInt(), (contact - 16f + delta).toInt()]
            }
        }
        assertTrue(
            samples.all { it.red - it.blue > 0.12f && it.green - it.blue > 0.04f },
            "The paws sit at the distant horizon instead of on sand: contact=$contact samples=$samples",
        )
    }

    @Test
    fun `glass shows the same anchored garden coordinates as the full blurred scene`() = runSkikoComposeUiTest(
        size = Size(412f, 915f),
        density = Density(1f),
    ) {
        val state = HomeBackdropState().apply { mascotBounds = Rect(16f, 208f, 396f, 370.5f) }
        setContent {
            CompositionLocalProvider(LocalHomeBackdrop provides state) {
                HomeGardenBackground(Modifier.fillMaxSize().blur(15.dp, BlurredEdgeTreatment.Unbounded))
            }
        }
        val blurredGarden = captureToImage().toPixelMap()
        setContent {
            CompositionLocalProvider(LocalHomeBackdrop provides state) {
                Box(Modifier.fillMaxSize()) {
                    HomeGardenBackground(Modifier.fillMaxSize())
                    HomeGlassBackdrop(Modifier.offset(80.dp, 420.dp).size(240.dp, 200.dp))
                }
            }
        }
        val glass = captureToImage().toPixelMap()
        // Stay inside the blur kernel's edge treatment and compare absolute scene points.
        listOf(125 to 465, 205 to 520, 275 to 575).forEach { (x, y) ->
            val expected = blurredGarden[x, y]
            val actual = glass[x, y]
            assertTrue(
                abs(expected.red - actual.red) <= 2f / 255f &&
                    abs(expected.green - actual.green) <= 2f / 255f &&
                    abs(expected.blue - actual.blue) <= 2f / 255f,
                "Glass shifted the garden at ($x,$y): $expected versus $actual",
            )
        }
    }
}
