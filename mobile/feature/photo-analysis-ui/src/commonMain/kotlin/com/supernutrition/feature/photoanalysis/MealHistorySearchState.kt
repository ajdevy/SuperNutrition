package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Meal search text stays in the current session and is never written into saved instance state. */
@Stable
class MealHistorySearchState(initialQuery: String = "") {
    var query by mutableStateOf(initialQuery)
        private set

    fun updateQuery(value: String) {
        query = value
    }
}
