package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.profile_back
import org.jetbrains.compose.resources.painterResource

internal val ProfileBackground = Color(0xFFF6F7F5)
internal val ProfileInk = Color(0xFF253F26)
internal val ProfileBody = Color(0xFF33302C)
internal val ProfileSecondary = Color(0xFF6E6A65)

/** Shared profile. Identity and confirmed foods are supplied by the owning app session. */
@Composable
fun ProfileScreen(
    displayName: String,
    confirmedFoods: List<ConfirmedConsumedFood>,
    actions: ProfileScreenActions,
    modifier: Modifier = Modifier,
    copy: ProfileScreenCopy = ProfileScreenCopy(),
) {
    ControlledProfileScreen(
        identity = ProfileScreenIdentity(displayName, confirmedFoods),
        actions = actions,
        flowState = rememberProfileFlowState(),
        modifier = modifier,
        copy = copy,
    )
}

@Composable
internal fun ControlledProfileScreen(
    identity: ProfileScreenIdentity,
    actions: ProfileScreenActions,
    flowState: ProfileFlowState,
    modifier: Modifier = Modifier,
    copy: ProfileScreenCopy = ProfileScreenCopy(),
) {
    val displayName = identity.displayName
    val confirmedFoods = identity.confirmedFoods
    var dialog by rememberSaveable { mutableStateOf<ProfileDialog?>(null) }
    Box(modifier.fillMaxSize().background(ProfileBackground)) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = MainContentMaxWidth).fillMaxSize()
                .imePadding().verticalScroll(
                    rememberScrollState(),
                ).padding(horizontal = 16.dp).padding(top = 7.dp, bottom = 24.dp),
        ) {
            ProfileHeader(copy) {
                if (flowState.nameEditing) flowState.cancelNameEdit() else actions.onBackRequested()
            }
            Spacer(Modifier.height(11.dp))
            if (actions.namePersistence?.loadFailed == true) ProfileStorageMessage(copy.nameLoadFailed)
            ProfileIdentity(
                displayName,
                copy,
                onEditRequested = flowState::beginNameEdit,
                editor = if (flowState.nameEditing) {
                    { ProfileNameEditor(displayName, copy, actions::saveName, flowState::cancelNameEdit) }
                } else {
                    null
                },
            )
            Spacer(Modifier.height(22.dp))
            if (identity.diaryLoadFailed) ProfileStorageMessage(copy.diaryLoadFailed)
            ProfileHistoryCard(
                if (identity.diaryLoadFailed) null else mergedMealHistory(confirmedFoods, identity.savedMeals).size,
                copy,
                actions.onHistoryRequested,
            )
            Spacer(Modifier.height(14.dp))
            ProfileSettings(
                copy = copy,
                onPrivacyRequested = {
                    openProfileDocument(actions.onPrivacyPolicyRequested) { dialog = ProfileDialog.PRIVACY }
                },
                onTermsRequested = {
                    openProfileDocument(actions.onTermsRequested) { dialog = ProfileDialog.TERMS }
                },
                onCrashReportsRequested = { dialog = ProfileDialog.CRASH_REPORTS },
            )
        }
    }
    ProfileDialogs(dialog, copy) { dialog = null }
}

internal enum class ProfileDialog { PRIVACY, TERMS, CRASH_REPORTS }

private fun openProfileDocument(action: (() -> Unit)?, onUnavailable: () -> Unit) {
    if (action == null) {
        onUnavailable()
        return
    }
    try {
        action()
    } catch (_: IllegalArgumentException) {
        // Native HTTPS handlers throw when the device has no browser available.
        onUnavailable()
    }
}

@Composable
private fun ProfileHeader(copy: ProfileScreenCopy, onBackRequested: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = copy.back, onClick = onBackRequested)
                .semantics { contentDescription = copy.back }.automationId(UiAutomationIds.PROFILE_CLOSE),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.size(38.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Image(painterResource(Res.drawable.profile_back), null, Modifier.size(20.dp))
            }
        }
        Text(
            copy.title,
            color = ProfileInk,
            fontFamily = frauncesFont(),
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).automationId(UiAutomationIds.PROFILE_TITLE),
        )
        Spacer(Modifier.size(48.dp))
    }
}
