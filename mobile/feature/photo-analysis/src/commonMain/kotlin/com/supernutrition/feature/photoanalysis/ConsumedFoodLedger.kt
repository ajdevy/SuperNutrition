package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Storage boundary for confirmed foods; adapters can persist the immutable entries. */
interface ConsumedFoodLedgerStore {
    fun load(): List<ConfirmedConsumedFood>

    fun save(entries: List<ConfirmedConsumedFood>)
}

/** Deterministic store used by the current in-memory host and by tests. */
class InMemoryConsumedFoodLedgerStore(initialEntries: List<ConfirmedConsumedFood> = emptyList()) :
    ConsumedFoodLedgerStore {
    private var entries: List<ConfirmedConsumedFood> = initialEntries.distinctBy(ConfirmedConsumedFood::entryId)

    override fun load(): List<ConfirmedConsumedFood> = entries

    override fun save(entries: List<ConfirmedConsumedFood>) {
        this.entries = entries.distinctBy(ConfirmedConsumedFood::entryId)
    }
}

/**
 * Confirmed-food day ledger used as the Rewards source of truth.
 *
 * Raw photo candidates and identity-only manual suggestions cannot enter this
 * API because every write requires [ConfirmedConsumedFood].
 */
class ConsumedFoodLedger(
    initialEntries: List<ConfirmedConsumedFood> = emptyList(),
    private val store: ConsumedFoodLedgerStore = InMemoryConsumedFoodLedgerStore(),
    private val onChanged: (List<ConfirmedConsumedFood>) -> Unit = {},
) {
    private val _entries = MutableStateFlow(loadInitialEntries(initialEntries))

    val entries: StateFlow<List<ConfirmedConsumedFood>> = _entries.asStateFlow()

    fun recordConfirmedFood(food: ConfirmedConsumedFood) {
        publish(_entries.value.filterNot { it.entryId == food.entryId } + food)
    }

    fun removeConfirmedFood(entryId: String): Boolean {
        val next = _entries.value.filterNot { it.entryId == entryId }
        if (next.size == _entries.value.size) return false
        publish(next)
        return true
    }

    fun replaceConfirmedFoods(entries: List<ConfirmedConsumedFood>) {
        publish(entries)
    }

    fun entriesForDay(localDay: Int): List<ConfirmedConsumedFood> = _entries.value.filter { it.localDay == localDay }

    private fun publish(entries: List<ConfirmedConsumedFood>) {
        val normalized = entries.distinctBy(ConfirmedConsumedFood::entryId)
        store.save(normalized)
        _entries.value = normalized
        onChanged(normalized)
    }

    private fun loadInitialEntries(initialEntries: List<ConfirmedConsumedFood>): List<ConfirmedConsumedFood> {
        val storedEntries = store.load()
        val loaded = if (storedEntries.isEmpty()) {
            initialEntries
        } else {
            storedEntries.distinctBy(ConfirmedConsumedFood::entryId)
        }
        if (storedEntries.isEmpty() && loaded.isNotEmpty()) store.save(loaded)
        return loaded
    }
}
