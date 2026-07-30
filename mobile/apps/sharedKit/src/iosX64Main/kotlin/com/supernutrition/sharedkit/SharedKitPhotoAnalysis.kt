package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.PhotoAnalysisNativeAction
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

/**
 * Keeps the Swift API shape identical while the Intel renderer remains a
 * compatibility-only SwiftUI implementation. This function is never called
 * from the x86_64 branch of the host.
 */
@OptIn(ExperimentalForeignApi::class)
public object SharedKitPhotoAnalysis {
    public fun viewController(
        session: SharedKitPhotoAnalysisSession,
        onNativeAction: (PhotoAnalysisNativeAction) -> Unit,
    ): UIViewController = error("Compose iOS UI is unavailable on the iosX64 compatibility lane")
}
