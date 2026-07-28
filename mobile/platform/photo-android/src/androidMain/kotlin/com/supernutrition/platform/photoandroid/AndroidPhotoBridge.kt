package com.supernutrition.platform.photoandroid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Android-only bridge. Native intents and permission objects stay here; the
 * common feature receives only PhotoRef and bridge outcomes.
 */
class AndroidPhotoBridge(private val context: Context) : PhotoBridgePort {
    override fun cameraPermissionState(): CameraPermissionState = when {
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
            CameraPermissionState.GRANTED

        else -> CameraPermissionState.DENIED
    }

    override fun cameraAvailability(): CameraAvailability = if (
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    ) {
        CameraAvailability.AVAILABLE
    } else {
        CameraAvailability.UNAVAILABLE
    }

    override fun recoveryFor(
        permission: CameraPermissionState,
        availability: CameraAvailability,
    ): List<PhotoRecoveryAction> = when {
        permission != CameraPermissionState.GRANTED -> listOf(
            PhotoRecoveryAction.OpenSettings,
            PhotoRecoveryAction.ImportPhoto,
            PhotoRecoveryAction.ManualSearch,
        )

        availability == CameraAvailability.UNAVAILABLE -> listOf(
            PhotoRecoveryAction.ImportPhoto,
            PhotoRecoveryAction.ManualSearch,
        )

        else -> emptyList()
    }

    fun cameraPermissionName(): String = Manifest.permission.CAMERA

    fun cameraCaptureIntent(attemptId: String = "android-photo-attempt"): Intent =
        Intent(context, AndroidCameraCaptureActivity::class.java).apply {
            putExtra(AndroidPhotoCaptureFiles.EXTRA_ATTEMPT_ID, attemptId)
        }

    fun cleanupStaleCaptureFiles(): Int = AndroidPhotoCaptureFiles.cleanupStale(context)

    fun isCaptureFailure(result: Intent?): Boolean =
        result?.getBooleanExtra(AndroidPhotoCaptureFiles.EXTRA_CAPTURE_FAILURE, false) == true

    /**
     * Uses the system document picker for a single selected URI. Some OEM API
     * 36 builds expose ACTION_PICK_IMAGES but return to the launcher when the
     * legacy acceptance host starts it; OPEN_DOCUMENT is the safer selected-
     * only system fallback and does not grant broad media access.
     */
    fun photoPickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "image/*"
        addCategory(Intent.CATEGORY_OPENABLE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun settingsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    /** Reads one selected URI or consumes the in-memory CameraX result. */
    fun photoBytesFromResult(result: Intent?): ByteArray? {
        result?.getStringExtra(AndroidPhotoCaptureFiles.EXTRA_CAPTURE_TOKEN)?.let { token ->
            return AndroidPhotoCaptureResultStore.take(token)
        }

        val uri = result?.data ?: result?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (AndroidPhotoCaptureFiles.isOwnedUri(context, uri)) {
                uri.path?.let { File(it).delete() }
            }
            return bytes
        }

        val thumbnail = result?.extras?.get("data") as? Bitmap ?: return null
        return ByteArrayOutputStream().use { output ->
            if (!thumbnail.compress(Bitmap.CompressFormat.JPEG, 92, output)) null else output.toByteArray()
        }
    }
}
