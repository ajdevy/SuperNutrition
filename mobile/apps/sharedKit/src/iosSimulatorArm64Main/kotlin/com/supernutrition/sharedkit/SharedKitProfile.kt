package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.profileViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
public object SharedKitProfile {
    public fun viewController(session: SharedKitProfileSession, onCloseRequested: () -> Unit): UIViewController =
        profileViewController(
            feature = session.rewardsFeature,
            displayName = session.displayNameFlow,
            onNameChanged = { session.updateDisplayName(it) },
            onCloseRequested = onCloseRequested,
            mealDiary = session.mealDiary,
        )
}
