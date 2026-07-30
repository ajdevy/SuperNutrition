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
import kotlinx.coroutines.flow.StateFlow
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState

data class PhotoAnalysisCaptureSurface(
    val copy: PhotoAnalysisPresenterCopy,
    val title: String = copy.capture.title,
    val shutterLabel: String = copy.capture.takeMealPhoto,
    val importLabel: String = copy.capture.choosePhoto,
    val openSettingsLabel: String = copy.capture.openSettings,
    val scanAgainLabel: String = copy.recovery.scanAgain,
    val cancelLabel: String = copy.capture.cancel,
    val titleAutomationId: String = UiAutomationIds.PHOTO_ANALYSIS_TITLE.value,
    val statusAutomationId: String = UiAutomationIds.PHOTO_ANALYSIS_STATUS.value,
    val shutterAutomationId: String = UiAutomationIds.CAPTURE_SHUTTER.value,
    val importAutomationId: String = UiAutomationIds.CAPTURE_IMPORT.value,
    val openSettingsAutomationId: String = UiAutomationIds.CAPTURE_OPEN_SETTINGS.value,
    val scanAgainAutomationId: String = UiAutomationIds.CAPTURE_SCAN_AGAIN.value,
    val cancelAutomationId: String = UiAutomationIds.CAPTURE_CANCEL.value,
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
    stateFlow: StateFlow<PhotoAnalysisState>,
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    surface: PhotoAnalysisCaptureSurface,
    modifier: Modifier = Modifier,
) {
    val state by stateFlow.collectAsState()
    PhotoAnalysisCaptureScreen(
        model = PhotoAnalysisPresenterModel.from(state, surface.copy),
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
    surface: PhotoAnalysisCaptureSurface,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = surface.title,
            modifier = Modifier.automationId(surface.titleAutomationId),
        )
        Text(
            text = model.statusLabel,
            modifier = Modifier.automationId(surface.statusAutomationId),
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
                    modifier = Modifier.automationId(surface.openSettingsAutomationId),
                ) {
                    Text(surface.openSettingsLabel)
                }

                PhotoAnalysisPresenterAction.ScanAgain -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(surface.scanAgainAutomationId),
                ) {
                    Text(surface.scanAgainLabel)
                }

                PhotoAnalysisPresenterAction.Cancel -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(surface.cancelAutomationId),
                ) {
                    Text(surface.cancelLabel)
                }
            }
        }
    }
}

private fun Modifier.automationId(id: String): Modifier = testTag(id)
