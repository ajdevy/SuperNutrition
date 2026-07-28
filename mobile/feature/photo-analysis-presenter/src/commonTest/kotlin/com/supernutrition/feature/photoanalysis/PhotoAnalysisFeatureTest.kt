package com.supernutrition.feature.photoanalysis

import com.supernutrition.core.config.ConfigurationSnapshot
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisBackgroundSnapshot
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.domain.photoanalysis.RecoveryReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoAnalysisFeatureTest {
    @Test
    fun `feature starts on capture with bounded capture surface`() {
        val feature = PhotoAnalysisFeature(
            attemptId = AttemptId("attempt-1"),
            configurationSnapshot = snapshot("attempt-1", "b"),
        )

        assertEquals(PhotoAnalysisState.Capture, feature.state)
        assertEquals(2, CaptureSurfaceUi().requiredTapBudget)
        assertEquals(1, CaptureSurfaceUi().interactionBudgetMaxima().auxiliary.back)
        assertEquals(1, CaptureSurfaceUi().interactionBudgetMaxima().auxiliary.permissionPrompt)
        assertEquals(PhotoAnalysisBackgroundSnapshot.Capture, feature.snapshotForPersistence())
    }

    @Test
    fun `persistence snapshot is opaque and follows the reducer lifecycle`() {
        val feature = PhotoAnalysisFeature(
            attemptId = AttemptId("attempt-snapshot"),
            configurationSnapshot = snapshot("attempt-snapshot", "c"),
        )

        val ref = feature.issuePhotoRef("opaque-photo")
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        assertEquals(PhotoAnalysisBackgroundSnapshot.Preparing(ref), feature.snapshotForPersistence())
    }

    @Test
    fun `presenter exposes only the bounded capture actions on capture`() {
        val model = PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Capture)

        assertEquals(PhotoAnalysisPresenterState.CAPTURE, model.state)
        assertEquals(
            listOf(PhotoAnalysisPresenterAction.TakePhoto, PhotoAnalysisPresenterAction.ImportPhoto),
            model.actions,
        )
        assertEquals("capture_shutter", model.shutterAutomationId)
        assertEquals("capture_import", model.importAutomationId)
    }

    @Test
    fun `presenter makes recovery explicit and never adds a remote consent action`() {
        val model = PhotoAnalysisPresenterModel.from(
            PhotoAnalysisState.Recovery(reason = RecoveryReason.PERMISSION_DENIED),
        )

        assertEquals(PhotoAnalysisPresenterState.RECOVERY, model.state)
        assertEquals(
            listOf(PhotoAnalysisPresenterAction.OpenSettings, PhotoAnalysisPresenterAction.ImportPhoto),
            model.actions,
        )
    }

    @Test
    fun `presenter exposes no-food and cancellation recovery`() {
        val noFood = PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Recovery(RecoveryReason.NO_FOOD))
        val cancelled = PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Recovery(RecoveryReason.CANCELLED))

        assertEquals("No food was found in the photo", noFood.statusLabel)
        assertEquals("Analysis cancelled", cancelled.statusLabel)
        assertEquals(
            listOf(PhotoAnalysisPresenterAction.ScanAgain, PhotoAnalysisPresenterAction.ImportPhoto),
            cancelled.actions,
        )
    }

    @Test
    fun `presenter keeps every typed recovery state actionable`() {
        val retryableReasons = listOf(
            RecoveryReason.CAMERA_UNAVAILABLE,
            RecoveryReason.CAPTURE_FAILED,
            RecoveryReason.POOR_IMAGE,
            RecoveryReason.NO_FOOD,
            RecoveryReason.OFFLINE,
            RecoveryReason.CANCELLED,
        )

        retryableReasons.forEach { reason ->
            val model = PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Recovery(reason))

            assertEquals(PhotoAnalysisPresenterState.RECOVERY, model.state)
            assertEquals(
                listOf(PhotoAnalysisPresenterAction.ScanAgain, PhotoAnalysisPresenterAction.ImportPhoto),
                model.actions,
            )
            assertTrue(model.statusLabel.isNotBlank())
        }
    }

    @Test
    fun `presenter maps every live state and preserves custom surface copy`() {
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
        val surface = CaptureSurfaceUi(
            title = "Meal",
            shutterLabel = "Capture",
            importLabel = "Select",
            shutterAutomationId = "meal_capture",
            importAutomationId = "meal_import",
        )

        val models = listOf(
            PhotoAnalysisState.Preparing(ref),
            PhotoAnalysisState.Ready(input),
            PhotoAnalysisState.Analyzing(ref, com.supernutrition.domain.photoanalysis.RequestId("request")),
            PhotoAnalysisState.CandidateReview(ref, com.supernutrition.domain.photoanalysis.RequestId("request")),
            PhotoAnalysisState.Backgrounded(PhotoAnalysisBackgroundSnapshot.Capture),
        ).map { state -> PhotoAnalysisPresenterModel.from(state, surface) }

        assertEquals(
            listOf(
                PhotoAnalysisPresenterState.PREPARING,
                PhotoAnalysisPresenterState.READY,
                PhotoAnalysisPresenterState.ANALYZING,
                PhotoAnalysisPresenterState.CANDIDATE_REVIEW,
                PhotoAnalysisPresenterState.BACKGROUNDED,
            ),
            models.map { it.state },
        )
        assertEquals(listOf(PhotoAnalysisPresenterAction.Cancel), models[0].actions)
        assertEquals(listOf(PhotoAnalysisPresenterAction.Cancel), models[1].actions)
        assertEquals(listOf(PhotoAnalysisPresenterAction.Cancel), models[2].actions)
        assertEquals(listOf(PhotoAnalysisPresenterAction.Cancel), models[3].actions)
        assertTrue(models[4].actions.isEmpty())
        models.forEach { model ->
            assertEquals("Meal", model.title)
            assertEquals("Capture", model.shutterLabel)
            assertEquals("Select", model.importLabel)
            assertEquals("meal_capture", model.shutterAutomationId)
            assertEquals("meal_import", model.importAutomationId)
            assertTrue(model.statusLabel.isNotBlank())
        }
    }

    @Test
    fun `presenter renders every recovery label`() {
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
            assertEquals(label, PhotoAnalysisPresenterModel.from(PhotoAnalysisState.Recovery(reason)).statusLabel)
        }
    }

    @Test
    fun `state observer follows dispatch and can be disposed`() {
        val feature = PhotoAnalysisFeature(
            attemptId = AttemptId("attempt-observer"),
            configurationSnapshot = snapshot("attempt-observer", "d"),
        )
        var notifications = 0
        val dispose = feature.observe { notifications += 1 }

        feature.dispatch(PhotoAnalysisEvent.PermissionDenied)
        assertEquals(1, notifications)
        dispose()
        dispose()
        feature.dispatch(PhotoAnalysisEvent.ScanAgain)
        assertEquals(1, notifications)
    }

    @Test
    fun `each observer disposer is independently idempotent`() {
        val feature = PhotoAnalysisFeature(
            attemptId = AttemptId("attempt-duplicate-observer"),
            configurationSnapshot = snapshot("attempt-duplicate-observer", "e"),
        )
        var notifications = 0
        val observer: () -> Unit = { notifications += 1 }
        val disposeFirst = feature.observe(observer)
        val disposeSecond = feature.observe(observer)

        disposeFirst()
        disposeFirst()
        feature.dispatch(PhotoAnalysisEvent.PermissionDenied)
        assertEquals(1, notifications)

        disposeSecond()
        disposeSecond()
        feature.dispatch(PhotoAnalysisEvent.ScanAgain)
        assertEquals(1, notifications)
    }

    @Test
    fun `ignored events do not notify state observers`() {
        val feature = PhotoAnalysisFeature(
            attemptId = AttemptId("attempt-ignored-event"),
            configurationSnapshot = snapshot("attempt-ignored-event", "f"),
        )
        var notifications = 0
        feature.observe { notifications += 1 }

        feature.dispatch(PhotoAnalysisEvent.ScanAgain)

        assertEquals(0, notifications)
    }

    private fun snapshot(id: String, digestSeed: String): ConfigurationSnapshot = ConfigurationSnapshot(
        snapshotId = id,
        revision = 1,
        defaultLocale = "en-US",
        measurementSystem = "metric",
        catalogCountry = "US",
        marketJurisdiction = "US",
        processingRegion = "us-east",
        policyVersion = "v1",
        digest = digestSeed.repeat(64),
    )
}
