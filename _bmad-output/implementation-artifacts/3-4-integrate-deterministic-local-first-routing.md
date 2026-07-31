---
baseline_commit: 84fc6d7e2128b01a45a9b7cec8b37755d04120e6
---

# Story 3.4: Integrate deterministic local-first routing

Status: in-progress

## Story

As a product owner,
I want local-first routing with bounded escalation,
so that remote analysis is offered only for an approved typed reason and never
silently uploads a photo.

## Acceptance Criteria

1. Routing is pure and owned by `mobile/domain/photo-analysis`; it consumes
   capability snapshots, a closed route policy, explicit attempt route/consent,
   local result evidence, and an attempt token. It does not import a local or
   remote adapter implementation.
2. With no explicit alternate route, a ready local engine is selected first.
   A user-selected remote route is represented as an explicit choice and still
   requires an explicit remote-consent state before transfer.
3. After local execution, remote eligibility is closed to exactly
   `LOCAL_UNAVAILABLE`, `LOCAL_ABSTENTION`, or a validated Gate G-5
   `LOCAL_LOW_CONFIDENCE` decision. A successful high-confidence local result
   cannot be escalated by arbitrary configuration.
4. Every remote offer carries a typed eligibility reason, policy/configuration
   digest, attempt ID, and monotonic decision ID. A declined remote route is
   not re-offered in the same attempt unless explicitly requested.
5. One attempt has at most one active engine and one terminal outcome. A
   replacement, cancellation, or stale token invalidates prior decisions and
   late engine results cannot reopen the route.
6. Unit tests cover local-first selection, explicit remote consent, every
   allowed/forbidden escalation trigger, G-5 absence, high-confidence terminal
   local results, duplicate/stale decisions, cancellation, and no-network
   behavior. No UI or interaction-budget policy changes are made.

## Tasks / Subtasks

- [x] Define the closed `RoutePolicy`, explicit consent/selection states,
  typed `RemoteEligibilityReason`, decision IDs, and immutable policy snapshot
  in `mobile/domain/photo-analysis`.
- [x] Implement a pure local-first router that consumes only the shared
  `PhotoInferenceEngine` contract and cannot invent a remote trigger or
  silently grant consent.
- [x] Integrate attempt-token/cancellation and stale-decision invalidation with
  the existing photo-analysis reducer/ports without adding a second workflow
  state owner.
- [x] Add deterministic tests first and implementation second for all route
  branches, terminal-result ownership, high-confidence non-escalation, and
  explicit consent semantics.
- [x] Run all domain/inference tests, policy/coverage/quality gates, graphify,
  and available Android/native evidence lanes. Android physical acceptance is
  15/15 and the signed iOS acceptance/fixture lanes are 5/5 each; these are
  platform correctness evidence, while the signed G-3 runtime/model benchmark
  remains the production gate.

## Dev Notes

### Authoritative rules

- AD-5 and AD-6 in the architecture spine make `domain/photo-analysis` the
  sole routing/orchestration owner. `domain/inference` stays contract-only;
  local Android, local iOS, and remote modules remain independent.
- Before G-5 exists, use a conservative named policy such as `LocalOnly` or
  `ManualConfirmation`; do not invent a confidence cutoff. A typed low-
  confidence result may be represented as unavailable policy evidence, but it
  must not authorize escalation until G-5 validates the threshold.
- Remote transfer and consent are modeled only as decision/evidence states in
  this story. E4 owns the full transfer transport lifecycle; this story must
  not add Ktor, provider, gateway, upload, or consent UI dependencies.
- Configuration cannot add arbitrary triggers. Allowed post-local triggers are
  local unavailability, abstention, or validated G-5 low confidence. A
  successful high-confidence local result is terminal for this route.
- Attempt ID, request/configuration digest, policy digest, and decision ID are
  immutable evidence. All stale/replacement/cancellation checks fail closed.

### Existing code to preserve

- Extend the existing reducer/ports in
  `mobile/domain/photo-analysis/src/commonMain`; do not duplicate
  `PhotoSessionStorePort`, `PhotoLeaseStorePort`, `PhotoAnalysisState`, or
  configuration snapshot models.
