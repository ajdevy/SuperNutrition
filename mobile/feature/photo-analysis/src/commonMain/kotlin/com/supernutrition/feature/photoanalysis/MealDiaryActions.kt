package com.supernutrition.feature.photoanalysis

/** An explicit text save is independent of the photo-analysis workflow state. */
fun PhotoAnalysisFeature.saveTextMeal(draft: MealDraft): Boolean = mealDiary.add(draft, SavedMealSource.TEXT) != null

/** Refreshes history and rewards synchronously after storage accepts an explicit correction. */
fun MealDiary.updateFromHistory(id: String, draft: MealDraft, rewards: RewardsFeature): Boolean =
    update(id, draft) { saved -> rewards.updateRecordedMeals(saved) }

/** A deleted authored meal cannot uncover obsolete grounded facts during diary publication. */
fun MealDiary.deleteFromHistory(id: String, rewards: RewardsFeature): Boolean {
    val removed = meals.value.firstOrNull { it.id == id } ?: return false
    return delete(id) { remaining -> rewards.refreshDiaryProjection(remaining, removed) }
}
