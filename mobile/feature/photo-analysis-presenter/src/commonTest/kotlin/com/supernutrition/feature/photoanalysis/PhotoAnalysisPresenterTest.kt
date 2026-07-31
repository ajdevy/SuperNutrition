package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisBackgroundSnapshot
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.domain.photoanalysis.RequestId
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
        val baseCopy = PhotoAnalysisPresenterCopyFixtures.localized()
        val copy = baseCopy.copy(
            status = baseCopy.status.copy(readyToCapture = "Pronto"),
            capture = baseCopy.capture.copy(openSettings = "Impostazioni"),
        )

        assertEquals(
            copy.status.readyToCapture,
            PhotoAnalysisPresenterModel.from(
                PhotoAnalysisState.Capture,
                copy,
            ).statusLabel,
        )
        assertEquals(
            copy.recovery.cameraPermissionRequired,
            PhotoAnalysisPresenterModel.from(
                PhotoAnalysisState.Recovery(RecoveryReason.PERMISSION_DENIED),
                copy,
            ).statusLabel,
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
                PhotoAnalysisState.Analyzing(ref, RequestId("request")),
                copy,
            ).statusLabel.isNotBlank(),
        )
    }

    @Test
    fun `presenter renders every recovery label and bounded actions`() {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        val expected = mapOf(
            RecoveryReason.PERMISSION_DENIED to copy.recovery.cameraPermissionRequired,
            RecoveryReason.CAMERA_UNAVAILABLE to copy.recovery.cameraUnavailable,
            RecoveryReason.CAPTURE_FAILED to copy.recovery.photoCaptureFailed,
            RecoveryReason.POOR_IMAGE to copy.recovery.photoQualityTooLow,
            RecoveryReason.NO_FOOD to copy.recovery.noFoodFound,
            RecoveryReason.OFFLINE to copy.recovery.analysisOffline,
            RecoveryReason.CANCELLED to copy.recovery.analysisCancelled,
        )
        expected.forEach { (reason, label) ->
            val model = PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Recovery(reason), copy)
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
        val model = PhotoAnalysisPresenterModel.from(
            PhotoAnalysisState.Capture,
            PhotoAnalysisPresenterCopyFixtures.localized(),
        )

        assertEquals(PhotoAnalysisPresenterState.CAPTURE, model.state)
        assertEquals(
            listOf(PhotoAnalysisPresenterAction.TakePhoto, PhotoAnalysisPresenterAction.ImportPhoto),
            model.actions,
        )
        assertEquals(
            setOf(PhotoAnalysisPresenterAction.TakePhoto, PhotoAnalysisPresenterAction.ImportPhoto),
            model.actions.toSet(),
        )
    }
}
