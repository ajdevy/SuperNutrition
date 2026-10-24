package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Immutable

/** Host-replaceable copy for the session-scoped profile and its local dialogs. */
@Immutable
data class ProfileScreenCopy(
    val title: String = "Profile",
    val back: String = "Back to Home",
    val unknownName: String = "Your name",
    val editName: String = "Edit name",
    val clearName: String = "Clear name",
    val save: String = "Save",
    val cancel: String = "Cancel",
    val done: String = "Done",
    val history: String = "Meal history",
    val mealSaved: String = "meal saved",
    val mealsSaved: String = "meals saved",
    val emptyHistory: String = "No meals saved yet. Meals you confirm will appear here.",
    val privacy: String = "Privacy Policy",
    val terms: String = "Terms & Conditions",
    val crashReports: String = "Send crash reports",
    val crashReportsDescription: String = "Share diagnostic data to help fix crashes",
    val crashReportsState: String = "Off",
    val privacyUnavailable: String = "The privacy policy could not be opened. Please try again with a web browser.",
    val termsUnavailable: String = "The terms could not be opened. Please try again with a web browser.",
    val crashReportsUnavailable: String = "Crash reporting is unavailable in this build. No crash reports are sent.",
    val sessionNameDescription: String = "Your name is optional and stays on this device until you clear it and save.",
    val nameSaveFailed: String = "Your name could not be saved. Please try again. Your previous name is unchanged.",
    val nameLoadFailed: String = "Your saved name could not be loaded. It has not been erased or replaced.",
    val nameInvalid: String = "Use up to ${ProfileNamePolicy.MAX_LENGTH} characters without control characters.",
    val diaryLoadFailed: String = "Your saved meals could not be loaded. They have not been erased or replaced.",
    val historyUnavailable: String = "Count unavailable",
)
