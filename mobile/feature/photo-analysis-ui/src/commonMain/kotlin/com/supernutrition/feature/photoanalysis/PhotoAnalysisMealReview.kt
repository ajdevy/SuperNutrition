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
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.core.ui.photoAnalysisVisibleComponentId
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.refresh_choice_check
import com.supernutrition.feature.photoanalysis.resources.refresh_choice_plus
import com.supernutrition.feature.photoanalysis.resources.refresh_review_pencil
import org.jetbrains.compose.resources.painterResource

private val ReviewTopPadding = 30.94.dp
private val ReviewBottomPadding = 31.1.dp
private val ReviewNoteHeight = 45.2.dp
private val ReviewNoteGap = 4.dp
private val ReviewActionsGap = 18.6.dp
private val ReviewMinimumScrollHeight = 64.dp

@Composable
internal fun PhotoAnalysisMealReview(
    model: PhotoAnalysisPresenterModel,
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    surface: PhotoAnalysisCaptureSurface,
) {
    val choices = requireNotNull(surface.mealReviewSelection)
    val noteFocus = remember { PhotoAnalysisReviewNoteFocus() }
    val review = model.review
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = PHOTO_TEXT_SCRIM_ALPHA))
            .safeDrawingPadding().imePadding(),
    ) {
        val copy = surface.presenterCopy.review
        val actionHeight = reviewActionsHeight(copy.retake, copy.addToMeal, minOf(maxWidth, 430.dp) - 32.dp)
        val minimumPinnedHeight = actionHeight + ReviewNoteHeight + ReviewNoteGap + ReviewActionsGap +
            ReviewTopPadding + ReviewBottomPadding + ReviewMinimumScrollHeight
        val pinFooter = maxHeight >= minimumPinnedHeight
        // The compact form scrolls below the independently anchored Close touch target.
        val density = LocalDensity.current
        val closeClearancePx = if (pinFooter) {
            0
        } else {
            with(density) {
                minOf(
                    (ReviewCloseTopPadding + CameraMinimumTouchSize).roundToPx(),
                    (constraints.maxHeight - CameraMinimumTouchSize.roundToPx()).coerceAtLeast(0),
                )
            }
        }
        val closeClearance = with(density) { closeClearancePx.toDp() }
        val viewportHeight = with(density) { (constraints.maxHeight - closeClearancePx).toDp() }
        KeepFocusedReviewNoteVisible(noteFocus, viewportHeight)
        val viewportModifier = if (pinFooter) {
            Modifier.fillMaxHeight()
        } else {
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)
        }
        Column(
            Modifier.widthIn(max = 430.dp).fillMaxWidth().align(Alignment.TopCenter)
                .padding(top = closeClearance).then(viewportModifier)
                .padding(top = ReviewTopPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val bodyModifier = if (pinFooter) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()
            MealReviewScrollableContent(review, surface, bodyModifier, pinFooter)
            MealReviewFooter(review, onAction, surface, choices, noteFocus)
        }
    }
}

@Composable
private fun MealReviewScrollableContent(
    review: PhotoAnalysisReviewModel?,
    surface: PhotoAnalysisCaptureSurface,
    modifier: Modifier,
    scrollable: Boolean,
) {
    val choices = requireNotNull(surface.mealReviewSelection)
    BoxWithConstraints(modifier) {
        val contentModifier = if (scrollable) {
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
        } else {
            Modifier
        }
        Column(
            modifier = Modifier.fillMaxWidth().then(contentModifier),
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
                )
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
        style = reviewTextStyle(20f, FontWeight.SemiBold).copy(fontFamily = frauncesFont()),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 64.dp).automationId(surface.automationIds.status),
    )
    Spacer(Modifier.height(5.34.dp))
    Text(
        copy.subtitle,
        style = reviewTextStyle(13f).copy(color = Color(0xFFD2D4D2)),
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
    Spacer(Modifier.height(18.dp))
    DishChoices(review, surface, choices, onChoicesChanged)
}

