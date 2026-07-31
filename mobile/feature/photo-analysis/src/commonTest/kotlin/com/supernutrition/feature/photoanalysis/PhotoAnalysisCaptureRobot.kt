package com.supernutrition.feature.photoanalysis

import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.testing.composesupport.AcceptanceTrace
import com.supernutrition.testing.composesupport.SemanticId
import com.supernutrition.testing.composesupport.StepId
import com.supernutrition.testing.composesupport.TypedScreenScope

/** Shared robot contract; platform runners provide only the host adapter. */
interface PhotoAnalysisCaptureRobotHost {
    fun state(): PhotoAnalysisPresenterState
    fun text(target: SemanticId): String?
    fun click(target: SemanticId)
}

class PhotoAnalysisCaptureRobot(
    private val host: PhotoAnalysisCaptureRobotHost,
    private val copy: PhotoAnalysisPresenterCopy,
    val trace: AcceptanceTrace = AcceptanceTrace("photo-analysis-capture-recovery"),
) : TypedScreenScope {
    override val screenId: String = PhotoAnalysisCaptureRobotContract.SCREEN_ID

    private val title = SemanticId(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value)
    private val status = SemanticId(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value)
    private val shutter = SemanticId(UiAutomationIds.CAPTURE_SHUTTER.value)
    private val import = SemanticId(UiAutomationIds.CAPTURE_IMPORT.value)
    private val scanAgain = SemanticId(UiAutomationIds.CAPTURE_SCAN_AGAIN.value)

    fun assertCaptureSurface() {
        trace.assertFinal(PhotoAnalysisCaptureRobotContract.CAPTURE_SURFACE_STEP, title) {
            host.state() == PhotoAnalysisPresenterState.CAPTURE &&
                host.text(title) == copy.capture.title &&
                host.text(status) == copy.status.readyToCapture
        }
        check(host.text(shutter) == copy.capture.takeMealPhoto)
        check(host.text(import) == copy.capture.choosePhoto)
    }

    fun takePhoto() {
        trace.record(PhotoAnalysisCaptureRobotContract.TAKE_PHOTO_STEP, shutter)
        host.click(shutter)
    }

    fun importPhoto() {
        trace.record(PhotoAnalysisCaptureRobotContract.IMPORT_PHOTO_STEP, import)
        host.click(import)
    }

    fun assertRecoverySurface(expectedStatus: String) {
        trace.assertFinal(PhotoAnalysisCaptureRobotContract.RECOVERY_SURFACE_STEP, status) {
            host.state() == PhotoAnalysisPresenterState.RECOVERY &&
                host.text(status) == expectedStatus
        }
        check(host.text(scanAgain) == copy.recovery.scanAgain)
    }
}

internal object PhotoAnalysisCaptureRobotContract {
    const val SCREEN_ID = "photo-analysis.capture"
    val CAPTURE_SURFACE_STEP = StepId("capture-surface")
    val TAKE_PHOTO_STEP = StepId("take-photo")
    val IMPORT_PHOTO_STEP = StepId("import-photo")
    val RECOVERY_SURFACE_STEP = StepId("recovery-surface")
}
