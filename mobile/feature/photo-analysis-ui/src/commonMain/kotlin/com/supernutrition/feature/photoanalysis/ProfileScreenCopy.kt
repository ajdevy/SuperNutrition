package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Immutable

/** Host-replaceable copy for the session-scoped profile and its local dialogs. */
@Immutable
data class ProfileScreenCopy(
    val title: String = "Profile",
    val back: String = "Back to Home",
    val unknownName: String = "Your name",
    val editName: String = "Edit name",
    val save: String = "Save",
    val cancel: String = "Cancel",
    val done: String = "Done",
    val history: String = "Meal history",
    val mealSaved: String = "meal saved",
    val mealsSaved: String = "meals saved",
    val emptyHistory: String = "No meals saved yet. Meals you confirm will appear here during this session.",
    val privacy: String = "Privacy Policy",
    val terms: String = "Terms & Conditions",
    val crashReports: String = "Send crash reports",
    val crashReportsDescription: String = "Share diagnostic data to help fix crashes",
    val crashReportsState: String = "Off",
    val privacyUnavailable: String = "The privacy policy could not be opened. Please try again with a web browser.",
    val termsUnavailable: String = "The terms could not be opened. Please try again with a web browser.",
    val crashReportsUnavailable: String = "Crash reporting is unavailable in this build. No crash reports are sent.",
    val sessionNameDescription: String = "Your name is optional and stays in this app session.",
)
