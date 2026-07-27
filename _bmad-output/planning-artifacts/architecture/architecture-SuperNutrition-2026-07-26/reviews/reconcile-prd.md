# Architecture Input Reconciliation — PRD

Reviewed: 2026-07-26
Architecture input: `../../../prds/prd-SuperNutrition-2026-07-26/prd.md`
Target: `../ARCHITECTURE-SPINE.md`
Mode: reconciliation only; neither source was edited

## Verdict

**Not fully reconciled.** The updated spine carries most structural requirements
and resolves the major local/remote, configuration, evidence, accessibility-bridge,
and operational seams. It still drops one load-bearing product policy, reassigns
stable decision-gate IDs, weakens one model-rollback requirement without surfacing
the source conflict, and leaves offline recovery, tone/localization, assumptions,
and acceptance evidence only partially bound.

## Critical misses and contradictions

### PRD decision-gate IDs were reassigned

The PRD defines stable gate meanings:

- G-1 — Tier 1 launch sequence.
- G-2 — Nutrition Source packages.
- G-3 — Local capability and device matrix.
- G-4 — Remote gateway and provider.
- G-5 — Evaluation thresholds.
- G-6 — Correction telemetry retention.

The spine instead defines G-2 as the Evaluation Corpus and G-6 as Nutrition
Sources. The PRD's correction-telemetry retention gate is absent. This is not
cosmetic: stories citing G-2 or G-6 can wait on or satisfy the wrong decision.

The missing G-6 also weakens FR-22 and the Privacy guardrail. AD-14 permits
controlled correction telemetry “when the applicable beta consent permits,” but
does not bind the required purpose, retention/deletion period, regional handling,
or default of no server retention.

**Required reconciliation:** preserve the PRD gate IDs exactly. Restore G-2 as
Nutrition Source packages and G-6 as Correction telemetry retention. Give corpus
governance a new architecture-local identifier that cannot be confused with a PRD
gate, or fold it into the exit evidence for the appropriate evaluation gate.

Evidence: PRD lines 679–692; spine lines 314–323.

### The local-first policy did not become an architecture invariant

FR-9 requires a ready Local Engine to be attempted before remote analysis unless
the user explicitly selects another permitted route. FR-10 limits normal remote
escalation to local unavailability, Abstention, or policy-defined low Confidence,
and local failure can never imply upload consent.

AD-6 makes routing deterministic from capability, policy, consent, and attempt
token, while AD-20 freezes one configuration snapshot. Neither rule constrains
the policy to be local-first or restricts the allowed escalation triggers.
A valid-looking configuration could therefore select remote-first and still
comply with the spine.

**Required reconciliation:** amend AD-6 or add a policy AD: the bundled safe
default is local-first; remote is offered only after the FR-10 triggers or an
explicit user route choice; local failure never grants or infers consent. G-5 may
set thresholds but cannot reverse these product invariants.

Evidence: PRD FR-9/FR-10, lines 267–289; spine AD-6 and AD-20, lines 86–90 and
170–174.

### AD-22 intentionally weakens FR-24 without reconciling the PRD

FR-24 says a failing model remains inactive and the previous compatible model
remains usable. AD-22 correctly recognizes that platform-managed models may be
disabled by policy but their binaries cannot be restored by the application.
That technical correction is honest, but it contradicts the unconditional PRD
requirement.

**Required reconciliation:** update the upstream FR-24 consequence to distinguish
app-managed and platform-managed models, or record the conflict as an explicit
open source decision. The spine must not silently claim full FR-24 coverage while
implementing weaker behavior.

Evidence: PRD FR-24, lines 446–456; spine AD-22, lines 182–186.

## High misses

### Offline manual recovery has no guaranteed catalog delivery invariant

FR-14 and FR-21 require manual Nutrition Source search when Engines are unavailable
and while offline. AD-7 and AD-19 define authoritative catalog identity and
calculation, but do not require any safe catalog package to be available offline.
AD-12 selects a package by country, while Deferred G-6 postpones source approval;
neither decides whether the minimum beta package is bundled, downloaded, or
atomically cached.

