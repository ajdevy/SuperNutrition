package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.MassEvidence
import com.supernutrition.domain.photoanalysis.CandidateReviewDraft
import com.supernutrition.domain.photoanalysis.DishIngredientHint
import com.supernutrition.domain.photoanalysis.LocalAnalysisStage
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.RecoveryReason

/** Stable state vocabulary exposed to platform presenters. */
enum class PhotoAnalysisPresenterState {
    CAPTURE,
    PREPARING,
    READY,
    ANALYZING,
    CANDIDATE_REVIEW,
    RECOVERY,
    BACKGROUNDED,
}

/** UI intents stay explicit; the presenter never invents remote consent. */
sealed interface PhotoAnalysisPresenterAction {
    data object TakePhoto : PhotoAnalysisPresenterAction
    data object ImportPhoto : PhotoAnalysisPresenterAction
    data object OpenSettings : PhotoAnalysisPresenterAction
    data object Cancel : PhotoAnalysisPresenterAction
    data object ScanAgain : PhotoAnalysisPresenterAction

    /** Explicit row-major 3x3 research prompt; valid indices are 0 through 8. */
    data class SelectGridCell(val index: Int) : PhotoAnalysisPresenterAction {
        init {
            require(index in 0..8) { "Grid cell index must be between 0 and 8" }
        }
    }

    companion object {
        /** Stable list of ordinary capture actions; dynamic grid prompts are intentionally omitted. */
        val entries: List<PhotoAnalysisPresenterAction> = listOf(
            TakePhoto,
            ImportPhoto,
            OpenSettings,
            Cancel,
            ScanAgain,
        )
    }
}

/** Localizable copy is supplied by the product host; the presenter owns no locale lookup. */
data class PhotoAnalysisPresenterCopy(
    val capture: CaptureCopy,
    val status: StatusCopy,
    val evidence: EvidenceCopy,
    val recovery: RecoveryCopy,
) {
    init {
        (capture.values + status.values + evidence.values + recovery.values).forEach { require(it.isNotBlank()) }
    }
}

data class CaptureCopy(
    val title: String,
    val takeMealPhoto: String,
    val choosePhoto: String,
    val openSettings: String,
    val cancel: String,
    val researchWarning: String =
        "Research PoC / ingredient labels limited to model vocabulary / grams unavailable",
    val gridHintsTitle: String = "Grid hints",
    val resultPlaceholder: String = "Top-3 dish candidates and model regions will appear after analysis.",
    val loadingLabel: String = "Analysis…",
) {
    val values: List<String>
        get() = listOf(
            title,
            takeMealPhoto,
            choosePhoto,
            openSettings,
            cancel,
            researchWarning,
            gridHintsTitle,
            resultPlaceholder,
            loadingLabel,
        )
}

data class StatusCopy(
    val readyToCapture: String,
    val preparingPhoto: String,
    val photoReady: String,
    val analyzingPhoto: String,
    val findingVisibleFoods: String,
    val identifyingDish: String,
    val estimatingPortions: String,
    val checkingResults: String,
    val reviewCandidate: String,
    val restoringPhotoAnalysis: String,
) {
    val values: List<String>
        get() = listOf(
            readyToCapture,
            preparingPhoto,
            photoReady,
            analyzingPhoto,
            findingVisibleFoods,
            identifyingDish,
            estimatingPortions,
            checkingResults,
            reviewCandidate,
            restoringPhotoAnalysis,
        )
}

