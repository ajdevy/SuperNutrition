package com.supernutrition.platform.photoios

import com.supernutrition.platform.photoios.captureinterop.SNFileDataRepresentation
import com.supernutrition.platform.photoios.permissioninterop.SNGetCameraAuthorizationStatus
import com.supernutrition.platform.photoios.permissioninterop.SNRequestCameraAccess
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
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
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
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
import kotlin.native.ref.WeakReference
import com.supernutrition.platform.photoios.captureinterop.SNCreateCaptureInput
import com.supernutrition.platform.photoios.captureinterop.SNDefaultBackCamera
import com.supernutrition.platform.photoios.captureinterop.SNSetViewAccessibilityIdentifier
import com.supernutrition.core.ui.UiAutomationIds

/** iOS bridge seam; AVFoundation/PhotosUI objects stay behind the platform boundary. */
@OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
class IosPhotoBridge : IosPhotoBridgePort {
    override fun cameraPermissionState(): IosCameraPermissionState = when (SNGetCameraAuthorizationStatus()) {
        AVAuthorizationStatusAuthorized -> IosCameraPermissionState.GRANTED
        AVAuthorizationStatusDenied -> IosCameraPermissionState.DENIED
        AVAuthorizationStatusRestricted -> IosCameraPermissionState.RESTRICTED
        AVAuthorizationStatusNotDetermined -> IosCameraPermissionState.NOT_DETERMINED
        else -> IosCameraPermissionState.RESTRICTED
    }

    override fun cameraUsageDescription(): String = usageDescription(CAMERA_USAGE_KEY)

    override fun photoPickerUsageDescription(): String = usageDescription(PHOTO_LIBRARY_USAGE_KEY)

    /** True when the native camera source is present on this device/runtime. */
    fun cameraAvailable(): Boolean = cameraDevice() != null

    /** Resolves the permission prompt in the bridge; the host receives only a Boolean. */
    fun ensureCameraAccess(onResult: (Boolean) -> Unit) {
        when (cameraPermissionState()) {
            IosCameraPermissionState.GRANTED -> onResult(true)
            IosCameraPermissionState.NOT_DETERMINED -> SNRequestCameraAccess(onResult)
            IosCameraPermissionState.DENIED,
            IosCameraPermissionState.RATIONALE,
            IosCameraPermissionState.PERMANENTLY_DENIED,
            IosCameraPermissionState.RESTRICTED,
            -> onResult(false)
        }
    }

    /** Presents the native camera and returns only opaque bytes to the app host. */
    fun presentCamera(
        from: UIViewController,
        cancelLabel: String,
        shutterLabel: String,
        onResult: (NSData?) -> Unit,
        onFailure: () -> Unit,
    ) {
        if (from.presentedViewController != null || from.isBeingDismissed()) {
            failPresentation(onFailure)
            return
        }
        val weakBridge = WeakReference(this)
        val controller = CameraCaptureViewController(
            cancelLabel = cancelLabel,
            shutterLabel = shutterLabel,
            onResult = onResult,
            onFailure = onFailure,
        ) { weakBridge.get()?.activeDelegate = null }
        if (!retain(controller)) {
            failPresentation(onFailure)
            return
        }
        from.presentViewController(controller, animated = true, completion = null)
    }

