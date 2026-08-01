package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationId
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
                surface = PhotoAnalysisCaptureSurface(presenterCopy = copy),
            )
        }

        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value).assertTextEquals(copy.capture.title)
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).assertTextEquals(copy.status.readyToCapture)
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.CAPTURE_SHUTTER.value).assertTextEquals(copy.capture.takeMealPhoto)
        onNodeWithTag(UiAutomationIds.CAPTURE_IMPORT.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.CAPTURE_IMPORT.value).assertTextEquals(copy.capture.choosePhoto)
    }

    @Test
    fun `screen renders and dispatches every bounded action`() = runComposeUiTest {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val clicked = mutableListOf<PhotoAnalysisPresenterAction>()
        val model = PhotoAnalysisPresenterModel(
            statusLabel = copy.recovery.photoCaptureFailed,
            state = PhotoAnalysisPresenterState.RECOVERY,
            actions = PhotoAnalysisPresenterAction.entries,
        )
        setContent {
            PhotoAnalysisCaptureScreen(
                model = model,
                onAction = clicked::add,
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = copy,
                    automationIds = PhotoAnalysisCaptureAutomationIds(
                        shutter = UiAutomationId("custom_shutter"),
                        import = UiAutomationId("custom_import"),
                    ),
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
                    statusLabel = copy.status.restoringPhotoAnalysis,
                    state = PhotoAnalysisPresenterState.BACKGROUNDED,
                    actions = emptyList(),
                ),
                onAction = {},
                surface = PhotoAnalysisCaptureSurface(presenterCopy = copy),
            )
        }

        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).assertIsDisplayed()
    }
}
