package com.supernutrition.feature.photoanalysis

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.supernutrition.platform.photoandroid.AndroidPrivateDocumentStore

/** Only the distributable app activates approved retention. Acceptance and PoC hosts stay isolated. */
internal fun androidLocalUserData(application: Application, catalogSession: MealCatalogSession): LocalUserData =
    if (application.packageName == "app.supernutrition") {
        LocalUserData(catalogSession) { id -> AndroidPrivateDocumentStore(application, id) }
    } else {
        LocalUserData(catalogSession)
    }

internal data class LocalProfileSnapshot(val name: String, val nameLoadFailed: Boolean, val diaryLoadFailed: Boolean)

@Composable
internal fun LocalUserData.profileSnapshot(): LocalProfileSnapshot {
    val name by profile.displayName.collectAsState()
    val diaryFailure by mealDiary.failure.collectAsState()
    return LocalProfileSnapshot(
        name,
        profile.loadFailed,
        diaryFailure != null && diaryFailure != MealDiaryFailure.WRITE_FAILED,
    )
}
