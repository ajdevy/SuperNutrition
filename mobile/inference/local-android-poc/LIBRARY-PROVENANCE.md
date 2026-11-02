# Android food-identification runtime artifact

The Java ONNX runtime source and its compiled AAR are maintained in the
private repository
[`flexsent-labs/supernutrition-food-identification-android`](https://github.com/flexsent-labs/supernutrition-food-identification-android).
SuperNutrition consumes the private Maven package; it does not check in the
library source or AAR. Android builds need read access to that repository's
GitHub Packages artifact.

| Field | Value |
| --- | --- |
| Library version | `0.1.1` |
| Source commit | [`9b7df3d91ac6d9a86957688c97a69ba739635d9c`](https://github.com/flexsent-labs/supernutrition-food-identification-android/commit/9b7df3d91ac6d9a86957688c97a69ba739635d9c) |
| Build command | `./gradlew assembleRelease` in the private source repository |
| Maven coordinate | `com.supernutrition.inference:food-identification-runtime:0.1.1` |
| Android namespace | `com.supernutrition.foodidentification.runtime` |
| AAR SHA-256 | `36cec9481a84ff939ea05c17c4b1b246f6146f8b4889ac14d7e26842b488abf0` |
| AAR size | 25,083 bytes |

The runtime AAR contains no model weights and no standalone license file. Its
Maven metadata omits license fields. The app includes ONNX Runtime Android
`1.28.0` and these classifier assets under `src/main/assets/`:

| Asset | SHA-256 |
| --- | --- |
| `swin-finetuned-food101-matmul-int8.onnx` | `9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df` |
| `food101-labels.txt` | `eb6b396455a281445a698d8af22b2142be9a6eb9401520ee702746545f1c8820` |

The Kotlin host adapter verifies both asset digests. The model-set digest is
`390a541d90a124fc6ca6d601b400ed72708452675497d4ca760750685e4dd63e`.
Component detection is disabled until a separately cleared model is selected.
