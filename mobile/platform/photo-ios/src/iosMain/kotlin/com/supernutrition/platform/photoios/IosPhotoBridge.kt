package com.supernutrition.platform.photoios

import com.supernutrition.platform.photoios.permissioninterop.SNGetCameraAuthorizationStatus
import com.supernutrition.platform.photoios.permissioninterop.SNRequestCameraAccess
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
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

/** iOS bridge seam; AVFoundation/PhotosUI objects stay behind the platform boundary. */
@OptIn(ExperimentalForeignApi::class)
class IosPhotoBridge : IosPhotoBridgePort {
    override fun cameraPermissionState(): IosCameraPermissionState = when (SNGetCameraAuthorizationStatus().toInt()) {
        3 -> IosCameraPermissionState.AUTHORIZED
        2 -> IosCameraPermissionState.DENIED
        1 -> IosCameraPermissionState.RESTRICTED
        else -> IosCameraPermissionState.NOT_DETERMINED
    }

    override fun cameraUsageDescription(): String = "Take a meal photo for on-device analysis."

    override fun photoPickerUsageDescription(): String = "Choose one meal photo to prepare."

    /** True when the native camera source is present on this device/runtime. */
    fun cameraAvailable(): Boolean = UIImagePickerController.isSourceTypeAvailable(
        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
    )

    /** Resolves the permission prompt in the bridge; the host receives only a Boolean. */
    fun ensureCameraAccess(onResult: (Boolean) -> Unit) {
        when (cameraPermissionState()) {
            IosCameraPermissionState.AUTHORIZED -> onResult(true)
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
        activeDelegate = delegate
        picker.delegate = delegate
        from.presentViewController(picker, animated = true, completion = null)
    }

    /** Presents the native single-selection picker and returns opaque bytes only. */
    fun presentPhotoPicker(from: UIViewController, onResult: (NSData?) -> Unit) {
        val picker = PHPickerViewController(
            configuration = PHPickerConfiguration(photoLibrary = platform.Photos.PHPhotoLibrary.sharedPhotoLibrary()),
        )
        picker.configuration.filter = platform.PhotosUI.PHPickerFilter.imagesFilter()
        picker.configuration.selectionLimit = 1
        val delegate = PhotoPickerDelegate(onResult) { activeDelegate = null }
        activeDelegate = delegate
        picker.delegate = delegate
        from.presentViewController(picker, animated = true, completion = null)
    }

    private var activeDelegate: NSObject? = null
}

@OptIn(ExperimentalForeignApi::class)
private class CameraPickerDelegate(
    private val onResult: (NSData?) -> Unit,
    private val clear: () -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) {
            clear()
            onResult(null)
        }
    }

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val data = image?.let { UIImageJPEGRepresentation(it, 1.0) }
        picker.dismissViewControllerAnimated(true) {
            clear()
            onResult(data)
        }
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
            picker.dismissViewControllerAnimated(true) {
                clear()
                onResult(null)
            }
            return
        }

        result.itemProvider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, _ ->
            picker.dismissViewControllerAnimated(true) {
                clear()
                onResult(data)
            }
        }
    }
}
