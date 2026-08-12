---
baseline_commit: d1357b4f
story_key: 5-1-implement-candidate-review-and-correction
---

# Story 5.1: Implement minimal candidate review and correction

Status: in-progress

## Story

As a user,
I want to explicitly confirm a proposed main dish and only the ingredients I
recognize,
so that I remain the authority over what the photo means.

## Acceptance Criteria

1. A review input exposes at most three distinct, ranked v1 main-dish proposals.
   No dish is selected by default; a no-match/manual-search path remains
   available. A v1 dish proposal never produces an ingredient.
2. A v2 `VisibleComponent` is represented only as separately selectable
   `From this photo` evidence. It begins unselected, retains its source,
   confidence, ambiguity, region, and honest mass state, and is reversible for
   the active analysis session. Invalid or stale component IDs do nothing.
3. An explicit confirmation returns only the user's selected dish and selected
   photo-evidence component IDs. It creates no catalog binding, serving,
   nutrition, measured-weight, recipe, or hidden-ingredient claim.
4. Replacing, cancelling, or scanning again clears all review selections with
   the photo attempt. Background/restore preserves only the active in-memory
   review draft when its leased photo remains valid; it never persists photo
   bytes, native images, or a user correction as a future preference.
5. The candidate-review visual implementation is photo-first: dish proposals
   are compact text, photo evidence is tappable icon controls, and the user
   receives one visible confirm action plus manual search. Icon semantics must
   include localized name, `From this photo`, confidence/ambiguity, and selected
   state; a non-colour marker distinguishes evidence from suggestions.
6. Selecting a dish may update only a separately sourced, initially unselected
   add-ingredient suggestion grid. The baseline must not hard-code a dish-to-
   ingredient table, infer ingredients from a dish label, call a model, or claim
   a personalized ranking. E5.4 owns the versioned/licensed dish-composition
   source; the MVP has no persisted `Most used by you` history.
7. All changed production branches have 100% unit line and branch coverage; the
   module/product floors remain at least 85%. Tests prove the no-preselection,
   no-decomposition, reversible-selection, invalid-ID, stale-attempt, and
   no-nutrition invariants. Visual tests are added only after UX-A1 is signed.

## Gate and delivery split

- **Allowed now — state and contract work:** Add pure KMP review data and a
  deterministic reducer/controller in `mobile/domain/photo-analysis`, plus
  common JVM tests. It receives immutable v1 proposal and v2 component evidence
  but does not select a runtime or send data.
- **Blocked — visual Compose work:** Do not change
  `mobile/feature/photo-analysis-ui/**`, Android/iOS hosts, automation IDs,
  Maestro flows, or the irreversible `photo-analysis` quality profile until
  Product/Design signs UX-A1 and accessibility/responsive mocks for the single
  photo-analysis surface (Capture → retained photo → Candidate review →
  in-place nutrient profile) and the optional Serving editor. Paid Remote
  consent is deferred and must not be part of the free/default visual slice.
  The prior five-mock acceptance was superseded by the current nutrient-first
  revision.
- **Blocked — data-backed suggestions:** E5.4 must first publish the
  versioned/licensed, country-aware dish-composition reference and provenance
  contract. A future source can populate the icon grid only after explicit dish
  selection; every item remains unselected and visibly distinct from photo
  evidence.

## Tasks / Subtasks

- [x] Add a pure candidate-review value model and deterministic selection reducer (AC: 1–4, 6)
  - [x] Place the model beside the existing reducer in
    `mobile/domain/photo-analysis/src/commonMain/.../photoanalysis/`; keep it
    KMP-only and depend only on `domain/inference` values already exposed by the
    module.
  - [x] Preserve v1 `InferenceCandidate` as a main-dish proposal only and cap
    input to three distinct candidate IDs/ranks. Do not copy or loosen v1
    invariants.
  - [x] Preserve v2 `VisibleComponent` as immutable photo evidence; do not turn
    its label into a catalog record, recipe ingredient, or mass fact.
  - [x] Model selection by stable candidate/component IDs, keep all selections
    initially empty, and ignore unknown IDs deterministically.
  - [x] Expose confirmation only as explicit user selection data for a later
    E5.3 boundary. Do not import `data/nutrition`, catalog, serving, Compose,
    Koin, Ktor, SQLDelight, Android, or Apple APIs.
- [ ] Integrate the pure review state at the photo-analysis reducer seam (AC: 2–4)
  - [ ] Extend the existing `PhotoAnalysisState.CandidateReview` and lifecycle
    snapshot only after E3.9 supplies a versioned result-to-review boundary.
    Preserve attempt/request/photo lease validation and the stale-result guards
    in `PhotoAnalysisReducerTransitions`.
  - [ ] Route selection events through the sole domain reducer. The feature
    coordinator only exposes its `StateFlow`; it never resolves dish suggestions
    or mutates selection state directly.
  - [ ] Do not replace `AnalysisSucceeded` with an untyped result payload or
    bypass E3.9's staged/evidence ownership.
