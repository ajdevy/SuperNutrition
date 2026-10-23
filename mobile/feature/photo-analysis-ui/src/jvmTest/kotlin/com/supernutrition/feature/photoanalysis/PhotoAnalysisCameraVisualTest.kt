package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Image as SkiaImage

/** Renders the production composables against the exported photo, never a baked screen image. */
@OptIn(ExperimentalTestApi::class)
class PhotoAnalysisCameraVisualTest {
    @Test
    fun `capture fills the Figma viewport and places its controls`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val photo = fixturePhoto()
        setContent {
            PhotoAnalysisCaptureScreen(
                PhotoAnalysisPresenterModel("Ready", PhotoAnalysisPresenterState.CAPTURE, emptyList()),
                {},
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                    liveCamera = { Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
                ),
            )
        }
        val shutter = onNodeWithTag(
            UiAutomationIds.CAPTURE_SHUTTER.value,
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(abs(shutter.top - 655.5f) < 1f, "Shutter top was ${shutter.top}")
        assertTrue(abs(shutter.width - 88.2f) < 1f)
        val preview = onNodeWithTag(
            UiAutomationIds.PHOTO_ANALYSIS_RETAINED_PHOTO.value,
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(preview.width == 375f && preview.height == 812f)
        writeScreenshot("capture", captureToImage().asSkiaBitmap())
    }

    @Test
    fun `review uses individually selectable artwork over the retained photo`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val photo = fixturePhoto()
        setContent {
            PhotoAnalysisCaptureScreen(
                cameraReviewFixture(),
                {},
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                    retainedPhoto = { Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
                ),
            )
        }
        onNodeWithTag(UiAutomationIds.photoAnalysisDishChoice(1).value).performClick()
        listOf("salmon", "broccoli", "avocado", "leafy-greens").forEach { id ->
            onNodeWithTag(photoAnalysisVisibleComponentId("photo-$id").value).performClick().assertIsSelected()
        }
        val footer = onNodeWithTag(
            UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL.value,
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        writeScreenshot("review", captureToImage().asSkiaBitmap())
        assertTrue(abs(footer.height - 48f) < 1f, "Normal-text footer must fit its 48 dp action: $footer")
        assertTrue(abs(footer.bottom - (812f - 31.1f)) < 1f, "Footer lost its bottom anchor: $footer")
    }

    private fun fixturePhoto() = SkiaImage.makeFromEncoded(
        File("../../fixtures/photo-analysis/figma-camera-viewfinder.png").readBytes(),
    ).toComposeImageBitmap()

    @Test
    fun `custom meal shows its current label and preserves selection at larger text sizes`() {
        val photo = fixturePhoto()
        listOf(Size(375f, 812f) to 1f, Size(320f, 568f) to 1f, Size(320f, 568f) to 1.5f)
            .forEach { (viewport, fontScale) ->
                runSkikoComposeUiTest(size = viewport, density = Density(1f, fontScale)) {
                    setContent {
                        PhotoAnalysisCaptureScreen(
                            cameraReviewFixture(),
                            {},
                            PhotoAnalysisCaptureSurface(
                                presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
                                retainedPhoto = {
                                    Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                },
                            ),
                        )
                    }
                    val customId = UiAutomationIds.PHOTO_ANALYSIS_CUSTOM_MEAL.value
                    listOf(false, true).forEach { selected ->
                        val customMeal = onNodeWithTag(customId).performScrollTo().assertIsDisplayed()
                            .assertContentDescriptionEquals("Custom meal")
                        if (selected) customMeal.assertIsSelected() else customMeal.assertIsNotSelected()
                        onNodeWithText("Custom meal", useUnmergedTree = true).assertIsDisplayed()
                        val card = customMeal.fetchSemanticsNode().boundsInRoot
                        val label = onNodeWithText(
                            "Custom meal",
                            useUnmergedTree = true,
                        ).fetchSemanticsNode().boundsInRoot
                        assertTrue(label.top >= card.top && label.bottom <= card.bottom)
                        assertTrue(abs(label.center.x - card.center.x) <= 1f)
                        val variant = if (selected) "selected" else "unselected"
                        writeScreenshot(
                            "custom-meal-plus-$variant-${viewport.width.toInt()}x${viewport.height.toInt()}-$fontScale",
                            captureToImage().asSkiaBitmap(),
                        )
                        if (!selected) customMeal.performClick().assertIsSelected()
                    }
                }
            }
    }

    private fun writeScreenshot(name: String, bitmap: org.jetbrains.skia.Bitmap) {
        val destination = File("build/reports/camera-design/$name.png")
        destination.parentFile.mkdirs()
        SkiaImage.makeFromBitmap(bitmap).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> destination.writeBytes(data.bytes) }
        }
    }
}
