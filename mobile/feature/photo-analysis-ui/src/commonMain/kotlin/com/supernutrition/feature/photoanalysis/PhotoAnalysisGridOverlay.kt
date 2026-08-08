package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisGridCellId

@Composable
internal fun PhotoAnalysisGridOverlay(
    review: PhotoAnalysisReviewModel?,
    selectedGridCell: Int,
    surface: PhotoAnalysisCaptureSurface,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .automationId(UiAutomationIds.PHOTO_ANALYSIS_GRID_OVERLAY)
            .semantics { contentDescription = "Photo analysis 3 by 3 grid with detected regions" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawGrid(selectedGridCell)
            review?.visibleComponents?.forEach { component ->
                component.region?.let { region -> drawRegion(region) }
            }
        }

        review?.visibleComponents?.forEach { component ->
            component.region?.let { region ->
                Text(
                    text = component.label,
                    style = surface.overlayTextStyle(),
                    modifier = Modifier
                        .padding(
                            start = maxWidth * region.left.coerceIn(0.0, 1.0).toFloat(),
                            top = maxHeight * region.top.coerceIn(0.0, 1.0).toFloat(),
                        )
                        .semantics { contentDescription = component.label },
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(selectedGridCell: Int) {
    val cellWidth = size.width / 3f
    val cellHeight = size.height / 3f
    if (selectedGridCell in 0..8) {
        val row = selectedGridCell / 3
        val column = selectedGridCell % 3
        drawRect(
            color = Color(0x4D1EB464),
            topLeft = Offset(column * cellWidth, row * cellHeight),
            size = Size(cellWidth, cellHeight),
        )
    }
    for (index in 1..2) {
        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(index * cellWidth, 0f),
            end = Offset(index * cellWidth, size.height),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(0f, index * cellHeight),
            end = Offset(size.width, index * cellHeight),
            strokeWidth = 2.dp.toPx(),
        )
    }
    drawRect(color = Color.White.copy(alpha = 0.9f), style = Stroke(width = 2.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRegion(region: PhotoAnalysisVisibleRegionModel) {
    val left = region.left.coerceIn(0.0, 1.0).toFloat() * size.width
    val top = region.top.coerceIn(0.0, 1.0).toFloat() * size.height
    val right = (region.left + region.width).coerceIn(0.0, 1.0).toFloat() * size.width
    val bottom = (region.top + region.height).coerceIn(0.0, 1.0).toFloat() * size.height
    drawRect(
        color = Color(0xFFFFC107),
        topLeft = Offset(left, top),
        size = Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)),
        style = Stroke(width = 2.dp.toPx()),
    )
}

@Composable
internal fun PhotoAnalysisGridHints(
    selectedGridCell: Int,
    onGridCellSelected: (Int) -> Unit,
    surface: PhotoAnalysisCaptureSurface,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(surface.presenterCopy.capture.gridHintsTitle, style = pocBodyTextStyle())
        repeat(3) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(3) { column ->
                    val index = row * 3 + column
                    Button(
                        onClick = { onGridCellSelected(index) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .automationId(photoAnalysisGridCellId(index))
                            .semantics {
                                contentDescription = "Select grid cell ${index + 1}"
                                stateDescription = if (selectedGridCell == index) "Selected" else "Not selected"
                            },
                        colors = if (selectedGridCell == index) {
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1EB464),
                                contentColor = Color(0xFF202124),
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE0E0E0),
                                contentColor = Color(0xFF202124),
                            )
                        },
                        shape = RectangleShape,
                    ) {
                        Text((index + 1).toString())
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhotoAnalysisLoadingOverlay(model: PhotoAnalysisPresenterModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .automationId(UiAutomationIds.PHOTO_ANALYSIS_LOADING)
            .semantics { contentDescription = model.statusLabel },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = 0.55f)) }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = Color.White)
            Text(
                text = model.statusLabel,
                style = pocBodyTextStyle().copy(color = Color.White),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