@Composable
private fun MealReviewFooter(
    review: PhotoAnalysisReviewModel?,
    onAction: (PhotoAnalysisPresenterAction) -> Unit,
    surface: PhotoAnalysisCaptureSurface,
    choices: MealReviewChoices,
    noteFocus: PhotoAnalysisReviewNoteFocus,
) {
    val copy = surface.presenterCopy.review
    Column(
        Modifier.fillMaxWidth().bringIntoViewRequester(noteFocus.footerRequester)
            .onSizeChanged { noteFocus.footerHeightPx = it.height }.padding(bottom = ReviewBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(ReviewNoteGap))
        ReviewNoteInput(surface, noteFocus)
        Spacer(Modifier.height(ReviewActionsGap))
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
    val dishes = review?.mainDishProposals.orEmpty()
        .filter { it.confidenceBand != ConfidenceBand.LOW || it.candidateId == choices.dishId }
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).automationId(surface.automationIds.mainDishProposals),
    ) {
        if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.2f) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                dishes.forEach { dish ->
                    DishProposalChoice(dish, choices, Modifier.fillMaxWidth(), onChoicesChanged)
                }
                CustomDishChoice(surface, choices, onChoicesChanged)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                dishes.forEachIndexed { index, dish ->
                    DishProposalChoice(
                        dish,
                        choices,
                        Modifier.weight(listOf(100.2f, 85f, 76.9f).getOrElse(index) { 85f }).fillMaxHeight(),
                        onChoicesChanged,
                    )
                }
                CustomDishChoice(surface, choices, onChoicesChanged, Modifier.fillMaxHeight())
            }
        }
    }
}

@Composable
private fun DishProposalChoice(
    dish: PhotoAnalysisDishProposalModel,
    choices: MealReviewChoices,
    modifier: Modifier,
    onChoicesChanged: (MealReviewChoices) -> Unit,
) {
    DishChoice(
        content = DishChoiceContent(
            label = dish.label.replace('_', ' '),
            description = listOf(dish.label, dish.confidenceLabel, dish.provenanceLabel).joinToString(),
        ),
        isSelected = choices.dishId == dish.candidateId,
        modifier = modifier.automationId(UiAutomationIds.photoAnalysisDishChoice(dish.rank)),
        onClick = { onChoicesChanged(choices.copy(dishId = dish.candidateId, customMeal = false)) },
    )
}

@Composable
private fun CustomDishChoice(
    surface: PhotoAnalysisCaptureSurface,
    choices: MealReviewChoices,
    onChoicesChanged: (MealReviewChoices) -> Unit,
    modifier: Modifier = Modifier,
) {
    DishChoice(
        content = DishChoiceContent(
            surface.presenterCopy.review.customMeal,
            surface.presenterCopy.review.customMeal,
            custom = true,
        ),
        isSelected = choices.customMeal,
        modifier = modifier.width(62.8.dp).automationId(UiAutomationIds.PHOTO_ANALYSIS_CUSTOM_MEAL),
        onClick = { onChoicesChanged(choices.copy(dishId = null, customMeal = true)) },
    )
}

@Composable
private fun DishChoice(content: DishChoiceContent, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.heightIn(min = 67.4.dp).clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) ReviewGreen else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
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
            Image(painterResource(Res.drawable.refresh_choice_plus), null, Modifier.size(20.dp))
            Text(content.label, style = reviewTextStyle(12.5f, FontWeight.SemiBold), textAlign = TextAlign.Center)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) Image(painterResource(Res.drawable.refresh_choice_check), null, Modifier.size(17.dp))
                Text(
                    content.label,
                    modifier = Modifier.weight(1f),
                    style = reviewTextStyle(12.5f, FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                )
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
private fun ReviewNoteInput(surface: PhotoAnalysisCaptureSurface, noteFocus: PhotoAnalysisReviewNoteFocus) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp)
            .automationId(surface.automationIds.manualIngredientAdd)
            .clickable(
                onClickLabel = surface.presenterCopy.capture.manualIngredientAdd,
            ) { noteFocus.focusRequester.requestFocus() }
            .bringIntoViewRequester(noteFocus.noteRequester)
            .clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(999.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(painterResource(Res.drawable.refresh_review_pencil), null, Modifier.size(20.dp))
        BasicTextField(
            value = surface.manualIngredientInput,
            onValueChange = surface.onManualIngredientInputChanged,
            modifier = Modifier.weight(1f).focusRequester(noteFocus.focusRequester)
                .onFocusChanged { noteFocus.focused = it.isFocused }
                .automationId(surface.automationIds.manualIngredientInput)
                .semantics { contentDescription = surface.presenterCopy.review.anythingElse },
            singleLine = true,
            textStyle = reviewTextStyle(15f),
            cursorBrush = SolidColor(ReviewWhite),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            decorationBox = { inner ->
                Box {
                    if (surface.manualIngredientInput.isEmpty()) {
                        Text(
                            surface.presenterCopy.review.anythingElse,
                            style = reviewTextStyle(15f).copy(color = Color(0xFFBCB8B3)),
                        )
                    }
                    inner()
                }
            },
        )
    }
}
