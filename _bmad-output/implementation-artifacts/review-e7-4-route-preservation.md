# Review — E7.4 route preservation (telemetry, confirmation, fixture set)

Adversarial review of the uncommitted E7.4 slice on `feat/e7-meal-search-ui`.
Five independent lenses (contract drift, redaction, logic, fixture quality, test
quality), then one refute-by-default skeptic per deduplicated finding.

**15 raw findings → 14 after dedup → 3 survived verification, 11 refuted.**

Recorded before any fix, because a review whose output lives only in the
conversation has to be re-derived after compaction.

## Survived

### F-1 — the telemetry vocabularies are pinned in neither direction (medium)

`scripts/verify-inference-v2-contract.sh:163-168`

The new vocabulary loop checks each field against only the single value the
canonical fixture happens to carry. Seven of the twenty-four declared wire values
are pinned; seventeen are not. Nothing anywhere compares the `schema.toml` lists
to the Kotlin `@SerialName` values that actually produce the bytes.

Reproduced twice by the verifier. Renaming `@SerialName("over-5s")` to
`"over-30s"` in `InferenceTelemetryV2.kt` breaks no test — `OVER_5S` is
constructed in the failure test but never encoded, and the only encode assertions
use the fixture's `"under-2s"`. Separately, on a scratch copy with `route` cut to
`["local","local-text"]`, `"over-5s"` renamed, `"abstention"` renamed, and the
digest re-pinned exactly as this change re-pins it, the verifier printed
`inference-engine contract: OK` and exited 0.

Release immutability does not cover it: `contracts/inference/v2` is status `A`
against the merge base and has no entry in `contracts/release-catalog/v1`.

This is the failure the change's own comment claims to have closed — "a binding
written from the fixture and a binding written from the schema cannot disagree
about what the wire value is" is true for seven values and false for seventeen.
No wrong answer is produced at this commit; the defect is an unguarded invariant
asserted as guarded, which is the shape AGENTS.md names for duplicated
vocabularies that "each drifted within days".

Values unguarded in both directions are the v2-only ones: `2-to-5s`, `over-5s`,
`moderate`, `high`, `none`, `1`, `4-plus`, plus `remote` schema-side.
`capability_tier` and `terminal_outcome` reuse v1 enums, so a Kotlin rename there
would break v1 tests.

**Fix:** a binding verifier comparing the Kotlin `@SerialName` sets to the schema
vocabularies in both directions, with its own negative fixtures.

### F-2 — an unrecognised modality silently disables the route-agreement rule (medium)

`scripts/verify-inference-v2-contract.sh:154-178`

`modality` is in `emitted_fields`, so the key-set equality requires the fixture to
carry the key — but `modality` is not in `VOCABULARIES`, so its *value* is never
checked against the closed `["photo", "text"]` set. The two agreement assertions
are plain string equality, so any unrecognised value makes both `False` and
disables the only structural enforcement of the rule the whole `[telemetry]`
section was added for.

Reproduced via the script's own `SUPERNUTRITION_INFERENCE_V2_CONTRACT` override
with the digest re-pinned as a release author would: `modality="voice"`,
`modality="Text"`, and `modality=""` each paired with `route="local"` all exit 0,
while the correctly-spelled `text`/`local` and `photo`/`local-text` are rejected.

**Fix:** validate `modality` against `[modality].values`, and add negative
fixtures so the rule is proven rather than declared.

### F-3 — a fixture claims a mechanism it does not exercise (low)

`mobile/domain/nutrition/.../IdentificationFixtures.kt:38`

The `berenjena` fixture is labelled "Spanish, with diacritic fold". `berenjena`
has no accented character; it matches the literal alternate name at
`SeedCatalog.kt:115` at the EXACT tier, and `normalizeForSearch` reduces to plain
`lowercase()` for it. No string in `SeedCatalog.kt` contains any character the
fold rewrites — the only non-ASCII is Cyrillic, and `ё` is not in the Latin
`ACCENTED_CHARACTERS` table — so deleting the fold entirely leaves all 25 fixtures
passing.

Verified independently: `grep '[À-ÿ]' SeedCatalog.kt` returns nothing.

The fold is not untested — `CatalogSearchTest` exercises it with a synthetic
`Plátano` record and does fail if the fold is removed — and no user gets a wrong
answer. The defect is the claim, which conflicts with the repo rule that a test
must fail for the reason it states.

Found while confirming this: `SeedCatalog.kt:18` makes the same false claim in
prose — "rely on the diacritic fold for berenjena". Pre-existing and in scope,
because leaving it means the next reader re-derives the same wrong belief.

**Fix:** state what the case actually exercises, in both places.

## Refuted, and worth recording

Eleven findings did not survive. Four are worth keeping because the reasoning
settles a design question rather than merely dismissing a claim.

- **`terminal_outcome` declares `cancelled`, which nothing can emit.** Refuted:
  `InferenceTelemetryEventV2` has a public constructor and its `init` does not
  constrain `terminalOutcome`, so `CANCELLED` is constructible and encodes. The
  `forAttempt` derivation cannot produce it, which is not the same claim.
- **`reportingProvenance()` should compare routes, not whole provenances.**
  Refuted, twice, independently. `emitted_fields` pins `engineId` and
  `modelVersion` as well as `route`, so an event genuinely cannot describe two
  engines that share a route. Comparing routes only would admit such a result and
  then force an arbitrary engine id into the event — the misattribution the
  design refuses. The loud refusal is correct.
- **`require(candidate in candidates)` cannot tell duplicate groups apart.**
  Refuted: the span comes from the receiver, not from the membership check. The
  duplicate-food protection is carried by the signature — the caller must name
  the group — and the `require` is a secondary sanity check on which candidate
  within that group was chosen. No candidate-side check can detect a
  wrong-receiver bug.
- **`assertTrue(rate in 0..100)` is vacuous.** Refuted on its stated grounds: the
  quoted premise is a verbatim spec quote about routing, not a claim that
  `search` never returns empty, and `ribs`/`draw` are independently pinned by
  `ReviewRegressionTest`. The narrow point stands anyway — the assertion is
  arithmetically true for every possible behaviour — so it is being tightened to
  assert the measurement against the set's own declaration. Cheap, and the repo
  rule about tests failing for a stated reason applies to this one too.
