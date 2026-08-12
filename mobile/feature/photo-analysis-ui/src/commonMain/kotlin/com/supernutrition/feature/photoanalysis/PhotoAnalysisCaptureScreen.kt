package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import kotlinx.coroutines.flow.StateFlow

/** Native image renderers choose this from the retained photo's visual contrast. */
enum class PhotoAnalysisPhotoTextContrast {
    LIGHT_TEXT,
    DARK_TEXT,
}

data class PhotoAnalysisCaptureSurface(
    val presenterCopy: PhotoAnalysisPresenterCopy,
    val automationIds: PhotoAnalysisCaptureAutomationIds = PhotoAnalysisCaptureAutomationIds(),
    /** Native hosts supply the live camera before a retained photo exists. */
    val liveCamera: (@Composable () -> Unit)? = null,
    /** Native hosts supply this optional preview slot; shared UI never owns a photo decoder or bytes. */
    val retainedPhoto: (@Composable () -> Unit)? = null,
    /** Null retains the app theme when there is no rendered photo backdrop. */
    val photoTextContrast: PhotoAnalysisPhotoTextContrast? = null,
    /** Enabled when the app has a local model runtime capable of honoring grid prompts. */
    val gridHintsEnabled: Boolean = false,
    val onGridCellSelected: ((Int) -> Unit)? = null,
    val retainedPhotoAspectRatio: Float? = null,
)

data class PhotoAnalysisCaptureAutomationIds(
    val title: UiAutomationId = UiAutomationIds.PHOTO_ANALYSIS_TITLE,
    val status: UiAutomationId = UiAutomationIds.PHOTO_ANALYSIS_STATUS,
    val stage: UiAutomationId = UiAutomationIds.PHOTO_ANALYSIS_STAGE,
    val retainedPhoto: UiAutomationId = UiAutomationIds.PHOTO_ANALYSIS_RETAINED_PHOTO,
    val mainDishProposals: UiAutomationId = UiAutomationIds.PHOTO_ANALYSIS_MAIN_DISH_PROPOSALS,
    val componentEvidence: UiAutomationId = UiAutomationIds.PHOTO_ANALYSIS_COMPONENT_EVIDENCE,
    val compositionHints: UiAutomationId = UiAutomationIds.PHOTO_ANALYSIS_COMPOSITION_HINTS,
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
    var selectedGridCell by remember { mutableIntStateOf(-1) }
    val photoVisible = (surface.liveCamera != null || surface.retainedPhoto != null) &&
        model.state != PhotoAnalysisPresenterState.BACKGROUNDED
    val gridHintsVisible = surface.gridHintsEnabled && model.state != PhotoAnalysisPresenterState.BACKGROUNDED

    LaunchedEffect(model.state) {
        if (model.state == PhotoAnalysisPresenterState.CAPTURE) selectedGridCell = -1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PhotoAnalysisPocHeader(surface)

        if (photoVisible) {
            PhotoAnalysisPocPhotoArea(
                review = model.review,
                selectedGridCell = selectedGridCell,
                analyzing = model.state == PhotoAnalysisPresenterState.ANALYZING,
                surface = surface,
            )
        }

        PhotoAnalysisPocResearchWarning(surface)
        PhotoAnalysisPocCaptureActions(model.actions, onAction, surface)

        if (gridHintsVisible) {
            PhotoAnalysisGridHints(
                selectedGridCell = selectedGridCell,
                onGridCellSelected = { index ->
                    selectedGridCell = index
                    surface.onGridCellSelected?.invoke(index)
                },
                surface = surface,
            )
        }

        PhotoAnalysisStatus(model, surface)
        val review = model.review
        if (review == null) {
            Text(surface.presenterCopy.capture.resultPlaceholder, style = pocBodyTextStyle())
        } else {
            PhotoAnalysisReviewContent(review, surface)
        }
    }
}

@Composable
private fun PhotoAnalysisStatus(model: PhotoAnalysisPresenterModel, surface: PhotoAnalysisCaptureSurface) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .automationId(surface.automationIds.stage)
            .semantics { stateDescription = model.statusLabel },
    ) {
        Text(
            text = model.statusLabel,
            style = pocBodyTextStyle(),
            modifier = Modifier.automationId(surface.automationIds.status),
        )
    }
}