An implementation can satisfy the spine with a remotely fetched catalog and still
fail Noah's required offline journey.

**Required reconciliation:** after restoring PRD G-2, make its exit artifact bind
the minimum bundled/offline package, storage budget, schema/data migration,
atomic update/rollback, and behavior when a country package is missing.

Evidence: PRD FR-14 and FR-21, lines 328–336 and 408–415; UJ-4, lines 114–124;
spine AD-7/AD-12/AD-19 and Deferred G-6.

### Tone, externalized copy, and several accessibility constraints are overclaimed

The Capability Map says AD-4, AD-12, and AD-23 govern NFR-13..NFR-16. AD-23 binds
platform accessibility services, focus restoration, target sizes, spoken cues,
and error semantics. It does not bind:

- large-text reflow and reduced motion;
- color-independent state and WCAG 2.2 AA contrast;
- externalization of all user-visible text;
- the prohibition on language, country, decimal format, serving unit, and
  Nutrition Source in domain/storage models;
- neutral, nonjudgmental, non-medical, non-weight-shaming copy;
- the calm, minimal treatment of progress and nutrition numbers.

These are cross-team divergence points because engine capability/error codes,
shared Compose UI, native bridges, and catalog formatting can each surface copy
or semantics.

**Required reconciliation:** extend AD-23 and AD-12, or add a presentation contract
AD, covering externalized resource keys, formatting ownership, semantic state,
large text, reduced motion, contrast/color independence, and the PRD tone
guardrail. Otherwise narrow the Capability Map rather than claiming coverage.

Evidence: PRD NFR-13..NFR-16, lines 606–619; spine AD-12/AD-23 and Capability Map
line 310.

### The consent/cancellation boundary is not preserved completely

The PRD distinguishes:

- revocation before transfer begins;
- cancellation after transfer begins, which stops downstream processing/result
  delivery where possible;
- an explicit prohibition on claiming that already-sent bytes can be recalled.

AD-8 says transfer begins after consent and AD-18 makes transfer-start effects
at-most-once, but neither carries the post-transfer honesty and best-effort
downstream-cancellation rule. AD-8's deletion-on-cancellation wording can be
misread as guaranteeing remote recall.

**Required reconciliation:** bind consent revocation to the pre-transfer state and
bind post-transfer cancellation to best-effort gateway/provider cancellation,
result suppression, local cleanup, and plain disclosure that transmitted bytes
cannot be recalled.

Evidence: PRD FR-10, lines 278–289; spine AD-8/AD-18, lines 98–102 and 158–162.

### Photo preparation does not explicitly exclude EXIF from remote payloads

AD-22 fixes normalization, color space, dimensions, taxonomy, and golden parity.
AD-8 protects image lifecycle. Neither explicitly requires orientation correction
and removal of EXIF/unrelated metadata before the Remote Engine obtains upload
content. `PhotoContentSource` even supplies “normalized bytes and metadata,” so
two adapters can expose different metadata sets.

**Required reconciliation:** make the normalization profile specify orientation,
allowed metadata, EXIF stripping, remote size/MIME limits, and whether the
content digest is computed before or after sanitization.

Evidence: PRD FR-4, lines 200–209; spine AD-4/AD-22.

## Requirement coverage

