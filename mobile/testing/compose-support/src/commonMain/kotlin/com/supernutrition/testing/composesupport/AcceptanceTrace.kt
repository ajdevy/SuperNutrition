package com.supernutrition.testing.composesupport

import kotlin.time.Duration
import kotlin.time.TimeSource

data class StepId(val value: String) {
    init {
        require(value.isNotBlank()) { "Step IDs must not be blank" }
    }
}

data class SemanticId(val value: String) {
    init {
        require(value.isNotBlank()) { "Semantic IDs must not be blank" }
    }
}

data class StepRecord(val id: StepId, val semanticTarget: SemanticId)

class SelectorScopeEscapeException(message: String) : IllegalArgumentException(message)

data class RedactedFailureBundle(
    val scenarioId: String,
    val steps: List<StepRecord>,
    val semanticsTreeIncluded: Boolean,
    val screenshotDigest: String?,
) {
    init {
        require(scenarioId.isNotBlank())
        require(screenshotDigest == null || screenshotDigest.matches(Regex("[a-f0-9]{64}"))) {
            "Screenshots must be represented by a SHA-256 digest"
        }
    }
}

/**
 * Shared v2 acceptance substrate. Concrete product robots stay beside their
 * feature and expose typed screen scopes rather than raw selectors.
 */
class AcceptanceTrace(private val scenarioId: String) {
    private val started = TimeSource.Monotonic.markNow()
    private val records = mutableListOf<StepRecord>()
    private val finalAssertions = mutableSetOf<String>()

    fun record(id: StepId, target: SemanticId) {
        records += StepRecord(id = id, semanticTarget = target)
    }

    /** A product assertion is final: a failed evaluation cannot be retried into success. */
    fun assertFinal(id: StepId, target: SemanticId, condition: () -> Boolean) {
        require(finalAssertions.add(id.value)) {
            "Assertion ${id.value} was already evaluated and cannot be retried"
        }
        record(id, target)
        check(condition()) { "Acceptance assertion failed: ${id.value}" }
    }

    fun boundedWait(timeout: Duration, condition: () -> Boolean): Boolean {
        require(!timeout.isNegative()) { "Wait timeout must be non-negative" }
        if (condition()) return true
        val deadline = started.elapsedNow() + timeout
        while (started.elapsedNow() < deadline) {
            if (condition()) return true
        }
        return false
    }

    fun failureBundle(screenshotDigest: String? = null): RedactedFailureBundle = RedactedFailureBundle(
        scenarioId = scenarioId,
        steps = records.toList(),
        semanticsTreeIncluded = true,
        screenshotDigest = screenshotDigest,
    )

    fun steps(): List<StepRecord> = records.toList()
}

interface TypedScreenScope {
    val screenId: String

    fun semanticTarget(id: SemanticId): SemanticId = id

    fun rejectRawSelector(selector: String): Nothing = throw SelectorScopeEscapeException(
        "Raw selector escaped typed screen scope $screenId: $selector",
    )
}

/** Test-only lifecycle seam for a Compose host; product hosts never depend on it. */
interface ComposeAcceptanceHost {
    fun setContent(content: () -> Unit)
    fun awaitIdle()
    fun clearContent()
}

class AcceptanceHostSession(
    private val host: ComposeAcceptanceHost,
    private val cleanup: CleanupRegistry = CleanupRegistry(),
) : AutoCloseable {
    private var started = false
    private var closed = false

    fun start(content: () -> Unit) {
        check(!closed) { "Acceptance host session is already closed" }
        if (started) return
        host.setContent(content)
        host.awaitIdle()
        started = true
    }

    override fun close() {
        if (closed) return
        cleanup.close()
        host.clearContent()
        closed = true
    }
}

class CleanupRegistry {
    private val actions = mutableListOf<() -> Unit>()
    private var closed = false

    fun register(action: () -> Unit) {
        check(!closed) { "Cannot register cleanup after close" }
        actions += action
    }

    fun close() {
        if (closed) return
        actions.asReversed().forEach { it() }
        actions.clear()
        closed = true
    }
}

interface RedactedArtifactSink {
    fun write(name: String, content: String)
}

/** Writes only allowlisted, digest-based failure evidence. */
class PrivacySafeArtifactWriter(private val sink: RedactedArtifactSink) {
    fun writeFailure(bundle: RedactedFailureBundle) {
        val screenshot = bundle.screenshotDigest ?: "none"
        val steps = bundle.steps.joinToString(",") { "${it.id.value}:${it.semanticTarget.value}" }
        sink.write(
            name = "${bundle.scenarioId}-failure.txt",
            content = "scenario=${bundle.scenarioId};steps=$steps;semantics=true;screenshot=$screenshot",
        )
    }
}
