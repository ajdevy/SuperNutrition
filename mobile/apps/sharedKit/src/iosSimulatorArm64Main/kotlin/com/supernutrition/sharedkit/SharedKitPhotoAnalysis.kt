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
        onCloseRequested: () -> Unit,
    ): UIViewController = PhotoAnalysisViewController(
        feature = session.feature,
        copy = session.presenterCopy,
        retainedPhoto = { session.retainedPhoto },
        onNativeAction = { action -> onNativeAction(action.toSharedKitAction()) },
        onCloseRequested = onCloseRequested,
    )
}

private fun PhotoAnalysisNativeAction.toSharedKitAction(): SharedKitPhotoAnalysisNativeAction = when (this) {
    PhotoAnalysisNativeAction.TakePhoto -> SharedKitPhotoAnalysisNativeAction.TakePhoto
    PhotoAnalysisNativeAction.ImportPhoto -> SharedKitPhotoAnalysisNativeAction.ImportPhoto
    PhotoAnalysisNativeAction.OpenSettings -> SharedKitPhotoAnalysisNativeAction.OpenSettings
}
