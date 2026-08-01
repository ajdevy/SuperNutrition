# Epic 3 G-3 device benchmark harness

This is a non-production research harness for the signed G-3 decision. It
measures a candidate local runtime on explicit physical Android and iOS
devices and now proves a bounded semantic recognition contract. It does not
change the production inference adapters.

The harness deliberately separates two fixture groups:

- five composite meal photographs for exploratory behavior only;
- five single-dish photographs with explicit Food-101 ground truth for
  meaningful top-1 recognition evidence.

The composite group must not be described as multi-object recognition. A
classifier-only model can return one dish label for a plate containing several
foods; it does not establish independent detection, ingredient extraction,
portion estimation, or nutrition correctness.

## Ingredient and weight benchmark

The five composite fixtures also form the first meal-composition corpus:
[`meal-benchmark-manifest.json`](meal-benchmark-manifest.json). Its ingredient
labels are conservative, manually reviewed *visible components* only. They do
not claim hidden recipe ingredients, oil, dressing composition, or nutrition
values.

The manifest is an oracle-ready benchmark for a future detector/segmenter and
mass estimator. It measures ingredient-set precision/recall/F1 per meal,
per-ingredient mass MAE/p90 absolute error, and optional total plated-mass MAE
plus p90 absolute error in grams. The current Food-101
runner intentionally emits an explicit capability result instead of a fake
score:

- `ingredient_benchmark.status = unsupported`: the model returns one dish
  label and no ingredient candidates, boxes, or masks;
- `weight_benchmark.status = not-evaluable`: the public photos have no
  calibrated scale reading and the model returns no mass estimate.

Evaluate future predictions with the dependency-free host evaluator:

```bash
python3 tools/g3-benchmark/evaluate-meal-benchmark.py \
  --manifest tools/g3-benchmark/meal-benchmark-manifest.json
```

Predictions, when a component model exists, use one object per fixture:

```json
{
  "cases": {
    "steak-side-plate": {
      "ingredients": ["beef_steak", "quinoa", "green_bean"],
      "ingredient_masses_g": {
        "beef_steak": 180,
        "quinoa": 120,
        "green_bean": 70
      },
      "total_mass_g": 420
    }
  }
}
```

Weight scoring remains blocked until each image is captured with tared scale
records for every visible ingredient, plus the optional total. The controlled
protocol is: tare the empty vessel, plate the exact meal, weigh each component
before plating and the final plate, record scale resolution, then capture
overview and oblique photos on each device tier. Do not infer grams from pixels
or from a generic dish label.

| Fixture group | Fixtures | Acceptance meaning |
| --- | --- | --- |
| Exploratory composite | steak with side plate, fish with side plate, chopped salad, Buddha bowl, salmon/grain plate | exercised on-device; no expected label and no accuracy claim |
| Semantic single dish | steak, Greek salad, grilled salmon, sushi, waffles | exact top-1 label match and confidence `>= 0.55` on all five |

## Pinned model and input contract

The candidate is the Food-101 fine-tuned Swin ONNX model from the
[Hugging Face model card](https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX).
The source FP32 model is pinned by SHA-256 in `download-assets.sh`; the device
artifact is a reproducible MatMul-only dynamic QInt8 derivative:

- model: `swin-finetuned-food101-matmul-int8.onnx`;
- model SHA-256:
  `9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df`;
- labels: 101 lines in the official Food-101 order;
- preprocessing: RGB, centered square crop, bilinear resize to `224 x 224`,
  ImageNet mean/std normalization;
- confidence: softmax top-1 must be at least `0.55` for semantic acceptance.

