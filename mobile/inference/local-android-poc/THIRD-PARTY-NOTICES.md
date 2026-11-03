# Third-party notices for local food classification

The root [`LICENSE`](../../../LICENSE) covers SuperNutrition's first-party
code. It does not replace the terms for third-party runtimes, models, or data.
The local model is a dish-classification proof of concept, not an ingredient,
nutrition, or portion-size authority. Component detection is unavailable until
a separately cleared model is selected.

## ONNX Runtime Android

The app depends on `com.microsoft.onnxruntime:onnxruntime-android:1.28.0`.
Microsoft distributes ONNX Runtime under the MIT License. See the
[ONNX Runtime repository](https://github.com/microsoft/onnxruntime) and the
dependency's included notices.

## Food-101 Swin model

The app includes `swin-finetuned-food101-matmul-int8.onnx`, an ONNX-converted
Food-101 Swin classifier. Its
[model card](https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX)
identifies Apache-2.0 for the model package. The underlying Food-101 images
remain third-party content: the
[dataset card](https://huggingface.co/datasets/ethz/food101) says broader use
of the images must be arranged with the original rights holders. The app does
not include the original Food-101 image set.

## Private runtime source and artifact

The first-party Android runtime source and built AAR are maintained in the
private [food-identification Android repository](https://github.com/flexsent-labs/supernutrition-food-identification-android).
The public SuperNutrition project consumes the private Maven package and does
not include the AAR or its source. Access credentials are local build inputs
and must not be committed.
