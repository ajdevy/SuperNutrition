package com.supernutrition.feature.photoanalysis

/** Optional success-aware host contract; legacy session owners can continue using onNameChanged. */
data class ProfileNamePersistence(val save: (String) -> Boolean, val loadFailed: Boolean = false)

internal fun ProfileScreenActions.saveName(value: String): Boolean {
    val persistence = namePersistence
    if (persistence != null) return persistence.save(value)
    onNameChanged(value)
    return true
}
