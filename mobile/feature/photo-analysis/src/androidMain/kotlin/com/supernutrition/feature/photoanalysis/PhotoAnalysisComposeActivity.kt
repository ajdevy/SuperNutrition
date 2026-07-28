package com.supernutrition.feature.photoanalysis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.supernutrition.core.config.ConfigurationSnapshot
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.RecoveryReason
import com.supernutrition.feature.photoanalysis.PhotoAnalysisCaptureScreen
import com.supernutrition.platform.photoandroid.AndroidImageNormalizer
import com.supernutrition.platform.photoandroid.AndroidPhotoBridge
import com.supernutrition.platform.photoandroid.CameraAvailability

/** Thin Android host: lifecycle and native intents only; product UI stays Compose-owned. */
class PhotoAnalysisComposeActivity : ComponentActivity() {
    private lateinit var feature: PhotoAnalysisFeature
    private lateinit var photoBridge: AndroidPhotoBridge
    private lateinit var composeView: ComposeView
    private var wasBackgrounded = false
    private var captureSequence = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val attemptId = AttemptId("android-photo-attempt")
        feature = PhotoAnalysisFeature(
            attemptId = attemptId,
            configurationSnapshot = ConfigurationSnapshot(
                snapshotId = attemptId.value,
                revision = 1,
                defaultLocale = "en-US",
                measurementSystem = "metric",
                catalogCountry = "US",
                marketJurisdiction = "US",
                processingRegion = "us-east",
                policyVersion = "v1",
                digest = "a".repeat(64),
            ),
        )
        PhotoAnalysisSnapshotBundleCodec.decode(savedInstanceState, attemptId)?.let { snapshot ->
            feature.dispatch(PhotoAnalysisEvent.RestoreFromSnapshot(snapshot))
        }
        photoBridge = AndroidPhotoBridge(this)
        photoBridge.cleanupStaleCaptureFiles()
        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        setContentView(composeView)
        composeView.setContent {
            val state = remember { mutableStateOf(feature.state) }
            DisposableEffect(feature) {
                val dispose = feature.observe { state.value = feature.state }
                onDispose(dispose)
            }

            PhotoAnalysisCaptureScreen(
                model = PhotoAnalysisPresenterModel.from(state.value),
                onAction = ::handleAction,
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                bootstrapLaunchMarker = true,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putAll(PhotoAnalysisSnapshotBundleCodec.encode(feature.snapshotForPersistence()))
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (wasBackgrounded) {
            feature.dispatch(PhotoAnalysisEvent.Restored)
            wasBackgrounded = false
        }
    }

    override fun onStop() {
        feature.dispatch(PhotoAnalysisEvent.Backgrounded)
        wasBackgrounded = true
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startActivityForResult(
                photoBridge.cameraCaptureIntent(ATTEMPT_ID),
                CAMERA_CAPTURE_REQUEST,
            )
        } else {
            feature.dispatch(PhotoAnalysisEvent.PermissionDenied)
        }
    }

    @Deprecated("Activity result API retained for the thin native bridge boundary")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CAMERA_CAPTURE_REQUEST && requestCode != IMPORT_REQUEST) return
        if (resultCode == RESULT_OK) {
            captureSequence += 1
            val ref = feature.issuePhotoRef("android-capture-$captureSequence")
            feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
            val sourceBytes = photoBridge.photoBytesFromResult(data)
            if (sourceBytes == null) {
                feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAPTURE_FAILED))
            } else {
                runCatching {
                    AndroidImageNormalizer().normalize(ref, sourceBytes)
                }.onSuccess { normalized ->
                    feature.dispatch(PhotoAnalysisEvent.PreparationSucceeded(normalized))
                }.onFailure {
                    feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.POOR_IMAGE))
                }
            }
        } else {
            feature.dispatch(
                PhotoAnalysisEvent.PreparationFailed(
                    if (requestCode == CAMERA_CAPTURE_REQUEST && photoBridge.isCaptureFailure(data)) {
                        RecoveryReason.CAPTURE_FAILED
                    } else {
                        RecoveryReason.CANCELLED
                    },
                ),
            )
        }
    }

    private fun handleAction(action: PhotoAnalysisPresenterAction) {
        when (action) {
            PhotoAnalysisPresenterAction.TakePhoto -> openCamera()

            PhotoAnalysisPresenterAction.ImportPhoto -> runCatching {
                startActivityForResult(photoBridge.photoPickerIntent(), IMPORT_REQUEST)
            }.onFailure {
                feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAPTURE_FAILED))
            }

            PhotoAnalysisPresenterAction.OpenSettings -> startActivity(photoBridge.settingsIntent())

            PhotoAnalysisPresenterAction.Cancel -> {
                feature.dispatch(PhotoAnalysisEvent.Cancel)
            }

            PhotoAnalysisPresenterAction.ScanAgain -> {
                feature.dispatch(PhotoAnalysisEvent.ScanAgain)
            }
        }
    }

    private fun openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        } else if (photoBridge.cameraAvailability() == CameraAvailability.UNAVAILABLE) {
            feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAMERA_UNAVAILABLE))
        } else {
            runCatching {
                startActivityForResult(
                    photoBridge.cameraCaptureIntent(ATTEMPT_ID),
                    CAMERA_CAPTURE_REQUEST,
                )
            }.onFailure {
                feature.dispatch(PhotoAnalysisEvent.PreparationFailed(RecoveryReason.CAMERA_UNAVAILABLE))
            }
        }
    }

    private companion object {
        const val ATTEMPT_ID = "android-photo-attempt"
        const val CAMERA_PERMISSION_REQUEST = 41
        const val CAMERA_CAPTURE_REQUEST = 42
        const val IMPORT_REQUEST = 43
    }
}
