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
    override val screenId: String = "photo-analysis.capture"

    private val title = SemanticId(UiAutomationIds.PHOTO_ANALYSIS_TITLE.value)
    private val status = SemanticId(UiAutomationIds.PHOTO_ANALYSIS_STATUS.value)
    private val shutter = SemanticId(UiAutomationIds.CAPTURE_SHUTTER.value)
    private val import = SemanticId(UiAutomationIds.CAPTURE_IMPORT.value)
    private val scanAgain = SemanticId(UiAutomationIds.CAPTURE_SCAN_AGAIN.value)

    fun assertCaptureSurface() {
        trace.assertFinal(StepId("capture-surface"), title) {
            host.state() == PhotoAnalysisPresenterState.CAPTURE &&
                host.text(title) == copy.capture.title &&
                host.text(status) == copy.status.readyToCapture
        }
        check(host.text(shutter) == copy.capture.takeMealPhoto) { "Capture shutter semantics drifted" }
        check(host.text(import) == copy.capture.choosePhoto) { "Capture import semantics drifted" }
    }

    fun takePhoto() {
        trace.record(StepId("take-photo"), shutter)
        host.click(shutter)
    }

    fun importPhoto() {
        trace.record(StepId("import-photo"), import)
        host.click(import)
    }

    fun assertRecoverySurface(expectedStatus: String) {
        trace.assertFinal(StepId("recovery-surface"), status) {
            host.state() == PhotoAnalysisPresenterState.RECOVERY &&
                host.text(status) == expectedStatus
        }
        check(host.text(scanAgain) == copy.recovery.scanAgain) { "Recovery retry semantics drifted" }
}
}
