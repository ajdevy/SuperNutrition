package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.refresh_review_check
import org.jetbrains.compose.resources.painterResource

/** Only the active photo's immutable evidence is offered; manual additions have their own explicit control. */
@Composable
internal fun PhotoAnalysisFoodPalette(
    review: PhotoAnalysisReviewModel?,
    surface: PhotoAnalysisCaptureSurface,
    choices: MealReviewChoices,
    onChoicesChanged: (MealReviewChoices) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).automationId(surface.automationIds.componentEvidence),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.2f) 2 else 3
            val spacing = 8.dp
            val choiceWidth = ((maxWidth - spacing * (columns - 1)) / columns).coerceAtLeast(48.dp)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(spacing),
                maxItemsInEachRow = columns,
            ) {
                review?.visibleComponents.orEmpty().forEach { component ->
                    key(component.componentId) {
                        PhotoFoodChoice(component, choiceWidth, surface, choices, onChoicesChanged)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoFoodChoice(
    component: PhotoAnalysisVisibleComponentModel,
    width: Dp,
    surface: PhotoAnalysisCaptureSurface,
    choices: MealReviewChoices,
    onChoicesChanged: (MealReviewChoices) -> Unit,
) {
    val isSelected = component.componentId in choices.componentIds
    val sticker = foodStickerFor(component.label)
    val shape = RoundedCornerShape(12.dp)
    val background = when {
        sticker != null -> Color.Transparent
        isSelected -> ReviewGreen
        else -> Color.Black.copy(alpha = .3f)
    }
    val border = if (sticker == null) Color.White.copy(alpha = .45f) else Color.Transparent
    Column(
        Modifier.width(width).heightIn(min = 48.dp).automationId(photoAnalysisVisibleComponentId(component.componentId))
            .background(background, shape).border(1.dp, border, shape)
            .clickable(role = Role.Checkbox) { onChoicesChanged(choices.toggleComponent(component.componentId)) }
            .semantics {
                contentDescription = component.reviewDescription()
                selected = isSelected
                stateDescription = if (isSelected) {
                    surface.presenterCopy.evidence.selected
                } else {
                    surface.presenterCopy.evidence.notSelected
                }
            }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        if (sticker != null) {
            FoodStickerArtwork(
                sticker,
                isSelected,
                (width - 12.dp).coerceAtMost(116.dp),
                surface.presenterCopy.evidence.selected,
            )
        }
        if (sticker == null) {
            Text(
                component.label.replace('_', ' '),
                style = reviewTextStyle(13f),
                textAlign = TextAlign.Center,
            )
        }
        if (sticker == null && isSelected) {
            Image(
                painterResource(Res.drawable.refresh_review_check),
                surface.presenterCopy.evidence.selected,
                Modifier.size(22.5.dp),
            )
        }
    }
}

@Composable
private fun FoodStickerArtwork(sticker: FoodSticker, isSelected: Boolean, size: Dp, selectedLabel: String) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (isSelected) {
            Image(
                painterResource(sticker.artwork),
                null,
                Modifier.size(size)
                    .blur(7.dp, BlurredEdgeTreatment.Unbounded),
                colorFilter = ColorFilter.tint(Color(0xA67AB462)),
            )
            Image(
                painterResource(sticker.artwork),
                null,
                Modifier.size(size)
                    .blur(3.5.dp, BlurredEdgeTreatment.Unbounded),
                colorFilter = ColorFilter.tint(Color(0xFF7AB462)),
            )
        }
        Image(
            painterResource(sticker.artwork),
            null,
            Modifier.size(size),
        )
        if (isSelected) {
            Image(
                painterResource(Res.drawable.refresh_review_check),
                selectedLabel,
                Modifier.align(Alignment.TopEnd).size(22.5.dp),
            )
        }
    }
}
