package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId

@Composable
internal fun PhotoAnalysisManualIngredientInput(surface: PhotoAnalysisCaptureSurface) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = surface.manualIngredientInput,
                onValueChange = surface.onManualIngredientInputChanged,
                modifier = Modifier
                    .weight(1f)
                    .automationId(surface.automationIds.manualIngredientInput),
                label = { Text(surface.presenterCopy.capture.manualIngredientLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { surface.onManualIngredientAdded() }),
            )
            Button(
                onClick = surface.onManualIngredientAdded,
                enabled = surface.manualIngredientInput.isNotBlank(),
                modifier = Modifier
                    .height(56.dp)
                    .automationId(surface.automationIds.manualIngredientAdd),
            ) {
                Text(surface.presenterCopy.capture.manualIngredientAdd)
            }
        }
        if (surface.manualIngredientUnresolved.isNotEmpty()) {
            Text(
                text = surface.manualIngredientUnresolved.joinToString(", "),
                style = pocBodyTextStyle(),
            )
        }
    }
}

@Composable
internal fun PhotoAnalysisManualIngredients(surface: PhotoAnalysisCaptureSurface) {
    if (surface.manualIngredients.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .automationId(surface.automationIds.componentEvidence),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(surface.presenterCopy.evidence.visibleComponentsTitle, style = pocBodyTextStyle())
        PhotoAnalysisManualIngredientRows(surface)
    }
}

@Composable
internal fun PhotoAnalysisManualIngredientRows(surface: PhotoAnalysisCaptureSurface) {
    surface.manualIngredients.forEach { ingredient ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .automationId(photoAnalysisVisibleComponentId("manual:${ingredient.canonicalItemId}"))
                .semantics {
                    contentDescription = listOfNotNull(
                        ingredient.label,
                        surface.presenterCopy.evidence.manualIngredientSource,
                        ingredient.sourceDishName,
                    ).joinToString()
                },
        ) {
            Text(ingredient.label, style = pocBodyTextStyle())
            Text(
                text = ingredient.sourceDishName?.let { dish ->
                    "${surface.presenterCopy.evidence.manualIngredientSource}: $dish"
                } ?: surface.presenterCopy.evidence.manualIngredientSource,
                style = pocBodyTextStyle(),
            )
        }
    }
}
