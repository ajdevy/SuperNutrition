# Epic 3 G-3 device benchmark harness

This is a non-production research harness for the signed G-3 decision. It
measures a candidate local runtime on explicit physical Android and iOS
devices and now proves a bounded semantic recognition contract. It does not
change the production inference adapters.

The Android runner compiles the shipped
`mobile/inference/local-android-poc` Java inference source directly. It owns
only its research UI, fixtures, and benchmark-only prior provider, so a
benchmark build cannot silently use an older copy of `PocInferenceEngine`.

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

### Real 300+ case evidence

The public five-fixture evaluator above is not the production-quality path.
For the consented weighed corpus, first validate the redacted manifest:

```bash
python3 tools/g3-benchmark/validate_weighed_corpus.py \
  --require-g3 /path/to/redacted-corpus.json
```

Then score a strict `supernutrition.g3-corpus-predictions.v1` handoff with:

```bash
python3 tools/g3-benchmark/evaluate_consented_corpus.py \
  --require-g3 \
  --manifest /path/to/redacted-corpus.json \
  --predictions /path/to/redacted-predictions.json \
  --output /path/to/mass-quality-evidence.json
```

The evaluator matches visible components one-to-one by canonical label and
normalized-box IoU. It reports ingredient macro/case F1, component and total
mass MAE/relative MAE/p90/bias, coverage, abstention, and partition/device/
lighting/angle slices. It rejects missing/unknown cases, duplicate prediction
IDs, raw paths, invalid boxes, and missing model/calibration provenance. The
command is fail-closed: `pass` is the quantitative evidence layer, while
model/legal/corpus/rollback and Product/Engineering approval remain separate
activation requirements. The schemas are
[`consented-weighed-corpus.schema.json`](consented-weighed-corpus.schema.json)
and [`g3-corpus-predictions.schema.json`](g3-corpus-predictions.schema.json).

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

### Research-only Android candidate activities

`MobileSamActivity` and `YoloWorldActivity` are separate from the G-3
Food-101 runner. They are manual, research-only activities that write an
app-owned JSON record after local ONNX Runtime CPU inference; they do not call
an API or run UI automation. Their non-production model assets are deliberately
not the selected runtime:

| Activity | Asset contract | Capability boundary |
| --- | --- | --- |
| `MobileSamActivity` | MobileSAM encoder SHA-256 `580f5fb648ea1062c0aabc26217aed56921985f03f0cbbd852bba81d760cc749`; decoder SHA-256 `8976b90a87ba50a6a72217a5ff994f7d25ce16f2229fcc1ed259e1294c622ffe` | Point-prompted class-agnostic masks only; no ingredient identity or mass |
| `YoloWorldActivity` | 640 px current PoC YOLO-World v2 ONNX SHA-256 `d27ac81640ca470cc7e42edd638b6626ac6b4f4dcce74aabb6e09cd02a13c84f`; embedded COCO-80 vocabulary | Detector-only; no complete ingredient vocabulary, normalization, or mass |
| `YoloXNanoActivity` | official YOLOX-Nano 416 px COCO ONNX SHA-256 `c789161ed43c8269fcd4e67c67eeeb4e80c622da2eb296a20bc6007bd18a0b7d`; BGR letterbox preprocessing | Detector-only; no complete ingredient vocabulary, normalization, or mass |

For an explicit connected device, launch one activity and retrieve its local
record with an ADB serial supplied by the operator:

```bash
ANDROID_DEVICE_SERIAL=your-adb-serial
adb -s "$ANDROID_DEVICE_SERIAL" shell am start -W \
  -n com.supernutrition.g3benchmark/.MobileSamActivity
adb -s "$ANDROID_DEVICE_SERIAL" exec-out run-as com.supernutrition.g3benchmark \
  cat files/mobile-sam-result.json
```

Replace `MobileSamActivity`/`mobile-sam-result.json` with
`YoloWorldActivity`/`yolo-world-v2-result.json` for the detector screen. The
physical Android outcomes and the explicit unexecuted statuses for FoodSAM and
FoodSeg103 are recorded in
[`ingredient-weight-research/android-candidate-device-evidence-2026-08-01.json`](../../ingredient-weight-research/android-candidate-device-evidence-2026-08-01.json).

Use `YoloXNanoActivity`/`yolox-nano-result.json` for the official YOLOX-Nano
COCO screen. The model is downloaded by `download-assets.sh` and is never a
product activation: the test is a five-fixture deployment comparison only.
The 2026-08-11 physical comparison with the current YOLO-World artifact is in
[`ingredient-weight-research/yolox-nano-android-evidence-2026-08-11.json`](../../ingredient-weight-research/yolox-nano-android-evidence-2026-08-11.json).

### Full photo-to-grid research PoC

The standalone runner also contains `PocActivity`, a manual, research-only
vertical slice:

