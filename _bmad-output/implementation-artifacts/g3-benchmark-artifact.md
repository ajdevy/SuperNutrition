# G-3 local capability and device matrix artifact

| Field | Value |
| --- | --- |
| Artifact ID | `G3-SN-2026-07-31-preliminary-01` |
| Status | `v1-engineering-approved; product-approved-v2-replan; v2 G-3 open` |
| Scope | Epic 3 local inference runtime/model, semantic recognition, ingredient capability, weight capability, and resource-budget decision |
| Production activation | **Blocked until user acceptance/signature and remaining gaps close** |
| Measurement harness | [`tools/g3-benchmark/README.md`](../../tools/g3-benchmark/README.md) |
| Research record | [`docs/research/epic-3-food-recognition-preprocessing-camera.md`](../../docs/research/epic-3-food-recognition-preprocessing-camera.md) |
| Evidence date | 2026-08-02 |

This document records reproducible physical-device evidence. Evidence alone is
not a signature; this revision records one scoped Engineering approval and a
Product-approved v2 replan, while the component/mass Product and Engineering
G-3 signatures and production activation remain blocked. It includes a
bounded semantic recognition proof for five single-dish Food-101 labels. It
does not claim multi-object detection, ingredient extraction, portion
estimation, nutrition correctness, or general Food-101 accuracy. The meal
benchmark records those capabilities as explicit unsupported/not-evaluable
until a component model and calibrated scale corpus exist.

## Candidate and semantic contract

Both platforms use the same pinned artifact:

- model: `swin-finetuned-food101-matmul-int8.onnx`;
- model SHA-256:
  `9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df`;
- source: Food-101 fine-tuned Swin ONNX model, pinned and transformed by the
  repository's MatMul-only dynamic QInt8 quantization script;
- input: RGB centered square crop, resize to `224 x 224`, ImageNet mean/std;
- semantic fixtures and ground truth:
  `steak -> steak`, `greek-salad -> greek_salad`,
  `salmon-on-grill -> grilled_salmon`, `sushi -> sushi`,
  `waffles -> waffles`;
- semantic pass: all five exact top-1 labels at confidence `>= 0.55`;
- composite fixtures: five public meal photos exercised without an expected
  label; they are exploratory only.

