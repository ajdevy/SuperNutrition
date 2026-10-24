package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.ProfileLegalLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Optional identity is scoped to this host session and is never uploaded. */
public class SharedKitProfileSession(
    rewardsSession: SharedKitRewardsSession,
    photoAnalysisSession: SharedKitPhotoAnalysisSession?,
) {
    public constructor(rewardsSession: SharedKitRewardsSession) : this(rewardsSession, null)

    internal val rewardsFeature = rewardsSession.feature
    internal val mealDiary = photoAnalysisSession?.feature?.mealDiary
    private val name = MutableStateFlow("")
    internal val displayNameFlow = name.asStateFlow()

    public val displayName: String
        get() = name.value

    public val privacyPolicyUrl: String
        get() = ProfileLegalLinks.PRIVACY_POLICY

    public val termsUrl: String
        get() = ProfileLegalLinks.TERMS

    public fun updateDisplayName(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isEmpty()) return false
        name.value = normalized
        return true
    }
}
