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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.icon_leaf_vector
import com.supernutrition.feature.photoanalysis.resources.profile_button_vector
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun HomeHeader(model: HomeScreenModel, onProfileRequested: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HomeBrandAndProfile(model.profileActionLabel, onProfileRequested)
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
private fun HomeBrandAndProfile(profileLabel: String, onProfileRequested: () -> Unit) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            HomeBrand(Modifier.fillMaxWidth())
            HomeProfile(profileLabel, onProfileRequested)
        },
    ) { measurables, constraints ->
        val naturalConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val profile = measurables[1].measure(naturalConstraints)
        val besideWidth = (constraints.maxWidth - profile.width * 2).coerceAtLeast(0)
        // Read the rendered brand's own typography; there is no second font or wordmark definition.
        val besideProfile = measurables[0].maxIntrinsicWidth(Constraints.Infinity) <= besideWidth
        val brandWidth = if (besideProfile) besideWidth else constraints.maxWidth
        val brand = measurables[0].measure(naturalConstraints.copy(minWidth = brandWidth, maxWidth = brandWidth))
        val gap = 4.dp.roundToPx()
        val height = if (besideProfile) maxOf(brand.height, profile.height) else profile.height + gap + brand.height
        layout(constraints.maxWidth, height) {
            if (besideProfile) {
                brand.placeRelative(profile.width, (height - brand.height) / 2)
                profile.placeRelative(constraints.maxWidth - profile.width, (height - profile.height) / 2)
            } else {
                profile.placeRelative(constraints.maxWidth - profile.width, 0)
                brand.placeRelative(0, profile.height + gap)
            }
        }
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
