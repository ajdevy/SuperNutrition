# Third-party notices for the Android food-identification proof of concept

The root [`LICENSE`](../../../LICENSE) covers SuperNutrition's first-party
code. It does not replace the terms for third-party runtimes, models, or data.
This proof of concept is staged and is not a production food, ingredient,
nutrition, or portion-size authority.

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

## YOLO-World model

The app includes `yolov8s-worldv2-coco.onnx`. Ultralytics documents YOLOv8
under AGPL-3.0 or its Enterprise License. See the official
[YOLOv8 licensing documentation](https://github.com/ultralytics/ultralytics/blob/main/docs/en/models/yolov8.md).
Use and redistribution of this model must follow the applicable upstream
terms. The project owner has confirmed proceeding under those terms; any
separate approval required by the submission process remains outstanding.

## Private runtime source

The first-party Android runtime source is MIT-licensed and maintained in the
private [food-identification Android repository](https://github.com/flexsent-labs/supernutrition-food-identification-android).
The public project contains the compiled runtime AAR, not its Java source.