- Reuse `PhotoInferenceEngine`, `EngineCapability`, `InferenceOutcome`,
  `InferenceProvenance`, and `Confidence` from `mobile/domain/inference`.
- No numeric nutrition, catalog selection, remote DTO, UI state, or provider
  routing belongs in this story.

### Validation and evidence

- Preserve the repository's 85% line/branch module and product floors and
  100% changed-code rule. Run read-only quality entrypoints; formatting is
  separate from validation.
- Physical Android/iOS evidence cannot be replaced by JVM, emulator, or
  simulator output. Bind successful device records to commit, artifact,
  fixture/configuration digests, device/OS, task version, and UTC execution.

## Interaction-budget attestation

Non-UI story. The route policy does not alter tap, scroll, field, choice,
consent, default, or navigation budgets.

## References

- [Source: `_bmad-output/planning-artifacts/epics.md`, Story E3.4]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`, AD-5, AD-6, AD-8, AD-11, G-5]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/solution-design.md`, section 9 Routing]
- [Source: `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducer.kt`]
- [Source: `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/InferenceModels.kt`]

## Dev Agent Record

### Agent Model Used

GPT-5 / Codex

### Debug Log References

### Completion Notes List

- Added a pure `LocalFirstRouter` with explicit remote consent, typed local
  unavailability/abstention/G-5 low-confidence triggers, immutable policy
  digest evidence, and local-only terminal behavior for unrelated failures.
- Added generation/attempt token coordination that rejects replacement,
  cancellation, foreign-attempt, and stale decision emissions.
- Domain tests and Kover pass at 91.7073% line and 85.1301% branch coverage;
  policy validation and graphify update pass. No remote DTO, transport, UI, or
  interaction-policy code was added.
- 2026-07-31 physical correctness rerun on code commit `5f6c8772`:
  Android local adapter `4/4`, Android UI/capture `15/15`, iOS Xcode
  acceptance `5/5`, and iOS arm64 bridge/normalization fixtures `5/5` all
  passed on the paired physical devices. These records do not activate a
  concrete local runtime or close the signed G-3 ingredient/weight gate.
- 2026-08-01 rerun on the unchanged production code from `5f6c8772` repeated
  all four physical-device correctness lanes successfully: Android `4/4` plus
  `15/15`, and iOS `5/5` plus `5/5`. The hosted check remains independent and
  is not used as evidence because GitHub billing prevented any CI step from
  starting.

### File List

- `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/LocalFirstRouting.kt`
- `mobile/domain/photo-analysis/src/commonTest/kotlin/com/supernutrition/domain/photoanalysis/LocalFirstRoutingTest.kt`
- `mobile/domain/photo-analysis/src/commonTest/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisDomainTest.kt`
- `mobile/config/quality/module-matrix.toml`
- `mobile/config/quality/coverage.toml`

### Change Log

- 2026-07-31: Created implementation-ready E3.4 context with closed trigger,
  consent, stale-attempt, and G-5 guardrails.

### Review Findings

- [x] [Review][Patch] Remote execution now requires a validated, attempt-bound
  consent receipt containing photo/configuration/policy/region/purpose/copy
  evidence; bare `GRANTED` state cannot emit `RunRemote`
  [mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/LocalFirstRouting.kt].
- [x] [Review][Patch] G-5 low-confidence escalation is fail-closed behind an
  injected evidence verifier; an arbitrary nonblank evidence ID is insufficient
  [mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/LocalFirstRouting.kt].
- [x] [Review][Patch] Route tokens now bind request/configuration/photo
  identity, decision IDs include attempt identity, and the coordinator consumes
  revisions while enforcing one active-engine/terminal state machine
  [mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/LocalFirstRouting.kt].
- [x] [Review][Patch] Route attempt invalidation is connected to the existing
  reducer lifecycle and exposed through the photo-analysis feature boundary
  [mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducer.kt].
- [x] [Review][Defer] Concrete Android/iOS production wiring, G-3 model/runtime
  selection, and platform privacy/network inference evidence remain deferred by
  the signed-gate constraints; the Android/iOS device records in this branch
  cover host/bridge correctness, not a production inference benchmark.
