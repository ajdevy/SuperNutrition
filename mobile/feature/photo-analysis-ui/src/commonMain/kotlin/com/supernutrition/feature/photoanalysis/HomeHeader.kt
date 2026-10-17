package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.icon_leaf_vector
import com.supernutrition.feature.photoanalysis.resources.logo_sprout_vector
import com.supernutrition.feature.photoanalysis.resources.profile_button_vector
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun HomeHeader(model: HomeScreenModel, onProfileRequested: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (LocalDensity.current.fontScale > 1.5f) {
            // Give large type a full line instead of splitting brand words around the profile slot.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                HomeProfile(model.profileActionLabel, onProfileRequested)
            }
            HomeBrand(Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(48.dp))
                HomeBrand(Modifier.weight(1f))
                HomeProfile(model.profileActionLabel, onProfileRequested)
            }
        }
        Text(
            text = model.dateLabel,
            color = HomeInk,
            fontFamily = frauncesFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.automationId(UiAutomationIds.HOME_DATE),
        )
        HomeRhythmBadge(model.rhythmBadgeLabel)
    }
}

@Composable
private fun HomeBrand(modifier: Modifier) {
    Box(modifier.semantics { contentDescription = "Super Nutrition" }, contentAlignment = Alignment.TopCenter) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)) {
                    append("Super ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic)) {
                    append("Nutrition")
                }
            },
            color = Color(0xFF173A00),
            fontFamily = frauncesFont(),
            fontSize = 23.5.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.5056).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Image(
            painter = painterResource(Res.drawable.logo_sprout_vector),
            contentDescription = null,
            modifier = Modifier.offset(x = 37.dp, y = 7.dp).size(6.dp, 5.dp),
        )
    }
}

@Composable
private fun HomeRhythmBadge(label: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(ChipGreen)
            .border(1.dp, Color(0xFFAACB6E), RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(painterResource(Res.drawable.icon_leaf_vector), null, Modifier.size(11.dp))
        Text(
            label,
            color = HomeInk,
            fontFamily = nunitoFont(),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun HomeProfile(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.95f))
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .automationId(UiAutomationIds.HOME_PROFILE),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(Res.drawable.profile_button_vector), null, Modifier.size(22.dp))
    }
}
