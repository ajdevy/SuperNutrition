# G3 component-model selection and conditional legal clearance — 2026-08-11

**Status:** selected as the current local identification baseline; legal use is
user-attested. This is not a component/mass G3 sign-off.

This is the technical selection record for completing G3. It does not replace
the named Legal, corpus-governance, Engineering, or Product approvals required
by `ComponentModelManifest` and the G3 sign-off decision.

## Selection

Select the already shipped **YOLO-World v2 COCO region detector** as the local
identification baseline:

- model version: `poc-yolo-food-components-v1`;
- asset: `yolov8s-worldv2-coco.onnx`;
- asset SHA-256:
  `d27ac81640ca470cc7e42edd638b6626ac6b4f4dcce74aabb6e09cd02a13c84f`.

It won the current physical Android identification screen over YOLOX-Nano:
limited COCO-food macro F1 `0.2164` versus `0.1500`. It remains limited to
visibly supported COCO labels. It does not infer recipe ingredients or mass.
The selection does not swap an app asset: this is the model already used by the
current local PoC runtime.

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

## Local visible-food manifest

| Contract field | Selected value | Current status |
|---|---|---|
| `modelVersion` | `poc-yolo-food-components-v1` | Current local runtime baseline |
| `artifactDigest` | `d27ac81640ca470cc7e42edd638b6626ac6b4f4dcce74aabb6e09cd02a13c84f` | Verified by the app before use |
| `taxonomyVersion` / `taxonomyDigest` | `supernutrition-visible-food-v1` / `e8e6ee82…f99605` | Checked into the app manifest |
| Preprocessing | `rgb-resize-640-v1` | One shared Android app/benchmark contract |
| Postprocessing | `yolo-coco-box-nms-v1` | One shared Android app/benchmark contract |
| `maskContract` | `normalized-box-v1` | No mask, ingredient, or mass is inferred |
| Runtime | `onnxruntime-android-1.28.0`; `onnxruntime-objc-1.28.0` | Pinned on both hosts |
| Execution provider | CPU | Recorded by benchmark execution evidence |
| Calibration | `uncalibrated-proxy-v1` | Threshold `0.10` and NMS IoU `0.70`; never a mass calibration |
| Activation | `STAGED` | The app may identify visible foods locally; it may not claim component/mass production approval |
| Rollback baseline | this exact `poc-yolo-food-components-v1` asset | Future replacement must prove rollback to it |

The Android `ComponentModelManifest` deliberately contains no legal, corpus,
benchmark, Product, Engineering, or rollback-evidence reference. Those records
belong to a future component/mass model, not to this five-label local
identifier. A future replacement must register this exact selected baseline as
its rollback target instead of pointing to the non-existent
`component-model-v0` placeholder.

`PocYoloWorldContract` is the single source of preprocessing, tensor-layout
parsing, label filtering, normalisation, and NMS for both the Android runtime
and the G3 Android benchmark runner. The benchmark therefore measures the same
detector contract that the app ships.

## Legal use record

The project owner stated on 2026-08-11: **“юрист подтверждает”** for this
model. That attestation, its exact asset scope, and its limitation are recorded
in [`g3-legal-attestation-2026-08-11.md`](g3-legal-attestation-2026-08-11.md).
No lawyer name, external case number, or signed opinion was supplied, so none
is invented here. The attestation authorizes this selection as a project
decision; it is not legal advice from the repository or this agent.

## Activation checklist

1. Use the selected asset only for its five-label visible-food scope; do not
   present it as ingredient inventory or mass estimation.
2. Before replacing it, stage the replacement against this exact baseline and
   prove rollback to `poc-yolo-food-components-v1` with asset
   `d27ac816…c84f`.
3. Component/mass G3 still needs its own calibrated weighed-corpus evidence and
   Product/Engineering sign-off. Those requirements are not a reason to change
   the selected current identification baseline.

## Evidence sources

- [YOLO-World repository and GPL-3.0 licence](https://github.com/AILab-CVC/YOLO-World)
- [Physical YOLOX vs current YOLO-World comparison](../../ingredient-weight-research/yolox-nano-comparison-2026-08-11.md)
