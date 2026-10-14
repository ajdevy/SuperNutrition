package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.capture_close
import com.supernutrition.feature.photoanalysis.resources.capture_gallery
import com.supernutrition.feature.photoanalysis.resources.capture_shutter
import org.jetbrains.compose.resources.painterResource

internal val CameraBackground = Color(0xFF141310)
internal val ReviewGreen = Color(0xFF677712)
internal val ReviewWhite = Color(0xFFF9F9F9)
internal const val PHOTO_TEXT_SCRIM_ALPHA = 0.65f

/** Figma 196:235 and 196:246. Native hosts retain ownership of camera/photo pixels. */
@Composable
internal fun PhotoAnalysisCameraScreen(
    model: PhotoAnalysisPresenterModel,
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    surface: PhotoAnalysisCaptureSurface,
    modifier: Modifier,
) {
    val reviewSurface = surface.withRetainedMealReviewChoices(model.state)
    val retained = surface.retainedPhoto != null && model.state != PhotoAnalysisPresenterState.BACKGROUNDED &&
        model.state != PhotoAnalysisPresenterState.CAPTURE
    val reviewing = model.state == PhotoAnalysisPresenterState.CANDIDATE_REVIEW ||
        (retained && model.state == PhotoAnalysisPresenterState.READY)
    Box(
        modifier = modifier.fillMaxSize().background(CameraBackground).clipToBounds()
            .automationId(surface.automationIds.title)
            .semantics { contentDescription = surface.presenterCopy.capture.title },
    ) {
        Box(
            modifier = Modifier.fillMaxSize().automationId(surface.automationIds.stage)
                .semantics { stateDescription = model.statusLabel },
        ) {
            // The stage owns both layers so it cannot occlude native access-action semantics.
            Box(
                modifier = Modifier.fillMaxSize().automationId(surface.automationIds.retainedPhoto)
                    .graphicsLayer {
                        if (reviewing) {
                            scaleX = 1.2f
                            scaleY = 1.2f
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                    },
            ) {
                if (retained) {
                    surface.retainedPhoto.invoke()
                } else if (model.state != PhotoAnalysisPresenterState.BACKGROUNDED) {
                    surface.liveCamera?.invoke()
                }
            }
            when {
                reviewing -> PhotoAnalysisMealReview(model, onAction, reviewSurface)
                model.state == PhotoAnalysisPresenterState.CAPTURE -> CameraControls(onAction, surface)
                else -> CameraProgressAndRecovery(model, onAction, surface)
            }
        }
        CameraArtworkButton(
            artwork = CameraArtwork(Res.drawable.capture_close, 41.4.dp),
            label = surface.closeLabel,
            id = surface.automationIds.close,
            modifier = Modifier.safeDrawingPadding().padding(start = 16.dp, top = if (reviewing) 6.dp else 34.2.dp),
            onClick = surface.onCloseRequested,
        )
    }
}

@Composable
private fun CameraControls(onAction: (PhotoAnalysisPresenterAction) -> Unit, surface: PhotoAnalysisCaptureSurface) {
    BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding()) {
        val bottomPadding = if (maxHeight < 360.dp) 24.dp else 68.3.dp
        CameraCaptureActions(onAction, surface, Modifier.fillMaxSize().padding(bottom = bottomPadding))
    }
}

@Composable
private fun CameraCaptureActions(
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    surface: PhotoAnalysisCaptureSurface,
    modifier: Modifier,
) {
    Box(modifier) {
        CameraArtworkButton(
            artwork = CameraArtwork(Res.drawable.capture_shutter, 88.2.dp, 109.dp),
            label = surface.presenterCopy.capture.takeMealPhoto,
            id = surface.automationIds.shutter,
            modifier = Modifier.align(Alignment.BottomCenter),
            onClick = { onAction(PhotoAnalysisPresenterAction.TakePhoto) },
        )
        CameraArtworkButton(
            artwork = CameraArtwork(Res.drawable.capture_gallery, 51.3.dp),
            label = surface.presenterCopy.capture.choosePhoto,
            id = surface.automationIds.import,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = 16.15.dp, y = (-16.65).dp),
            onClick = { onAction(PhotoAnalysisPresenterAction.ImportPhoto) },
        )
    }
}

@Composable
internal fun CameraArtworkButton(
    artwork: CameraArtwork,
    label: String,
    id: UiAutomationId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val touchSize = maxOf(48.dp, artwork.size)
    Box(
        modifier = modifier.size(artwork.size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.requiredSize(touchSize).automationId(id)
                .clickable(role = Role.Button, onClick = onClick)
                .clearAndSetSemantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(artwork.resource), null, Modifier.requiredSize(artwork.artworkSize))
        }
    }
}

@Composable
private fun CameraProgressAndRecovery(
    model: PhotoAnalysisPresenterModel,
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    surface: PhotoAnalysisCaptureSurface,
) {
    val busy = model.state == PhotoAnalysisPresenterState.PREPARING ||
        model.state == PhotoAnalysisPresenterState.ANALYZING
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = PHOTO_TEXT_SCRIM_ALPHA)).safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = ReviewWhite,
                    modifier = Modifier.size(36.dp).automationId(UiAutomationIds.PHOTO_ANALYSIS_LOADING),
                )
            }
            Text(model.statusLabel, color = ReviewWhite, modifier = Modifier.automationId(surface.automationIds.status))
            model.actions.forEach { action ->
                val (label, id) = when (action) {
                    PhotoAnalysisPresenterAction.ScanAgain ->
                        surface.presenterCopy.review.retake to
                            surface.automationIds.scanAgain

                    PhotoAnalysisPresenterAction.ImportPhoto ->
                        surface.presenterCopy.capture.choosePhoto to
                            surface.automationIds.import

                    PhotoAnalysisPresenterAction.Cancel ->
                        surface.presenterCopy.capture.cancel to
                            surface.automationIds.cancel

                    PhotoAnalysisPresenterAction.OpenSettings ->
                        surface.presenterCopy.capture.openSettings to
                            surface.automationIds.openSettings

                    PhotoAnalysisPresenterAction.TakePhoto ->
                        surface.presenterCopy.capture.takeMealPhoto to
                            surface.automationIds.shutter

                    is PhotoAnalysisPresenterAction.SelectGridCell -> return@forEach
                }
                Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.fillMaxWidth().automationId(id),
                ) { Text(label) }
            }
        }
    }
}
