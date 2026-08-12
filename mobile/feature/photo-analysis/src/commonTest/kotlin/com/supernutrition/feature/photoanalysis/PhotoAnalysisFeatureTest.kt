package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisBackgroundSnapshot
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.domain.photoanalysis.RecoveryReason
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoAnalysisFeatureTest {
    @Test
    fun `feature exposes state flow and injected stores`() {
        val feature = feature("attempt-1")

        assertEquals(PhotoAnalysisState.Capture, feature.stateFlow.value)
        assertEquals(PhotoAnalysisBackgroundSnapshot.Capture, feature.snapshotForPersistence())

        feature.dispatch(PhotoAnalysisEvent.PermissionDenied)

        assertEquals(PhotoAnalysisState.Recovery(RecoveryReason.PERMISSION_DENIED), feature.stateFlow.value)
    }

    @Test
    fun `native routing is typed and feature actions stay local`() {
        val feature = feature("attempt-routing")
        val nativeActions = mutableListOf<PhotoAnalysisNativeAction>()

        feature.route(PhotoAnalysisPresenterAction.TakePhoto, nativeActions::add)
        feature.route(PhotoAnalysisPresenterAction.ImportPhoto, nativeActions::add)
        feature.route(PhotoAnalysisPresenterAction.OpenSettings, nativeActions::add)
        feature.route(PhotoAnalysisPresenterAction.Cancel, nativeActions::add)

        assertEquals(
            listOf(
                PhotoAnalysisNativeAction.TakePhoto,
                PhotoAnalysisNativeAction.ImportPhoto,
                PhotoAnalysisNativeAction.OpenSettings,
            ),
            nativeActions,
        )
        assertEquals(PhotoAnalysisState.Capture, feature.state)
    }

    @Test
    fun `native replacement routing clears the retained attempt before opening a picker`() {
        val feature = feature("attempt-native-replacement")
        val ref = feature.issuePhotoRef("retained-photo")
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        feature.dispatch(
            PhotoAnalysisEvent.PreparationSucceeded(
                NormalizedPhotoInput(
                    ref = ref,
                    bytes = byteArrayOf(1),
                    width = 1,
                    height = 1,
                    orientation = ImageOrientation.UP,
                    colorSpace = "sRGB",
                    metadataKeys = emptySet(),
                    contentDigest = "a".repeat(64),
                ),
            ),
        )
        val nativeActions = mutableListOf<PhotoAnalysisNativeAction>()

        feature.route(PhotoAnalysisPresenterAction.ImportPhoto, nativeActions::add)

        assertEquals(PhotoAnalysisState.Capture, feature.state)
        assertEquals(listOf(PhotoAnalysisNativeAction.ImportPhoto), nativeActions)
    }

    @Test
    fun `ignored events do not change the feature state stream`() {
        val feature = feature("attempt-ignored-event")

        feature.dispatch(PhotoAnalysisEvent.ScanAgain)

        assertEquals(PhotoAnalysisState.Capture, feature.stateFlow.value)
    }

    private fun feature(attempt: String) = PhotoAnalysisFeature(
        attemptId = AttemptId(attempt),
        leases = PhotoLeaseStore(),
        sessions = PhotoSessionStore(),
    )
}
