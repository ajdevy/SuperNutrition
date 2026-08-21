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

---

# Closing decisions (2026-08-13)

The three items above were recorded as deferred. Two of them are still the
owner's, for a specific reason. One is not, and is decided here.

## 1. `contracts/mobile-api/v1`: the rewrite stands — DECIDED

This slice edited three published v1 files to add the E4.3 gateway fields. The
immutability gate it also adds rejects exactly that, which is why
`acknowledged-edits.toml` carries three pinned entries.

**Decision: v1 keeps the rewrite. mobile-api/v2 is not published.**

The immutability rule exists to protect consumers that cannot be updated in
lockstep with the producer. `mobile-api/v1` has exactly one consumer, the mobile
client in this repository, and no build of it has reached a user. There is
nobody holding the old bytes. Publishing v2 to protect an audience of zero would
add a second live release, a second fixture set and a second migration story for
no reader.

What makes this safe is that it is bounded rather than general: the three
acknowledgements are pinned to exact digests, so any further edit to those files
fails the gate, and they expire 2026-09-30. That expiry is not a deadline for
this decision — it is the point at which a NEW edit would need its own
justification. Tracked so it cannot lapse silently.

This is reversible: publishing v2 later costs the same as publishing it now.

## 2. Server-side market binding — REMAINS THE OWNER'S

Not decided here, and deliberately so. Binding the market server-side means
choosing a signal to derive it from: request geography, installation
attestation, or an account attribute. Each is a decision to start collecting
something about users that the product does not collect today.

That is the kind of choice that is hard to walk back — once a jurisdiction
signal is collected and relied upon, removing it changes the privacy posture and
the legal basis, not just the code. It is business-irreversible in a way the
contract-versioning question is not.

Contained meanwhile: fixture markets in test mode, empty production policy, and
the response reports the resolved jurisdiction and region so the client can
refuse a reply that contradicts the receipt shown before consent.

**Recommendation if asked:** derive from installation attestation rather than IP.
It is already collected for AD-21, it is not a location, and it does not change
what the product knows about a user.

## 3. Consent-receipt persistence — REMAINS THE OWNER'S

Also not decided here, for the same class of reason. Storing receipts means
holding a durable record of what each user agreed to and when: personal data
with its own retention period, subject-access path and deletion obligation.

AD-8 keeps images ephemeral and makes no recall claims. Adding a consent store
to a product built around not keeping things is a deliberate reversal, and
un-collecting it later is not possible for records already written.

Contained meanwhile: every receipt is verified per request against digest,
attempt binding, expiry and clock skew. What is missing is evidence after the
fact, not enforcement during.

**Recommendation if asked:** if the obligation to evidence consent is real, store
the receipt digest and decision only — not the receipt body — with the same
retention as the inference run it belongs to.

---

# Owner decisions D1–D3 (2026-08-13, against main 401daec)

Recorded when the stacked photo-analysis pair was closed out. main is
unchanged by any of it: the slice awaits a fresh pull request.

## D1 — PR #39 is superseded and closed

main advanced twice past #39's base: Epic 3 (local inference) and Epic 7 (#37 —
typed identification, `contracts/inference/v3` as the **evidence** release,
market policy). Epic 7 re-implemented most of #39's scope independently.

The only content unique to #39 was `mobile/inference/remote` and its engine
contract. It has zero consumers, and its contract collides by path with main's
published v3. It was **not** re-homed to v4: a second engine release with no
consumer is a contract nobody reads.

Closed with a per-finding record on the PR itself. Every finding is either
satisfied-on-main or parked in issue #43, and no parked item's code path is
reachable on main today, so closing carries no shipping delta.

## D2 — PR #38 is the merge candidate

Merged as `401daec` and then reverted from main at the owner's instruction, so
the slice now waits on this branch for a fresh pull request. Nothing about its
content changed: `401daec`'s tree and this branch's tree are the same work.
Carries all of handover-38: H1–H3, M1–M18, every Low group, and the
three deferred contract decisions above.

