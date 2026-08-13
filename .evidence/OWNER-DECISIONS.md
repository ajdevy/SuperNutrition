# Deferred owner decisions

Three findings from the review are real but are not mine to settle: each one
picks between defensible designs, and the choice binds a published contract or
a privacy posture. They were left alone deliberately. This records what the
current behaviour is, what the options cost, and what would have to change.

Nothing here is a blocker for either pull request. Each is written so it can be
decided without re-reading the review.

## 1. Rewrite `inference/v1` or publish `inference/v2`

**Current state.** `contracts/inference/v2` and
`contracts/inference-evidence/v2` exist and are published alongside v1. Both
releases describe the same surface. Their compatibility matrices mark unbuilt
consumers `not-yet-implemented` rather than `accepted`, and
`scripts/tests/test_v2_compatibility_claims.py` ties an `accepted` claim to a
real implementation, so nothing currently claims support it does not have.

**The decision.** Two published versions of one surface is a cost that
compounds: every consumer has to say which it speaks, every fixture exists
twice, and the negative fixtures have to mutate both. The alternatives:

- **Fold v2 back into v1.** v1 is frozen, so this is a contract break and every
  consumer needs a coordinated update. Cheapest long-term if v1 has no external
  consumers yet.
- **Keep both, deprecate v1.** No break, but the duplication stays until every
  consumer migrates, and `not-yet-implemented` rows have to be worked off one
  at a time.

**What settles it:** whether anything outside this repository already speaks
`inference/v1`. That is not visible from here.

## 2. Server-side market binding

**Current state.** The client sends `claimedMarketJurisdiction` and the gateway
resolves policy from it. `MarketPolicyResolver` owns the resolution, and the
response reports `resolvedMarketJurisdiction` and `resolvedProcessingRegion`,
which the client binds against the region the consent receipt promised — a
response from a region the user was not shown is refused.

**The gap.** The jurisdiction itself is still the client's claim. A client that
claims a permissive market gets that market's policy. The binding proves the
server answered consistently with what it was told, not that what it was told
was true.

**The decision.** Deriving the jurisdiction server-side means deriving it from
something — request geography, the installation attestation, or an account
attribute. Each has a privacy cost of its own, and inferring a user's country
from their IP to decide what their privacy rights are is a policy question, not
an implementation detail. Doing it wrong is worse than the current state,
because a wrongly-derived jurisdiction is not correctable by the user.

**What settles it:** which signal the product is willing to collect for this,
and what happens when it disagrees with the client's claim — refuse, or apply
the stricter of the two.

## 3. Consent receipt persistence

**Current state.** Receipts are verified per request and not stored. Verification
covers the digest binding, the attempt binding, expiry, and clock skew
(`CONSENT_CLOCK_SKEW_SECONDS`). The product does not keep a record of what the
user consented to after the request that carried it.

**The gap.** There is no way to answer "what was this user shown when they
agreed to this upload" after the fact. That is a question a regulator or the
user may ask.

**The decision.** Storing receipts means storing a record of what each user
consented to and when — durable personal data, with its own retention policy,
subject-access path and deletion obligation. AD-8 keeps images ephemeral and
makes no recall claims; a receipt store is a different kind of record from the
image, but it is still a record, and adding one to a product built around not
keeping things is a deliberate reversal rather than an oversight to correct.

**What settles it:** whether the obligation to evidence consent outweighs the
cost of holding it, and if so, what the retention period for the receipts
themselves is.
