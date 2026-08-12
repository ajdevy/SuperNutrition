package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState

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
