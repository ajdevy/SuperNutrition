package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.RewardsFeature
import com.supernutrition.feature.photoanalysis.RewardsPresenterCopy
import platform.Foundation.NSDate

/** Shared Apple session for the temporary Rewards tab. */
public class SharedKitRewardsSession {
    public val presenterCopy: RewardsPresenterCopy = localizedRewardsCopy()
    internal val feature: RewardsFeature = RewardsFeature(
        localDay = { (NSDate().timeIntervalSince1970 / SECONDS_PER_DAY).toInt() },
    )

    private companion object {
        const val SECONDS_PER_DAY = 86_400.0
    }
}
