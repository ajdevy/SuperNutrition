# YOLOX-Nano comparative screen — 2026-08-11

## Result

YOLOX-Nano is **not selected for production**. On the physical Honor DNY-NX9,
it used materially less memory and had a lower five-fixture p95 than the
current YOLO-World artifact, but its restricted COCO-food macro F1 was lower:
`0.1500` vs `0.2164`. Neither model identifies the product's full visible
component vocabulary or estimates mass.

The fresh raw evidence is
[`yolox-nano-android-evidence-2026-08-11.json`](yolox-nano-android-evidence-2026-08-11.json).
Both current rows use the same physical device, ONNX Runtime Android 1.28.0
CPU, and five public composite fixtures. Each candidate retains its upstream
preprocessing/postprocessing; a forced common pipeline would make one of the
models invalid. Five timings are a deployment screen only: p95 equals the
maximum and is not a latency acceptance result.

| Candidate | Where actually run | Component result | Device profile | Verdict |
| --- | --- | --- | --- | --- |
| **YOLOX-Nano COCO** | Fresh physical Android, five fixtures | COCO-food macro F1 **0.1500** | init 265 ms; p50/p95 174/468 ms; PSS 133 MB | Fastest detector checked, but lower recognition; reject for ingredient/grams runtime |
| **YOLO-World v2**, current PoC artifact | Fresh physical Android, same screen | COCO-food macro F1 **0.2164** | init 248 ms; p50/p95 652/1,117 ms; PSS 244 MB | Better screen F1, substantially heavier; still COCO-only and no mass |
| Food-101 Swin INT8 | Historical physical Android/iOS semantic G-3 runs | Unsupported: one dish label, not components | Android semantic screen recorded separately | Keep only as a dish-semantic baseline |
| MobileSAM | Historical physical Android, five fixtures | Unsupported: class-agnostic prompted masks | Android p95 1,891 ms; PSS 735 MB | Annotation/correction aid only |
| FPB / YOLOv12-FoodWeight | Host CPU, five fixtures | Macro F1 **0.0** | No valid phone run | Reject; raw gram output cannot be scored without tared ground truth |
| FoodSAM | Not run on Android | No device score | No complete Android artifact | Offline research only |
| FoodSeg103 / ReLeM | Not run on Android | No device score | No Android-compatible artifact | Offline teacher/pretraining research only |

## Provenance and legal boundary

The tested YOLOX ONNX file is the official `0.1.1rc0` release artifact,
SHA-256 `c789161ed43c8269fcd4e67c67eeeb4e80c622da2eb296a20bc6007bd18a0b7d`.
YOLOX source is Apache-2.0 and supports ONNX deployment, but this does **not**
clear the separately released COCO-pretrained checkpoint or its training data
for product use. A lawyer must approve that exact artifact and provenance.

YOLO-World remains a separate legal decision; its repository is GPL-3.0. No
result in this comparison activates, clears, or replaces the existing product
runtime.

Sources: [YOLOX repository and release](https://github.com/Megvii-BaseDetection/YOLOX),
[YOLO-World repository](https://github.com/AILab-CVC/YOLO-World), and the
[previous Android candidate record](android-candidate-device-evidence-2026-08-02.json).
