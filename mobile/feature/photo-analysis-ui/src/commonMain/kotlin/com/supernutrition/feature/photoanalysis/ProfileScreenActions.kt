package com.supernutrition.feature.photoanalysis

/** The host owns session identity and document navigation. */
data class ProfileScreenActions(
    val onNameChanged: (String) -> Unit,
    val onBackRequested: () -> Unit,
    val onPrivacyPolicyRequested: (() -> Unit)? = null,
    val onTermsRequested: (() -> Unit)? = null,
    val onHistoryRequested: () -> Unit = {},
)
