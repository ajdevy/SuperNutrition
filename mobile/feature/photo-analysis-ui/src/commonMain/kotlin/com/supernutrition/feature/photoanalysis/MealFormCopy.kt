package com.supernutrition.feature.photoanalysis

/** Hosts may supply localized authoring copy without changing validation or saved content. */
data class MealFormCopy(
    val addTitle: String = "Add by text",
    val editTitle: String = "Edit meal",
    val description: String = "Describe what you ate",
    val name: String = "Name",
    val portion: String = "Portion (g)",
    val portionDescription: String = "Optional, for the whole meal.",
    val ingredients: String = "Ingredients",
    val ingredientDescription: String = "Separate foods with commas.",
    val add: String = "Add meal",
    val save: String = "Save",
    val cancel: String = "Cancel",
    val cameraHint: String = "No photo? Type the meal instead",
    val saveFailed: String = "Couldn't save this meal. Please try again.",
    val nameRequired: String = "Enter a name for this meal.",
    val nameTooLong: String = "Meal names can contain up to 160 characters.",
    val portionInvalid: String = "Enter a positive portion in grams, or leave it empty.",
    val ingredientsTooLong: String = "The ingredient list is too long.",
    val tooManyIngredients: String = "Enter no more than 50 ingredients.",
    val ingredientRequired: String = "Each ingredient needs a name.",
    val ingredientTooLong: String = "Each ingredient can contain up to 120 characters.",
    val noteTooLong: String = "The meal note is too long.",
    val note: String = "Note",
    val catalogUnavailable: String = "The selected nutrition source is unavailable. Try again or remove the selection.",
    val catalogSelectionInvalid: String = "Choose this product again or remove the selection.",
    val selectedProductPortionDescription: String =
        "Required for the selected product. Enter a positive portion in grams.",
    val selectedProductPortionInvalid: String = "Enter a positive portion in grams for the selected product.",
)

internal fun MealFormCopy.errorDescription(errors: Set<MealDraftError>, catalogSelected: Boolean): String =
    errors.joinToString("\n") {
        when (it) {
            MealDraftError.NAME_REQUIRED -> nameRequired
            MealDraftError.NAME_TOO_LONG -> nameTooLong
            MealDraftError.PORTION_INVALID -> if (catalogSelected) selectedProductPortionInvalid else portionInvalid
            MealDraftError.INGREDIENTS_TOO_LONG -> ingredientsTooLong
            MealDraftError.TOO_MANY_INGREDIENTS -> tooManyIngredients
            MealDraftError.INGREDIENT_REQUIRED -> ingredientRequired
            MealDraftError.INGREDIENT_TOO_LONG -> ingredientTooLong
            MealDraftError.NOTE_TOO_LONG -> noteTooLong
            MealDraftError.CATALOG_UNAVAILABLE -> catalogUnavailable
            MealDraftError.CATALOG_SELECTION_INVALID -> catalogSelectionInvalid
        }
    }
