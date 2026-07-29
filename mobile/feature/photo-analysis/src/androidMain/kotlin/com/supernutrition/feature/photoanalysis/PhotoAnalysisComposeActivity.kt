package com.supernutrition.feature.photoanalysis

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.platform.photoandroid.AndroidImageNormalizer
import com.supernutrition.platform.photoandroid.AndroidPhotoBridge

/** Android composition root: lifecycle, result launchers, and feature wiring only. */
class PhotoAnalysisComposeActivity : ComponentActivity() {
    private val attemptId = AttemptId(ATTEMPT_ID)
    private val feature = PhotoAnalysisFeature(
        attemptId = attemptId,
        leases = PhotoLeaseStore(),
        sessions = PhotoSessionStore(),
    )
    private lateinit var host: PhotoAnalysisAndroidHostController
    private var wasBackgrounded = false

    private val presentationCopy: PhotoAnalysisPresenterCopy by lazy {
        PhotoAnalysisPresenterCopy.english().let { copy ->
            copy.copy(
                captureTitle = localized("capture_title", copy.captureTitle),
                takeMealPhoto = localized("capture_shutter", copy.takeMealPhoto),
                choosePhoto = localized("capture_import", copy.choosePhoto),
                openSettings = localized("capture_open_settings", copy.openSettings),
                scanAgain = localized("capture_scan_again", copy.scanAgain),
                cancel = localized("capture_cancel", copy.cancel),
                readyToCapture = localized("capture_status_ready", copy.readyToCapture),
                preparingPhoto = localized("capture_status_preparing", copy.preparingPhoto),
                photoReady = localized("capture_status_ready_photo", copy.photoReady),
                analyzingPhoto = localized("capture_status_analyzing", copy.analyzingPhoto),
                reviewCandidate = localized("capture_status_review", copy.reviewCandidate),
                restoringPhotoAnalysis = localized("capture_status_restoring", copy.restoringPhotoAnalysis),
                cameraPermissionRequired = localized("capture_recovery_permission", copy.cameraPermissionRequired),
                cameraUnavailable = localized("capture_recovery_unavailable", copy.cameraUnavailable),
                photoCaptureFailed = localized("capture_recovery_failed", copy.photoCaptureFailed),
                photoQualityTooLow = localized("capture_recovery_quality", copy.photoQualityTooLow),
                noFoodFound = localized("capture_recovery_no_food", copy.noFoodFound),
                analysisOffline = localized("capture_recovery_offline", copy.analysisOffline),
                analysisCancelled = localized("capture_recovery_cancelled", copy.analysisCancelled),
            )
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        host.onActivityResult(result.resultCode, result.data, isCamera = true)
    }
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        host.onActivityResult(result.resultCode, result.data, isCamera = false)
    }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        host.onPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val photoBridge = AndroidPhotoBridge(this)
        host = PhotoAnalysisAndroidHostController(
            feature = feature,
            photoBridge = photoBridge,
            normalizer = AndroidImageNormalizer(),
            requestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            launchCamera = { cameraLauncher.launch(photoBridge.cameraCaptureIntent(ATTEMPT_ID)) },
            launchImport = { importLauncher.launch(photoBridge.photoPickerIntent()) },
            launchSettings = { startActivity(photoBridge.settingsIntent()) },
        ).also { it.initialize() }

        PhotoAnalysisSnapshotBundleCodec.decode(savedInstanceState, attemptId)?.let { snapshot ->
            feature.dispatch(com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent.RestoreFromSnapshot(snapshot))
        }
        setContent {
            PhotoAnalysisCaptureContent(
                stateFlow = feature.stateFlow,
                onAction = host::handleAction,
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                surface = PhotoAnalysisCaptureSurface(copy = presentationCopy),
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAll(PhotoAnalysisSnapshotBundleCodec.encode(feature.snapshotForPersistence(), attemptId))
    }

    override fun onStart() {
        super.onStart()
        if (wasBackgrounded) {
            feature.dispatch(com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent.Restored)
            wasBackgrounded = false
        }
    }

    override fun onStop() {
        feature.dispatch(com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent.Backgrounded)
        wasBackgrounded = true
        super.onStop()
    }

    private fun localized(name: String, fallback: String): String {
        val resourceId = resources.getIdentifier(name, "string", packageName)
        return if (resourceId == 0) fallback else getString(resourceId)
    }

    private companion object {
        const val ATTEMPT_ID = "android-photo-attempt"
    }
}
