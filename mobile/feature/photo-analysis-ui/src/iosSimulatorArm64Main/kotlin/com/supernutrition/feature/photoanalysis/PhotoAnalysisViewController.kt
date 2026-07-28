package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

/** Canonical Apple simulator entry point; the surface remains Compose-owned. */
@OptIn(ExperimentalForeignApi::class)
fun PhotoAnalysisViewController(
    feature: PhotoAnalysisFeature,
    onNativeAction: (String) -> Unit,
): UIViewController = ComposeUIViewController {
    val state = remember { mutableStateOf(feature.state) }
    DisposableEffect(feature) {
        val dispose = feature.observe { state.value = feature.state }
        onDispose(dispose)
    }

    PhotoAnalysisCaptureScreen(
        model = PhotoAnalysisPresenterModel.from(state.value),
        onAction = { action ->
            when (action) {
                PhotoAnalysisPresenterAction.Cancel -> feature.dispatch(PhotoAnalysisEvent.Cancel)
                PhotoAnalysisPresenterAction.ScanAgain -> feature.dispatch(PhotoAnalysisEvent.ScanAgain)
                PhotoAnalysisPresenterAction.TakePhoto,
                PhotoAnalysisPresenterAction.ImportPhoto,
                PhotoAnalysisPresenterAction.OpenSettings,
                -> onNativeAction(action.name)
            }
        },
        bootstrapLaunchMarker = true,
    )
}
