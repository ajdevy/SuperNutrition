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
): UIViewController = ComposeUIViewController {
    val uriHandler = LocalUriHandler.current
    ProfileFlow(
        state = rememberProfileFlowState(),
        displayName = displayName.collectAsState().value,
        diary = ProfileDiaryContext(
            confirmedFoods = feature.confirmedFoods.collectAsState().value,
            savedMeals = mealDiary?.meals?.collectAsState()?.value.orEmpty(),
            onMealEdited = { id, draft -> mealDiary?.updateFromHistory(id, draft, feature) == true },
            onMealDeleted = { id -> mealDiary?.deleteFromHistory(id, feature) == true },
        ),
        actions = ProfileScreenActions(
            onNameChanged = onNameChanged,
            onBackRequested = onCloseRequested,
            onPrivacyPolicyRequested = { uriHandler.openUri(ProfileLegalLinks.PRIVACY_POLICY) },
            onTermsRequested = { uriHandler.openUri(ProfileLegalLinks.TERMS) },
        ),
    )
}
