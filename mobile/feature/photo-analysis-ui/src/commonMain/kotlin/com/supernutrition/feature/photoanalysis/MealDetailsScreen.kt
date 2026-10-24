package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds

/** Figma Meal Details over real entries; photos and numeric estimates are never reconstructed. */
@Composable
fun MealDetailsScreen(
    meal: MealHistoryMeal,
    actions: MealDetailsActions,
    modifier: Modifier = Modifier,
    referenceDay: Int = currentHistoryReferenceDay(),
    copy: MealDetailsCopy = MealDetailsCopy(),
) {
    var per100 by remember(meal.id) { mutableStateOf(false) }
    var deleting by remember(meal.id) { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().background(ProfileBackground).automationId(UiAutomationIds.MEAL_DETAILS_SCREEN)
            .padding(horizontal = 16.dp).padding(top = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = MainContentMaxWidth).fillMaxWidth()) {
            DiaryHeader("", UiAutomationIds.MEAL_DETAILS_BACK, actions.onBackRequested, copy.back)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MealDetailsIdentity(meal, referenceDay, copy)
                MealDetailsSource(meal, copy)
                MealDetailsPortion(meal, per100, copy) { per100 = it }
                MealDetailsNutrition(meal, per100, copy)
                MealDetailsIngredients(meal, actions.onEditRequested, copy)
                MealDetailsBreakdowns(meal, per100, copy)
                actions.onDeleteRequested?.let {
                    DiaryButton(
                        copy.deleteMeal,
                        UiAutomationIds.MEAL_DETAILS_DELETE,
                        { deleting = true },
                        primary = false,
                    )
                }
            }
        }
    }
    if (deleting && actions.onDeleteRequested != null) {
        MealDeleteConfirmation(actions.onDeleteRequested, { deleting = false }, copy)
    }
}

@Composable
private fun MealDetailsIdentity(meal: MealHistoryMeal, referenceDay: Int, copy: MealDetailsCopy) {
    val image = meal.metadata.image
    if (image != null) {
        Image(
            image,
            null,
            Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        DiaryBody(copy.photoUnavailable, secondary = true)
    }
    Text(
        meal.title,
        color = ProfileInk,
        fontFamily = frauncesFont(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        modifier = Modifier.automationId(UiAutomationIds.MEAL_DETAILS_TITLE),
    )
    DiaryBody(
        listOf(
            mealHistoryDayLabel(meal.id.localDay, referenceDay, MealHistoryCopy()),
            meal.metadata.timeLabel ?: copy.timeUnavailable,
        ).joinToString(" · "),
        secondary = true,
    )
    meal.savedMeal?.let {
        DiaryBody(if (it.source == SavedMealSource.PHOTO) copy.photoSource else copy.textSource, secondary = true)
    }
}

@Composable
private fun MealDetailsSource(meal: MealHistoryMeal, copy: MealDetailsCopy) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFE1F3DA), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (meal.groundedFoods.isEmpty() || meal.savedMeal != null) {
            DiaryBody(copy.nutritionUnavailable, Modifier.automationId(UiAutomationIds.MEAL_DETAILS_NUTRITION_UNKNOWN))
            DiaryBody(
                copy.unavailableDescription,
                secondary = true,
            )
        } else {
            DiaryBody(copy.nutritionSource)
            DiaryBody(
                meal.groundedFoods.map { it.grounded.catalogSnapshot.identity }
                    .distinct().joinToString { "${it.catalogId} · ${it.version}" },
                secondary = true,
            )
        }
    }
}

@Composable
private fun MealDeleteConfirmation(onDelete: () -> Boolean, onDismiss: () -> Unit, copy: MealDetailsCopy) {
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.automationRoot(),
        title = { Text(copy.deleteTitle, fontFamily = frauncesFont()) },
        text = {
            DiaryBody(if (failed) copy.deleteFailed else copy.deleteDescription)
        },
        confirmButton = {
            TextButton(
                onClick = { if (onDelete()) onDismiss() else failed = true },
                modifier = Modifier.automationId(UiAutomationIds.MEAL_DETAILS_DELETE_CONFIRM),
            ) { Text(copy.delete, fontFamily = nunitoFont()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(copy.cancel, fontFamily = nunitoFont()) } },
    )
}
