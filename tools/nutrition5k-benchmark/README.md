# Nutrition5k fusion benchmark

This directory contains a deterministic 100-image benchmark for the local
photo-analysis PoC. It downloads only the official Nutrition5k overhead RGB
frames needed for the sample, creates plate-interior crops, and compares the
PoC component proposals with and without the current hard dish-prior gate.

## Source and sample

- Dataset: [official Nutrition5k repository](https://github.com/google-research-datasets/Nutrition5k)
- Source objects: `imagery/realsense_overhead/<dish_id>/rgb.png`
- Split: `rgb_test`
- Sample: 100 eligible dish IDs, deterministic random sample without replacement
- Seed: `20260803`
- Local data: `data/nutrition5k-100/`

The raw overhead frames are 640x480. The generated `dish_crops/` images use
the normalized crop box `[0.25, 0.12, 0.92, 0.86]` and an elliptical mask. This
removes the fixed rig border and QR markers while retaining plate context. It
is preprocessing, not ground-truth food segmentation.

## Reproduce

Dependencies are isolated through `uv`:

```bash
uv run --with Pillow==12.0.0 python tools/nutrition5k-benchmark/download_sample.py --count 100

uv run --with numpy==2.1.3 --with onnxruntime==1.23.2 --with Pillow==12.0.0 \
  python tools/nutrition5k-benchmark/compare_fusion.py \
  --manifest tools/nutrition5k-benchmark/data/nutrition5k-100/manifest.json \
  --dish-model tools/g3-benchmark/android-runner/app/src/main/assets/swin-finetuned-food101-matmul-int8.onnx \
  --dish-labels tools/g3-benchmark/food101-labels.txt \
  --region-model tools/g3-benchmark/android-runner/app/src/main/assets/yolov8s-worldv2-coco.onnx \
  --output tools/nutrition5k-benchmark/data/nutrition5k-100/comparison.json
```

`compare_fusion.py` mirrors the host-visible PoC behavior: Food-101 top-1
dish classification, YOLO-World v2 COCO proposals, confidence filtering,
per-label NMS, and the exact prior map in
`tools/g3-benchmark/android-runner/app/src/main/java/com/supernutrition/g3benchmark/poc/PocDishIngredientPriors.java`.
The two strategies are:

- `no_fusion`: raw filtered detector proposals;
- `hard_top1_dish_prior`: retain only proposals whose exact label appears in
  the top-1 dish prior, while retaining rejected proposals in the JSON debug
  record.

The host run uses ONNX Runtime `1.23.2` with `CPUExecutionProvider`. It is
not Android physical-device evidence.

## Evaluation boundary

Nutrition5k ingredient metadata describes the recipe and is not a per-pixel
annotation of what is visibly detected in a particular region. The comparison
therefore scores only the five labels currently supported by this PoC:
`apple`, `banana`, `broccoli`, `carrot`, and `orange`. The reported metrics are
a weak recipe-level proxy, not semantic segmentation or ingredient-detection
quality. MobileSAM is not run because its class-agnostic mask does not change
proposal identity.

The complete per-case manifest, model hashes, crop policy, raw/fused regions,
and weak-target calculations are in
`data/nutrition5k-100/manifest.json` and
`data/nutrition5k-100/comparison.json`.

## E0 policy replay

The deterministic policy replay consumes `comparison.json`, so it does not
rerun ONNX inference or change the 100-image sample:

```bash
python3 tools/nutrition5k-benchmark/replay_policy.py \
  --comparison tools/nutrition5k-benchmark/data/nutrition5k-100/comparison.json \
  --output tools/nutrition5k-benchmark/data/nutrition5k-100/policy-replay.json
```

The five replay arms are `no_fusion`, the existing `hard_top1_dish_prior`,
`top1_soft_support`, `topk_union`, and `bounded_late_fusion`. All non-hard
arms keep raw visual regions and store dish-prior candidates separately as
composition-only suggestions. The replay is not calibrated and its
Nutrition5k scores remain weak recipe-level proxy metrics.

E0 verification on 100 cases:

| Strategy | Overlay regions | Images with overlays | Visual weak-proxy F1 | Visual + composition weak-proxy F1 | Prior-only suggestions |
|---|---:|---:|---:|---:|---:|
| `no_fusion` | 200 | 69 | 25.00% | 25.00% | 0 |
| `hard_top1_dish_prior` | 153 | 54 | 22.02% | 22.02% | 0 |
| `top1_soft_support` | 200 | 69 | 25.00% | 28.99% | 10 |
| `topk_union` | 200 | 69 | 25.00% | 28.97% | 17 |
| `bounded_late_fusion` | 200 | 69 | 25.00% | 28.97% | 17 |

The apparent union uplift is not visual accuracy: it includes prior-only
recipe hypotheses and must not be presented as `From this photo`. The useful
E0 result is structural: soft/late policies preserve all 200 raw visual
regions while exposing composition hypotheses separately; the hard prior
still removes 47 regions. See `data/nutrition5k-100/policy-replay.json` for
per-case provenance and decision reasons.

## E1 visible-evidence audit

The deterministic audit slice is selected with:

```bash
python3 tools/nutrition5k-benchmark/select_audit_cases.py \
  --comparison tools/nutrition5k-benchmark/data/nutrition5k-100/comparison.json \
  --replay tools/nutrition5k-benchmark/data/nutrition5k-100/policy-replay.json \
  --output tools/nutrition5k-benchmark/data/nutrition5k-100/visible-audit.json
```

The slice contains all 16 hard-gate conflicts and 34 deterministic controls.
Two independent model-assisted reviewers covered the 16 conflicts and one
reviewer covered the controls. All 50 cases and all normalized regions passed
schema validation. The review artifact is explicitly
`model_assisted_pending_human_adjudication`: it is not human ground truth or
calibrated probability evidence. All 16 conflict cases remain flagged for
adjudication because exact label overlap is insufficient when reviewers use
different safe descriptions for the same visible food.

Results are stored in
`data/nutrition5k-100/visible-audit-reviewers.json`. This audit supports the
direction of suggestion-first fusion and ontology expansion, but does not by
itself authorize production activation.

## E2 disjoint 200-image holdout

The second sample is a locked holdout: its 200 `dish_id` values are excluded
from the original 100-image manifest before sampling. It uses the same source
SHA, crop policy, ONNX model hashes, thresholds, and weak recipe-level proxy.

```bash
uv run --with Pillow==12.0.0 python tools/nutrition5k-benchmark/download_sample.py \
  --count 200 --seed 20260804 --sample-prefix nutrition5k-holdout \
  --exclude-manifest tools/nutrition5k-benchmark/data/nutrition5k-100/manifest.json \
  --output-dir tools/nutrition5k-benchmark/data/nutrition5k-200-holdout

uv run --with numpy==2.1.3 --with onnxruntime==1.23.2 --with Pillow==12.0.0 \
  python tools/nutrition5k-benchmark/compare_fusion.py \
  --manifest tools/nutrition5k-benchmark/data/nutrition5k-200-holdout/manifest.json \
  --dish-model tools/g3-benchmark/android-runner/app/src/main/assets/swin-finetuned-food101-matmul-int8.onnx \
  --dish-labels tools/g3-benchmark/food101-labels.txt \
  --region-model tools/g3-benchmark/android-runner/app/src/main/assets/yolov8s-worldv2-coco.onnx \
  --output tools/nutrition5k-benchmark/data/nutrition5k-200-holdout/comparison.json

python3 tools/nutrition5k-benchmark/replay_policy.py \
  --comparison tools/nutrition5k-benchmark/data/nutrition5k-200-holdout/comparison.json \
  --output tools/nutrition5k-benchmark/data/nutrition5k-200-holdout/policy-replay.json
```

Holdout verification found 200/200 raw frames and crops, zero ID overlap with
the development sample, and matching source/model hashes. The selected
`bounded_late_fusion` arm kept all raw visual proposals and added composition
hypotheses separately:

| Strategy | Regions | Images with regions | Visual weak-proxy F1 | Visual + composition weak-proxy F1 | Prior-only suggestions |
|---|---:|---:|---:|---:|---:|
| `no_fusion` | 420 | 133 | 28.67% | 28.67% | 0 |
| `hard_top1_dish_prior` | 355 | 114 | 26.77% | 26.77% | 0 |
| `top1_soft_support` | 420 | 133 | 28.67% | 29.45% | 13 |
| `topk_union` | 420 | 133 | 28.67% | 30.23% | 32 |
| `bounded_late_fusion` | 420 | 133 | 28.67% | 30.23% | 32 |

The holdout does not prove a visual-accuracy improvement: the visual metric is
unchanged from `no_fusion`; the composition uplift is a weak recipe proxy and
is not a visual claim. It does show that the hard gate loses 65 regions on this
disjoint sample, while the bounded policy preserves them.

## E3 physical Android PoC verification

The research PoC was rebuilt and run on physical DNY-NX9
(`192.168.0.150:43381`) with the `bounded_late_fusion` policy. PoC unit tests
passed (22 Gradle tasks), the debug APK assembled and installed, and all five
user-sample photos completed with `success`:

| Photo | Top-1 dish candidate | Top-3 dish candidates | Regions |
|---|---|---|---:|
| breakfast bacon/eggs/fruit | `french_toast` 39.7% | `french_toast`, `pancakes`, `omelette` | 3 |
| chicken/salad/vegetables | `pork_chop` 50.1% | `pork_chop`, `grilled_salmon`, `caesar_salad` | 9 |
| fruit/cottage-cheese/vegetables | `beet_salad` 20.1% | `beet_salad`, `cheese_plate`, `gnocchi` | 1 |
| green salad/protein | `greek_salad` 92.5% | `greek_salad`, `beet_salad`, `caesar_salad` | 3 |
| mixed beef/rice/salad/vegetables | `greek_salad` 96.5% | `greek_salad`, `caesar_salad`, `bibimbap` | 5 |

The UI showed the full dish-prior list separately from detector proposals and
kept detector-only proposals for visual review. A grid-cell smoke test also
passed; it added a class-agnostic `grid prompt` region and displayed the
warning that the mask does not identify an ingredient. Full device evidence is
in `data/android-dny-nx9-20260803.json`. This remains research PoC evidence,
not production or human-ground-truth evidence.