    /** Presents the native single-selection picker and returns opaque bytes only. */
    fun presentPhotoPicker(
        from: UIViewController,
        onResult: (NSData?) -> Unit,
        onFailure: () -> Unit,
    ) {
        if (from.presentedViewController != null || from.isBeingDismissed()) {
            failPresentation(onFailure)
            return
        }
        val configuration = PHPickerConfiguration(
            photoLibrary = platform.Photos.PHPhotoLibrary.sharedPhotoLibrary(),
        ).apply {
            filter = platform.PhotosUI.PHPickerFilter.imagesFilter()
            selectionLimit = 1
        }
        val weakBridge = WeakReference(this)
        lateinit var delegate: PhotoPickerDelegate
        val picker = TrackedPhotoPickerViewController(configuration) {
            delegate.presentationDismissed()
        }
        delegate = PhotoPickerDelegate(onResult, onFailure) { weakBridge.get()?.activeDelegate = null }
        if (!retain(delegate)) {
            failPresentation(onFailure)
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

    private fun failPresentation(onFailure: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) { onFailure() }
    }

    private fun cameraDevice(): AVCaptureDevice? = SNDefaultBackCamera()

    private fun usageDescription(key: String): String =
        checkNotNull(NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String) {
            "Missing required Info.plist key: $key"
        }

    private companion object {
        const val CAMERA_USAGE_KEY = "NSCameraUsageDescription"
        const val PHOTO_LIBRARY_USAGE_KEY = "NSPhotoLibraryUsageDescription"
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, kotlin.experimental.ExperimentalNativeApi::class)
private class CameraCaptureViewController(
    private val cancelLabel: String,
    private val shutterLabel: String,
    private val onResult: (NSData?) -> Unit,
    private val onFailure: () -> Unit,
    private val clear: () -> Unit,
) : UIViewController(nibName = null, bundle = null), AVCapturePhotoCaptureDelegateProtocol {
    private val captureSession = AVCaptureSession()
    private val photoOutput = AVCapturePhotoOutput()
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var cancelButton: UIButton? = null
    private var shutterButton: UIButton? = null
    private var finished = false

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor
        SNSetViewAccessibilityIdentifier(view, UiAutomationIds.CAPTURE_NATIVE.value)

        val device = SNDefaultBackCamera()
        val input = if (device == null) null else SNCreateCaptureInput(device)
        if (input == null || !configureCapture(input)) {
            finishFailure()
            return
        }

        previewLayer = AVCaptureVideoPreviewLayer(session = captureSession).also {
            it.videoGravity = AVLayerVideoGravityResizeAspectFill
            view.layer.addSublayer(it)
        }

        cancelButton = UIButton().apply {
            setTitle(cancelLabel, forState = UIControlStateNormal)
            setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
            addTarget(
                target = this@CameraCaptureViewController,
                action = NSSelectorFromString("cancelCapture"),
                forControlEvents = UIControlEventTouchUpInside,
            )
        }
        view.addSubview(checkNotNull(cancelButton))

        shutterButton = UIButton().apply {
            setTitle(shutterLabel, forState = UIControlStateNormal)
            setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
            addTarget(
                target = this@CameraCaptureViewController,
                action = NSSelectorFromString("capturePhoto"),
                forControlEvents = UIControlEventTouchUpInside,
            )
        }
        view.addSubview(checkNotNull(shutterButton))
        captureSession.startRunning()
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
        val safeArea = view.safeAreaInsets.useContents { top to bottom }
        cancelButton?.setFrame(
            platform.CoreGraphics.CGRectMake(20.0, safeArea.first + 16.0, 120.0, 48.0),
        )
        shutterButton?.setFrame(
            platform.CoreGraphics.CGRectMake(
                (CGRectGetWidth(view.bounds) - 160.0) / 2.0,
                CGRectGetHeight(view.bounds) - safeArea.second - 80.0,
                160.0,
                64.0,
            ),
        )
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)
        if (!finished) {
            finished = true
            captureSession.stopRunning()
            clear()
            onResult(null)
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
        if (data == null) finishFailure() else finish(data)
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

    private fun finishFailure() {
        if (finished) return
        finished = true
        captureSession.stopRunning()
        dismissOnMain(this) { clear(); onFailure() }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class TrackedPhotoPickerViewController(
    configuration: PHPickerConfiguration,
    private val onDismissed: () -> Unit,
) : PHPickerViewController(configuration = configuration) {
    private var programmaticDismissal = false

    fun markProgrammaticDismissal() {
        programmaticDismissal = true
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)
        if (!programmaticDismissal) onDismissed()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PhotoPickerDelegate(
    private val onResult: (NSData?) -> Unit,
    private val onFailure: () -> Unit,
    private val clear: () -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    private var completed = false

    fun presentationDismissed() {
        complete { onResult(null) }
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        if (completed) return
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            dismissPickerOnMain(picker) { onResult(null) }
            return
        }

        result.itemProvider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, error ->
            if (error != null || data == null || data.length > MAX_PICKER_BYTES.toULong()) {
                dispatch_async(dispatch_get_main_queue()) {
                    dismissPicker(picker) { onFailure() }
                }
                return@loadDataRepresentationForTypeIdentifier
            }
            dispatch_async(dispatch_get_main_queue()) {
                dismissPicker(picker) { onResult(data) }
            }
        }
    }

    private fun complete(action: () -> Unit) {
        if (completed) return
        completed = true
        clear()
        action()
    }

    private fun dismissPickerOnMain(picker: PHPickerViewController, action: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) { dismissPicker(picker, action) }
    }

    private fun dismissPicker(picker: PHPickerViewController, action: () -> Unit) {
        (picker as? TrackedPhotoPickerViewController)?.markProgrammaticDismissal()
        picker.dismissViewControllerAnimated(true) { complete(action) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun dismissOnMain(picker: UIViewController, onDismissed: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) {
        picker.dismissViewControllerAnimated(true, completion = onDismissed)
    }
}

private const val MAX_PICKER_BYTES = 10 * 1024 * 1024
