package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/** Only destination and scroll state are saved; meal contents and search text remain in memory. */
@Stable
class ProfileFlowState internal constructor(
    historyVisible: Boolean = false,
    private val currentDay: () -> Int = ::currentHistoryReferenceDay,
) {
    var historyVisible by mutableStateOf(historyVisible)
        private set
    internal var referenceDay by mutableStateOf(currentDay())
        private set

    internal fun showHistory() {
        referenceDay = currentDay()
        historyVisible = true
    }

    fun showProfile() {
        historyVisible = false
    }

    internal companion object {
        val StateSaver = Saver<ProfileFlowState, Boolean>(
            save = { it.historyVisible },
            restore = { ProfileFlowState(it) },
        )
    }
}
