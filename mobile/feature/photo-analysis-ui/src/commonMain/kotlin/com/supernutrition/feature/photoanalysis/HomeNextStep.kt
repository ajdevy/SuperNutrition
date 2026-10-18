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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.icon_chevron_right_vector
import com.supernutrition.feature.photoanalysis.resources.icon_sun_vector
import com.supernutrition.feature.photoanalysis.resources.salad
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun HomeNextStep(model: HomeScreenModel, onDoIt: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().automationId(UiAutomationIds.HOME_NEXT_STEP)
            .clip(RoundedCornerShape(20.dp)).background(Color(0xFFFDF3DF))
            .border(1.5.dp, Color(0xFFF0DCA8), RoundedCornerShape(20.dp))
            .padding(12.dp),
    ) {
        HomeNextStepLayout(model, onDoIt)
    }
}

@Composable
private fun HomeNextStepLayout(model: HomeScreenModel, onDoIt: () -> Unit) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            HomeNextStepIllustration(model.saladDescription)
            HomeNextStepCopy(model, Modifier)
            HomeNextStepAction(model.nextStepAction, onDoIt)
        },
    ) { measurables, constraints ->
        val naturalConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val illustration = measurables[0].measure(naturalConstraints)
        val action = measurables[2].measure(naturalConstraints)
        val gap = 8.dp.roundToPx()
        val rowCopyWidth = (constraints.maxWidth - illustration.width - action.width - gap * 2).coerceAtLeast(0)
        val stackCopyWidth = (constraints.maxWidth - illustration.width - gap).coerceAtLeast(0)
        val rowFitsWords = rowCopyWidth >= measurables[1].minIntrinsicWidth(Constraints.Infinity)
        val rowHeight = if (rowFitsWords) {
            maxOf(illustration.height, action.height, measurables[1].maxIntrinsicHeight(rowCopyWidth))
        } else {
            Constraints.Infinity
        }
        val stackHeight = maxOf(illustration.height, measurables[1].maxIntrinsicHeight(stackCopyWidth)) +
            gap + action.height
        val useRow = rowFitsWords && rowHeight <= stackHeight
        val copyWidth = if (useRow) rowCopyWidth else stackCopyWidth
        val copy = measurables[1].measure(naturalConstraints.copy(minWidth = copyWidth, maxWidth = copyWidth))
        val copyRowHeight = maxOf(illustration.height, copy.height)
        val height = if (useRow) maxOf(copyRowHeight, action.height) else copyRowHeight + gap + action.height
        layout(constraints.maxWidth, height) {
            val topRowHeight = if (useRow) height else copyRowHeight
            illustration.placeRelative(0, (topRowHeight - illustration.height) / 2)
            copy.placeRelative(illustration.width + gap, (topRowHeight - copy.height) / 2)
            val actionY = if (useRow) (height - action.height) / 2 else copyRowHeight + gap
            action.placeRelative(constraints.maxWidth - action.width, actionY)
        }
    }
}

@Composable
private fun HomeNextStepIllustration(description: String) {
    Image(
        painter = painterResource(Res.drawable.salad),
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(56.dp, 44.dp),
    )
}

@Composable
private fun HomeNextStepCopy(model: HomeScreenModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(Res.drawable.icon_sun_vector), null, Modifier.size(15.dp))
            Text(
                text = model.nextStepTitle,
                color = HomeAmberText,
                fontFamily = nunitoFont(),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                modifier = Modifier.weight(1f).automationId(UiAutomationIds.HOME_EMPTY_TITLE),
            )
        }
        Text(
            text = model.nextStepDescription,
            color = HomeBody,
            fontFamily = frauncesFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            modifier = Modifier.automationId(UiAutomationIds.HOME_EMPTY_DESCRIPTION),
        )
    }
}

@Composable
private fun HomeNextStepAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFFEECD3))
            .border(1.dp, Color(0xFFFED596), RoundedCornerShape(16.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            color = HomeAmberText,
            fontFamily = nunitoFont(),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        )
        Image(painterResource(Res.drawable.icon_chevron_right_vector), null, Modifier.size(16.dp))
    }
}
