package com.supernutrition.feature.photoanalysis

import com.supernutrition.testing.composesupport.SemanticId
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoAnalysisCaptureRobotTest {
    @Test
    fun `robot uses typed semantics and bounded final assertions`() {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val host = FakeHost(
            state = PhotoAnalysisPresenterState.CAPTURE,
            texts = mapOf(
                "photo_analysis_title" to copy.capture.title,
                "photo_analysis_status" to copy.status.readyToCapture,
                "capture_shutter" to copy.capture.takeMealPhoto,
                "capture_import" to copy.capture.choosePhoto,
                "capture_scan_again" to copy.recovery.scanAgain,
            ),
        )
        val robot = PhotoAnalysisCaptureRobot(host, copy)

        robot.assertCaptureSurface()
        robot.takePhoto()
        robot.importPhoto()

        assertEquals(listOf("capture_shutter", "capture_import"), host.clicked)
        assertEquals(
            listOf("capture-surface", "take-photo", "import-photo"),
            robot.trace.steps().map { it.id.value },
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
