package com.supernutrition.testing.composesupport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class AcceptanceTraceTest {
    @Test
    fun `trace records typed steps and redacts failure evidence`() {
        val trace = AcceptanceTrace("bootstrap-launch-di")
        trace.record(StepId("launch"), SemanticId("bootstrap_shell_launch_di"))

        val bundle = trace.failureBundle("a".repeat(64))

        assertEquals(1, bundle.steps.size)
        assertTrue(bundle.semanticsTreeIncluded)
        assertEquals("a".repeat(64), bundle.screenshotDigest)
    }

    @Test
    fun `bounded wait does not sleep or retry after success`() {
        val trace = AcceptanceTrace("bootstrap-launch-di")
        var evaluations = 0

        val result = trace.boundedWait(100.milliseconds) {
            evaluations += 1
            true
        }

        assertTrue(result)
        assertEquals(1, evaluations)
    }

    @Test
    fun `invalid screenshot digests and blank IDs are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            SemanticId("")
        }
        assertFailsWith<IllegalArgumentException> {
            RedactedFailureBundle("scenario", emptyList(), true, "not-a-digest")
        }
        assertFalse("raw-photo-body".matches(Regex("[a-f0-9]{64}")))
    }

    @Test
    fun `failed assertion is final and raw selectors cannot escape typed scope`() {
        val trace = AcceptanceTrace("scenario")
        assertFailsWith<IllegalStateException> {
            trace.assertFinal(StepId("ready"), SemanticId("ready")) { false }
        }
        assertFailsWith<IllegalArgumentException> {
            trace.assertFinal(StepId("ready"), SemanticId("ready")) { true }
        }

        val scope = object : TypedScreenScope {
            override val screenId = "capture"
        }
        assertEquals("capture_shutter", scope.semanticTarget(SemanticId("capture_shutter")).value)
        assertFailsWith<SelectorScopeEscapeException> {
            scope.rejectRawSelector("//*[@text='Capture']")
        }
    }

    @Test
    fun `host session and cleanup are idempotent and privacy writer emits only digests`() {
        val events = mutableListOf<String>()
        val host = object : ComposeAcceptanceHost {
            override fun setContent(content: () -> Unit) {
                events += "set"
                content()
            }

            override fun awaitIdle() {
                events += "idle"
            }

            override fun clearContent() {
                events += "clear"
            }
        }
        val cleanup = CleanupRegistry()
        cleanup.register { events += "first" }
        cleanup.register { events += "second" }
        val session = AcceptanceHostSession(host, cleanup)
        session.start { events += "content" }
        session.start { events += "ignored" }
        session.close()
        session.close()

        assertEquals(listOf("set", "content", "idle", "second", "first", "clear"), events)

        var artifactName = ""
        var artifactContent = ""
        PrivacySafeArtifactWriter(object : RedactedArtifactSink {
            override fun write(name: String, content: String) {
                artifactName = name
                artifactContent = content
            }
        }).writeFailure(
            RedactedFailureBundle(
                scenarioId = "scenario",
                steps = listOf(StepRecord(StepId("launch"), SemanticId("shell"))),
                semanticsTreeIncluded = true,
                screenshotDigest = "a".repeat(64),
            ),
        )
        assertEquals("scenario-failure.txt", artifactName)
        assertTrue(artifactContent.contains("a".repeat(64)))
        assertFalse(artifactContent.contains("raw-photo"))
    }
}