/** Localizable labels for factual photo evidence; no catalog or health claim enters this boundary. */
data class EvidenceCopy(
    val fromThisPhoto: String,
    val highConfidence: String,
    val mediumConfidence: String,
    val lowConfidence: String,
    val identityClear: String,
    val identityAmbiguous: String,
    val mixedContent: String,
    val fullyVisible: String,
    val partiallyOccluded: String,
    val regionAvailable: String,
    val regionUnavailable: String,
    val estimatedMass: String,
    val massNotEvaluable: String,
    val massAbstained: String,
    val localProvenance: String,
    val remoteProvenance: String,
    val selected: String,
    val notSelected: String,
    val dishCandidatesTitle: String = "Dish candidates",
    val visibleComponentsTitle: String = "Detected in photo",
    val compositionHintsTitle: String = "Possible ingredients from dish",
    val compositionHintSource: String = "Dish hint, not visual evidence",
) {
    init {
        require(estimatedMass.contains("{lower}") && estimatedMass.contains("{upper}")) {
            "Estimated mass copy must include lower and upper placeholders"
        }
    }

    val values: List<String>
        get() = listOf(
            fromThisPhoto,
            highConfidence,
            mediumConfidence,
            lowConfidence,
            identityClear,
            identityAmbiguous,
            mixedContent,
            fullyVisible,
            partiallyOccluded,
            regionAvailable,
            regionUnavailable,
            estimatedMass,
            massNotEvaluable,
            massAbstained,
            localProvenance,
            remoteProvenance,
            selected,
            notSelected,
            dishCandidatesTitle,
            visibleComponentsTitle,
            compositionHintsTitle,
            compositionHintSource,
        )
}

data class RecoveryCopy(
    val scanAgain: String,
    val cameraPermissionRequired: String,
    val cameraUnavailable: String,
    val photoCaptureFailed: String,
    val photoQualityTooLow: String,
    val noFoodFound: String,
    val analysisOffline: String,
    val analysisCancelled: String,
) {
    val values: List<String>
        get() = listOf(
            scanAgain,
            cameraPermissionRequired,
            cameraUnavailable,
            photoCaptureFailed,
            photoQualityTooLow,
            noFoodFound,
            analysisOffline,
            analysisCancelled,
        )
}

data class PhotoAnalysisPresenterModel(
    val statusLabel: String,
    val state: PhotoAnalysisPresenterState,
    val actions: List<PhotoAnalysisPresenterAction>,
    val review: PhotoAnalysisReviewModel? = null,
) {
    companion object {
        fun from(state: PhotoAnalysisState, copy: PhotoAnalysisPresenterCopy): PhotoAnalysisPresenterModel {
            val presenterState = when (state) {
                PhotoAnalysisState.Capture -> PhotoAnalysisPresenterState.CAPTURE
                is PhotoAnalysisState.Preparing -> PhotoAnalysisPresenterState.PREPARING
                is PhotoAnalysisState.Ready -> PhotoAnalysisPresenterState.READY
                is PhotoAnalysisState.Analyzing -> PhotoAnalysisPresenterState.ANALYZING
                is PhotoAnalysisState.CandidateReview -> PhotoAnalysisPresenterState.CANDIDATE_REVIEW
                is PhotoAnalysisState.Recovery -> PhotoAnalysisPresenterState.RECOVERY
                is PhotoAnalysisState.Backgrounded -> PhotoAnalysisPresenterState.BACKGROUNDED
            }
            return PhotoAnalysisPresenterModel(
                statusLabel = statusLabel(state, copy),
                state = presenterState,
                actions = actions(state),
                review = (state as? PhotoAnalysisState.CandidateReview)?.draft?.toPresentation(copy),
            )
        }

        private fun statusLabel(state: PhotoAnalysisState, copy: PhotoAnalysisPresenterCopy): String = when (state) {
            PhotoAnalysisState.Capture -> copy.status.readyToCapture
            is PhotoAnalysisState.Preparing -> copy.status.preparingPhoto
            is PhotoAnalysisState.Ready -> copy.status.photoReady
            is PhotoAnalysisState.Analyzing -> stageLabel(state.stage, copy)
            is PhotoAnalysisState.CandidateReview -> copy.status.reviewCandidate
            is PhotoAnalysisState.Recovery -> recoveryLabel(state.reason, copy)
            is PhotoAnalysisState.Backgrounded -> copy.status.restoringPhotoAnalysis
        }

        private fun stageLabel(stage: LocalAnalysisStage, copy: PhotoAnalysisPresenterCopy): String = when (stage) {
            LocalAnalysisStage.FINDING_VISIBLE_FOODS -> copy.status.findingVisibleFoods
            LocalAnalysisStage.IDENTIFYING_DISH -> copy.status.identifyingDish
            LocalAnalysisStage.ESTIMATING_PORTIONS -> copy.status.estimatingPortions
            LocalAnalysisStage.CHECKING_RESULTS -> copy.status.checkingResults
        }

        private fun recoveryLabel(reason: RecoveryReason, copy: PhotoAnalysisPresenterCopy): String = when (reason) {
            RecoveryReason.PERMISSION_DENIED -> copy.recovery.cameraPermissionRequired
            RecoveryReason.CAMERA_UNAVAILABLE -> copy.recovery.cameraUnavailable
            RecoveryReason.CAPTURE_FAILED -> copy.recovery.photoCaptureFailed
            RecoveryReason.POOR_IMAGE -> copy.recovery.photoQualityTooLow
            RecoveryReason.NO_FOOD -> copy.recovery.noFoodFound
            RecoveryReason.OFFLINE -> copy.recovery.analysisOffline
            RecoveryReason.CANCELLED -> copy.recovery.analysisCancelled
        }

        private fun actions(state: PhotoAnalysisState): List<PhotoAnalysisPresenterAction> = when (state) {
            PhotoAnalysisState.Capture -> listOf(
                PhotoAnalysisPresenterAction.TakePhoto,
                PhotoAnalysisPresenterAction.ImportPhoto,
            )

            is PhotoAnalysisState.Recovery -> when (state.reason) {
                RecoveryReason.PERMISSION_DENIED -> listOf(
                    PhotoAnalysisPresenterAction.OpenSettings,
                    PhotoAnalysisPresenterAction.ImportPhoto,
                )

                else -> listOf(PhotoAnalysisPresenterAction.ScanAgain, PhotoAnalysisPresenterAction.ImportPhoto)
            }

            is PhotoAnalysisState.Backgrounded -> emptyList()

            is PhotoAnalysisState.Preparing,
            is PhotoAnalysisState.Ready,
            is PhotoAnalysisState.Analyzing,
            is PhotoAnalysisState.CandidateReview,
            -> replacementActions()
        }

        private fun replacementActions(): List<PhotoAnalysisPresenterAction> = listOf(
            PhotoAnalysisPresenterAction.ScanAgain,
            PhotoAnalysisPresenterAction.ImportPhoto,
            PhotoAnalysisPresenterAction.Cancel,
        )
    }
}

