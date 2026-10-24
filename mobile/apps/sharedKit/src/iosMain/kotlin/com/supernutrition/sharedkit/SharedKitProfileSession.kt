package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.LocalProfile
import com.supernutrition.feature.photoanalysis.ProfileLegalLinks

/** The production composition selects durable local identity; legacy constructors remain isolated. */
public class SharedKitProfileSession internal constructor(
    rewardsSession: SharedKitRewardsSession,
    photoAnalysisSession: SharedKitPhotoAnalysisSession?,
    internal val profile: LocalProfile,
) {
    public constructor(
        rewardsSession: SharedKitRewardsSession,
        photoAnalysisSession: SharedKitPhotoAnalysisSession?,
    ) : this(rewardsSession, photoAnalysisSession, LocalProfile())

    public constructor(rewardsSession: SharedKitRewardsSession) : this(rewardsSession, null)

    internal val rewardsFeature = rewardsSession.feature
    internal val mealDiary = photoAnalysisSession?.feature?.mealDiary
    internal val displayNameFlow = profile.displayName

    public val displayName: String
        get() = profile.displayName.value

    public val privacyPolicyUrl: String
        get() = ProfileLegalLinks.PRIVACY_POLICY

    public val termsUrl: String
        get() = ProfileLegalLinks.TERMS

    public fun updateDisplayName(value: String): Boolean = profile.updateDisplayName(value)
}
