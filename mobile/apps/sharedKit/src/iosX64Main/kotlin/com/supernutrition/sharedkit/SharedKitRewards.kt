package com.supernutrition.sharedkit

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

/** Compose is unavailable on the Intel compatibility lane; SwiftUI owns the fallback list. */
@OptIn(ExperimentalForeignApi::class)
public object SharedKitRewards {
    @Suppress("UnusedParameter")
    public fun viewController(session: SharedKitRewardsSession): UIViewController =
        error("Compose iOS UI is unavailable on the iosX64 compatibility lane")
}