- [ ] Keep the visual/data boundaries explicit (AC: 5–6)
  - [x] Treat the photo-first HTML mock and `EXPERIENCE.md` as behavioral draft
    evidence, not implementation authorization.
  - [ ] When UX-A1 is signed, create a separate visual sub-slice/PR that uses
    Compose semantic state and stable `UiAutomationId`s; no coordinate selectors
    or colour-only source/selection/confidence state.
  - [ ] When E5.4 is ready, inject a separately provenanced suggestion source.
    It may update the unselected offer set after a dish choice but must preserve
    a food the person already selected. Do not add a local hard-coded mapping or
    a persisted user-history ranking in this story.
- [ ] Test and verify (AC: 1–7)
  - [x] Write red common tests before the model/reducer implementation for
    empty selection, three-proposal bound, duplicate/unknown IDs, v1
    non-decomposition, v2-only photo evidence, reversible toggle, confirmation,
    replacement/cancel cleanup, and restore guards.
  - [x] Run the domain JVM test and Kover report, then the affected feature
    common tests. Do not claim Android/iOS visual or device evidence from JVM
    results.
  - [ ] Run the read-only local quality command appropriate to changed
    inventories, `git diff --check`, and `graphify update .` after source edits.

## Developer Guardrails

### Existing implementation to extend

| Path | Current behavior | Required preservation |
| --- | --- | --- |
| `mobile/domain/photo-analysis/.../PhotoAnalysisState.kt` | Candidate review currently carries only leased `PhotoRef` and `RequestId`; snapshots are opaque. | Keep the photo lease/attempt boundary and never persist photo data or user corrections. |
| `mobile/domain/photo-analysis/.../PhotoAnalysisReducer*.kt` | The sole workflow reducer rejects stale request IDs, revokes on replacement/cancel, and restores only live leases/session input. | New review actions must be pure, deterministic, request/attempt scoped, and must not weaken cleanup or stale-result guards. |
| `mobile/domain/inference/.../InferenceModels.kt` | v1 candidates are ranked dish proposals with confidence/assumptions/provenance. | Reuse as dishes only; never derive components or catalog bindings. |
| `mobile/domain/inference/.../InferenceV2Models.kt` | v2 `VisibleComponent` has calibrated confidence, ambiguity, optional region, mass evidence, and provenance. | Retain values as evidence. `estimated` is an editable range, and `not-evaluable`/`abstained` are not zero grams. |
| `mobile/feature/photo-analysis/.../PhotoAnalysisFeature.kt` | Exposes reducer state through `StateFlow`; host/UI dispatch events. | Do not make the feature or hosts an alternate state owner. |
| `mobile/feature/photo-analysis-ui/**` | Current shared Compose capture surface is the visual product shell. | No changes before UX-A1/full mock approval. |

### Product and safety constraints

- No preselected uncertain identity, ingredient, recipe, serving, mass, catalog,
  nutrition, consent, or user-history choice.
- No recipe decomposition from a dish candidate. `VisibleComponent` evidence is
  the only photo-ingredient source; its provenance is not recipe/catalog
  provenance.
- No model-generated calories/macros, no catalog lookup, and no nutrition
  calculation. E5.3 owns serving/grounding after explicit confirmation.
- Do not collect, log, persist, or rank prior corrections. E5.4/another approved
  personalization story must define consent, retention, provenance, and deletion
  before any `Most used by you` behavior.
- All user-visible copy/localization stays outside domain values. Future icons
  use semantics to expose meaning/state rather than visible ingredient text only.

### Technical requirements

- Keep `mobile/domain/photo-analysis` pure KMP. Existing dependencies are only
  `:core:config` and API `:domain:inference`; add no new dependency or version.
- Immutable state and closed domain events/effects follow AD-5. Constructor
  validation rejects malformed review data; unsupported actions are ignored
  deterministically without an exception or effect.
- The review model must retain source separation: `From this photo`, future
  `Also common with this dish`, and future `Recipe reference` cannot share a
  source enum/value or default semantics.
- Compose semantics are a later visual requirement: an icon needs a localized
  content description and state description, and Compose semantics remain the
  test surface as well as the assistive-technology surface.

### Testing and evidence

- `mobile/domain/photo-analysis/src/commonTest` is the first test home. Use
  `kotlin.test`; do not add Android/iOS framework tests for pure state logic.
- Run `./gradlew :domain:photo-analysis:jvmTest
  :domain:photo-analysis:koverXmlReportJvm` and the affected feature JVM tests.
  The exact coverage report must retain a denominator for every new production
  file; do not add a baseline, exclusion, optional flag, or wildcard stability
  configuration.
