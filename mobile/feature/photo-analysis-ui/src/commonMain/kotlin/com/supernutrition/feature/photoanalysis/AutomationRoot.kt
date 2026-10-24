package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.Modifier

/** Native windows need their own automation scope; Activity semantics do not cross window boundaries. */
internal expect fun Modifier.automationRoot(): Modifier
