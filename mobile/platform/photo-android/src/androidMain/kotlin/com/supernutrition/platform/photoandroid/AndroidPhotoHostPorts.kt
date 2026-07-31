package com.supernutrition.platform.photoandroid

import android.content.Intent
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.PhotoRef

/** Android-only operations kept behind the feature host's platform seam. */
interface AndroidPhotoBridgePort : PhotoBridgePort {
    fun cameraCaptureIntent(operationId: String): Intent
    fun photoPickerIntent(): Intent
    fun settingsIntent(): Intent
    fun cleanupStaleCaptureFiles(): Int
    fun isCaptureFailure(result: Intent?): Boolean
    fun photoBytesFromResult(result: Intent?): ByteArray?
    fun markCameraPermissionRequestStarted()
    fun markCameraPermissionRequestCompleted()
}

interface AndroidPhotoNormalizerPort {
    fun normalize(
        ref: PhotoRef,
        sourceBytes: ByteArray,
        sourceOrientation: ImageOrientation = sourceBytes.exifOrientation(),
    ): NormalizedPhotoInput
}
