package com.supernutrition.feature.photoanalysis

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Explicit diary writes publish only after the complete document has been saved successfully. */
class MealDiary(
    private val recordContext: () -> MealRecordContext = ::currentMealRecordContext,
    private val store: MealDiaryStore = InMemoryMealDiaryStore(),
) {
    private var writable = true
    private val _failure = MutableStateFlow<MealDiaryFailure?>(null)
    private val _meals = MutableStateFlow(load())

    val meals: StateFlow<List<SavedMeal>> = _meals.asStateFlow()
    val failure: StateFlow<MealDiaryFailure?> = _failure.asStateFlow()

    fun add(draft: MealDraft, source: SavedMealSource): SavedMeal? {
        val validated = MealDraftValidator.validate(draft) as? MealDraftValidation.Valid ?: return null
        return add(validated.value, source)
    }

    internal fun add(draft: ValidatedMealDraft, source: SavedMealSource): SavedMeal? {
        if (!writable) return null
        val context = recordContext()
        if (_meals.value.any { it.id == context.id }) {
            _failure.value = MealDiaryFailure.WRITE_FAILED
            return null
        }
        val meal = SavedMeal(
            context.id,
            context.localDay,
            context.timeLabel,
            draft.name,
            draft.portionGrams,
            draft.ingredients.toList(),
            draft.note,
            source,
        )
        return if (persist(listOf(meal) + _meals.value)) meal else null
    }

    fun update(id: String, draft: MealDraft): Boolean = update(id, draft) {}

    internal fun update(id: String, draft: MealDraft, beforePublish: (List<SavedMeal>) -> Unit): Boolean {
        val existing = _meals.value.firstOrNull { it.id == id } ?: return false
        val validated = MealDraftValidator.validate(draft, existing) as? MealDraftValidation.Valid ?: return false
        return update(id, validated.value, beforePublish)
    }

    internal fun update(
        id: String,
        draft: ValidatedMealDraft,
        beforePublish: (List<SavedMeal>) -> Unit = {},
    ): Boolean {
        if (!writable || _meals.value.none { it.id == id }) return false
        return persist(
            _meals.value.map { meal ->
                if (meal.id == id) {
                    meal.copy(
                        name = draft.name,
                        portionGrams = draft.portionGrams,
                        ingredients = draft.ingredients.toList(),
                        note = draft.note,
                    )
                } else {
                    meal
                }
            },
            beforePublish,
        )
    }

    fun delete(id: String): Boolean = delete(id) {}

    internal fun delete(id: String, beforePublish: (List<SavedMeal>) -> Unit): Boolean {
        if (!writable) return false
        val remaining = _meals.value.filterNot { it.id == id }
        return remaining.size != _meals.value.size && persist(remaining, beforePublish)
    }

    private fun load(): List<SavedMeal> = try {
        val encoded = store.load()
        if (encoded == null) {
            emptyList()
        } else {
            when (val decoded = MealDiaryCodec.decode(encoded)) {
                is MealDiaryDecodeResult.Success -> decoded.meals
                is MealDiaryDecodeResult.Failure -> rejectLoad(MealDiaryFailure.CORRUPT_STORAGE)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        rejectLoad(MealDiaryFailure.LOAD_FAILED)
    }

    private fun rejectLoad(reason: MealDiaryFailure): List<SavedMeal> {
        writable = false
        _failure.value = reason
        return emptyList()
    }

    private fun persist(meals: List<SavedMeal>, beforePublish: (List<SavedMeal>) -> Unit = {}): Boolean {
        val saved = try {
            store.save(MealDiaryCodec.encode(meals))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (!saved) {
            _failure.value = MealDiaryFailure.WRITE_FAILED
            return false
        }
        beforePublish(meals)
        _meals.value = meals
        _failure.value = null
        return true
    }
}
