# Comparative visible-component benchmark — 2026-08-01

## Scope and rule of interpretation

This is a research-only screen of candidates for Epic 3 v2. It does not alter
the mobile app, activate a model, or sign G-3. A candidate must identify only
visibly separable components; unknown or incompatible labels remain raw rather
than being coerced into a product ingredient. A gram metric is valid only where
the photo has calibrated, tared scale ground truth.

## Common complex-meal screen

The screen contains five public fixtures: steak with sides, fish with mixed
vegetables, chopped salad, Buddha bowl, and salmon with grains. Its conservative
annotations contain 33 visibly separable components. The images have no scale
records, so they can score component labels but cannot score grams.

| Model | Execution evidence | Ingredient result | Mass result | Runtime / license decision |
| --- | --- | --- | --- | --- |
| Food-101 Swin INT8 | Existing physical Android/iOS G-3 runs | `unsupported`; emits one dish label only | `not-evaluable` | Valid local semantic baseline, not a component model |
| FPB / YOLOv12-FoodWeight | Five-fixture CPU run, input 640, confidence 0.25, NMS IoU 0.7 | 8 detections; **0 exact component matches; macro F1 0.0** | Raw head emitted 119.57–270.35 g, but **MAE not evaluable** because no tared scale ground truth | Fails this component screen; AGPL code and CC BY-NC dataset block shipping pending legal review |
| FoodSAM | Local bootstrap assessed, not executed | No result: requires Python 3.7, PyTorch 1.8/CUDA 11.1, SAM ViT-H, FoodSeg103 semantic, and UniDet checkpoints | No mass head | Offline research/teacher only; do not treat paper metrics as this-screen performance |
| FoodSeg103 / ReLeM | Model and semantic taxonomy assessed, not executed | No result: semantic masks do not separate repeated/touching instances | No mass head | Teacher/pretraining candidate pending data terms |
| MobileSAM | Promptable model assessed, not executed | No automatic food identity; it makes class-agnostic masks | No mass head | Human correction/annotation tool, not automatic recognition |
| YOLO-World v2 | Local library bootstrap succeeded; the 24.7 MB checkpoint transfer did not complete under the available network throughput | No result: the five-fixture inference never started | No mass head | A later open-vocabulary screen could use the identical component prompts; current Ultralytics AGPL/Enterprise terms block direct shipping |

## FPB raw output

The FPB checkpoint was exercised with a process-local compatibility adapter.
The project-pinned repository cannot load the released checkpoint because it
lacks `C3k2`; current Ultralytics needs an extra-grams-channel adapter. This is
research instrumentation, never production code.

| Fixture | Raw FPB labels | Exact visible-component F1 |
| --- | --- | ---: |
| Steak with side | `sandwich`, `sandwich`, `lulya_kebab` | 0.0 |
| Fish with vegetables | `bliny`, `grilled_vegetables` | 0.0 |
| Chopped salad | `pizza` | 0.0 |
| Buddha bowl | `chelpek` | 0.0 |
| Salmon/grain plate | `sprite` | 0.0 |

The full coordinates, confidence, raw grams, checksum, runtime, and repeat-run
record are in
[`fpb-yolov12-foodweight-composite-evidence.json`](fpb-yolov12-foodweight-composite-evidence.json).
[`fpb-yolov12-foodweight-composite-predictions.json`](fpb-yolov12-foodweight-composite-predictions.json)
passes raw labels into the G-3 evaluator under an explicit unmapped namespace;
it does not hide incorrect labels through synonym mapping.

## Decision

FPB/YOLOv12-FoodWeight is eliminated as the current v2 product candidate: it
does not recognize this multi-component set, its weights cannot be validated on
these public photos, and its repository/data terms cannot be shipped directly.

The attempted YOLO-World v2 bootstrap is not a failed detector result: the
checkpoint never completed transfer, so it receives no score. It remains a
useful future zero-shot comparison because it supports custom vocabulary prompts,
but it cannot estimate grams and requires an explicit license decision before
any product use.

The next meaningful benchmark requires a commercial-use-cleared compact
detector/instance-segmenter plus a consented, tared corpus. FoodSAM/FoodSeg103
may supply offline teacher or annotation experiments after their asset/data
terms are reviewed; MobileSAM may help correct masks after an ingredient
identifier proposes them. None replaces the weighed corpus or makes a
single-RGB exact-grams claim valid.
