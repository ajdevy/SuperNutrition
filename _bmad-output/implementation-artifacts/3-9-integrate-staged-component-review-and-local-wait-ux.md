---
baseline_commit: d1357b4f
story_key: 3-9-integrate-staged-component-review-and-local-wait-ux
---

# Story 3.9: Integrate staged component evidence and local wait UX

Status: done

## Story

As a user,
I want the app to explain a longer local analysis and show me the evidence it
found,
so that I can trust, correct, or cancel the result.

## Acceptance Criteria

1. Given a local component attempt, when work changes stage, the shared
   photo-analysis surface shows only factual `Preparing photo`, `Finding visible
   foods`, `Estimating portions`, and `Checking results` state copy, with one
   accessible Cancel action and no countdown, simulated completion, remote offer,
   or calorie/nutrition value.
2. Given a matching active `RequestId` and live photo lease, when a v1 dish
   result and v2 component evidence arrive, the sole domain reducer produces one
   typed Candidate-review boundary. v1 candidates remain ranked main-dish
   proposals; v2 `VisibleComponent` instances remain separately visible `From
   this photo` evidence with confidence, ambiguity, region, mass status/range,
   and provenance. A dish label is never decomposed into ingredients.
3. Given a v2 abstention/failure, no-component result, stale request, replaced
   photo, cancelled attempt, expired lease, or process-restored opaque snapshot,
   the reducer follows the typed recovery/cleanup path and never renders stale
   evidence. Only a still-live in-memory background/restore may retain the
   current immutable evidence draft; it never persists a photo byte, native
   image, or correction as a preference.
4. The feature coordinator exposes the reducer `StateFlow` only. The presenter
   maps state to localizable copy and UI models; shared Compose renders state and
   sends explicit actions back through the feature. No host, composable, or
   presenter owns workflow state, resolves a catalog, fabricates grams, or starts
   remote work.
5. The evidence screen is photo-first and semantics-first: the retained photo
   stays in the same surface; main-dish choices are compact text; visible
   components use stable locale-neutral `UiAutomationId`s and expose localized
   name, `From this photo`, confidence/ambiguity, selected/unselected state,
   and mass status without relying on colour. E3.9 renders evidence only;
   E5.1 remains the owner of selection/correction and E5.3 remains the owner of
   serving, catalog grounding, and nutrient calculation.
6. Common reducer/presenter/Compose semantics tests cover every stage, matching
   and stale completion, candidate/component source separation, v2
   abstention/failure/no-component recovery, cancellation/replacement/scan-again,
   in-memory background restore, process-snapshot recovery, and no-calorie/no-
   catalog/no-remote invariants. Android connected semantics and manual device
   evidence record the available capture, Cancel, recovery, and lifecycle
   surface. Staged/review device-result evidence is deferred to the Epic 3 G3
   exit gate until a runtime is approved; optional iOS simulator/physical
   records remain explicit and are never claimed from JVM evidence.

## Scope and sequencing

This story consumes the public v1/v2 contract vocabulary released by E3.5 and
defines the typed result-to-review handoff needed by E5.1. It does not choose a
concrete component runtime/model, download a corpus, make a G-3 claim, add paid
Remote consent, or calculate nutrition. E3.7/E3.8 remain the runtime spikes;
E5.1 owns user selections; E5.2/E5.3 own catalog/serving/nutrient work; E5.4
owns dish-composition suggestions.

The production composition boundary is deliberately explicit: E3.9 exposes a
host lifecycle seam through `PhotoAnalysisAnalysisLauncher`, but Android and iOS
default it to `PhotoAnalysisAnalysisLauncher.None`. No production runtime is
composed until a selected model has a valid `ComponentModelManifest` with
`APP_MANAGED` ownership, a rollback target, and non-empty product,
engineering, legal, corpus, benchmark, and rollback evidence references.
E3.9 implementation therefore closes at the typed coordinator/UI and lifecycle
seam. Production runtime composition and physical staged-result evidence remain
Epic 3 G3 exit gates owned by the selected E3.7/E3.8 runtime and approval bundle;
keeping both hosts at `None` until that gate passes is intentional and is not an
E3.9 implementation failure.

## Current Epic 3 handover correction (2026-08-11)

The composition paragraph above records the pre-release state. Owner ruling D1
now makes the PoC the current local production runtime: Android supplies the
`AndroidPocRuntimeFactory`, while iOS supplies the shared `IosPocInference`
bridge. The runtime is therefore available in the running app, but the signed
G-3 quality/release gate remains open and must not be inferred from this wiring
or from host/UI evidence.

