---
audit_date: 2026-08-02
scope: epic-3
status: pre-release-complete-production-g3-deferred
---

# Epic 3 closure audit

This is the current requirement-by-requirement closure record for Epic 3. The
approved 2026-08-02 scope amendment closes the pre-release implementation while
keeping production v2 G3 explicitly deferred. It does not promote historical
host/bridge evidence into v2 model evidence.

## Pre-release Epic 3 closure

Under
[`sprint-change-proposal-post-release-v2-g3-corpus-2026-08-02.md`](../planning-artifacts/sprint-change-proposal-post-release-v2-g3-corpus-2026-08-02.md),
Epic 3's pre-release implementation boundary is complete. The app remains
safe because no component runtime is activated and no public research data is
treated as product evidence. The deferred production gates below remain
mandatory before any component/mass claim or `activation = production`.

## Verified repository work

- E3.1 and E3.5 contracts are present and policy validation passes.
- E3.2/E3.3 adapter seams, lease/digest validation, cancellation, release,
  provenance, and fail-closed non-production activation are implemented and
  covered by tests.
- E3.4 local-first routing and attempt invalidation are implemented and tested.
- E3.6 has a privacy-safe schema, dependency-free validator, strict
  `--require-g3` mode, and 10 passing validator tests.
- E3.7/E3.8 have the shared immutable manifest, atomic activation/rollback
  controller, and independent Android/iOS v2 adapter ports.
- E3.9 implementation is complete under the approved pre-release scope: it has
  the typed reducer/presenter/Compose evidence boundary, the request-bound
  `PhotoAnalysisAnalysisCoordinator` seam, and an explicit
  `PhotoAnalysisAnalysisLauncher` host lifecycle seam. Android and iOS keep its
  binding at `None` until the Epic 3 G3 production gate; no production runtime
  is composed before the manifest gate.
- `qualityCheckJvmAndroid` and `qualityCheckApple` pass. Current JVM host
  coverage is 97.98% line / 85.84% branch across eight production modules.
- The historical fresh physical Android gate passed production `15/15`, UI acceptance
  `15/15`, and local-Android adapter `4/4`, all with zero failures, errors, or
  skips on Honor DNY-NX9 / Android 16.
- Fresh physical iOS correctness evidence was captured on iPhone 14 Pro / iOS
  26.5.2 with the configured Apple team: Xcode UI acceptance `5/5` and the
  binary-bound `iosArm64` fixture lane `5/5` (3 normalization, 2 bridge). This
  is correctness/platform evidence, not selected-model G3 evidence; the Apple
  quality gate remains good-to-have under the current lifecycle policy.
- The fresh Android v2 candidate screen ran on five complex fixtures. The
  detector-specific evidence was later removed under D-13; the retained record
  does not supply accepted ingredient-plus-grams quality evidence.
- A public research cache now contains 300 FoodSeg103 image/mask pairs and 300
  Nutrition5k RGB-D/mass plates. These samples are explicitly research-only and
  do not satisfy the consent, phone-capture, or split-isolation contract.
- `git diff --check` and `graphify update .` pass.

## Requirements not achieved

| Requirement | Current evidence | Closure condition |
| --- | --- | --- |
| Cleared production component/mass model | No model artifact exists under the searched product/research trees; candidate screening rejected classification-only and uncleared research options | Legal clearance, artifact digest, calibration, mobile runtime, and rollback manifest |
| Consented weighed corpus | Schema, validator, collection runbook, and separate public research cache are ready; no consented corpus manifest exists | At least 300 consented cases with opaque references, masks, tared readings, QC, and train/calibration/held-out isolation |
| Android v2 G3 | Physical Android evidence exists, but no retained candidate proves visible ingredient identity and calibrated grams | Selected, legally cleared model run on a physical Android device with component quality, grams, latency, memory, thermal, battery, privacy, and rollback evidence |
| iOS v2 G3 | Physical iOS correctness evidence is locally recorded on iPhone 14 Pro / iOS 26.5.2 with the configured Apple team: UI acceptance `5/5` and binary-bound `iosArm64` fixtures `5/5` (3 normalization, 2 bridge). This is not selected-model G3 evidence. | Same selected model and corpus benchmark on the physical iPhone with iOS-specific runtime/resource evidence |
| E3.9 story implementation | Coordinator, typed UI, host lifecycle seam, and focused tests are complete under the approved pre-release scope; both hosts intentionally bind `PhotoAnalysisAnalysisLauncher.None` because no production runtime is approved | No story-level code blocker remains. At Epic 3 G3 exit, bind the selected runtime only after `ComponentModelManifest` has `APP_MANAGED` ownership, rollback target, and product/engineering/legal/corpus/benchmark/rollback refs; then execute staged/review/Cancel/recovery on a physical device and record that production-bound evidence |
| Product/Engineering G3 signoff | Engineering conditional GO is recorded in `g3-signoff-decision-2026-08-02.md`; Product final release sign-off is pending | Bind the complete model/legal/corpus/benchmark/rollback bundle, attach Product sign-off, and have Engineering verify the manifest references |

