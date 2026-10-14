package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.camera_fab_vector
import com.supernutrition.feature.photoanalysis.resources.icon_home_fill_vector
import com.supernutrition.feature.photoanalysis.resources.icon_trophy_fill_vector
import com.supernutrition.feature.photoanalysis.resources.nav_pill_vector
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val ActiveIcon = Color(0xFF8EA336)
private val InactiveIcon = Color(0xFF767B7D)
private val TabLabel = Color(0xFF243740)
internal val MainBottomBarHeight = 83.dp
private const val BAR_DESIGN_WIDTH = 375f
private const val TAB_CENTER_DISTANCE = 95f

/** A bounded, centered navigation shell with mirrored tab slots and a camera action. */
@Composable
fun PhotoAnalysisMainBottomBar(
    state: PhotoAnalysisMainBottomBarState,
    actions: PhotoAnalysisMainBottomBarActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(MainBottomBarHeight), contentAlignment = Alignment.TopCenter) {
        val canvasWidth = minOf(maxWidth.value, BAR_DESIGN_WIDTH)
        val scale = canvasWidth / BAR_DESIGN_WIDTH
        Box(Modifier.width(canvasWidth.dp).height(MainBottomBarHeight)) {
            Image(
                painter = painterResource(Res.drawable.nav_pill_vector),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 10.5.dp)
                    .requiredSize((347f * scale).dp, 86.dp).automationId(UiAutomationIds.BOTTOM_BAR_PILL),
            )
            PhotoAnalysisCameraButton(
                cameraLabel = state.cameraLabel,
                onClick = actions.onCameraRequested,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 3.dp),
            )
            PhotoAnalysisBottomBarItem(
                item = BottomBarItem(
                    state.homeLabel,
                    state.selectedTab == 0,
                    UiAutomationIds.HOME_TAB,
                    Res.drawable.icon_home_fill_vector,
                ),
                onClick = actions.onHomeSelected,
                modifier = Modifier.offset(
                    x = (canvasWidth / 2 - TAB_CENTER_DISTANCE * scale - 36).dp,
                    y = 20.5.dp,
                ),
            )
            PhotoAnalysisBottomBarItem(
                item = BottomBarItem(
                    state.rewardsLabel,
                    state.selectedTab == 1,
                    UiAutomationIds.REWARDS_TAB,
                    Res.drawable.icon_trophy_fill_vector,
                ),
                onClick = actions.onRewardsSelected,
                modifier = Modifier.offset(
                    x = (canvasWidth / 2 + TAB_CENTER_DISTANCE * scale - 36).dp,
                    y = 20.5.dp,
                ),
            )
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
private fun PhotoAnalysisCameraButton(cameraLabel: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier.size(50.dp).clickable(onClick = onClick)
            .semantics {
                contentDescription = cameraLabel
                role = Role.Button
            }
            .automationId(UiAutomationIds.CAMERA_ACTION),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(Res.drawable.camera_fab_vector), null, Modifier.fillMaxSize())
    }
}

@Composable
private fun PhotoAnalysisBottomBarItem(item: BottomBarItem, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.size(72.dp, 58.dp)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .semantics {
                contentDescription = item.label
                role = Role.Tab
                selected = item.selected
            }
            .automationId(item.automationId),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(item.icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (item.selected) ActiveIcon else InactiveIcon),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = item.label,
            color = TabLabel,
            fontFamily = nunitoFont(),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class BottomBarItem(
    val label: String,
    val selected: Boolean,
    val automationId: UiAutomationId,
    val icon: DrawableResource,
)
