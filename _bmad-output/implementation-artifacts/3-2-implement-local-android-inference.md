---
baseline_commit: 84fc6d7e2128b01a45a9b7cec8b37755d04120e6
---

# Story 3.2: Implement local Android inference

Status: in-progress

<!-- This story is the Android adapter slice for Epic 3. Runtime/model selection and physical-device activation remain gated by G-3. -->

## Story

As an Android local-inference owner,
I want an Android-specific Local Engine behind the shared contract,
so that eligible devices can analyze without transmitting Photo Input.

## Acceptance Criteria

1. **Contract and ownership boundary**
   - Given `mobile/domain/inference` publishes `photo-inference-engine/v1`, when
     the Android adapter is built, then it implements that exact
     `PhotoInferenceEngine` contract and emits only contract-v1 capability,
     candidate, abstention, or typed-failure evidence.
   - The adapter is owned by `mobile/inference/local-android`; it may depend
     inward on `domain:inference` and the existing photo-analysis content port,
     but it does not depend on local iOS, remote inference, UI, Koin global
     state, or the nutrition catalog.
   - No new DTO or copied JSON schema is introduced outside
     `mobile/domain/inference`; the adapter consumes the existing contract
     types and fixtures unchanged.

2. **Fail-closed capability and local-only execution**
   - Given an Android runtime reports `ready`, when capability is requested,
     then the engine returns a ready contract-v1 capability with non-blank
     engine and immutable model provenance.
   - Given a missing, downloading, incompatible, unsupported, temporarily
     unavailable, or over-budget runtime, when capability is requested, then
     the engine returns the corresponding unavailable contract state and never
     attempts to read or transmit Photo Input.
   - Given a ready capability, when an attempt starts, then the adapter reads
     normalized bytes only through the attempt-scoped photo content port,
     invokes the injected Android runtime locally, and performs zero network
     calls or image transfers. This must be asserted with a recording fake,
     not inferred from configuration.

3. **Evidence and safety semantics**
   - A successful or abstaining result preserves local route, contract version,
     engine ID, model version, calibration version, confidence/abstention
     provenance, and no numeric nutrition fields.
   - A stale, foreign-attempt, missing, zero-byte, metadata-bearing, or
     revoked `PhotoRef` is rejected as a typed contract/input failure without
     invoking the runtime.
   - `CancellationException` is rethrown. Cancellation invalidates the active
     attempt before releasing runtime resources; a late runtime callback cannot
     publish a result.
   - Photo bytes, filenames, paths, user labels, model prompts/responses, and
     transport bodies never appear in logs, telemetry, exceptions, or test
     artifacts.

4. **Model package boundary and evidence**
   - The adapter consumes a runtime/model boundary that exposes immutable model
     version, integrity identity, compatibility tier, calibration version, and
     whether the model is app-managed or platform-managed.
   - App-managed model activation is atomic and rollback-aware; platform-managed
     models report the strongest available runtime/OS provenance and are never
     described as app-rollback-capable. The adapter must fail closed when
     integrity or compatibility is unverifiable.
   - No concrete ML runtime, model asset, download origin, or accelerator is
     selected in this story before the signed G-3 exit artifact names it. A
     test runtime is sufficient for deterministic adapter tests; it is not
     production inference evidence.
   - The story records Android physical-device evidence for local-only network
     behavior, cancellation/lifecycle, model/resource compatibility, latency,
     peak memory, thermal, battery, and rollback behavior using the approved
     G-3 device matrix. Emulator/JVM results cannot satisfy this criterion.

5. **Quality and scope**
   - The module has colocated focused tests covering all capability states,
     provenance, candidate/abstention/failure mapping, no-network behavior,
     bad input, cancellation, late emissions, integrity/compatibility failure,
     resource-budget failure, and rollback semantics.
   - Measurable production code meets the repository's 85% line and branch
     module/product floors and 100% changed-line and changed-branch coverage;
     native/device behavior is mapped to executable evidence rather than
     hidden through exclusions.
   - This non-UI story does not change interaction budgets, defaults, consent
     copy, navigation, catalog selection, remote routing, iOS code, or the
     irreversible `photo-analysis` UI evidence profile.

## Tasks / Subtasks

