package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.bubble_tail_vector
import com.supernutrition.feature.photoanalysis.resources.icon_salad_vector
import com.supernutrition.feature.photoanalysis.resources.icon_target_vector
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun HomeCarePrompt(
    model: HomeScreenModel,
    onAddFood: () -> Unit,
    onViewGoals: () -> Unit,
    compact: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val padding = if (compact) 10.dp else 12.dp
        Column(
            modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(20.dp), spotColor = Color(0x2E594D3D))
                .clip(RoundedCornerShape(20.dp)).background(Color.White)
                .padding(start = padding, end = padding, top = padding, bottom = padding - 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = model.careQuestion,
                color = HomeInk,
                fontFamily = frauncesFont(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeChip(
                    label = model.addFoodLabel,
                    appearance = HomeChipAppearance(
                        Res.drawable.icon_salad_vector,
                        ChipGreen,
                        Color(0xFFCFDBCA),
                        HomeInk,
                    ),
                    automationId = UiAutomationIds.HOME_ADD_FOOD,
                    onClick = onAddFood,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                HomeChip(
                    label = model.viewGoalsLabel,
                    appearance = HomeChipAppearance(
                        Res.drawable.icon_target_vector,
                        ChipLilac,
                        ChipLilacLine,
                        Color(0xFF7A4FBF),
                    ),
                    automationId = UiAutomationIds.HOME_VIEW_GOALS,
                    onClick = onViewGoals,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        Image(
            painter = painterResource(Res.drawable.bubble_tail_vector),
            contentDescription = null,
            modifier = Modifier.offset(x = (-24).dp).size(17.dp, if (compact) 12.dp else 16.dp),
        )
    }
}

@Composable
private fun HomeChip(
    label: String,
    appearance: HomeChipAppearance,
    automationId: UiAutomationId,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        // The touch frame extends into the bubble spacing without enlarging the painted chip.
        modifier = modifier.heightIn(min = 48.dp)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            }.automationId(automationId).padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(appearance.background).border(1.dp, appearance.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(appearance.icon), null, Modifier.size(16.dp))
        }
        Text(
            text = label,
            color = appearance.text,
            fontFamily = nunitoFont(),
            fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private data class HomeChipAppearance(
    val icon: DrawableResource,
    val background: Color,
    val border: Color,
    val text: Color,
)
