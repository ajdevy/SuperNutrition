package com.supernutrition.feature.photoanalysis

import com.supernutrition.core.config.InMemoryPrivateDocumentStore
import com.supernutrition.core.config.PrivateDocumentStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LocalProfileFailure { LOAD_FAILED, CORRUPT_STORAGE, WRITE_FAILED }

/** Publishes a bounded optional identity only after storage accepts the replacement. */
class LocalProfile(private val store: PrivateDocumentStore = InMemoryPrivateDocumentStore()) {
    private var writable = true
    private val _failure = MutableStateFlow<LocalProfileFailure?>(null)
    private val _displayName = MutableStateFlow(load())
    val displayName = _displayName.asStateFlow()
    val failure = _failure.asStateFlow()
    val loadFailed: Boolean get() = !writable

    fun updateDisplayName(value: String): Boolean {
        val name = ProfileNamePolicy.normalize(value) ?: return false
        if (!writable) return false
        val saved = try {
            store.save(PREFIX + name)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (!saved) {
            _failure.value = LocalProfileFailure.WRITE_FAILED
            return false
        }
        _displayName.value = name
        _failure.value = null
        return true
    }

    private fun load(): String = try {
        val encoded = store.load()
        if (encoded == null) {
            ""
        } else {
            val name = encoded.removePrefix(PREFIX)
            if (!encoded.startsWith(PREFIX) || ProfileNamePolicy.normalize(name) != name) {
                rejectLoad(LocalProfileFailure.CORRUPT_STORAGE)
            } else {
                name
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        rejectLoad(LocalProfileFailure.LOAD_FAILED)
    }

    private fun rejectLoad(reason: LocalProfileFailure): String {
        writable = false
        _failure.value = reason
        return ""
    }

    private companion object {
        const val PREFIX = "SNPN1:"
    }
}
