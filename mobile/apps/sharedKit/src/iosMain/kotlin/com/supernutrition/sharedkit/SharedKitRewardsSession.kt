package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.RewardsFeature
import com.supernutrition.feature.photoanalysis.RewardsPresenterCopy
import com.supernutrition.feature.photoanalysis.currentMealRecordContext

/** Shared Apple session for the temporary Rewards tab. */
public class SharedKitRewardsSession {
    public val presenterCopy: RewardsPresenterCopy = localizedRewardsCopy()
    internal val feature: RewardsFeature = RewardsFeature(
        localDay = { currentMealRecordContext().localDay },
    )
}
