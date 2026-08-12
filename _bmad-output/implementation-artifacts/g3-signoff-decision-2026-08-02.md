---
decision_id: epic-3-g3-signoff-2026-08-02
decision_date: 2026-08-02
owner_decision_date: 2026-08-11
scope: epic-3-v2-visible-component-and-mass-inference
status: conditional-go-production-evidence-pending
implementation_status: pre-release-complete-production-deferred
---

# Product/Engineering G3 signoff decision

## Decision

**Production v2 activation: GO, conditional on the required evidence below.**

**Pre-release implementation and research preparation: GO under the approved
scope amendment.**

Engineering owner AJ recorded the conditional GO on 2026-08-11. The decision
authorizes production release execution only after every required external
evidence item below is attached and independently verified, and after the
Product owner signs the resulting release bundle. It does not select or approve
a production component/mass model or production manifest by itself. The
post-release scope amendment explicitly allows Epic 3's pre-release status to
be `done` while the release bundle remains incomplete.

This decision is intentionally a signoff, not a substitute for missing
evidence. It prevents a public-data model, dish-only classifier, UI fixture, or
unweighed photo benchmark from being treated as a production G3 approval.

## Role decisions

| Role | Owner | Decision | Scope | Reference |
| --- | --- | --- | --- | --- |
| Product | Anastasia | final release sign-off pending | v2 product direction and the amended pre-release boundary are accepted; the Product owner must sign the completed evidence bundle before activation | `g3-benchmark-artifact.md`, current Product direction row |
| Engineering | AJ (`ajdevy`) | GO, conditional | owner-authorized 2026-08-11: engineering may release only after a selected, bound, verified model/legal/corpus/Android+iOS/rollback bundle and Product sign-off | user-authorized G2-1 in this task |

## Evidence reviewed

### Accepted for amended pre-release implementation

- v2 contract and compatibility policy pass validation.
- Android/iOS adapter ports enforce lease, digest, cancellation, provenance,
  and non-production fail-closed behavior.
- Atomic model activation/rollback controller exists and requires production
  references before activation.
- Staged reducer/presenter/Compose boundary, coordinator seam, and explicit
  Android/iOS launcher lifecycle seam are covered by focused tests; both hosts
  intentionally remain bound to `PhotoAnalysisAnalysisLauncher.None`.
- `qualityCheckJvmAndroid` passed with `97.36%` line and `85.75%` branch
  coverage across eight production modules.
- `qualityCheckApple`, `verifyMobilePolicy`, corpus validator (`10/10`),
  focused JVM tests (`67` Gradle tasks), `git diff --check`, and `graphify
  update .` passed.
- Public FoodSeg103/Nutrition5k samples and Android candidate runs are retained
  as research-only evidence.
- Fresh physical iOS correctness evidence was captured on iPhone 14 Pro / iOS
  26.5.2 with the configured Apple team: Xcode UI acceptance `5/5` and the
  binary-bound `iosArm64` fixture lane `5/5` (3 normalization, 2 bridge), with
  an emitted local-only bundle under `/tmp/supernutrition-ios-evidence-20260802/`.
- Post-hardening rerun passed v1/v2 positive and negative contract checks,
  `:domain:inference:jvmTest` `46/46`, the G3 validator suite `10/10`, both
  quality gates, and physical Android adapter/production/UI suites `4/4`,
  `15/15`, and `15/15`. This strengthens implementation readiness but does
  not satisfy the missing model, corpus, iOS v2, or approval gates.

### Required before release execution

| Gate | Required evidence | Current state |
| --- | --- | --- |
| Model/legal | One selected component model with legal clearance, artifact and taxonomy digests, runtime/EP, calibration, and rollback target | **Missing; no model selected** |
| Corpus | At least 300 consented weighed cases with masks, tared component readings, QC, withdrawal handling, and train/calibration/held-out isolation | **Missing; public samples do not qualify** |
| Android G3 | Selected model on physical Android with component quality, gram MAE/p90, latency, memory, thermal, battery, privacy, and rollback evidence | **Missing; the fresh candidate screen is recorded, but no selected component model, accepted mass quality, consented corpus, or rollback bundle exists** |
| iOS G3 | Same selected model/corpus on physical iPhone with iOS runtime/resource and rollback evidence | **Missing for production G3; physical product acceptance is now evidenced locally (`5/5` UI, `5/5` iosArm64 fixtures) on iPhone 14 Pro, but no selected component model, consented mass corpus, calibrated mass score, or rollback bundle exists. Simulator lane remains blocked by the missing strict-verification checksum for `kotlinx-serialization-core-iosx64:1.9.0`.** |
| Epic 3 G3 staged device flow | Bound runtime, staged wait, review evidence, Cancel, and recovery demonstrated on a physical device | **Deferred; E3.9 coordinator/UI/lifecycle implementation is complete under the amendment, both hosts intentionally bind `None`, and no production runtime is approved** |
| Release authorization | Product and Engineering approvals referenced by the production manifest | **Engineering conditional GO recorded; Product final sign-off and the complete bound bundle are pending** |

## Activation rule

This decision is the Engineering approval reference, not a complete production
bundle. A `ComponentModelManifest` may use `activation = production` only when
it binds this decision together with the completed model, legal, corpus,
benchmark, rollback, and Product approval references, and those references
have been independently verified.

## Exit criteria for a release-ready manifest

1. Attach the validated `--require-g3` corpus manifest and its immutable digest.
2. Attach the selected model manifest, legal clearance, artifact/taxonomy
   digests, calibration record, mobile runtime evidence, and rollback evidence.
3. Run the same selected model and corpus on physical Android and iOS devices;
   publish component identity/mask and mass metrics plus resource/privacy
   slices.
4. Bind the selected runtime to the staged UI flow and record physical-device
   staged/review/Cancel/recovery evidence.
5. Re-run local quality gates, attach the Product sign-off, and have
   Engineering confirm that the manifest references the complete bundle.

Epic 3 is `done` for the amended pre-release scope. Production release
execution remains pending until the complete evidence bundle and Product
sign-off are attached.

## Authoritative records

- `epic-3-closure-audit.md`
- `g3-benchmark-artifact.md`
- `3-6-establish-consented-weighed-component-corpus.md`
- `3-7-spike-local-android-visible-component-and-mass-inference.md`
- `3-8-spike-local-ios-visible-component-and-mass-inference.md`
- `3-9-integrate-staged-component-review-and-local-wait-ux.md`
- `../planning-artifacts/sprint-change-proposal-post-release-v2-g3-corpus-2026-08-02.md`
- `mobile/domain/inference/.../ComponentModelManifest.kt`