Two defects surfaced only when the merge first ran the workflow against a tree
where Epic 7's rename had landed, and both are recorded in their own commits:

- `ci-required.yml` called three `inference-v2-*` scripts that Epic 7 had
  renamed to v3. main's own required check was red for this reason before the
  merge; the merge only surfaced it.
- the immutability gate rejected M14's own market-policy digest correction. It
  is acknowledged narrowly, pinned to both digests and expiring 2026-09-30,
  rather than reverted or waved through.

## D3 — gap audit against main 401daec: no relocations

Every handover-38 finding is on main. Of handover-39, four are satisfied-on-main
(P1-1, P1-2, P1-3, P1-6, plus two P2s) and the rest are parked.

Three findings are genuinely absent from main and were **not** relocated,
because none is reachable in a shipped flow:

| finding | why it is not reachable |
|---|---|
| P1-5 | `LocalFirstRouter` is on main and complete; the coordinator never consults it. No remote path exists on main, so the policy cannot be violated in a way that reaches a provider. |
| P1-11 | main's v3 matrix marks `remote-v3` and `evaluation-v3` accepted while `mobile/inference/remote` and `mobile/evaluation/harness` are both absent. |
| P1-12 | `MealSearchScreen` rows carry no selection callback, and no app module depends on `meal-search-ui`. |

That is the terminal state: nothing left to port.

## Follow-ups

- **#43** — remote inference: the engine module, its contract, and P1-4/5/7/8/9/10/11/12
  plus three P2s parked with them.
- **evaluation-v2** — `contracts/inference/v2` marks it accepted with no harness
  in the tree. Unchanged by decision; same class as P1-11 above.

---

# Owner decisions on D-01 and D-02 (2026-08-15)

Source: the owner's `pr44-handover-decisions.md`, which reviews handover-3 and
handover-4 and adopts a recommendation for each open contract decision. Recorded
here because an issue comment is not the durable record; both are also posted to
#48 and #50.

Both decisions bear on `contracts/mobile-api/v1`, a published immutable release.
Neither is implemented by this branch: PR #44 documents the divergences in code
and tracks them, and claims neither closed.

