package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent

/** Native effects exposed as a closed, cross-language vocabulary. */
enum class PhotoAnalysisNativeAction {
    TakePhoto,
    ImportPhoto,
    OpenSettings,
}

fun PhotoAnalysisFeature.route(action: PhotoAnalysisPresenterAction, onNative: (PhotoAnalysisNativeAction) -> Unit) {
    when (action) {
        PhotoAnalysisPresenterAction.Cancel -> dispatch(PhotoAnalysisEvent.Cancel)
        PhotoAnalysisPresenterAction.ScanAgain -> dispatch(PhotoAnalysisEvent.ScanAgain)
        PhotoAnalysisPresenterAction.TakePhoto -> onNative(PhotoAnalysisNativeAction.TakePhoto)
        PhotoAnalysisPresenterAction.ImportPhoto -> onNative(PhotoAnalysisNativeAction.ImportPhoto)
        PhotoAnalysisPresenterAction.OpenSettings -> onNative(PhotoAnalysisNativeAction.OpenSettings)
    }
}
