# Epic 3 G-3 device benchmark harness

This is a non-production research harness for the signed G-3 decision. It
measures a candidate local runtime on explicit physical Android and iOS
devices; it does not change the production inference adapters and it does not
make an accuracy claim.

The harness uses five public composite meal photographs so that the same input
shape is exercised on both devices:

| Fixture | Source |
| --- | --- |
| steak-side-plate | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Steak_dinner_with_vegetables.jpg) |
| fish-side-plate | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Pike_and_vegetables.jpg) |
| chopped-salad | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Mixed_salad_(Kosambri_or_Kachumber).jpg) |
| buddha-bowl | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:BuddhaBowlLot.jpg) |
| salmon-grain-plate | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Rice,_beans_and_Salmon_Fish.jpg) |

The candidate model is `mobilenet_v2_food101.onnx` from the
[Apache-2.0 model repository](https://github.com/AlexKoff88/mobilenetv2_food101).
The expected model SHA-256 is
`af237923fd4636f5c6156059e372871a2b63e4a851f377f1b92ae8c71d018890`.
The Android candidate is ONNX Runtime Android `1.28.0`; the iOS candidate is
ONNX Runtime Objective-C `1.27.0` with XNNPACK. Runtime versions are pinned in
the runner build files and are not production dependencies.

## Run on physical devices

Download the model and fixtures, verifying every file before use:

```bash
tools/g3-benchmark/download-assets.sh
```

Android requires an explicit serial and a connected, unlocked device:

```bash
ANDROID_DEVICE_SERIAL=AU3N025429006787 \
  tools/g3-benchmark/run-android.sh
```

iOS requires the physical UDID and a locally configured Apple development
account/team in Xcode:

```bash
DEVELOPMENT_TEAM=REDACTED_TEAM_ID \
IOS_DEVICE_UDID=REDACTED_PHYSICAL_DEVICE_UDID \
  tools/g3-benchmark/run-ios.sh
```

The scripts print the redacted JSON result. They do not upload photos, call a
product API, or use UI automation. The iOS script uses local Xcode signing
and `devicectl` only to install, launch, and copy the app-owned result file.

## Current physical spike

The measured runs used five warmups and 50 measured inferences per device.
The model output was checked only for a non-crashing tensor/top-1 index.
Accuracy requires a separate consented, weighed evaluation corpus and is not
represented by these five public fixtures.

| Device | Runtime/model | p50 | p95 | max | Memory before -> after | Thermal |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Honor DNY-NX9, Android 16/API 36, arm64 | ORT Android 1.28.0, MobileNetV2 Food101 | 20 ms | 36 ms | 37 ms | 29,880 -> 101,589 KB PSS | 0 -> 0 |
| iPhone 14 Pro, iOS 26.5.2, arm64e | ORT Objective-C 1.27.0, XNNPACK, same model | 4 ms | 5 ms | 5 ms | 98,624 -> 98,656 KB resident | 0 -> 0 |

Android also recorded battery current `-1150 -> -1459 µA`, battery level
`96 -> 95%`, and reported temperature `34.0 -> 34.0°C` while AC/USB powered.
iOS recorded battery level `1.000 -> 1.000`, but not current/energy; a signed
artifact must add an iOS energy trace or an approved equivalent measurement.

An iOS Xcode 26 `Activity Monitor` trace was also captured for 31.0 seconds
while launching the runner on the physical phone. It includes the
`SuperNutritionG3` process and reports approximately 60.53 MiB physical
footprint, 86.53 MiB resident size, and 0.91 seconds combined CPU time. This
is supporting native resource evidence, not a battery/energy measurement.

These results are preliminary evidence only. See
`_bmad-output/implementation-artifacts/g3-benchmark-artifact.md` for the
unsigned decision record, proposed budgets, and remaining acceptance gaps.
