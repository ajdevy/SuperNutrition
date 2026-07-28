package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds

/** Shared production Compose presenter surface used by Android and iOS hosts. */
@Composable
fun PhotoAnalysisCaptureScreen(
    model: PhotoAnalysisPresenterModel,
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    modifier: Modifier = Modifier,
    bootstrapLaunchMarker: Boolean = false,
) {
    val markerModifier = if (bootstrapLaunchMarker) {
        Modifier.automationId(UiAutomationIds.BOOTSTRAP_LAUNCH_DI.value)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .then(markerModifier)
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = model.title,
            modifier = Modifier.automationId(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value),
        )
        Text(
            text = model.statusLabel,
            modifier = Modifier.automationId(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value),
        )

        model.actions.forEach { action ->
            when (action) {
                PhotoAnalysisPresenterAction.TakePhoto -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(model.shutterAutomationId),
                ) {
                    Text(model.shutterLabel)
                }

                PhotoAnalysisPresenterAction.ImportPhoto -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(model.importAutomationId),
                ) {
                    Text(model.importLabel)
                }

                PhotoAnalysisPresenterAction.OpenSettings -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(UiAutomationIds.CAPTURE_OPEN_SETTINGS.value),
                ) {
                    Text("Open settings")
                }

                PhotoAnalysisPresenterAction.ScanAgain -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(UiAutomationIds.CAPTURE_SCAN_AGAIN.value),
                ) {
                    Text("Scan again")
                }

                PhotoAnalysisPresenterAction.Cancel -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(UiAutomationIds.CAPTURE_CANCEL.value),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

private fun Modifier.automationId(id: String): Modifier = testTag(id).semantics { contentDescription = id }
