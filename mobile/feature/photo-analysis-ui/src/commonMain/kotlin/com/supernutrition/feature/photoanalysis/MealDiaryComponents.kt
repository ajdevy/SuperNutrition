package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.profile_back
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DiaryHeader(
    title: String,
    backId: UiAutomationId,
    onBack: () -> Unit,
    backLabel: String = "Back",
    titleId: UiAutomationId? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = backLabel }.automationId(backId),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.size(38.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Image(painterResource(Res.drawable.profile_back), null, Modifier.size(20.dp))
            }
        }
        Text(
            title,
            fontFamily = frauncesFont(),
            color = ProfileInk,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).then(if (titleId != null) Modifier.automationId(titleId) else Modifier),
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
internal fun DiaryCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
internal fun DiaryHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontFamily = historyNunitoFont(FontWeight.Bold),
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        color = ProfileBody,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
internal fun DiaryBody(text: String, modifier: Modifier = Modifier, secondary: Boolean = false) {
    Text(
        text,
        fontFamily = nunitoFont(),
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = if (secondary) ProfileSecondary else ProfileBody,
        modifier = modifier,
    )
}

@Composable
internal fun DiaryButton(text: String, id: UiAutomationId, onClick: () -> Unit, primary: Boolean = true) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(28.dp))
            .background(if (primary) Color(0xFF518841) else Color(0xFFE1F3DA))
            .clickable(role = Role.Button, onClick = onClick).automationId(id).padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontFamily = historyNunitoFont(FontWeight.Bold),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            color = if (primary) Color.White else ProfileInk,
        )
    }
}
