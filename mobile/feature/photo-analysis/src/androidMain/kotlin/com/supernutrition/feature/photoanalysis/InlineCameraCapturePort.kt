package com.supernutrition.feature.photoanalysis

import androidx.camera.core.ImageCapture
import java.util.concurrent.Executor

/** Native one-shot camera effect; its callback may arrive after Activity destruction. */
internal fun interface InlineCameraCapturePort {
    fun takePicture(
        camera: ImageCapture,
        options: ImageCapture.OutputFileOptions,
        executor: Executor,
        callback: ImageCapture.OnImageSavedCallback,
    )
}
