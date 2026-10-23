package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.refresh_camera
import com.supernutrition.feature.photoanalysis.resources.refresh_home_active
import com.supernutrition.feature.photoanalysis.resources.refresh_home_inactive
import com.supernutrition.feature.photoanalysis.resources.refresh_rewards_active
import com.supernutrition.feature.photoanalysis.resources.refresh_rewards_inactive
import org.jetbrains.compose.resources.painterResource

internal val MainBottomBarCanvasHeight = 80.dp
internal val MainBottomBarHeight = MainBottomBarCanvasHeight

/** Current Figma capsule; all three controls retain native 48dp targets and natural label height. */
@Composable
fun PhotoAnalysisMainBottomBar(
    state: PhotoAnalysisMainBottomBarState,
    actions: PhotoAnalysisMainBottomBarActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = 398.dp).fillMaxWidth().heightIn(min = 64.dp)
                .shadow(8.dp, CircleShape, spotColor = Color(0x141C241E)).background(Color.White, CircleShape)
                .automationId(UiAutomationIds.BOTTOM_BAR_PILL).padding(horizontal = 24.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab(
                state.homeLabel,
                state.selectedTab == 0,
                UiAutomationIds.HOME_TAB,
                actions.onHomeSelected,
                Modifier.weight(1f),
            )
            // White source artwork needs 3:1: this close green gives 3.048:1; Figma's gives 2.461:1.
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF6DA158))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = state.cameraLabel,
                        onClick = actions.onCameraRequested,
                    )
                    .semantics { contentDescription = state.cameraLabel }.automationId(UiAutomationIds.CAMERA_ACTION),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(Res.drawable.refresh_camera), null, Modifier.size(25.455.dp))
            }
            MainTab(
                state.rewardsLabel,
                state.selectedTab == 1,
                UiAutomationIds.REWARDS_TAB,
                actions.onRewardsSelected,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MainTab(label: String, isSelected: Boolean, id: UiAutomationId, onClick: () -> Unit, modifier: Modifier) {
    val icon = if (id == UiAutomationIds.REWARDS_TAB) {
        if (isSelected) Res.drawable.refresh_rewards_active else Res.drawable.refresh_rewards_inactive
    } else {
        if (isSelected) Res.drawable.refresh_home_active else Res.drawable.refresh_home_inactive
    }
    Column(
        modifier.heightIn(
            min = 60.dp,
        ).clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = isSelected
            }.automationId(id).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Image(
                painterResource(icon),
                null,
                if (id == UiAutomationIds.REWARDS_TAB) {
                    Modifier.size(16.6148.dp, 17.1.dp)
                } else {
                    Modifier.size(22.dp)
                },
            )
        }
        Text(
            label,
            color = if (isSelected) Color(0xFF3C6B31) else Color(0xFF243740),
            fontFamily = nunitoFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

data class PhotoAnalysisMainBottomBarState(
    val selectedTab: Int,
    val homeLabel: String,
    val rewardsLabel: String,
    val cameraLabel: String,
)

data class PhotoAnalysisMainBottomBarActions(
    val onHomeSelected: () -> Unit,
    val onRewardsSelected: () -> Unit,
    val onCameraRequested: () -> Unit,
)
