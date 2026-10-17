package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Measures the native pixel slot in the production screen, including any parent layer transform. */
@OptIn(ExperimentalTestApi::class)
class GalleryPhotoFitTest {
    @Test
    fun `landscape gallery photo fits and centers during analysis`() = assertAnalysisFrame(3f / 2f)

    @Test
    fun `tall portrait gallery photo fits and centers during analysis`() = assertAnalysisFrame(1f / 4f)

    @Test
    fun `square gallery photo fits and centers during analysis`() = assertAnalysisFrame(1f)

    @Test
    fun `one pixel high landscape photo stays bounded without invalid constraints`() = assertAnalysisFrame(1024f)

    @Test
    fun `one pixel wide portrait photo stays bounded without invalid constraints`() = assertAnalysisFrame(1f / 4096f)

    @Test
    fun `retained photo keeps its fitted frame across preparation analysis and review`() = runSkikoComposeUiTest(
        size = PHONE_SIZE,
        density = Density(1f),
    ) {
        val probe = PhotoFrameProbe()
        val original = cameraReviewFixture()
        val model = mutableStateOf(original.copy(state = PhotoAnalysisPresenterState.PREPARING, review = null))
        val ratio = 3030f / 2670f
        setContent {
            PhotoAnalysisCaptureScreen(model.value, {}, retainedSurface(probe, ratio))
        }
        val prepared = runOnIdle { probe.boundsInRoot() }
        assertFittedFrame(prepared, ratio)
        listOf(
            PhotoAnalysisPresenterState.ANALYZING,
            PhotoAnalysisPresenterState.CANDIDATE_REVIEW,
            PhotoAnalysisPresenterState.READY,
        ).forEach { state ->
            runOnIdle { model.value = original.copy(state = state) }
            assertSameFrame(prepared, runOnIdle { probe.boundsInRoot() })
            assertViewport(
                onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STAGE.value).fetchSemanticsNode().boundsInRoot,
            )
        }
    }

    @Test
    fun `unknown or invalid photo aspect metadata stays unzoomed during review`() {
        listOf(null, 0f, -1f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN).forEach { ratio ->
            runSkikoComposeUiTest(size = PHONE_SIZE, density = Density(1f)) {
                val probe = PhotoFrameProbe()
                setContent { PhotoAnalysisCaptureScreen(cameraReviewFixture(), {}, retainedSurface(probe, ratio)) }
                assertViewport(runOnIdle { probe.boundsInRoot() })
                assertViewport(
                    onNodeWithTag(
                        UiAutomationIds.PHOTO_ANALYSIS_RETAINED_PHOTO.value,
                    ).fetchSemanticsNode().boundsInRoot,
                )
            }
        }
    }

    @Test
    fun `fitting retained photos leaves review controls in their existing positions`() = runSkikoComposeUiTest(
        size = PHONE_SIZE,
        density = Density(1f),
    ) {
        val probe = PhotoFrameProbe()
        val ratio = mutableStateOf(1f)
        val model = mutableStateOf(cameraReviewFixture())
        setContent { PhotoAnalysisCaptureScreen(model.value, {}, retainedSurface(probe, ratio.value)) }
        val controlIds = listOf(
            UiAutomationIds.CAPTURE_CLOSE,
            UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL,
            UiAutomationIds.PHOTO_ANALYSIS_CUSTOM_MEAL,
        )
        val before = controlIds.map { onNodeWithTag(it.value).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        runOnIdle {
            ratio.value = 3f / 2f
            model.value = model.value.copy(state = PhotoAnalysisPresenterState.READY)
        }
        assertFittedFrame(runOnIdle { probe.boundsInRoot() }, ratio.value)
        controlIds.forEachIndexed { index, id ->
            assertSameFrame(
                before[index],
                onNodeWithTag(id.value).assertIsDisplayed().fetchSemanticsNode().boundsInRoot,
            )
        }
    }

    @Test
    fun `live capture fills the viewport despite stale retained metadata and keeps controls usable`() =
        runSkikoComposeUiTest(size = PHONE_SIZE, density = Density(1f)) {
            val probe = PhotoFrameProbe()
            val actions = mutableListOf<PhotoAnalysisPresenterAction>()
            var closed = false
            setContent {
                PhotoAnalysisCaptureScreen(
                    PhotoAnalysisPresenterModel("Ready", PhotoAnalysisPresenterState.CAPTURE, emptyList()),
                    actions::add,
                    retainedSurface(PhotoFrameProbe(), 3f / 2f).copy(
                        liveCamera = { ProbePixels(probe, LIVE_PIXELS) },
                        onCloseRequested = { closed = true },
                    ),
                )
            }
            onNodeWithTag(PHOTO_PIXELS).assertDoesNotExist()
            assertViewport(runOnIdle { probe.boundsInRoot() })
            assertViewport(
                onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_RETAINED_PHOTO.value).fetchSemanticsNode().boundsInRoot,
            )
            listOf(UiAutomationIds.CAPTURE_SHUTTER, UiAutomationIds.CAPTURE_IMPORT, UiAutomationIds.CAPTURE_CLOSE)
                .forEach { onNodeWithTag(it.value).assertIsDisplayed().performClick() }
            assertEquals(
                listOf(PhotoAnalysisPresenterAction.TakePhoto, PhotoAnalysisPresenterAction.ImportPhoto),
                actions,
            )
            assertTrue(closed)
        }

    @Test
    fun `backgrounding removes retained pixels without reviving live camera`() = runSkikoComposeUiTest(
        size = PHONE_SIZE,
        density = Density(1f),
    ) {
        val model = mutableStateOf(cameraReviewFixture())
        val photo = PhotoFrameProbe()
        val live = PhotoFrameProbe()
        setContent {
            PhotoAnalysisCaptureScreen(
                model.value,
                {},
                retainedSurface(photo, 1f).copy(liveCamera = { ProbePixels(live, LIVE_PIXELS) }),
            )
        }
        onNodeWithTag(PHOTO_PIXELS).assertExists()
        onNodeWithTag(LIVE_PIXELS).assertDoesNotExist()
        runOnIdle { model.value = model.value.copy(state = PhotoAnalysisPresenterState.BACKGROUNDED, review = null) }
        onNodeWithTag(PHOTO_PIXELS).assertDoesNotExist()
        onNodeWithTag(LIVE_PIXELS).assertDoesNotExist()
    }

    private fun assertAnalysisFrame(ratio: Float) = runSkikoComposeUiTest(size = PHONE_SIZE, density = Density(1f)) {
        val probe = PhotoFrameProbe()
        setContent {
            PhotoAnalysisCaptureScreen(
                PhotoAnalysisPresenterModel("Analyzing", PhotoAnalysisPresenterState.ANALYZING, emptyList()),
                {},
                retainedSurface(probe, ratio),
            )
        }
        assertFittedFrame(runOnIdle { probe.boundsInRoot() }, ratio)
        assertViewport(
            onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_RETAINED_PHOTO.value).fetchSemanticsNode().boundsInRoot,
        )
        assertViewport(onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STAGE.value).fetchSemanticsNode().boundsInRoot)
    }

    private fun retainedSurface(probe: PhotoFrameProbe, ratio: Float?) = PhotoAnalysisCaptureSurface(
        presenterCopy = PhotoAnalysisPresenterCopyFixtures.localized(),
        retainedPhoto = { ProbePixels(probe, PHOTO_PIXELS) },
        retainedPhotoAspectRatio = ratio,
    )

    private fun assertFittedFrame(frame: Rect, ratio: Float) {
        val expectedWidth = minOf(PHONE_SIZE.width, PHONE_SIZE.height * ratio).coerceAtLeast(1f)
        val expectedHeight = minOf(PHONE_SIZE.height, PHONE_SIZE.width / ratio).coerceAtLeast(1f)
        assertTrue(
            frame.width >= 1f && frame.height >= 1f,
            "A valid photo must retain at least one physical pixel: $frame",
        )
        assertNear(expectedWidth, frame.width, "Photo frame width")
        assertNear(expectedHeight, frame.height, "Photo frame height")
        assertNear(PHONE_SIZE.width / 2f, frame.center.x, "Photo horizontal center")
        assertNear(PHONE_SIZE.height / 2f, frame.center.y, "Photo vertical center")
        assertTrue(
            frame.left >= -TOLERANCE && frame.top >= -TOLERANCE &&
                frame.right <= PHONE_SIZE.width + TOLERANCE && frame.bottom <= PHONE_SIZE.height + TOLERANCE,
            "The full photo must fit inside the viewport: $frame",
        )
    }

    private fun assertViewport(frame: Rect) = assertSameFrame(Rect(Offset.Zero, PHONE_SIZE), frame)

    private fun assertSameFrame(expected: Rect, actual: Rect) {
        assertNear(expected.left, actual.left, "Frame left")
        assertNear(expected.top, actual.top, "Frame top")
        assertNear(expected.right, actual.right, "Frame right")
        assertNear(expected.bottom, actual.bottom, "Frame bottom")
    }

    private fun assertNear(expected: Float, actual: Float, label: String) {
        assertTrue(abs(expected - actual) <= TOLERANCE, "$label expected $expected, got $actual")
    }

    private companion object {
        val PHONE_SIZE = Size(375f, 812f)
        const val TOLERANCE = 1f
        const val PHOTO_PIXELS = "gallery-photo-pixels"
        const val LIVE_PIXELS = "gallery-live-pixels"
    }
}

private class PhotoFrameProbe {
    lateinit var coordinates: LayoutCoordinates

    fun boundsInRoot(): Rect = Rect(
        coordinates.localToRoot(Offset.Zero),
        coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())),
    )
}

@Composable
private fun ProbePixels(probe: PhotoFrameProbe, tag: String) {
    Box(Modifier.fillMaxSize().testTag(tag).onGloballyPositioned { probe.coordinates = it })
}
