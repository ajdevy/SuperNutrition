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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.profile_chevron
import com.supernutrition.feature.photoanalysis.resources.profile_diagnostics
import com.supernutrition.feature.photoanalysis.resources.profile_diagnostics_off
import com.supernutrition.feature.photoanalysis.resources.profile_history
import com.supernutrition.feature.photoanalysis.resources.profile_privacy
import com.supernutrition.feature.photoanalysis.resources.profile_terms
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ProfileHistoryCard(mealCount: Int?, copy: ProfileScreenCopy, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 74.dp).shadow(1.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).border(1.dp, Color(0xFFD8EED2), RoundedCornerShape(20.dp))
            .background(Color(0xFFE1F3DA)).clickable(role = Role.Button, onClick = onClick)
            .automationId(UiAutomationIds.PROFILE_HISTORY).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileRowIcon(Res.drawable.profile_history, Color(0xFFE1F3DA), history = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ProfileRowTitle(copy.history, bold = true)
            Text(
                if (mealCount == null) {
                    copy.historyUnavailable
                } else {
                    "$mealCount ${if (mealCount == 1) copy.mealSaved else copy.mealsSaved}"
                },
                color = ProfileSecondary,
                fontFamily = nunitoFont(),
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
        }
        ProfileChevron()
    }
}

@Composable
internal fun ProfileSettings(
    copy: ProfileScreenCopy,
    onPrivacyRequested: () -> Unit,
    onTermsRequested: () -> Unit,
    onCrashReportsRequested: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).background(Color.White)
            .padding(top = 6.dp, bottom = 8.dp),
    ) {
        ProfileSettingsRow(
            copy.privacy,
            Res.drawable.profile_privacy,
            UiAutomationIds.PROFILE_PRIVACY,
            onPrivacyRequested,
        )
        ProfileDivider()
        ProfileSettingsRow(
            copy.terms,
            Res.drawable.profile_terms,
            UiAutomationIds.PROFILE_TERMS,
            onTermsRequested,
        )
        ProfileDivider()
        ProfileCrashReportsRow(copy, onCrashReportsRequested)
    }
}

@Composable
private fun ProfileSettingsRow(title: String, icon: DrawableResource, id: UiAutomationId, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)
            .clickable(role = Role.Button, onClick = onClick).automationId(id)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileRowIcon(icon, Color(0xFFF8F4E9))
        Box(Modifier.weight(1f)) { ProfileRowTitle(title) }
        ProfileChevron()
    }
}

@Composable
private fun ProfileCrashReportsRow(copy: ProfileScreenCopy, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = copy.crashReportsState }
            .automationId(UiAutomationIds.PROFILE_CRASH_REPORTS).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileRowIcon(Res.drawable.profile_diagnostics, Color(0xFFF8F4E9))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ProfileRowTitle(copy.crashReports)
            Text(
                copy.crashReportsDescription,
                color = ProfileSecondary,
                fontFamily = nunitoFont(),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        Image(painterResource(Res.drawable.profile_diagnostics_off), null, Modifier.size(width = 54.dp, height = 37.dp))
    }
}

@Composable
private fun ProfileRowIcon(icon: DrawableResource, background: Color, history: Boolean = false) {
    Box(
        modifier = Modifier.size(if (history) 40.dp else 38.dp).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), null, Modifier.size(if (history) 22.dp else 20.dp))
    }
}

@Composable
private fun ProfileRowTitle(title: String, bold: Boolean = false) {
    Text(
        title,
        color = ProfileBody,
        fontFamily = nunitoFont(),
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
    )
}

@Composable
private fun ProfileChevron() {
    Image(painterResource(Res.drawable.profile_chevron), null, Modifier.size(20.dp))
}

@Composable
private fun ProfileDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 68.dp, end = 14.dp).height(1.dp).background(Color(0xFFEFEAE4)))
}
