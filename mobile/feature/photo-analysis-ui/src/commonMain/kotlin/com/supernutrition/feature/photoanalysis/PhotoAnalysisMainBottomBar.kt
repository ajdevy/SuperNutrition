package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.camera_fab_vector
import com.supernutrition.feature.photoanalysis.resources.icon_home_fill_vector
import com.supernutrition.feature.photoanalysis.resources.icon_trophy_fill_vector
import com.supernutrition.feature.photoanalysis.resources.nav_pill_vector
import com.supernutrition.feature.photoanalysis.resources.nunito
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

private val ActiveIcon = Color(0xFF8EA336)
private val ActiveLabel = Color(0xFF3A6A05)
private val InactiveIcon = Color(0xFF767B7D)
private val InactiveLabel = Color(0xFF243740)
private const val DESIGN_SCREEN_WIDTH = 375f
private const val DESIGN_CANVAS_HEIGHT = 111f
private const val BAR_LEFT = 28f
private const val BAR_WIDTH = 319f
private const val BAR_HEIGHT = 78.5f
private const val PILL_TOP = 20.5f
private const val PILL_WIDTH = 319f
private const val PILL_HEIGHT = 58f
private const val PILL_ASSET_LEFT = 0f
private const val PILL_ASSET_TOP = 4f
private const val PILL_ASSET_WIDTH = 347f
private const val PILL_ASSET_HEIGHT = 86f
private const val CAMERA_LEFT = 134.5f
private const val CAMERA_TOP = 3f
private const val TAB_TOP = 20.5f
private const val HOME_TAB_LEFT = 36.8f
private const val HOME_ICON_LEFT = 17.8f
private const val HOME_ICON_TOP = 12.6f
private const val REWARDS_TAB_LEFT = 228f
private const val REWARDS_ICON_LEFT = 18.3f
private const val REWARDS_ICON_TOP = 13.9f
private const val TAB_WIDTH = 52f
private const val TAB_HEIGHT = 58f
private const val TAB_LABEL_TOP = 35.2f

@Composable
private fun bottomBarNunitoFont() = FontFamily(Font(Res.font.nunito))

/** Figma-aligned two-tab shell with a centered camera action. */
@Composable
fun PhotoAnalysisMainBottomBar(
    state: PhotoAnalysisMainBottomBarState,
    actions: PhotoAnalysisMainBottomBarActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.TopStart,
    ) {
        val scaleX = maxWidth.value / DESIGN_SCREEN_WIDTH

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((DESIGN_CANVAS_HEIGHT * scaleX).dp),
        ) {
            Box(
                modifier = Modifier
                    .offset((BAR_LEFT * scaleX).dp, 0.dp)
                    .requiredSize((BAR_WIDTH * scaleX).dp, (BAR_HEIGHT * scaleX).dp),
            ) {
                PhotoAnalysisBottomBarPill(
                    modifier = Modifier.offset(0.dp, (PILL_TOP * scaleX).dp),
                    scale = scaleX,
                )
                PhotoAnalysisCameraButton(
                    modifier = Modifier.offset((CAMERA_LEFT * scaleX).dp, (CAMERA_TOP * scaleX).dp),
                    cameraLabel = state.cameraLabel,
                    onClick = actions.onCameraRequested,
                    scale = scaleX,
                )
                PhotoAnalysisBottomBarItem(
                    modifier = Modifier.offset((HOME_TAB_LEFT * scaleX).dp, (TAB_TOP * scaleX).dp),
                    label = state.homeLabel,
                    selected = state.selectedTab == 0,
                    automationId = UiAutomationIds.HOME_TAB,
                    onClick = actions.onHomeSelected,
                    iconLeft = HOME_ICON_LEFT * scaleX,
                    iconTop = HOME_ICON_TOP * scaleX,
                    scale = scaleX,
                    icon = { color, iconScale -> HomeIcon(color, iconScale) },
                )
                PhotoAnalysisBottomBarItem(
                    modifier = Modifier.offset((REWARDS_TAB_LEFT * scaleX).dp, (TAB_TOP * scaleX).dp),
                    label = state.rewardsLabel,
                    selected = state.selectedTab == 1,
                    automationId = UiAutomationIds.REWARDS_TAB,
                    onClick = actions.onRewardsSelected,
                    iconLeft = REWARDS_ICON_LEFT * scaleX,
                    iconTop = REWARDS_ICON_TOP * scaleX,
                    scale = scaleX,
                    icon = { color, iconScale -> TrophyIcon(color, iconScale) },
                )
            }
        }
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

@Composable
private fun PhotoAnalysisBottomBarPill(
    modifier: Modifier,
    scale: Float,
) {
    Box(
        modifier = modifier
            .requiredSize((PILL_WIDTH * scale).dp, (PILL_HEIGHT * scale).dp)
    ) {
        Image(
            painter = painterResource(Res.drawable.nav_pill_vector),
            contentDescription = null,
            modifier = Modifier
                .offset((PILL_ASSET_LEFT * scale).dp, (PILL_ASSET_TOP * scale).dp)
                .requiredSize((PILL_ASSET_WIDTH * scale).dp, (PILL_ASSET_HEIGHT * scale).dp)
                .automationId(UiAutomationIds.BOTTOM_BAR_PILL),
        )
    }
}

@Composable
private fun PhotoAnalysisCameraButton(
    modifier: Modifier,
    cameraLabel: String,
    onClick: () -> Unit,
    scale: Float,
) {
    Box(
        modifier = modifier
            .size((50f * scale).dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = cameraLabel
                role = Role.Button
            }
            .automationId(UiAutomationIds.CAMERA_ACTION),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.camera_fab_vector),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PhotoAnalysisBottomBarItem(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    automationId: com.supernutrition.core.ui.UiAutomationId,
    onClick: () -> Unit,
    iconLeft: Float,
    iconTop: Float,
    scale: Float,
    icon: @Composable (Color, Float) -> Unit,
) {
    val color = if (selected) ActiveIcon else InactiveIcon
    Box(
        modifier = modifier
            .requiredSize((TAB_WIDTH * scale).dp, (TAB_HEIGHT * scale).dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            }
            .automationId(automationId),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(Modifier.offset(iconLeft.dp, iconTop.dp)) { icon(color, scale) }
        Text(
            text = label,
            color = if (selected) ActiveLabel else InactiveLabel,
            fontFamily = bottomBarNunitoFont(),
            fontSize = (9f * scale).sp,
            lineHeight = (12f * scale).sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .offset(y = (TAB_LABEL_TOP * scale).dp)
                .requiredSize((TAB_WIDTH * scale).dp, (12f * scale).dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun HomeIcon(color: Color, scale: Float) {
    Image(
        painter = painterResource(Res.drawable.icon_home_fill_vector),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(width = (16.3f * scale).dp, height = (18f * scale).dp),
    )
}

@Composable
private fun TrophyIcon(color: Color, scale: Float) {
    Image(
        painter = painterResource(Res.drawable.icon_trophy_fill_vector),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(width = (16.6148f * scale).dp, height = (17.1f * scale).dp),
    )
}
