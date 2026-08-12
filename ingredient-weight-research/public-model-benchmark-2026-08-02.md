# Public model benchmark — 2026-08-02

This is a research-only benchmark on the locally cached public samples. It is
not consented-user data, not phone-capture evidence, and not G3 or production
quality evidence.

## Command

The run used `ingredient-weight-research/public_model_benchmark.py` with:

- 300 FoodSeg103 image/mask pairs from the cached first training shard;
- 300 Nutrition5k overhead RGB/depth plates from the cached official test-ID
  list;
- the repository's exported YOLO-World v2, MobileSAM encoder, and MobileSAM
  decoder ONNX assets;
- ONNX Runtime 1.23.2, NumPy 2.5.1, and Pillow 12.3.0 on the host.

The generated JSON is kept with the research cache at
`/tmp/supernutrition-public-research-20260802/public-model-benchmark.json`.

## Results

| Candidate / baseline | Dataset | Metric | Result | Interpretation |
|---|---|---|---:|---|
| YOLO-World v2 | FoodSeg103, n=300 | micro / macro binary foreground pixel F1 | **0.6171 / 0.5607** | Detected boxes rasterized as foreground; not class-aware or instance segmentation |
| MobileSAM | FoodSeg103, n=300 | micro / macro binary foreground pixel F1 | **0.6928 / 0.6557** | Fixed centre positive prompt plus padding negative prompt; class-agnostic mask, not ingredient recognition |
| RGB-D linear mass baseline | Nutrition5k, 200 fit / 100 holdout | total-mass MAE / p90 absolute error | **73.54 g / 157.01 g** | Small public-data baseline using RGB statistics and depth statistics; not a released model |
| Train-slice median mass baseline | Nutrition5k, same 100 holdout | total-mass MAE / p90 absolute error | **116.11 g / 260.80 g** | Reference lower-complexity baseline |

YOLO-World produced 969 detections over the 300 FoodSeg103 images. The mask
metric is deliberately described as *box-derived foreground overlap*: the
model does not emit segmentation masks, and the FoodSeg103 labels are semantic
class masks. MobileSAM's prompt is fixed and is not ground-truth-guided, so its
score must not be read as an ingredient-segmentation score.

Neither ONNX candidate has a mass head. The mass result therefore comes from a
separate linear baseline trained and evaluated only inside the downloaded
Nutrition5k sample. Its split is deterministic within the sample and is not the
official Nutrition5k train/test protocol; the result is exploratory.

## NutritionVerse-Real dataset screen

NutritionVerse-Real was added to the research cache for a source/data screen,
not as a model run. The primary paper reports 889 iPhone 13 Pro Max images from
251 dishes and 45 food types, measured ingredient weights, and human masks for
four randomly selected views per ten-view scene. Kaggle's dataset metadata
reports `CC BY-NC-SA 4.0`. That non-commercial license, the 251-dish count, and
the absence of the project's opaque consent, scale-QC, withdrawal, and split
manifest prevent it from serving as a production corpus or model-selection
artifact. The downloaded archive is SHA-256
`22910bc84cb5840cb4a66be20d71ce2b7ba51f1d1a7f62108debf78fe7180780`; its
metadata contains 1,184 positive per-ingredient weights and its published
Train/Val split keeps dish IDs separate, but has no calibration partition. No
G3 metric is assigned to it.

## Decision

Keep both ONNX artifacts as research references only. Do not promote either
metric to the v2 model contract, G3 signoff, or release acceptance. The next
credible mass/ingredient quality gate remains the post-release consented,
weighed component-capture feature and its isolated evaluation split.

## Provenance and limitations

- FoodSeg103 provenance and its pixel-wise benchmark description are recorded
  in [`public-research-acquisition-2026-08-02.md`](public-research-acquisition-2026-08-02.md)
  and the [official FoodSeg103 repository](https://github.com/LARC-CMU-SMU/FoodSeg103-Benchmark-v1).
- Nutrition5k provenance, overhead RGB-D data, ingredient mass labels, and
  licensing caveats are recorded in the same acquisition note and the
  [official Nutrition5k repository](https://github.com/google-research-datasets/Nutrition5k).
- Public data remains subject to dataset-specific terms and legal review. No
  public image or label is copied into the application runtime or product
  corpus.
