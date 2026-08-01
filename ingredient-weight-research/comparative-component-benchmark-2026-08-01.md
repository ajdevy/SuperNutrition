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
| FoodSAM | No supported Android artifact for its released three-model research stack | No Android score; its semantic/instance output still has no mass head | No mass head | Offline research/teacher only; do not treat paper metrics as this-screen performance |
| FoodSeg103 / ReLeM | No Android-compatible artifact in the project; PyTorch/MMsegmentation semantic benchmark | No Android score; semantic masks do not separate repeated/touching instances | No mass head | Teacher/pretraining candidate pending data terms |
| MobileSAM | **Physical Android run:** ONNX Runtime CPU, five fixtures, all encoder/decoder passes | Class-agnostic prompted masks only; ingredient F1 is unsupported | No mass head | Human correction/annotation tool, not automatic recognition; p95 `2,060 ms`, PSS `729,305 KB` |
| YOLO-World v2 | **Physical Android run:** exported 640 px ONNX with embedded COCO-80 vocabulary, five fixtures | COCO food subset exact-label macro F1 **0.2233**; it finds some `broccoli`/`carrot` but also many false labels | No mass head | Reject for current ingredient UX; p95 `581 ms`, PSS `245,411 KB`; custom ingredient prompts and licensing remain separate gates |

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

YOLO-World v2 now has a real physical Android detector run, but that is not a
product recognition pass. The exported artifact contains only the model's
embedded COCO-80 vocabulary, not the benchmark's visible-component vocabulary.
It produced exact `broccoli`/`carrot` matches on some meals, but its `0.2233`
macro F1 also contains false food labels such as `sandwich`, `pizza`, `donut`,
and `cake`. It remains a useful zero-shot deployment comparison, not a
candidate ingredient model; it cannot estimate grams and requires an explicit
license decision before any product use.

The next meaningful benchmark requires a commercial-use-cleared compact
detector/instance-segmenter plus a consented, tared corpus. FoodSAM/FoodSeg103
may supply offline teacher or annotation experiments after their asset/data
terms are reviewed; MobileSAM may help correct masks after an ingredient
identifier proposes them. None replaces the weighed corpus or makes a
single-RGB exact-grams claim valid.

The physical Android candidate evidence, including model checksums, raw COCO
food detections, resource measurements, and the explicit non-execution reasons
for FoodSAM/FoodSeg103, is in
[`android-candidate-device-evidence-2026-08-01.json`](android-candidate-device-evidence-2026-08-01.json).
