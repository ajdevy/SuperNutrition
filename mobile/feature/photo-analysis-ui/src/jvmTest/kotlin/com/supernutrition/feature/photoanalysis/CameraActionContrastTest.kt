package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CameraActionContrastTest {
    @Test
    fun `rendered camera artwork reaches three to one against its actual circle`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent { PhotoAnalysisMainLayout(layoutBottomBarState(), layoutBottomBarActions()) {} }
        val camera = onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).fetchSemanticsNode().boundsInRoot
        val pixels = captureToImage().toPixelMap()
        val circle = pixels[camera.center.x.toInt(), (camera.top + 4f).toInt()].luminance()
        val glyph = (camera.center.y.toInt() - 7..camera.center.y.toInt() + 7).maxOf { y ->
            (camera.center.x.toInt() - 7..camera.center.x.toInt() + 7).maxOf { x -> pixels[x, y].luminance() }
        }
        assertTrue(glyph > .95f, "The actual white camera artwork was not rendered")
        val ratio = (glyph + .05f) / (circle + .05f)
        assertTrue(ratio >= 3f, "Camera glyph/circle contrast $ratio is below WCAG SC 1.4.11's 3:1 threshold")
    }
}