## Current external state

- Earlier physical Android runs used Honor DNY-NX9 serial
  `PHYSICAL_DEVICE_SERIAL`; the explicit mDNS serial `PHYSICAL_DEVICE_ENDPOINT` belongs
  to the previous physical evidence. The current `adb devices -l` is empty;
  the physical rerun after adding `UiDevice.wakeUp()` is pending reconnection.
  The historical Gradle run passed the isolated local-Android adapter `4/4`,
  production app `15/15`, and `uiAcceptance` `15/15`, with zero failures,
  errors, or skips. The previous production attempt was `15 tests/1 failure`
  due to the lock screen, not product Compose semantics.
- Physical iOS correctness evidence is locally recorded on the paired iPhone 14
  Pro / iOS 26.5.2: Xcode UI acceptance `5/5` and binary-bound `iosArm64`
  fixtures `5/5` (3 normalization, 2 bridge). This is correctness/platform
  evidence only, not selected-model G3 evidence; Apple quality gate execution
  remains good-to-have under the current lifecycle policy.
- The production manifest intentionally rejects missing legal, corpus,
  benchmark, and rollback references; no fake production manifest is present.

Epic 3 is `done` for the amended pre-release scope in `sprint-status.yaml`.
Production v2 G3 remains `NO-GO` and is intentionally deferred until the
selected model, consented corpus, selected-model Android/iOS benchmark, legal,
rollback, and replacement approval inputs above exist.

## 2026-08-02 re-audit

The repository-owned gates were re-run against the current dirty worktree:

- `qualityCheckJvmAndroid` passed, including compile, Detekt, policy,
  unit/JVM tests, and coverage (`97.98%` line / `85.84%` branch across eight
  production modules).
- The preceding audit's coverage snapshot is historical: `97.36%` line /
  `85.75%` branch across eight production modules.
- `qualityCheckApple` passed its local policy/host gate; Apple execution remains
  good-to-have under the current lifecycle policy.
- `verifyMobilePolicy` passed.
- `python3 -m unittest discover -s tools/g3-benchmark -p 'test_*.py'` passed
  `10/10` tests.
- Focused JVM tests for inference, photo-analysis, presenter, UI, and both
  local adapters passed (`67` Gradle tasks, zero test failures).
- The public research runner completed all 300 FoodSeg103 pairs and all 300
  Nutrition5k plates, but its box-derived/prompted masks and RGB-D linear mass
  baseline remain research-only and do not satisfy the consented G-3 contract.
