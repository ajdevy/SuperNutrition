package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileEditStateTest {
    @Test
    fun `saved profile state preserves editing mode and remains cancellable`() {
        val state = ProfileFlowState().apply { beginNameEdit() }
        val saved = with(ProfileFlowState.StateSaver) {
            SaverScope { true }.save(state)
        }
        val restored = assertNotNull(ProfileFlowState.StateSaver.restore(assertNotNull(saved)))
        assertTrue(restored.nameEditing)
        assertFalse(restored.historyVisible)
        restored.cancelNameEdit()
        assertFalse(restored.nameEditing)
        assertFalse(restored.historyVisible)
    }

    @Test
    fun `legacy saved destinations restore without opening an editor`() {
        val history = assertNotNull(ProfileFlowState.StateSaver.restore(true))
        assertTrue(history.historyVisible)
        assertFalse(history.nameEditing)
        val profile = assertNotNull(ProfileFlowState.StateSaver.restore(false))
        assertFalse(profile.historyVisible)
        assertFalse(profile.nameEditing)
        val hiddenEditor = assertNotNull(ProfileFlowState.StateSaver.restore(listOf(true, true)))
        assertTrue(hiddenEditor.historyVisible)
        assertFalse(hiddenEditor.nameEditing)
    }

    @Test
    fun `malformed saved destinations are rejected`() {
        listOf("profile", emptyList<Boolean>(), listOf(true), listOf(true, "edit")).forEach { saved ->
            assertNull(ProfileFlowState.StateSaver.restore(saved))
        }
    }

    @Test
    fun `destination changes close editing and history cannot start a hidden editor`() {
        val state = ProfileFlowState().apply { beginNameEdit() }
        state.showHistory()
        assertTrue(state.historyVisible)
        assertFalse(state.nameEditing)
        state.beginNameEdit()
        assertFalse(state.nameEditing)
        state.showProfile()
        assertFalse(state.historyVisible)
        state.beginNameEdit()
        assertTrue(state.nameEditing)
        state.showProfile()
        assertFalse(state.nameEditing)
    }
}
