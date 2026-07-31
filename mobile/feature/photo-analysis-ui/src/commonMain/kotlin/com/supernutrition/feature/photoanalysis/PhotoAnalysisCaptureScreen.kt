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
import com.supernutrition.core.ui.UiAutomationId
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState

data class PhotoAnalysisCaptureSurface(
    val presenterCopy: PhotoAnalysisPresenterCopy,
    val automationIds: PhotoAnalysisCaptureAutomationIds = PhotoAnalysisCaptureAutomationIds(),
)

data class PhotoAnalysisCaptureAutomationIds(
    val title: UiAutomationId = UiAutomationIds.PHOTO_ANALYSIS_TITLE,
    val status: UiAutomationId = UiAutomationIds.PHOTO_ANALYSIS_STATUS,
    val shutter: UiAutomationId = UiAutomationIds.CAPTURE_SHUTTER,
    val import: UiAutomationId = UiAutomationIds.CAPTURE_IMPORT,
    val openSettings: UiAutomationId = UiAutomationIds.CAPTURE_OPEN_SETTINGS,
    val scanAgain: UiAutomationId = UiAutomationIds.CAPTURE_SCAN_AGAIN,
    val cancel: UiAutomationId = UiAutomationIds.CAPTURE_CANCEL,
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
        model = PhotoAnalysisPresenterModel.from(state, surface.presenterCopy),
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
            text = surface.presenterCopy.capture.title,
            modifier = Modifier.automationId(surface.automationIds.title),
        )
        Text(
            text = model.statusLabel,
            modifier = Modifier.automationId(surface.automationIds.status),
        )

        model.actions.forEach { action ->
            when (action) {
                PhotoAnalysisPresenterAction.TakePhoto -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(surface.automationIds.shutter),
                ) {
                    Text(surface.presenterCopy.capture.takeMealPhoto)
                }

                PhotoAnalysisPresenterAction.ImportPhoto -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(surface.automationIds.import),
                ) {
                    Text(surface.presenterCopy.capture.choosePhoto)
                }

                PhotoAnalysisPresenterAction.OpenSettings -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(surface.automationIds.openSettings),
                ) {
                    Text(surface.presenterCopy.capture.openSettings)
                }

                PhotoAnalysisPresenterAction.ScanAgain -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(surface.automationIds.scanAgain),
                ) {
                    Text(surface.presenterCopy.recovery.scanAgain)
                }

                PhotoAnalysisPresenterAction.Cancel -> Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.automationId(surface.automationIds.cancel),
                ) {
                    Text(surface.presenterCopy.capture.cancel)
                }
            }
        }
    }
}

private fun Modifier.automationId(id: UiAutomationId): Modifier = testTag(id.value)
