package com.supernutrition.platform.photoios

import com.supernutrition.platform.photoios.captureinterop.SNFileDataRepresentation
import com.supernutrition.platform.photoios.permissioninterop.SNGetCameraAuthorizationStatus
import com.supernutrition.platform.photoios.permissioninterop.SNRequestCameraAccess
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIButton
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
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
    fun cameraAvailable(): Boolean = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) != null

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
        val controller = CameraCaptureViewController(onResult) { activeDelegate = null }
        if (!retain(controller)) {
            failPresentation(onResult)
            return
        }
        from.presentViewController(controller, animated = true, completion = null)
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
        if (!retain(delegate)) {
            failPresentation(onResult)
            return
        }
        picker.delegate = delegate
        from.presentViewController(picker, animated = true, completion = null)
    }

    private var activeDelegate: NSObject? = null

    private fun retain(delegate: NSObject): Boolean {
        if (activeDelegate != null) return false
        activeDelegate = delegate
        return true
    }

    private fun failPresentation(onResult: (NSData?) -> Unit) {
        dispatch_async(dispatch_get_main_queue()) { onResult(null) }
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

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class CameraCaptureViewController(
    private val onResult: (NSData?) -> Unit,
    private val clear: () -> Unit,
) : UIViewController(nibName = null, bundle = null), AVCapturePhotoCaptureDelegateProtocol {
    private val captureSession = AVCaptureSession()
    private val photoOutput = AVCapturePhotoOutput()
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var finished = false

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor

        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
        if (input == null || !configureCapture(input)) {
            finish(null)
            return
        }

        previewLayer = AVCaptureVideoPreviewLayer(session = captureSession).also {
            it.videoGravity = AVLayerVideoGravityResizeAspectFill
            view.layer.addSublayer(it)
        }

        val cancelButton = UIButton().apply {
            setTitle(localized("photo_analysis.capture.cancel", "Cancel"), forState = UIControlStateNormal)
            setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
            addTarget(
                target = this@CameraCaptureViewController,
                action = NSSelectorFromString("cancelCapture"),
                forControlEvents = UIControlEventTouchUpInside,
            )
        }
        view.addSubview(cancelButton)

        val shutterButton = UIButton().apply {
            setTitle(localized("photo_analysis.capture.shutter", "Take photo"), forState = UIControlStateNormal)
            setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
            addTarget(
                target = this@CameraCaptureViewController,
                action = NSSelectorFromString("capturePhoto"),
                forControlEvents = UIControlEventTouchUpInside,
            )
        }
        view.addSubview(shutterButton)
        captureSession.startRunning()
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
        view.subviews.forEachIndexed { index, rawSubview ->
            val subview = rawSubview as platform.UIKit.UIView
            when (index) {
                0 -> subview.setFrame(platform.CoreGraphics.CGRectMake(20.0, 52.0, 120.0, 48.0))
                else -> subview.setFrame(platform.CoreGraphics.CGRectMake(
                    (CGRectGetWidth(view.bounds) - 160.0) / 2.0,
                    CGRectGetHeight(view.bounds) - 112.0,
                    160.0,
                    64.0,
                ))
            }
        }
    }

    @ObjCAction
    private fun capturePhoto() {
        photoOutput.capturePhotoWithSettings(AVCapturePhotoSettings.photoSettings(), delegate = this)
    }

    @ObjCAction
    private fun cancelCapture() {
        finish(null)
    }

    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?,
    ) {
        val data = if (error == null) SNFileDataRepresentation(didFinishProcessingPhoto) else null
        finish(data)
    }

    private fun configureCapture(input: AVCaptureDeviceInput): Boolean {
        captureSession.beginConfiguration()
        val configured = captureSession.canAddInput(input) && captureSession.canAddOutput(photoOutput)
        if (configured) {
            captureSession.addInput(input)
            captureSession.addOutput(photoOutput)
        }
        captureSession.commitConfiguration()
        return configured
    }

    private fun finish(data: NSData?) {
        if (finished) return
        finished = true
        captureSession.stopRunning()
        dismissOnMain(this) { clear(); onResult(data) }
    }

    private fun localized(key: String, fallback: String): String =
        NSBundle.mainBundle.localizedStringForKey(key, value = fallback, table = null)
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
