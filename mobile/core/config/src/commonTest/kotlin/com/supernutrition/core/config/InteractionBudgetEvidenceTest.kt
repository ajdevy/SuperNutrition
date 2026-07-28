package com.supernutrition.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InteractionBudgetEvidenceTest {
    private val maxima = InteractionBudgetMaxima(
        required = RequiredInteractionCounts(tap = 2, scroll = 0, field = 0, choice = 0),
        auxiliary = AuxiliaryInteractionCounts(
            textEntryChars = 0,
            choiceSurface = 0,
            back = 1,
            undo = 0,
            cancel = 1,
            permissionPrompt = 1,
        ),
    )

    private fun evidence(tap: Int = 2) = InteractionBudgetEvidence(
        schemaVersion = "interaction-budget.v1",
        policyVersion = "v1",
        policyDigest = "b".repeat(64),
        configurationDigest = "c".repeat(64),
        proposalId = null,
        decisionId = null,
        resolvedValueDigest = null,
        fieldId = "capture",
        sourceIdentity = "photo-analysis-capture-recovery",
        scenarioId = "photo-analysis-capture-recovery",
        platform = "android",
        locale = "en-US",
        accessibilityMode = "default",
        defaultProvenance = null,
        lifecycleOutcome = "capture-or-cancellation",
        eventSequence = 1,
        required = RequiredInteractionCounts(tap, 0, 0, 0),
        auxiliary = AuxiliaryInteractionCounts(0, 0, 0, 0, 0, 0),
    )

    @Test
    fun `bounded evidence is accepted`() {
        maxima.assertContains(evidence())
    }

    @Test
    fun `observed dimensions above maxima are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            maxima.assertContains(evidence(tap = 3))
        }
    }

    @Test
    fun `invalid digest cannot create evidence`() {
        assertFailsWith<IllegalArgumentException> {
            evidence().copy(policyDigest = "invalid")
        }
    }

    @Test
    fun `every required and auxiliary dimension is bounded independently`() {
        val required = listOf(
            evidence().copy(required = RequiredInteractionCounts(3, 0, 0, 0)),
            evidence().copy(required = RequiredInteractionCounts(2, 1, 0, 0)),
            evidence().copy(required = RequiredInteractionCounts(2, 0, 1, 0)),
            evidence().copy(required = RequiredInteractionCounts(2, 0, 0, 1)),
        )
        required.forEach { observed ->
            assertFailsWith<IllegalArgumentException> { maxima.assertContains(observed) }
        }

        val auxiliary = listOf(
            evidence().copy(auxiliary = AuxiliaryInteractionCounts(1, 0, 0, 0, 0, 0)),
            evidence().copy(auxiliary = AuxiliaryInteractionCounts(0, 1, 0, 0, 0, 0)),
            evidence().copy(auxiliary = AuxiliaryInteractionCounts(0, 0, 2, 0, 0, 0)),
            evidence().copy(auxiliary = AuxiliaryInteractionCounts(0, 0, 0, 1, 0, 0)),
            evidence().copy(auxiliary = AuxiliaryInteractionCounts(0, 0, 0, 0, 2, 0)),
            evidence().copy(auxiliary = AuxiliaryInteractionCounts(0, 0, 0, 0, 0, 2)),
        )
        auxiliary.forEach { observed ->
            assertFailsWith<IllegalArgumentException> { maxima.assertContains(observed) }
        }
    }

    @Test
    fun `negative interaction counters are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RequiredInteractionCounts(tap = -1, scroll = 0, field = 0, choice = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            AuxiliaryInteractionCounts(0, 0, 0, 0, 0, permissionPrompt = -1)
        }
    }

    @Test
    fun `all evidence identity fields are validated`() {
        val invalid = listOf<() -> Unit>(
            { evidence().copy(schemaVersion = "") },
            { evidence().copy(policyVersion = "") },
            { evidence().copy(configurationDigest = "invalid") },
            { evidence().copy(proposalId = "") },
            { evidence().copy(decisionId = "") },
            { evidence().copy(resolvedValueDigest = "invalid") },
            { evidence().copy(fieldId = "") },
            { evidence().copy(sourceIdentity = "") },
            { evidence().copy(scenarioId = "") },
            { evidence().copy(platform = "") },
            { evidence().copy(locale = "") },
            { evidence().copy(accessibilityMode = "") },
            { evidence().copy(lifecycleOutcome = "") },
            { evidence().copy(eventSequence = -1) },
        )

        invalid.forEach { createInvalid ->
            assertFailsWith<IllegalArgumentException> { createInvalid() }
        }
        assertEquals(64, evidence().policyDigest.length)
    }
}
