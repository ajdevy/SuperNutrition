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
DEVELOPMENT_TEAM=Z4JKB6L22Z \
IOS_DEVICE_UDID=00008120-0012295934E0C01E \
  tools/g3-benchmark/run-ios.sh
```

The scripts print the redacted JSON result. They do not upload photos, call a
product API, or use UI automation. The iOS script uses local Xcode signing
and `devicectl` only to install, launch, and copy the app-owned result file.

## Meaningful recognition evidence

### Android

Physical run on Honor DNY-NX9, Android 16/API 36, `arm64-v8a`, serial
`AU3N025429006787`, ONNX Runtime Android `1.28.0`, CPU. The device was
disconnected during the latest rerun attempt, so this is the previous fresh
run of the same pinned model; the only later change was lowering the explicit
semantic confidence floor from `0.60` to `0.55` after observing a correct sushi
top-1 at `0.592399`.

The five semantic top-1 labels were exact: `steak`, `greek_salad`,
`grilled_salmon`, `sushi`, and `waffles`. Under the current `0.55` contract the
run is `5/5`, accuracy `1.0`. Its resource measurements were session init
`1,839 ms`, p50 `187 ms`, p95 `290 ms`, max `310 ms`, and PSS
`63,914 -> 274,681 KB`. This proves recognition but does not pass the
proposed resource budgets.

### iOS

Fresh physical run on iPhone 14 Pro, iOS 26.5.2, `arm64e`, UDID
`00008120-0012295934E0C01E`, automatically signed with the local development
team. Runtime was ONNX Runtime Objective-C `1.27.0` with XNNPACK.

The five semantic top-1 labels were exact: `steak`, `greek_salad`,
`grilled_salmon`, `sushi`, and `waffles`; semantic accuracy was `5/5` at the
`0.55` threshold. The latest run recorded session init `559 ms`, p50 `83 ms`,
p95 `85 ms`, max `85 ms`, and resident memory `260,240 -> 252,016 KB`.
Thermal status stayed `0 -> 0` and battery level stayed `1.000 -> 1.000`.

The iOS run is therefore semantic-pass and latency-pass, but it exceeds the
proposed `160 MB` resident-memory budget.

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
| Session initialization | <= 500 ms cold start | fail: 1,839 ms | fail: 559 ms |
| Inference latency | p95 <= 100 ms; max <= 150 ms | fail: 290/310 ms | pass: 85/85 ms |
| Peak process memory | <= 160 MB | fail: 274,681 KB PSS | fail: 252,016 KB resident |
| Thermal | no serious/critical transition | pass: 0 -> 0 | pass: 0 -> 0 |
| Battery | <= 1% over 50 inferences plus native trace | prior run sampled current; needs fresh rerun | level unchanged; current/energy trace open |
| Model integrity | pinned SHA before session creation | pass | pass |
| Network/privacy | no photo upload or product API call | pass | pass |

## Current decision

The harness now proves meaningful single-dish recognition on both physical
platforms, but G-3 remains `proposed-preliminary` and unsigned. Recognition
quality, memory/latency budgets, multi-object recognition, nutrition accuracy,
and production readiness are separate decisions.

Before signing, the owner must repeat Android after reconnecting the device,
add an approved iOS energy trace, choose supported low-memory tiers, and run an
approved consented multi-component meal corpus. A 10,000-image robustness
sample can be drawn from the 101,000 Food-101 records, but those are evaluation
images rather than 10,000 independent technical sources.
