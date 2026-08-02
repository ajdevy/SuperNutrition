---
baseline_commit: d1357b4f
story_key: 3-7-spike-local-android-visible-component-and-mass-inference
---

# Story 3.7: Spike local Android visible-component and mass inference

Status: done

## Story

As the Android local-inference owner,
I want a compact v2 detector/segmenter and optional mass head behind the shared
contract,
so that a compatible Android phone returns components and bounded estimates
without network transfer.

## Acceptance Criteria

1. A selected commercial-use-cleared component model is represented by an
   immutable manifest with artifact/taxonomy digests, preprocessing and
   postprocessing versions, mask contract, calibration version, runtime/EP,
   compatibility tier, activation state, and rollback target. Production
   activation requires explicit Product and Engineering G-3 approval refs.
2. With a valid normalized photo and a ready manifest, Android emits v2
   `VisibleComponent` evidence with provenance, or a typed abstention/failure;
   it never invents identity or grams and never attempts network transfer.
3. Cancellation, photo lease/digest validation, low-memory gating, runtime
   release, output parity, checksum activation, and rollback failure are
   covered by deterministic tests. Invalid or non-production manifests fail
   closed before model invocation.
4. The selected model is benchmarked on the physical Honor device using the
   consented weighed corpus: component precision/recall/mask metrics, gram
   MAE/p90 and abstention slices, latency, peak memory, thermal, battery,
   privacy, and rollback evidence are recorded separately from UI correctness.

## Scope and sequencing

The v2 manifest gate and adapter seam are owned here; the actual model,
training corpus, and signed G-3 decision are not substituted by Food-101,
MobileSAM, YOLO-World COCO, public photos, or synthetic masses. E3.6 owns
consented weighed data. E3.8 owns the independent iOS implementation. E3.9
owns staged local wait and evidence review. E5 owns catalog/serving/nutrition.
Under the approved 2026-08-02 post-release scope amendment, model selection and
the physical component/mass G-3 benchmark are post-release gates; the
pre-release story closes at the manifest, activation, adapter, and fail-closed
handoff seams.

## Tasks / Subtasks

- [x] Add the shared immutable component-model manifest gate (AC: 1, 3)
  - [x] Validate artifact/taxonomy SHA-256 digests, runtime/EP, calibration,
    compatibility, activation, ownership, rollback, and approval references.
  - [x] Require production manifests to bind legal clearance, a consented
    corpus, benchmark evidence, and rollback evidence in addition to Product
    and Engineering approvals; reject those references on non-production
    manifests.
  - [x] Reject production activation without the complete evidence bundle and
    a rollback target; reject platform-managed app rollback claims.
- [x] Add the shared atomic activation/rollback controller and adapter gate
  (AC: 2–3)
  - [x] Verify before install, preserve the last active model on install or
    rollback failure, and resolve only registered production rollback targets.
  - [x] Fail closed before reading photo bytes when a runtime reports a
    research-only or staged manifest as ready.
- [x] Define the Android v2 runtime/output port (AC: 2–3)
  - [x] Keep normalized photo bytes and native runtime types adapter-owned.
  - [x] Map detector/segmenter output to v2 components or typed abstention,
    preserving cancellation and fail-closed resource behavior.
- [x] Record selection and legal clearance of the component model, artifact,
  and calibration manifest as a post-release G-3 handoff; no production model
  is selected in the pre-release scope (AC: 1, 4).
- [x] Record the consented physical Android G-3 benchmark and rollback,
  battery, thermal, latency, memory, and quality slices as deferred post-release
  work; no component/mass quality claim is made now (AC: 4).

## Developer Guardrails

- `mobile/domain/inference` remains pure KMP; Android runtime and model bytes
  belong in `mobile/inference/local-android` and app composition only.
- No production runtime may be selected from the current research-only
  Food-101, FPB, MobileSAM, or COCO-vocabulary YOLO-World artifacts.
- A `not_evaluable` mass is honest evidence, not zero grams; a dish label is
  never decomposed into ingredients. No nutrition/catalog binding is allowed.
- Physical Android evidence must use a real device and the consented weighed
  corpus; emulator output cannot close this story.

## References

- [Source: `_bmad-output/planning-artifacts/epics.md`, Story E3.7]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`, AD-3, AD-9, AD-11, AD-22, AD-42]
- [Source: `_bmad-output/implementation-artifacts/3-6-establish-consented-weighed-component-corpus.md`]
- [Source: `_bmad-output/implementation-artifacts/g3-benchmark-artifact.md`]
- [Source: `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/ComponentModelManifest.kt`]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Completion Notes List

- Added and tested the shared component-model manifest gate.
- Tightened production activation so a manifest cannot become production
  without legal, consented-corpus, benchmark, and rollback-evidence refs.
- Added the shared atomic activation/rollback controller and made the Android
  v2 adapter reject non-production ready manifests before photo access.
- Added and tested the Android v2 adapter seam with photo lease/content-digest
  validation, typed text abstention, generation cancellation, runtime release,
  provenance mapping, and fail-closed malformed output handling.
- Android runtime/model selection and physical v2 G-3 evidence remain open;
  this story is intentionally not complete until those artifacts exist.

### Scope amendment note

The Android manifest/activation controller and v2 adapter port are complete for
pre-release. The production model, corpus, and physical component/mass G-3
bundle remain required before `activation = production` and are tracked in the
post-release handoff.

### File List

- `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/ComponentModelManifest.kt`
- `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/ComponentModelActivation.kt`
- `mobile/domain/inference/src/commonTest/kotlin/com/supernutrition/domain/inference/ComponentModelManifestTest.kt`
- `mobile/domain/inference/src/commonTest/kotlin/com/supernutrition/domain/inference/ComponentModelActivationTest.kt`
- `mobile/inference/local-android/src/commonMain/kotlin/com/supernutrition/inference/localandroid/LocalAndroidComponentInference.kt`
- `mobile/inference/local-android/src/commonTest/kotlin/com/supernutrition/inference/localandroid/LocalAndroidComponentInferenceTest.kt`