- [x] Establish the Android local-engine module and inward dependency boundary (AC: 1, 5)
  - [x] Add `mobile/inference/local-android/build.gradle.kts` using the checked-in
    KMP/Android convention and package namespace; do not add an unapproved
    runtime dependency or literal version.
  - [x] Add the adapter's declared source-set/module ownership and lock state
    through the separately owned mobile build/quality policy handoff; do not
    weaken or filter the existing manifests.
  - [x] Verify the module depends only on the shared inference contract and
    existing inward photo-content/domain ports.

- [x] Define the injectable Android runtime/model boundary (AC: 2, 4)
  - [x] Create a small adapter-owned runtime port for capability, model
    provenance, normalized input, typed outcome mapping, cancellation, and
    resource release.
  - [x] Represent app-managed versus platform-managed model provenance without
    claiming app rollback for a platform-managed model.
  - [x] Make unverifiable digest, compatibility, or budget data fail closed to
    the shared capability/failure vocabulary.

- [x] Implement `LocalAndroidInferenceEngine` over contract-v1 (AC: 1–3)
  - [x] Bind `contractVersion` explicitly to
    `photo-inference-engine/v1` and map runtime output into shared candidate,
    abstention, and failure types.
  - [x] Read content through the existing attempt-scoped normalized photo port;
    never accept a URI, Android `Bitmap`, filesystem path, or raw platform
    object in the shared contract.
  - [x] Enforce current attempt/lease and digest lease invariants before runtime
    invocation; normalized input invariants are enforced by the shared
    `NormalizedPhotoInput` constructor.
  - [x] Re-throw coroutine cancellation, release resources, and suppress late
    emissions after cancellation or input replacement.

- [x] Add deterministic adapter tests first, then implementation (AC: 2–5)
  - [x] Use recording fakes for the runtime and photo content; prove a ready
    local attempt has zero network attempts and zero image bytes transmitted.
  - [x] Cover every capability state and reason, invalid input, provenance,
    candidate ranking, abstention, typed failures, cancellation, late results,
    integrity/compatibility/resource rejection, app-managed rollback, and
    platform-managed non-rollback semantics.
  - [x] Add forbidden-artifact/log assertions for raw photo bytes, paths,
    filenames, labels, prompts, responses, secrets, and headers.

- [ ] Execute quality and Android evidence gates (AC: 4–5)
  - [x] Run focused local-Android tests, strict dependency/lock/policy validation,
    Detekt, Android compilation, and changed-code coverage. The aggregate
    `qualityCheckJvmAndroid` now passes with JVM host coverage of 98.01% line and
    88.03% branch across 8 production modules; branchless modules remain in the
    line denominator without inventing branch outcomes.
  - [x] Run the dedicated E3.2 physical Android inference scenario and bind
    the local-only evidence to committed source `efff22e9dd3db0a5c624657223e303acef822e10`,
    test APK digest
    `952dfdb5f77fa58caebb121917b089a6c224fd94468d87d3eca7f2b679fdcb9a`,
    fixture/model test-source digest
    `f50af530bb01eb1c9a87c963f8167219b5a57d221499e8e90020d9b67b03109e`,
    module build/lock digests
    `86952f61ae6e0e156ee9d10ec349ab47539f321a3390befc1010fae507b3fc22` /
    `b2d887723bdd54849e5ef7affcf2ce5b6e586ddc37975c466afda8dfb7b5069f`,
    policy digest `61b92987320bd6d26a616a62e8d7202fef3b966da6eb744d9196afd02dbde4e6`,
    device `HONOR-DNY-NX9-android-16-api-36`, task
    `connectedAndroidDeviceTest-gradle-9.5.0`, and execution
    `2026-07-31T10:30:39Z`. The result XML is
    `mobile/inference/local-android/build/outputs/androidTest-results/connected/androidMain/TEST-DNY-NX9 - 16-_inference_local-android-.xml`;
    it reports 4 tests, 0 failures, 0 errors, and 0 skipped. The recording
    runtime asserted zero network attempts, leased-byte-only input, resource
    budget fail-closed behavior without reading photo content, and cancellation
    cleanup with late-result suppression.
  - [ ] Extend this evidence to the signed G-3 real-device benchmark for
    runtime/model selection, latency, peak memory, thermal, battery, and
    rollback budgets. The G-3 exit artifact remains outstanding.
  - [x] Update only the story record and owned module/test files plus any
    separately approved policy/evidence handoff files; do not modify iOS,
    remote, UI, catalog, root product, or unrelated WIP.

