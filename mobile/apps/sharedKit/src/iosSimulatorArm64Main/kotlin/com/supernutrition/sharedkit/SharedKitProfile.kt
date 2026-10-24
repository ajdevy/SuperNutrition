package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.persistentProfileViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
public object SharedKitProfile {
    public fun viewController(session: SharedKitProfileSession, onCloseRequested: () -> Unit): UIViewController =
        persistentProfileViewController(
            feature = session.rewardsFeature,
            profile = session.profile,
            onCloseRequested = onCloseRequested,
            mealDiary = session.mealDiary,
        )
}
