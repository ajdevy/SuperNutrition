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
| [NutritionVerse-Real](https://arxiv.org/abs/2401.08598) | 889 iPhone 13 Pro Max images across 251 dishes and 45 food types; ingredient weights measured with a food scale; human masks on four of ten views per scene | Kaggle metadata reports `CC BY-NC-SA 4.0`; only 251 distinct dishes, no verified product-use grant, and the public record does not expose the project's consent/scale-QC manifest | Research benchmark only; do not use as product corpus or production model source |
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

The machine-readable corpus boundary is frozen in
[`tools/g3-benchmark/consented-weighed-corpus.schema.json`](../tools/g3-benchmark/consented-weighed-corpus.schema.json).
The capture and quality-review handoff is in
[`tools/g3-benchmark/corpus-collection-runbook.md`](../tools/g3-benchmark/corpus-collection-runbook.md).
Validate a locally held manifest with the dependency-free checker:

```bash
python3 tools/g3-benchmark/validate_weighed_corpus.py /path/to/corpus.json
```

For a G-3 acceptance check, add `--require-g3`; this requires at least 300
consented cases and the `train`, `calibration`, and `held-out` partitions:

```bash
python3 tools/g3-benchmark/validate_weighed_corpus.py --require-g3 /path/to/corpus.json
```

The checker accepts only opaque references and SHA-256 digests; it does not
open or copy photos, masks, scale exports, or external datasets. It rejects
withdrawn consent, failed scale calibration, missing tared component readings,
visible components without masks, non-evaluable components with invented mass,
and participant/meal/recipe/device/venue/session leakage across partitions.
No real corpus is checked into this repository, so the strict G-3 mode has no
passing input until the consented pilot manifest is supplied.

### Corpus mass-quality evaluator

The real-corpus score is intentionally separate from the five public-photo
screen. Once a redacted corpus and a model prediction handoff exist, run:

```bash
python3 tools/g3-benchmark/evaluate_consented_corpus.py \
  --require-g3 \
  --manifest /path/to/redacted-corpus.json \
  --predictions /path/to/redacted-predictions.json \
  --output /path/to/mass-quality-evidence.json
```

The evaluator validates `supernutrition.g3-corpus-predictions.v1`, requires
model and calibration provenance, matches visible components one-to-one by
canonical label and normalized-box IoU, and records missing components,
unexpected components, mass abstentions, component/total mass errors, and
coverage. It also emits slices by partition, device group, lighting, and
camera angle. It never opens raw photos or masks and rejects raw paths/PII.
The command exits non-zero for `fail` and `not-evaluable`; a `pass` is only
the quantitative evidence layer and does not replace Product/Engineering,
Legal, model, or rollback approval.

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
   run only adapters whose code and data terms are cleared for the intended
   use, without importing research datasets into the product binary.
3. Train or fine-tune a small commercial-cleared detector/segmenter on the
   consented corpus; use the public sets only where license permits.
4. Add a separate mass head and calibration/reference experiment; keep grams
   absent or ranged when calibration is missing.
5. Version a new inference contract for multi-component results and progress
   events; retain the current dish-only contract as a fallback.
6. Export the smallest viable model to ONNX/Core ML/LiteRT or ExecuTorch and
   rerun Android/iOS device evidence under the relaxed exploratory budgets.

## Fresh public candidate screen — 2026-08-01

The latest public search produced two relevant references, but neither closes
the production gate:

| Candidate | Positive evidence | Blocking gap | Decision |
| --- | --- | --- | --- |
| [ConvNeXt-Food-CLF-75](https://huggingface.co/Alas-V/ConvNeXt-Food-CLF-75) | Apache-2.0 model; 75-class multi-label ingredient probabilities | Classification only: no instance masks, geometry, mass head, mobile export, or independently verified commercial rights for the training data | Reject as a v2 runtime; retain as a possible semantic pretraining input |
| [Weighed-Meal Reference Set v1.0 mini-180](https://dietaryassessmentinitiative.org/datasets/weighed-meal-reference-set-180/) | CC BY 4.0 tabular layer with per-ingredient weighed grams | Only 180 meals; the photographic layer is restricted researcher-use and supplies no repository-ready photo/mask manifest | Reject as the G-3 corpus; use only as a lead for a separately approved access request |

The first candidate cannot emit the typed component evidence required by E3.7
and E3.8. The second cannot satisfy the 300-case minimum or the repository's
photo/mask/consent boundary. Neither is added to the product or treated as
model/corpus approval.

No current G-3 status or owner signature is changed by this research note.

## Excluded research integrations

The FPB/YOLOv12-FoodWeight runner, tests, and generated predictions were
removed from this repository under
[the release cleanup decision](../.evidence/OWNER-DECISIONS.md). They are not
part of the app or its distributable runtime. Historical license and candidate
notes elsewhere in the research archive are informational only.

## Comparative candidate status — 2026-08-01

| Candidate | What was actually exercised | Visible ingredients on five complex meals | Grams | Current decision |
| --- | --- | --- | --- | --- |
| Food-101 Swin INT8 | Physical Android and iOS G-3 harness | `unsupported`: one dish label, no components | `not-evaluable` | Retain only as the dish-classification baseline |
| FoodSAM | Official research stack audited; no supported Android artifact for its SAM + semantic + detector composition | No Android score | No mass head | Offline teacher/annotation candidate, not an immediate mobile runtime |
| FoodSeg103 / ReLeM | Official semantic benchmark and model zoo audited; no Android-compatible artifact in this checkout | No Android score; semantic masks are not instances | No mass head | Pretraining/teacher candidate only pending dataset terms |
| MobileSAM | **Physical Android run:** ONNX encoder/decoder completed all five complex meals | Not a food identifier; prompted class-agnostic masks, so ingredient F1 is unsupported | No mass head | Use only for user-corrected masks or annotation; p95 `2,060 ms`, PSS `729,305 KB` |

The report is intentionally not a model leaderboard: the models do not share a
taxonomy, an instance contract, or weighed ground truth. Only matched tasks are
scored, and a candidate without a mass head or calibrated masses cannot receive
a grams score.

The reproducible Android result and its explicit non-execution reasons are in
[`android-candidate-device-evidence-2026-08-01.json`](android-candidate-device-evidence-2026-08-01.json).

## Fresh physical v2 candidate screen — 2026-08-02

The Android candidate activities were rerun on the connected physical Honor
DNY-NX9 (`PHYSICAL_DEVICE_ENDPOINT`, Android 16/API 36) with ONNX Runtime Android
`1.28.0` CPU. The latest complete result, including artifact digests and
fixture outcomes, is in
[`android-component-mass-benchmark-2026-08-02.json`](android-component-mass-benchmark-2026-08-02.json).

| Candidate | Fresh result | v2 quality decision |
| --- | --- | --- |
| MobileSAM | 5/5 mask executions; p95 `2,003 ms`; PSS `733,237 KB` | `unsupported` for ingredient identity; `not-evaluable` for grams because masks are class-agnostic and prompted |

This fresh run improves the deployment evidence only. It does not select a
production v2 model, establish calibrated mass quality, or close G-3.

## Public research sample acquisition — 2026-08-02

The pre-release research cache and its license/domain caveats are recorded in
[`public-research-acquisition-2026-08-02.md`](public-research-acquisition-2026-08-02.md).
It contains a 300-pair FoodSeg103 segmentation sample and a 300-plate
Nutrition5k RGB-D/mass sample outside Git. These public samples are research
baselines only; the future user-consented corpus remains required for G-3.
The former detector-based ONNX benchmark and its model outputs were removed
under release cleanup decision D-13; the cached public datasets are not
included in the app or distributed from this repository.

## Public model screen — 2026-08-02 web refresh

Two additional public candidates were checked against the actual E3.7/E3.8
boundary. Neither is promoted to a runtime:

| Candidate | Public evidence | Blocking gap | Decision |
| --- | --- | --- | --- |
| [FoodSAM](https://github.com/jamesjg/FoodSAM) | Apache-2.0 repository; combines SAM, a semantic segmenter, and an object detector for semantic/instance/panoptic food segmentation | No mass head, no calibrated grams, and a heavy research-time PyTorch composition rather than an Android/iOS artifact | Keep as an offline teacher/annotation candidate only |
| [Boba Food VLM 0.8B](https://huggingface.co/Doses-AI/boba-0.8b-food-GGUF) | Apache-2.0 model card; reports structured per-ingredient grams/nutrition and a Nutrition5k test result | A 505 MB Q4 model plus 196 MB vision projector, VLM-generated grams without the project's masks/abstention/calibration contract, and training/data provenance still requires legal review | Keep as a research comparison only; do not ship or bind as G-3 |

The source claims are useful for prioritizing experiments, not acceptance: the
FoodSAM repository itself describes three combined models and reports benchmark
segmentation numbers, while the Boba card reports its own Nutrition5k-derived
calorie benchmark. Neither source provides the consented phone-photo corpus,
component tared readings, rollback manifest, or independent product evidence
required here.

The follow-up screen also checked [ConvNeXt-Food-CLF-75](https://huggingface.co/Alas-V/ConvNeXt-Food-CLF-75)
and the [FoodSeg103 Food Density & Nutrition dataset](https://huggingface.co/datasets/arunapb/food-density-nutrition).
The former is a 75-class multi-label classifier with no regions or mass head;
the latter supplies class-level density/nutrition lookup values, not a trained
vision model or measured per-photo mass. They are useful pretraining/heuristic
inputs only and do not remove the weighed-corpus requirement.
