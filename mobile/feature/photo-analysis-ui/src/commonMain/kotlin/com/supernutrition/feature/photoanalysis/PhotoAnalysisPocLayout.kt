package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PhotoAnalysisPocHeader(surface: PhotoAnalysisCaptureSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = surface.presenterCopy.capture.title,
            style = TextStyle(color = Color(0xFF202124), fontSize = 22.sp),
            modifier = Modifier
                .weight(1f)
                .automationId(surface.automationIds.title),
        )
        Text(
            text = "×",
            style = TextStyle(color = Color(0xFF202124), fontSize = 32.sp),
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = surface.onCloseRequested)
                .semantics {
                    contentDescription = surface.closeLabel
                    role = Role.Button
                }
                .automationId(surface.automationIds.close),
        )
    }
}

@Composable
internal fun PhotoAnalysisPocResearchWarning(surface: PhotoAnalysisCaptureSurface) {
    Text(
        text = surface.presenterCopy.capture.researchWarning,
        style = TextStyle(color = Color(0xFF8A3B12), fontSize = 14.sp),
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
internal fun PhotoAnalysisPocCaptureActions(
    actions: List<PhotoAnalysisPresenterAction>,
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    surface: PhotoAnalysisCaptureSurface,
) {
    val captureActions = actions.filter {
        it == PhotoAnalysisPresenterAction.TakePhoto ||
            it == PhotoAnalysisPresenterAction.ScanAgain ||
            it == PhotoAnalysisPresenterAction.ImportPhoto
    }
    if (captureActions.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            captureActions.forEach { action ->
                PocButton(
                    action = action,
                    onAction = onAction,
                    surface = surface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    actions.filterNot { it in captureActions }.forEach { action ->
        PocButton(action, onAction, surface, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PocButton(
    action: PhotoAnalysisPresenterAction,
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    surface: PhotoAnalysisCaptureSurface,
    modifier: Modifier,
) {
    val (label, automationId) = when (action) {
        PhotoAnalysisPresenterAction.TakePhoto ->
            surface.presenterCopy.capture.takeMealPhoto to surface.automationIds.shutter

        PhotoAnalysisPresenterAction.ImportPhoto ->
            surface.presenterCopy.capture.choosePhoto to surface.automationIds.import

        PhotoAnalysisPresenterAction.OpenSettings ->
            surface.presenterCopy.capture.openSettings to surface.automationIds.openSettings

        PhotoAnalysisPresenterAction.ScanAgain ->
            surface.presenterCopy.recovery.scanAgain to surface.automationIds.scanAgain

        PhotoAnalysisPresenterAction.Cancel ->
            surface.presenterCopy.capture.cancel to surface.automationIds.cancel

        is PhotoAnalysisPresenterAction.SelectGridCell -> return
    }
    Button(
        onClick = { onAction(action) },
        modifier = modifier.height(48.dp).automationId(automationId),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE0E0E0),
            contentColor = Color(0xFF202124),
        ),
    ) {
        Text(label)
    }
}

@Composable
internal fun PhotoAnalysisPocPhotoArea(
    review: PhotoAnalysisReviewModel?,
    selectedGridCell: Int,
    analyzing: Boolean,
    surface: PhotoAnalysisCaptureSurface,
) {
    val photoModifier = Modifier
        .fillMaxWidth()
        // The camera and retained photo deliberately share one geometry so the
        // action buttons below do not move when a capture result arrives.
        .aspectRatio(surface.retainedPhotoAspectRatio ?: DEFAULT_PHOTO_SLOT_ASPECT_RATIO)
        .background(Color(0xFF14181C))
        .automationId(surface.automationIds.retainedPhoto)

    Box(modifier = photoModifier) {
        if (surface.retainedPhoto != null) {
            surface.retainedPhoto.invoke()
        } else {
            surface.liveCamera?.invoke()
        }
        if (surface.retainedPhoto != null && surface.gridHintsEnabled) {
            PhotoAnalysisGridOverlay(review, selectedGridCell, surface)
        }
        if (surface.retainedPhoto != null && analyzing) {
            PhotoAnalysisLoadingOverlay(
                model = PhotoAnalysisPresenterModel(
                    statusLabel = surface.presenterCopy.capture.loadingLabel,
                    state = PhotoAnalysisPresenterState.ANALYZING,
                    actions = emptyList(),
                    review = review,
                ),
            )
        }
    }
}

private const val DEFAULT_PHOTO_SLOT_ASPECT_RATIO = 4f / 3f
