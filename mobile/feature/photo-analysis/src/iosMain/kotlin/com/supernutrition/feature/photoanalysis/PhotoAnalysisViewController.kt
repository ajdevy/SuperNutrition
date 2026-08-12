package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

/** Shared Apple composition-root entry point; UI only renders the supplied state stream. */
@OptIn(ExperimentalForeignApi::class)
@Suppress("FunctionNaming")
fun PhotoAnalysisViewController(
    feature: PhotoAnalysisFeature,
    copy: PhotoAnalysisPresenterCopy,
    retainedPhoto: () -> IosRetainedPhotoPreview?,
    onNativeAction: (PhotoAnalysisNativeAction) -> Unit,
): UIViewController = ComposeUIViewController {
    // retainedPhoto is native state, so observe the shared state stream here
    // as well as inside the content to refresh the same-slot preview after a
    // photo is returned or native inference finishes.
    feature.stateFlow.collectAsState().value
    val preview = retainedPhoto()
    PhotoAnalysisCaptureContent(
        stateFlow = feature.stateFlow,
        onAction = { action -> feature.route(action, onNativeAction) },
        surface = PhotoAnalysisCaptureSurface(
            presenterCopy = copy,
            retainedPhoto = preview?.let { current -> { IosRetainedPhotoBackdrop(current) } },
            photoTextContrast = null,
        ),
    )
}
