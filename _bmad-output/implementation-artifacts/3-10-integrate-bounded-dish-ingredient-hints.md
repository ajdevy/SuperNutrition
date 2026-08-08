---
baseline_commit: 666b69d937745958b1d328bd579fd4c0e7d53d1c
story_key: 3-10-integrate-bounded-dish-ingredient-hints
---

# Story 3.10: Integrate bounded dish-ingredient hints

Status: done

## Story

As a person reviewing a photo,
I want possible ingredients from the dish guess to be shown separately from
what the visual detector found,
so that useful suggestions survive an imperfect dish classification without
being presented as facts about the photo.

## Acceptance Criteria

1. Given ranked dish candidates and typed v2 `VisibleComponent` evidence, the
   bounded fusion policy preserves the dish candidate order and every visual
   component. A dish prior may add ranking support and suggestions, but it may
   not veto, delete, relabel, or create a `VisibleComponent`.
2. Given a supported dish label, the policy emits a bounded, de-duplicated,
   deterministically ordered list of `DishIngredientHint` values from the
   versioned in-app prior table. Each hint carries a stable ingredient ID,
   display label, bounded support score, and the dish candidate IDs that
   contributed to it. Unknown dish labels emit no hints and do not fail the
   photo review.
3. `CandidateReviewDraft` carries composition hints as a separate field with
   constructor invariants. Hints contain no region, mass, nutrition, catalog
   binding, correction, persistence, or medical/allergen claim.
4. The shared photo-analysis presenter and Compose surface show three explicit
   lanes: dish candidates, `Detected in photo` visual evidence, and `Possible
   ingredients from dish` hints. The full bounded hint list is rendered for
   review/debug; a hint is never labelled `From this photo`.
5. The coordinator applies the policy after both engine results arrive while
   retaining request, attempt, lease, cancellation, and stale-result guards.
   The current manifest-gated Android/iOS runtime binding remains explicit
   `None`; this story must not fabricate a production model runtime.
6. Domain, presenter, Compose semantics, and coordinator tests cover: prior
   support accumulation, duplicate merging, deterministic ordering, unknown
   dishes, visual-evidence preservation under a mismatched dish, UI lane
   separation, and no nutrition/catalog output. Existing Epic 3 quality and
   device capture gates remain green.

## Scope and sequencing

This story consumes the existing v1 dish and v2 visible-component contracts
and the E3.9 review seam. It implements the safe late-fusion policy and its
read-only presentation. It does not activate a native model, change
`ComponentModelManifest`, add a catalog lookup, calculate nutrition, persist
corrections, or claim G3 detector accuracy. Runtime activation remains owned by
the approved component-model/G3 exit work.

## Tasks / Subtasks

- [x] Define and test the pure bounded fusion policy in `domain/photo-analysis`
  - [x] Add the immutable hint model and prior table with bounded invariants.
  - [x] Add red tests for support accumulation, duplicate merging, order,
    unknown labels, and preservation of all visual components.
  - [x] Implement the minimal policy and keep it independent of Compose,
    model runtimes, catalogs, and persistence.
- [x] Extend the review draft and coordinator
  - [x] Carry composition hints as a distinct draft field.
  - [x] Apply the policy only after valid dish and component results.
  - [x] Preserve all request/attempt/lease/stale/cancellation behavior.
- [x] Present the separated evidence lanes
  - [x] Add localizable copy and presenter UI models for dish candidates,
    visual evidence, and dish-prior ingredient hints.
  - [x] Render all hint entries with source and semantics distinct from
    visual evidence; add a stable automation ID for the hint lane.
  - [x] Add Compose semantics tests for the three lanes and the no-nutrition
    invariant.
- [x] Verify and document
  - [x] Run focused domain/presenter/feature/UI tests, quality checks, and
    `git diff --check`.
  - [x] Run `graphify update .` after source edits.
  - [x] Add the short bounded-fusion HTML explainer and presentation index
    entry under `_bmad-output/presentations/`.
  - [x] Record that production runtime activation remains manifest/G3-gated.

