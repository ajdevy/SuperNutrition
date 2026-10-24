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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.food_sticker_bread
import com.supernutrition.feature.photoanalysis.resources.refresh_fats
import com.supernutrition.feature.photoanalysis.resources.refresh_fiber
import com.supernutrition.feature.photoanalysis.resources.refresh_protein
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun MealDetailsPortion(
    meal: MealHistoryMeal,
    per100: Boolean,
    copy: MealDetailsCopy,
    onChanged: (Boolean) -> Unit,
) {
    val portion = meal.portionGrams()?.let { copy.portionTemplate.replace("{grams}", diaryNumber(it)) }
        ?: copy.unknownPortion
    Row(
        Modifier.fillMaxWidth().background(Color.White, CircleShape).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MealPortionTab(
            portion,
            !per100,
            Modifier.weight(1f).automationId(UiAutomationIds.MEAL_DETAILS_PORTION),
        ) { onChanged(false) }
        MealPortionTab(
            copy.per100Grams,
            per100,
            Modifier.weight(1f).automationId(UiAutomationIds.MEAL_DETAILS_PER_100),
        ) { onChanged(true) }
    }
}

@Composable
private fun MealPortionTab(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).background(if (active) Color(0xFFE1F3DA) else Color.White, CircleShape)
            .clickable(role = Role.Tab, onClick = onClick).semantics { selected = active }.padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontFamily = nunitoFont(),
            color = if (active) ProfileInk else ProfileSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun MealDetailsNutrition(meal: MealHistoryMeal, per100: Boolean, copy: MealDetailsCopy) {
    val nutrients = meal.nutrition(per100)
    DiaryHeading(copy.nutrition)
    DiaryBody(if (per100) copy.per100Description else copy.perPortionDescription, secondary = true)
    val cards = listOf(
        MealMacro(copy.protein, nutrients?.proteinGrams, Res.drawable.refresh_protein),
        MealMacro(copy.carbs, nutrients?.carbohydrateGrams, Res.drawable.food_sticker_bread),
        MealMacro(copy.fat, nutrients?.fatGrams, Res.drawable.refresh_fats),
        MealMacro(copy.fiber, nutrients?.fiberGrams, Res.drawable.refresh_fiber),
    )
    cards.chunked(if (LocalDensity.current.fontScale > 1.3f) 1 else 2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { card -> MealMacroCard(card, Modifier.weight(1f)) }
        }
    }
}

private data class MealMacro(val title: String, val amount: Double?, val icon: DrawableResource)

@Composable
private fun MealMacroCard(card: MealMacro, modifier: Modifier) {
    DiaryCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(36.dp).background(Color(0xFFF8F4E9), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(card.icon), null, Modifier.size(22.dp)) }
            Column(Modifier.weight(1f)) {
                DiaryBody(card.title, secondary = true)
                Text(
                    nutrientValue(card.amount),
                    color = ProfileBody,
                    fontFamily = frauncesFont(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    lineHeight = 25.sp,
                )
            }
        }
    }
}

@Composable
internal fun MealDetailsIngredients(meal: MealHistoryMeal, onEdit: (() -> Unit)?, copy: MealDetailsCopy) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DiaryHeading(copy.ingredients, Modifier.weight(1f))
        onEdit?.let {
            Box(
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = it)
                    .automationId(UiAutomationIds.MEAL_DETAILS_EDIT).padding(horizontal = 12.dp, vertical = 10.dp),
            ) { DiaryBody(copy.edit) }
        }
    }
    DiaryCard(Modifier.automationId(UiAutomationIds.MEAL_DETAILS_INGREDIENTS)) {
        if (meal.ingredients.isEmpty()) DiaryBody(copy.noIngredients, secondary = true)
        meal.ingredients.forEachIndexed { index, ingredient ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DiaryBody(ingredient, Modifier.weight(1f))
                DiaryBody(
                    nutrientValue(meal.groundedFoods.getOrNull(index)?.grounded?.serving?.grams),
                    secondary = true,
                )
            }
        }
    }
    meal.savedMeal?.note?.takeIf { it.isNotBlank() }?.let { note ->
        DiaryHeading(copy.notes)
        DiaryCard { DiaryBody(note) }
    }
}
