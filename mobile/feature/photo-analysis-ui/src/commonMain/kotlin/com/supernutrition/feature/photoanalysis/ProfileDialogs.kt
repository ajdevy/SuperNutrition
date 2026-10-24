package com.supernutrition.feature.photoanalysis

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun ProfileDialogs(dialog: ProfileDialog?, copy: ProfileScreenCopy, onDismiss: () -> Unit) {
    when (dialog) {
        ProfileDialog.PRIVACY -> ProfileInformationDialog(copy.privacy, copy.privacyUnavailable, copy.done, onDismiss)

        ProfileDialog.TERMS -> ProfileInformationDialog(copy.terms, copy.termsUnavailable, copy.done, onDismiss)

        ProfileDialog.CRASH_REPORTS ->
            ProfileInformationDialog(copy.crashReports, copy.crashReportsUnavailable, copy.done, onDismiss)

        null -> Unit
    }
}

@Composable
private fun ProfileInformationDialog(title: String, body: String, done: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = frauncesFont()) },
        text = { Text(body, fontFamily = nunitoFont()) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(done, fontFamily = nunitoFont()) } },
    )
}
