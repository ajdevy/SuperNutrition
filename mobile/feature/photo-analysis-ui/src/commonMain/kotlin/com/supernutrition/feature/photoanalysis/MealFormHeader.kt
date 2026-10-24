package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.refresh_name_clear
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun MealFormHeader(title: String, editing: Boolean, copy: MealFormCopy, onCancel: () -> Unit) {
    if (editing) {
        DiaryHeader(title, UiAutomationIds.MEAL_FORM_CANCEL, onCancel, copy.cancel, UiAutomationIds.MEAL_FORM_TITLE)
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = ProfileBody,
                fontFamily = frauncesFont(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                modifier = Modifier.weight(1f).automationId(UiAutomationIds.MEAL_FORM_TITLE).semantics { heading() },
            )
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onCancel)
                    .automationId(UiAutomationIds.MEAL_FORM_CANCEL).semantics { contentDescription = copy.cancel },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(32.dp).background(Color(0xFFF8F4E9), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Image(painterResource(Res.drawable.refresh_name_clear), null, Modifier.size(16.dp)) }
            }
        }
    }
}
