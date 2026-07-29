package com.supernutrition.feature.photoanalysis

import com.supernutrition.testing.composesupport.SemanticId
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoAnalysisCaptureRobotTest {
    @Test
    fun `robot uses typed semantics and bounded final assertions`() {
        val host = FakeHost(
            state = PhotoAnalysisPresenterState.CAPTURE,
            texts = mapOf(
                "photo_analysis_title" to "Capture",
                "photo_analysis_status" to "Ready to capture",
                "capture_shutter" to "Take meal photo",
                "capture_import" to "Choose photo",
                "capture_scan_again" to "Scan again",
            ),
        )
        val robot = PhotoAnalysisCaptureRobot(host, testCopy())

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

    private fun testCopy() = PhotoAnalysisPresenterCopy(
        captureTitle = "Capture",
        takeMealPhoto = "Take meal photo",
        choosePhoto = "Choose photo",
        openSettings = "Open settings",
        scanAgain = "Scan again",
        cancel = "Cancel",
        readyToCapture = "Ready to capture",
        preparingPhoto = "Preparing photo",
        photoReady = "Photo ready",
        analyzingPhoto = "Analyzing photo",
        reviewCandidate = "Review candidate",
        restoringPhotoAnalysis = "Restoring photo analysis",
        cameraPermissionRequired = "Camera permission is required",
        cameraUnavailable = "Camera is unavailable",
        photoCaptureFailed = "Photo capture failed",
        photoQualityTooLow = "Photo quality is too low",
        noFoodFound = "No food was found in the photo",
        analysisOffline = "Analysis is offline",
        analysisCancelled = "Analysis cancelled",
    )
}
