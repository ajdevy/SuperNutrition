package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.photoanalysis.CandidateReviewDraft

/** Reconfirming one retained review replaces its own diary entry rather than duplicating it. */
internal class MealReviewDiaryRecorder(private val diary: MealDiary) {
    private var savedMealId: String? = null

    fun reset() {
        savedMealId = null
    }

    fun save(confirmation: PhotoAnalysisMealReviewConfirmation, evidence: CandidateReviewDraft?): Boolean {
        val selectedIds = confirmation.photoEvidenceComponentIds.toSet()
        val labels = evidence?.photoEvidence.orEmpty()
            .filter { it.componentId in selectedIds }.map { it.candidate.label }
        val draft = ValidatedMealDraft(
            name = confirmation.dishLabel.trim().ifEmpty { "Custom meal" },
            portionGrams = null,
            ingredients = (labels + confirmation.addedIngredientLabels).map(String::trim).distinct(),
            note = confirmation.note,
        )
        val previousId = savedMealId
        if (previousId != null) return diary.update(previousId, draft)
        val saved = diary.add(draft, SavedMealSource.PHOTO) ?: return false
        savedMealId = saved.id
        return true
    }
}
