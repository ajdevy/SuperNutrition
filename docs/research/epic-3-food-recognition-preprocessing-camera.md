# Epic 3 food recognition: preprocessing and camera research

Date: 2026-07-31  
Scope: on-device Food-101 classification, Android CameraX, iOS AVFoundation, and the Epic 3 G-3 device benchmark.

## Decision summary

The current model input path should remain conservative:

- decode to RGB;
- center-crop the shortest image side to a square;
- resize to `224 x 224` with bilinear interpolation;
- normalize with ImageNet mean/std (`[0.485, 0.456, 0.406]` and `[0.229, 0.224, 0.225]`);
- do not apply global sharpening, contrast stretching, or color filters by default.

This is an evidence-based hold, not a claim that preprocessing is solved. On the five semantic fixtures used by G-3, all seven tested variants achieved `5/5` top-1 matches. The existing direct square crop had the highest mean top-1 confidence (`0.952`) among the tested variants. Because this is only five curated images, it is insufficient to claim general accuracy improvement.

The camera path should explicitly request platform 3A behavior rather than hard-coding scene-dependent values:

- Android: keep `CAPTURE_MODE_MAXIMIZE_QUALITY`, set the target rotation, and run a centered `AF/AE/AWB` `FocusMeteringAction` before the one-shot capture.
- iOS: use the photo session preset and request continuous autofocus, auto-exposure, and auto-white-balance when the device supports those modes.
- Do not force torch, exposure compensation, zoom, or a fixed white-balance temperature for every scene. Those controls should be conditional and measured on the target device.

The next meaningful improvement for plates containing several foods is a region-of-interest step or a multi-food detector. A classifier-only center crop cannot prove that a steak, side dish, fish, salad, or Buddha bowl was independently identified. Composite fixtures therefore remain exploratory and must not be counted as multi-object recognition.

## Research evidence

### Dataset and model boundary

Food-101 is a 101-class dish image-classification benchmark with 101,000 images. Its task is classifying one dish image into one of 101 classes; it does not provide ingredient lists, nutrition metadata, or a multi-object annotation. The dataset card also documents source-style and cuisine bias, noisy training labels, and manually reviewed test images. These limitations are why the G-3 semantic proof uses explicit single-dish ground truth and does not turn a top-1 dish label into an ingredient or nutrition claim.

The pinned G-3 candidate is the Food-101 fine-tuned Swin ONNX model, with a reproducible MatMul-only dynamic QInt8 derivative for device execution. The derivative is useful for the benchmark, but its current Android resource measurements still exceed the proposed memory and p95 latency budgets. Recognition proof and resource-budget proof remain separate gates.

### Preprocessing experiment

Host experiment: ONNX Runtime 1.23.2, derived QInt8 model, the five downloaded semantic fixtures, same RGB/ImageNet normalization, and no test-time label filtering.

| Variant | Top-1 matches | Mean top-1 confidence | Interpretation |
| --- | ---: | ---: | --- |
| Direct centered square crop, resize 224 | 5/5 | 0.952 | Current baseline; retained |
| Resize shortest side to 256, center crop 224 | 5/5 | 0.901 | No improvement in this sample |
| Resize shortest side to 224, center crop 224 | 5/5 | 0.950 | Equivalent, not better |
| Aspect-preserving letterbox | 5/5 | 0.875 | Lower confidence; reject as default |
| Center crop + autocontrast | 5/5 | 0.938 | No demonstrated gain |
| Center crop + contrast `1.15x` | 5/5 | 0.936 | No demonstrated gain |
| Center crop + unsharp mask | 5/5 | 0.920 | Lower confidence; reject as default |

The correct follow-up is not to pick the highest confidence transformation from five images. It is to evaluate the same transformation matrix on a stratified set of real phone captures across lighting, distance, angle, cuisine, and composition, with an abstention rule for poor-quality images.

### Camera and capture findings

Android's CameraX documentation states that high-quality `ImageCapture` supports the 3A controls (auto-white-balance, auto-exposure, and auto-focus), a quality-oriented capture mode, explicit target rotation, crop rects, and center-region focus/metering. It also notes that AF/AE/AWB region support is device-dependent and therefore best-effort.

iOS AVFoundation exposes the corresponding focus, exposure, and white-balance modes, and requires configuration locking before device properties are changed. The implementation therefore requests continuous automatic modes only when supported and releases the configuration lock immediately.

For both platforms, the safe product behavior is:

1. show a centered plate/food guide in the preview;
2. let the user keep the food in the guide and avoid glare/occlusion;
3. settle 3A before capture;
4. preserve orientation and RGB conversion;
5. run a blur/brightness quality gate before recognition and ask for another photo when the input is not usable;
6. use a detector/segmentation stage for multiple visible foods instead of pretending a single classifier is multi-label.

## Sources and the “10,000 sources” requirement

I will not fabricate a 10,000-paper bibliography. The primary-source review for this change is intentionally small and auditable. There is, however, a suitable data-scale path: Food-101 contains 101,000 image records, so a future robustness run can sample at least 10,000 labeled image records and report per-class, lighting, crop, and device strata. Those image records are evaluation examples, not 10,000 independent technical sources.

Primary sources:

- [Food-101 dataset card, ETH Zurich](https://huggingface.co/datasets/ethz/food101)
- [Food-101 original dataset homepage](https://data.vision.ee.ethz.ch/cvl/datasets_extra/food-101/)
- [Pinned Food-101 Swin ONNX model card](https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX)
- [Android CameraX: capture an image](https://developer.android.com/media/camera/camerax/take-photo)
- [Android CameraX: configuration options](https://developer.android.com/media/camera/camerax/configuration)
- [Android CameraX: FocusMeteringAction](https://developer.android.com/reference/androidx/camera/core/FocusMeteringAction)
- [Apple AVFoundation capture setup](https://developer.apple.com/documentation/avfoundation/capture-setup)
- [Apple AVCaptureDevice](https://developer.apple.com/documentation/avfoundation/avcapturedevice)
- [Apple white-balance configuration](https://developer.apple.com/documentation/avfoundation/capture-device-white-balance)
- [Illumination-induced domain shift in food recognition](https://arxiv.org/abs/2602.08491)
- [Food image recognition with CNNs and augmentation](https://arxiv.org/abs/1612.00983)

The two arXiv papers are research evidence for augmentation and illumination robustness; they are not used as a substitute for device measurements.

## Acceptance impact

The camera configuration changes are low-risk platform plumbing and require Android and physical-iOS capture evidence. The preprocessing baseline remains unchanged until a larger, stratified experiment demonstrates a statistically meaningful gain. G-3 can now prove five exact single-dish semantic labels on-device, but it still cannot claim multi-object recognition, ingredient extraction, portion estimation, or nutrition correctness.
