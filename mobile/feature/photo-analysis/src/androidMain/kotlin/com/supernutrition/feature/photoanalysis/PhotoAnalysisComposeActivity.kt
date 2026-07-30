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
import com.supernutrition.platform.photoandroid.AndroidPhotoAttemptIds
import com.supernutrition.platform.photoandroid.AndroidPhotoBridge

/** Android composition root: lifecycle, result launchers, and feature wiring only. */
class PhotoAnalysisComposeActivity : ComponentActivity() {
    private val attemptId = AttemptId(AndroidPhotoAttemptIds.CAPTURE)
    private lateinit var retainedState: RetainedState
    private val feature: PhotoAnalysisFeature get() = retainedState.feature
    private lateinit var host: PhotoAnalysisAndroidHostController
    private var wasBackgrounded = false

    private val presentationCopy: PhotoAnalysisPresenterCopy by lazy {
        PhotoAnalysisPresenterCopy(
            capture = CaptureCopy(
                title = getString(R.string.capture_title),
                takeMealPhoto = getString(R.string.capture_shutter),
                choosePhoto = getString(R.string.capture_import),
                openSettings = getString(R.string.capture_open_settings),
                cancel = getString(R.string.capture_cancel),
            ),
            status = StatusCopy(
                readyToCapture = getString(R.string.capture_status_ready),
                preparingPhoto = getString(R.string.capture_status_preparing),
                photoReady = getString(R.string.capture_status_ready_photo),
                analyzingPhoto = getString(R.string.capture_status_analyzing),
                reviewCandidate = getString(R.string.capture_status_review),
                restoringPhotoAnalysis = getString(R.string.capture_status_restoring),
            ),
            recovery = RecoveryCopy(
                scanAgain = getString(R.string.capture_scan_again),
                cameraPermissionRequired = getString(R.string.capture_recovery_permission),
                cameraUnavailable = getString(R.string.capture_recovery_unavailable),
                photoCaptureFailed = getString(R.string.capture_recovery_failed),
                photoQualityTooLow = getString(R.string.capture_recovery_quality),
                noFoodFound = getString(R.string.capture_recovery_no_food),
                analysisOffline = getString(R.string.capture_recovery_offline),
                analysisCancelled = getString(R.string.capture_recovery_cancelled),
            ),
        )
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
        retainedState = lastNonConfigurationInstance as? RetainedState ?: RetainedState(attemptId)
        wasBackgrounded = retainedState.wasBackgrounded
        val photoBridge = AndroidPhotoBridge(this)
        host = PhotoAnalysisAndroidHostController(
            feature = feature,
            photoBridge = photoBridge,
            normalizer = AndroidImageNormalizer(),
            requestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            launchCamera = { operationId ->
                cameraLauncher.launch(photoBridge.cameraCaptureIntent(AndroidPhotoAttemptIds.CAPTURE, operationId))
            },
            launchImport = { importLauncher.launch(photoBridge.photoPickerIntent()) },
            launchSettings = { startActivity(photoBridge.settingsIntent()) },
            captureOperation = retainedState.captureOperation,
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

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRetainCustomNonConfigurationInstance(): Any = retainedState

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAll(PhotoAnalysisSnapshotBundleCodec.encode(feature.snapshotForPersistence(), attemptId))
    }

    override fun onStart() {
        super.onStart()
        if (wasBackgrounded) {
            feature.dispatch(com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent.Restored)
            wasBackgrounded = false
            retainedState.wasBackgrounded = false
        }
    }

    override fun onStop() {
        feature.dispatch(com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent.Backgrounded)
        wasBackgrounded = true
        retainedState.wasBackgrounded = true
        super.onStop()
    }

    private class RetainedState(attemptId: AttemptId) {
        val feature = PhotoAnalysisFeature(
            attemptId = attemptId,
            leases = PhotoLeaseStore(),
            sessions = PhotoSessionStore(),
        )
        val captureOperation = CaptureOperationState()
        var wasBackgrounded: Boolean = false
    }

}
