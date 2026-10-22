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
): UIViewController = ComposeUIViewController {
    val uriHandler = LocalUriHandler.current
    ProfileScreen(
        displayName = displayName.collectAsState().value,
        confirmedFoods = feature.confirmedFoods.collectAsState().value,
        actions = ProfileScreenActions(
            onNameChanged = onNameChanged,
            onBackRequested = onCloseRequested,
            onPrivacyPolicyRequested = { uriHandler.openUri(ProfileLegalLinks.PRIVACY_POLICY) },
            onTermsRequested = { uriHandler.openUri(ProfileLegalLinks.TERMS) },
        ),
    )
}
