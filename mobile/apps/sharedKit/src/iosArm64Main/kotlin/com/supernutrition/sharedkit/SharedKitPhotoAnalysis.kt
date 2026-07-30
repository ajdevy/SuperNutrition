package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.PhotoAnalysisNativeAction
import com.supernutrition.feature.photoanalysis.PhotoAnalysisViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
public object SharedKitPhotoAnalysis {
    public fun viewController(
        session: SharedKitPhotoAnalysisSession,
        onNativeAction: (PhotoAnalysisNativeAction) -> Unit,
    ): UIViewController = PhotoAnalysisViewController(session.feature, session.presenterCopy, onNativeAction)
}
