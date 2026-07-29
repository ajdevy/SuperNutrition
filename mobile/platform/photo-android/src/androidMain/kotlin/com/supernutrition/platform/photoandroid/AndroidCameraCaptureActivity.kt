package com.supernutrition.platform.photoandroid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import android.util.Log

/**
 * Bridge-only CameraX surface. Product presentation remains owned by Compose;
 * this activity only owns camera lifecycle and bounded in-memory capture.
 */
class AndroidCameraCaptureActivity : ComponentActivity() {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var captureOutput: BoundedCaptureOutputStream? = null
    private var captureStarted = false
    private var captureRequested = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureRequested = savedInstanceState?.getBoolean(CAPTURE_REQUESTED_KEY, true) ?: true
        captureStarted = savedInstanceState?.getBoolean(CAPTURE_STARTED_KEY, false) ?: false
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finishWithFailure(CaptureFailureReason.PERMISSION_DENIED)
            return
        }

        val previewView = PreviewView(this)
        val root = FrameLayout(this).apply {
            addView(
                previewView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        setContentView(root)
        bindCamera(previewView)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(CAPTURE_REQUESTED_KEY, captureRequested)
        outState.putBoolean(CAPTURE_STARTED_KEY, captureStarted)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        captureOutput?.clear()
        super.onDestroy()
    }

    private fun bindCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            runCatching {
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                val provider = cameraProviderFuture.get()
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
                cameraProvider = provider
                this@AndroidCameraCaptureActivity.imageCapture = imageCapture
                if (captureRequested && !captureStarted) {
                    captureStarted = true
                    captureRequested = false
                    capture()
                }
            }.onFailure { error ->
                Log.e(TAG, "Camera binding failed", error)
                finishWithFailure(CaptureFailureReason.CAMERA_UNAVAILABLE)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capture() {
        val imageCapture = imageCapture ?: run {
            finishWithFailure(CaptureFailureReason.CAMERA_UNAVAILABLE)
            return
        }
        val output = BoundedCaptureOutputStream(MAX_CAPTURE_BYTES)
        captureOutput = output
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(output).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bytes = output.toByteArray()
                    if (bytes.isEmpty()) {
                        finishWithFailure(CaptureFailureReason.CAPTURE_FAILED)
                    } else {
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(
                                AndroidPhotoCaptureFiles.EXTRA_CAPTURE_TOKEN,
                                AndroidPhotoCaptureResultStore.put(bytes),
                            ),
                        )
                        finish()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Camera capture failed", exception)
                    finishWithFailure(CaptureFailureReason.CAPTURE_FAILED)
                }
            },
        )
    }

    private fun finishWithFailure(reason: CaptureFailureReason) {
        setResult(
            Activity.RESULT_FIRST_USER,
            Intent()
                .putExtra(AndroidPhotoCaptureFiles.EXTRA_CAPTURE_FAILURE, true)
                .putExtra(AndroidPhotoCaptureFiles.EXTRA_CAPTURE_FAILURE_REASON, reason.name),
        )
        finish()
    }

    private class BoundedCaptureOutputStream(private val maxBytes: Int) :
        ByteArrayOutputStream(INITIAL_CAPTURE_CAPACITY) {
        override fun write(value: Int) {
            ensureCapacity(1)
            super.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            ensureCapacity(length)
            super.write(buffer, offset, length)
        }

        fun clear() {
            buf.fill(0, 0, count)
            reset()
        }

        private fun ensureCapacity(incomingBytes: Int) {
            if (incomingBytes < 0 || count > maxBytes - incomingBytes) {
                throw IOException("Camera capture exceeds the in-memory size bound")
            }
        }

        private companion object {
            const val INITIAL_CAPTURE_CAPACITY = 1024 * 1024
        }
    }

    private companion object {
        const val TAG = "AndroidCameraCapture"
        const val MAX_CAPTURE_BYTES = 10 * 1024 * 1024
        const val CAPTURE_REQUESTED_KEY = "photo_analysis.camera.capture_requested"
        const val CAPTURE_STARTED_KEY = "photo_analysis.camera.capture_started"
    }
}

private enum class CaptureFailureReason {
    PERMISSION_DENIED,
    CAMERA_UNAVAILABLE,
    CAPTURE_FAILED,
}
