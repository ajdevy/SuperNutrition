# G3 component-model selection and conditional legal clearance — 2026-08-11

**Status:** proposed; not production-approved and not activatable.

This is the technical selection record for completing G3. It does not replace
the named Legal, corpus-governance, Engineering, or Product approvals required
by `ComponentModelManifest` and the G3 sign-off decision.

## Selection

Train and ship **`sn-yolox-nano-components-onnx-v1`**, a YOLOX-nano component
detector exported to ONNX, from scratch on the consented SuperNutrition corpus.
Do not initialize it from, bundle, or redistribute third-party pretrained
weights. Its output remains the current visible-food contract: normalized boxes
for `apple`, `banana`, `broccoli`, `carrot`, and `orange` only. A box is not an
ingredient, recipe, or mass assertion.

YOLOX source is Apache-2.0 and supports ONNX export. This selection deliberately
does not use the current YOLO-World asset (GPL-3.0) or the Food-101-derived
dish classifier: the latter's model card is Apache-2.0, but its upstream
Food-101 dataset card does not state a usable dataset licence. See the linked
primary sources in the evidence section below.

## Immutable taxonomy

| Field | Value |
|---|---|
| File | `mobile/config/supernutrition-visible-food-v1.json` |
| Version | `supernutrition-visible-food-v1` |
| SHA-256 | `e8e6ee82fa7ae0de0a3f1fe8b0b219913db81dfd6413ceacbe38c48564f99605` |
| Classes | `apple`, `banana`, `broccoli`, `carrot`, `orange` |

The digest is over the checked-in UTF-8 JSON bytes. Any class, name, ordering,
or contract change creates a new taxonomy version and digest; it does not
silently modify this candidate.

## Required production manifest values

| Contract field | Selected value | Activation evidence still required |
|---|---|---|
| `modelVersion` | `sn-yolox-nano-components-onnx-v1` | Final model file and manifest registration |
| `artifactDigest` | **pending** | SHA-256 of the final exported ONNX bytes |
| `taxonomyVersion` / `taxonomyDigest` | `supernutrition-visible-food-v1` / `e8e6ee82…f99605` | Included in the signed manifest |
| Preprocessing | `rgb-resize-416-v1` | Exact exporter/inference parity test |
| Postprocessing | `yolox-nano-decode-nms-v1` | Golden-box parity test on Android and iOS |
| `maskContract` | `normalized-box-v1` | Scope test: no mask, ingredient, or mass is inferred |
| Runtime | `onnxruntime-android-1.28.0`; `onnxruntime-objc-1.28.0` | Lockfile/dependency check on both hosts |
| Execution provider | CPU | Android and physical-iOS benchmark must record the actual CPU EP |
| Calibration | `sn-component-temperature-v1` | Held-out calibration split, method/config digest, fitted parameters, and before/after metrics |
| Rollback target | **pending: `sn-yolox-nano-components-onnx-v0`** | An independently built, registered, benchmarked and approved v0 artifact; it must exist before v1 can activate |

There is currently no honest artifact SHA-256, fitted calibration, or existing
production rollback artifact for this candidate. The present PoC assets are not
an acceptable rollback target for it: their component manifest has placeholder
references and does not establish the required source-data, calibration, or
legal provenance.

## Conditional legal clearance

**Proposed clearance scope:** use the Apache-2.0 YOLOX source code only as a
training/export implementation; distribute the resulting SuperNutrition ONNX
artifacts only when they were trained exclusively from the governed
SuperNutrition corpus. Preserve the Apache-2.0 licence and NOTICE material in
the training/release record.

**Explicit exclusions:** no YOLO-World code or weights; no Food-101 images,
labels, derived weights, or other third-party pretrained weights; no FPB,
FoodSeg, or FoodSAM assets unless a separate evidence record establishes their
rights and model-data provenance.

This clearance is **not yet effective**. A named Legal approver must confirm
the corpus contributors' capture, annotation, training, and commercial
distribution rights, plus the final third-party notices, against the actual
artifact digest. The approver's immutable reference belongs in
`legalApprovalRef`; it cannot be fabricated in source control.

## Activation checklist

1. Govern a consented, weighed corpus of at least 300 cases, with immutable
   corpus/annotation/consent digests and a named corpus approval.
2. Train and register v0; record its ONNX SHA-256, taxonomy digest, CPU runtime,
   calibration, Android/iOS component and mass benchmark results, and approvals.
3. Train v1 from the same governed process, fit calibration only on the held-out
   calibration partition, and record the final artifact SHA-256 and metrics.
4. Verify v1 and its existing v0 rollback target on physical Android and iOS;
   the iOS device evidence needs a host supported by the connected iOS version.
5. Obtain named Engineering, Legal, corpus-governance, benchmark, rollback, and
   Product sign-offs, then create a manifest with the real immutable references
   and activate it through the existing controller.

## Evidence sources

- [YOLOX repository and Apache-2.0 licence](https://github.com/Megvii-BaseDetection/YOLOX)
- [ONNX Runtime repository and MIT licence](https://github.com/microsoft/onnxruntime)
- [YOLO-World repository and GPL-3.0 licence](https://github.com/AILab-CVC/YOLO-World)
- [Food-101 ONNX model card](https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX)
- [Food-101 dataset card](https://huggingface.co/datasets/ethz/food101/blob/main/README.md)
