package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.PartialNutrientProfile

@Composable
internal fun MealDetailsBreakdowns(meal: MealHistoryMeal, per100: Boolean, copy: MealDetailsCopy) {
    mealNutrientSections(meal.nutrition(per100), copy.nutrients).forEach { section ->
        MealNutrientSection(section, copy.nutrients)
    }
    DiaryBody(copy.unknownDescription, secondary = true)
}

@Composable
private fun MealNutrientSection(section: MealNutrientSectionModel, copy: MealNutrientCopy) {
    var expanded by remember(section.id) { mutableStateOf(true) }
    DiaryCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { expanded = !expanded }
                .automationId(
                    section.id,
                ).semantics { stateDescription = if (expanded) copy.expanded else copy.collapsed },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiaryHeading(section.title, Modifier.weight(1f))
            Text(if (expanded) "−" else "+", color = ProfileSecondary, fontFamily = nunitoFont())
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                section.rows.forEach { (name, value) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DiaryBody(name, Modifier.weight(1f), secondary = true)
                        DiaryBody(value, secondary = true)
                    }
                }
            }
        }
    }
}

private data class MealNutrientSectionModel(
    val title: String,
    val id: UiAutomationId,
    val rows: List<Pair<String, String>>,
)

private fun mealNutrientSections(values: PartialNutrientProfile?, copy: MealNutrientCopy) = listOf(
    MealNutrientSectionModel(
        copy.carbohydrates,
        UiAutomationIds.MEAL_DETAILS_CARBS,
        listOf(
            copy.totalCarbs to nutrientValue(values?.carbohydrateGrams),
            copy.fiber to nutrientValue(values?.fiberGrams),
            copy.sugars to nutrientValue(values?.sugarGrams),
            copy.addedSugars to "—",
        ),
    ),
    MealNutrientSectionModel(
        copy.fatBreakdown,
        UiAutomationIds.MEAL_DETAILS_FATS,
        listOf(copy.totalFat to nutrientValue(values?.fatGrams)) + listOf(
            copy.unsaturated,
            copy.monounsaturated,
            copy.polyunsaturated,
            copy.omega3,
            copy.omega6,
            copy.saturated,
            copy.transFat,
        ).map { it to "—" },
    ),
    MealNutrientSectionModel(copy.vitamins, UiAutomationIds.MEAL_DETAILS_VITAMINS, copy.vitaminNames.map { it to "—" }),
    MealNutrientSectionModel(
        copy.minerals,
        UiAutomationIds.MEAL_DETAILS_MINERALS,
        copy.mineralNames.map { it to "—" } + (copy.sodium to nutrientValue(values?.sodiumMilligrams, "mg")),
    ),
    MealNutrientSectionModel(copy.more, UiAutomationIds.MEAL_DETAILS_MORE, copy.moreNames.map { it to "—" }),
)