Food-101 itself is a 101-class single-dish classification benchmark. The
[dataset card](https://huggingface.co/datasets/ethz/food101) documents that it
contains 101,000 images, noisy training labels, source/cuisine bias, and no
ingredient or nutrition metadata.

## Ingredient and plated-weight benchmark

The five composite fixtures are defined in
[`tools/g3-benchmark/meal-benchmark-manifest.json`](../../tools/g3-benchmark/meal-benchmark-manifest.json).
The annotation scope is visible, separable components only; hidden recipe
ingredients, oil, sauces whose composition is not visible, and nutrition
values are excluded. The intended metrics are per-meal ingredient-set
precision/recall/F1 and total plated-mass MAE/p90 absolute error in grams.
Per-ingredient mass is also required; the total is an optional aggregate used
to detect conservation errors.

The current pinned Food-101 runner reports the capability boundary on each
device:

| Capability | Current device result | Why it is not a quality score |
| --- | --- | --- |
| Visible ingredient set | `unsupported`, 5 cases in the corpus, 0 predictions | The runner emits one dish label, not ingredient candidates, boxes, or masks |
| Plated mass in grams | `not-evaluable`, 0 calibrated cases, 0 predictions | Public photos have no tared scale readings and the model emits no mass |

The dependency-free evaluator is
[`tools/g3-benchmark/evaluate-meal-benchmark.py`](../../tools/g3-benchmark/evaluate-meal-benchmark.py).
Weights must be added from a consented controlled capture: tare the empty
vessel, weigh each visible ingredient before plating, weigh the final plate,
record scale readings and resolution, then capture overview and oblique device
photos. Pixels and a dish class are not a valid gram oracle.

The E3.6 corpus boundary is now executable through
[`tools/g3-benchmark/consented-weighed-corpus.schema.json`](../../tools/g3-benchmark/consented-weighed-corpus.schema.json)
and [`tools/g3-benchmark/validate_weighed_corpus.py`](../../tools/g3-benchmark/validate_weighed_corpus.py).
The validator enforces opaque references, scrubbed photo/mask digests, tared
per-component readings, explicit non-evaluable states, QC, and split isolation;
no human corpus is checked in, so this implementation does not add G-3 quality
evidence or a runtime/model selection.

The validator's explicit `--require-g3` mode now enforces the minimum 300-case
pilot and the complete `train`, `calibration`, and `held-out` partition set.
The repository contains no consented manifest, so this strict check remains an
acceptance gate rather than fabricated G-3 evidence.

The real-corpus scoring layer is now executable through
[`tools/g3-benchmark/evaluate_consented_corpus.py`](../../tools/g3-benchmark/evaluate_consented_corpus.py).
It consumes only a validated redacted corpus plus a strict model prediction
handoff, matches visible components one-to-one by canonical label and box IoU,
and emits ingredient F1, component/total mass error, coverage/abstention, and
partition/device/lighting/angle slices. With no 300-case consented corpus or
production model prediction handoff in this checkout, its production command
must remain `not-evaluable`; no score is fabricated from the public fixtures.

The shared `component-model/v1` manifest now also requires every production
candidate to bind four additional evidence references: legal clearance, the
consented corpus, the component/mass benchmark, and rollback verification.
Those references are rejected on research-only and staged manifests, so a
runtime cannot promote a candidate merely by adding Product and Engineering
approval strings. Owner ruling D1 now composes the Android PoC as the current
local production runtime, but that candidate still does not satisfy the G-3
quality/release gate: the checked-in corpus, physical component/mass evidence,
legal/rollback evidence, and replacement Product/Engineering approval bundle
remain absent.

### FPB research baseline for ingredient and weight work

FPB/YOLOv12-FoodWeight was the first research baseline for the
multi-component experiment. Its pinned repository/checkpoint/dataset
provenance, AGPL-3.0 repository boundary, adapter, and smoke evidence are
recorded in
[`ingredient-weight-research/README.md`](../../ingredient-weight-research/README.md),
[`fpb-yolov12-foodweight-manifest.json`](../../ingredient-weight-research/fpb-yolov12-foodweight-manifest.json),
and [`fpb-smoke-evidence.json`](../../ingredient-weight-research/fpb-smoke-evidence.json).
The five-image CPU smoke produced detections on `5/5` cases, class accuracy
`0.60`, mean box IoU `0.805`, weight MAE `48.54 g`, and p90 absolute error
`110.48 g`. The published checkpoint required a temporary compatibility shim
with current Ultralytics because the pinned repository cannot load its
`C3k2` layer; this is not a production mobile integration.

The follow-up five-complex-meal CPU screen is recorded in
[`ingredient-weight-research/fpb-yolov12-foodweight-composite-evidence.json`](../../ingredient-weight-research/fpb-yolov12-foodweight-composite-evidence.json).
At confidence `0.25` it emitted eight raw FPB boxes, but no label matched a
conservative visible-component annotation; macro component F1 is `0.0` across
all five meals. The public fixtures have no tared mass record, so the raw grams
head remains `not-evaluable` for mass MAE. FPB is consequently **not a current
v2 runtime candidate**. Its repository/data license boundary and phone-photo
generalization remain unresolved, and no G-3 signature or production runtime
selection is implied by the research artifacts.

### Android research-candidate screen (not a G-3 selection)

On 2026-08-01, two additional candidates were exercised on the physical Android
research device through ONNX Runtime Android CPU. This is a deployment screen,
not a shared acceptance test and does not change the signed Food-101 model,
the G-3 state, or the product runtime.

| Candidate | Actual Android result | Ingredient conclusion | Weight conclusion |
| --- | --- | --- | --- |
| MobileSAM | encoder and prompted decoder completed all five complex fixtures; p95 `2,060 ms`; PSS `729,305 KB` | `unsupported`: four masks/fixture are class-agnostic and prompt-dependent | `not-evaluable`: no mass output and no tared public-photo ground truth |
| YOLO-World v2 | exported `640 x 640` ONNX completed all five fixtures; p95 `581 ms`; PSS `245,411 KB` | COCO-80 exact-label subset macro F1 `0.2233`; it found some carrots/broccoli but also false food labels | `not-evaluable`: no mass head and no tared public-photo ground truth |

The YOLO-World measurements in this historical table are bound to the
benchmark artifact SHA-256 `ef272175d4db2216cde08082f1accf120bdf5ee03b5bc2ba5f51735cda1e9cbc`.
The production PoC runtime now pins the re-exported region artifact
`d27ac81640ca470cc7e42edd638b6626ac6b4f4dcce74aabb6e09cd02a13c84f`.
Therefore the `0.2233` macro-F1 and associated latency/resource numbers are
historical evidence only; they are not a quality claim for the shipped
runtime until the benchmark is rerun against the pinned production artifact.

FoodSAM and FoodSeg103/ReLeM were not run on the phone: their released research
implementations do not supply a complete Android-compatible artifact in this
checkout. FoodSAM is a SAM + semantic-segmenter + detector stack; FoodSeg103 is
semantic segmentation and does not yield separate repeated components or mass.
Neither omission receives an invented device metric. Full candidate provenance,
checksums, and fixture outputs are in
[`ingredient-weight-research/android-candidate-device-evidence-2026-08-01.json`](../../ingredient-weight-research/android-candidate-device-evidence-2026-08-01.json).

On 2026-08-02, the two Android research activities were rerun on the connected
physical Honor DNY-NX9 (`AU3N025429006787`). MobileSAM completed `5/5`
prompted mask runs with p95 `1,891 ms` and PSS `735,484 KB`, but remains
`unsupported` for ingredient identity and `not-evaluable` for mass. YOLO-World
v2 completed `5/5` runs with exact visible-component macro F1 `0.2233`, p95
`594 ms`, and PSS `245,274 KB`; it remains limited to the embedded COCO-80
vocabulary and has no mass head. The fresh record is
[`ingredient-weight-research/android-candidate-device-evidence-2026-08-02.json`](../../ingredient-weight-research/android-candidate-device-evidence-2026-08-02.json).
This is fresh deployment/candidate evidence only; it does not select a v2
production model or close the Product/Engineering G-3 gate.

The latest component-candidate rerun used the same physical device through the
explicit ADB mDNS serial `192.168.0.150:37613` and the installed
`com.supernutrition.g3benchmark` APK (SHA-256
`e584f14a0bc007d31893e5d22fdf65d2e5d423b1f77609e40a0e9ee609e27fc7`).
MobileSAM completed `5/5` prompted mask runs with p95 `2,003 ms` and PSS
`733,237 KB`; YOLO-World v2 completed `5/5` runs with exact visible-component
macro F1 `0.2233`, p95 `670 ms`, and PSS `244,432 KB`. Both stayed thermally
stable at `0 -> 0`. This is now a fresh physical Android component-candidate
screen, but it is **not** a component/mass G-3 pass: MobileSAM remains
class-agnostic, YOLO-World is limited to COCO-80, and both mass results are
`not-evaluable` because the fixtures have no calibrated/tared scale truth and
neither model emits calibrated mass. The complete record is
[`ingredient-weight-research/android-component-mass-benchmark-2026-08-02.json`](../../ingredient-weight-research/android-component-mass-benchmark-2026-08-02.json).

## Device matrix and measured evidence

| OS/device tier | Physical device | Runtime and execution provider | Semantic result | Ingredient / weight result | Runtime/resource result |
| --- | --- | --- | --- | --- |
| Android broad-tier candidate | Honor DNY-NX9; Android 16/API 36; `arm64-v8a`; explicit ADB mDNS serial `adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp` | ONNX Runtime Android `1.28.0`; CPU; available processor threads | fresh `5/5`, accuracy `1.0` under current `0.55` floor | visible ingredients `unsupported`; plated weight `not-evaluable` | latest fresh run: init `2,611 ms`; p50 `182 ms`; p95 `279 ms`; max `334 ms`; PSS `27,520 -> 273,547 KB`; battery current `-1,355 -> -1,543 µA`; thermal `0 -> 0` |
| iOS high-tier candidate | iPhone 14 Pro; iOS 26.5.2; `arm64e`; UDID `REDACTED_PHYSICAL_DEVICE_UDID` | Historical evidence: ONNX Runtime Objective-C `1.27.0`; XNNPACK; 2 threads. Current source pins `1.28.0`. | historical fresh `5/5`, accuracy `1.0` | visible ingredients `unsupported`; plated weight `not-evaluable` | historical run: init `560 ms`; p50 `83 ms`; p95 `85 ms`; max `85 ms`; resident `261,360 -> 253,008 KB`; thermal `0 -> 0`; battery level `1 -> 1` |

The Android run above is the latest fresh physical run after the device was
reconnected. It was repeated on 2026-08-01 in
`/tmp/supernutrition-g3-android-20260801`; the result is semantic `5/5`, init
`2,611 ms`, p50/p95/max `182/279/334 ms`, PSS `27,520 -> 273,547 KB`, battery
current `-1,355 -> -1,543 µA`, and thermal `0 -> 0`. The correctly classified
sushi fixture scored `0.592399`, so the declared floor remains `0.55` rather
than `0.60`. In fixture order, the
exploratory composite top-1 labels were
`steak`, `grilled_salmon`, `fried_rice`, `bibimbap`, and `chicken_curry`.
They remain single dish labels and do not establish ingredient recognition.

The same Honor device also passed the rebuilt Gradle-connected Android suites:
UI/semantics and capture suite `15/15` through
`connectedUiAcceptanceDebugAndroidTest`, plus isolated local-Android adapter
suite `4/4` through `connectedAndroidDeviceTest`, all with zero failures,
errors, or skips. The strict dependency locks were refreshed after syncing
`origin/main`, and both tasks ran offline against the explicit wireless ADB
serial.

The physical correctness rerun on code commit `5f6c8772` reproduced those
Android results on the explicit serial. The paired physical iPhone 14 Pro
also passed the automatic-signing Xcode acceptance scheme `5/5` and the
binary-bound `iosArm64` fixture runner `5/5` (3 normalization, 2 bridge) on
iOS 26.5.2. These records are local-only because the checkout intentionally
preserves the user's untracked manual-testing presentation; they are
correctness evidence and do not close the signed runtime/model, ingredient,
or weight gates.

On 2026-08-01, the same device matrix was rerun against the unchanged
production code from `5f6c8772`: Android local adapter `4/4`, Android UI
acceptance `15/15`, iOS Xcode acceptance `5/5`, and iOS arm64 fixtures `5/5`.
This refreshes platform correctness evidence only; it does not change the
preliminary Android resource measurements and now refreshes the iOS resource
measurements as well; it does not add ingredient/grams capability or supply
the required Engineering/Product owner signatures.

The latest iOS result was obtained on 2026-08-01 in
`/tmp/supernutrition-g3-ios-20260801-r3` from a freshly installed,
automatically signed `SuperNutritionG3` app using the configured Apple
development team. All ten fixtures were loaded from the bundle. The run
completed semantic `5/5`, init `560 ms`, p50/p95/max `83/85/85 ms`, resident
memory `261,360 -> 253,008 KB`, thermal `0 -> 0`, and battery level `1 -> 1`.
In fixture order, the composite top-1 dish labels were `filet_mignon`,
`grilled_salmon`, `fried_rice`, `bibimbap`, and `chicken_curry`. These are
exploratory single-label outputs, not ingredient predictions, and are not used
as ingredient quality scores.

A subsequent trace-bound physical run was captured with Xcode Instruments
`Power Profiler` against iPhone 14 Pro / iOS 26.5.2. Its copied result completed
semantic `5/5`, init `572 ms`, p50/p95/max `83/84/84 ms`, resident memory
`255,744 -> 247,360 KB`, thermal `0 -> 0`, and battery level `1 -> 1`. The
local-only trace is
`/tmp/supernutrition-ios-power-20260801.trace`; its table of contents records
the physical UDID, `SuperNutritionG3` process exit `0`, a 32.486 s capture, and
the `Power Profiler` template. The exported `ProcessSubsystemPowerImpact`
table contains CPU-impact samples for the app and reports `0 Bytes` for every
Wi-Fi and cellular receive/transmit field. This is native energy/network trace
evidence, but the current export does not expose a calibrated joule or battery
percentage delta, so the numeric battery budget remains unaccepted rather than
being marked as a pass.

## Fixture provenance

The five composite and five semantic images are downloaded from Wikimedia
Commons and verified by SHA-256 in `tools/g3-benchmark/download-assets.sh`.
The semantic source pages are:

- [Steak (1)](https://commons.wikimedia.org/wiki/File:Steak_(1).jpg)
- [Greek salad](https://commons.wikimedia.org/wiki/File:Greek_salad.jpg)
- [Salmon on grill](https://commons.wikimedia.org/wiki/File:Salmon_on_grill.jpg)
- [Sushi](https://commons.wikimedia.org/wiki/File:Sushi_(29678778543).jpg)
- [Waffles (1)](https://commons.wikimedia.org/wiki/File:Waffles_(1).jpg)

## Proposed budgets

The strict budgets below remain comparison targets, not the approved runtime
envelope.

| Budget | Proposed bound | Android | iOS |
| --- | --- | --- | --- |
| Session initialization | <= 500 ms cold start | **fail**, 2,611 ms | **fail**, 560 ms |
| Inference latency | p95 <= 100 ms; max <= 150 ms after warm-up | **fail**, 279/334 ms | pass, 85/85 ms |
| Peak process memory | <= 160 MB Android PSS; <= 160 MB iOS resident | **fail**, 273,547 KB | **fail**, 253,008 KB |
| Thermal | no serious/critical transition | pass, 0 -> 0 | pass, 0 -> 0 |
| Battery | <= 1% over 50 inferences plus native trace where available | sampled current `-1,287 -> -1,563 µA`; energy/percentage open | native `Power Profiler` trace captured; calibrated energy/percentage open |
| Model integrity | pinned SHA before session creation; mismatch fails closed | pass | pass |
| Network/privacy | no photo upload or product API call | pass | pass |

### Relaxed local-wait rationale

Product direction for the ingredient/weight experiment allows a long local
wait when the UI continuously explains the active stage and exposes Cancel.
The following tiers provided the basis for the scoped Engineering approval;
they do not by themselves activate a production G-3 runtime:

| Tier | Cold init | Analysis | Peak process memory | Required UX |
| --- | ---: | ---: | ---: | --- |
| High | <= 15 s | <= 45 s | <= 512 MB | stage copy, bounded progress refresh, Cancel |
| Mid | <= 30 s | <= 90 s | <= 768 MB | stage copy, bounded progress refresh, Cancel |
| Low | no hard promise | dish-only/manual fallback | device-dependent | do not load the heavy stack after a memory gate |

The current Food-101 semantic runner fits the exploratory High resource
envelope on both measured devices, but it does not emit ingredients or grams.
FPB/YOLOv12-FoodWeight has not yet been measured on either phone, so these
tiers must not be treated as FPB device acceptance.

### Engineering-approved G-3 budget

On 2026-08-01, Engineering owner AJ (`ajdevy`) approved the **High** tier for
the current pinned Food-101 semantic-research harness on the measured Android
and iOS candidate devices:

| Budget | Engineering-approved bound | Current Android | Current iOS |
| --- | ---: | ---: | ---: |
| Cold initialization | <= 15 s | 2.611 s | 0.572 s |
| Analysis completion | <= 45 s | p95 279 ms | p95 84 ms |
| Peak process memory | <= 512 MB | 273,547 KB PSS | 255,744 KB resident |
| Thermal | no serious/critical transition | 0 -> 0 | 0 -> 0 |
| Network/privacy | no photo upload or product API call | pass | native trace: zero Wi-Fi/cellular bytes |

The approval is intentionally narrow: it accepts a staged, cancellable local
wait for the **dish-only** v1 semantic capability. Product integration must
show stage copy and Cancel before it can rely on this envelope. It does not
approve the failed strict budget, a production FPB/YOLOv12 integration,
ingredient-set extraction, gram estimation, nutrition output, or a device tier
outside the measured matrix.

## Preprocessing and camera decision

The host comparison tested seven preprocessing variants on the five semantic
fixtures. All remained `5/5`; direct centered square crop had the best mean
top-1 confidence in this small sample. No global sharpening, autocontrast, or
fixed color filter is enabled by default.

The product camera bridges now request platform-safe capture behavior:

- Android target rotation, quality-oriented capture, and centered best-effort
  AF/AE/AWB metering before the one-shot capture;
- iOS photo session preset and continuous autofocus, auto-exposure, and
  auto-white-balance when supported;
- no unconditional torch, zoom, exposure compensation, or fixed white balance.

The detailed evidence and primary-source links are in the research record
linked at the top of this artifact.

## Required before final product sign-off

- Review and accept the fresh Android semantic/resource result, including its
  failed proposed initialization, latency, and memory budgets. The latest
  2026-08-01 run is init `2,611 ms`, p95/max `279/334 ms`, and PSS peak
  `273,547 KB`.
- Review and accept the captured iOS `Power Profiler` trace, or add an
  approved calibrated energy/percentage reading if that trace is insufficient
  for the selected battery budget.
- Decide and record supported Android and iOS tiers, including low-memory
  behavior.
- Run a consented, weighed multi-component meal corpus (target 300-500 meals)
  with independent food-level ground truth; the five public semantic images
  do not establish real-world accuracy.
- Add a detector/segmenter that emits ingredient candidates and visible
  regions, then run the five composite cases plus the weighed corpus through
  the ingredient-set evaluator; current Food-101 output cannot satisfy this.
- Capture tared plated mass for every weight case and report gram MAE, relative
  MAE, and p90 absolute error by device, cuisine, lighting, and meal type.
- Add or select a detector/segmentation stage before claiming multi-object
  recognition for plates with sides, salads, fish, or Buddha bowls.
- Record rollback behavior for model download/cache and runtime initialization
  failure.
- Product direction is now approved for the v2 visible-component/gram-estimate
  track. This is deliberately **not** acceptance of a dish-only v1 product
  capability: select and benchmark the v2 runtime/model, corpus, thresholds,
  and staged-Cancel UX before a new Product and Engineering G-3 signature.

The 2026-08-01 Android G-3 rerun completed on the same physical Honor device:
semantic `5/5`, init `2,611 ms`, p50/p95/max `182/279/334 ms`, PSS
`27,520 -> 273,547 KB`, battery current `-1,355 -> -1,543 µA`, and thermal
`0 -> 0`. Ingredient capability remains `unsupported` and weight capability
remains `not-evaluable`. The paired iPhone was initially locked during one
attempt, but the successful unlocked rerun completed semantic `5/5`, init
`560 ms`, p50/p95/max `83/85/85 ms`, resident memory
`261,360 -> 253,008 KB`, thermal `0 -> 0`, and battery level `1 -> 1`.

The 2026-08-02 current-worktree Android research rerun used the physical Honor
DNY-NX9 through `192.168.0.150:37613` and the pinned Food-101 artifact. It
completed semantic `5/5`, init `2,150 ms`, p50/p95/max `187/218/335 ms`, PSS
`26,350 -> 270,889 KB`, battery current `-253 -> -1,674 µA`, and thermal
`0 -> 0`. The result explicitly reports ingredient capability `unsupported`
and plated weight `not-evaluable`; the full local-only bundle is
`/tmp/supernutrition-g3-android-20260802-current`. This is a fresh
research/device result, not component/mass G-3 evidence or production
activation.

The 2026-08-02 iOS research-runner rerun was attempted against the paired
iPhone 14 Pro (`REDACTED_PHYSICAL_DEVICE_UDID`) with local identity
`6U74HTSWH3`. Xcode stopped before compilation because no authenticated account
or provisioning profile exists for that team and bundle `com.supernutrition.g3`.
That research-only `com.supernutrition.g3` attempt produced no benchmark result
and is not counted. The separate product physical acceptance path was then
rerun with the configured Team `REDACTED_TEAM_ID`: Xcode UI acceptance completed
`5/5`, and the binary-bound `iosArm64` fixture lane completed `5/5` (3
normalization, 2 bridge) on iPhone 14 Pro / iOS 26.5.2. The emitted bundle is
`/tmp/supernutrition-ios-evidence-20260802/bundle/`, with status
`local-only` because this checkout is dirty; it binds the physical UDID,
artifact/fixture/policy digests, Xcode/Gradle versions, `.xcresult`, and JUnit
counts. The iOS simulator lane remains unexecuted because strict dependency
verification lacks the `kotlinx-serialization-core-iosx64:1.9.0` checksum.
These are platform correctness/evidence results only; they do not establish
component identity, calibrated mass quality, or production G-3 approval.

## Acceptance and signature

| Role | Name | Decision | Date | Signature/reference |
| --- | --- | --- | --- | --- |
| Engineering owner | AJ (`ajdevy`) | approved: scoped High local-wait budget for current dish-only v1 harness | 2026-08-01 | user-authorized in this task; PR #29 |
| Product owner | Anastasia | approved: v2 visible-component and estimated-grams direction; v1 dish-only is not product acceptance | 2026-08-01 | user approval in this task; v2 G-3 signature remains pending evidence |
| Engineering owner | AJ (`ajdevy`) | GO, conditional: release execution may proceed after the complete verified production bundle and Product sign-off | 2026-08-11 | user-authorized G2-1 in this task |

## 2026-08-02 Product/Engineering G3 decision

The dated decision record is
[`g3-signoff-decision-2026-08-02.md`](g3-signoff-decision-2026-08-02.md).
Engineering records **GO, conditional, for production v2 release execution**;
the Product owner's final release sign-off remains pending. The decision accepts
the repository-owned implementation evidence, but the conditional GO does not
substitute for the selected model, consented weighed corpus, accepted physical
Android/iOS component/mass G-3 benchmark bundle, bound E3.9 runtime evidence,
or complete approval and rollback references. The latest Android candidate
screen is recorded separately and does not satisfy that bundle.

Under the approved 2026-08-02 post-release scope amendment, E3.2/E3.3/E3.4
and the rest of Epic 3 are complete for the pre-release implementation scope.
G-3 remains open for production v2 activation: the manifest must bind the
complete model, legal, consented-corpus, physical Android/iOS benchmark,
staged-flow, rollback, and Product approval bundle before release execution.
This artifact must not be read as production component/mass approval by itself.
