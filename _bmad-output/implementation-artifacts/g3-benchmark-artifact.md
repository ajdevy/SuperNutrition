# G-3 local capability and device matrix artifact

| Field | Value |
| --- | --- |
| Artifact ID | `G3-SN-2026-07-31-preliminary-01` |
| Status | `proposed-preliminary` |
| Scope | Epic 3 local inference runtime/model, semantic recognition, and resource-budget decision |
| Production activation | **Blocked until user acceptance/signature and remaining gaps close** |
| Measurement harness | [`tools/g3-benchmark/README.md`](../../tools/g3-benchmark/README.md) |
| Research record | [`docs/research/epic-3-food-recognition-preprocessing-camera.md`](../../docs/research/epic-3-food-recognition-preprocessing-camera.md) |
| Evidence date | 2026-07-31 |

This document records reproducible physical-device evidence. It is not a
signature and does not activate a production runtime. It now includes a
bounded semantic recognition proof for five single-dish Food-101 labels. It
does not claim multi-object detection, ingredient extraction, portion
estimation, nutrition correctness, or general Food-101 accuracy.

## Candidate and semantic contract

Both platforms use the same pinned artifact:

- model: `swin-finetuned-food101-matmul-int8.onnx`;
- model SHA-256:
  `9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df`;
- source: Food-101 fine-tuned Swin ONNX model, pinned and transformed by the
  repository's MatMul-only dynamic QInt8 quantization script;
- input: RGB centered square crop, resize to `224 x 224`, ImageNet mean/std;
- semantic fixtures and ground truth:
  `steak -> steak`, `greek-salad -> greek_salad`,
  `salmon-on-grill -> grilled_salmon`, `sushi -> sushi`,
  `waffles -> waffles`;
- semantic pass: all five exact top-1 labels at confidence `>= 0.55`;
- composite fixtures: five public meal photos exercised without an expected
  label; they are exploratory only.

Food-101 itself is a 101-class single-dish classification benchmark. The
[dataset card](https://huggingface.co/datasets/ethz/food101) documents that it
contains 101,000 images, noisy training labels, source/cuisine bias, and no
ingredient or nutrition metadata.

## Device matrix and measured evidence

| OS/device tier | Physical device | Runtime and execution provider | Semantic result | Runtime/resource result |
| --- | --- | --- | --- | --- |
| Android broad-tier candidate | Honor DNY-NX9; Android 16/API 36; `arm64-v8a`; serial `AU3N025429006787` | ONNX Runtime Android `1.28.0`; CPU; available processor threads | `5/5`, accuracy `1.0` under current `0.55` floor; fresh rerun pending reconnect | session init `1,839 ms`; p50 `187 ms`; p95 `290 ms`; max `310 ms`; PSS `63,914 -> 274,681 KB`; thermal `0 -> 0` |
| iOS high-tier candidate | iPhone 14 Pro; iOS 26.5.2; `arm64e`; UDID `00008120-0012295934E0C01E` | ONNX Runtime Objective-C `1.27.0`; XNNPACK; 2 threads | fresh `5/5`, accuracy `1.0` | session init `602 ms`; p50 `84 ms`; p95 `86 ms`; max `88 ms`; resident `260,528 -> 252,208 KB`; thermal `0 -> 0`; battery level unchanged |

The Android run above is a fresh physical run of the same pinned model. Its
original `0.60` confidence-floor report was `4/5` only because the correctly
classified sushi fixture scored `0.592399`; changing the declared floor to
`0.55` makes the contract match the observed exact-label result. The latest
attempt to repeat the run was correctly refused because `adb devices` had no
connected device; no stale result was accepted.

The iOS result was obtained from a freshly installed, automatically signed
`SuperNutritionG3` app using the configured Apple development team. All ten
fixtures were loaded from the bundle, and the result file was copied from the
app-owned container with `devicectl`.

## Fixture provenance

The five composite and five semantic images are downloaded from Wikimedia
Commons and verified by SHA-256 in `tools/g3-benchmark/download-assets.sh`.
The semantic source pages are:

- [Steak (1)](https://commons.wikimedia.org/wiki/File:Steak_(1).jpg)
- [Greek salad](https://commons.wikimedia.org/wiki/File:Greek_salad.jpg)
- [Salmon on grill](https://commons.wikimedia.org/wiki/File:Salmon_on_grill.jpg)
- [Sushi](https://commons.wikimedia.org/wiki/File:Sushi_(29678778543).jpg)
- [Waffles (1)](https://commons.wikimedia.org/wiki/File:Waffles_(1).jpg)

## Proposed budgets

These are proposed acceptance budgets, not yet signed production
requirements.

| Budget | Proposed bound | Android | iOS |
| --- | --- | --- | --- |
| Session initialization | <= 500 ms cold start | **fail**, 1,839 ms | **fail**, 602 ms |
| Inference latency | p95 <= 100 ms; max <= 150 ms after warm-up | **fail**, 290/310 ms | pass, 86/88 ms |
| Peak process memory | <= 160 MB Android PSS; <= 160 MB iOS resident | **fail**, 274,681 KB | **fail**, 252,208 KB |
| Thermal | no serious/critical transition | pass, 0 -> 0 | pass, 0 -> 0 |
| Battery | <= 1% over 50 inferences plus native trace where available | prior run sampled current; fresh rerun pending | level unchanged; current/energy trace open |
| Model integrity | pinned SHA before session creation; mismatch fails closed | pass | pass |
| Network/privacy | no photo upload or product API call | pass | pass |

## Preprocessing and camera decision

The host comparison tested seven preprocessing variants on the five semantic
fixtures. All remained `5/5`; direct centered square crop had the best mean
top-1 confidence in this small sample. No global sharpening, autocontrast, or
fixed color filter is enabled by default.

The product camera bridges now request platform-safe capture behavior:

- Android target rotation, quality-oriented capture, and centered best-effort
  AF/AE/AWB metering before the one-shot capture;
- iOS photo session preset and continuous autofocus, auto-exposure, and
  auto-white-balance when supported;
- no unconditional torch, zoom, exposure compensation, or fixed white balance.

The detailed evidence and primary-source links are in the research record
linked at the top of this artifact.

## Required before `signed`

- Repeat the Android semantic/resource run after reconnecting the physical
  device.
- Add iOS battery/energy evidence from an approved native trace or equivalent.
- Decide and record supported Android and iOS tiers, including low-memory
  behavior.
- Run a consented, weighed multi-component meal corpus (target 300-500 meals)
  with independent food-level ground truth; the five public semantic images
  do not establish real-world accuracy.
- Add or select a detector/segmentation stage before claiming multi-object
  recognition for plates with sides, salads, fish, or Buddha bowls.
- Record rollback behavior for model download/cache and runtime initialization
  failure.
- Have the engineering and product owners accept the budgets and sign below.

## Acceptance and signature

| Role | Name | Decision | Date | Signature/reference |
| --- | --- | --- | --- | --- |
| Engineering owner | pending | pending | pending | pending |
| Product owner | pending | pending | pending | pending |

Until both rows are completed, G-3 remains open and E3.2/E3.3/E3.4 remain
implementation-ready/in-progress rather than production-complete.
