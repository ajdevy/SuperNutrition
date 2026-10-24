package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIViewController

/** Shared Profile renderer; native hosts own navigation and session lifetime. */
@OptIn(ExperimentalForeignApi::class)
fun profileViewController(
    feature: RewardsFeature,
    displayName: StateFlow<String>,
    onNameChanged: (String) -> Unit,
    onCloseRequested: () -> Unit,
    mealDiary: MealDiary? = null,
): UIViewController = createProfileViewController(
    feature,
    IosProfileIdentity(displayName, onNameChanged, null),
    onCloseRequested,
    mealDiary,
)

@OptIn(ExperimentalForeignApi::class)
fun persistentProfileViewController(
    feature: RewardsFeature,
    profile: LocalProfile,
    onCloseRequested: () -> Unit,
    mealDiary: MealDiary?,
): UIViewController = createProfileViewController(
    feature,
    IosProfileIdentity(profile.displayName, {}, ProfileNamePersistence(profile::updateDisplayName, profile.loadFailed)),
    onCloseRequested,
    mealDiary,
)

private data class IosProfileIdentity(
    val displayName: StateFlow<String>,
    val onNameChanged: (String) -> Unit,
    val persistence: ProfileNamePersistence?,
)

@OptIn(ExperimentalForeignApi::class)
private fun createProfileViewController(
    feature: RewardsFeature,
    identity: IosProfileIdentity,
    onCloseRequested: () -> Unit,
    mealDiary: MealDiary?,
): UIViewController = ComposeUIViewController {
    val uriHandler = LocalUriHandler.current
    val diaryFailure = mealDiary?.failure?.collectAsState()?.value
    val catalogState = mealDiary?.catalogSession?.state?.collectAsState()?.value ?: MealCatalogState.Unavailable
    ProfileFlow(
        state = rememberProfileFlowState(),
        displayName = identity.displayName.collectAsState().value,
        diary = ProfileDiaryContext(
            confirmedFoods = feature.confirmedFoods.collectAsState().value,
            savedMeals = mealDiary?.meals?.collectAsState()?.value.orEmpty(),
            onMealEdited = { id, draft -> mealDiary?.updateFromHistory(id, draft, feature) == true },
            onMealDeleted = { id -> mealDiary?.deleteFromHistory(id, feature) == true },
            storageLoadFailed = diaryFailure != null && diaryFailure != MealDiaryFailure.WRITE_FAILED,
            catalog = MealCatalogContext(catalogState, { mealDiary?.catalogSession?.retry() }),
        ),
        actions = ProfileScreenActions(
            onNameChanged = identity.onNameChanged,
            namePersistence = identity.persistence,
            onBackRequested = onCloseRequested,
            onPrivacyPolicyRequested = { uriHandler.openUri(ProfileLegalLinks.PRIVACY_POLICY) },
            onTermsRequested = { uriHandler.openUri(ProfileLegalLinks.TERMS) },
        ),
    )
}
