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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.history_chevron
import com.supernutrition.feature.photoanalysis.resources.history_clock
import com.supernutrition.feature.photoanalysis.resources.history_placeholder
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun MealHistoryCard(meal: MealHistoryMeal, index: Int, copy: MealHistoryCopy, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 77.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x0A1C241E), spotColor = Color(0x0F1C241E))
            .clip(RoundedCornerShape(20.dp)).background(Color.White)
            .clickable(role = Role.Button, onClick = onClick).automationId(UiAutomationIds.mealHistoryRow(index))
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MealHistoryThumbnail(meal, copy)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MealHistoryTime(meal.metadata.timeLabel ?: copy.timeUnavailable)
            Text(
                meal.title,
                color = ProfileBody,
                fontFamily = historyNunitoFont(FontWeight.Bold),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 21.sp,
            )
            Text(
                meal.metadata.ingredientSummary ?: meal.ingredients.joinToString(", ").ifEmpty { copy.confirmedFood },
                color = HistoryMuted,
                fontFamily = nunitoFont(),
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
        }
        Image(painterResource(Res.drawable.history_chevron), null, Modifier.size(18.dp))
    }
}

@Composable
private fun MealHistoryThumbnail(meal: MealHistoryMeal, copy: MealHistoryCopy) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(width = 90.dp, height = 69.dp).clip(shape)
            .background(Color(0xFFE1F3DA)).border(1.dp, Color(0xFFD8EED2), shape),
        contentAlignment = Alignment.Center,
    ) {
        val image = meal.metadata.image
        if (image == null) {
            Image(
                painterResource(Res.drawable.history_placeholder),
                copy.photoUnavailable,
                Modifier.size(width = 52.dp, height = 32.dp),
            )
        } else {
            Image(image, null, Modifier.size(width = 90.dp, height = 69.dp), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun MealHistoryTime(label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(Res.drawable.history_clock), null, Modifier.size(13.dp))
        Text(
            label,
            color = HistoryMuted,
            fontFamily = historyNunitoFont(FontWeight.SemiBold),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
    }
}
