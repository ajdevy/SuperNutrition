package com.supernutrition.feature.photoanalysis

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.platform.photoandroid.AndroidImageNormalizer
import com.supernutrition.platform.photoandroid.AndroidPhotoAttemptIds
import com.supernutrition.platform.photoandroid.AndroidPhotoBridge
import kotlinx.coroutines.launch

/** Android composition root: lifecycle, result launchers, and feature wiring only. */
class PhotoAnalysisComposeActivity : ComponentActivity() {
    private val attemptId = AttemptId(AndroidPhotoAttemptIds.CAPTURE)
    private val retainedState: RetainedState by lazy {
        ViewModelProvider(this)[RetainedState::class.java]
    }
    private val feature: PhotoAnalysisFeature get() = retainedState.feature
    private lateinit var host: PhotoAnalysisAndroidHostController
    private var analysisLauncher: PhotoAnalysisAnalysisLauncher = PhotoAnalysisAnalysisLauncher.None
    private var runtimeBinding: PhotoAnalysisAndroidRuntimeBinding? = null
    private val inlineCameraController = PhotoAnalysisInlineCameraController()
    private var cameraPermissionGranted by mutableStateOf(false)
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
                researchWarning = getString(R.string.capture_research_warning),
                gridHintsTitle = getString(R.string.capture_grid_hints_title),
                gridOverlayDescription = getString(R.string.capture_grid_overlay_description),
                gridCellDescription = getString(R.string.capture_grid_cell_description),
                selectedState = getString(R.string.capture_selected),
                notSelectedState = getString(R.string.capture_not_selected),
                resultPlaceholder = getString(R.string.capture_result_placeholder),
                loadingLabel = getString(R.string.capture_loading),
            ),
            status = StatusCopy(
                readyToCapture = getString(R.string.capture_status_ready),
                preparingPhoto = getString(R.string.capture_status_preparing),
                photoReady = getString(R.string.capture_status_ready_photo),
                analyzingPhoto = getString(R.string.capture_status_analyzing),
                findingVisibleFoods = getString(R.string.capture_status_finding_visible_foods),
                identifyingDish = getString(R.string.capture_status_identifying_dish),
                estimatingPortions = getString(R.string.capture_status_estimating_portions),
                checkingResults = getString(R.string.capture_status_checking_results),
                reviewCandidate = getString(R.string.capture_status_review),
                restoringPhotoAnalysis = getString(R.string.capture_status_restoring),
            ),
            evidence = EvidenceCopy(
                fromThisPhoto = getString(R.string.capture_evidence_from_photo),
                highConfidence = getString(R.string.capture_evidence_high_confidence),
                mediumConfidence = getString(R.string.capture_evidence_medium_confidence),
                lowConfidence = getString(R.string.capture_evidence_low_confidence),
                identityClear = getString(R.string.capture_evidence_identity_clear),
                identityAmbiguous = getString(R.string.capture_evidence_identity_ambiguous),
                mixedContent = getString(R.string.capture_evidence_mixed_content),
                fullyVisible = getString(R.string.capture_evidence_fully_visible),
                partiallyOccluded = getString(R.string.capture_evidence_partially_occluded),
                regionAvailable = getString(R.string.capture_evidence_region_available),
                regionUnavailable = getString(R.string.capture_evidence_region_unavailable),
                estimatedMass = getString(R.string.capture_evidence_estimated_mass),
                massNotEvaluable = getString(R.string.capture_evidence_mass_not_evaluable),
                massAbstained = getString(R.string.capture_evidence_mass_abstained),
                localProvenance = getString(R.string.capture_evidence_local_provenance),
                remoteProvenance = getString(R.string.capture_evidence_remote_provenance),
                selected = getString(R.string.capture_evidence_selected),
                notSelected = getString(R.string.capture_evidence_not_selected),
                dishCandidatesTitle = getString(R.string.capture_evidence_dish_candidates),
                visibleComponentsTitle = getString(R.string.capture_evidence_visible_components),
                visibleRegionCount = getString(R.string.capture_evidence_visible_region_count),
                compositionHintsTitle = getString(R.string.capture_evidence_composition_hints),
                compositionHintSource = getString(R.string.capture_evidence_composition_hint_source),
                partialDegradation = getString(R.string.capture_evidence_partial_degradation),
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
                reviewRestoreUnavailable = getString(R.string.capture_recovery_review_restore_unavailable),
            ),
        )
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        host.onActivityResult(result.resultCode, result.data, isCamera = false)
    }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted = granted
        host.onPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreActivityState(savedInstanceState)
        cameraPermissionGranted = checkSelfPermission(Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val photoBridge = AndroidPhotoBridge(
            context = this,
            permissionRequestInFlight = retainedState.captureOperation.permissionRequestInFlight,
        )
        bindAnalysisRuntime()
        host = createHost(photoBridge).also { it.initialize() }
        inlineCameraController.onCaptureResult = { operationId, result ->
            host.onInlinePhotoResult(operationId, result)
        }
        restoreFeatureSnapshot(savedInstanceState)
        observeRetainedPhotoLifecycle()
        renderContent()
    }

    private fun restoreActivityState(savedInstanceState: Bundle?) {
        wasBackgrounded = retainedState.wasBackgrounded
        savedInstanceState?.getString(PENDING_OPERATION_ID_KEY)?.let {
            retainedState.captureOperation.pendingOperationId = it
        }
        // Android does not replay an Activity Result permission callback after
        // process death. Restoring this transient guard would prevent a retry.
        retainedState.captureOperation.permissionRequestInFlight = false
    }

    private fun bindAnalysisRuntime() {
        val factory = (application as? PhotoAnalysisAndroidRuntimeProvider)?.photoAnalysisRuntimeFactory
        runtimeBinding = factory?.create(
            context = this,
            feature = feature,
            leases = retainedState.leases,
            sessions = retainedState.sessions,
            scope = lifecycleScope,
        )
        analysisLauncher = runtimeBinding?.analysisLauncher ?: PhotoAnalysisAnalysisLauncher.None
    }

    private fun createHost(photoBridge: AndroidPhotoBridge): PhotoAnalysisAndroidHostController =
        PhotoAnalysisAndroidHostController(
            feature = feature,
            photoBridge = photoBridge,
            normalizer = AndroidImageNormalizer(),
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
            capturePhoto = inlineCameraController::requestCapture,
            launchImport = { importLauncher.launch(photoBridge.photoPickerIntent()) },
            launchSettings = { startActivity(photoBridge.settingsIntent()) },
            captureOperation = retainedState.captureOperation,
            onRetainedPhotoReady = retainedState.previewStore::show,
            onRetainedPhotoInvalidated = retainedState.previewStore::clear,
            analysisLauncher = analysisLauncher,
        )

    private fun restoreFeatureSnapshot(savedInstanceState: Bundle?) {
        PhotoAnalysisSnapshotBundleCodec.decode(savedInstanceState, attemptId)?.let { snapshot ->
            feature.dispatch(com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent.RestoreFromSnapshot(snapshot))
        }
    }

    private fun renderContent() {
        setContent {
            val preview by retainedState.previewStore.preview.collectAsState()
            PhotoAnalysisCaptureContent(
                stateFlow = feature.stateFlow,
                onAction = host::handleAction,
                modifier = Modifier
                    .statusBarsPadding()
                    .semantics { testTagsAsResourceId = true },
                surface = PhotoAnalysisCaptureSurface(
                    presenterCopy = presentationCopy,
                    liveCamera = if (preview == null) {
                        {
                            PhotoAnalysisLiveCameraPreview(
                                cameraPermissionGranted = cameraPermissionGranted,
                                permissionHint = presentationCopy.recovery.cameraPermissionRequired,
                                unavailableHint = presentationCopy.recovery.cameraUnavailable,
                                cameraController = inlineCameraController,
                            )
                        }
                    } else {
                        null
                    },
                    retainedPhoto = preview?.let { current -> { AndroidRetainedPhotoBackdrop(current) } },
                    photoTextContrast = preview?.let { current ->
                        if (current.hasLightBackdrop) {
                            PhotoAnalysisPhotoTextContrast.DARK_TEXT
                        } else {
                            PhotoAnalysisPhotoTextContrast.LIGHT_TEXT
                        }
                    },
                    gridHintsEnabled = runtimeBinding != null,
                    onGridCellSelected = { index -> host.handleGridCellSelected(index) },
                    // Keep camera and captured photo in exactly the same slot.
                    retainedPhotoAspectRatio = 4f / 3f,
                ),
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

    override fun onDestroy() {
        inlineCameraController.clear()
        runtimeBinding?.close()
        runtimeBinding = null
        super.onDestroy()
    }

    private fun observeRetainedPhotoLifecycle() {
        lifecycleScope.launch {
            feature.stateFlow.collect(retainedState.previewStore::retainFor)
        }
    }

    class RetainedState : ViewModel() {
        internal val leases = PhotoLeaseStore()
        internal val sessions = PhotoSessionStore()
        internal val previewStore = AndroidRetainedPhotoPreviewStore()
        val feature = PhotoAnalysisFeature(
            attemptId = AttemptId(AndroidPhotoAttemptIds.CAPTURE),
            leases = leases,
            sessions = sessions,
        )
        val captureOperation = CaptureOperationState()
        var wasBackgrounded: Boolean = false

        override fun onCleared() {
            previewStore.clear()
            super.onCleared()
        }
    }

    private companion object {
        const val PENDING_OPERATION_ID_KEY = "photo_analysis.pending_operation_id"
        const val PERMISSION_REQUEST_IN_FLIGHT_KEY = "photo_analysis.permission_request_in_flight"
    }
}
