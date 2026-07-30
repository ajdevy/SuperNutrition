package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

/** The shared production screen is exercised through the Compose UI Test v2 API. */
@OptIn(ExperimentalTestApi::class)
class PhotoAnalysisCaptureScreenComposeTest {
    @Test
    fun `capture screen exposes the shared semantic contract`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        setContent {
            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel.from(
                    com.supernutrition.domain.photoanalysis.PhotoAnalysisState.Capture,
                    copy,
                ),
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(copy = copy),
            )
        }

        onNodeWithTag("photo_analysis_title").assertIsDisplayed()
        onNodeWithTag("photo_analysis_title").assertTextEquals(copy.captureTitle)
        onNodeWithTag("photo_analysis_status").assertIsDisplayed()
        onNodeWithTag("photo_analysis_status").assertTextEquals(copy.readyToCapture)
        onNodeWithTag("capture_shutter").assertIsDisplayed()
        onNodeWithTag("capture_shutter").assertTextEquals(copy.takeMealPhoto)
        onNodeWithTag("capture_import").assertIsDisplayed()
        onNodeWithTag("capture_import").assertTextEquals(copy.choosePhoto)
    }

    @Test
    fun `screen renders and dispatches every bounded action`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val clicked = mutableListOf<PhotoAnalysisPresenterAction>()
        val model = PhotoAnalysisPresenterModel(
            statusLabel = copy.photoCaptureFailed,
            state = PhotoAnalysisPresenterState.RECOVERY,
            actions = PhotoAnalysisPresenterAction.entries,
        )
        setContent {
            PhotoAnalysisCaptureScreen(
                model = model,
                onAction = clicked::add,
                surface = PhotoAnalysisCaptureSurface(
                    copy = copy,
                    title = copy.captureTitle,
                    shutterLabel = copy.takeMealPhoto,
                    importLabel = copy.choosePhoto,
                    shutterAutomationId = "custom_shutter",
                    importAutomationId = "custom_import",
                ),
            )
        }

        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).assertIsDisplayed()
        onNodeWithTag("custom_shutter").performClick()
        onNodeWithTag("custom_import").performClick()
        onNodeWithTag(UiAutomationIds.CAPTURE_OPEN_SETTINGS.value).performClick()
        onNodeWithTag(UiAutomationIds.CAPTURE_CANCEL.value).performClick()
        onNodeWithTag(UiAutomationIds.CAPTURE_SCAN_AGAIN.value).performClick()

        assertEquals(PhotoAnalysisPresenterAction.entries.toList(), clicked.toList())
    }

    @Test
    fun `screen remains a stable status surface when no action is available`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        setContent {
            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel(
                    statusLabel = copy.restoringPhotoAnalysis,
                    state = PhotoAnalysisPresenterState.BACKGROUNDED,
                    actions = emptyList(),
                ),
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(copy = copy),
            )
        }

        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).assertIsDisplayed()
    }
}
