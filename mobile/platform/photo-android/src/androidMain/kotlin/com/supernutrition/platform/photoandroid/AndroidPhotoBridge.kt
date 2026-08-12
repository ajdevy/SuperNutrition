package com.supernutrition.platform.photoandroid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.supernutrition.core.ui.CameraAvailability
import com.supernutrition.core.ui.CameraPermissionState

/**
 * Android-only bridge. Native intents and permission objects stay here; the
 * common feature receives only PhotoRef and bridge outcomes.
 */
class AndroidPhotoBridge @JvmOverloads constructor(
    private val context: Context,
    permissionRequestInFlight: Boolean = false,
) : AndroidPhotoBridgePort {
    private var permissionRequestInFlight = permissionRequestInFlight

    override fun cameraPermissionState(): CameraPermissionState = when {
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
            CameraPermissionState.GRANTED

        permissionRequestInFlight -> CameraPermissionState.NOT_DETERMINED

        context is android.app.Activity &&
            context.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ->
            CameraPermissionState.RATIONALE

        !context.getSharedPreferences(PERMISSIONS_PREFS, Context.MODE_PRIVATE)
            .getBoolean(CAMERA_PERMISSION_REQUESTED, false) -> CameraPermissionState.NOT_DETERMINED

        else -> CameraPermissionState.PERMANENTLY_DENIED
    }

    override fun cameraAvailability(): CameraAvailability = if (
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    ) {
        CameraAvailability.AVAILABLE
    } else {
        CameraAvailability.UNAVAILABLE
    }

    override fun cameraCaptureIntent(operationId: String): Intent =
        Intent(context, AndroidCameraCaptureActivity::class.java).apply {
            putExtra(AndroidPhotoCaptureFiles.EXTRA_OPERATION_ID, operationId)
        }

    override fun cleanupStaleCaptureFiles(): Int = AndroidPhotoCaptureFiles.cleanupStale(context)

    override fun isCaptureFailure(result: Intent?): Boolean =
        result?.getBooleanExtra(AndroidPhotoCaptureFiles.EXTRA_CAPTURE_FAILURE, false) == true

    /**
     * Uses the system document picker for a single selected URI. Some OEM API
     * 36 builds expose ACTION_PICK_IMAGES but return to the launcher when the
     * legacy acceptance host starts it; OPEN_DOCUMENT is the safer selected-
     * only system fallback and does not grant broad media access.
     */
    override fun photoPickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "image/*"
        addCategory(Intent.CATEGORY_OPENABLE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    override fun settingsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    /** Reads one selected URI or consumes the in-memory CameraX result. */
    override fun photoBytesFromResult(result: Intent?): ByteArray? = AndroidPhotoResultReader(context).read(result)

    override fun markCameraPermissionRequestStarted() {
        permissionRequestInFlight = true
        context.getSharedPreferences(PERMISSIONS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CAMERA_PERMISSION_REQUESTED, true)
            .apply()
    }

    override fun markCameraPermissionRequestCompleted() {
        permissionRequestInFlight = false
    }

    private companion object {
        const val PERMISSIONS_PREFS = "photo_analysis_permissions"
        const val CAMERA_PERMISSION_REQUESTED = "camera_permission_requested"
    }
}

object AndroidPhotoResultKeys {
    /** Legacy camera contracts use this platform-defined thumbnail extra. */
    const val LEGACY_THUMBNAIL = "data"
}

object AndroidPhotoAttemptIds {
    const val CAPTURE = "android-photo-attempt"
}