Food-101 is a 101-class dish classification benchmark, not an ingredient or
nutrition dataset. See the [ETH Zurich dataset card](https://huggingface.co/datasets/ethz/food101)
for its class map, source limitations, label noise, and missing nutrition
metadata.

## Run on physical devices

Download the model and fixtures, verifying every file before use:

```bash
tools/g3-benchmark/download-assets.sh
```

Android requires an explicit serial and a connected, unlocked device:

```bash
ANDROID_DEVICE_SERIAL=AU3N025429006787 \
  tools/g3-benchmark/run-android.sh
```

iOS requires the physical UDID and a locally configured Apple development
account/team in Xcode:

```bash
DEVELOPMENT_TEAM=REDACTED_TEAM_ID \
IOS_DEVICE_UDID=REDACTED_PHYSICAL_DEVICE_UDID \
  tools/g3-benchmark/run-ios.sh
```

The scripts print the redacted JSON result. They do not upload photos, call a
product API, or use UI automation. The iOS script uses local Xcode signing
and `devicectl` only to install, launch, and copy the app-owned result file.

## Meaningful recognition evidence

### Android

Fresh physical run on Honor DNY-NX9, Android 16/API 36, `arm64-v8a`, connected
over the explicit ADB mDNS serial
`adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp`, using ONNX Runtime Android
`1.28.0` on CPU. The pinned model and manifest digests were checked before the
session. The semantic confidence floor is `0.55`, so the correctly classified
sushi fixture at `0.592399` is included.

The five semantic top-1 labels were exact: `steak`, `greek_salad`,
`grilled_salmon`, `sushi`, and `waffles`. Under the current `0.55` contract the
run is `5/5`, accuracy `1.0`. The latest rerun recorded session init `2,922 ms`,
p50 `198 ms`, p95 `287 ms`, max `535 ms`, and PSS
`26,445 -> 271,125 KB`; sampled battery current was `-1,287 -> -1,563 µA`
and thermal status stayed `0 -> 0`. This proves recognition but does not pass
the proposed initialization, latency, or memory budgets. The exploratory
composite top-1 labels, in fixture order, were `steak`, `grilled_salmon`,
`fried_rice`, `bibimbap`, and `chicken_curry`; these are single dish labels,
not ingredient predictions.

On the same physical device, the rebuilt acceptance APK/test APK were run by
the Gradle-connected tasks `connectedUiAcceptanceDebugAndroidTest` (`15/15`)
and `connectedAndroidDeviceTest` (`4/4`). These are physical-device
correctness records, separate from the G-3 model/resource result.

### iOS

Fresh physical run on iPhone 14 Pro, iOS 26.5.2, `arm64e`, UDID
`REDACTED_PHYSICAL_DEVICE_UDID`, automatically signed with the local development
team. Runtime was ONNX Runtime Objective-C `1.27.0` with XNNPACK.

The five semantic top-1 labels were exact: `steak`, `greek_salad`,
`grilled_salmon`, `sushi`, and `waffles`; semantic accuracy was `5/5` at the
`0.55` threshold. The latest controlled rerun recorded session init `595 ms`,
p50 `87 ms`, p95 `89 ms`, max `89 ms`, and resident memory
`261,200 -> 252,864 KB`. Thermal status stayed `0 -> 0` and battery level
stayed `0.850 -> 0.850`.

The iOS run is therefore semantic-pass and latency-pass, but it exceeds the
proposed `160 MB` resident-memory budget. A subsequent trace-bound physical run
completed semantic `5/5`, init `572 ms`, p50/p95/max `83/84/84 ms`, resident
memory `255,744 -> 247,360 KB`, thermal `0 -> 0`, and battery level `1 -> 1`.
The native Xcode Instruments `Power Profiler` trace is at
`/tmp/supernutrition-ios-power-20260801.trace`; its process power table shows
zero Wi-Fi and cellular receive/transmit bytes. The trace does not expose a
calibrated joule or battery-percentage delta in the current export, so the
numeric battery budget remains open.

For transparency, the same iOS run's composite top-1 dish labels were
`filet_mignon` for the steak plate, `grilled_salmon` for the fish plate,
`fried_rice` for the chopped salad, `bibimbap` for the Buddha bowl, and
`chicken_curry` for the fish/grain plate. These are single dish labels, not
ingredient predictions; they are recorded as exploratory outputs only.

## Preprocessing and camera findings

The host experiment compared direct square crop, shortest-side resize, two
letterbox/resize alternatives, autocontrast, contrast adjustment, and
sharpening on the five semantic fixtures. Every variant was `5/5`; direct
centered square crop had the highest mean confidence in this small sample.
No filter is enabled by default based on five curated images. The detailed
research record is
[`docs/research/epic-3-food-recognition-preprocessing-camera.md`](../../docs/research/epic-3-food-recognition-preprocessing-camera.md).

The product camera bridges now also:

- set Android target rotation and run best-effort centered AF/AE/AWB metering
  before capture;
- use the iOS photo session preset and request continuous autofocus,
  auto-exposure, and auto-white-balance when supported;
- avoid hard-coded torch, zoom, exposure, or white-balance settings that could
  harm other lighting conditions.

## Proposed budgets

These are proposed acceptance budgets, not signed production requirements.

| Budget | Proposed bound | Android evidence | iOS evidence |
| --- | --- | --- | --- |
| Session initialization | <= 500 ms cold start | fail: 2,922 ms | fail: 595 ms |
| Inference latency | p95 <= 100 ms; max <= 150 ms | fail: 287/535 ms | pass: 89/89 ms |
| Peak process memory | <= 160 MB | fail: 271,125 KB PSS | fail: 252,864 KB resident |
| Thermal | no serious/critical transition | pass: 0 -> 0 | pass: 0 -> 0 |
| Battery | <= 1% over 50 inferences plus native trace | sampled current `-1,287 -> -1,563 µA`; energy/percentage still open | native `Power Profiler` trace captured; calibrated energy/percentage open |
| Model integrity | pinned SHA before session creation | pass | pass |
| Network/privacy | no photo upload or product API call | pass | pass |

## Current decision

The harness now proves meaningful single-dish recognition on both physical
platforms, but G-3 remains `proposed-preliminary` and unsigned. Recognition
quality, memory/latency budgets, multi-object recognition, nutrition accuracy,
and production readiness are separate decisions.

Before signing, the owner must review and accept the fresh Android result,
review and accept the iOS `Power Profiler` trace (or add a calibrated energy
reading if required), choose supported low-memory tiers, and run an
approved consented multi-component meal corpus. A 10,000-image robustness
sample can be drawn from the 101,000 Food-101 records, but those are evaluation
images rather than 10,000 independent technical sources.
