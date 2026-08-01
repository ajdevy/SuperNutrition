# Ingredient detection, segmentation, and food-weight research

Research date: 2026-07-31

This track is separate from the existing Food-101/G-3 evidence. It answers a
different question: how to produce visible ingredient candidates, regions, and
per-component mass estimates for multi-component meals.

## Executive finding

There is no verified GitHub repository that can be dropped into SuperNutrition
and reliably returns both ingredient identities and grams from an arbitrary
single RGB phone photo under a commercial-friendly license.

The strongest building blocks are complementary:

| Source | What it provides | Evidence / limitation | Decision |
| --- | --- | --- | --- |
| [FoodSeg103 benchmark](https://github.com/LARC-CMU-SMU/FoodSeg103-Benchmark-v1) | 7,118 images, 104 ingredient classes, pixel-wise masks, about 6 ingredient labels/image | Semantic masks, not per-instance mass; code is Apache-2.0, dataset terms need legal verification | Use for semantic pretraining and an ingredient benchmark, not as the only product corpus |
| [FoodInsSeg](https://github.com/jamesjg/FoodInsSeg) | 7,118 images, 103 categories, 119,048 ingredient-level instance masks; COCO polygons and boxes | Dataset is CC BY-NC-SA 4.0 and explicitly academic/non-profit only | Excellent teacher/evaluation reference; do not train a commercial product on it without permission |
| [FoodSAM](https://github.com/jamesjg/FoodSAM) | Food semantic, instance, panoptic, and promptable segmentation; Apache-2.0 model repo | Python/PyTorch pipeline combines SAM, semantic segmentation, and a detector; too heavy and dependency-sensitive for the first mobile runtime | Use offline as a pseudo-label/teacher experiment, not as the on-device model |
| [Nutrition5k](https://github.com/google-research-datasets/Nutrition5k) | 5,006 plates, ingredient list, per-ingredient grams, total mass, RGB-D overhead images, multi-angle video, official split | 181.4 GB; California cafeteria/custom scanning rig; repository is archived; phone-photo domain gap remains | Best public source for mass regression pretraining and benchmark design; must validate on our own corpus |
| [FPB / YOLOv12-FoodWeight](https://github.com/IS2AI/Multitask-Food-Portion-Estimation) | Per-object boxes plus a grams label and a weight-regression head; prediction CSV includes weight | Repository is AGPL-3.0; public dataset/checkpoint licensing must be checked separately; very small public project signal | Run as a research baseline only after a legal gate; do not ship code/weights by default |
| [NutritionVerse-Synth](https://github.com/saeejithnair/nvsynth) | MIT synthetic multi-view scenes and Nutrition5k-shaped metadata generation | Synthetic appearance and mass priors do not prove real-photo accuracy | Optional augmentation and controlled ablations, never the acceptance corpus |
| [Ultralytics segmentation docs](https://github.com/ultralytics/ultralytics) | Practical instance-mask output and training/export path | Framework/model licensing is AGPL-3.0 or commercial; COCO pretraining is not food recognition | Candidate training tooling only after licensing decision |
| [Food201 Android example](https://github.com/janarez/Food201) | On-device Android segmentation experiment using Food201/Food-101-derived classes and a TensorFlow Lite target | Old research project; meal-class segmentation is not ingredient masks or mass; no current iOS path | Useful mobile export precedent, not a model candidate |
| [LiteRT samples](https://github.com/google-ai-edge/litert-samples) | Apache-2.0 Android/iOS examples for classification, detection, and segmentation model execution | Runtime samples do not supply food labels or weight ground truth | Preferred deployment reference once our model is trained |

## What the model must become

The current `photo-inference-engine/v1` emits one dish candidate. It cannot
produce ingredient masks or grams. The required production shape is a staged
pipeline:

1. Image quality and plate/reference geometry gate.
2. Food/ingredient detector or instance/semantic segmenter producing one
   candidate per visible component: canonical label, confidence, box/mask,
   visible/occluded flag.
3. Food identity normalization against a curated nutrition catalog.
4. Per-component portion/mass head using mask area plus camera geometry and,
   where available, depth. The output must include a range, confidence, and an
   abstention reason; a generic VLM must not invent grams.
5. Meal aggregate derived from accepted component estimates, with user review.

For arbitrary RGB-only images, exact grams are not identifiable: density,
hidden ingredients, depth, plate size, preparation, and occlusion are missing.
The product should either capture a reference/depth cue or display an estimate
range and require confirmation. A scale-assisted capture mode is the cleanest
way to create ground truth and the safest route to meaningful weight quality.

## Consented weighed corpus blueprint

Use two phases:

- Pilot: 300 plates to verify the label protocol and train the first detector.
- Acceptance: 500+ additional plates, held out by participant/session/recipe;
  do not split near-duplicate frames across train and test.

Each plate should deliberately cover single items, steak with side dish, fish,
salads, Buddha bowls, rice/curry, soups, sauces, mixed/occluded foods, and
culturally diverse preparation styles. Capture top-down and 30-60 degree views
on the target phones; record optional depth when the device provides it.

Ground truth per case:

- participant consent, deletion/withdrawal identifier, device/camera metadata,
  capture geometry, lighting, and plate/reference dimensions;
- canonical visible ingredient IDs plus unknown/hidden/preparation tags;
- instance polygons or masks and boxes for every visible component;
- tared plate mass, each component mass in grams before plating or after
  controlled separation, final plate mass, and a reconciliation check;
- independent second annotator for identity/mask review and a measured-scale
  audit sample.

Report at least ingredient-set precision/recall/F1, per-class recall, mask
mIoU or instance AP, missed-component rate, abstention calibration, per-item
gram MAE, median and p90 relative error, total-meal gram MAE, and error split by
component size, occlusion, dish type, device, and camera angle.

Proposed experimental gates (not signed G-3 budgets): visible common-food
ingredient F1 >= 0.85, set recall >= 0.80, and for components >=30 g mass MAE
<=25 g plus relative MAE <=25%. Unknown/hidden ingredients must abstain rather
than receive a fabricated label or mass. These thresholds should be re-approved
after the pilot variance is known.

## Relaxed local budget proposal

The user-facing experiment can tolerate slow local inference, but it must never
look hung:

| Tier | Init | Analysis | Peak memory | Behavior |
| --- | ---: | ---: | ---: | --- |
| High | <=15 s | <=45 s | <=512 MB | Full detector + mask + mass stages |
| Mid | <=30 s | <=90 s | <=768 MB | Lower resolution or sequential stages |
| Low | no hard promise | manual/dish-only fallback | device-dependent | Do not load the heavy stack if memory gate fails |

These are exploratory budgets, not a production SLA and not a G-3 signature.
The UI should publish stage copy such as “loading food model”, “finding visible
components”, “estimating portions”, and “checking confidence”, refresh it on a
bounded cadence, and expose Cancel. A hard watchdog should abort after the
analysis budget and preserve a manual/dish-only fallback.

## Recommended next implementation order

1. Freeze the corpus schema and consent/retention protocol.
2. Build an offline evaluator that accepts COCO masks plus per-instance grams;
   run FoodSeg103/FoodInsSeg/Nutrition5k/FPB adapters without importing their
   incompatible data into the product binary.
3. Train or fine-tune a small commercial-cleared detector/segmenter on the
   consented corpus; use the public sets only where license permits.
4. Add a separate mass head and calibration/reference experiment; keep grams
   absent or ranged when calibration is missing.
5. Version a new inference contract for multi-component results and progress
   events; retain the current dish-only contract as a fallback.
6. Export the smallest viable model to ONNX/Core ML/LiteRT or ExecuTorch and
   rerun Android/iOS device evidence under the relaxed exploratory budgets.

No current G-3 status or owner signature is changed by this research note.

## FPB research baseline

FPB/YOLOv12-FoodWeight was the first baseline. The pinned
provenance and license boundary are in
[`fpb-yolov12-foodweight-manifest.json`](fpb-yolov12-foodweight-manifest.json).
Its CSV output can be converted into the existing G-3 evaluator shape with
[`fpb_to_g3_predictions.py`](fpb_to_g3_predictions.py). The adapter preserves
per-box instances and aggregates repeated labels for the current evaluator;
it does not create masks, fix the dataset license, or turn the reported
laboratory MAE into a phone-photo guarantee.

The first five-image CPU smoke run is recorded in
[`fpb-smoke-evidence.json`](fpb-smoke-evidence.json): 5/5 detections, class
accuracy 0.60, mean box IoU 0.805, weight MAE 48.54 g, and p90 absolute weight
error 110.48 g. The published checkpoint does not load with the pinned FPB
repository code because that code lacks `C3k2`; the smoke run therefore uses
current Ultralytics plus a temporary compatibility shim that preserves the
extra grams channel. This is evidence that the research artifact can emit
food classes, boxes, and grams on FPB samples, not a product or G-3 claim.

The follow-up five-photo complex-meal screen is recorded in
[`fpb-yolov12-foodweight-composite-evidence.json`](fpb-yolov12-foodweight-composite-evidence.json).
At the fixed `0.25` confidence threshold it produced eight boxes, but none of
the raw FPB labels matched the conservative visible-component taxonomy:
macro ingredient F1 is `0.0/1.0` across all five meals. The raw grams output
is retained for audit only; it has no tared scale ground truth in these public
photos, so mass MAE is deliberately `not-evaluable`. FPB is therefore **not a
current v2 ingredient or mass candidate**. Its AGPL-3.0 repository and
non-commercial dataset boundary also remain incompatible with direct mobile
shipping.

## Comparative candidate status — 2026-08-01

| Candidate | What was actually exercised | Visible ingredients on five complex meals | Grams | Current decision |
| --- | --- | --- | --- | --- |
| Food-101 Swin INT8 | Physical Android and iOS G-3 harness | `unsupported`: one dish label, no components | `not-evaluable` | Retain only as the dish-classification baseline |
| FPB / YOLOv12-FoodWeight | Reproducible CPU run, 640 px, 0.25 confidence; raw predictions and G-3 evaluator record included | `0.0` macro F1; 0 exact components across 8 boxes | Raw head present; quality `not-evaluable` without scale truth | Reject for the current v2 candidate screen; research evidence only |
| FoodSAM | Official research stack audited; no supported Android artifact for its SAM + semantic + detector composition | No Android score | No mass head | Offline teacher/annotation candidate, not an immediate mobile runtime |
| FoodSeg103 / ReLeM | Official semantic benchmark and model zoo audited; no Android-compatible artifact in this checkout | No Android score; semantic masks are not instances | No mass head | Pretraining/teacher candidate only pending dataset terms |
| MobileSAM | **Physical Android run:** ONNX encoder/decoder completed all five complex meals | Not a food identifier; prompted class-agnostic masks, so ingredient F1 is unsupported | No mass head | Use only for user-corrected masks or annotation; p95 `2,060 ms`, PSS `729,305 KB` |
| YOLO-World v2 | **Physical Android run:** exported 640 px ONNX with embedded COCO-80 vocabulary | Exact visible-component macro F1 `0.2233`; some `broccoli`/`carrot`, many false COCO food labels | No mass head | Reject for current ingredient UX; p95 `581 ms`, PSS `245,411 KB`; prompt and licence gates remain |

The report is intentionally not a model leaderboard: the models do not share a
taxonomy, an instance contract, or weighed ground truth. Only matched tasks are
scored, and a candidate without a mass head or calibrated masses cannot receive
a grams score.

The reproducible Android result and its explicit non-execution reasons are in
[`android-candidate-device-evidence-2026-08-01.json`](android-candidate-device-evidence-2026-08-01.json).
