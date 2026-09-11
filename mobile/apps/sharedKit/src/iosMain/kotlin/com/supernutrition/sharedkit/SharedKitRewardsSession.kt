package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.RewardsFeature
import com.supernutrition.feature.photoanalysis.RewardsPresenterCopy
import platform.Foundation.NSDate

/** Shared Apple session for the temporary Rewards tab. */
public class SharedKitRewardsSession {
    public val presenterCopy: RewardsPresenterCopy = localizedRewardsCopy()
    internal val feature: RewardsFeature = RewardsFeature(
        localDay = {
            ((NSDate().timeIntervalSinceReferenceDate + APPLE_REFERENCE_DATE_OFFSET_SECONDS) / SECONDS_PER_DAY).toInt()
        },
    )

    private companion object {
        const val SECONDS_PER_DAY = 86_400.0
        const val APPLE_REFERENCE_DATE_OFFSET_SECONDS = 978_307_200.0
    }
}
