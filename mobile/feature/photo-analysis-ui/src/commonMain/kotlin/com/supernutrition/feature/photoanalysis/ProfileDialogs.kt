package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.supernutrition.domain.nutrition.ConfirmedConsumedFood

@Composable
internal fun ProfileDialogs(
    dialog: ProfileDialog?,
    displayName: String,
    confirmedFoods: List<ConfirmedConsumedFood>,
    copy: ProfileScreenCopy,
    actions: ProfileDialogActions,
) {
    when (dialog) {
        ProfileDialog.NAME -> ProfileNameDialog(displayName, copy, actions.onNameChanged, actions.onDismiss)

        ProfileDialog.HISTORY -> ProfileHistoryDialog(confirmedFoods, copy, actions.onDismiss)

        ProfileDialog.PRIVACY ->
            ProfileInformationDialog(copy.privacy, copy.privacyUnavailable, copy.done, actions.onDismiss)

        ProfileDialog.TERMS -> ProfileInformationDialog(copy.terms, copy.termsUnavailable, copy.done, actions.onDismiss)

        ProfileDialog.CRASH_REPORTS ->
            ProfileInformationDialog(copy.crashReports, copy.crashReportsUnavailable, copy.done, actions.onDismiss)

        null -> Unit
    }
}

@Composable
private fun ProfileNameDialog(
    displayName: String,
    copy: ProfileScreenCopy,
    onNameChanged: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(displayName) { mutableStateOf(displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(copy.editName, fontFamily = frauncesFont()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(copy.sessionNameDescription, fontFamily = nunitoFont())
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(copy.unknownName, fontFamily = nunitoFont()) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.isNotBlank(),
                onClick = {
                    onNameChanged(draft.trim())
                    onDismiss()
                },
            ) { Text(copy.save, fontFamily = nunitoFont()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(copy.cancel, fontFamily = nunitoFont()) } },
    )
}

@Composable
private fun ProfileHistoryDialog(foods: List<ConfirmedConsumedFood>, copy: ProfileScreenCopy, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(copy.history, fontFamily = frauncesFont()) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (foods.isEmpty()) Text(copy.emptyHistory, fontFamily = nunitoFont())
                foods.groupBy { it.mealId }.values.forEach { meal ->
                    Text(
                        meal.joinToString(", ") { it.displayName },
                        color = ProfileBody,
                        fontFamily = nunitoFont(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(copy.done, fontFamily = nunitoFont()) } },
    )
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