@Composable
private fun PhotoAnalysisReviewContent(review: PhotoAnalysisReviewModel, surface: PhotoAnalysisCaptureSurface) {
    PhotoAnalysisDishCandidates(review, surface)
    Text(
        surface.presenterCopy.evidence.visibleRegionCount.replace(
            "{count}",
            review.visibleComponents.count { it.region != null }.toString(),
        ),
        style = pocBodyTextStyle(),
    )
    review.degradationLabel?.let { Text(it, style = pocBodyTextStyle()) }
    PhotoAnalysisVisibleComponents(review, surface)
    PhotoAnalysisCompositionHints(review, surface)
    Text(surface.presenterCopy.capture.researchWarning, style = pocBodyTextStyle())
}

@Composable
private fun PhotoAnalysisDishCandidates(review: PhotoAnalysisReviewModel, surface: PhotoAnalysisCaptureSurface) {
    if (review.mainDishProposals.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .automationId(surface.automationIds.mainDishProposals),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = surface.presenterCopy.evidence.dishCandidatesTitle,
            style = pocBodyTextStyle(),
        )
        review.mainDishProposals.forEach { dish ->
            Text(
                text = dish.label,
                style = pocBodyTextStyle(),
                modifier = Modifier.semantics {
                    contentDescription = listOf(
                        dish.label,
                        dish.confidenceLabel,
                        dish.provenanceLabel,
                    ).joinToString()
                    stateDescription = surface.presenterCopy.evidence.notSelected
                },
            )
        }
    }
}

@Composable
private fun PhotoAnalysisVisibleComponents(review: PhotoAnalysisReviewModel, surface: PhotoAnalysisCaptureSurface) {
    if (review.visibleComponents.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .automationId(surface.automationIds.componentEvidence),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = surface.presenterCopy.evidence.visibleComponentsTitle,
            style = pocBodyTextStyle(),
        )
        review.visibleComponents.forEach { component ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .automationId(photoAnalysisVisibleComponentId(component.componentId))
                    .semantics {
                        contentDescription = listOfNotNull(
                            component.label,
                            component.sourceLabel,
                            component.confidenceLabel,
                            component.ambiguityLabel,
                            component.visibilityLabel,
                            component.regionLabel,
                            component.mass?.label,
                            component.provenanceLabel,
                        ).joinToString()
                        stateDescription = component.selectionLabel
                    },
            ) {
                Text(component.label, style = pocBodyTextStyle())
                Text(component.sourceLabel, style = pocBodyTextStyle())
                component.mass?.let { mass -> Text(mass.label, style = pocBodyTextStyle()) }
            }
        }
    }
}

@Composable
private fun PhotoAnalysisCompositionHints(review: PhotoAnalysisReviewModel, surface: PhotoAnalysisCaptureSurface) {
    if (review.compositionHints.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .automationId(surface.automationIds.compositionHints),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = surface.presenterCopy.evidence.compositionHintsTitle,
            style = pocBodyTextStyle(),
        )
        review.compositionHints
            .distinctBy { it.ingredientId.trim().lowercase() }
            .forEach { hint ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = listOf(hint.label, hint.sourceLabel).joinToString()
                            stateDescription = hint.sourceLabel
                        },
                ) {
                    Text(hint.label, style = pocBodyTextStyle())
                    Text(hint.sourceLabel, style = pocBodyTextStyle())
                }
            }
    }
}

internal fun Modifier.automationId(id: UiAutomationId): Modifier = testTag(id.value)

internal fun pocBodyTextStyle(): TextStyle = TextStyle.Default.copy(color = Color(0xFF202124))

internal fun PhotoAnalysisCaptureSurface.overlayTextStyle(): TextStyle = TextStyle.Default.merge(
    when (photoTextContrast) {
        PhotoAnalysisPhotoTextContrast.LIGHT_TEXT -> TextStyle(
            color = Color.White,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.65f),
                offset = Offset(0.5f, 0.5f),
                blurRadius = 1.5f,
            ),
        )

        PhotoAnalysisPhotoTextContrast.DARK_TEXT -> TextStyle(
            color = Color.Black,
            shadow = Shadow(
                color = Color.White.copy(alpha = 0.65f),
                offset = Offset(0.5f, 0.5f),
                blurRadius = 1.5f,
            ),
        )

        null -> TextStyle.Default
    },
)
