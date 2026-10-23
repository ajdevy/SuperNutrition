package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeCarePromptLayoutTest {
    @Test
    fun `care tail remains painted below the speech body and within its measured section`() = runSkikoComposeUiTest(
        size = Size(360f, 780f),
        density = Density(1f),
    ) {
        setContent {
            Box(
                Modifier.fillMaxSize().background(Color.Magenta).padding(24.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                HomeCarePrompt(layoutHomeModel(), {})
            }
        }
        val panel = onNodeWithTag(UiAutomationIds.HOME_CARE_PROMPT.value).fetchSemanticsNode().boundsInRoot
        val body = onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(15.5f, panel.bottom - body.bottom, 1f)
        val pixels = captureToImage().toPixelMap()
        val whitePixels = (panel.left.toInt() until panel.right.toInt()).count { x ->
            pixels[x, (body.bottom + 7f).toInt()] == Color.White
        }
        assertTrue(whitePixels in 3..17, "Speech tail is missing, clipped or too wide: $whitePixels")
        assertEquals(Color.Magenta, pixels[panel.center.x.toInt(), (panel.bottom + 2).toInt()])
    }

    @Test
    fun `guidance stays centered readable and actionable at regular and larger text sizes`() {
        listOf(Size(375f, 812f) to 1f, Size(320f, 568f) to 1.5f).forEach { (viewport, fontScale) ->
            runSkikoComposeUiTest(size = viewport, density = Density(1f, fontScale)) {
                val model = layoutHomeModel()
                var requests = 0
                setContent { HomeScreen(model, onAddFoodRequested = { requests++ }) }
                val button = onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).assertIsDisplayed()
                    .assertHeightIsAtLeast(48.dp)
                val node = onNodeWithText(model.careQuestion, useUnmergedTree = true)
                val layout = node.assertReadableHomeText(fontScale)
                val body = button.fetchSemanticsNode().boundsInRoot
                val text = node.fetchSemanticsNode().boundsInRoot
                assertTrue(abs(body.center.x - text.center.x) <= 1f)
                repeat(layout.lineCount) { line ->
                    val center = (layout.getLineLeft(line) + layout.getLineRight(line)) / 2f
                    assertTrue(abs(center - layout.multiParagraph.width / 2f) <= 1f, "Guidance line is off center")
                }
                button.performClick()
                assertEquals(1, requests)
                onNodeWithText(model.viewGoalsLabel).assertDoesNotExist()
            }
        }
    }
}
