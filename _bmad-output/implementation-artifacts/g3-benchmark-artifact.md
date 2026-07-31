# G-3 local capability and device matrix artifact

| Field | Value |
| --- | --- |
| Artifact ID | `G3-SN-2026-07-31-preliminary-01` |
| Status | `proposed-preliminary` |
| Scope | Epic 3 local inference runtime/model, semantic recognition, ingredient capability, weight capability, and resource-budget decision |
| Production activation | **Blocked until user acceptance/signature and remaining gaps close** |
| Measurement harness | [`tools/g3-benchmark/README.md`](../../tools/g3-benchmark/README.md) |
| Research record | [`docs/research/epic-3-food-recognition-preprocessing-camera.md`](../../docs/research/epic-3-food-recognition-preprocessing-camera.md) |
| Evidence date | 2026-07-31 |

This document records reproducible physical-device evidence. It is not a
signature and does not activate a production runtime. It now includes a
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
| Android broad-tier candidate | Honor DNY-NX9; Android 16/API 36; `arm64-v8a`; explicit ADB mDNS serial `adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp` | ONNX Runtime Android `1.28.0`; CPU; available processor threads | fresh `5/5`, accuracy `1.0` under current `0.55` floor | visible ingredients `unsupported`; plated weight `not-evaluable` | session init `2,922 ms`; p50 `198 ms`; p95 `287 ms`; max `535 ms`; PSS `26,445 -> 271,125 KB`; battery current `-1,287 -> -1,563 µA`; thermal `0 -> 0` |
| iOS high-tier candidate | iPhone 14 Pro; iOS 26.5.2; `arm64e`; UDID `00008120-0012295934E0C01E` | ONNX Runtime Objective-C `1.27.0`; XNNPACK; 2 threads | fresh `5/5`, accuracy `1.0` | visible ingredients `unsupported`; plated weight `not-evaluable` | session init `595 ms`; p50 `87 ms`; p95 `89 ms`; max `89 ms`; resident `261,200 -> 252,864 KB`; thermal `0 -> 0`; battery level `0.85 -> 0.85` |

The Android run above is the latest fresh physical run after the device was
reconnected. The correctly classified sushi fixture scored `0.592399`, so the
declared floor remains `0.55` rather than `0.60`. In fixture order, the
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

The current-HEAD correctness rerun after commit `5f6c8772` reproduced those
Android results on the explicit serial. The paired physical iPhone 14 Pro
also passed the automatic-signing Xcode acceptance scheme `5/5` and the
binary-bound `iosArm64` fixture runner `5/5` (3 normalization, 2 bridge) on
iOS 26.5.2. These records are local-only because the checkout intentionally
preserves the user's untracked manual-testing presentation; they are
correctness evidence and do not close the signed runtime/model, ingredient,
or weight gates.

The latest iOS result was obtained from a freshly installed, automatically
signed `SuperNutritionG3` app using the configured Apple development team after
terminating the prior runner process. All ten fixtures were loaded from the
bundle, and the result file was copied from the app-owned container with
`devicectl`. In the same run, the composite top-1 dish labels were
`filet_mignon`, `grilled_salmon`, `fried_rice`, `bibimbap`, and `chicken_curry`
in fixture order. These are exploratory single-label outputs, not ingredient
predictions, and are not used as ingredient quality scores.

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

These are proposed acceptance budgets, not yet signed production
requirements.

| Budget | Proposed bound | Android | iOS |
| --- | --- | --- | --- |
| Session initialization | <= 500 ms cold start | **fail**, 2,922 ms | **fail**, 595 ms |
| Inference latency | p95 <= 100 ms; max <= 150 ms after warm-up | **fail**, 287/535 ms | pass, 89/89 ms |
| Peak process memory | <= 160 MB Android PSS; <= 160 MB iOS resident | **fail**, 271,125 KB | **fail**, 252,864 KB |
| Thermal | no serious/critical transition | pass, 0 -> 0 | pass, 0 -> 0 |
| Battery | <= 1% over 50 inferences plus native trace where available | sampled current `-1,287 -> -1,563 µA`; energy/percentage open | level unchanged; current/energy trace open |
| Model integrity | pinned SHA before session creation; mismatch fails closed | pass | pass |
| Network/privacy | no photo upload or product API call | pass | pass |

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

## Required before `signed`

- Review and accept the fresh Android semantic/resource result, including its
  failed proposed initialization, latency, and memory budgets.
- Add iOS battery/energy evidence from an approved native trace or equivalent.
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
- Have the engineering and product owners accept the budgets and sign below.

## Acceptance and signature

| Role | Name | Decision | Date | Signature/reference |
| --- | --- | --- | --- | --- |
| Engineering owner | pending | pending | pending | pending |
| Product owner | pending | pending | pending | pending |

Until both rows are completed, G-3 remains open and E3.2/E3.3/E3.4 remain
implementation-ready/in-progress rather than production-complete.
