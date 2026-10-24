package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

internal actual fun Modifier.automationRoot(): Modifier = semantics { testTagsAsResourceId = true }