## Developer Guardrails

- `VisibleComponent` is visual evidence only. Never turn a dish prior into a
  component, region, mass estimate, or nutrition item.
- Bounded fusion is additive and recoverable: hints may be wrong, so source
  labels must remain visible and the original evidence list must be intact.
- Keep domain logic pure KMP. Compose remains a dumb renderer; all state and
  transformations belong in domain/presenter models.
- Do not change v1/v2 inference contracts or create a parallel production
  evidence-state enum.
- Do not bind a fake, fixture, or research-only runtime into Android/iOS
  production hosts to make the screen look populated.

## References

- `_bmad-output/implementation-artifacts/3-9-integrate-staged-component-review-and-local-wait-ux.md`
- `_bmad-output/planning-artifacts/research/suggestion-oriented-fusion-and-evaluation-research-2026-08-03.md`
- `_bmad-output/planning-artifacts/research/review-uncertainty-aware-dish-fusion.md`
- `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md` (AD-5, AD-17, AD-34)

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Implementation Plan

Implement a pure prior table and bounded late-fusion function first, then
thread its additive hints through the existing CandidateReviewDraft,
presenter, and shared Compose surface. Keep Android/iOS model activation at the
existing manifest-gated `None` seam and verify each layer independently.

### Completion Notes List

- `BoundedDishIngredientFusion` uses `e0-suggestion-policy.v1`, preserves every
  `VisibleComponent`, and emits at most 24 separate composition hints from the
  top three dish candidates.
- The fusion policy caps main-dish proposals at the three-item
  `CandidateReviewDraft` contract and keeps the Android photo picker grant
  persistable for the result reader.
- The coordinator, presenter, Compose lane, Android copy, iOS copy, automation
  IDs, and tests are wired additively. Hints retain a source label and never
  become visual evidence, regions, mass, nutrition, catalog, or medical data.
- `qualityCheckJvmAndroid`: PASS; JVM line coverage 98.45%, branch coverage
  87.64%. `qualityCheckApple`: PASS; local policy validated and Apple evidence
  remains good-to-have.
- Physical Honor DNY-NX9 evidence is green: production and UI-acceptance
  instrumentation each passed 16/16 with 0 failures, 0 errors, and 0 skipped.
- The explicit `poc` Android flavor reaches the review-candidate screen with
  separate dish, component, and composition-hint lanes. Its Food-101/YOLO
  adapter remains research-only; production runtime activation is unchanged.
- `graphify update .` and `git diff --check`: PASS. The standalone explainer
  is indexed in `_bmad-output/presentations/README.md`.
- Android/iOS production runtime activation remains the existing explicit
  manifest-gated `None` seam; this story does not fabricate model output.

### File List

- `_bmad-output/implementation-artifacts/3-10-integrate-bounded-dish-ingredient-hints.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `_bmad-output/presentations/README.md`
- `_bmad-output/presentations/bounded-fusion-explainer/index.html`
- `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/BoundedDishIngredientFusion.kt`
- `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/CandidateReviewSelection.kt`
- `mobile/domain/photo-analysis/src/commonTest/kotlin/com/supernutrition/domain/photoanalysis/BoundedDishIngredientFusionTest.kt`
- `mobile/feature/photo-analysis/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisAnalysisCoordinator.kt`
- `mobile/feature/photo-analysis-presenter/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisPresenter.kt`
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisCaptureScreen.kt`
- Android/iOS localized copy, automation IDs, and focused presenter/coordinator/
  Compose tests listed in the PR diff.

### Change Log

- 2026-08-03: Created story for the bounded dish-ingredient hint slice.
- 2026-08-03: Implemented additive bounded fusion, separated UI hint lane,
  BMAD presentation index, host quality evidence, and bounded Android
  research-only review path; physical Android production/UI evidence passed.
