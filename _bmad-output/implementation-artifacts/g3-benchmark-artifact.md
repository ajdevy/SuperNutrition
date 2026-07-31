# G-3 local capability and device matrix artifact

| Field | Value |
| --- | --- |
| Artifact ID | `G3-SN-2026-07-31-preliminary-01` |
| Status | `proposed-preliminary` |
| Scope | Epic 3 local inference runtime/model and resource-budget decision |
| Production activation | **Blocked until user acceptance/signature and remaining gaps close** |
| Measurement harness | [`tools/g3-benchmark/README.md`](../../tools/g3-benchmark/README.md) |
| Evidence date | 2026-07-31 |

This document records a reproducible physical-device spike. It is not a
signature, does not activate a production runtime, and does not claim Food-101
accuracy. The architecture requires a signed real-device benchmark before the
Android/iOS local-engine stories can select a production runtime and model.

## Candidate matrix and measured evidence

All runs used the same Apache-2.0 `mobilenet_v2_food101.onnx` model,
SHA-256
`af237923fd4636f5c6156059e372871a2b63e4a851f377f1b92ae8c71d018890`, five
public composite meal fixtures, five warmups, and 50 measured inferences.

Fixture inputs were downloaded from the linked Wikimedia Commons pages and
verified before both runs:

| Fixture | SHA-256 |
| --- | --- |
| `steak-side-plate.jpg` | `619a90aed1f8307ad39ecadefa318a663d27885403bb28d3fbe2c0a1828154b5` |
| `fish-side-plate.jpg` | `8e7e6dbf90e33c6116346600ef48f1a02de547f455613184dc829bb81e971f1d` |
| `chopped-salad.jpg` | `f0c02d4deae3f4b950b4ef88b2467e23d650d6f5ea195bedef178bc28375101c` |
| `buddha-bowl.jpg` | `503be94cd0e3345178b838c1d50e79d3f28b3cf70449e56708d84178e0fa4c67` |
| `salmon-grain-plate.jpg` | `a6c0467ff4c49694f28a308a57a09029cc83be729fa76aea4300c7d8fd54c20a` |

An EfficientNetV2B0 Food-101 ONNX candidate from the STM32 model zoo was
rejected: its SLA0044 model license restricts execution to or with
STMicroelectronics hardware, which is incompatible with this Android/iOS
selection.

| OS/device tier | Physical device | Runtime and execution provider | Result |
| --- | --- | --- | --- |
| Android broad-tier candidate | Honor DNY-NX9; Android 16/API 36; `arm64-v8a`; serial `AU3N025429006787` | ONNX Runtime Android `1.28.0`; CPU; intra-op threads = available processors | session init 286 ms; p50 20 ms; p95 36 ms; max 37 ms; PSS 29,880 -> 101,589 KB; battery current -1,150 -> -1,459 µA; battery level 96 -> 95%; temperature 34.0 -> 34.0°C; thermal 0 -> 0 |
| iOS high-tier candidate | iPhone 14 Pro; iOS 26.5.2; `arm64e`; UDID `00008120-0012295934E0C01E` | ONNX Runtime Objective-C `1.27.0`; XNNPACK, intra-op threads = 2 | session init 48 ms; p50 4 ms; p95 5 ms; max 5 ms; resident memory 98,624 -> 98,656 KB; battery level 1.000 -> 1.000; thermal 0 -> 0 |

The iOS result was obtained from the freshly installed, automatically signed
`SuperNutritionG3` app using the local Apple development account. The iOS
short-run recorded battery level but not battery current/energy; this is an
explicit open item.

Supporting native resource evidence is available from an Xcode 26
`Activity Monitor` trace on the same physical phone: 31.006 seconds, with the
`SuperNutritionG3` process present, approximately 60.53 MiB physical
footprint, 86.53 MiB resident size, and 0.91 seconds combined CPU time. The
trace is a local run artifact under `/tmp/supernutrition-g3-ios-energy.OxfLKs/`
and is intentionally not committed as a generated binary. It does not expose
an Energy Log instrument on this host and therefore does not close the iOS
battery/energy item.

## Proposed selection and budgets

These are proposed acceptance budgets, not yet signed production requirements.
They are intentionally bounded by the measured runs and leave room for image
decode, concurrent app memory, and device variance.

| Budget | Proposed bound | Preliminary evidence |
| --- | --- | --- |
| Session initialization | <= 500 ms cold start | Android 286 ms; iOS 48 ms |
| Inference latency | p95 <= 100 ms; max <= 150 ms after warm-up | Android 36/37 ms; iOS 5/5 ms |
| Peak process memory | <= 160 MB Android PSS; <= 160 MB iOS resident memory | Android after-run 101,589 KB; iOS after-run 98,656 KB |
| Thermal | no transition to serious/critical during the acceptance run | Android 0 -> 0; iOS 0 -> 0 |
| Battery | <= 1% battery level loss over a 50-inference acceptance run, with an OS-native energy/current trace where available | Android 1% loss while AC/USB powered/current sampled; iOS level unchanged, current/energy not measured |
| Model integrity | downloaded model must match the pinned SHA-256 before session creation; mismatch fails closed | verified by harness on both runner inputs |
| Network/privacy | no photo upload or product API call in the benchmark or local runtime lane | harness is device-local; production adapter remains unchanged |

### Proposed runtime/model policy

1. Keep runtime/model selection behind the existing local adapter seam until
   this artifact is accepted.
2. For the benchmark candidate, use ONNX Runtime with CPU on Android and
   XNNPACK on iOS, with the pinned model and SHA-256 above.
3. Download/cache is allowed only for the versioned model package after
   integrity verification. A missing, stale, or mismatched model fails closed;
   it must not silently upload the photo or switch to a remote provider.
4. Do not bundle this research model or add either runner runtime to the
   production app solely because this preliminary spike is fast.

## Tier coverage and remaining gaps

The two devices establish one Android and one iOS candidate tier. They do not
prove the complete product matrix. Before signature, the owner must either
explicitly narrow the supported tiers to these representatives or add at least
one lower-memory Android and one older/smaller iOS representative, then repeat
the run with the same pinned inputs and budgets.

Required before changing `Status` to `signed`:

- Add iOS battery/energy evidence from an approved native trace or equivalent.
- Decide and record the supported Android and iOS device/OS tiers, including
  minimum OS and low-memory behavior.
- Run the approved accuracy protocol on a consented, weighed meal corpus
  (target 300-500 meals with multi-component dishes); the five public images in
  this spike are latency/resource fixtures only.
- Record rollback behavior for model download/cache failure and runtime
  initialization failure.
- Have the product owner accept the budgets and sign the artifact below.

## Acceptance and signature

| Role | Name | Decision | Date | Signature/reference |
| --- | --- | --- | --- | --- |
| Engineering owner | pending | pending | pending | pending |
| Product owner | pending | pending | pending | pending |

Until both rows are completed, G-3 remains open and E3.2/E3.3/E3.4 remain
implementation-ready/in-progress rather than production-complete.
