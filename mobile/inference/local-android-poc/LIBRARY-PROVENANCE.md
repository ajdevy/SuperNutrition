# Android food-identification runtime artifact

The source for the Java ONNX runtime is maintained in the private repository
[`flexsent-labs/supernutrition-food-identification-android`](https://github.com/flexsent-labs/supernutrition-food-identification-android).
The public app repository checks in the built AAR so that building the app does
not require access to that private repository.

| Field | Value |
| --- | --- |
| Library version | `0.1.0` |
| Source commit | [`e9d65cfee31d015708c1988c82269658e7bc1534`](https://github.com/flexsent-labs/supernutrition-food-identification-android/commit/e9d65cfee31d015708c1988c82269658e7bc1534) |
| Build command | `./gradlew assembleRelease` in the private source repository |
| AAR | `libs/food-identification-runtime-0.1.0.aar` |
| AAR SHA-256 | `dbe9631a58daef59ce2b17c15cf0f1133feaef167340a94ecec96a647d7575f3` |
| AAR size | 25,167 bytes |

The AAR contains runtime code only. The host app packages ONNX Runtime Android
`1.28.0` and the following assets in `src/main/assets/`:

| Asset | SHA-256 |
| --- | --- |
| `swin-finetuned-food101-matmul-int8.onnx` | `9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df` |
| `yolov8s-worldv2-coco.onnx` | `d27ac81640ca470cc7e42edd638b6626ac6b4f4dcce74aabb6e09cd02a13c84f` |
| `food101-labels.txt` | `eb6b396455a281445a698d8af22b2142be9a6eb9401520ee702746545f1c8820` |

The Kotlin host adapter verifies these asset digests before inference. The
source-only private repository does not contain model weights.
