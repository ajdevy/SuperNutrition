---
baseline_commit: d1357b4f
story_key: 3-8-spike-local-ios-visible-component-and-mass-inference
---

# Story 3.8: Spike local iOS visible-component and mass inference

Status: done

## Story

As the iOS local-inference owner,
I want the same v2 component/mass evidence on iOS,
so that iOS is not a semantic-only fork.

## Acceptance Criteria

1. iOS consumes the same immutable component-model manifest and v2 fixtures
   as Android. Runtime/EP, calibration, compatibility, activation, and
   rollback provenance remain explicit and digest-bound.
2. With the selected cleared artifact and a valid normalized photo, iOS emits
   parity-equivalent v2 evidence or a typed abstention/failure without remote
   transfer. Text and dish-only results cannot acquire component grams.
3. Depth availability, preprocessing/output parity, cancellation, low-memory
   behavior, runtime release, checksum activation, and rollback failure are
   covered by deterministic tests and native fixture evidence.
4. The selected model is benchmarked on the physical iPhone device using the
   consented weighed corpus with component/mass quality slices and separate
   latency, resident memory, thermal, battery, privacy, and rollback evidence.

## Scope and sequencing

This story owns the independent iOS adapter and device benchmark. It does not
turn the existing Food-101 semantic harness, bridge fixtures, MobileSAM masks,
YOLO-World detector, or public-photo screen into component/mass acceptance.
E3.6 owns the consented weighed corpus; E3.7 owns Android; E3.9 owns staged
wait/review UX; E5 owns catalog/serving/nutrition. Under the approved 2026-08-02
post-release scope amendment, selected model activation and physical
component/mass G-3 evidence are post-release gates; the pre-release story
closes at the independent adapter and fail-closed handoff.

## Tasks / Subtasks

- [x] Consume the shared immutable component-model manifest gate (AC: 1, 3)
  - [x] Reuse the pure KMP manifest and reject unapproved/invalid activation
    before any iOS runtime invocation.
  - [x] Require production manifests to bind legal clearance, a consented
    corpus, benchmark evidence, and rollback evidence in addition to Product
    and Engineering approvals.
- [x] Consume the shared atomic activation/rollback and production-manifest
  gate (AC: 1–3)
  - [x] Preserve the active model on failed install/rollback and reject
    research-only or staged manifests before iOS photo access.
- [x] Define the iOS v2 runtime/output port (AC: 2–3)
  - [x] Keep `UIImage`, Core ML/ONNX objects, and photo bytes in the iOS
    adapter; expose only the v2 contract to common code.
  - [x] Map component/mass outputs with cancellation, release, parity, and
    typed abstention/failure semantics.
- [x] Record selection/legal clearance of the shared artifact, calibration, and
  iOS-specific depth/EP compatibility as a post-release G-3 handoff; no
  production artifact is selected in the pre-release scope (AC: 1, 4).
- [x] Record the consented physical iOS G-3 benchmark and resource, quality,
  privacy, and rollback evidence as deferred post-release work, separately from
  bridge/UI tests; no component/mass quality claim is made now (AC: 4).

## Developer Guardrails

- `mobile/inference/local-ios` remains independent from Android and remote
  adapters. App composition selects the runtime; common domain stays pure.
- Do not claim component/mass parity from the current 5/5 Xcode acceptance or
  arm64 normalization/bridge fixtures; those tests do not run a v2 model.
- No model-generated nutrition, catalog binding, or exact/measured grams may
  enter the v2 result. Unsupported depth must remain explicit.
- Physical iOS evidence must use the paired iPhone and a consented weighed
  corpus; simulator output is not device proof.

## References

- [Source: `_bmad-output/planning-artifacts/epics.md`, Story E3.8]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`, AD-3, AD-9, AD-11, AD-22, AD-42]
- [Source: `_bmad-output/implementation-artifacts/3-6-establish-consented-weighed-component-corpus.md`]
- [Source: `_bmad-output/implementation-artifacts/g3-benchmark-artifact.md`]
- [Source: `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/ComponentModelManifest.kt`]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Completion Notes List

- Added and tested the shared component-model manifest gate.
- The shared production gate now requires legal, consented-corpus, benchmark,
  and rollback-evidence refs; missing evidence remains fail-closed on iOS.
- Added the shared atomic activation/rollback controller and made the iOS v2
  adapter fail closed before photo access for non-production ready manifests.
- Added and tested the iOS v2 adapter seam with photo lease/content-digest
  validation, typed text abstention, generation cancellation, runtime release,
  provenance mapping, and fail-closed malformed output handling.
- iOS runtime/model selection and physical v2 G-3 evidence remain open; this
  story is intentionally not complete until those artifacts exist.

### Scope amendment note

The iOS manifest/activation reuse and independent v2 adapter are complete for
pre-release. The selected production artifact, consented corpus, and physical
component/mass benchmark remain required before `activation = production`.

### File List

- `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/ComponentModelManifest.kt`
- `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/ComponentModelActivation.kt`
- `mobile/domain/inference/src/commonTest/kotlin/com/supernutrition/domain/inference/ComponentModelManifestTest.kt`
- `mobile/domain/inference/src/commonTest/kotlin/com/supernutrition/domain/inference/ComponentModelActivationTest.kt`
- `mobile/inference/local-ios/src/commonMain/kotlin/com/supernutrition/inference/localios/LocalIosComponentInference.kt`
- `mobile/inference/local-ios/src/commonTest/kotlin/com/supernutrition/inference/localios/LocalIosComponentInferenceTest.kt`
