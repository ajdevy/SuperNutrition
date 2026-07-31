package com.supernutrition.buildlogic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NativeEquivalentEvidenceVerifierTest {
    private val now = Instant.parse("2026-07-28T00:00:00Z")

    @Test
    fun `no native-equivalent denominator does not gate JVM coverage`() {
        assertTrue(
            NativeEquivalentEvidenceVerifier.allowed(
                coverageText = "[[denominator]]\ncoverage_kind = \"jvm\"",
                nativeGapsText = "",
                evidenceText = "",
                now = now,
            ),
        )
    }

    @Test
    fun `optional Apple native evidence does not gate local coverage`() {
        assertTrue(
            NativeEquivalentEvidenceVerifier.allowed(
                coverageText = """
                    [[denominator]]
                    coverage_kind = "native-equivalent"
                    evidence_ids = ["native-ios"]
                """.trimIndent(),
                nativeGapsText = """
                    lifecycle = "scaffold-pending"
                    [[gap]]
                    id = "native-ios"
                    optional = true
                    max_age_days = 30
                """.trimIndent(),
                evidenceText = "",
                now = now,
            ),
        )
    }

    @Test
    fun `fresh successful native evidence satisfies the gate`() {
        assertTrue(
            NativeEquivalentEvidenceVerifier.allowed(
                coverageText = coverage,
                nativeGapsText = gaps,
                evidenceText = evidence,
                now = now,
            ),
        )
    }

    @Test
    fun `pending lifecycle, missing records, invalid attestation, and stale evidence fail closed`() {
        assertFalse(
            NativeEquivalentEvidenceVerifier.allowed(
                coverageText = coverage,
                nativeGapsText = gaps.replace("lifecycle = \"active\"", "lifecycle = \"scaffold-pending\""),
                evidenceText = evidence,
                now = now,
            ),
        )
        assertFalse(
            NativeEquivalentEvidenceVerifier.allowed(coverage, gaps, evidence.replace("id = \"native-bridge\"", "id = \"other\""), now),
        )
        assertFalse(
            NativeEquivalentEvidenceVerifier.allowed(coverage, gaps, evidence.replace("status = \"success\"", "status = \"pending\""), now),
        )
        assertFalse(
            NativeEquivalentEvidenceVerifier.allowed(coverage, gaps, evidence.replace("commit_sha = \"${"0".repeat(40)}\"", "commit_sha = \"pending\""), now),
        )
        assertFalse(
            NativeEquivalentEvidenceVerifier.allowed(coverage, gaps, evidence.replace("artifact_digest = \"${"1".repeat(64)}\"", "artifact_digest = \"bad\""), now),
        )
        assertFalse(
            NativeEquivalentEvidenceVerifier.allowed(coverage, gaps, evidence.replace("fixture_digest = \"${"2".repeat(64)}\"", "fixture_digest = \"bad\""), now),
        )
        assertFalse(
            NativeEquivalentEvidenceVerifier.allowed(coverage, gaps, evidence.replace("device_os = \"pixel\"", "device_os = \"\""), now),
        )
        assertFalse(
            NativeEquivalentEvidenceVerifier.allowed(coverage, gaps, evidence.replace("executed_at = \"2026-07-27T00:00:00Z\"", "executed_at = \"invalid\""), now),
        )
        assertFalse(
            NativeEquivalentEvidenceVerifier.allowed(coverage, gaps.replace("max_age_days = 30", "max_age_days = 1"), evidence, now),
        )
    }

    @Test
    fun `missing denominator inventory fails closed`() {
        assertFailsWith<IllegalArgumentException> {
            NativeEquivalentEvidenceVerifier.allowed("lifecycle = \"active\"", "", "", now)
        }
    }

    @Test
    fun `duplicate evidence identifiers fail closed`() {
        assertFailsWith<IllegalArgumentException> {
            NativeEquivalentEvidenceVerifier.allowed(
                coverage,
                gaps,
                evidence + "\n[[evidence]]\nid = \"native-bridge\"",
                now,
            )
        }
    }

    private companion object {
        val coverage = """
            [[denominator]]
            source_path = "mobile/platform/photo-android"
            coverage_kind = "native-equivalent"
            evidence_ids = ["native-bridge"]
        """.trimIndent()

        val gaps = """
            lifecycle = "active"
            [[gap]]
            id = "native-bridge"
            max_age_days = 30
        """.trimIndent()

        val evidence = """
            lifecycle = "active"
            [[evidence]]
            id = "native-bridge"
            status = "success"
            commit_sha = "${"0".repeat(40)}"
            artifact_digest = "${"1".repeat(64)}"
            fixture_digest = "${"2".repeat(64)}"
            device_os = "pixel"
            task_version = "instrumentation-v1"
            executed_at = "2026-07-27T00:00:00Z"
        """.trimIndent()
    }
}