- The visual acceptance portion later requires shared Compose semantics tests,
  Android `connectedAndroidTest`, and the profile/journey/automation inventory
  update. It does not permit replacing a real device evidence requirement with
  an emulator/simulator claim.

## References

- [Source: `_bmad-output/planning-artifacts/epics.md`, Story E5.1 and E5.4]
- [Source: `_bmad-output/planning-artifacts/sprint-change-proposal-minimal-confirmation-amendment-2026-08-01.md`, ownership and UX gate]
- [Source: `_bmad-output/planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/EXPERIENCE.md`, Candidate review confirmation envelope and Add-ingredient tray]
- [Source: `_bmad-output/planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/.working/key-candidate-review.html`, provisional photo-first draft]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`, AD-5, AD-28, AD-34, AD-42, UX-A1]
- [Source: `_bmad-output/planning-artifacts/prds/prd-SuperNutrition-2026-07-26/prd.md`, FR-13..FR-18 and NFR-12..NFR-17]
- [Source: `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/{PhotoAnalysisState,PhotoAnalysisReducer,PhotoAnalysisReducerTransitions,PhotoAnalysisReducerLifecycle}.kt`]
- [Source: `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/{InferenceModels,InferenceV2Models}.kt`]
- [Android Compose semantics documentation](https://developer.android.com/develop/ui/compose/accessibility/semantics)

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Completion Notes List

- Story context created after the E5.1 minimal-confirmation amendment and the
  provisional photo-first candidate-review mock approval.
- The source inspection found that `CandidateReview` is a current reducer state
  with no evidence/selection payload. The safe first implementation is therefore
  a pure, tested state model; visual rendering and data-backed dish suggestions
  are explicitly gated.
- Latest official Compose documentation confirms that semantic descriptions and
  state are the accessible/testable meaning of icon controls; no dependency
  upgrade is authorized.
- Implemented the allowed pure KMP slice in `CandidateReviewSelection.kt`:
  separate v1 dish proposals and v2 photo evidence, no default selection,
  deterministic reversible ID selection, and confirmation that contains stable
  IDs only. The new production file is 100% line and branch covered by the
  domain JVM Kover report.
- Passed: focused and full `:domain:photo-analysis:jvmTest`,
  `:domain:photo-analysis:koverXmlReportJvm`, and all four photo-analysis
  source-set Detekt tasks. `:feature:photo-analysis:jvmTest` also ran before
  the repository-wide quality gate reached unrelated failures.
- `qualityCheckJvmAndroid` remains red because the pre-existing v2 inference
  files have 34 common-main and 200 common-test Detekt violations. This story
  does not edit them. `graphify update .` refreshed the code graph to 13,595
  nodes and 14,952 edges; its zero-node warning is for unsupported/non-code
  artifacts rather than the new Kotlin source.
- Product owner Anastasia approved the unified photo-analysis and optional
  Serving editor mock set on 2026-08-01, clearing the UX-A1/mock condition for
  the visual sub-slice. E5.1 still cannot integrate or render its review state
  until backlog Story E3.9 publishes the typed result-to-review boundary.

### Change Log

- 2026-08-01 — Added the pure Candidate review selection state/test slice;
  integration is waiting for E3.9. Product approval of the unified
  photo-analysis and optional Serving editor mock set cleared the visual gate;
  E3.9's typed result-to-review boundary is now available for review. The
  production-bound runtime and physical staged-result evidence remain Epic 3
  G3 exit dependencies, not a reason to fabricate a manifest or connect a
  non-production adapter in E5.1.

### File List

- `_bmad-output/implementation-artifacts/5-1-implement-candidate-review-and-correction.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/CandidateReviewSelection.kt`
- `mobile/domain/photo-analysis/src/commonTest/kotlin/com/supernutrition/domain/photoanalysis/CandidateReviewSelectionTest.kt`
- `_bmad-output/planning-artifacts/epics.md`
- `_bmad-output/planning-artifacts/prds/prd-SuperNutrition-2026-07-26/prd.md`
- `_bmad-output/planning-artifacts/sprint-change-proposal-local-default-nutrient-first-2026-08-01.md`
- `_bmad-output/planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/DESIGN.md`
- `_bmad-output/planning-artifacts/sprint-change-proposal-minimal-confirmation-amendment-2026-08-01.md`
- `_bmad-output/planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/EXPERIENCE.md`
- `_bmad-output/planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/review-ux.md`
- `_bmad-output/planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/.memlog.md`
- `_bmad-output/planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/.working/key-candidate-review.html`

### Review Findings (adversarial PR review, 2026-08-09) — resolved 2026-08-11

- [x] [Review][Patch] An empty candidate draft transitions to typed `ReviewEvidenceUnavailable` recovery instead of creating an unconfirmable review state.
