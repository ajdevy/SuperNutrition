# G-6 readiness: what correction telemetry retention must supply

**Owner:** privacy/legal + the principal who owns retention.
**Blocks:** server-side correction telemetry, which is off by default.

The spine's exit artifact is *"approved consent copy, versioned schema, purpose,
retention/deletion period, regional handling, user-rights path, and owner."*

G-6 is the one gate whose subject matter is almost entirely absent from code,
and that is the correct state rather than a gap. **No transport, sink or uploader
exists anywhere in `mobile/`.** "Correction telemetry is off by default" is true
because there is nothing to turn on, not because a flag is set to false.

## What must be supplied

| Input | Why the code needs it | State today |
| --- | --- | --- |
| **Approved consent copy** | The only consent in code governs sending a *description to an inference provider*, not retaining telemetry — a different purpose needing its own copy | Telemetry consent does not exist as a concept |
| **Versioned schema** | A correction event has no type; no action-code vocabulary exists | Absent |
| **Purpose** | Determines which fields are defensible at all | Not stated |
| **Retention / deletion period** | The central clause | `retention` appears in no source file |
| **Regional handling** | Would reuse E1.6's market axis or introduce a second one — deciding which is a G-6 question | Undecided |
| **User-rights path** | How someone reaches their correction data | Absent |
| **Owner** | Who approves and who is accountable | Unnamed |

## What the code already commits to

- **The v2 telemetry producer's field list is closed at eleven named fields**,
  with `unlisted_field = "reject"` and `raw_payload = "forbidden"`, checked
  against the canonical fixture in both directions. A correction field cannot be
  slipped in.
- **Free text is non-loggable in three independent places**: declared in the
  contract, enforced by the contract verifier, and asserted on the *encoded
  bytes* by a test that fails if `description`, a span, a canonical id or a label
  ever appears.
- **A telemetry event is derived from the result it describes**, not assembled by
  a caller, so route, engine, outcome and item count cannot be reported as
  something other than what happened. A result spanning two engines refuses to
  produce an event at all.
- **Counts are bucketed, not exact** — "this person logged seven items is closer
  to a diary entry than to an operational measurement." That precedent should
  govern the correction event too.
- **Corrections are already session-scoped**, declared in the released
  interaction-policy contract and digest-pinned. G-6 is not being asked whether
  to retain corrections locally; that answer is checked in.
- **The forbidden-default inventory is closed by set equality in the build** and
  contains no telemetry consent entry. Adding one is a deliberate edit to a
  digest-pinned manifest, not a configuration change.

## The design questions to settle before drafting a schema

1. **Where does the correction vocabulary live?** v2's `emitted_fields` is closed
   and its `event_name` list has one entry, justified as "a vocabulary nothing
   emits is a claim, not a contract". A correction event is a second event name
   and at least one new field, which reads as a **v3**, not an amendment. Settle
   this first, or G-6 produces a schema with no release to live in.
2. **An edit, or a review outcome?** `SegmentationReview` offers five operations.
   One code per operation reconstructs a keystroke-level history of someone
   editing their meal; one summary per review does not. The bucketing precedent
   argues for the summary.
3. **May a correction event carry an unresolved fragment?** "Which words the floor
   could not resolve" is the most operationally valuable correction signal — and
   `Floor.unresolvedFragments` holds literal substrings of what the user typed,
   which the contract forbids. By contrast `unmatchedPreparation` is safe: it is
   filtered against a closed 20-word vocabulary. A bounded fragment vocabulary
   would be an explicit design decision, not a field someone adds.
4. **Does the attempt correlation ID arrive with G-6 or before it?** AD-14 permits
   an opaque attempt ID; neither telemetry event has one. Without it a correction
   cannot be joined to the attempt it corrected — most of the analytic value —
   yet adding it changes a released contract's emitted fields.

## How we will know the gate is satisfied

1. A named owner, a purpose, and a retention period with a deletion mechanism —
   not a policy sentence.
2. A correction schema in a release that can hold it, with the action-code
   vocabulary closed and bound to the Kotlin in both directions the way the
   telemetry vocabularies now are.
3. Consent copy specific to retention, distinct from transfer consent, and a
   forbidden-default entry so it can never be preselected.
4. Negative fixtures proving a correction event cannot carry raw text.

## Two things not to restate

**Resource-bucket vocabulary is G-3's.** `resource_bucket` is absolute rather
than budget-relative because no device budget exists; G-3's document already
names revisiting it as a v3 question.

**"Server-side" needs G-4.** `backend/mobile-api` contains a quality-check script
and a lifecycle file, and no Kotlin. There is no server for correction telemetry
to be retained on. G-6 can approve a retention policy while G-4 is open, but
nothing can implement it.
