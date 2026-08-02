package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.CaptureCopy
import com.supernutrition.feature.photoanalysis.EvidenceCopy
import com.supernutrition.feature.photoanalysis.PhotoAnalysisPresenterCopy
import com.supernutrition.feature.photoanalysis.RecoveryCopy
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
    ),
    status = StatusCopy(
        readyToCapture = localized("photo_analysis.status.ready_to_capture"),
        preparingPhoto = localized("photo_analysis.status.preparing_photo"),
        photoReady = localized("photo_analysis.status.photo_ready"),
        analyzingPhoto = localized("photo_analysis.status.analyzing_photo"),
        findingVisibleFoods = localized("photo_analysis.status.finding_visible_foods"),
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
    ),
    recovery = RecoveryCopy(
        scanAgain = localized("photo_analysis.recovery.scan_again"),
        cameraPermissionRequired = localized("photo_analysis.recovery.permission_required"),
        cameraUnavailable = localized("photo_analysis.recovery.camera_unavailable"),
        photoCaptureFailed = localized("photo_analysis.recovery.capture_failed"),
        photoQualityTooLow = localized("photo_analysis.recovery.quality_too_low"),
        noFoodFound = localized("photo_analysis.recovery.no_food_found"),
        analysisOffline = localized("photo_analysis.recovery.offline"),
        analysisCancelled = localized("photo_analysis.recovery.cancelled"),
    ),
)

private fun localized(key: String): String = NSBundle.mainBundle.localizedStringForKey(
    key = key,
    value = key,
    table = null,
).takeUnless { it == key }
    ?: error("Missing required Apple localization key: $key")
