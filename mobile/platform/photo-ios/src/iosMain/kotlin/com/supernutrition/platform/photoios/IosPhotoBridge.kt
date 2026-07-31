package com.supernutrition.platform.photoios

import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.platform.photoios.captureinterop.SNCreateCaptureInput
import com.supernutrition.platform.photoios.captureinterop.SNDefaultBackCamera
import com.supernutrition.platform.photoios.captureinterop.SNFileDataRepresentation
import com.supernutrition.platform.photoios.captureinterop.SNSetViewAccessibilityIdentifier
import com.supernutrition.platform.photoios.permissioninterop.SNGetCameraAuthorizationStatus
import com.supernutrition.platform.photoios.permissioninterop.SNRequestCameraAccess
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSSelectorFromString
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIButton
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.native.ref.WeakReference

/** iOS bridge seam; AVFoundation/PhotosUI objects stay behind the platform boundary. */
interface IosPhotoPresentationPort {
    fun cameraAvailable(): Boolean
    fun ensureCameraAccess(onResult: (Boolean) -> Unit)
    fun presentCamera(
        from: UIViewController,
        cancelLabel: String,
        shutterLabel: String,
        onResult: (NSData?) -> Unit,
        onFailure: () -> Unit,
    )
    fun presentPhotoPicker(from: UIViewController, onResult: (NSData?) -> Unit, onFailure: () -> Unit)
}

@OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@Suppress("TooManyFunctions")
class IosPhotoBridge :
    IosPhotoBridgePort,
    IosPhotoPresentationPort {
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
    override fun cameraAvailable(): Boolean = cameraDevice() != null

    /** Resolves the permission prompt in the bridge; the host receives only a Boolean. */
    override fun ensureCameraAccess(onResult: (Boolean) -> Unit) {
        when (cameraPermissionState()) {
            IosCameraPermissionState.GRANTED -> deliver(onResult, true)

            IosCameraPermissionState.NOT_DETERMINED -> SNRequestCameraAccess { granted ->
                deliver(onResult, granted)
            }

            IosCameraPermissionState.DENIED,
            IosCameraPermissionState.RATIONALE,
            IosCameraPermissionState.PERMANENTLY_DENIED,
            IosCameraPermissionState.RESTRICTED,
            -> deliver(onResult, false)
        }
    }

    /** Presents the native camera and returns only opaque bytes to the app host. */
    override fun presentCamera(
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
            onResult = { data -> deliver(onResult, data) },
            onFailure = { failPresentation(onFailure) },
        ) { weakBridge.get()?.activeDelegate = null }
        if (!retain(controller)) {
            failPresentation(onFailure)
            return
        }
        from.presentViewController(controller, animated = true, completion = null)
    }

    /** Presents the native single-selection picker and returns opaque bytes only. */
    override fun presentPhotoPicker(from: UIViewController, onResult: (NSData?) -> Unit, onFailure: () -> Unit) {
        if (from.presentedViewController != null || from.isBeingDismissed()) {
            failPresentation(onFailure)
            return
        }
        val configuration = PHPickerConfiguration().apply {
            filter = platform.PhotosUI.PHPickerFilter.imagesFilter()
            selectionLimit = 1
        }
        val weakBridge = WeakReference(this)
        lateinit var delegate: PhotoPickerDelegate
        val picker = PHPickerViewController(configuration = configuration)
        delegate = PhotoPickerDelegate(
            onResult = { data -> deliver(onResult, data) },
            onFailure = { failPresentation(onFailure) },
        ) { weakBridge.get()?.activeDelegate = null }
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

    private fun <T> deliver(callback: (T) -> Unit, value: T) {
        dispatch_async(dispatch_get_main_queue()) { callback(value) }
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
) : UIViewController(nibName = null, bundle = null),
    AVCapturePhotoCaptureDelegateProtocol {
    private val captureSession = AVCaptureSession()
    private val captureSessionQueue = dispatch_queue_create(
        "com.supernutrition.photo.capture.session",
        null,
    )
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
            titleLabel?.numberOfLines = 0
            titleLabel?.adjustsFontSizeToFitWidth = true
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
            titleLabel?.numberOfLines = 0
            titleLabel?.adjustsFontSizeToFitWidth = true
            addTarget(
                target = this@CameraCaptureViewController,
                action = NSSelectorFromString("capturePhoto"),
                forControlEvents = UIControlEventTouchUpInside,
            )
        }
        view.addSubview(checkNotNull(shutterButton))
        dispatch_async(captureSessionQueue) {
            captureSession.startRunning()
        }
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
        val safeArea = view.safeAreaInsets.useContents { top to bottom }
        val horizontalInset = minOf(24.0, CGRectGetWidth(view.bounds) / 12.0)
        val availableWidth = maxOf(120.0, CGRectGetWidth(view.bounds) - horizontalInset * 2.0)
        cancelButton?.setFrame(
            platform.CoreGraphics.CGRectMake(
                horizontalInset,
                safeArea.first + 16.0,
                minOf(availableWidth, 220.0),
                56.0,
            ),
        )
        val shutterWidth = minOf(availableWidth, 260.0)
        shutterButton?.setFrame(
            platform.CoreGraphics.CGRectMake(
                (CGRectGetWidth(view.bounds) - shutterWidth) / 2.0,
                CGRectGetHeight(view.bounds) - safeArea.second - 88.0,
                shutterWidth,
                72.0,
            ),
        )
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)
        if (!finished) {
            finished = true
            dispatch_async(captureSessionQueue) { captureSession.stopRunning() }
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
        dispatch_async(captureSessionQueue) { captureSession.stopRunning() }
        dismissOnMain(this) {
            clear()
            onResult(data)
        }
    }

    private fun finishFailure() {
        if (finished) return
        finished = true
        dispatch_async(captureSessionQueue) { captureSession.stopRunning() }
        dismissOnMain(this) {
            clear()
            onFailure()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PhotoPickerDelegate(
    private val onResult: (NSData?) -> Unit,
    private val onFailure: () -> Unit,
    private val clear: () -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    private var completed = false

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
