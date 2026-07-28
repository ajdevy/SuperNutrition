package com.supernutrition.testing.scaffoldprobe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class BootstrapShellRobotTest {
    @Test
    fun `robot records logical launch and DI steps without selectors`() {
        val shell = FakeBootstrapShell()
        val robot = BootstrapShellRobot(shell)

        robot.launch()
        assertTrue(robot.awaitDependencyInjection(10.milliseconds))
        robot.assertLaunchMarker()

        val bundle = robot.failureBundle("b".repeat(64))
        assertEquals("bootstrap-launch-di", bundle.scenarioId)
        assertEquals(listOf("launch", "await-di"), bundle.steps.map { it.id.value })
        assertEquals(
            listOf("bootstrap_shell_launch_di", "bootstrap_shell_di_ready"),
            bundle.steps.map { it.semanticTarget.value },
        )
    }
}

private class FakeBootstrapShell : BootstrapShellPort {
    override var dependencyInjectionReady: Boolean = false
        private set
    override val launchMarker: String = "bootstrap_shell_launch_di"

    override fun launch() {
        dependencyInjectionReady = true
    }
}