> **Addendum, 2026-08-21 — the sentence above is no longer accurate for D-01.**
>
> Recorded as an addendum rather than an edit, because this file is the durable
> record and rewriting what it said is exactly the failure it exists to prevent.
>
> D-01's admission-entry half — #48(a), "a transfer is entered on admission,
> never on hand-off" — IS implemented on this branch. It is in
> `contracts/mobile-api/v1/transfer-states.toml` ("Entered on admission, never
> on hand-off: an attempt a provider never accepted has not transferred"), and
> the gateway follows it: `resultSuppressed` in
> `routes/gateway/transfer-state.ts` was rewritten because #48 stopped promoting
> the state at hand-off, which changed what a cancellation arriving mid-flight
> must report.
>
> The rest of D-01, and all of D-02, remain unimplemented and untracked as
> closed. Found by the PR #44 review (handover-10 item 20).

## D-01 — transfer state machine (#48)

**Decided: authorize the redefinition under the existing acknowledgement
mechanism rather than publishing `mobile-api/v2`, with a hard expiry condition.**

The reasoning the owner gives: `mobile-api/v1` has exactly one consumer, the
mobile client in this repository, and no build has reached a user. Publishing v2
to protect an audience of zero costs a second live release and a client
migration. The same reasoning already approved the E4.3 rewrite under three
pinned acknowledgements expiring 2026-09-30 (#47), and handover-4 explicitly
permits either path "while no client has shipped".

**The hard condition:** before the first external consumer or a shipped build,
these changes go into a real `v2`. Tied to the #47 deadline so it cannot lapse
silently.

### Correction to the framing — this is two divergences, not one

Reading `transfer-states.toml:35-46` and
`fixtures/cancellation-transferred.toml` against the code, the single decision
above actually covers two separate problems with different costs. This matters
because most of #48 does not need a contract decision at all:

**(a) When `transferred` is entered — no contract change required.**
The release says `provider_called = true` for the `transferred` state. The code
sets it before `orchestrator.analyze()` runs. Moving the transition to after the
provider accepts the payload makes the code *conform* to the published contract.
It is a pure code fix. It needs no acknowledgement, no v2, and no owner
decision — only the work.

**(b) `downstream_deletion_requested = true` — blocked by G-4, not by the
contract mechanism.**
The release requires `downstream_deletion_requested = true` when a `transferred`
attempt is cancelled, and the canonical fixture asserts it. The code returns
`false`, truthfully, because no provider adapter exposes an erasure operation —
which is what H-02 corrected. The release also carries an `honest_claim` for
this state reading "a deletion request was sent downstream", an assertion the
product cannot currently make.

This is the only part that needs the contract decision above. And note what
gates it: making the code satisfy the contract requires a real provider deletion
capability, which is a provider/DPA/legal decision under **G-4** — an open
deferral this document elsewhere instructs us not to touch.

**Consequence for sequencing.** (a) can be implemented immediately and
independently. (b) cannot be closed by an acknowledgement alone: an
acknowledgement authorizes editing the released file, but what to edit it to
depends on whether provider deletion will ever exist. Two coherent shapes:

- Record that `downstream_deletion_requested` is `false` until a provider
  deletion capability exists, and correct the `honest_claim` to match — the
  AD-8-honest option, available now, and one that stops the contract asserting
  something untrue.
- Wait for G-4 and then require `true` with a real adapter invocation behind it.

The first is recommended: leaving a published contract claiming a deletion
request that is never sent is the same class of defect H-02 fixed in the code.

## D-02 — validation order (#50)

**Decided: option 2 — split the concepts in the published list.**
`streamed-size-guard` stays pre-authentication, since it reads a length header
rather than a body; `magic-byte-sniff` and `dimension-bounds` are explicitly
marked post-authentication and pre-provider.

Option 3 (change the code to match the contract) is refused outright: it
reinstates the unauthenticated 8 MiB memory pin that the auth-first ordering was
introduced to remove. The code is right and the published text is wrong.

Nothing that these checks would reject reaches a provider, which is the property
the order exists to guarantee and states in its own comment. Only the relative
position of `authentication` diverges.

**Amendment to #50 as filed, adopted:** #50 recommended deferring to the next
release. The owner overrides that — #48 has to touch the same frozen contract
anyway, so both go in one round: one decision, one acknowledgement, one pass.

## Sequencing

1. Decisions recorded (this entry, plus comments on #48 and #50).
2. Merge #44 — **the owner's action.** D-01/D-02 do not block it.
3. Implement #48 and #50 together against the contract. Note that #48(a) needs
   no contract change and can start immediately; #48(b) needs the shape above
   settled first.
4. #49 (distributed state) strictly after #48, or the current state machine gets
   spread across instances and redone. Already in #49's acceptance criteria.
   AD-8 is unbreakable there: Redis carries identifiers, ownership, expiry and
   cancellation — never image bytes.
5. #42 then #41, in that order. Verified disjoint: #42 adds
   `docs/food-image-datasets-research.md` (+140); #41 removes
   `docs/food-photo-recognition-report.md` (-712) and
   `food-photo-recognition-research/outline.yaml` (-37). No conflict, and the
   order avoids a window with no current document.
6. Branch cleanup, and close #22/#23/#26/#27 as superseded by #44.

## Standing, unaffected

#45 (server-side market binding) and #46 (consent-receipt persistence) remain
the owner's; #47 expires 2026-09-30. G-4 continues to block production market,
production authentication, and provider/DPA terms. None of these is touched.
