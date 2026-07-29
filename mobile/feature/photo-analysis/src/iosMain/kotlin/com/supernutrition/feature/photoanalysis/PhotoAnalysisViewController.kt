package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

/** Shared Apple composition-root entry point; UI only renders the supplied state stream. */
@OptIn(ExperimentalForeignApi::class)
fun PhotoAnalysisViewController(
    feature: PhotoAnalysisFeature,
    onNativeAction: (PhotoAnalysisNativeAction) -> Unit,
): UIViewController = ComposeUIViewController {
    PhotoAnalysisCaptureContent(
        stateFlow = feature.stateFlow,
        onAction = { action -> feature.route(action, onNativeAction) },
    )
}