## Tasks / Subtasks

- [x] Define the typed staged-result and review boundary in `domain/photo-analysis` (AC: 1–3)
  - [x] Write red common tests for accepted stage updates, matching completion,
    v1/v2 source separation, stale/expired rejection, cleanup, and restoration.
  - [x] Extend the sealed reducer state/event/effect vocabulary; reuse
    `CandidateReviewDraft`, `InferenceCandidate`, and `VisibleComponent` rather
    than creating a parallel evidence schema.
  - [x] Preserve request/attempt/photo-lease guards and one-terminal-result
    behavior. Keep review evidence only in a live in-memory state; opaque
    process snapshots never recreate it.
- [x] Map typed stages and evidence to presenter models and feature state flow (AC: 1, 4)
  - [x] Add localizable copy keys/host strings and presentation models for each
    bounded stage, candidate evidence, mass status, and typed recovery.
  - [x] Keep the presenter pure and the feature coordinator as the only
    `StateFlow` bridge; add no ViewModel, runtime, catalog, network, or
    persistence dependency.
- [x] Render the approved unified photo-analysis states in shared Compose (AC: 1, 4–5)
  - [x] Add the immutable presenter/UI model to the existing shared surface;
    preserve capture/import and use the retained-photo seam rather than a new
    navigation destination.
  - [x] Add stable `UiAutomationId`s, Compose semantics, and state descriptions
    for stages, evidence, mass state, and Cancel. Do not use coordinate
    selectors, colour-only state, or hard-coded ingredient suggestions.
  - [x] Keep explicit selection controls out of this task; E5.1 integrates its
    already-tested `CandidateReviewSelectionReducer` after this boundary lands.
- [x] Bind the sequenced UI evidence and test the manifest-gated seam (AC: 1–6)
  - [x] Add the request-bound `PhotoAnalysisAnalysisLauncher` seam to the
    Android and iOS preparation/cancel/background lifecycle without selecting a
    runtime or model.
  - [x] Keep production runtime composition behind the manifest activation gate
    and complete G3 evidence bundle; the current `None` bindings are the
    deliberate E3.9 completion state until that Epic 3 exit dependency is met.
    Do not use a local adapter as a substitute for production activation.
  - [x] Write red then green common reducer, presenter, feature, and Compose
    semantics tests; include no-nutrition/no-catalog/no-remote negative tests.
  - [x] Extend the feature-local robot, UI automation registry, quality scenario,
    and Maestro flow only where required by the current `photo-analysis` profile;
    preserve a single logical Android/iOS flow and no raw selectors.
  - [x] Run module JVM tests/Kover, `connectedAndroidTest`, the scoped read-only
    quality entrypoint, `git diff --check`, and `graphify update .`. Record
    Android device evidence separately; Apple execution is good-to-have.

## Developer Guardrails

### Domain and lifecycle

- `mobile/domain/photo-analysis` remains pure KMP and is the sole workflow
  mutator (AD-5). Do not import Compose, Android/Apple APIs, Koin, Ktor,
  SQLDelight, a model runtime, catalog, or test code.
- `CandidateReviewDraft` is the only v1/v2 aggregation seam. It caps v1 dishes
  to three, keeps v2 IDs unique, and has no catalog, serving, nutrition, recipe,
  hidden-ingredient, mask, raw-photo, or persistent-correction field.
- Stages are closed factual states. Do not derive percentage, timer, estimated
  duration, health statement, or remote escalation from them.
- A completion must bind the active `RequestId`, attempt, lease, and session.
  Replacement, cancellation, Scan again, expiry, and late results discard the
  draft and prevent a later callback from reopening review.
- Backgrounding may keep the immutable draft only in an active in-memory state
  while the lease/session is valid. `PhotoAnalysisBackgroundSnapshot` and the
  Android/iOS process-restoration codec stay opaque; an opaque Candidate-review
  snapshot restores to typed recovery and requires a fresh analysis.

### Presentation and UX

- Compose is a dumb renderer: collect the feature `StateFlow`, render a typed
  presentation model, and route actions to the existing feature reducer seam.
  Do not put a second state owner in `remember`/`rememberSaveable`.
