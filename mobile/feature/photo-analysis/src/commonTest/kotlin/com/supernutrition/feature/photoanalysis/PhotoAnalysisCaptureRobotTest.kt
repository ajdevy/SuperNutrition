package com.supernutrition.feature.photoanalysis

import com.supernutrition.testing.composesupport.SemanticId
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoAnalysisCaptureRobotTest {
    @Test
    fun `robot uses typed semantics and bounded final assertions`() {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val host = FakeHost(
            state = PhotoAnalysisPresenterState.CAPTURE,
            texts = mapOf(
                UiAutomationIds.PHOTO_ANALYSIS_TITLE.value to copy.capture.title,
                UiAutomationIds.PHOTO_ANALYSIS_STATUS.value to copy.status.readyToCapture,
                UiAutomationIds.CAPTURE_SHUTTER.value to copy.capture.takeMealPhoto,
                UiAutomationIds.CAPTURE_IMPORT.value to copy.capture.choosePhoto,
                UiAutomationIds.CAPTURE_SCAN_AGAIN.value to copy.recovery.scanAgain,
            ),
        )
        val robot = PhotoAnalysisCaptureRobot(host, copy)

        robot.assertCaptureSurface()
        robot.takePhoto()
        robot.importPhoto()

        assertEquals(
            listOf(UiAutomationIds.CAPTURE_SHUTTER.value, UiAutomationIds.CAPTURE_IMPORT.value),
            host.clicked,
        )
        assertEquals(
            listOf(
                PhotoAnalysisCaptureRobotContract.CAPTURE_SURFACE_STEP,
                PhotoAnalysisCaptureRobotContract.TAKE_PHOTO_STEP,
                PhotoAnalysisCaptureRobotContract.IMPORT_PHOTO_STEP,
            ),
            robot.trace.steps().map { it.id },
        )
    }

    private class FakeHost(private val state: PhotoAnalysisPresenterState, private val texts: Map<String, String>) :
        PhotoAnalysisCaptureRobotHost {
        val clicked = mutableListOf<String>()

        override fun state(): PhotoAnalysisPresenterState = state

        override fun text(target: SemanticId): String? = texts[target.value]

        override fun click(target: SemanticId) {
            clicked += target.value
        }
    }
}
