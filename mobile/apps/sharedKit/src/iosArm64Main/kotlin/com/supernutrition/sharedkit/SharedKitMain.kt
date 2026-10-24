package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.mainViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
public object SharedKitMain {
    public fun viewController(
        session: SharedKitPhotoAnalysisSession,
        rewardsSession: SharedKitRewardsSession,
        onCaptureRequested: () -> Unit,
        onProfileRequested: () -> Unit,
    ): UIViewController = mainViewController(
        feature = session.feature,
        rewards = rewardsSession.feature,
        copy = rewardsSession.presenterCopy,
        onCaptureRequested = onCaptureRequested,
        onProfileRequested = onProfileRequested,
    )
}
