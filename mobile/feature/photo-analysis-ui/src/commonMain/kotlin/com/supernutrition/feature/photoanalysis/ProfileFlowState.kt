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
    var historyVisible by mutableStateOf(historyVisible)
        private set
    var nameEditing by mutableStateOf(nameEditing && !historyVisible)
        private set
    internal var referenceDay by mutableStateOf(currentDay())
        private set

    internal fun showHistory() {
        cancelNameEdit()
        referenceDay = currentDay()
        historyVisible = true
    }

    fun showProfile() {
        cancelNameEdit()
        historyVisible = false
    }

    fun beginNameEdit() {
        if (!historyVisible) nameEditing = true
    }

    fun cancelNameEdit() {
        nameEditing = false
    }

    internal companion object {
        val StateSaver = Saver<ProfileFlowState, Any>(
            save = { listOf(it.historyVisible, it.nameEditing) },
            restore = { saved ->
                when {
                    saved is Boolean -> ProfileFlowState(saved)

                    saved is List<*> && saved.size == 2 && saved.all { it is Boolean } ->
                        ProfileFlowState(saved[0] as Boolean, saved[1] as Boolean)

                    else -> null
                }
            },
        )
    }
}
