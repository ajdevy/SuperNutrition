package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.refresh_bubble_tail
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun HomeCarePrompt(model: HomeScreenModel, onAddFood: () -> Unit) {
    Column(
        Modifier.automationId(UiAutomationIds.HOME_CARE_PROMPT).widthIn(max = 240.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            model.careQuestion,
            color = HomeInk,
            fontFamily = frauncesFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.shadow(8.dp, RoundedCornerShape(22.dp), spotColor = Color(0x0F1C241E))
                .background(Color.White, RoundedCornerShape(22.dp))
                .widthIn(
                    min = 183.dp,
                ).heightIn(
                    min = 48.dp,
                ).clickable(role = Role.Button, onClickLabel = model.addFoodLabel, onClick = onAddFood)
                .automationId(UiAutomationIds.HOME_ADD_FOOD).padding(horizontal = 12.dp, vertical = 13.dp),
        )
        Image(
            painterResource(Res.drawable.refresh_bubble_tail),
            null,
            Modifier.offset(x = (-11).dp).size(17.dp, 15.5.dp),
        )
    }
}