/** Immutable UI projection of the v1 dish proposal and v2 photo-evidence boundary. */
data class PhotoAnalysisReviewModel(
    val mainDishProposals: List<PhotoAnalysisDishProposalModel>,
    val visibleComponents: List<PhotoAnalysisVisibleComponentModel>,
    val compositionHints: List<PhotoAnalysisDishIngredientHintModel> = emptyList(),
)

data class PhotoAnalysisDishProposalModel(
    val candidateId: String,
    val label: String,
    val rank: Int,
    val confidenceLabel: String,
    val provenanceLabel: String,
)

data class PhotoAnalysisVisibleComponentModel(
    val componentId: String,
    val label: String,
    val sourceLabel: String,
    val confidenceLabel: String,
    val ambiguityLabel: String,
    val visibilityLabel: String,
    val region: PhotoAnalysisVisibleRegionModel?,
    val regionLabel: String,
    val mass: PhotoAnalysisMassModel,
    val provenanceLabel: String,
    /** E3.9 renders only immutable evidence; E5.1 owns a user-controlled selection. */
    val isSelected: Boolean = false,
    val selectionLabel: String,
)

data class PhotoAnalysisDishIngredientHintModel(
    val ingredientId: String,
    val label: String,
    val sourceLabel: String,
    val sourceDishCandidateIds: List<String>,
)

data class PhotoAnalysisVisibleRegionModel(val left: Double, val top: Double, val width: Double, val height: Double)

enum class PhotoAnalysisMassStatus {
    ESTIMATED,
    NOT_EVALUABLE,
    ABSTAINED,
}

data class PhotoAnalysisMassModel(val status: PhotoAnalysisMassStatus, val label: String)

