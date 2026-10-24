package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cheap app-root port; opening and verification happen only on the supplied background dispatcher. */
fun interface MealCatalogFactory {
    fun open(): MealCatalogHandle?
}

/** The app root wraps its adapter session here and retains disposal authority. */
interface MealCatalogHandle {
    val catalog: ApprovedNutritionCatalog

    /** Implementations release their source idempotently. */
    fun close()
}

/**
 * One host lifetime. Call start, retry, source queries and close on the owning main context.
 * Constructors are cheap and isolated; a missing factory explicitly keeps the source unavailable.
 */
class MealCatalogSession(private val factory: MealCatalogFactory? = null) {
    private val mutableState = MutableStateFlow<MealCatalogState>(MealCatalogState.Unavailable)
    val state: StateFlow<MealCatalogState> = mutableState.asStateFlow()
    val catalog: ApprovedNutritionCatalog? get() = state.value.catalogOrNull
    private var execution: CatalogExecution? = null
    private var opening: Job? = null
    private var handle: MealCatalogHandle? = null
    private var closed = false

    fun start(ownerScope: CoroutineScope, backgroundDispatcher: CoroutineDispatcher) {
        if (closed || execution != null) return
        execution = CatalogExecution(ownerScope, backgroundDispatcher)
        retry()
    }

    fun retry() {
        if (closed || opening?.isActive == true) return
        val current = execution ?: return
        val create = factory ?: return
        if (!current.scope.isActive) return
        mutableState.value = MealCatalogState.Loading
        opening = current.scope.launch {
            var candidate: MealCatalogHandle? = null
            var published = false
            try {
                // Capture inside withContext: prompt cancellation can discard its return value.
                withContext(current.background) { candidate = create.open() }
                val opened = candidate
                if (!closed && opened != null) {
                    val ready = MealCatalogState.Ready(opened.catalog)
                    val previous = handle
                    handle = opened
                    candidate = null
                    published = true
                    mutableState.value = ready
                    closeCandidate(previous)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = MealCatalogState.Unavailable
            } finally {
                finishOpening(candidate, published)
            }
        }
    }

    private fun finishOpening(candidate: MealCatalogHandle?, published: Boolean) {
        closeCandidate(candidate)
        if (!published) mutableState.value = MealCatalogState.Unavailable
        opening = null
    }

    fun close() {
        if (closed) return
        closed = true
        opening?.cancel()
        val previous = handle
        handle = null
        mutableState.value = MealCatalogState.Unavailable
        closeCandidate(previous)
    }
}

private class CatalogExecution(val scope: CoroutineScope, val background: CoroutineDispatcher)

private fun closeCandidate(candidate: MealCatalogHandle?) {
    try {
        candidate?.close()
    } catch (_: Exception) {
        // Cleanup must not replace cancellation or resurrect a disposed source.
    }
}
