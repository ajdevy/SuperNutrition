package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.photoanalysis.AttemptId
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
