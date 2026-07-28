package com.supernutrition.feature.photoanalysis

import com.supernutrition.core.config.AuxiliaryInteractionCounts
import com.supernutrition.core.config.ConfigurationSnapshot
import com.supernutrition.core.config.InteractionBudgetMaxima
import com.supernutrition.core.config.RequiredInteractionCounts
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoAnalysisBackgroundSnapshot
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisReducer
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.domain.photoanalysis.PhotoSessionStore

/** Pure-KMP feature state. Platform hosts only provide native bridge effects. */
class PhotoAnalysisFeature(attemptId: AttemptId, val configurationSnapshot: ConfigurationSnapshot) {
    private val reducer = PhotoAnalysisReducer(
        leases = PhotoLeaseStore(),
        sessions = PhotoSessionStore(),
        attemptId = attemptId,
    )
    private val observers = mutableListOf<() -> Unit>()

    val state: PhotoAnalysisState
        get() = reducer.state

    fun dispatch(event: PhotoAnalysisEvent) {
        val previous = reducer.state
        reducer.dispatch(event)
        if (reducer.state != previous) {
            observers.toList().forEach { it() }
        }
    }

    /** Registers a host-safe state observer and returns an idempotent disposer. */
    fun observe(observer: () -> Unit): () -> Unit {
        observers += observer
        var disposed = false
        return {
            if (!disposed) {
                disposed = true
                observers.remove(observer)
            }
        }
    }

    fun snapshotForPersistence(): PhotoAnalysisBackgroundSnapshot = reducer.snapshotForPersistence()

    fun issuePhotoRef(opaqueId: String): PhotoRef = reducer.issuePhotoRef(opaqueId)
}

data class CaptureSurfaceUi(
    val title: String = "Capture",
    val shutterLabel: String = "Take meal photo",
    val importLabel: String = "Choose photo",
    val shutterAutomationId: String = UiAutomationIds.CAPTURE_SHUTTER.value,
    val importAutomationId: String = UiAutomationIds.CAPTURE_IMPORT.value,
    val requiredTapBudget: Int = 2,
    val requiredScrollBudget: Int = 0,
    val requiredFieldBudget: Int = 0,
    val requiredChoiceBudget: Int = 0,
    val auxiliaryTextEntryCharsBudget: Int = 0,
    val auxiliaryChoiceSurfaceBudget: Int = 0,
    val auxiliaryBackBudget: Int = 1,
    val auxiliaryUndoBudget: Int = 0,
    val auxiliaryCancelBudget: Int = 1,
    val auxiliaryPermissionPromptBudget: Int = 1,
) {
    fun interactionBudgetMaxima(): InteractionBudgetMaxima = InteractionBudgetMaxima(
        required = RequiredInteractionCounts(
            tap = requiredTapBudget,
            scroll = requiredScrollBudget,
            field = requiredFieldBudget,
            choice = requiredChoiceBudget,
        ),
        auxiliary = AuxiliaryInteractionCounts(
            textEntryChars = auxiliaryTextEntryCharsBudget,
            choiceSurface = auxiliaryChoiceSurfaceBudget,
            back = auxiliaryBackBudget,
            undo = auxiliaryUndoBudget,
            cancel = auxiliaryCancelBudget,
            permissionPrompt = auxiliaryPermissionPromptBudget,
        ),
    )
}
