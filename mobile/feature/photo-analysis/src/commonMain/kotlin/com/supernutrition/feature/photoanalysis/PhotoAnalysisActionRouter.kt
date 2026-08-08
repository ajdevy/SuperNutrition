@file:Suppress("MatchingDeclarationName")

package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState

/** Native effects exposed as a closed, cross-language vocabulary at the host boundary. */
enum class PhotoAnalysisNativeAction {
    TakePhoto,
    ImportPhoto,
    OpenSettings,
}

fun PhotoAnalysisFeature.route(action: PhotoAnalysisPresenterAction, onNative: (PhotoAnalysisNativeAction) -> Unit) {
    when (action) {
        PhotoAnalysisPresenterAction.Cancel -> dispatch(PhotoAnalysisEvent.Cancel)
        PhotoAnalysisPresenterAction.ScanAgain -> dispatch(PhotoAnalysisEvent.ScanAgain)
        is PhotoAnalysisPresenterAction.SelectGridCell -> dispatch(PhotoAnalysisEvent.GridCellSelected(action.index))
        PhotoAnalysisPresenterAction.TakePhoto -> {
            prepareForNativePhotoAction()
            onNative(PhotoAnalysisNativeAction.TakePhoto)
        }
        PhotoAnalysisPresenterAction.ImportPhoto -> {
            prepareForNativePhotoAction()
            onNative(PhotoAnalysisNativeAction.ImportPhoto)
        }
        PhotoAnalysisPresenterAction.OpenSettings -> onNative(PhotoAnalysisNativeAction.OpenSettings)
    }
}

private fun PhotoAnalysisFeature.prepareForNativePhotoAction() {
    if (state != PhotoAnalysisState.Capture) {
        dispatch(PhotoAnalysisEvent.ScanAgain)
    }
}
