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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.review_pencil
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun PhotoAnalysisMealReview(
    model: PhotoAnalysisPresenterModel,
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    surface: PhotoAnalysisCaptureSurface,
) {
    val choices = requireNotNull(surface.mealReviewSelection)
    val noteFocus = remember { FocusRequester() }
    val review = model.review
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = PHOTO_TEXT_SCRIM_ALPHA))
            .safeDrawingPadding().imePadding(),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 430.dp).fillMaxWidth().align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(top = 30.94.dp, bottom = 31.1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MealReviewHeader(review, surface, choices, surface.onMealReviewSelectionChanged)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.BottomCenter) {
                    ExtraReviewEvidence(surface)
                }
                PhotoAnalysisFoodPalette(
                    review = review,
                    surface = surface,
                    choices = choices,
                    onChoicesChanged = surface.onMealReviewSelectionChanged,
                    onAddRequested = { noteFocus.requestFocus() },
                )
                MealReviewFooter(review, onAction, surface, choices, noteFocus)
            }
        }
    }
}

@Composable
private fun MealReviewHeader(
    review: PhotoAnalysisReviewModel?,
    surface: PhotoAnalysisCaptureSurface,
    choices: MealReviewChoices,
    onChoicesChanged: (MealReviewChoices) -> Unit,
) {
    val copy = surface.presenterCopy.review
    Text(
        copy.title,
        style = reviewTextStyle(18.22f, FontWeight.SemiBold),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 64.dp).automationId(surface.automationIds.status),
    )
    Spacer(Modifier.height(5.34.dp))
    Text(
        copy.subtitle,
        style = reviewTextStyle(11.7f).copy(color = Color(0xFFD2D4D2)),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    review?.degradationLabel?.let { label ->
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = reviewTextStyle(12f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    Spacer(Modifier.height(34.12.dp))
    DishChoices(review, surface, choices, onChoicesChanged)
}

@Composable
private fun MealReviewFooter(
    review: PhotoAnalysisReviewModel?,
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    surface: PhotoAnalysisCaptureSurface,
    choices: MealReviewChoices,
    noteFocus: FocusRequester,
) {
    val copy = surface.presenterCopy.review
    Spacer(Modifier.height(4.dp))
    ReviewNoteInput(surface, noteFocus)
    Spacer(Modifier.height(18.6.dp))
    val hasContent = listOf(
        choices.componentIds.isNotEmpty(),
        choices.pantryIds.isNotEmpty(),
        surface.manualIngredients.isNotEmpty(),
        surface.manualIngredientInput.isNotBlank(),
    ).any { it }
    ReviewActions(
        retake = ReviewButtonSpec(copy.retake, surface.automationIds.scanAgain),
        add = ReviewButtonSpec(
            label = copy.addToMeal,
            id = UiAutomationIds.PHOTO_ANALYSIS_ADD_TO_MEAL,
            filled = true,
            enabled = choices.dishId != null || hasContent,
        ),
        onRetake = { onAction(PhotoAnalysisPresenterAction.ScanAgain) },
        onAdd = { surface.onMealReviewConfirmed(confirmationForChoices(review, choices, surface)) },
    )
}

private fun confirmationForChoices(
    review: PhotoAnalysisReviewModel?,
    choices: MealReviewChoices,
    surface: PhotoAnalysisCaptureSurface,
) = PhotoAnalysisMealReviewConfirmation(
    dishCandidateId = choices.dishId,
    dishLabel = review?.mainDishProposals?.firstOrNull { it.candidateId == choices.dishId }
        ?.label ?: surface.presenterCopy.review.customMeal,
    photoEvidenceComponentIds = review?.visibleComponents.orEmpty()
        .filter { it.componentId in choices.componentIds }.map { it.componentId },
    addedIngredientLabels = FoodStickers.filter { it.id in choices.pantryIds }
        .map(surface.presenterCopy.review::pantryLabel) +
        surface.manualIngredients.map { it.label },
    note = surface.manualIngredientInput.trim(),
)

@Composable
private fun DishChoices(
    review: PhotoAnalysisReviewModel?,
    surface: PhotoAnalysisCaptureSurface,
    choices: MealReviewChoices,
    onChoicesChanged: (MealReviewChoices) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(IntrinsicSize.Min)
            .automationId(surface.automationIds.mainDishProposals),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        review?.mainDishProposals.orEmpty()
            .filter { it.confidenceBand != ConfidenceBand.LOW || it.candidateId == choices.dishId }
            .forEachIndexed { index, dish ->
                val isSelected = choices.dishId == dish.candidateId
                DishChoice(
                    content = DishChoiceContent(
                        label = dish.label.replace('_', ' '),
                        description = listOf(dish.label, dish.confidenceLabel, dish.provenanceLabel).joinToString(),
                    ),
                    isSelected = isSelected,
                    modifier = Modifier.weight(listOf(100.2f, 85f, 76.9f).getOrElse(index) { 85f })
                        .automationId(UiAutomationIds.photoAnalysisDishChoice(dish.rank)),
                    onClick = { onChoicesChanged(choices.copy(dishId = dish.candidateId, customMeal = false)) },
                )
            }
        DishChoice(
            content = DishChoiceContent(
                surface.presenterCopy.review.customMeal,
                surface.presenterCopy.review.customMeal,
                custom = true,
            ),
            isSelected = choices.customMeal,
            modifier = Modifier.width(62.8.dp).automationId(UiAutomationIds.PHOTO_ANALYSIS_CUSTOM_MEAL),
            onClick = { onChoicesChanged(choices.copy(dishId = null, customMeal = true)) },
        )
    }
}

@Composable
private fun DishChoice(content: DishChoiceContent, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxHeight().heightIn(min = 67.4.dp).clip(RoundedCornerShape(11.5.dp))
            .background(if (isSelected) Color(0xFF6B7C18) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(11.5.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = content.description
                selected = isSelected
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (content.custom) {
            Text("+", style = reviewTextStyle(24f), textAlign = TextAlign.Center)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) Text("✓ ", style = reviewTextStyle(13f))
                Text(content.label, style = reviewTextStyle(13f), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ExtraReviewEvidence(surface: PhotoAnalysisCaptureSurface) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        surface.manualIngredientUnresolved.takeIf { it.isNotEmpty() }?.let {
            Text(it.joinToString(", "), style = reviewTextStyle(12f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            surface.manualIngredients.forEach { ingredient ->
                Text(
                    ingredient.label,
                    style = reviewTextStyle(13f),
                    modifier = Modifier.automationId(
                        photoAnalysisVisibleComponentId("manual:${ingredient.canonicalItemId}"),
                    )
                        .semantics {
                            contentDescription =
                                "${ingredient.label}, ${surface.presenterCopy.evidence.manualIngredientSource}"
                        }
                        .padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewNoteInput(surface: PhotoAnalysisCaptureSurface, focusRequester: FocusRequester) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(45.2.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(painterResource(Res.drawable.review_pencil), null, Modifier.size(20.dp))
        BasicTextField(
            value = surface.manualIngredientInput,
            onValueChange = surface.onManualIngredientInputChanged,
            modifier = Modifier.weight(1f).focusRequester(focusRequester)
                .automationId(surface.automationIds.manualIngredientInput)
                .semantics { contentDescription = surface.presenterCopy.review.anythingElse },
            singleLine = true,
            textStyle = reviewTextStyle(15.5f),
            cursorBrush = SolidColor(ReviewWhite),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            decorationBox = { inner ->
                Box {
                    if (surface.manualIngredientInput.isEmpty()) {
                        Text(
                            surface.presenterCopy.review.anythingElse,
                            style = reviewTextStyle(15.5f).copy(color = Color(0xFF95928F)),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

internal fun reviewTextStyle(size: Float, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = ReviewWhite,
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    fontWeight = weight,
)
