package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.CaptureCopy
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
        cancel = localized("photo_analysis.action.cancel"),
    ),
    status = StatusCopy(
        readyToCapture = localized("photo_analysis.status.ready_to_capture"),
        preparingPhoto = localized("photo_analysis.status.preparing_photo"),
        photoReady = localized("photo_analysis.status.photo_ready"),
        analyzingPhoto = localized("photo_analysis.status.analyzing_photo"),
        reviewCandidate = localized("photo_analysis.status.review_candidate"),
        restoringPhotoAnalysis = localized("photo_analysis.status.restoring_photo_analysis"),
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
