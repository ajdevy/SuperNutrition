package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds

private val NavigationPill = Color(0xFFF3EEEB)
private val ActiveGreen = Color(0xFF8EA336)
private val CameraGreen = Color(0xFF92A638)
private val InactiveIcon = Color(0xFF767B7D)
private val InactiveLabel = Color(0xFF243740)
private val CameraRing = Color(0xFF252726)

/** Figma-aligned two-tab shell with a centered camera action. */
@Composable
fun PhotoAnalysisMainBottomBar(
    state: PhotoAnalysisMainBottomBarState,
    actions: PhotoAnalysisMainBottomBarActions,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
            .height(80.dp),
    ) {
        PhotoAnalysisBottomBarPill(
            modifier = Modifier.align(Alignment.BottomCenter),
            state = state,
            actions = actions,
        )
        PhotoAnalysisCameraButton(
            modifier = Modifier.align(Alignment.TopCenter),
            cameraLabel = state.cameraLabel,
            onClick = actions.onCameraRequested,
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

@Composable
private fun PhotoAnalysisBottomBarPill(
    modifier: Modifier,
    state: PhotoAnalysisMainBottomBarState,
    actions: PhotoAnalysisMainBottomBarActions,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(
                elevation = 12.dp,
                shape = NavigationPillShape,
                spotColor = Color(0x332B211C),
            )
            .clip(NavigationPillShape)
            .background(NavigationPill),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 42.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotoAnalysisBottomBarItem(
                label = state.homeLabel,
                selected = state.selectedTab == 0,
                automationId = UiAutomationIds.HOME_TAB,
                onClick = actions.onHomeSelected,
                icon = { color -> HomeIcon(color) },
            )
            PhotoAnalysisBottomBarItem(
                label = state.rewardsLabel,
                selected = state.selectedTab == 1,
                automationId = UiAutomationIds.REWARDS_TAB,
                onClick = actions.onRewardsSelected,
                icon = { color -> TrophyIcon(color) },
            )
        }
    }
}

@Composable
private fun PhotoAnalysisCameraButton(modifier: Modifier, cameraLabel: String, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(50.dp)
            .shadow(5.dp, CircleShape, spotColor = Color(0x662B211C))
            .clip(CircleShape)
            .background(CameraRing)
            .padding(3.dp)
            .clip(CircleShape)
            .background(CameraGreen)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = cameraLabel
                role = Role.Button
            }
            .automationId(UiAutomationIds.CAMERA_ACTION),
        contentAlignment = Alignment.Center,
    ) {
        CameraIcon(Color.White)
    }
}

private val NavigationPillShape = GenericShape { size, _ ->
    val radius = size.height / 2f
    val center = size.width / 2f
    val notchHalfWidth = size.width * (50f / 319f)
    val notchDepth = size.height * 0.64f
    moveTo(radius, 0f)
    lineTo(center - notchHalfWidth, 0f)
    cubicTo(
        center - notchHalfWidth * 0.72f,
        0f,
        center - notchHalfWidth * 0.72f,
        notchDepth,
        center,
        notchDepth,
    )
    cubicTo(
        center + notchHalfWidth * 0.72f,
        notchDepth,
        center + notchHalfWidth * 0.72f,
        0f,
        center + notchHalfWidth,
        0f,
    )
    lineTo(size.width - radius, 0f)
    arcTo(
        rect = Rect(size.width - 2f * radius, 0f, size.width, size.height),
        startAngleDegrees = -90f,
        sweepAngleDegrees = 180f,
        forceMoveTo = false,
    )
    lineTo(radius, size.height)
    arcTo(
        rect = Rect(0f, 0f, 2f * radius, size.height),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 180f,
        forceMoveTo = false,
    )
    close()
}

@Composable
private fun PhotoAnalysisBottomBarItem(
    label: String,
    selected: Boolean,
    automationId: com.supernutrition.core.ui.UiAutomationId,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val color = if (selected) ActiveGreen else InactiveIcon
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 58.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            }
            .automationId(automationId),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            icon(color)
            Text(
                text = label,
                color = if (selected) ActiveGreen else InactiveLabel,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HomeIcon(color: Color) {
    Canvas(Modifier.size(width = 17.dp, height = 18.dp)) {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height * 0.42f)
            lineTo(size.width, size.height)
            lineTo(size.width * 0.62f, size.height)
            lineTo(size.width * 0.62f, size.height * 0.54f)
            lineTo(size.width * 0.38f, size.height * 0.54f)
            lineTo(size.width * 0.38f, size.height)
            lineTo(0f, size.height)
            lineTo(0f, size.height * 0.42f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun TrophyIcon(color: Color) {
    Canvas(Modifier.size(width = 17.dp, height = 18.dp)) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val body = Path().apply {
            moveTo(size.width * 0.22f, 0f)
            lineTo(size.width * 0.78f, 0f)
            lineTo(size.width * 0.78f, size.height * 0.32f)
            cubicTo(
                size.width * 0.78f,
                size.height * 0.57f,
                size.width * 0.64f,
                size.height * 0.7f,
                size.width * 0.5f,
                size.height * 0.7f,
            )
            cubicTo(
                size.width * 0.36f,
                size.height * 0.7f,
                size.width * 0.22f,
                size.height * 0.57f,
                size.width * 0.22f,
                size.height * 0.32f,
            )
            close()
        }
        drawPath(body, color)
        drawLine(
            color,
            Offset(size.width * 0.5f, size.height * 0.7f),
            Offset(size.width * 0.5f, size.height * 0.86f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.width * 0.3f, size.height * 0.86f),
            Offset(size.width * 0.7f, size.height * 0.86f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawArc(
            color = color,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(-size.width * 0.01f, size.height * 0.08f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.25f, size.height * 0.28f),
            style = stroke,
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.76f, size.height * 0.08f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.25f, size.height * 0.28f),
            style = stroke,
        )
    }
}

@Composable
private fun CameraIcon(color: Color) {
    Canvas(Modifier.size(width = 20.dp, height = 17.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(1.dp.toPx(), 3.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width - 2.dp.toPx(), size.height - 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            style = stroke,
        )
        drawLine(
            color,
            Offset(size.width * 0.31f, 3.dp.toPx()),
            Offset(size.width * 0.4f, 1.dp.toPx()),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.width * 0.4f, 1.dp.toPx()),
            Offset(size.width * 0.6f, 1.dp.toPx()),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.width * 0.6f, 1.dp.toPx()),
            Offset(size.width * 0.69f, 3.dp.toPx()),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = color,
            radius = 3.2.dp.toPx(),
            center = Offset(size.width / 2f, size.height / 2f + 1.dp.toPx()),
            style = stroke,
        )
    }
}