| PRD area | Reconciliation judgment |
| --- | --- |
| FR-1..FR-4 Capture and preparation | **Partial.** Camera/picker boundaries, leases, native accessibility, and preprocessing parity landed. Explicit EXIF stripping and orientation behavior did not. |
| FR-5..FR-8 Contract and Engines | **Covered structurally.** AD-2, AD-3, AD-13, AD-17, AD-21, and canonical fixtures provide independent local/remote seams. Capability-state details remain code seed owned by `photo-inference-api`. |
| FR-9..FR-12 Orchestration | **Partial.** Determinism, attempts, cancellation, provenance, and idempotent effects landed. Local-first ordering, allowed escalation triggers, and the complete pre/post-transfer cancellation distinction did not. |
| FR-13..FR-17 Confirmation and grounding | **Mostly covered.** AD-7/AD-19 fix authoritative deterministic calculation and prevent model numbers. The safety rule requiring Abstention or an explicit question for hidden-ingredient/portion/allergen ambiguity is not stated as an invariant. |
| FR-18..FR-21 Results and recovery | **Partial.** State/result provenance and cleanup landed. Offline manual-search availability is not guaranteed; neutral/non-medical presentation is not bound. |
| FR-22..FR-23 Diagnostics and corpus | **Partial.** Versioned allowlisted telemetry and an evaluation harness landed. PRD G-6 retention governance is missing, and required report slices/metrics are not all named in the telemetry/evidence contract. |
| FR-24 Model parity | **Contradicted in part.** Preprocessing and parity fixtures landed, but platform-managed rollback weakens the PRD's unconditional previous-model requirement. |
| FR-25 Configuration | **Covered structurally.** Signed snapshots, precedence, provenance, environment binding, operator ownership, and rollback are present. |

## NFR coverage

| PRD area | Reconciliation judgment |
| --- | --- |
| NFR-1..NFR-3 Performance/resources | **Covered as a gate, but hypotheses were dropped.** AD-11/AD-16 and G-3 require physical-device evidence, yet the two-, four-, and ten-second spike hypotheses are not preserved as benchmark inputs. |
| NFR-4..NFR-8 Privacy/security | **Mostly covered.** Logging, secrets, TLS, configuration integrity, gateway ownership, and operations landed. EXIF sanitization and post-transfer cancellation honesty remain incomplete. |
| NFR-9..NFR-12 Reliability/compatibility | **Covered structurally.** Contract fixtures, device evidence, toolchain proof, effect idempotence, update versioning, and honest model rollback are present, subject to the FR-24 source conflict. |
| NFR-13..NFR-16 Accessibility/localization/tone | **Partial and overclaimed.** Native bridge semantics landed; large text, reduced motion, contrast/color rules, full copy externalization, formatting boundaries, and tone did not. |
| NFR-17..NFR-19 Testing/maintainability | **Covered.** Independent modules, 100% deterministic coverage intent, physical-device evidence, bounded agents, and atomic toolchain/version changes landed. “Where tooling can measure it” remains a weaker formulation than the PRD and should require explicit evidence for exclusions. |

## Metric reconciliation

| Metric | Landed? | Miss |
| --- | --- | --- |
| SM-1 | **Partial** | The state machine supports the path, but no invariant or acceptance artifact preserves the two-primary-action budget. |
| SM-2 / SM-3 | **Partial** | G-3 captures eventual budgets, but the 2s/4s/10s hypotheses are absent as spike inputs. |
| SM-4 | **Yes** | AD-7 and AD-19 bind 100% numeric provenance and deterministic serving calculation. |
| SM-5 | **Mostly** | AD-8/AD-21 bind consent and no sensitive transfer; a zero-image-network assertion for local-only sessions should be explicit in the route acceptance evidence. |
| SM-6 | **Partial** | Corpus governance exists under the wrong G-2 ID. The 300–500+ starting assumption, leakage dimensions, weighed ground truth, and full report metric set are not all preserved. |
| SM-7 | **Yes** | AD-11, AD-17, and AD-22 bind shared contract/parity fixtures and real-device evidence. |
| SM-8 | **Yes as Deferred** | G-5 blocks public activation/claims pending thresholds. |
| SM-9 | **Partial** | AD-11 requires route-by-platform evidence but does not enumerate consent decline, offline, unavailable Engine, manual recovery, and cancellation as mandatory matrix cases. |
| SM-10 | **Yes as Deferred** | G-5 requires hybrid comparison and a materiality threshold. |
| SM-C1 | **Partial** | Calibration/Abstention exist, but the counter-pressure against accepting uncertain Candidates is not an explicit routing/evaluation rule. |
| SM-C2 | **Yes** | G-3, AD-11, and AD-23 cover resource and accessibility evidence. |
| SM-C3 | **Mostly** | AD-8, AD-13, AD-14, AD-21, and AD-24 cover privacy, dependence, cost, and operations; per-accepted-result cost including retries/rejections should be explicit in telemetry. |