- Reuse `PhotoAnalysisPresenter`, `PhotoAnalysisCaptureContent`, and the shared
  `PhotoAnalysisCaptureScreen` file as the product surface. It becomes the
  unified photo-analysis surface; do not add a second candidate-review route.
- The approved flow is Capture → retained photo → factual wait stages → visible
  evidence. Text dish names and icon evidence remain minimal over the retained
  photo. Serving is later optional edit-and-return; no calorie total or
  health-effect claim belongs here.
- UI semantics are accessibility and test API. Interactive/icon evidence must
  expose localized name, source, confidence/ambiguity, selected state, and mass
  status. `UiAutomationId`s are stable locale-neutral snake_case identifiers in
  `core/ui`; labels stay localized.

### Interaction and safety attestation

- The analysis state is automatic local UI work: 0 extra taps, 0 fields, 0
  choices, 0 text entry, and one Cancel recovery action. Candidate selection is
  not auto-advanced or auto-confirmed by E3.9.
- Free/default flow has no Remote consent, upgrade prompt, transfer, or network
  fallback. Medical/allergen conclusions, calorie totals, catalog binding,
  serving values, and model-generated nutrition are prohibited.

### Testing and evidence

- New production branches need 100% line and branch coverage; module/product
  floors remain at least 85%. No baselines, exclusions, wildcard stability
  configuration, or optional coverage flags.
- Use the existing feature-local Compose v2 robot/semantics pattern and named
  `UiAutomationId`s. Tests assert semantic state, source separation, and action
  routing, never coordinates or pixels.
