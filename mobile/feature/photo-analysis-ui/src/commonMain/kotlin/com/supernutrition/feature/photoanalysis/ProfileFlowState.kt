package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/** Destination and editing mode are saved; identity, meals and draft text remain session-owned. */
@Stable
class ProfileFlowState internal constructor(
    historyVisible: Boolean = false,
    nameEditing: Boolean = false,
    private val currentDay: () -> Int = ::currentHistoryReferenceDay,
) {
    internal val historySearch = MealHistorySearchState()
    var historyVisible by mutableStateOf(historyVisible)
        private set
    var nameEditing by mutableStateOf(nameEditing && !historyVisible)
        private set
    var selectedMealId by mutableStateOf<MealHistoryId?>(null)
        private set
    var mealEditing by mutableStateOf(false)
        private set
    val canGoBack: Boolean
        get() = nameEditing || historyVisible
    internal var referenceDay by mutableStateOf(currentDay())
        private set

    internal fun showHistory() {
        cancelNameEdit()
        selectedMealId = null
        mealEditing = false
        referenceDay = currentDay()
        historyVisible = true
    }

    fun showProfile() {
        cancelNameEdit()
        selectedMealId = null
        mealEditing = false
        historyVisible = false
    }

    internal fun showMeal(id: MealHistoryId) {
        historyVisible = true
        selectedMealId = id
        mealEditing = false
    }

    internal fun beginMealEdit() {
        if (selectedMealId != null) mealEditing = true
    }

    internal fun cancelMealEdit() {
        mealEditing = false
    }

    /** Returns true only when a shared destination or editor consumed Back. */
    fun back(): Boolean = when {
        mealEditing -> {
            cancelMealEdit()
            true
        }

        selectedMealId != null -> {
            selectedMealId = null
            true
        }

        historyVisible -> {
            showProfile()
            true
        }

        nameEditing -> {
            cancelNameEdit()
            true
        }

        else -> false
    }

    fun beginNameEdit() {
        if (!historyVisible) nameEditing = true
    }

    fun cancelNameEdit() {
        nameEditing = false
    }

    internal companion object {
        val StateSaver = Saver<ProfileFlowState, Any>(
            save = {
                listOf(
                    it.historyVisible,
                    it.nameEditing,
                    it.selectedMealId?.localDay ?: -1,
                    it.selectedMealId?.mealId.orEmpty(),
                    it.mealEditing,
                )
            },
            restore = { saved ->
                when {
                    saved is Boolean -> ProfileFlowState(saved)

                    saved is List<*> && saved.size == 2 && saved.all { it is Boolean } ->
                        ProfileFlowState(saved[0] as Boolean, saved[1] as Boolean)

                    saved is List<*> && saved.size == 5 -> restoreMealDestination(saved)

                    else -> null
                }
            },
        )

        private fun restoreMealDestination(saved: List<*>): ProfileFlowState? {
            val history = saved[0] as? Boolean ?: return null
            val name = saved[1] as? Boolean ?: return null
            val day = saved[2] as? Int ?: return null
            val meal = saved[3] as? String ?: return null
            val editing = saved[4] as? Boolean ?: return null
            val state = ProfileFlowState(history, name)
            if (day == -1 && meal.isEmpty() && !editing) return state
            if (!history || day < 0 || meal.isBlank()) return null
            state.showMeal(MealHistoryId(day, meal))
            if (editing) state.beginMealEdit()
            return state
        }
    }
}