- The web model screen was refreshed against [FoodSAM](https://github.com/jamesjg/FoodSAM)
  and [Boba Food VLM 0.8B](https://huggingface.co/Doses-AI/boba-0.8b-food-GGUF):
  FoodSAM lacks a calibrated mass head and mobile production artifact; Boba
  reports VLM-generated grams but lacks the required mask/region, abstention,
  calibration, rollback, and product-clearance bundle. Neither is selected.
- A second public screen checked [ConvNeXt-Food-CLF-75](https://huggingface.co/Alas-V/ConvNeXt-Food-CLF-75)
  and the [FoodSeg103 density/nutrition dataset](https://huggingface.co/datasets/arunapb/food-density-nutrition).
  The classifier has no regions or mass head; the density dataset has class
  lookup values, not measured per-photo mass or a deployable vision model.
  Neither changes the model or corpus decision.
- Product/Engineering G3 signoff is now recorded as a dated decision: the
  amended pre-release implementation is accepted, while production v2
  activation is explicitly `NO-GO` until the missing selected model, consented
  corpus, selected-model Android/iOS benchmark, and rollback evidence is
  attached.

Current external execution state now includes fresh physical Android and iOS
correctness results, but not selected-model v2 quality results. The connected
Android adapter and packaged suites passed as recorded above. On the paired
iPhone 14 Pro / iOS 26.5.2, Xcode UI acceptance passed `5/5` and the
binary-bound `iosArm64` fixture lane passed `5/5` (3 normalization, 2 bridge).
These are correctness/platform evidence only and do not satisfy selected-model
G3. Product/Engineering signoff is recorded as `NO-GO` for production with
the pre-release implementation accepted; the selected legally cleared
component/mass artifact, consented 300-case corpus, and selected-model
Android/iOS benchmark remain absent.

The production G3 status therefore remains `blocked-on-external-evidence`: the
code and local quality gates are green, while the selected legally cleared
component/mass artifact, consented 300-case corpus, and selected-model
Android/iOS benchmark remain absent. The recorded NO-GO signoff does not
replace those required gates.

The E3.9 runtime boundary is intentional rather than incomplete wiring: the
coordinator and UI are present, but the launcher has no production binding. The
`ComponentModelManifest` implementation rejects production activation unless
all six approval/evidence references are present and the app-managed rollback
target is supplied; it also rejects those references on staged manifests.
Accordingly, E3.9 is tracked as `review` at story level, while the production-
bound staged/review device flow remains an Epic 3 G3 exit condition.

## 2026-08-02 connected-device follow-up

`xcrun devicectl list devices` reports the paired iPhone 14 Pro as
`available (paired)`. The current local signoff records physical iOS
correctness evidence on that device: Xcode UI acceptance `5/5` and the
binary-bound `iosArm64` fixture lane `5/5` (3 normalization, 2 bridge). This
does not run a selected model and is not selected-model G3 evidence. A separate
research-only Food-101 run on the Android device passed semantic
recognition `5/5` with init `2,150 ms`, p50/p95/max `187/218/335 ms`, PSS
`26,350 -> 270,889 KB`, battery current `-253 -> -1,674 µA`, and thermal
`0 -> 0`; its ingredient result is `unsupported` and weight is
`not-evaluable`. These results do not select or activate a production model.

## 2026-08-02 post-hardening re-audit

- Closed the repository-owned v2 contract findings: text results now reject
  visual-region evidence, public list inputs are snapshotted before invariant
  checks, request/result modality parity is enforced at the engine boundary,
  and exact-mass ranges remain rejected.
- Re-ran positive and negative v1/v2 contract verification; both passed.
- `:domain:inference:jvmTest` passed all `46/46` tests. The G3 validator suite
  passed `10/10` tests, and `git diff --check` passed.
- Re-ran `qualityCheckJvmAndroid` and `qualityCheckApple`; both completed with
  `BUILD SUCCESSFUL`. Current JVM coverage is `97.98%` line / `85.84%` branch.
- Historical physical Android evidence on `PHYSICAL_DEVICE_ENDPOINT` / Honor
  DNY-NX9 recorded local adapter `4/4`, production `15/15`, and UI acceptance
  `15/15`, all with zero failures, errors, or skips.
- After adding `UiDevice.wakeUp()`, the physical rerun was not completed because
  the DNY endpoint is currently offline. The previous production attempt was
  `15 tests/1 failure` due to the lock screen, not product Compose semantics.
- These checks close repository-owned hardening gaps. Physical iOS correctness
  evidence is present as recorded above, but the selected model, consented
  corpus, selected-model Android/iOS benchmark, and owner approval bundle remain
  the post-release production G3 gate under the approved scope amendment.

## 2026-08-02 post-release scope amendment closure

- E3.2, E3.3, E3.4, E3.6, E3.7, E3.8, and E3.9 are marked `done` for the
  pre-release implementation boundary; their story records explicitly preserve
  the deferred production work.
- The 1.13 GB NutritionVerse-Real archive was downloaded outside Git and
  inventoried as research-only: 251 dish groups, 1,184 ingredient-weight
  records, 905 COCO image records, and `CC BY-NC-SA 4.0`. It is not a product
  corpus or production model source.
- The separate post-release upload/consent feature remains responsible for the
  300+ consented weighed cases, scale/QC metadata, withdrawal handling, and
  leakage-isolated corpus manifest.
- The Engineering conditional GO does not remove the G3 prerequisites:
  production release execution remains pending until the complete model, legal,
  corpus, selected-model Android/iOS benchmark, rollback, and Product approval
  bundle is bound and verified.
