package com.supernutrition.feature.photoanalysis

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Connects the shared Compose camera preview to an inline one-shot capture.
 * The controller queues a request until the preview's ImageCapture use case is
 * bound, so granting permission can continue the same screen without opening
 * another Activity.
 */
internal class PhotoAnalysisInlineCameraController {
    var onCaptureResult: ((String, Result<ByteArray>) -> Unit)? = null

    private var context: Context? = null
    private var imageCapture: ImageCapture? = null
    private var requestedOperationId: String? = null
    private var captureInFlight = false
    private var captureOutput: BoundedCaptureOutputStream? = null

    fun bind(context: Context, imageCapture: ImageCapture) {
        this.context = context
        this.imageCapture = imageCapture
        startPendingCapture()
    }

    fun unbind(imageCapture: ImageCapture) {
        if (this.imageCapture === imageCapture) {
            this.imageCapture = null
            context = null
        }
    }

    fun requestCapture(operationId: String) {
        if (requestedOperationId != null || captureInFlight) return
        requestedOperationId = operationId
        startPendingCapture()
    }

    fun failPendingCapture() {
        val operationId = requestedOperationId ?: return
        requestedOperationId = null
        onCaptureResult?.invoke(
            operationId,
            Result.failure(IllegalStateException("Inline camera capture is unavailable")),
        )
    }

    fun clear() {
        requestedOperationId = null
        captureOutput?.clear()
        captureOutput = null
        imageCapture = null
        context = null
        captureInFlight = false
        onCaptureResult = null
    }

    private fun startPendingCapture() {
        val operationId = requestedOperationId ?: return
        val imageCapture = imageCapture ?: return
        val context = context ?: return
        if (captureInFlight) return

        requestedOperationId = null
        captureInFlight = true
        val output = BoundedCaptureOutputStream(MAX_CAPTURE_BYTES)
        captureOutput = output
        val executor = ContextCompat.getMainExecutor(context)
        try {
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(output).build(),
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val bytes = output.toByteArray()
                        if (bytes.isEmpty()) {
                            finishCapture(
                                operationId,
                                Result.failure(IllegalStateException("Inline camera returned an empty image")),
                            )
                        } else {
                            finishCapture(operationId, Result.success(bytes))
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        finishCapture(operationId, Result.failure(exception))
                    }
                },
            )
        } catch (exception: IllegalStateException) {
            finishCapture(operationId, Result.failure(exception))
        }
    }

    private fun finishCapture(operationId: String, result: Result<ByteArray>) {
        captureOutput?.clear()
        captureOutput = null
        captureInFlight = false
        onCaptureResult?.invoke(operationId, result)
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
                throw IOException("Inline camera capture exceeds the in-memory size bound")
            }
        }

        private companion object {
            const val INITIAL_CAPTURE_CAPACITY = 1024 * 1024
        }
    }

    private companion object {
        const val MAX_CAPTURE_BYTES = 10 * 1024 * 1024
    }
}