## Assumptions, gates, open questions, and scope

| PRD input | Spine status |
| --- | --- |
| A-1 reference cohort: US, CA, UK, AU, NZ | **Not carried.** AD-12 binds a reference cohort abstractly and G-1 defers launch countries, but the five-country beta/corpus anchor is absent. |
| A-2 account-free, no meal history | **Not explicit.** The seed contains no account/history modules and gateway auth is installation-scoped, but no rule prevents stories from adding user identity or meal-result persistence. |
| A-3 300–500+ consented weighed meals | **Not carried.** Deferred corpus governance omits the benchmark-size assumption. |
| A-4 2-second camera hypothesis | **Not carried as a G-3 input.** |
| A-5 4-second local / 10-second remote hypotheses | **Not carried as G-3 inputs.** |
| G-1 launch sequence | **Present with compatible meaning.** |
| G-2 Nutrition Source packages | **ID contradicted:** architecture uses G-2 for corpus governance. |
| G-3 local capability/device matrix | **Present with compatible meaning.** |
| G-4 remote gateway/provider | **Present with compatible meaning.** |
| G-5 evaluation thresholds | **Present, narrowed toward routing calibration; ensure it also owns all public quality/resource/cost thresholds.** |
| G-6 correction telemetry retention | **Missing:** architecture reuses G-6 for Nutrition Sources. |
| Open questions 1–5 | **Represented through G-1/G-3/G-4/G-5 and nutrition-source Deferred, subject to gate-ID repair.** |
| Open question 6 correction retention | **Not represented after G-6 drift.** |
| Open question 7 working name/trademark | **Intentionally outside the technical spine; no architecture divergence.** |

The following release boundaries are also not explicit in the spine:

- no accounts, persistent meal history, diary, coaching, monetization, barcode,
  label OCR, voice, or video in the first release;
- a generic VLM/device foundation model must not become the sole identification
  path;
- free provider tiers are development aids, not launch capacity;
- separate Android and iOS local stories/PRs are required even if one owner
  delivers both.

Most can remain PRD-only scope constraints if every downstream story cites the
PRD. The account/history boundary, “not one generic VLM” constraint, free-tier
operations constraint, and Android/iOS ownership split should be repeated in the
implementation guide or an architecture convention because they affect module,
identity, provider, and ownership decisions.

## Reconciliation actions before finalization

1. Restore PRD G-2 and G-6 meanings and preserve every stable source ID.
2. Bind local-first routing and the FR-10 escalation/consent triggers.
3. Resolve the FR-24 platform-managed rollback conflict upstream or flag it
   explicitly.
4. Guarantee a minimum offline catalog/manual-search path through repaired G-2.
5. Carry the complete NFR-13..NFR-16 presentation contract or stop claiming full
   coverage.
6. Add EXIF/orientation sanitization and the pre/post-transfer cancellation
   boundary.
7. Preserve A-1 through A-5 as named gate inputs, even while they remain
   assumptions.
8. Make SM-1, SM-6, SM-9, and cost-per-accepted-result acceptance evidence
   explicit.

## Follow-up resolution for PR #5 inline review

The original reconciliation snapshot above predates the final inline-review
follow-up. The four remaining planning gaps are now resolved in the binding
artifacts:

- AD-6, PRD FR-9/FR-10, and the orchestration story constrain post-local remote
  escalation to local unavailability, Local Engine Abstention, or a Gate G-5
  low-confidence result, with a typed eligibility reason.
- AD-8, AR-21/AR-22, FR-4, and the photo-preparation story require EXIF/GPS and
  non-essential metadata scrubbing before the consent/digest boundary.
- AD-12, AD-20, AR-26, the country configuration section, and the UX flow keep
  `CatalogCountry`, `MarketJurisdiction`, and `ProcessingRegion` independent.
- `epics.md` now contains six real epics and 21 bounded stories; the BMAD
  placeholder block has been replaced by a requirements coverage map and
  delivery order.
