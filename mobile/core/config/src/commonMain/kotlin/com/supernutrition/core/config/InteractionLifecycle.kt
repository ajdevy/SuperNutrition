package com.supernutrition.core.config

/**
 * A proposed default is immutable and bound to one configuration/policy
 * snapshot. The ledger below is intentionally in-memory and attempt-scoped;
 * correcting a default never becomes a durable user preference.
 */
data class DefaultProposal(
    val proposalId: String,
    val field: DefaultableField,
    val value: String,
    val provenance: DefaultProvenance,
    val snapshotDigest: String,
    val policyDigest: String,
    val revision: Long,
) {
    init {
        require(proposalId.isNotBlank())
        require(value.isNotBlank())
        require(snapshotDigest.matches(DIGEST_PATTERN))
        require(policyDigest.matches(DIGEST_PATTERN))
        require(revision >= 0)
    }

    private companion object {
        val DIGEST_PATTERN = Regex("[a-f0-9]{64}")
    }
}

sealed interface DefaultDecision {
    val decisionId: String
    val proposalId: String
    val revision: Long

    data class Accepted(
        override val decisionId: String,
        override val proposalId: String,
        override val revision: Long,
        val value: String,
        val provenance: DefaultProvenance,
    ) : DefaultDecision

    data class Overridden(
        override val decisionId: String,
        override val proposalId: String,
        override val revision: Long,
        val value: String,
        val provenance: DefaultProvenance,
    ) : DefaultDecision

    data class Undone(
        override val decisionId: String,
        override val proposalId: String,
        override val revision: Long,
        val undoneDecisionId: String,
    ) : DefaultDecision

    data class Invalidated(
        override val decisionId: String,
        override val proposalId: String,
        override val revision: Long,
        val reason: String,
    ) : DefaultDecision
}

/**
 * Owns proposal/decision lifecycle for one analysis attempt. Every mutating
 * operation is idempotent by decision ID, rejects stale revisions, and refuses
 * to act after invalidation or a snapshot/policy digest change.
 */
class DefaultDecisionLedger(private val snapshotDigest: String, private val policyDigest: String) {
    private val decisionsById = mutableMapOf<String, DefaultDecision>()
    private val latestRevisionByProposal = mutableMapOf<String, Long>()
    private val invalidatedProposals = mutableSetOf<String>()

    init {
        require(snapshotDigest.matches(DIGEST_PATTERN))
        require(policyDigest.matches(DIGEST_PATTERN))
    }

    fun accept(proposal: DefaultProposal, decisionId: String): DefaultDecision = record(
        proposal = proposal,
        decisionId = decisionId,
    ) {
        DefaultDecision.Accepted(
            decisionId = decisionId,
            proposalId = proposal.proposalId,
            revision = proposal.revision,
            value = proposal.value,
            provenance = proposal.provenance,
        )
    }

    fun override(proposal: DefaultProposal, decisionId: String, value: String): DefaultDecision {
        require(value.isNotBlank())
        return record(proposal, decisionId) {
            DefaultDecision.Overridden(
                decisionId = decisionId,
                proposalId = proposal.proposalId,
                revision = proposal.revision,
                value = value,
                provenance = proposal.provenance,
            )
        }
    }

    fun undo(proposal: DefaultProposal, decisionId: String, undoneDecisionId: String): DefaultDecision {
        require(decisionsById[undoneDecisionId] != null) {
            "Cannot undo an unknown default decision"
        }
        return record(proposal, decisionId) {
            DefaultDecision.Undone(
                decisionId = decisionId,
                proposalId = proposal.proposalId,
                revision = proposal.revision,
                undoneDecisionId = undoneDecisionId,
            )
        }
    }

    fun invalidate(proposal: DefaultProposal, decisionId: String, reason: String): DefaultDecision {
        require(reason.isNotBlank())
        val decision = record(proposal, decisionId) {
            DefaultDecision.Invalidated(
                decisionId = decisionId,
                proposalId = proposal.proposalId,
                revision = proposal.revision,
                reason = reason,
            )
        }
        invalidatedProposals += proposal.proposalId
        return decision
    }

    fun decisions(): List<DefaultDecision> = decisionsById.values.toList()

    private fun record(
        proposal: DefaultProposal,
        decisionId: String,
        factory: () -> DefaultDecision,
    ): DefaultDecision {
        require(decisionId.isNotBlank())
        decisionsById[decisionId]?.let { existing ->
            require(existing.proposalId == proposal.proposalId) {
                "Decision ID cannot be reused for another proposal"
            }
            return existing
        }
        require(proposal.snapshotDigest == snapshotDigest) {
            "Default proposal is bound to a different configuration snapshot"
        }
        require(proposal.policyDigest == policyDigest) {
            "Default proposal is bound to a different interaction policy"
        }
        require(proposal.proposalId !in invalidatedProposals) {
            "Invalidated default proposals cannot be decided"
        }
        val previousRevision = latestRevisionByProposal[proposal.proposalId]
        require(previousRevision == null || proposal.revision > previousRevision) {
            "Default proposal revisions must be monotonic"
        }
        val decision = factory()
        decisionsById[decisionId] = decision
        latestRevisionByProposal[proposal.proposalId] = proposal.revision
        if (decision is DefaultDecision.Invalidated) {
            invalidatedProposals += proposal.proposalId
        }
        return decision
    }

    private companion object {
        val DIGEST_PATTERN = Regex("[a-f0-9]{64}")
    }
}
