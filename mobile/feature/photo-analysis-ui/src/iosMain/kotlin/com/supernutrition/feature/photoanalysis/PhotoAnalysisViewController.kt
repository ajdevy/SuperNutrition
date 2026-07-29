package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

/** Shared Apple host entry point; only native effects cross the boundary. */
@OptIn(ExperimentalForeignApi::class)
fun PhotoAnalysisViewController(
    feature: PhotoAnalysisFeature,
    onNativeAction: (PhotoAnalysisNativeAction) -> Unit,
): UIViewController = ComposeUIViewController {
    PhotoAnalysisCaptureContent(
        feature = feature,
        onAction = { action -> feature.route(action, onNativeAction) },
    )
}
