package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.RewardsViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
public object SharedKitRewards {
    public fun viewController(session: SharedKitRewardsSession): UIViewController = RewardsViewController(
        feature = session.feature,
        copy = session.presenterCopy,
    )
}
