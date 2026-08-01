# G-3 local capability and device matrix artifact

| Field | Value |
| --- | --- |
| Artifact ID | `G3-SN-2026-07-31-preliminary-01` |
| Status | `v1-engineering-approved; product-approved-v2-replan; v2 G-3 open` |
| Scope | Epic 3 local inference runtime/model, semantic recognition, ingredient capability, weight capability, and resource-budget decision |
| Production activation | **Blocked until user acceptance/signature and remaining gaps close** |
| Measurement harness | [`tools/g3-benchmark/README.md`](../../tools/g3-benchmark/README.md) |
| Research record | [`docs/research/epic-3-food-recognition-preprocessing-camera.md`](../../docs/research/epic-3-food-recognition-preprocessing-camera.md) |
| Evidence date | 2026-08-01 |

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

### Selected research baseline for ingredient and weight work

FPB/YOLOv12-FoodWeight is the selected first research baseline for the next
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

Therefore FPB is now the implementation/evaluation starting point for
ingredient candidates, boxes, and grams, but it does not change this artifact's
current capability result: the product contract still emits a single dish
candidate, the smoke has no masks, and the repository/dataset license and
phone-photo generalization remain unresolved. No G-3 signature or production
runtime selection is implied by choosing this baseline.

## Device matrix and measured evidence

| OS/device tier | Physical device | Runtime and execution provider | Semantic result | Ingredient / weight result | Runtime/resource result |
| --- | --- | --- | --- | --- |
| Android broad-tier candidate | Honor DNY-NX9; Android 16/API 36; `arm64-v8a`; explicit ADB mDNS serial `adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp` | ONNX Runtime Android `1.28.0`; CPU; available processor threads | fresh `5/5`, accuracy `1.0` under current `0.55` floor | visible ingredients `unsupported`; plated weight `not-evaluable` | latest fresh run: init `2,611 ms`; p50 `182 ms`; p95 `279 ms`; max `334 ms`; PSS `27,520 -> 273,547 KB`; battery current `-1,355 -> -1,543 µA`; thermal `0 -> 0` |
| iOS high-tier candidate | iPhone 14 Pro; iOS 26.5.2; `arm64e`; UDID `00008120-0012295934E0C01E` | ONNX Runtime Objective-C `1.27.0`; XNNPACK; 2 threads | latest fresh `5/5`, accuracy `1.0` | visible ingredients `unsupported`; plated weight `not-evaluable` | latest fresh run: init `560 ms`; p50 `83 ms`; p95 `85 ms`; max `85 ms`; resident `261,360 -> 253,008 KB`; thermal `0 -> 0`; battery level `1 -> 1` |

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

## Acceptance and signature

| Role | Name | Decision | Date | Signature/reference |
| --- | --- | --- | --- | --- |
| Engineering owner | AJ (`ajdevy`) | approved: scoped High local-wait budget for current dish-only v1 harness | 2026-08-01 | user-authorized in this task; PR #29 |
| Product owner | Anastasia | approved: v2 visible-component and estimated-grams direction; v1 dish-only is not product acceptance | 2026-08-01 | user approval in this task; v2 G-3 signature remains pending evidence |

Until both rows are completed, G-3 remains open and E3.2/E3.3/E3.4 remain
implementation-ready/in-progress rather than production-complete.
