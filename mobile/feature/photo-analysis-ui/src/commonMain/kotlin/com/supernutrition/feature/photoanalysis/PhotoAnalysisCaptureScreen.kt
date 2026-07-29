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
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

data class PhotoAnalysisCaptureSurface(
    val title: String = "Capture",
    val shutterLabel: String = "Take meal photo",
    val importLabel: String = "Choose photo",
    val shutterAutomationId: String = UiAutomationIds.CAPTURE_SHUTTER.value,
    val importAutomationId: String = UiAutomationIds.CAPTURE_IMPORT.value,
    val requiredTapBudget: Int = 2,
    val requiredScrollBudget: Int = 0,
    val requiredFieldBudget: Int = 0,
    val requiredChoiceBudget: Int = 0,
    val auxiliaryTextEntryCharsBudget: Int = 0,
    val auxiliaryChoiceSurfaceBudget: Int = 0,
    val auxiliaryBackBudget: Int = 1,
    val auxiliaryUndoBudget: Int = 0,
    val auxiliaryCancelBudget: Int = 1,
    val auxiliaryPermissionPromptBudget: Int = 1,
)

@Composable
fun PhotoAnalysisCaptureContent(
    feature: PhotoAnalysisFeature,
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    modifier: Modifier = Modifier,
    surface: PhotoAnalysisCaptureSurface = PhotoAnalysisCaptureSurface(),
) {
    val state by feature.stateFlow.collectAsState()
    PhotoAnalysisCaptureScreen(
        model = PhotoAnalysisPresenterModel.from(state),
        onAction = onAction,
        modifier = modifier,
        surface = surface,
    )
}

/** Shared production Compose presenter surface used by Android and iOS hosts. */
@Composable
fun PhotoAnalysisCaptureScreen(
    model: PhotoAnalysisPresenterModel,
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    modifier: Modifier = Modifier,
    surface: PhotoAnalysisCaptureSurface = PhotoAnalysisCaptureSurface(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = surface.title,
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
                    modifier = Modifier.automationId(surface.shutterAutomationId),
                ) {
                    Text(surface.shutterLabel)
                }

                PhotoAnalysisPresenterAction.ImportPhoto -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(surface.importAutomationId),
                ) {
                    Text(surface.importLabel)
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

private fun Modifier.automationId(id: String): Modifier = testTag(id)
