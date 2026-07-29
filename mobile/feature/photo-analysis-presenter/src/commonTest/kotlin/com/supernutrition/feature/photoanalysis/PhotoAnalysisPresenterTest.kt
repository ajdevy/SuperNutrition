package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisBackgroundSnapshot
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.domain.photoanalysis.RecoveryReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoAnalysisPresenterTest {
    @Test
    fun `presenter maps every live state and preserves localized copy`() {
        val ref = PhotoRef.issue(AttemptId("presenter-attempt"), "presenter-photo")
        val input = NormalizedPhotoInput(
            ref = ref,
            bytes = byteArrayOf(1),
            width = 1,
            height = 1,
            orientation = ImageOrientation.UP,
            colorSpace = "sRGB",
            metadataKeys = emptySet(),
            contentDigest = "a".repeat(64),
        )
        val copy = PhotoAnalysisPresenterCopy.english().copy(
            readyToCapture = "Pronto",
            openSettings = "Impostazioni",
        )

        assertEquals("Pronto", PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Capture, copy).statusLabel)
        assertEquals(
            "Impostazioni",
            PhotoAnalysisPresenterCopy.english().copy(openSettings = "Impostazioni").openSettings,
        )
        assertEquals(
            listOf(PhotoAnalysisPresenterAction.Cancel),
            PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Ready(input), copy).actions,
        )
        assertEquals(
            PhotoAnalysisPresenterState.BACKGROUNDED,
            PhotoAnalysisPresenterModel.from(
                PhotoAnalysisState.Backgrounded(PhotoAnalysisBackgroundSnapshot.Capture),
                copy,
            ).state,
        )
        assertTrue(
            PhotoAnalysisPresenterModel.from(
                PhotoAnalysisState.Analyzing(ref, com.supernutrition.domain.photoanalysis.RequestId("request")),
                copy,
            ).statusLabel.isNotBlank(),
        )
    }

    @Test
    fun `presenter renders every recovery label and bounded actions`() {
        val expected = mapOf(
            RecoveryReason.PERMISSION_DENIED to "Camera permission is required",
            RecoveryReason.CAMERA_UNAVAILABLE to "Camera is unavailable",
            RecoveryReason.CAPTURE_FAILED to "Photo capture failed",
            RecoveryReason.POOR_IMAGE to "Photo quality is too low",
            RecoveryReason.NO_FOOD to "No food was found in the photo",
            RecoveryReason.OFFLINE to "Analysis is offline",
            RecoveryReason.CANCELLED to "Analysis cancelled",
        )
        expected.forEach { (reason, label) ->
            val model = PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Recovery(reason))
            assertEquals(label, model.statusLabel)
            assertEquals(
                if (reason == RecoveryReason.PERMISSION_DENIED) {
                    listOf(PhotoAnalysisPresenterAction.OpenSettings, PhotoAnalysisPresenterAction.ImportPhoto)
                } else {
                    listOf(PhotoAnalysisPresenterAction.ScanAgain, PhotoAnalysisPresenterAction.ImportPhoto)
                },
                model.actions,
            )
        }
    }

    @Test
    fun `presenter keeps capture actions bounded and excludes remote consent`() {
        val model = PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Capture)

        assertEquals(PhotoAnalysisPresenterState.CAPTURE, model.state)
        assertEquals(
            listOf(PhotoAnalysisPresenterAction.TakePhoto, PhotoAnalysisPresenterAction.ImportPhoto),
            model.actions,
        )
        assertTrue(model.actions.none { it.name.contains("Consent", ignoreCase = true) })
    }
}
