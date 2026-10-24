package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIViewController

/**
 * Shared Apple composition-root entry point; UI only renders the supplied state stream.
 *
 * PascalCase is the Compose/Apple interop convention for a factory that returns a
 * UIViewController — it mirrors `ComposeUIViewController`, and the name is part of
 * the surface Swift calls. Renaming it to satisfy the pattern would rename the
 * published entry point, so the rule is suppressed here rather than obeyed.
 */
@Suppress("FunctionNaming")
@OptIn(ExperimentalForeignApi::class)
fun PhotoAnalysisViewController(
    feature: PhotoAnalysisFeature,
    copy: PhotoAnalysisPresenterCopy,
    retainedPhoto: () -> IosRetainedPhotoPreview?,
    callbacks: PhotoAnalysisNativeCallbacks,
): UIViewController = ComposeUIViewController {
    // retainedPhoto is native state, so observe the shared state stream here
    // as well as inside the content to refresh the same-slot preview after a
    // photo is returned or native inference finishes.
    feature.stateFlow.collectAsState().value
    val manual = feature.manualIngredientStateFlow.collectAsState().value
    val mealReviewSelection = feature.mealReviewSelection.collectAsState().value
    val catalogState = feature.mealDiary.catalogSession.state.collectAsState().value
    val preview = retainedPhoto()
    PhotoAnalysisCaptureContent(
        stateFlow = feature.stateFlow,
        onAction = { action ->
            feature.route(action, callbacks.onNativeAction)
            if (action == PhotoAnalysisPresenterAction.ScanAgain) {
                callbacks.onNativeAction(PhotoAnalysisNativeAction.TakePhoto)
            }
        },
        surface = PhotoAnalysisCaptureSurface(
            catalog = MealCatalogContext(catalogState, feature.mealDiary.catalogSession::retry),
            presenterCopy = copy,
            closeLabel = "Close",
            onCloseRequested = callbacks.onCloseRequested,
            retainedPhoto = preview?.let { current -> { IosRetainedPhotoBackdrop(current) } },
            retainedPhotoAspectRatio = preview?.image?.size?.useContents {
                if (width > 0.0 && height > 0.0) (width / height).toFloat() else null
            }?.takeIf { it.isFinite() && it > 0f },
            photoTextContrast = null,
            manualIngredientInput = manual.query,
            manualIngredients = manual.ingredients.map { ingredient ->
                PhotoAnalysisManualIngredientModel(
                    canonicalItemId = ingredient.canonicalItemId,
                    label = ingredient.displayName,
                    sourceDishName = ingredient.sourceDishName,
                )
            },
            manualIngredientUnresolved = manual.unresolvedFragments,
            onManualIngredientInputChanged = feature::updateManualIngredientQuery,
            onManualIngredientAdded = feature::addManualIngredients,
            mealReviewSelection = mealReviewSelection,
            onMealReviewSelectionChanged = { feature.updateMealReviewSelection(it) },
            onMealReviewSaveRequested = { confirmation ->
                feature.saveReviewedMeal(confirmation).also { saved -> if (saved) callbacks.onMealReviewCompleted() }
            },
            onTextMealSaved = { draft ->
                feature.saveTextMeal(draft).also { saved -> if (saved) callbacks.onMealReviewCompleted() }
            },
        ),
    )
}
