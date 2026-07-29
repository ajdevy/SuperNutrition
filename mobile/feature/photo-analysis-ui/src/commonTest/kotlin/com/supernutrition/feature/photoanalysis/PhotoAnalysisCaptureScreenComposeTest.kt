package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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
        setContent {
            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel.from(
                    com.supernutrition.domain.photoanalysis.PhotoAnalysisState.Capture,
                ),
                onAction = {},
            )
        }

        onNodeWithTag("photo_analysis_title").assertIsDisplayed()
        onNodeWithTag("photo_analysis_status").assertIsDisplayed()
        onNodeWithTag("capture_shutter").assertIsDisplayed()
        onNodeWithTag("capture_import").assertIsDisplayed()
    }

    @Test
    fun `screen renders and dispatches every bounded action`() = runComposeUiTest {
        val clicked = mutableListOf<PhotoAnalysisPresenterAction>()
        val model = PhotoAnalysisPresenterModel(
            statusLabel = "Recovery",
            state = PhotoAnalysisPresenterState.RECOVERY,
            actions = PhotoAnalysisPresenterAction.entries,
        )
        setContent {
            PhotoAnalysisCaptureScreen(
                model = model,
                onAction = clicked::add,
                surface = PhotoAnalysisCaptureSurface(
                    title = "Meal",
                    shutterLabel = "Take",
                    importLabel = "Import",
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
        setContent {
            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel(
                    statusLabel = "Restoring photo analysis",
                    state = PhotoAnalysisPresenterState.BACKGROUNDED,
                    actions = emptyList(),
                ),
                onAction = {},
            )
        }

        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value).assertIsDisplayed()
    }
}