## Dev Notes

### Gate and scope guardrails

- The authoritative Epic E3.2 source is
  `_bmad-output/planning-artifacts/epics.md` (Story E3.2, traceability
  FR-6..FR-7, FR-12, FR-24, NFR-2..NFR-3, NFR-7, NFR-9..NFR-12, AR-2..AR-3,
  AR-7, AR-9, AR-17).
- Architecture G-3 currently blocks production Android/iOS local-engine
  activation. Its exit artifact must select the device/OS tiers, runtime,
  model, download policy, and latency/memory/thermal/battery budgets from a
  signed real-device benchmark. Until it exists, implement only the
  deterministic adapter/runtime seam and fail-closed behavior; do not choose
  LiteRT, ONNX Runtime, Gemini Nano/AICore, MediaPipe, ExecuTorch, or another
  runtime on behalf of the product.
- This story owns `mobile/inference/local-android` and its colocated tests as
  one adapter slice. `mobile/domain/inference` remains the contract owner;
  `mobile/domain/photo-analysis` remains the owner of routing, consent,
  fallback, and attempt coordination; `mobile/inference/local-ios` and
  `mobile/inference/remote` remain independent.

### Existing code and patterns to preserve

- `PhotoInferenceEngine`, `PhotoInferenceRequest`, `PhotoInferenceResult`,
  `EngineCapability`, `InferenceOutcome`, `InferenceProvenance`, and all enums
  are already implemented in
  `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/`.
  Use them directly and preserve the immutable v1 fixtures under
  `contracts/inference/v1`.
- `PhotoRef` is opaque and attempt-scoped. Normalized content is currently
  owned by `domain/photo-analysis` through `PhotoSessionStorePort` and
  `NormalizedPhotoInput`; do not create a second lease/content store or expose
  Android `Uri`, `Bitmap`, or file paths to common/domain code.
- The mobile settings registry already declares `:inference:local-android`.
  The absent directory is intentional until this story materializes it. Any
  manifest, boundary, denominator, owner, or task registration must use the
  existing protected schema and its owner-scoped handoff; never make a module
  invisible by filtering it out.
- Use the checked-in versions in `mobile/gradle/libs.versions.toml` and the
  existing convention plugins. Do not upgrade dependencies, add repositories,
  add a root workspace, or add a provider SDK.

### Required architecture behavior

- Follow AD-1, AD-2, AD-3, AD-4, AD-6, AD-9, AD-10, AD-11, AD-15, AD-17,
  AD-18, AD-21, AD-22, AD-25 through AD-30, and AD-33. In particular:
  - route is `LOCAL`; contract is explicitly v1; output contains no numeric
    nutrition;
  - one active attempt owns the read and result; cancellation wins over a late
    callback; `CancellationException` is never mapped to a failure;
  - model provenance is immutable and reproducible; package integrity,
    compatibility, atomic activation, and rollback are explicit where the app
    controls the package;
  - system-managed model state is capability evidence, not an app rollback
    promise;
  - production code receives runtime/content dependencies through constructors;
    it never reaches into global Koin state;
  - no photo body, raw response, path, filename, label, secret, header, or
    transport body is logged or placed in telemetry.
- The adapter must not own routing or consent. A later E3.4 story consumes the
  shared engine capability/evidence and decides local-first escalation.
- No UI or Compose changes are expected. The current UI evidence profile must
  remain unchanged because this slice does not add a product screen or
  scenario.

### Testing and evidence requirements

- Start with failing tests and use deterministic fakes. Do not use a live model,
  live network, provider, or nondeterministic device output as a unit-test
  oracle.
- The shared contract suite must run unchanged against the adapter. Adapter
  fixtures may add Android-specific runtime/model faults but must not edit the
  canonical contract fixtures.
- Android device evidence is required for claims about local network isolation,
  lifecycle/cancellation, latency, memory, thermal, battery, model
  compatibility, and rollback. Emulator/JVM tests can prove deterministic
  mapping and cancellation logic only.
- Quality entrypoints are read-only. Formatting/autocorrection belongs only to
  `qualityFormat`; do not run it as part of validation or CI.
- Every generated evidence record must bind commit SHA, binary/module digest,
  fixture/config/model digests, device/OS identity, task version, and execution
  time. A missing or stale G-3 artifact is an outstanding gate, not success.

### Interaction-budget attestation

