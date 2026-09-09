package com.supernutrition.sharedkit

import com.supernutrition.domain.nutrition.RewardId
import com.supernutrition.feature.photoanalysis.CaptureCopy
import com.supernutrition.feature.photoanalysis.EvidenceCopy
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterCopy
import com.supernutrition.feature.photoanalysis.RecoveryCopy
import com.supernutrition.feature.photoanalysis.RewardsCardCopy
import com.supernutrition.feature.photoanalysis.RewardsPresenterCopy
import com.supernutrition.feature.photoanalysis.StatusCopy
import platform.Foundation.NSBundle

/** Resolves product-owned copy from the host application bundle for every Apple target. */
internal fun localizedPhotoAnalysisCopy(): PhotoAnalysisPresenterCopy = PhotoAnalysisPresenterCopy(
    capture = CaptureCopy(
        title = localized("photo_analysis.capture.title"),
        takeMealPhoto = localized("photo_analysis.capture.take_photo"),
        choosePhoto = localized("photo_analysis.capture.choose_photo"),
        openSettings = localized("photo_analysis.capture.open_settings"),
        cancel = localized("photo_analysis.capture.cancel"),
        researchWarning = localized("photo_analysis.capture.research_warning"),
        gridHintsTitle = localized("photo_analysis.capture.grid_hints_title"),
        gridOverlayDescription = localized("photo_analysis.capture.grid_overlay_description"),
        gridCellDescription = localized("photo_analysis.capture.grid_cell_description"),
        selectedState = localized("photo_analysis.capture.selected"),
        notSelectedState = localized("photo_analysis.capture.not_selected"),
        manualIngredientLabel = localized("photo_analysis.capture.manual_ingredient_label"),
        manualIngredientAdd = localized("photo_analysis.capture.manual_ingredient_add"),
        resultPlaceholder = localized("photo_analysis.capture.result_placeholder"),
        loadingLabel = localized("photo_analysis.capture.loading"),
    ),
    status = StatusCopy(
        readyToCapture = localized("photo_analysis.status.ready_to_capture"),
        preparingPhoto = localized("photo_analysis.status.preparing_photo"),
        photoReady = localized("photo_analysis.status.photo_ready"),
        analyzingPhoto = localized("photo_analysis.status.analyzing_photo"),
        findingVisibleFoods = localized("photo_analysis.status.finding_visible_foods"),
        identifyingDish = localized("photo_analysis.status.identifying_dish"),
        estimatingPortions = localized("photo_analysis.status.estimating_portions"),
        checkingResults = localized("photo_analysis.status.checking_results"),
        reviewCandidate = localized("photo_analysis.status.review_candidate"),
        restoringPhotoAnalysis = localized("photo_analysis.status.restoring_photo_analysis"),
    ),
    evidence = EvidenceCopy(
        fromThisPhoto = localized("photo_analysis.evidence.from_photo"),
        highConfidence = localized("photo_analysis.evidence.high_confidence"),
        mediumConfidence = localized("photo_analysis.evidence.medium_confidence"),
        lowConfidence = localized("photo_analysis.evidence.low_confidence"),
        identityClear = localized("photo_analysis.evidence.identity_clear"),
        identityAmbiguous = localized("photo_analysis.evidence.identity_ambiguous"),
        mixedContent = localized("photo_analysis.evidence.mixed_content"),
        fullyVisible = localized("photo_analysis.evidence.fully_visible"),
        partiallyOccluded = localized("photo_analysis.evidence.partially_occluded"),
        regionAvailable = localized("photo_analysis.evidence.region_available"),
        regionUnavailable = localized("photo_analysis.evidence.region_unavailable"),
        estimatedMass = localized("photo_analysis.evidence.estimated_mass"),
        massNotEvaluable = localized("photo_analysis.evidence.mass_not_evaluable"),
        massAbstained = localized("photo_analysis.evidence.mass_abstained"),
        localProvenance = localized("photo_analysis.evidence.local_provenance"),
        remoteProvenance = localized("photo_analysis.evidence.remote_provenance"),
        selected = localized("photo_analysis.evidence.selected"),
        notSelected = localized("photo_analysis.evidence.not_selected"),
        dishCandidatesTitle = localized("photo_analysis.evidence.dish_candidates"),
        visibleComponentsTitle = localized("photo_analysis.evidence.visible_components"),
        visibleRegionCount = localized("photo_analysis.evidence.visible_region_count"),
        compositionHintsTitle = localized("photo_analysis.evidence.composition_hints"),
        compositionHintSource = localized("photo_analysis.evidence.composition_hint_source"),
        manualIngredientSource = localized("photo_analysis.evidence.manual_ingredient_source"),
        partialDegradation = localized("photo_analysis.evidence.partial_degradation"),
    ),
    recovery = RecoveryCopy(
        scanAgain = localized("photo_analysis.recovery.scan_again"),
        cameraPermissionRequired = localized("photo_analysis.recovery.permission_required"),
        cameraUnavailable = localized("photo_analysis.recovery.camera_unavailable"),
        photoCaptureFailed = localized("photo_analysis.recovery.capture_failed"),
        photoQualityTooLow = localized("photo_analysis.recovery.quality_too_low"),
        noFoodFound = localized("photo_analysis.recovery.no_food_found"),
        analysisFailed = localized("photo_analysis.recovery.analysis_failed"),
        analysisOffline = localized("photo_analysis.recovery.offline"),
        analysisCancelled = localized("photo_analysis.recovery.cancelled"),
        reviewRestoreUnavailable = localized("photo_analysis.recovery.review_restore_unavailable"),
    ),
)

internal fun localizedRewardsCopy(): RewardsPresenterCopy = RewardsPresenterCopy(
    title = localized("rewards.title"),
    earnedLabel = localized("rewards.earned"),
    readyLabel = localized("rewards.ready"),
    progressTemplate = localized("rewards.progress_template"),
    cards = mapOf(
        RewardId.PROTEIN_ALL_DAY to RewardsCardCopy(
            title = localized("rewards.protein.title"),
            description = localized("rewards.protein.description"),
        ),
        RewardId.FATS_PRESENT to RewardsCardCopy(
            title = localized("rewards.fats.title"),
            description = localized("rewards.fats.description"),
        ),
        RewardId.FIBER_PRESENT to RewardsCardCopy(
            title = localized("rewards.fiber.title"),
            description = localized("rewards.fiber.description"),
        ),
        RewardId.COLORS_OF_DAY to RewardsCardCopy(
            title = localized("rewards.colors.title"),
            description = localized("rewards.colors.description"),
        ),
        RewardId.THIRTY_PRODUCTS to RewardsCardCopy(
            title = localized("rewards.products.title"),
            description = localized("rewards.products.description"),
        ),
        RewardId.FERMENTED_THREE_DAYS to RewardsCardCopy(
            title = localized("rewards.fermented.title"),
            description = localized("rewards.fermented.description"),
        ),
        RewardId.SEVEN_DAYS_RECORDED to RewardsCardCopy(
            title = localized("rewards.seven_days.title"),
            description = localized("rewards.seven_days.description"),
        ),
    ),
)

private fun localized(key: String): String = NSBundle.mainBundle.localizedStringForKey(
    key = key,
    value = key,
    table = null,
).takeUnless { it == key }
    ?: error("Missing required Apple localization key: $key")
