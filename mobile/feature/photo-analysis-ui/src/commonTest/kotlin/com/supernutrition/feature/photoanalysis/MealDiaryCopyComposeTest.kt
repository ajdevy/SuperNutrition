package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MealDiaryCopyComposeTest {
    @Test
    fun `localized authoring labels actions and errors do not depend on English title`() = runComposeUiTest {
        val copy = MealFormCopy(
            editTitle = "Modifica pasto",
            name = "Nome",
            save = "Salva",
            cancel = "Annulla",
            nameRequired = "Inserisci un nome.",
        )
        setContent {
            MealForm(
                config = MealFormConfig(copy.editTitle, editing = true),
                actions = MealFormActions(onSave = { true }, onCancel = {}),
                copy = copy,
            )
        }
        onNodeWithText("Modifica pasto").assertIsDisplayed()
        onNodeWithText("Nome").assertIsDisplayed()
        onNodeWithText("Name").assertDoesNotExist()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_SAVE.value).performScrollTo().performClick()
        onNodeWithText("Inserisci un nome.").performScrollTo().assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_FORM_CANCEL.value).performScrollTo()
            .assertContentDescriptionEquals("Annulla")
    }

    @Test
    fun `detail source availability and nutrient sections use supplied copy`() = runComposeUiTest {
        val meal = mergedMealHistory(emptyList(), listOf(diaryMealFixture())).single()
        val copy = MealDetailsCopy(
            nutritionUnavailable = "Nutrition indisponible",
            per100Grams = "Pour 100 g",
            nutrients = MealNutrientCopy(vitamins = "Vitamines"),
        )
        setContent { MealDetailsScreen(meal, MealDetailsActions(onBackRequested = {}), copy = copy) }
        onNodeWithText("Nutrition indisponible").assertIsDisplayed()
        onNodeWithText("Pour 100 g").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.MEAL_DETAILS_VITAMINS.value).performScrollTo().assertIsDisplayed()
        onNodeWithText("Vitamines").assertIsDisplayed()
        onNodeWithText("Energy").assertDoesNotExist()
    }

    @Test
    fun `Home count uses supplied singular and plural templates`() {
        val copy = LiveHomeCopy(singularMealCount = "{count} repas", pluralMealCount = "{count} repas enregistrés")
        assertEquals("1 repas", liveHomeScreenModel("Aujourd'hui", 1, copy).rhythmSubtitle)
        assertEquals("2 repas enregistrés", liveHomeScreenModel("Aujourd'hui", 2, copy).rhythmSubtitle)
    }
}