- Official Android guidance confirms Compose semantics serve both accessibility
  and UI tests; use semantic labels/state descriptions and test them. Do not add
  `ui-test-junit4-accessibility` unless a separately approved dependency change
  is needed. [Source: Android Developers Compose semantics/testing,
  https://developer.android.com/develop/ui/compose/accessibility/semantics]
- JVM results do not establish Android/iOS device evidence. Do not claim a
  detector, grams accuracy, runtime/model result, or G-3 closure from fixture UI
  coverage.

## Existing code to preserve and extend

| Path | Preserve | Required E3.9 action |
| --- | --- | --- |
| `mobile/domain/photo-analysis/.../PhotoAnalysisState.kt` | Sealed state/event and opaque snapshots | Add only typed stages/result-review evidence; retain lease/request guards. |
| `PhotoAnalysisReducerTransitions.kt` / `PhotoAnalysisReducerLifecycle.kt` | Stale-result rejection, revoke effects, lifecycle opacity | Accept matching evidence once; discard late/replaced/cancelled evidence and fail closed on process restoration. |
| `CandidateReviewSelection.kt` | Pure E5.1 selection model | Reuse `CandidateReviewDraft`; do not integrate selection actions yet. |
| `mobile/domain/inference/.../{InferenceModels,InferenceV2Models}.kt` | Immutable v1/v2 evidence contracts | Consume values only; do not modify v1/v2 schemas. |
| `feature/photo-analysis/PhotoAnalysisFeature.kt` | Sole `StateFlow` bridge | Expose reducer state only; no presentation-side mutation. |
| `feature/photo-analysis-presenter/PhotoAnalysisPresenter.kt` | Pure copy/state mapping | Add factual stage/evidence models and localizable copy. |
| `feature/photo-analysis-ui/PhotoAnalysisCaptureScreen.kt` | Shared Android/iOS Compose surface | Render approved in-place states and semantics; no second route. |
| `core/ui/UiAutomationId.kt` | Stable cross-platform selector registry | Add only required photo-analysis semantic IDs and update its registry test. |

## References

- [Source: `_bmad-output/planning-artifacts/epics.md`, Story E3.9 and E5.1]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`, AD-4, AD-5, AD-28, AD-34, AD-42]
- [Source: `_bmad-output/planning-artifacts/prds/prd-SuperNutrition-2026-07-26/prd.md`, FR-32..FR-35 and NFR-28]
- [Source: `_bmad-output/planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/{DESIGN,EXPERIENCE}.md`, unified photo-analysis flow and state patterns]
- [Source: `_bmad-output/implementation-artifacts/3-5-release-v2-visible-component-and-mass-evidence-contract.md`]
- [Source: `_bmad-output/implementation-artifacts/5-1-implement-candidate-review-and-correction.md`]
- [Source: `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/{PhotoAnalysisState,PhotoAnalysisReducer,PhotoAnalysisReducerTransitions,PhotoAnalysisReducerLifecycle,CandidateReviewSelection}.kt`]
- [Source: `mobile/feature/photo-analysis{,-presenter,-ui}/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/`]
- [Android Compose semantics documentation](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- [Android Compose semantics testing documentation](https://developer.android.com/develop/ui/compose/testing/semantics)

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Completion Notes List

- Implemented the typed local-analysis handoff: factual wait stages, a single
  request/lease-guarded `CandidateReviewDraft`, separately ranked v1 dish
  proposals and v2 `VisibleComponent` evidence, and fail-closed recovery for
  stale, expired, cancelled, replaced, no-component, and process-restored
  cases. A live in-memory background restore may retain the immutable draft.
- Added a pure localizable presenter mapping, Android/iOS copy keys, shared
  Compose evidence rendering, stable automation IDs, and semantic descriptions.
  Components remain read-only and unselected in E3.9; no runtime, catalog,
  nutrition, remote initiation, consent, or fabricated grams was added.
- The retained-photo slot now fills the shared surface as a backdrop. Native
  renderers supply the display copy and overlay text contrast; shared Compose
  adds only a small opposing shadow and never decodes or inspects photo pixels.
- Added an injective, locale-neutral `UiAutomationId` encoder for opaque v2
  component IDs and attached one stable semantic node per visible component;
  labels, source, confidence, ambiguity, region, mass, provenance, and
  unselected state remain localized/read-only evidence.
- Added red/green Compose and feature-robot coverage for all three factual local
  wait stages, one Cancel action, no nutrition output, and candidate/component
  semantics. The current profile remains capture/recovery, so no unsupported
  staged Maestro scenario was added.
- Added `PhotoAnalysisAnalysisCoordinator` as the request-bound integration seam:
  it emits the factual local stages, invokes the v1 dish and v2 component ports
  independently, combines only typed evidence into `CandidateReviewDraft`, and
  recovers fail-closed for v2 abstention/failure, modality mismatch, stale, and
  cancelled attempts. The seam is covered by focused JVM tests, but no concrete
  selected v2 runtime exists yet.
- Added `PhotoAnalysisAnalysisLauncher` as the host composition seam. Android
  and iOS invoke its start/cancel hooks around preparation, replacement,
  Scan-again, backgrounding, and recovery, while the current binding remains
  explicit `None`. The launcher is covered with coordinator-backed fake-port
  tests; no production model, manifest, or local adapter is connected.
- The unconnected binding is intentional and policy-correct: the feature host
  does not depend on the production inference module, and no `production`
  `ComponentModelManifest` can be fabricated before its required approval,
  corpus, benchmark, legal, and rollback references exist.
- Latest local proof for this seam: `PhotoAnalysisAnalysisLauncherTest` 2/2,
  feature JVM tests 11/11, Android main compilation passed, iOS arm64
  compilation passed, `qualityCheckJvmAndroid` passed, and `qualityCheckApple`
  passed. These results do not establish physical staged-runtime evidence.
- On 2026-08-02 the current dirty worktree was exercised on physical Honor
  DNY-NX9 through `PHYSICAL_DEVICE_ENDPOINT`: the isolated local-Android adapter
  passed `4/4`, production packaged acceptance passed `15/15`, and
  `uiAcceptance` passed `15/15`, all with zero failures, errors, or skips.
  This is capture/adapter correctness evidence only; the launcher still binds
  `None`, so no staged v2 result was claimed.
- Verified focused JVM tests for all five affected modules, scoped Detekt, and
  Kover XML reports. The current `qualityCoverageJvm` gate passed at 97.36% line
  / 85.78% branch
  across eight JVM production modules. With ADB mDNS auto-connect disabled,
  physical Huawei DNY-NX9 USB evidence passed production `15/15` and
  `uiAcceptance` `15/15`; both reports show zero failures/errors/skips.
  `graphify update .` and `git diff --check` passed.
- On the connected physical iPhone 14 Pro (iOS 26.5.2), the current
  `iosArm64` binary-bound fixture runner passed `5/5` (3 normalization, 2
  bridge) with exit code 0, including the normalized display-preview dimensions.
  Earlier Xcode attempts stopped before tests while CoreDevice reported the
  device as `Connecting`; after the tunnel settled, the same physical Xcode
  acceptance run passed `5/5` with zero failures/skips.
- Emitted the local-only physical iOS evidence bundle at
  `mobile/apps/iosApp/build/physical-ios-evidence-20260801`; it binds the
  passing xcresult, fixture XML, arm64 framework, device, and dirty-worktree
  state. It is correctness evidence only because unrelated user WIP remains
  in the worktree.
- Re-ran the physical Apple lane against the current dirty worktree after the
  contract/coordinator changes: Xcode UI acceptance passed `5/5` with zero
  failures/skips on iPhone 14 Pro (iOS 26.5.2), the binary-bound arm64 fixture
  runner passed `5/5` (3 normalization, 2 bridge), and the fresh local-only
  bundle is `mobile/apps/iosApp/build/physical-ios-evidence-20260801-current`.
- Bound the iOS normalizer's bounded/oriented display image to the shared
  Compose retained-photo slot through `UIImageView`; the display copy is
  process-ephemeral and is cleared on cancellation, recovery, Scan again,
  backgrounding, and replacement.
- Android now decodes a bounded 1080px preview only from the normalized
  in-memory photo input, samples its luminance for text contrast, and renders
  it in the shared backdrop slot. The preview is tied to its `PhotoRef`, is not
  persisted, and is released on cancellation, replacement, terminal recovery,
  or `ViewModel` cleanup.
- Verified: focused main/test Detekt tasks; JVM tests for core UI, domain,
  presenter, shared UI, and feature; Android compilation; `git diff --check`.
  On physical Huawei DNY-NX9, a real CameraX capture remained as the full
  photo backdrop with legible light overlay text on the dark frame; Cancel
  returned to the plain surface and cleared the retained photo. The Android
  connected acceptance suite passed 15/15 on both USB and Wi-Fi ADB transports.
  This covers capture/backdrop cleanup only, not staged runtime output or
  recognition quality.
- Remaining before production G3 can be approved: select and approve an actual
  production runtime/model, satisfy every `ComponentModelManifest` production
  reference, compose it through the launcher seam, and capture Android and
  optional iOS evidence for the new stage/review states. The current physical
  acceptance evidence covers capture, picker, backdrop, and lifecycle behavior
  only. The current full `qualityCheckJvmAndroid` and `qualityCheckApple` gates
  are green; the remaining blocker is concrete runtime/model and staged-device
  evidence, not static quality.
- A fresh physical Xcode rerun on the currently paired iPhone reached signing
  but stopped before tests because no provisioning profile exists for the
  SuperNutrition acceptance app/test runner and Xcode has no authenticated
  account for team `6U74HTSWH3`. The earlier recorded 5/5 physical acceptance
  result remains valid evidence; this environment cannot refresh it until the
  Apple account/profile is restored.
- E3.9 is now implementation-complete under the approved pre-release scope.
  Its production binding is intentionally deferred: `ComponentModelManifest` rejects
  `production` activation without all required approval/evidence refs, so no
  placeholder manifest or fake runtime was introduced. The remaining physical
  staged/review result evidence is tracked at the Epic 3 G3 exit gate.

### File List

- `_bmad-output/implementation-artifacts/3-9-integrate-staged-component-review-and-local-wait-ux.md`
- `mobile/core/ui/src/commonMain/kotlin/com/supernutrition/core/ui/UiAutomationId.kt`
- `mobile/core/ui/src/commonTest/kotlin/com/supernutrition/core/ui/UiAutomationIdTest.kt`
- `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/{PhotoAnalysisState,PhotoAnalysisReducer,PhotoAnalysisReducerTransitions,PhotoAnalysisAnalysisTransitions,PhotoAnalysisReducerLifecycle}.kt`
- `mobile/domain/photo-analysis/src/commonTest/kotlin/com/supernutrition/domain/photoanalysis/StagedComponentReviewTest.kt`
- `mobile/feature/photo-analysis-presenter/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisPresenter.kt`
- `mobile/feature/photo-analysis-presenter/src/commonTest/kotlin/com/supernutrition/feature/photoanalysis/{PhotoAnalysisPresenterCopyFixtures,PhotoAnalysisPresenterReviewTest}.kt`
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisCaptureScreen.kt`
- `mobile/feature/photo-analysis-ui/src/commonTest/kotlin/com/supernutrition/feature/photoanalysis/{PhotoAnalysisCaptureScreenComposeTest,PhotoAnalysisPresenterCopyFixtures}.kt`
- `mobile/feature/photo-analysis/src/{androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt,androidMain/res/values/strings.xml,commonTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisPresenterCopyFixtures.kt}`
- `mobile/feature/photo-analysis/src/commonTest/kotlin/com/supernutrition/feature/photoanalysis/{PhotoAnalysisCaptureRobot,PhotoAnalysisCaptureRobotTest}.kt`
- `mobile/feature/photo-analysis/src/{commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisAnalysisCoordinator.kt,commonTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisAnalysisCoordinatorTest.kt}`
- `mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/{AndroidRetainedPhotoBackdrop,AndroidRetainedPhotoPreviewStore,PhotoAnalysisAndroidHostController}.kt`
- `mobile/apps/sharedKit/src/iosMain/kotlin/com/supernutrition/sharedkit/IosPhotoAnalysisCopy.kt`
- `mobile/apps/sharedKit/src/iosMain/kotlin/com/supernutrition/sharedkit/SharedKitPhotoAnalysisSession.kt`
- `mobile/apps/sharedKit/src/{iosArm64Main,iosSimulatorArm64Main}/kotlin/com/supernutrition/sharedkit/SharedKitPhotoAnalysis.kt`
- `mobile/feature/photo-analysis/src/iosMain/kotlin/com/supernutrition/feature/photoanalysis/{IosRetainedPhotoPreview,PhotoAnalysisViewController}.kt`
- `mobile/platform/photo-ios/src/iosMain/kotlin/com/supernutrition/platform/photoios/IosImageNormalizer.kt`
- `mobile/platform/photo-ios/src/iosTest/kotlin/com/supernutrition/platform/photoios/IosImageNormalizerFixtureTest.kt`
- `mobile/apps/iosApp/Localizable.strings`

### Change Log

- 2026-08-01 — Created implementation-ready E3.9 context and moved the story
  from backlog to ready-for-dev.
- 2026-08-01 — Implemented and verified the typed staged component-review
  handoff and shared evidence UI boundary; kept the story in progress pending
  runtime/photo-renderer wiring and new Android/iOS state evidence.
- 2026-08-01 — Changed the unified surface so a supplied retained photo is the
  backdrop and added a host-owned light/dark text-contrast contract without a
  gradient.
- 2026-08-01 — Wired Android capture/import output to a bounded, ephemeral
  retained-photo backdrop and assembled the Android acceptance APK. Manual
  Huawei DNY-NX9 CameraX evidence confirmed the backdrop and Cancel cleanup;
  Android acceptance passed 15/15 on USB and Wi-Fi ADB transports.
- 2026-08-01 — Bound opaque component IDs and local-wait/review semantics to
  the shared surface and feature robot; added no-nutrition Compose coverage and
  reran isolated physical USB Android evidence after suppressing the duplicate
  mDNS transport.
- 2026-08-01 — Bound the iOS normalized display image to the shared retained-
  photo surface, added fixture coverage for the display dimensions, and reran
  the physical iPhone fixture runner at 5/5. Xcode UI automation remained
  temporarily blocked while CoreDevice reported `Connecting`; after the
  device tunnel settled, the physical Xcode acceptance rerun passed 5/5 and a
  local-only evidence bundle was emitted.
- 2026-08-02 — Clarified E3.9 closure: the coordinator/UI/lifecycle seam is
  complete under the approved pre-release scope; production runtime activation
  and physical staged-result evidence remain Epic 3 G3 exit gates because the
  production manifest requires
  the complete approval, corpus, benchmark, and rollback reference bundle.

### Review Findings (adversarial PR review, 2026-08-09) — status updated 2026-08-11

- [x] [Review][Decision] Background cancellation/revocation is prohibited: `onStop` dispatches `Backgrounded` and restores the active analysis without discarding the photo.
- [x] [Review][Patch] The launcher and Android host preserve analysis through backgrounding instead of reporting `CANCELLED` and revoking the lease.
- [x] [Review][Patch] Completed `CandidateReview` restores from a typed review snapshot rather than misreporting `CAPTURE_FAILED`.
- [x] [Review][Patch] Local model failures map to `ANALYSIS_FAILED`, not `OFFLINE`, on the common coordinator and iOS native bridge.
- [x] [Review][Patch] Dish failure or abstention surfaces typed partial-degradation state instead of silently presenting a components-only review.
- [x] [Review][Patch] Native evidence records were downgraded to pending when their runs predated the capture-surface rewrite; current iOS fixture execution remains separate from complete XCTest evidence.
