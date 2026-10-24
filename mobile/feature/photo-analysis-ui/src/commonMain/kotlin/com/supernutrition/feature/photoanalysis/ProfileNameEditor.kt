package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.refresh_name_clear
import com.supernutrition.feature.photoanalysis.resources.refresh_name_save
import org.jetbrains.compose.resources.painterResource

/** Native input and keyboard, with an optional session name owned by the host. */
@Composable
internal fun ProfileNameEditor(
    displayName: String,
    copy: ProfileScreenCopy,
    onNameChanged: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var draft by remember(displayName) { mutableStateOf(displayName) }
    var saveFailed by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val save = {
        val normalized = ProfileNamePolicy.normalize(draft)
        if (normalized != null && ProfileNamePolicy.canSubmit(draft, displayName)) {
            if (onNameChanged(normalized)) {
                focus.clearFocus()
                onDismiss()
            } else {
                saveFailed = true
            }
        }
    }
    LaunchedEffect(Unit) { requester.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ProfileNameInputRow(
            draft,
            copy,
            requester,
            ProfileNameInputActions(
                onDraftChanged = {
                    draft = it
                    saveFailed = false
                },
                onSave = save,
                onCancel = {
                    focus.clearFocus()
                    onDismiss()
                },
                canSave = ProfileNamePolicy.canSubmit(draft, displayName),
            ),
        )
        if (saveFailed) ProfileStorageMessage(copy.nameSaveFailed)
        if (ProfileNamePolicy.normalize(draft) == null) ProfileStorageMessage(copy.nameInvalid)
        Text(
            copy.sessionNameDescription,
            color = ProfileSecondary,
            fontFamily = nunitoFont(),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfileNameInputRow(
    draft: String,
    copy: ProfileScreenCopy,
    requester: FocusRequester,
    actions: ProfileNameInputActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).background(Color.White, CircleShape)
                .border(1.dp, Color(0xFF7AB462), CircleShape).padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = actions.onDraftChanged,
                singleLine = true,
                textStyle = TextStyle(
                    color = ProfileInk,
                    fontFamily = frauncesFont(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                ),
                cursorBrush = SolidColor(Color(0xFF7AB462)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.onSave() }),
                modifier = Modifier.weight(
                    1f,
                ).focusRequester(requester).automationId(UiAutomationIds.PROFILE_NAME_INPUT)
                    .semantics {
                        contentDescription = "${copy.unknownName}. ${copy.sessionNameDescription}"
                        customActions = listOf(
                            CustomAccessibilityAction(copy.cancel) {
                                actions.onCancel()
                                true
                            },
                        )
                    },
            )
            ProfileNameClearButton(copy) { actions.onDraftChanged("") }
        }
        ProfileNameSaveButton(copy, actions.canSave, actions.onSave)
    }
}

private data class ProfileNameInputActions(
    val onDraftChanged: (String) -> Unit,
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
    val canSave: Boolean,
)

@Composable
private fun ProfileNameClearButton(copy: ProfileScreenCopy, onClear: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = copy.clearName, onClick = onClear)
            .semantics { contentDescription = copy.clearName }.automationId(UiAutomationIds.PROFILE_NAME_CLEAR),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(20.dp).background(Color(0xFFE7E4E0), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(Res.drawable.refresh_name_clear), null, Modifier.size(12.dp))
        }
    }
}

@Composable
private fun ProfileNameSaveButton(copy: ProfileScreenCopy, enabled: Boolean, onSave: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(
            enabled = enabled,
            role = Role.Button,
            onClickLabel = copy.save,
            onClick = onSave,
        ).semantics { contentDescription = copy.save }.automationId(UiAutomationIds.PROFILE_NAME_SAVE),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(32.dp).background(Color(0xFF518841), CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(Res.drawable.refresh_name_save), null, Modifier.size(18.dp))
        }
    }
}
