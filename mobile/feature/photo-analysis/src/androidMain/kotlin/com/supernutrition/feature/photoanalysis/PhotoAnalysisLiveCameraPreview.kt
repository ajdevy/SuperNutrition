package com.supernutrition.feature.photoanalysis

import android.util.Log
import android.view.Surface
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.supernutrition.core.ui.CameraPermissionState
import com.supernutrition.core.ui.UiAutomationIds

/** CameraX preview kept inside the stable photo slot owned by the shared UI. */
@Composable
internal fun PhotoAnalysisLiveCameraPreview(
    cameraAccess: PhotoAnalysisCameraAccess,
    permissionHint: String,
    unavailableHint: String,
    cameraController: PhotoAnalysisInlineCameraController,
) {
    val context = LocalContext.current
    val cameraAvailable = context.packageManager.hasSystemFeature(
        android.content.pm.PackageManager.FEATURE_CAMERA_ANY,
    )

    when {
        !cameraAvailable -> PhotoAnalysisCameraFallback(unavailableHint)

        cameraAccess.permissionState != CameraPermissionState.GRANTED ->
            PhotoAnalysisCameraPermissionFallback(cameraAccess, permissionHint)

        else -> PhotoAnalysisCameraSurface(context, unavailableHint, cameraController)
    }
}

@Composable
private fun PhotoAnalysisCameraPermissionFallback(access: PhotoAnalysisCameraAccess, message: String) {
    val settingsRequired = access.permissionState == CameraPermissionState.PERMANENTLY_DENIED ||
        access.permissionState == CameraPermissionState.RESTRICTED
    val label = stringResource(
        if (settingsRequired) R.string.capture_open_settings else R.string.capture_allow_camera,
    )
    val actionId = if (settingsRequired) {
        UiAutomationIds.CAPTURE_OPEN_SETTINGS
    } else {
        UiAutomationIds.CAPTURE_ALLOW_CAMERA
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF14181C)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = message, color = Color.White, textAlign = TextAlign.Center)
            if (access.actionVisible) {
                Button(
                    onClick = access.onAccessRequested,
                    enabled = !access.requestInFlight,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF677712),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp).testTag(actionId.value),
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun PhotoAnalysisCameraFallback(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14181C)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
    }
}

@Composable
private fun PhotoAnalysisCameraSurface(
    context: android.content.Context,
    unavailableHint: String,
    cameraController: PhotoAnalysisInlineCameraController,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val previewContainer = remember(previewView) {
        FrameLayout(context).apply {
            clipChildren = true
            clipToPadding = true
            addView(
                previewView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }
    val previewFailed = remember { mutableStateOf(false) }
    PhotoAnalysisCameraBinding(previewView, lifecycleOwner, previewFailed, cameraController)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14181C)),
        contentAlignment = Alignment.Center,
    ) {
        if (previewFailed.value) {
            Text(text = unavailableHint, color = Color.White)
        } else {
            AndroidView(
                factory = { previewContainer },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PhotoAnalysisCameraBinding(
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewFailed: androidx.compose.runtime.MutableState<Boolean>,
    cameraController: PhotoAnalysisInlineCameraController,
) {
    val context = previewView.context
    DisposableEffect(previewView, lifecycleOwner) {
        val preview = Preview.Builder().build()
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .build()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var cameraProvider: ProcessCameraProvider? = null
        var isBound = false
        var disposed = false

        fun unbindPreview() {
            cameraProvider?.let { provider ->
                if (isBound) provider.unbind(preview, imageCapture)
            }
            cameraController.unbind(imageCapture)
            isBound = false
        }

        fun bindPreview() {
            if (disposed || isBound || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                return
            }
            runCatching {
                val provider = providerFuture.get()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
                cameraProvider = provider
                isBound = true
                cameraController.bind(context, imageCapture)
                previewFailed.value = false
            }.onFailure { error ->
                Log.e(TAG, "Live camera preview binding failed", error)
                cameraController.failPendingCapture()
                previewFailed.value = true
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> bindPreview()
                Lifecycle.Event.ON_STOP -> unbindPreview()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        providerFuture.addListener({ bindPreview() }, mainExecutor)

        onDispose {
            disposed = true
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            unbindPreview()
            preview.setSurfaceProvider(null)
        }
    }
}

private const val TAG = "PhotoAnalysisLiveCamera"