`camera photo -> dish top-5 -> 3x3 grid hints -> bounded region proposals -> prompted mask overlay`.

It deliberately keeps the model capabilities visible in the UI:

- Food-101 supplies a single dish classification, not a decomposition of the
  dish into ingredients.
- YOLO-World uses its embedded COCO-80 vocabulary, filtered to a small set of
  component-like labels such as `carrot` and `broccoli`; composite labels such
  as `pizza` and `sandwich` are excluded and the result is still not a
  complete ingredient inventory.
- MobileSAM refines a selected proposal or grid cell into a class-agnostic
  mask; the mask does not prove the ingredient identity.
- No model in this PoC estimates grams. The mass state is explicitly
  `unavailable` until a calibrated/tared weighing protocol and a selected mass
  model exist.

Build and launch it on an unlocked Android device:

```bash
ANDROID_DEVICE_SERIAL=your-adb-serial \
  tools/g3-benchmark/run-poc-android.sh
```

The PoC is intentionally outside the production mobile roots and does not
change the Product/Engineering G-3 activation decision.

## Meaningful recognition evidence

### Android

Fresh physical run on the approved Honor DNY-NX9 Android 16/API 36
`arm64-v8a` tier, connected over an explicit operator-supplied ADB serial and
using ONNX Runtime Android `1.28.0` on CPU. The exact device serial is bound in
the local-only evidence record, not this repository guide. The pinned model
and manifest digests were checked before the session. The semantic confidence
floor is `0.55`, so the correctly classified sushi fixture at `0.592399` is
included.

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

The checked-in runner currently pins ONNX Runtime Objective-C `1.28.0`. The
physical result below is historical evidence from the earlier `1.27.0` runner
and must be rerun before being used as a current runtime claim. It was a fresh
physical run on the approved iPhone 14 Pro iOS 26.5.2 `arm64e` tier,
automatically signed with the local development team. The exact UDID is bound
in the local-only evidence record, not this repository guide. It used XNNPACK.

The five semantic top-1 labels were exact: `steak`, `greek_salad`,
`grilled_salmon`, `sushi`, and `waffles`; semantic accuracy was `5/5` at the
`0.55` threshold. The latest trace-bound rerun recorded session init `572 ms`,
p50 `83 ms`, p95 `84 ms`, max `84 ms`, and resident memory
`255,744 -> 247,360 KB`. Thermal status stayed `0 -> 0` and battery level
stayed `1 -> 1`.

The iOS run is therefore semantic-pass and latency-pass, but it exceeds the
proposed `160 MB` resident-memory budget. A native Xcode Instruments `Power
Profiler` trace was captured in the operator's local temporary evidence store;
its process power table shows zero Wi-Fi and cellular receive/transmit bytes.
The trace does not expose a calibrated joule or battery-percentage delta in the
current export, so the numeric battery budget remains open.

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

The strict budgets below remain comparison targets, not the approved runtime
envelope.

| Budget | Proposed bound | Android evidence | iOS evidence |
| --- | --- | --- | --- |
| Session initialization | <= 500 ms cold start | fail: 2,611 ms | fail: 572 ms |
| Inference latency | p95 <= 100 ms; max <= 150 ms | fail: 279/334 ms | pass: 84/84 ms |
| Peak process memory | <= 160 MB | fail: 273,547 KB PSS | fail: 247,360 KB resident |
| Thermal | no serious/critical transition | pass: 0 -> 0 | pass: 0 -> 0 |
| Battery | <= 1% over 50 inferences plus native trace | sampled current `-1,287 -> -1,563 µA`; energy/percentage still open | native `Power Profiler` trace captured; calibrated energy/percentage open |
| Model integrity | pinned SHA before session creation | pass | pass |
| Network/privacy | no photo upload or product API call | pass | pass |

## Current decision

The harness now proves meaningful single-dish recognition on both physical
platforms. Engineering owner AJ (`ajdevy`) approved the High local-wait tier
for the current dish-only harness: cold init <= 15 s, analysis <= 45 s, and
peak process memory <= 512 MB on the measured device matrix. This is a
staged/cancellable engineering envelope, not acceptance of the stricter
comparison budgets.

G-3 remains `engineering-approved-pending-product`. Recognition quality,
multi-object recognition, nutrition accuracy, and production readiness are
separate decisions. The approval does not select FPB/YOLOv12 for mobile,
approve ingredient/grams capability, or apply to unmeasured devices.

Before final product sign-off, the product owner must accept or reject the
High tier, decide whether the native iOS trace is sufficient for the numeric
battery expectation, choose supported low-memory behavior, and decide whether
ingredient/grams remains a later contract/story. That capability still needs a
consented, weighed multi-component meal corpus and a detector/segmenter. A
10,000-image robustness sample can be drawn from the 101,000 Food-101 records,
but those are evaluation images rather than 10,000 independent technical
sources.
