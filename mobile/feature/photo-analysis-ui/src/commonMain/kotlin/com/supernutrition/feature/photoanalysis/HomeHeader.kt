package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.refresh_logo
import com.supernutrition.feature.photoanalysis.resources.refresh_user
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun HomeHeader(model: HomeScreenModel, onProfileRequested: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().automationId(UiAutomationIds.HOME_HEADER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(Res.drawable.refresh_logo), null, Modifier.size(40.5.dp))
        Box(Modifier.weight(1f))
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = model.profileActionLabel, onClick = onProfileRequested)
                .semantics { contentDescription = model.profileActionLabel }.automationId(UiAutomationIds.HOME_PROFILE),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(40.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Image(painterResource(Res.drawable.refresh_user), null, Modifier.size(22.dp))
            }
        }
    }
}