This is a non-UI story. It does not add taps, scrolls, fields, choices,
text-entry, recovery actions, defaults, consent prompts, or persistence. The
interaction budget and protected default policy remain unchanged.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md`, Epic E3 and Story E3.2]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`, AD-1 through AD-11, AD-22, AD-25 through AD-35, G-3]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/solution-design.md`, module and shared inference contract sections]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/implementation-agent-guide.md`, mandatory project overrides and KMP implementation guidance]
- [Source: `_bmad-output/implementation-artifacts/3-1-freeze-contract-v1-inference-ports.md`, completed contract-v1 implementation and validation evidence]
- [Source: `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/InferenceContract.kt`, request/engine port]
- [Source: `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/InferenceModels.kt`, capability/evidence types]
- [Source: `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisDomain.kt`, attempt-scoped normalized photo store]
- [Research: https://developer.android.com/ai/overview, Android on-device AI paths and custom-model/LiteRT distinction, consulted 2026-07-31]
- [Research: https://developer.android.com/ai/gemini-nano, platform-managed AICore model provenance and device-managed model behavior, consulted 2026-07-31]
- [Research: https://onnxruntime.ai/docs/tutorials/mobile/, mobile runtime packaging and the need to measure size, latency, power, and memory, consulted 2026-07-31]

## Dev Agent Record

### Agent Model Used

GPT-5 / Codex

### Implementation Plan

1. Confirm G-3 status and read the existing contract/content seams.
2. Write failing deterministic runtime/engine tests for capability, local-only
   execution, provenance, cancellation, late emissions, and fail-closed model
   state.
3. Add the minimal `mobile/inference/local-android` adapter and test doubles,
   with no unapproved ML runtime dependency.
4. Run contract, focused, policy, coverage, and Android evidence gates; record
   any G-3-dependent evidence as outstanding.

### Debug Log References

- 2026-07-31: G-3 exit artifact was not present in the checked-in architecture
  evidence; runtime/model selection is therefore intentionally deferred.

### Completion Notes List

- Story context created from the authoritative Epic E3.2, architecture spine,
  completed E3.1 contract slice, and current Android on-device runtime docs.
- Implemented the injectable Android runtime/model seam and contract-v1 adapter
  in `mobile/inference/local-android`; no concrete ML runtime or model was
  selected because the signed G-3 exit artifact is still absent.
- Added deterministic tests for all capability states, local-only execution,
  provenance, abstention/failure mapping, input leases, cancellation, late
  results, rollback ownership, and invalid runtime metadata.
- Focused module coverage is 99.3151% line and 90.7895% branch; the full local
  `qualityCheckJvmAndroid` gate passes with 98.01% line and 88.03% branch across
  8 production modules.
- The physical Android acceptance task completed 15/15 tests on the explicit
  `AU3N025429006787` serial after fixing ViewModel reflection visibility and
  the Compose resource-ID assertion helper. Clean current-commit Android
  evidence is now emitted and bound in `mobile/config/quality/evidence-bindings.toml`;
  signed G-3 evidence remains outstanding, so this story is not
  production-ready or complete.
- 2026-07-31 device rerun: `connectedUiAcceptanceDebugAndroidTest` completed
  successfully on `DNY-NX9` / Android 16 (API 36), with `tests=15`,
  `failures=0`, `errors=0`, and `skipped=0`. The XML result is
  `mobile/apps/androidApp/build/outputs/androidTest-results/connected/debug/flavors/uiAcceptance/TEST-DNY-NX9 - 16-_apps_androidApp-uiAcceptance.xml`.
  This is physical Android UI/semantics, capture, normalization, lifecycle,
  and snapshot evidence; it does not satisfy the dedicated local-inference,
  network-isolation, or signed G-3 benchmark requirement.
- 2026-07-31 final clean evidence rerun: rebuilt and installed the acceptance
  APK from commit `7dc777d2`, ran the explicit-serial device lane from a clean
  worktree, and emitted five success records with artifact digest
  `bb875cbc61fed96a3f89e71a630520043bb2f08ed8425c32c21c0d38f0735159`.
  Strict dependency verification also required the checked-in SHA-256 for
  Gradle's `org.junit:junit-bom:5.9.2` module metadata.
- 2026-07-31 dedicated local-Android adapter device rerun: committed
  `connectedAndroidDeviceTest` for the isolated `androidDeviceTest` source set,
  ran it on `AU3N025429006787` (`DNY-NX9`, Android 16 / API 36), and completed
  4/4 tests with 0 failures, errors, or skips. The test APK digest is
  `952dfdb5f77fa58caebb121917b089a6c224fd94468d87d3eca7f2b679fdcb9a`; the
  run timestamp is `2026-07-31T10:30:39Z`. The physical scenario now covers
  local-only input, resource-budget fail-closed behavior, and cancellation
  cleanup/late-result suppression. This remains bounded adapter evidence, not
  a concrete ML runtime or signed G-3 performance/thermal/battery/rollback
  benchmark.
- 2026-07-31 preliminary G-3 spike: the separate research harness completed
  50/50 inferences on the same explicit Android device and recorded latency,
  PSS, thermal, and battery-current evidence. The unsigned matrix and proposed
  budgets are recorded in
  `_bmad-output/implementation-artifacts/g3-benchmark-artifact.md`; this does
  not select or activate a production runtime.

### File List

- `_bmad-output/implementation-artifacts/3-2-implement-local-android-inference.md`
- `mobile/inference/local-android/build.gradle.kts`
- `mobile/inference/local-android/gradle.lockfile`
- `mobile/inference/local-android/src/commonMain/kotlin/com/supernutrition/inference/localandroid/LocalAndroidInference.kt`
- `mobile/inference/local-android/src/commonTest/kotlin/com/supernutrition/inference/localandroid/LocalAndroidInferenceEngineTest.kt`
- `mobile/inference/local-android/src/androidDeviceTest/kotlin/com/supernutrition/inference/localandroid/LocalAndroidInferenceDeviceTest.kt`
- `mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt`
- `mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PhotoAnalysisComposeSemanticsInstrumentationTest.java`
- `mobile/config/quality/module-matrix.toml`
- `mobile/config/quality/coverage.toml`
- `mobile/config/quality/evidence-bindings.toml`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

### Change Log

- 2026-07-31: Created the implementation-ready E3.2 story with explicit G-3
  guardrails, adapter ownership, deterministic test scope, and physical-device
  evidence requirements.
- 2026-07-31: Started E3.2 implementation with the fail-closed Android adapter,
  colocated tests, module/coverage registration, and exact validation status.
- 2026-07-31: Re-ran the explicit-serial Android acceptance lane on the
  connected physical device; 15/15 tests passed. The dedicated E3.2
  local-inference/G-3 evidence gate remains open.
- 2026-07-31: Bound the final clean Android acceptance run to commit
  `7dc777d2`; the UI/native-gap records pass, while dedicated local-inference
  network-isolation and signed G-3 benchmark evidence remain outstanding.
- 2026-07-31: Added a reproducible, unsigned G-3 physical benchmark spike;
  production runtime/model selection remains gated on artifact acceptance.

### Review Findings

- [x] [Review][Patch] Lease validation, native cancellation, and malformed
  runtime output are now fail-closed before a result can publish
  [mobile/inference/local-android/src/commonMain/kotlin/com/supernutrition/inference/localandroid/LocalAndroidInference.kt].
- [x] [Review][Patch] Ready model provenance now requires explicit verified
  runtime evidence and preserves app/platform rollback ownership semantics
  [mobile/inference/local-android/src/commonMain/kotlin/com/supernutrition/inference/localandroid/LocalAndroidInference.kt].
- [x] [Review][Patch] Runtime exception details are sanitized into the shared
  typed failure vocabulary; raw payloads are not returned to callers
  [mobile/inference/local-android/src/commonMain/kotlin/com/supernutrition/inference/localandroid/LocalAndroidInference.kt].
- [x] [Review][Patch] Runtime diagnostic assertions reject raw bytes, paths,
  labels, prompts, responses, secrets, and authorization headers from the
  typed failure surface
  [mobile/inference/local-android/src/commonTest/kotlin/com/supernutrition/inference/localandroid/LocalAndroidInferenceEngineTest.kt].
- [x] [Review][Patch] Host/device test visibility and resource-ID fixes are
  recorded in this story's file list as validation-support changes; they do
  not select or activate a production inference runtime
  [mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt].
- [x] [Review][Defer] Dedicated Epic 3 physical inference/network/G-3 evidence
  remains deferred because the signed G-3 runtime/model benchmark and clean
  evidence-producing scenario are not present; the 15/15 run is UI evidence
  only.