private fun CandidateReviewDraft.toPresentation(copy: PhotoAnalysisPresenterCopy): PhotoAnalysisReviewModel {
    val visibleComponents = photoEvidence
        .distinctBy { ingredientDisplayKey(it.candidate.label) }
        .map { component ->
            PhotoAnalysisVisibleComponentModel(
                componentId = component.componentId,
                label = component.candidate.label,
                sourceLabel = copy.evidence.fromThisPhoto,
                confidenceLabel = confidenceLabel(component.confidence.band, copy.evidence),
                ambiguityLabel = ambiguityLabel(component.ambiguity, copy.evidence),
                visibilityLabel = visibilityLabel(component.visibility, copy.evidence),
                region = component.region?.let {
                    PhotoAnalysisVisibleRegionModel(it.left, it.top, it.width, it.height)
                },
                regionLabel = if (component.region == null) {
                    copy.evidence.regionUnavailable
                } else {
                    copy.evidence.regionAvailable
                },
                mass = massModel(component.mass, copy.evidence),
                provenanceLabel = provenanceLabel(component.provenance.route, copy.evidence),
                selectionLabel = copy.evidence.notSelected,
            )
        }
    val visibleIngredientKeys = visibleComponents.map { ingredientDisplayKey(it.label) }.toSet()

    return PhotoAnalysisReviewModel(
        mainDishProposals = mainDishProposals.map { candidate ->
            PhotoAnalysisDishProposalModel(
                candidateId = candidate.candidateId,
                label = candidate.label,
                rank = candidate.rank,
                confidenceLabel = confidenceLabel(candidate.confidence.band, copy.evidence),
                provenanceLabel = provenanceLabel(candidate.provenance.route, copy.evidence),
            )
        },
        visibleComponents = visibleComponents,
        compositionHints = compositionHints
            .asSequence()
            .filterNot { ingredientDisplayKey(it.label) in visibleIngredientKeys }
            .distinctBy { ingredientDisplayKey(it.label) }
            .map { hint -> hint.toPresentation(copy.evidence) }
            .toList(),
    )
}

private fun ingredientDisplayKey(label: String): String = label.trim().lowercase()

private fun DishIngredientHint.toPresentation(copy: EvidenceCopy): PhotoAnalysisDishIngredientHintModel =
    PhotoAnalysisDishIngredientHintModel(
        ingredientId = ingredientId,
        label = label,
        sourceLabel = copy.compositionHintSource,
        sourceDishCandidateIds = sourceDishCandidateIds,
    )

private fun confidenceLabel(band: ConfidenceBand, copy: EvidenceCopy): String = when (band) {
    ConfidenceBand.HIGH -> copy.highConfidence
    ConfidenceBand.MEDIUM -> copy.mediumConfidence
    ConfidenceBand.LOW -> copy.lowConfidence
}

private fun ambiguityLabel(ambiguity: ComponentAmbiguity, copy: EvidenceCopy): String = when (ambiguity) {
    ComponentAmbiguity.NONE -> copy.identityClear
    ComponentAmbiguity.IDENTITY_AMBIGUOUS -> copy.identityAmbiguous
    ComponentAmbiguity.MIXED_CONTENT -> copy.mixedContent
}

private fun visibilityLabel(visibility: ComponentVisibility, copy: EvidenceCopy): String = when (visibility) {
    ComponentVisibility.VISIBLE -> copy.fullyVisible
    ComponentVisibility.PARTIALLY_OCCLUDED -> copy.partiallyOccluded
}

private fun massModel(mass: MassEvidence, copy: EvidenceCopy): PhotoAnalysisMassModel = when (mass) {
    is MassEvidence.Estimated -> PhotoAnalysisMassModel(
        status = PhotoAnalysisMassStatus.ESTIMATED,
        label = copy.estimatedMass
            .replace("{lower}", mass.range.lowerGrams.asDisplayValue())
            .replace("{upper}", mass.range.upperGrams.asDisplayValue()),
    )

    is MassEvidence.NotEvaluable -> PhotoAnalysisMassModel(
        status = PhotoAnalysisMassStatus.NOT_EVALUABLE,
        label = copy.massNotEvaluable,
    )

    is MassEvidence.Abstained -> PhotoAnalysisMassModel(
        status = PhotoAnalysisMassStatus.ABSTAINED,
        label = copy.massAbstained,
    )
}

private fun provenanceLabel(route: InferenceRoute, copy: EvidenceCopy): String = when (route) {
    InferenceRoute.LOCAL -> copy.localProvenance
    InferenceRoute.REMOTE -> copy.remoteProvenance
}

private fun Double.asDisplayValue(): String = toString().removeSuffix(".0")
