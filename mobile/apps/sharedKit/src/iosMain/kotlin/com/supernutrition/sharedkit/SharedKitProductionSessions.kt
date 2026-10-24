package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.LocalUserData
import com.supernutrition.feature.photoanalysis.MealCatalogSession
import com.supernutrition.platform.photoios.IosPrivateDocumentStore

/** Explicit production retention selection; test/acceptance constructors never touch saved user data. */
public object SharedKitProductionSessions {
    public fun photoAnalysisSession(attemptId: String): SharedKitPhotoAnalysisSession = SharedKitPhotoAnalysisSession(
        attemptId,
        localizedPhotoAnalysisCopy(),
        LocalUserData(MealCatalogSession(SharedKitUsdaCatalogFactory())) { id -> IosPrivateDocumentStore(id) },
    )

    public fun profileSession(
        rewardsSession: SharedKitRewardsSession,
        photoAnalysisSession: SharedKitPhotoAnalysisSession,
    ): SharedKitProfileSession = SharedKitProfileSession(
        rewardsSession,
        photoAnalysisSession,
        photoAnalysisSession.localData.profile,
    )
}
