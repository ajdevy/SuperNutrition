package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds

/** Explicit authoring shared by camera text entry and historical correction. Drafts stay in memory. */
@Composable
internal fun MealForm(
    config: MealFormConfig,
    actions: MealFormActions,
    modifier: Modifier = Modifier,
    copy: MealFormCopy = MealFormCopy(),
) {
    var draft by remember(config.initialDraft) { mutableStateOf(config.initialDraft) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val cancel = {
        focus.clearFocus()
        actions.onCancel()
    }
    Column(
        modifier.fillMaxWidth().background(ProfileBackground).imePadding().verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        MealFormHeader(config.title, config.editing, copy, cancel)
        DiaryBody(copy.description, secondary = true)
        MealCatalogChooser(draft, config.catalog) {
            draft = it
            error = null
        }
        MealFormIdentity(draft, copy) {
            draft = it
            error = null
        }
        MealFormInput(
            copy.ingredients,
            draft.ingredients,
            UiAutomationIds.MEAL_FORM_INGREDIENTS,
            options = MealFormInputOptions(multiline = true),
        ) {
            draft = draft.withEditedIngredients(it)
            error = null
        }
        DiaryBody(copy.ingredientDescription, secondary = true)
        if (config.editing) {
            MealFormInput(
                copy.note,
                draft.note,
                UiAutomationIds.MEAL_FORM_NOTE,
                options = MealFormInputOptions(multiline = true),
            ) {
                draft = draft.copy(note = it)
                error = null
            }
        }
        error?.let { DiaryBody(it, Modifier.automationId(UiAutomationIds.MEAL_FORM_ERROR)) }
        DiaryButton(if (config.editing) copy.save else copy.add, UiAutomationIds.MEAL_FORM_SAVE, onClick = {
            val validation = MealDraftValidator.validate(draft, config.existingMeal, config.catalog.state.catalogOrNull)
            when (val result = validation) {
                is MealDraftValidation.Invalid -> {
                    error = copy.errorDescription(result.errors, draft.catalogBinding != null)
                }

                is MealDraftValidation.Valid -> {
                    if (actions.onSave(draft)) focus.clearFocus() else error = copy.saveFailed
                }
            }
        })
    }
}

@Composable
private fun MealFormIdentity(draft: MealDraft, copy: MealFormCopy, onChanged: (MealDraft) -> Unit) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MealFormInput(copy.name, draft.name, UiAutomationIds.MEAL_FORM_NAME) { onChanged(draft.withEditedName(it)) }
            MealFormPortion(draft, copy, onChanged)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1.7f)) {
                MealFormInput(
                    copy.name,
                    draft.name,
                    UiAutomationIds.MEAL_FORM_NAME,
                ) { onChanged(draft.withEditedName(it)) }
            }
            Column(Modifier.weight(1f)) { MealFormPortion(draft, copy, onChanged) }
        }
    }
    DiaryBody(
        if (draft.catalogBinding == null) copy.portionDescription else copy.selectedProductPortionDescription,
        secondary = true,
    )
}

@Composable
private fun MealFormPortion(draft: MealDraft, copy: MealFormCopy, onChanged: (MealDraft) -> Unit) {
    MealFormInput(
        copy.portion,
        draft.portion,
        UiAutomationIds.MEAL_FORM_PORTION,
        MealFormInputOptions(decimal = true),
    ) {
        onChanged(draft.copy(portion = it))
    }
}
