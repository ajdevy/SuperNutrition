package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ConfirmedConsumedFood

/** Identity and meal evidence supplied by the current app session. */
internal data class ProfileScreenIdentity(val displayName: String, val confirmedFoods: List<ConfirmedConsumedFood>)
