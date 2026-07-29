package com.supernutrition.platform.photoios

import com.supernutrition.platform.photoios.permissioninterop.SNGetCameraAuthorizationStatus
import com.supernutrition.platform.photoios.permissioninterop.SNRequestCameraAccess
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSBundle
import platform.UIKit.UIImage
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.PhotosUI.PHPickerConfiguration
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** iOS bridge seam; AVFoundation/PhotosUI objects stay behind the platform boundary. */
@OptIn(ExperimentalForeignApi::class)
class IosPhotoBridge : IosPhotoBridgePort {
    override fun cameraPermissionState(): IosCameraPermissionState = when (SNGetCameraAuthorizationStatus().toInt()) {
        CAMERA_AUTHORIZED_STATUS -> IosCameraPermissionState.GRANTED
        CAMERA_DENIED_STATUS -> IosCameraPermissionState.DENIED
        CAMERA_RESTRICTED_STATUS -> IosCameraPermissionState.RESTRICTED
        else -> IosCameraPermissionState.NOT_DETERMINED
    }

    override fun cameraUsageDescription(): String = usageDescription(CAMERA_USAGE_KEY)

    override fun photoPickerUsageDescription(): String = usageDescription(PHOTO_LIBRARY_USAGE_KEY)

    /** True when the native camera source is present on this device/runtime. */
    fun cameraAvailable(): Boolean = UIImagePickerController.isSourceTypeAvailable(
        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
    )

    /** Resolves the permission prompt in the bridge; the host receives only a Boolean. */
    fun ensureCameraAccess(onResult: (Boolean) -> Unit) {
        when (cameraPermissionState()) {
            IosCameraPermissionState.GRANTED -> onResult(true)
            IosCameraPermissionState.NOT_DETERMINED -> SNRequestCameraAccess(onResult)
            IosCameraPermissionState.DENIED,
            IosCameraPermissionState.RESTRICTED,
            -> onResult(false)
        }
    }

    /** Presents the native camera and returns only opaque bytes to the app host. */
    fun presentCamera(from: UIViewController, onResult: (NSData?) -> Unit) {
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        val delegate = CameraPickerDelegate(onResult) { activeDelegate = null }
        if (!retain(delegate)) return
        picker.delegate = delegate
        from.presentViewController(picker, animated = true, completion = null)
    }

    /** Presents the native single-selection picker and returns opaque bytes only. */
    fun presentPhotoPicker(from: UIViewController, onResult: (NSData?) -> Unit) {
        val configuration = PHPickerConfiguration(
            photoLibrary = platform.Photos.PHPhotoLibrary.sharedPhotoLibrary(),
        ).apply {
            filter = platform.PhotosUI.PHPickerFilter.imagesFilter()
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration = configuration)
        val delegate = PhotoPickerDelegate(onResult) { activeDelegate = null }
        if (!retain(delegate)) return
        picker.delegate = delegate
        from.presentViewController(picker, animated = true, completion = null)
    }

    private var activeDelegate: NSObject? = null

    private fun retain(delegate: NSObject): Boolean {
        if (activeDelegate != null) return false
        activeDelegate = delegate
        return true
    }

    private fun usageDescription(key: String): String =
        checkNotNull(NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String) {
            "Missing required Info.plist key: $key"
        }

    private companion object {
        const val CAMERA_AUTHORIZED_STATUS = 3
        const val CAMERA_DENIED_STATUS = 2
        const val CAMERA_RESTRICTED_STATUS = 1
        const val CAMERA_USAGE_KEY = "NSCameraUsageDescription"
        const val PHOTO_LIBRARY_USAGE_KEY = "NSPhotoLibraryUsageDescription"
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CameraPickerDelegate(
    private val onResult: (NSData?) -> Unit,
    private val clear: () -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        dismissOnMain(picker) { clear(); onResult(null) }
    }

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val data = image?.let { UIImageJPEGRepresentation(it, 1.0) }
        dismissOnMain(picker) { clear(); onResult(data) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PhotoPickerDelegate(
    private val onResult: (NSData?) -> Unit,
    private val clear: () -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            dismissOnMain(picker) { clear(); onResult(null) }
            return
        }

        result.itemProvider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, error ->
            val loadedData = data?.takeUnless { error != null }
            dispatch_async(dispatch_get_main_queue()) {
                picker.dismissViewControllerAnimated(true) {
                    clear()
                    onResult(loadedData)
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun dismissOnMain(picker: UIViewController, onDismissed: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) {
        picker.dismissViewControllerAnimated(true, completion = onDismissed)
    }
}
