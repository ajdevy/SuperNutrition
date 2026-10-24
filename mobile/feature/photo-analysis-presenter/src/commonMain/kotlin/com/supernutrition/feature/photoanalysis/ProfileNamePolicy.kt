package com.supernutrition.feature.photoanalysis

/** Empty is the explicit deletion of an optional name. */
object ProfileNamePolicy {
    const val MAX_LENGTH = 200

    fun normalize(value: String): String? = value.trim().takeIf {
        it.length <= MAX_LENGTH && it.none(Char::isISOControl)
    }

    fun canSubmit(value: String, currentName: String): Boolean = normalize(value)?.let {
        it.isNotEmpty() || currentName.isNotEmpty()
    } == true
}
