package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

/**
 * Shared Apple composition-root entry point; UI only renders the supplied state stream.
 *
 * PascalCase is the Compose/Apple interop convention for a factory that returns a
 * UIViewController — it mirrors `ComposeUIViewController`, and the name is part of
 * the surface Swift calls. Renaming it to satisfy the pattern would rename the
 * published entry point, so the rule is suppressed here rather than obeyed.
 */
@Suppress("FunctionNaming")
@OptIn(ExperimentalForeignApi::class)
@Suppress("FunctionNaming")
fun PhotoAnalysisViewController(
    feature: PhotoAnalysisFeature,
    copy: PhotoAnalysisPresenterCopy,
    retainedPhoto: () -> IosRetainedPhotoPreview?,
    onNativeAction: (PhotoAnalysisNativeAction) -> Unit,
): UIViewController = ComposeUIViewController {
    val preview = retainedPhoto()
    PhotoAnalysisCaptureContent(
        stateFlow = feature.stateFlow,
        onAction = { action -> feature.route(action, onNativeAction) },
        surface = PhotoAnalysisCaptureSurface(
            presenterCopy = copy,
            retainedPhoto = preview?.let { current -> { IosRetainedPhotoBackdrop(current) } },
            photoTextContrast = preview?.let { PhotoAnalysisPhotoTextContrast.LIGHT_TEXT },
        ),
    )
}
