package com.supernutrition.testing.scaffoldprobe

import com.supernutrition.testing.composesupport.AcceptanceTrace
import com.supernutrition.testing.composesupport.RedactedFailureBundle
import com.supernutrition.testing.composesupport.SemanticId
import com.supernutrition.testing.composesupport.StepId
import kotlin.time.Duration

/**
 * Test-only robot contract for the non-product bootstrap shell.
 *
 * A packaged Android/iOS acceptance host supplies the port. The robot owns
 * only the logical scenario and never stores platform selectors, photo data,
 * credentials, or provider fixtures.
 */
class BootstrapShellRobot(private val shell: BootstrapShellPort) {
    private val trace = AcceptanceTrace(SCENARIO_ID)

    fun launch() {
        trace.record(StepId("launch"), SemanticId(LAUNCH_SEMANTIC_ID))
        shell.launch()
    }

    fun awaitDependencyInjection(timeout: Duration): Boolean {
        trace.record(StepId("await-di"), SemanticId(DI_SEMANTIC_ID))
        return trace.boundedWait(timeout) { shell.dependencyInjectionReady }
    }

    fun assertLaunchMarker() {
        check(shell.launchMarker == LAUNCH_MARKER) {
            "Bootstrap host did not expose the expected launch marker"
        }
    }

    fun failureBundle(screenshotDigest: String? = null): RedactedFailureBundle = trace.failureBundle(screenshotDigest)

    private companion object {
        const val SCENARIO_ID = "bootstrap-launch-di"
        const val LAUNCH_SEMANTIC_ID = "bootstrap_shell_launch_di"
        const val DI_SEMANTIC_ID = "bootstrap_shell_di_ready"
        const val LAUNCH_MARKER = LAUNCH_SEMANTIC_ID
    }
}

interface BootstrapShellPort {
    val dependencyInjectionReady: Boolean
    val launchMarker: String

    fun launch()
}
