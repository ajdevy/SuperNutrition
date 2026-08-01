package com.supernutrition.feature.photoanalysis

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.platform.photoandroid.AndroidImageNormalizer
import com.supernutrition.platform.photoandroid.AndroidPhotoAttemptIds
import com.supernutrition.platform.photoandroid.AndroidPhotoBridge

/** Android composition root: lifecycle, result launchers, and feature wiring only. */
class PhotoAnalysisComposeActivity : ComponentActivity() {
    private val attemptId = AttemptId(AndroidPhotoAttemptIds.CAPTURE)
    private val retainedState: RetainedState by lazy {
        ViewModelProvider(this)[RetainedState::class.java]
    }
    private val feature: PhotoAnalysisFeature get() = retainedState.feature
    private lateinit var host: PhotoAnalysisAndroidHostController
    private var permissionRationale: android.app.AlertDialog? = null
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
        wasBackgrounded = retainedState.wasBackgrounded
        savedInstanceState?.getString(PENDING_OPERATION_ID_KEY)?.let {
            retainedState.captureOperation.pendingOperationId = it
        }
        // Android does not replay an Activity Result permission callback after
        // process death. Restoring this transient guard would prevent a retry.
        retainedState.captureOperation.permissionRequestInFlight = false
        val photoBridge = AndroidPhotoBridge(
            context = this,
            permissionRequestInFlight = retainedState.captureOperation.permissionRequestInFlight,
        )
        host = PhotoAnalysisAndroidHostController(
            feature = feature,
            photoBridge = photoBridge,
            normalizer = AndroidImageNormalizer(),
            callbacks = PhotoAnalysisHostCallbacks(
                requestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                showCameraPermissionRationale = { onContinue ->
                    permissionRationale?.dismiss()
                    if (!isFinishing && !isDestroyed) {
                        permissionRationale = android.app.AlertDialog.Builder(this)
                            .setMessage(R.string.capture_permission_rationale)
                            .setNegativeButton(R.string.capture_cancel, null)
                            .setPositiveButton(R.string.capture_permission_continue) { _, _ -> onContinue() }
                            .create()
                            .also { it.show() }
                    }
                },
                launchCamera = { operationId -> cameraLauncher.launch(photoBridge.cameraCaptureIntent(operationId)) },
                launchImport = { importLauncher.launch(photoBridge.photoPickerIntent()) },
                launchSettings = { startActivity(photoBridge.settingsIntent()) },
            ),
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
                surface = PhotoAnalysisCaptureSurface(presenterCopy = presentationCopy),
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAll(PhotoAnalysisSnapshotBundleCodec.encode(feature.snapshotForPersistence(), attemptId))
        retainedState.captureOperation.pendingOperationId?.let {
            outState.putString(PENDING_OPERATION_ID_KEY, it)
        }
        outState.putBoolean(
            PERMISSION_REQUEST_IN_FLIGHT_KEY,
            retainedState.captureOperation.permissionRequestInFlight,
        )
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
        permissionRationale?.dismiss()
        permissionRationale = null
        feature.dispatch(com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent.Backgrounded)
        wasBackgrounded = true
        retainedState.wasBackgrounded = true
        super.onStop()
    }

    class RetainedState : ViewModel() {
        val feature = PhotoAnalysisFeature(
            attemptId = AttemptId(AndroidPhotoAttemptIds.CAPTURE),
            leases = PhotoLeaseStore(),
            sessions = PhotoSessionStore(),
        )
        val captureOperation = CaptureOperationState()
        var wasBackgrounded: Boolean = false
    }

    private companion object {
        const val PENDING_OPERATION_ID_KEY = "photo_analysis.pending_operation_id"
        const val PERMISSION_REQUEST_IN_FLIGHT_KEY = "photo_analysis.permission_request_in_flight"
    }
}
