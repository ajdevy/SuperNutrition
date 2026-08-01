package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.PhotoAnalysisNativeAction
import com.supernutrition.feature.photoanalysis.PhotoAnalysisViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
public object SharedKitPhotoAnalysis {
    public fun viewController(
        session: SharedKitPhotoAnalysisSession,
        onNativeAction: (SharedKitPhotoAnalysisNativeAction) -> Unit,
    ): UIViewController = PhotoAnalysisViewController(
        session.feature,
        session.presenterCopy,
    ) { action -> onNativeAction(action.toSharedKitAction()) }
}

private fun PhotoAnalysisNativeAction.toSharedKitAction(): SharedKitPhotoAnalysisNativeAction =
    when (this) {
        PhotoAnalysisNativeAction.TakePhoto -> SharedKitPhotoAnalysisNativeAction.TakePhoto
        PhotoAnalysisNativeAction.ImportPhoto -> SharedKitPhotoAnalysisNativeAction.ImportPhoto
        PhotoAnalysisNativeAction.OpenSettings -> SharedKitPhotoAnalysisNativeAction.OpenSettings
    }
