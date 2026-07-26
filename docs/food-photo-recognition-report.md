# Food photo recognition in nutrition apps

> Generated from the research outline and validated JSON evidence. Uncertain fields are intentionally omitted from the generated field summaries; see the confidence/limits section at the end.

Research date: Current as of 2026-07-26, with historical evidence where needed

## Executive conclusion

The public evidence points to a dominant pattern: photo nutrition apps use a cloud or hybrid recognizer to propose foods, then map the accepted candidates to a proprietary food database and require user correction. A generic local LLM is useful for candidate labels and explanations, but no reviewed local model is a validated single-photo calorie, gram, or hidden-ingredient estimator.

### App/provider matrix

| App | Public provider signal | Local/cloud signal | Confidence |
| --- | --- | --- | --- |
| MyFitnessPal Meal Scan | MFP says models are built/run by MFP; Passio claims the partnership behind Meal Scan | Current APK has no obvious food model; Passio documents historical local mode and current remote photo mode | Partnership high; current path unresolved |
| YAZIO AI | Current privacy policy names OpenAI Ireland for YAZIO AI functions | Photo workflow and policy signal cloud processing; no obvious food model in current XAPK | OpenAI for YAZIO AI high; exact photo route unresolved |
| Lose It Snap It | Current provider not disclosed; Google documents historical custom Snap It model with ML Kit/TFLite | Historical implementation ran quantized model on-device/offline and could update from Firebase | Historical on-device high; current architecture unknown |
| Foodvisor | No current named food-recognition vendor found; early People For AI contribution is historical | Current XAPK exposes api.foodvisor.io and no obvious food model; likely server boundary is an inference, not proof | Current vendor unresolved |
| Cal AI | Privacy notice says photos go to its API/service providers; vendor not named | Strong server-processing signal; no obvious food model in current XAPK | Cloud/API signal high; vendor unknown |
| HealthifyMe | Policy names third-party AI processing category but not a vendor | Policy describes local food/non-food filtering followed by server processing, with some contradictory wording | Hybrid signal high; exact implementation/vendor unknown |

Sources: [MFP FAQ](https://support.myfitnesspal.com/hc/en-us/articles/360045761612-Meal-Scan-FAQ), [Passio MFP guide](https://www.passio.ai/blog/essential-guide-to-myfitnesspal-meal-scan), [YAZIO privacy](https://www.yazio.com/en/privacy), [Google Lose It case study](https://developers.google.com/ml-kit/case-studies/lose-it), [HealthifyMe policy](https://api.healthifyme.com/terms-of-use/), [Cal AI privacy](https://www.calai.app/privacy).

### Cost snapshot (public list prices, checked 2026-07-26)

| Option | Public price signal | Derived photo economics |
| --- | --- | --- |
| Passio Nutrition-AI Hub | $99/$599/$2,999 monthly tiers; $25/$10/$2.50 per 1M refill by tier | Vendor says 20k-30k tokens/photo: about $0.05-$0.075 at Pro refill, $0.20-$0.30 Growth, $0.50-$0.75 Starter; fixed fees/included credits change effective cost |
| LogMeal | Credit-based; one recognition+portion call = 1 credit; nutrition/recipe data add no credit; public trial 30 days/200 queries/5 users | Exact public plan table was not visible without account context |
| Gemini 3.1 Flash-Lite | $0.25/M input image tokens, $1.50/M output | At 1,120 image tokens + 300 output: approximately $0.00073/photo before infrastructure |
| OpenAI GPT-5.4 mini | $0.75/M input, $4.50/M output; image input billed as image tokens | Cost depends on image tokenization/detail and output length; not a YAZIO-specific price |
| AWS Rekognition DetectLabels | Starts at $0.001/image for first 1M, generic labels | Not food/nutrition-specific; needs custom model/database work |

Sources: [Passio pricing](https://www.passio.ai/pricing), [LogMeal pricing](https://logmeal.com/api/pricing/), [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing), [OpenAI GPT-5.4 mini](https://developers.openai.com/api/docs/models/gpt-5.4-mini), [AWS Rekognition pricing](https://aws.amazon.com/rekognition/pricing/).

### Local-model shortlist

- iOS: Core ML/Vision for a compact detector/classifier/segmenter; Apple Foundation Models for an optional semantic fallback on Apple Intelligence hardware.
- Android: LiteRT/MediaPipe for dedicated vision; Gemini Nano via ML Kit on supported devices; Gemma 3n E2B via LiteRT-LM for a high-end open-weight tier.
- Cross-platform research baseline: SmolVLM-256M/500M through ONNX Runtime Mobile; use a custom food classifier for broad device coverage.
- Qwen2.5-VL-3B and current Moondream 3.x are research/high-end candidates, not the first low-risk commercial mobile choice; licensing and memory are material constraints.

Sources: [Gemma 3n](https://ai.google.dev/gemma/docs/gemma-3n), [Apple image prompting](https://developer.apple.com/documentation/FoundationModels/analyzing-images-with-multimodal-prompting), [Android Gemini Nano](https://developer.android.com/ai/gemini-nano), [SmolVLM](https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct), [ONNX Runtime Mobile](https://onnxruntime.ai/docs/tutorials/mobile/).

### Decision for SuperNutrition

Start with a hybrid MVP: local quality gate + compact food detector/classifier, food-specific cloud fallback for ambiguous/recipe-level cases, deterministic nutrition database lookup, visible assumptions/ranges, and mandatory user confirmation for servings, brand/recipe, oil/sauce, and allergen-relevant ingredients. Select Passio vs LogMeal vs a generic VLM only after measuring corrected-meal accuracy, grams/calorie error, latency, privacy, and cost per accepted meal on a weighed, culturally diverse phone-photo set.

## Contents

1. [MyFitnessPal Meal Scan](#myfitnesspal_meal_scan) — Current public documentation says Meal Scan is available to Premium users on iOS 17 or newer and Android 12 or newer, with MyFitnessPal version 22.17 or newer and the device/app...
2. [Foodvisor](#foodvisor) — Primary native clients are iOS and Android.  The current US App Store listing reports iOS 17.0 or later and an A12 Bionic chip or later; the inspected Android XAPK is Android 9...
3. [YAZIO AI Food Scanner](#yazio_ai_food_scanner) — Android and iOS.  The public Android artifact analyzed was package com.yazio.android, version 26.29.0.
4. [Lose It Snap It](#lose_it_snap_it) — Mobile iOS and Android product; current public support documentation describes a mobile photo workflow.  A current APK was not locally analyzed in this pass.
5. [Cal AI and comparable photo-calorie apps](#cal_ai_and_comparable_photo_calorie_apps) — iOS and Android.  The public Android artifact analyzed was package com.viraldevelopment.calai, version 26.28.0.
6. [Recognition service and API cost landscape](#recognition_service_and_api_cost_landscape) — Cloud APIs can serve both iOS and Android through a backend.  Local runtime alternatives are covered in the on-device item.
7. [Public Android artifact static analysis](#public_android_artifact_static_analysis) — Android artifacts only; iOS binaries were not obtained or analyzed.
8. [On-device models and mobile runtimes](#on_device_models_and_mobile_runtimes) — iOS and Android, with different hardware/runtime strengths.
9. [Food recognition accuracy and safety](#food_recognition_accuracy_and_safety) — Evaluation should use phone RGB images on iOS/Android device cohorts, with optional depth/AR as a separate condition.
10. [Recommended hybrid architecture for SuperNutrition](#recommended_hybrid_architecture_for_supernutrition) — iOS and Android with a shared backend contract and platform-specific local inference adapters.

## Detailed findings

### 1. MyFitnessPal Meal Scan

*Investigate Meal Scan's image-recognition workflow, backend or model providers, subscription/product positioning, privacy disclosures, and evidence for or against on-device inference.*

#### Market and product: product_scope

MyFitnessPal Meal Scan is a Premium/Premium+ mobile logging feature.<br><br>It uses a live camera view, and in the newer iOS flow a gallery photo upload, to identify visible foods or ingredients in a meal.<br><br>It proposes matching verified foods from MyFitnessPal's food database; the user confirms items, edits serving size or number of servings, searches for missed items, and adds the selections to the Diary.<br><br>It is positioned as a logging-speed and adherence feature, not as a medical tool or a guaranteed measurement of calories.<br><br>MyFitnessPal describes the recognition layer as machine learning plus computer vision trained on millions of images.<br><br>Sources: https://support.myfitnesspal.com/hc/en-us/articles/360045761612-Meal-Scan-FAQ ; https://blog.myfitnesspal.com/meal-scan/ ; https://www.passio.ai/blog/essential-guide-to-myfitnesspal-meal-scan

#### Market and product: supported_platforms

Current public documentation says Meal Scan is available to Premium users on iOS 17 or newer and Android 12 or newer, with MyFitnessPal version 22.17 or newer and the device/app language set to English.<br><br>Photo upload is documented as iOS-only and United States-only in the January 2026 FAQ.<br><br>MyFitnessPal also has a web client and the app stores list iPhone/iPad/Apple Watch and Android clients, but the Meal Scan workflow itself is documented for mobile camera clients rather than the web.<br><br>MyFitnessPal says third-party APK installations are unsupported.<br><br>Sources: https://support.myfitnesspal.com/hc/en-us/articles/360045761612-Meal-Scan-FAQ ; https://support.myfitnesspal.com/hc/en-us/articles/4402625100941-Supported-Devices ; https://apps.apple.com/us/app/myfitnesspal-calorie-counter/id341232718 ; https://play.google.com/store/apps/details?hl=en_US&id=com.myfitnesspal.android

#### Market and product: pricing

US consumer pricing checked 2026-07-26: Premium is publicly listed at $19.99/month or $79.99/year; Premium+ is listed at $24.99/month or $99.99/year.<br><br>Meal Scan is included in Premium and Premium+.<br><br>The US App Store listing also exposes $9.99 monthly and $49.99/$79.99 annual purchase entries, which appear to be alternate, promotional, or legacy product identifiers; the in-app purchase screen and region should be treated as authoritative for a specific user.<br><br>MyFitnessPal promotes a free Premium trial, with availability and duration potentially varying by account and channel.<br><br>Sources: https://www.globenewswire.com/news-release/2026/02/24/3243668/0/en/MyFitnessPal-Debuts-Its-2026-Winter-Release.html ; https://support.myfitnesspal.com/hc/en-us/articles/34889191368077-What-s-the-difference-between-Free-Premium-and-Premium ; https://apps.apple.com/us/app/myfitnesspal-calorie-counter/id341232718 ; https://www.myfitnesspal.com/premium?legacy=true

#### Recognition pipeline: recognition_behavior

The documented flow is: open the blue plus button, select Meal Scan, follow the capture guidance, and either hover the live camera over the food or use the shutter/upload-photo option.<br><br>The recognizer can return multiple visible foods and suggested alternatives.<br><br>The user selects the desired suggestion, adjusts serving size or number of servings, uses database search for anything missed, reviews the selections, and taps the checkmark and Add to Diary; items can be edited later from the Add Food page.<br><br>MyFitnessPal and Passio say the system works best on common, clearly visible, separated ingredients.<br><br>A peach viewed from the top can resemble an apple; hidden ingredients, mixed dishes, soups, smoothies, and foods obscured by other foods are expected failure cases.<br><br>Sources: https://support.myfitnesspal.com/hc/en-us/articles/360045761612-Meal-Scan-FAQ ; https://blog.myfitnesspal.com/meal-scan/ ; https://www.passio.ai/blog/essential-guide-to-myfitnesspal-meal-scan

#### Static analysis: artifact_source

Public APKCombo XAPK used for current static inspection: package com.myfitnesspal.android, version 26.27.0, version code 50983, min SDK 32, target SDK 36, total XAPK size 163,592,837 bytes, SHA-256 08c3b51c7bdab6e055363c4bb4b4ade0fbff7121b30e55c58507bbdf979dbb9a.<br><br>Temporary files: /tmp/supernutrition-apk-analysis/myfitnesspal-26.27.0.xapk and /tmp/supernutrition-apk-analysis/myfitnesspal-26.27.0-jadx.

#### Static analysis: reverse_engineering_limits

No APK was installed or executed, no paid-account runtime session was captured, and no network trace was collected.<br><br>Third-party public artifacts are untrusted inputs; model assets can be downloaded later, encrypted, or delivered in a split/native component.<br><br>Static analysis cannot establish the current feature flag, image retention behavior, request payload, server-side model, data residency, or whether live-camera and gallery-upload paths use the same recognizer.<br><br>MyFitnessPal's public pages also do not publish a model card or an independent Meal Scan accuracy benchmark.

#### Mobile model evaluation: candidate_models

For SuperNutrition rather than as a claim about MyFitnessPal's proprietary model: (1) EfficientNet-Lite or MobileNetV3, using an Apache-2.0-compatible TensorFlow distribution and fine-tuning on a controlled food taxonomy, is the best first local classifier for fast common-food recognition; it needs a separate detector/segmenter for multi-item plates.<br><br>Source: https://blog.tensorflow.org/2020/03/higher-accuracy-on-vision-models-with-efficientnet-lite.html.<br><br>(2) MobileCLIP/MobileCLIP2 S0-S2 provides image-text zero-shot retrieval and Apple reports roughly 3-15 ms and 50-150M parameters for the family, but Apple's released model weights are under a research-only Apple Machine Learning Research Model license, so they are not a safe commercial default.<br><br>Sources: https://machinelearning.apple.com/research/mobileclip ; https://github.com/apple/ml-mobileclip ; https://github.com/apple/ml-mobileclip/blob/main/LICENSE_MODELS.<br><br>(3) SmolVLM-256M-Instruct is an Apache-2.0, image-plus-text 256M model intended for efficient multimodal inference and reported by its model card to run on one image with under 1GB of GPU RAM; use it for a prototype candidate generator or explanation layer, not as the authority for calories.<br><br>Source: https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct.<br><br>(4) Gemma 3n E2B/E4B is a commercially usable Google open-weight multimodal family optimized for phones, with visual/text input, a MobileNet-V5 vision encoder, selective parameter activation, and effective memory reduction; it is a higher-quality local fallback for capable devices but much heavier than a classifier and still needs food-specific validation.<br><br>Sources: https://ai.google.dev/gemma/docs/gemma-3n ; https://deepmind.google/models/gemma/gemma-3n/.<br><br>In all cases, use a canonical nutrition database after recognition instead of asking a generic VLM to invent calorie values.

#### Mobile model evaluation: mobile_runtime_support

Core ML is the native iOS route: it can convert models from PyTorch or TensorFlow and use CPU, GPU, and Neural Engine resources while keeping inference on-device.<br><br>Google AI Edge/LiteRT supports custom model conversion and hardware-accelerated CPU/GPU/NPU deployment across Android and iOS; MediaPipe is useful for camera/task plumbing, and LiteRT-LM is the Google route for on-device multimodal language models.<br><br>ONNX Runtime Mobile supports Android and iOS with CPU, XNNPACK, Android NNAPI, and iOS Core ML execution providers; its documentation recommends quantization and device-specific benchmarking.<br><br>ExecuTorch supports exporting PyTorch models to Android and iOS with XNNPACK, Vulkan, Qualcomm QNN, Core ML, and MPS backends.<br><br>Practical choice: LiteRT or ONNX Runtime for a compact detector/classifier; ExecuTorch or LiteRT-LM for a PyTorch/VLM experiment; Core ML for an iOS-specific optimized path.<br><br>Sources: https://developer.apple.com/documentation/coreml/ ; https://developers.google.com/edge ; https://onnxruntime.ai/docs/tutorials/mobile/ ; https://docs.pytorch.org/executorch/stable/pathway-quickstart.html

#### Economics and recommendation: recommendation

Use a transparent hybrid architecture as the next SuperNutrition experiment.<br><br>Run a small, quantized local detector/classifier for common foods and visible components, keep the photo and first-pass embeddings on-device, map accepted candidates to a curated nutrition database, and require the user to confirm or adjust servings.<br><br>Route only ambiguous, complex, or explicitly uploaded photos to a cloud recognizer such as a contracted Passio-style service; make the transfer opt-in, ephemeral, encrypted, region-aware, and visible in the UI.<br><br>Do not copy MyFitnessPal's ambiguous provider story or infer exact calories from a generic VLM.<br><br>First experiment: collect a consented, culturally diverse benchmark of roughly 300-500 meals with weighed portions and ingredient metadata; compare local-only, cloud-only, and hybrid modes for item accuracy, missed foods, serving-size error, calories/macros error, latency, battery, offline behavior, and privacy/retention.<br><br>If evaluating Passio, obtain written answers on the exact SDK/model version, live versus upload routing, image retention, model training, data location, DPA, SLA, and price before production use.<br><br>Sources: https://passio.gitbook.io/nutrition-ai/mobile-sdks/mobile-sdk-overview ; https://passio.gitbook.io/nutrition-ai/fundamentals/developer-faq ; https://developers.google.com/edge ; https://developer.apple.com/documentation/coreml/

### 2. Foodvisor

*Investigate Foodvisor's photo food-recognition pipeline, proprietary versus third-party services, pricing, privacy disclosures, and mobile inference clues.*

#### Market and product: product_scope

Foodvisor's photo feature is intended to identify multiple visible foods in a meal and return an editable food log with calories, macronutrients, vitamins, and minerals.<br><br>Premium is required for photo meal analysis; the free tier emphasizes manual logging.<br><br>Support recommends a top-down, bright photo with foods separated and allows selecting an alternative, searching manually, or omitting a wrong suggestion.<br><br>Historical technical reporting described food classification plus estimated weight from plate and phone geometry.<br><br>Sources: https://play.google.com/store/apps/details?hl=en-US&id=io.foodvisor.foodvisor ; https://foodvisor.zendesk.com/hc/en-us/articles/360013600800-Is-Foodvisor-free ; https://foodvisor.zendesk.com/hc/en-us/articles/360013100720-How-to-add-a-meal ; https://techcrunch.com/2019/10/14/foodvisor-automatically-tracks-what-you-eat-using-deep-learning/

#### Market and product: supported_platforms

Primary native clients are iOS and Android.<br><br>The current US App Store listing reports iOS 17.0 or later and an A12 Bionic chip or later; the inspected Android XAPK is Android 9 or later with min SDK 28.<br><br>Foodvisor's terms mention a website for subscriptions, but the checked sources do not establish a full web photo-recognition client.<br><br>Sources: https://apps.apple.com/us/app/foodvisor-ai-calorie-counter/id1064020872 ; https://apkpure.net/foodvisor-nutrition-diet/io.foodvisor.foodvisor/download ; https://www.foodvisor.io/en/terms-of-service/raw/

#### Market and product: pricing

The app is free to download; Foodvisor's help center says the free tier provides a manual food diary and Premium adds photo meal analysis, detailed breakdowns, recipes, courses, and nutrition customization.<br><br>Pricing is region-, platform-, promotion-, and offer-specific; subscriptions are sold through Apple, Google, or the Foodvisor website with Stripe and normally auto-renew.<br><br>The US App Store page observed on 2026-07-26 listed these displayed offer prices: Foodvisor Premium USD 23.99 and USD 83.99; 1 month of Foodvisor USD 14.99, USD 29.99, and USD 39.99; Premium or trial entries USD 59.99 and USD 71.99; 3 months USD 59.99 and USD 89.99; and Premium Bundle USD 29.99.<br><br>The offer labels do not prove the billing period for every entry.<br><br>Sport & Meal Plan is described as a one-time lifetime pack, but the help article does not state its price.<br><br>No public developer or food-recognition API tariff was found.<br><br>Sources: https://foodvisor.zendesk.com/hc/en-us/articles/360013600800-Is-Foodvisor-free ; https://foodvisor.zendesk.com/hc/en-us/articles/26650824663580-How-much-does-the-Foodvisor-subscription-cost ; https://apps.apple.com/us/app/foodvisor-ai-calorie-counter/id1064020872

#### Recognition pipeline: recognition_behavior

The public flow is Photo tab, capture or select an image, backend analysis, candidate foods and nutrition, user confirmation or correction, then meal logging.<br><br>Foodvisor recommends a top-down, bright image with foods separated; when a food is missed or mismatched, the user can choose an alternative, search manually, return to the photo, or omit the suggestion.<br><br>Static analysis of Android 7.2.0 maps the current multimodal path to a multipart POST at food/search/multimodal/ with image and payload parts, returning response_type, conversation_id, and an optional food or error.<br><br>Separate routes accept a food-report photo and nutrition-table photo, and a choice endpoint records corrections.<br><br>This is a current client request shape from static analysis, not a live UI or network trace.<br><br>Sources: https://foodvisor.zendesk.com/hc/en-us/articles/360013100720-How-to-add-a-meal ; https://foodvisor.zendesk.com/hc/en-us/articles/360013101720-What-to-do-when-my-food-is-not-recognized ; https://foodvisor.zendesk.com/hc/en-us/articles/360013600800-Is-Foodvisor-free ; https://apkpure.net/foodvisor-nutrition-diet/io.foodvisor.foodvisor/download

#### Recognition pipeline: portion_and_nutrition_method

Historical public reporting says Foodvisor used autofocus data to estimate plate-to-phone distance, measured the image area of each food, extrapolated volume using food-specific assumptions, and estimated weight; the user could correct the result before logging.<br><br>Current Android DTOs expose analysis confidence, main_food_id, quantity, food_id, unit_id, and FoodInfoRemote records with per-100g calories, proteins, lipids, carbohydrates, fiber, micronutrients, g_per_serving, food units, barcode, brand, and database-source fields.<br><br>This supports a vision quantity to Foodvisor food-database and nutrition-unit mapping, with manual correction and a separate nutrition-label OCR path for packaged foods.<br><br>The current portion formula, hidden-ingredient handling, density assumptions, and calibration are not disclosed; the terms state that results are indicative and accuracy is not guaranteed.<br><br>Sources: https://techcrunch.com/2019/10/14/foodvisor-automatically-tracks-what-you-eat-using-deep-learning/ ; https://www.foodvisor.io/en/terms-of-service/raw/ ; https://apkpure.net/foodvisor-nutrition-diet/io.foodvisor.foodvisor/download

#### Static analysis: artifact_source

Public APKPure XAPK downloaded for private static analysis from https://apkpure.net/foodvisor-nutrition-diet/io.foodvisor.foodvisor/download: package io.foodvisor.foodvisor, version 7.2.0, version code 5214, minimum SDK 28, target SDK 35.<br><br>The downloaded XAPK SHA-256 was 5d571f40a5940759f3e05f872dadd47d2307230a98540bd7dcbdfcd2834748e and it contained the base APK plus armeabi-v7a, English, and mdpi splits.<br><br>APKPure published the Foodvisor signing-certificate SHA-1 5ed5265d7b1cae0b8ef9b7edebb36b2d6114edd0.<br><br>The base APK was decoded with Apktool 3.0.2 into /tmp/foodvisor-apktool-fi3MIH.<br><br>APKMirror independently lists 7.2.0 as a signed Android bundle.<br><br>This is a third-party redistribution, not an official Foodvisor binary source.

#### Static analysis: static_findings

The Android manifest declares CAMERA, INTERNET, network-state and Wi-Fi access, RECORD_AUDIO, storage, billing, notifications, boot and wake permissions, Health Connect read and write access for steps, weight, exercise, calories and basal metabolic rate, and advertising or attribution permissions.<br><br>The base APK contains CameraX, Jetpack Compose, Retrofit, OkHttp, Moshi, Firebase, AppsFlyer, Amplitude, RevenueCat, AppLovin, Unity Ads, Facebook, and Zendesk classes; these are app plumbing, analytics, ads, monetization, or support and are not recognition-provider evidence.<br><br>A decoded API configuration contains https://api.foodvisor.io/api/{version}/{platform}/{country}/{locale}/.<br><br>The decoded Retrofit interface in smali_classes4/mq/a.smali exposes multipart food/search/multimodal/ with image and payload, food/search/multimodal/save_gpt_food, food_report/, food_report/nutri_table_ocr/, food/barcode/, food/search/app/, food/updates/, and food/search/choice/.<br><br>DTOs preserve response_type, conversation_id, food, error, analysis confidence, main_food_id, quantity, and detailed per-100g nutrition fields.<br><br>No obvious bundled food model asset or named external recognition endpoint was found.

#### Static analysis: reverse_engineering_limits

This was static inspection of one public Android 7.2.0 XAPK only.<br><br>The app was not installed or executed; no authenticated account, paid feature, food photo upload, runtime API response, TLS traffic capture, or server-side experiment was used; the iOS binary was not inspected.<br><br>R8 obfuscation, split packaging, remote configuration, downloaded models, feature flags, and backend changes can hide or change behavior.<br><br>The API path template was found, but the runtime-resolved version and platform path were not observed.<br><br>Absence of a model asset does not prove cloud-only inference, and the save_gpt_food name does not prove an OpenAI or other named provider.<br><br>Historical portion claims may not describe the current model.<br><br>The checked privacy policy does not state a precise raw food-photo retention period.<br><br>No attempt was made to bypass authentication, DRM, or transport security.

#### Mobile model evaluation: candidate_models

Candidates for SuperNutrition, not evidence of Foodvisor's model: MobileNetV3-small or large as a food-specific classifier backbone; EfficientDet-Lite or another small detector for multiple food regions; MobileNet- or DeepLab-style segmentation for portion masks; and a small ONNX detector such as a YOLO variant only after license review.<br><br>Use food-specific training or fine-tuning rather than generic ImageNet labels, and evaluate Food-101, FoodSeg103, Nutrition5k, or an internally consented meal set as appropriate.<br><br>MediaPipe Tasks accepts compatible TFLite model assets for Android; ONNX Runtime Mobile accepts ONNX models; ExecuTorch is an option for exported PyTorch vision models or small multimodal models.<br><br>Ultralytics code is AGPL-3.0 or commercial, while MediaPipe is Apache 2.0; verify the exact weights, dataset, and model license before shipping.<br><br>None of these models is a validated calorie or portion estimator by itself.<br><br>Sources: https://arxiv.org/abs/1905.02244 ; https://developers.google.com/edge/mediapipe/solutions/vision/image_classifier/android ; https://onnxruntime.ai/docs/tutorials/mobile/ ; https://pytorch.org/executorch/stable/ ; https://github.com/ultralytics/ultralytics ; https://github.com/google-ai-edge/mediapipe

#### Mobile model evaluation: mobile_runtime_support

Android options are LiteRT or MediaPipe Tasks with a compatible TFLite model in app assets and CPU, GPU, or platform delegates; ONNX Runtime Mobile with an ONNX model and CPU, XNNPACK, NNAPI, or other execution providers; or ExecuTorch with an exported PyTorch program and a selected XNNPACK, Vulkan, Qualcomm, or other backend.<br><br>iOS's native path is Core ML with an mlmodel or mlpackage, optionally wrapped by Vision; coremltools converts TensorFlow and PyTorch models, and current ML Program output targets iOS 15 or later.<br><br>ONNX Runtime also supports iOS with Core ML and XNNPACK execution providers.<br><br>Use separate detector, segmentation, and nutrition-database components rather than forcing a language model to produce authoritative nutrient numbers.<br><br>Foodvisor's package proves CameraX capture, not a local inference runtime.<br><br>Sources: https://developers.google.com/edge/mediapipe/solutions/vision/image_classifier/android ; https://onnxruntime.ai/docs/tutorials/mobile/ ; https://pytorch.org/executorch/stable/ ; https://apple.github.io/coremltools/docs-guides/source/target-conversion-formats.html

#### Mobile model evaluation: device_requirements

Planning estimates for a new app, not measurements of Foodvisor: start with a quantized 20M-100M-parameter detector or segmenter, approximately 10-100 MB of model storage, about 0.5-1.5 GB of free process memory, and a target of roughly 50-200 ms for one still-photo inference on a recent A12-class iPhone or modern mid-range Android device with an accelerator.<br><br>CPU fallback will be slower and use more battery.<br><br>A 0.5B-3B or larger multimodal model typically needs hundreds of MB to multiple GB, int4 or int8 quantization, model download and cache controls, and device gating, so it should not be the baseline.<br><br>Benchmark CPU, GPU, NPU or NNAPI/Core ML paths, app size, latency, thermal behavior, and battery on representative low, mid, and high tiers.<br><br>The current Foodvisor App Store page reports a 277.8 MB app size and A12 requirement, but that is not evidence of model size.<br><br>Sources: https://onnxruntime.ai/docs/tutorials/mobile/ ; https://developers.google.com/edge/mediapipe/solutions/vision/image_classifier/android ; https://apps.apple.com/us/app/foodvisor-ai-calorie-counter/id1064020872

#### Mobile model evaluation: expected_food_inference_quality

Evidence-backed expectations, not a Foodvisor benchmark: performance should be strongest when foods are separated, visible, well-lit, top-down, and common, matching Foodvisor's own capture guidance.<br><br>Likely failure modes are mixed or occluded dishes, sauces and oils, hidden ingredients, culturally specific foods, visually similar items, brand and packaging variants, blur or glare, and exact portion mass.<br><br>A single RGB photo cannot reliably see hidden ingredients or exact weight; autofocus or scale cues can add priors but cannot recover density or recipe composition.<br><br>Foodvisor's correction workflow is important for these cases, and its terms disclaim accuracy and medical use.<br><br>No independent current accuracy or portion-error benchmark for Foodvisor was found.<br><br>Sources: https://foodvisor.zendesk.com/hc/en-us/articles/360013100720-How-to-add-a-meal ; https://foodvisor.zendesk.com/hc/en-us/articles/360013101720-What-to-do-when-my-food-is-not-recognized ; https://www.foodvisor.io/en/terms-of-service/raw/ ; https://arxiv.org/abs/2208.02490

#### Economics and recommendation: privacy_and_compliance

Apple's privacy label says data linked to the user includes contact information, user and device identifiers, health and fitness data, purchases, photos or videos, product interaction, and customer support data; identifiers are listed for tracking and diagnostics are listed as not linked.<br><br>Foodvisor's privacy PDF, updated 2025-11-18, describes diet, lifestyle, nutrition, and health processing, including allergies and medical history; it states one-year retention for connection logs and IP data, a maximum 13-month cookie period, EU hosting, and access or deletion requests through data@foodvisor.io.<br><br>Its retention language is internally inconsistent: one section says health data is kept for service duration and immediately deleted on termination or account inactivity, while another summary says health data is retained until account deletion; the policy does not state a precise raw food-photo retention period.<br><br>The terms grant Foodvisor a non-exclusive, worldwide, royalty-free license to use and modify uploaded food photos, allow partners or subprocessors as needed, mention encryption, say Apple Health data is used only when authorized and not for marketing or third-party sharing, and disclaim accuracy and medical use.<br><br>Age eligibility wording should also be reconciled across the privacy policy and terms.<br><br>For SuperNutrition, require granular photo and health consent, purpose limitation, short raw-image TTL, deletion and export, a subprocessor and DPA inventory, regional transfer safeguards, no model-training reuse without a separate opt-in, and eating-disorder and medical-claim safeguards.<br><br>Sources: https://apps.apple.com/us/app/foodvisor-ai-calorie-counter/id1064020872 ; https://cdn.foodvisor.io/policies/fv_privacy_en.pdf ; https://www.foodvisor.io/en/terms-of-service/raw/

#### Economics and recommendation: recommendation

Use a hybrid architecture for SuperNutrition: local quantized image-quality, orientation, and food-region detection or segmentation; a first-party backend that maps candidates to a controlled nutrition database; and an optional multimodal fallback for ambiguous cases.<br><br>Transmit only after explicit consent, remove EXIF and unnecessary resolution, avoid raw-photo retention by default, return per-item confidence and uncertainty, and always provide edit, search, barcode, and manual fallback.<br><br>For the next experiment, collect 300-500 consented representative meals with item and portion ground truth and compare a local detector, a cloud multimodal baseline, and a Foodvisor-like correction workflow on food recall, portion error, calorie error, correction rate, latency, battery, and retention.<br><br>Do not infer Foodvisor's external provider from save_gpt_food or reproduce its endpoint; obtain runtime evidence or vendor disclosure if procurement depends on that attribution.<br><br>Sources: https://apkpure.net/foodvisor-nutrition-diet/io.foodvisor.foodvisor/download ; https://foodvisor.zendesk.com/hc/en-us/articles/360013101720-What-to-do-when-my-food-is-not-recognized ; https://cdn.foodvisor.io/policies/fv_privacy_en.pdf

### 3. YAZIO AI Food Scanner

*Investigate YAZIO's AI Food Scanner, image and nutrition-data providers, plan pricing, privacy disclosures, and evidence for or against local inference.*

#### Market and product: product_scope

YAZIO AI Food Recognition is a Pro feature that analyzes a meal photo, detects ingredients, estimates initial portions and nutrition, and lets the user edit ingredients, quantities, and meal name before saving.<br><br>YAZIO explicitly calls the result a rough estimate.

#### Market and product: supported_platforms

Android and iOS.<br><br>The public Android artifact analyzed was package com.yazio.android, version 26.29.0.

#### Market and product: pricing

The AI food-recognition feature is part of YAZIO Pro; consumer subscription price varies by market and offer.<br><br>No public developer API price for YAZIO's internal service was found.<br><br>Source: https://help.yazio.com/hc/en-us/articles/39137901903889-What-is-the-AI-Calorie-Tracking-Feature-and-how-does-it-work

#### Recognition pipeline: recognition_behavior

Photo -> AI detects ingredients -> initial portion/nutrition estimate -> user adjusts ingredients and amount -> meal is saved or templated.<br><br>The Android resource strings also state that photos are not stored or shared, that the AI was trained on millions of meals, and that users can have a daily AI-prompt limit.

#### Recognition pipeline: identified_services

YAZIO's current privacy policy names OpenAI Ireland Ltd.<br><br>as the technical service provider for YAZIO AI functions and says data may be transmitted to OpenAI servers.<br><br>It does not explicitly document which exact photo-scanner request or model is used, so an OpenAI connection is high-confidence for YAZIO AI generally but not fully mapped to every scanner path.<br><br>Sources: https://www.yazio.com/en/privacy and https://help.yazio.com/hc/en-us/articles/39137901903889-What-is-the-AI-Calorie-Tracking-Feature-and-how-does-it-work

#### Recognition pipeline: local_inference_evidence

YAZIO's photo workflow and privacy policy provide a strong cloud-processing signal, while the public Android XAPK contains no obvious food VLM/model assets.<br><br>The base APK includes ML Kit barcode assets/classes, which appear unrelated to food-photo understanding.<br><br>Runtime downloads cannot be ruled out.

#### Recognition pipeline: portion_and_nutrition_method

YAZIO documents initial estimates from detected ingredients and asks the user to enter the most accurate portion sizes/amounts.<br><br>It does not claim measurement-grade calories or grams.<br><br>Nutrition is presumably resolved through YAZIO's food database after the AI candidate step; the exact data pipeline is not public.

#### Static analysis: artifact_source

Public XAPK for package com.yazio.android, version 26.29.0, version code 607170145, min SDK 32, target SDK 35, total XAPK size 72,888,184 bytes, SHA-256 4bea1dbef69ca909530d7318aa360670cebc73f2518d4a9341e1604b2d6eda09.<br><br>Temporary files: /tmp/supernutrition-apk-analysis/yazio-26.29.0.xapk and /tmp/supernutrition-apk-analysis/yazio-26.29.0-jadx.

#### Static analysis: static_findings

The base APK has CAMERA and INTERNET permissions, AI food-tracking/photo-upload resources, CameraX image-analysis code, and bundled ML Kit barcode models/classes.<br><br>No obvious food VLM/classifier model file was found in the XAPK inventory.<br><br>The app is heavily obfuscated, so static endpoint/provider attribution is limited.

#### Static analysis: reverse_engineering_limits

No installation, execution, account login, traffic capture, or authentication bypass was attempted.<br><br>Static assets cannot prove whether a cloud request happens for a particular feature flag or whether models are downloaded later.

#### Mobile model evaluation: candidate_models

For SuperNutrition, evaluate a local food classifier/segmenter first; use Gemma 3n E2B or SmolVLM as optional local semantic fallbacks, Apple Foundation Models on supported iOS, and Gemini Nano on supported Android.<br><br>Use a nutrition database for final nutrients.

#### Mobile model evaluation: mobile_runtime_support

Core ML, LiteRT, ExecuTorch, and ONNX Runtime Mobile can host local vision models.<br><br>ONNX Runtime supports Android and iOS packages and Core ML/XNNPACK or NNAPI/XNNPACK accelerators.<br><br>Source: https://onnxruntime.ai/docs/tutorials/mobile/

#### Mobile model evaluation: device_requirements

A compact classifier is broadly deployable; a 2B-4B multimodal model should be limited to modern high-memory devices and measured for sustained battery/thermal behavior.

#### Mobile model evaluation: expected_food_inference_quality

YAZIO itself warns that outputs are rough.<br><br>Common visible foods should be easier than recipe composition, hidden sauces/oils, mixed dishes, unfamiliar cuisine, and portion size.

#### Economics and recommendation: privacy_and_compliance

YAZIO's policy says OpenAI Ireland is the technical provider, data may reach US servers under EU transfer safeguards, and data sent through OpenAI's API is not used to train general OpenAI models.<br><br>Product copy also says photos are not stored or shared.<br><br>Treat these as vendor disclosures that must be contractually verified for SuperNutrition.

#### Economics and recommendation: recommendation

YAZIO is a useful reference architecture: cloud AI candidate generation, explicit rough-estimate disclaimer, and user correction.<br><br>Replicate the confirmation UX and make local/cloud routing explicit rather than copying the hidden provider boundary.

### 4. Lose It Snap It

*Investigate Snap It recognition behavior, provider/model evidence, pricing, privacy disclosures, and mobile versus cloud processing.*

#### Market and product: product_scope

Lose It! Snap It is a Premium photo-logging feature that returns detected foods and estimated serving sizes for user review and editing.

#### Market and product: supported_platforms

Mobile iOS and Android product; current public support documentation describes a mobile photo workflow.<br><br>A current APK was not locally analyzed in this pass.

#### Market and product: pricing

Snap It is Premium-only.<br><br>Lose It does not publish a separate developer/API price for Snap It.<br><br>Source: https://loseit.zendesk.com/hc/en-us/articles/47771695186580-How-to-Use-Snap-It

#### Market and product: popularity_signal

Google's historical case study says Lose It had helped more than 30 million people by the time of publication; this is platform scale, not current Snap It usage.<br><br>Source: https://developers.google.com/ml-kit/case-studies/lose-it

#### Recognition pipeline: recognition_behavior

The current support flow is photo/upload -> detected foods and estimated serving size -> user can edit, swap, or add items.<br><br>Clear and bright photos improve results.

#### Recognition pipeline: local_inference_evidence

Google's case study explicitly says Snap It moved from a GPU server to a quantized custom model running on-device, including offline real-time inference, with models hostable/updated through Firebase.<br><br>Current behavior was not re-confirmed.

#### Recognition pipeline: portion_and_nutrition_method

The product exposes estimated serving size and maps the selected food to Lose It nutrition records; user editing is part of the workflow.<br><br>No current public evidence shows measurement-grade portion or calorie estimation.

#### Static analysis: static_findings

Historical official Google documentation is the strongest technical evidence: compressed/quantized TFLite custom model, ML Kit on-device deployment, Firebase model delivery, and offline real-time behavior.<br><br>This is a documented product case study rather than fresh binary proof.

#### Static analysis: reverse_engineering_limits

The historical case study cannot establish current model versions, cloud fallbacks, data retention, or feature flags.<br><br>No current APK installation or traffic inspection was attempted.

#### Mobile model evaluation: candidate_models

A compact food detector/classifier in Core ML or LiteRT is the closest local analogue.<br><br>Use local inference for fast candidates and a cloud fallback for ambiguous multi-food or recipe cases.

#### Mobile model evaluation: mobile_runtime_support

ML Kit/LiteRT is a natural Android path; Core ML is the native iOS equivalent.<br><br>ONNX Runtime Mobile is a cross-platform alternative.

#### Mobile model evaluation: device_requirements

Quantized small models can support broad device coverage; updateable model delivery is useful for drift but requires versioning, rollback, and privacy controls.

#### Mobile model evaluation: expected_food_inference_quality

On-device latency/offline behavior is achievable, but food taxonomy drift and complex meals remain risks.<br><br>Evaluate missed foods, top-k recall, serving error, and correction time rather than speed alone.

#### Economics and recommendation: unit_cost_estimate

A local model has no per-photo cloud charge; engineering, downloads, battery, and support are the main costs.<br><br>Any current Lose It unit cost is not public.

#### Economics and recommendation: privacy_and_compliance

Historical on-device inference reduces image transfer for that path.<br><br>If a cloud fallback exists today, the app must disclose it separately; do not infer privacy behavior from the historical case study.

#### Economics and recommendation: recommendation

Use the historical Snap It pattern as evidence that a small quantized local food model is viable for fast candidate generation, then measure a modern hybrid fallback on current devices.

### 5. Cal AI and comparable photo-calorie apps

*Investigate Cal AI, BiteSnap, and similar popular photo-calorie apps for model/provider signals, pricing, data handling, and user-reported strengths and failures.*

#### Market and product: product_scope

Cal AI is a photo-first calorie tracker that lets users snap a meal and receives an AI food/nutrition estimate; the user can edit the logged result.<br><br>Its public privacy notice treats image logging and dietary data as collected food/activity data.

#### Market and product: supported_platforms

iOS and Android.<br><br>The public Android artifact analyzed was package com.viraldevelopment.calai, version 26.28.0.

#### Market and product: pricing

Consumer subscription.<br><br>No official public developer/API price or named recognition vendor was found.<br><br>Source: https://www.calai.app/privacy

#### Market and product: popularity_signal

Google Play public metadata showed a 1M+ install class for the Android listing at the time of research.<br><br>Source: https://play.google.com/store/apps/details?id=com.viraldevelopment.calai

#### Recognition pipeline: recognition_behavior

Photo capture/upload -> AI identifies meal items and estimates calories/nutrients -> user reviews and edits.<br><br>Cal AI's privacy copy warns that blur, hidden portions, and mixed recipes can make results incomplete or inaccurate.

#### Recognition pipeline: local_inference_evidence

The public privacy language says photos are sent to an API/service provider, a strong cloud-processing signal.<br><br>The product homepage also markets phone depth-sensor volume estimation, which is not evidence that food recognition itself is local.

#### Recognition pipeline: portion_and_nutrition_method

AI estimates food and nutrition from the photo; the user is expected to correct the result.<br><br>The public disclosure itself acknowledges hidden portions and mixed recipes as failure sources.<br><br>Exact database and portion model are not public.

#### Static analysis: artifact_source

Public XAPK for package com.viraldevelopment.calai, version 26.28.0, version code 258, min SDK 29, target SDK 36, total XAPK size 131,781,927 bytes, SHA-256 2fd7c96d0e1471eccdd3d7814b5601ba40cd626ebc8e25caee7665fc26d91534.<br><br>Temporary files: /tmp/supernutrition-apk-analysis/calai-26.28.0.xapk and /tmp/supernutrition-apk-analysis/calai-26.28.0-jadx.

#### Static analysis: static_findings

The base APK has CAMERA and INTERNET permissions, obfuscated assets, ML Kit barcode classes/remote-model plumbing, and no obvious food model files by standard model extensions in the XAPK tree.<br><br>No named OpenAI/Gemini/Passio food endpoint was found in the static pass.<br><br>This is consistent with API-backed recognition but does not prove the absence of dynamically delivered models.

#### Static analysis: reverse_engineering_limits

No installation, execution, account login, network capture, or authentication bypass was attempted.<br><br>Obfuscation and split delivery limit endpoint attribution.

#### Mobile model evaluation: candidate_models

For a new app, keep a local compact food detector/classifier as a quality gate and candidate generator; use a cloud food API or multimodal model for difficult dishes, with a canonical nutrition database after confirmation.

#### Mobile model evaluation: mobile_runtime_support

LiteRT/MediaPipe, ONNX Runtime Mobile, and ExecuTorch can host local models on Android/iOS; Core ML is preferred for an iOS-specific path.

#### Mobile model evaluation: device_requirements

Depth/AR-assisted portion estimation should be evaluated separately from RGB recognition.<br><br>A local classifier is much cheaper than a general VLM in memory and battery.

#### Mobile model evaluation: expected_food_inference_quality

Expect difficulty with blur, hidden food, composite recipes, cooking oils, and portion scale.<br><br>Treat AI output as a draft, not a nutrition measurement.

#### Economics and recommendation: privacy_and_compliance

Cal AI's notice says photo-logged dietary data and images may be processed by its API/service providers and may be stored/synced depending on settings.<br><br>A nutrition app should document retention, training use, deletion, health-data handling, and cross-border transfers.

#### Economics and recommendation: recommendation

Cal AI is useful evidence for the cloud-photo plus correction UX, but not a source for vendor selection.<br><br>Ask for an explicit subprocessor/model disclosure if using a comparable API.

### 6. Recognition service and API cost landscape

*Compare credible image, multimodal LLM, food-classification, and nutrition-data services that could power the workflow, including current list prices, limits, and cost per analyzed meal.*

#### Market and product: product_scope

The practical service choices split into food-specific recognition/nutrition platforms, general multimodal LLM APIs, generic computer vision, and a first-party nutrition database.<br><br>Food-specific services reduce integration work; generic VLMs are flexible but require stronger validation and nutrition grounding.

#### Market and product: supported_platforms

Cloud APIs can serve both iOS and Android through a backend.<br><br>Local runtime alternatives are covered in the on-device item.

#### Market and product: pricing

Passio Nutrition-AI Hub: Starter $99/month with 1M tokens and $25/1M refill; Growth $599/month with 5M and $10/1M; Pro $2,999/month with 100M and $2.50/1M.<br><br>Passio says one photo analysis consumes roughly 20k-30k tokens, implying a derived refill-only estimate of $0.05-$0.075/photo at Pro rates, $0.20-$0.30 at Growth, and $0.50-$0.75 at Starter; fixed plan fees and included credits change effective cost.<br><br>LogMeal uses credits: food recognition plus portion estimation is 1 call/credit and nutrition/recipe data add no credit; a 30-day/200-query/5-user trial is public, while the exact plan table was not visible without account context.<br><br>Gemini 3.1 Flash-Lite is $0.25/M input image tokens and $1.50/M output; OpenAI GPT-5.4 mini is $0.75/M input and $4.50/M output with image input billed by image tokens.<br><br>AWS Rekognition generic DetectLabels starts at $0.001/image for the first million, but is not nutrition-specific.<br><br>Sources: https://www.passio.ai/pricing, https://logmeal.com/api/pricing/, https://ai.google.dev/gemini-api/docs/pricing, https://developers.openai.com/api/docs/models/gpt-5.4-mini, https://aws.amazon.com/rekognition/pricing/

#### Recognition pipeline: recognition_behavior

A sensible cloud call returns structured candidate foods, visible ingredients, portion range, confidence, ambiguity flags, and provenance IDs.<br><br>Nutrition values should be resolved through a canonical database rather than generated as free text.

#### Recognition pipeline: identified_services

Passio is the strongest directly food-specific option with public mobile SDKs, food database, portion endpoints, and a public MFP partnership claim.<br><br>LogMeal is another food-specific recognition/portion/nutrition API.<br><br>Gemini/OpenAI provide flexible multimodal reasoning but do not by themselves guarantee a food taxonomy or nutrition database.

#### Recognition pipeline: model_or_provider_confidence

Food-specific service capabilities and public prices are high-confidence from vendor documentation; per-photo cost estimates are derived from token/credit assumptions and must be confirmed in a vendor quote.

#### Recognition pipeline: local_inference_evidence

Cloud services are appropriate for difficult/ambiguous cases; local classifiers can remove obvious non-food/blurred frames and reduce cloud volume.<br><br>Passio documents both remote photo mode and legacy bundled local models.

#### Recognition pipeline: portion_and_nutrition_method

Passio/LogMeal expose food-specific nutrition and portion functionality.<br><br>Generic VLMs require a separate food database, unit conversion, recipe/brand resolution, and user confirmation.<br><br>No API should be treated as a scale from one RGB photo.

#### Static analysis: artifact_source

Pricing and limits were checked against official vendor pages on 2026-07-26; consumer APK artifacts are documented in Public_Android_artifact_static_analysis.json.

#### Static analysis: static_findings

No consumer app's public base XAPK was found to embed an obvious generic VLM or food model.<br><br>This supports a cloud/service boundary or runtime delivery but does not prove it.

#### Static analysis: reverse_engineering_limits

Vendor pricing can change, usage tokens are not equivalent across services, and public app artifacts do not reveal private enterprise contracts.<br><br>Do not infer a consumer app's provider price from public list prices.

#### Mobile model evaluation: candidate_models

For cloud fallback benchmark Gemini 3.1 Flash-Lite and OpenAI GPT-5.4 mini against Passio/LogMeal.<br><br>For local candidates benchmark Gemma 3n E2B, SmolVLM-256M/500M, Apple Foundation Models, and a dedicated food classifier.

#### Mobile model evaluation: mobile_runtime_support

Use a backend abstraction so mobile clients are independent of provider SDKs.<br><br>Keep local preprocessing in Core ML/LiteRT/ONNX Runtime and send only the minimal image/metadata needed for ambiguous cases.

#### Mobile model evaluation: device_requirements

Cloud models shift cost/latency/privacy to the backend; local models shift cost to app size, memory, thermal load, and device fragmentation.<br><br>Measure both paths on a fixed device matrix.

#### Mobile model evaluation: expected_food_inference_quality

Food-specific APIs should win on taxonomy/database grounding; general VLMs may win on novel recipes and multilingual descriptions.<br><br>Both need a benchmark with weighed meals and user correction.

#### Economics and recommendation: privacy_and_compliance

Require DPA/subprocessor list, image retention/deletion guarantees, no training on user photos by default, regional processing, encryption, auditability, and a user-visible cloud consent switch.<br><br>Food photos can reveal health and household data.

#### Economics and recommendation: recommendation

For MVP, run Passio and LogMeal trials in parallel with one generic VLM fallback, but keep the product contract centered on user confirmation and deterministic nutrition lookup.<br><br>Choose by measured corrected-meal accuracy, not raw model-demo quality or lowest token price.

### 7. Public Android artifact static analysis

*Locate publicly obtainable Android APKs for selected apps, inspect manifests, embedded SDKs, URLs, model assets, and strings with static tools, and clearly separate observations from inference.*

#### Market and product: product_scope

Static analysis examined public Android XAPK artifacts for Foodvisor, MyFitnessPal, Cal AI, and YAZIO to look for manifest permissions, embedded runtime/model assets, endpoints, and strings related to food-photo inference.

#### Market and product: supported_platforms

Android artifacts only; iOS binaries were not obtained or analyzed.

#### Market and product: pricing

Not applicable to the static pass; consumer subscription information remains in the app-specific results.

#### Market and product: popularity_signal

The four packages were selected as recognizable consumer nutrition/photo-calorie apps; selection is not a ranking or statistical market-share claim.

#### Recognition pipeline: recognition_behavior

Static code confirms camera/photo-analysis UI and network-capable app boundaries.<br><br>It does not reveal the complete runtime request/response behavior.

#### Recognition pipeline: local_inference_evidence

Foodvisor, MFP, Cal AI, and YAZIO XAPK trees had no obvious food model extensions (.tflite, .onnx, .mlmodel, .gguf, .mnn, .param, .pt, .pth, .lite, .ort).<br><br>MFP and YAZIO contained ML Kit barcode models, not food models.<br><br>This is compatible with server recognition or runtime model delivery.

#### Recognition pipeline: portion_and_nutrition_method

Not determinable from static inventory alone.

#### Static analysis: artifact_source

Foodvisor 7.2.0 SHA-256 889f0abfd1ae3d1b22d2a20663d96905a6ecf68028f60ddeb5a1c9f338cd1023; MyFitnessPal 26.27.0 SHA-256 08c3b51c7bdab6e055363c4bb4b4ade0fbff7121b30e55c58507bbdf979dbb9a; Cal AI 26.28.0 SHA-256 2fd7c96d0e1471eccdd3d7814b5601ba40cd626ebc8e25caee7665fc26d91534; YAZIO 26.29.0 SHA-256 4bea1dbef69ca909530d7318aa360670cebc73f2518d4a9341e1604b2d6eda09.<br><br>Temporary workspace: /tmp/supernutrition-apk-analysis/.

#### Static analysis: static_findings

Foodvisor manifest: io.foodvisor.foodvisor, version 7.2.0, min SDK 28, target 35; CAMERA/INTERNET; api.foodvisor.io; ML Kit barcode classes.<br><br>MFP: com.myfitnesspal.android, version 26.27.0, min SDK 32, target 36; CAMERA/INTERNET; bundled barcode TFLite models.<br><br>Cal AI: com.viraldevelopment.calai, version 26.28.0, min SDK 29, target 36; CAMERA/INTERNET; obfuscated assets; ML Kit barcode/remote-model infrastructure.<br><br>YAZIO: com.yazio.android, version 26.29.0, min SDK 32, target 35; CAMERA/INTERNET; AI food-tracking resources; bundled barcode TFLite models.<br><br>JADX was used on the base APKs; Foodvisor and YAZIO produced readable decompiled trees, while large MFP/Cal AI trees were partially decompiled before the long-running jobs were stopped after static evidence was collected.

#### Static analysis: reverse_engineering_limits

Only publicly downloadable artifacts were used.<br><br>Nothing was installed or executed; no paid account, DRM bypass, authentication bypass, network interception, or redistribution of decompiled code was attempted.<br><br>Publisher terms may restrict reverse engineering.<br><br>Static analysis cannot prove current production behavior.

#### Mobile model evaluation: candidate_models

No bundled food model was identified.<br><br>A new app should explicitly control model assets, versioning, checksums, rollback, and runtime download policy instead of relying on accidental binary discovery.

#### Mobile model evaluation: mobile_runtime_support

APK evidence shows ordinary Android camera/ML Kit plumbing, not a complete embedded food VLM.<br><br>For SuperNutrition evaluate LiteRT, ONNX Runtime Mobile, ExecuTorch, and Core ML separately.

#### Mobile model evaluation: device_requirements

No inference benchmark was run.<br><br>Package size and permission inventories are not substitutes for peak RAM, latency, battery, or thermal measurements.

#### Mobile model evaluation: expected_food_inference_quality

Not inferable from static artifacts; public product help pages and benchmark literature indicate common visible foods are easier than mixed meals and portions.

#### Economics and recommendation: unit_cost_estimate

No per-photo API cost can be inferred from an APK.<br><br>Network endpoints may be first-party or vendor-backed and may use private contracts.

#### Economics and recommendation: privacy_and_compliance

Permissions and endpoints show camera/network capability, not retention.<br><br>Verify privacy policies, App Store/Data Safety labels, and traffic contracts separately.

#### Economics and recommendation: recommendation

Use static analysis as corroboration only.<br><br>For a definitive current provider/runtime answer, obtain written vendor disclosures or perform an authorized, consented runtime test of an app account; do not treat decompiled strings as proof of active execution.

### 8. On-device models and mobile runtimes

*Research local vision-language and food-recognition models plus Core ML, ML Kit, TensorFlow Lite, ONNX Runtime, ExecuTorch, MediaPipe, MLC, and related iOS/Android deployment constraints.*

#### Market and product: product_scope

Local inference is realistic for food candidates, visible-item detection, image quality gates, barcode/OCR, and semantic labels; it is not validated as the sole source of grams, calories, macros, or hidden ingredients.

#### Market and product: supported_platforms

iOS and Android, with different hardware/runtime strengths.

#### Market and product: pricing

Local inference has no per-photo API charge, but model download/storage, battery, engineering, telemetry, update, and support costs remain.

#### Recognition pipeline: recognition_behavior

Recommended local pipeline: blur/lighting/food gate -> crop/segmentation -> food detector/classifier -> optional VLM for candidate explanation/JSON -> nutrition database lookup after confirmation.

#### Recognition pipeline: identified_services

Relevant local platform services: Apple Core ML/Foundation Models, Android Gemini Nano via ML Kit/AICore, Google LiteRT/LiteRT-LM, ONNX Runtime Mobile, ExecuTorch, MediaPipe, and MLC LLM.

#### Recognition pipeline: model_or_provider_confidence

Model/runtime capabilities are taken from official documentation/model cards; expected food quality is a recommendation requiring SuperNutrition benchmark validation.

#### Recognition pipeline: local_inference_evidence

Gemma 3n is explicitly optimized for phones/tablets and supports visual/text input; Apple Foundation Models supports image prompting on eligible Apple Intelligence devices; Gemini Nano supports on-device multimodal prompting on a limited Android device matrix.<br><br>Core ML, LiteRT, ExecuTorch, and ONNX Runtime provide deployment paths.

#### Recognition pipeline: portion_and_nutrition_method

No reviewed local VLM provides validated mass/nutrition calibration.<br><br>Use masks, reference objects, depth/AR, second views, or user serving input to reduce uncertainty, then calculate nutrients from a versioned database.

#### Static analysis: artifact_source

Official docs/model cards checked 2026-07-26: https://ai.google.dev/gemma/docs/gemma-3n, https://developer.apple.com/documentation/FoundationModels/analyzing-images-with-multimodal-prompting, https://developer.android.com/ai/gemini-nano, https://onnxruntime.ai/docs/tutorials/mobile/, https://docs.pytorch.org/executorch/stable/.

#### Static analysis: static_findings

The local-model shortlist is not derived from consumer APK assets.<br><br>It is a deployment recommendation based on official model/runtime documentation.

#### Static analysis: reverse_engineering_limits

Published memory and latency figures are not full app peak requirements; tokenizer, KV cache, preprocessing, runtime overhead, and device thermal state add cost.<br><br>Commercial model licenses must be reviewed separately.

#### Mobile model evaluation: candidate_models

Gemma 3n E2B: strongest open mobile VLM candidate to benchmark, but roughly multi-GB package class.<br><br>Apple Foundation Models: excellent iOS semantic fallback on supported hardware, but Apple controls the model and OS availability.<br><br>Gemini Nano: easiest Android prototype where supported, but device coverage/quotas/version drift matter.<br><br>SmolVLM-256M/500M: approachable Apache-2.0 open VLM baseline with ONNX components; 500M likely stronger, 256M easier.<br><br>Moondream 2 0.5B: spatial/detection specialist candidate after fine-tuning; current 3.x is too large for ordinary phones.<br><br>Qwen2.5-VL-3B: capable grounding/JSON candidate but the official 3B repository uses a research license and needs commercial licensing review.

#### Mobile model evaluation: mobile_runtime_support

Core ML: best iOS backend for a dedicated classifier/detector/segmenter and can use CPU/GPU/Neural Engine.<br><br>LiteRT/MediaPipe/LiteRT-LM: Google-native Android/iOS route; LiteRT-LM is preferred over maintenance-only MediaPipe LLM for new multimodal work.<br><br>ExecuTorch: strong if the team owns PyTorch export and backend testing.<br><br>ONNX Runtime Mobile: concrete cross-platform route for ONNX models, with Android NNAPI/XNNPACK and iOS Core ML/XNNPACK.<br><br>MLC LLM: useful text-LLM engine but not first choice for VLM production.

#### Economics and recommendation: privacy_and_compliance

Local-first processing reduces photo transfer and simplifies retention, but faces/interiors/metadata can still remain on device.<br><br>Provide deletion/export, avoid logging raw images, and disclose Apple/Google system-model terms and device eligibility.

#### Economics and recommendation: recommendation

Prototype three local tracks: iOS Core ML classifier/segmenter plus Foundation Models fallback; Android LiteRT classifier plus Gemini Nano on supported devices; cross-platform SmolVLM-256M via ONNX Runtime Mobile.<br><br>Benchmark Gemma 3n E2B only on a high-end-device tier.<br><br>Keep cloud fallback for ambiguous cases.

### 9. Food recognition accuracy and safety

*Research benchmark evidence, ambiguity classes, portion-size estimation limits, nutrition-database uncertainty, and safety requirements for a consumer nutrition app.*

#### Market and product: product_scope

Food recognition, segmentation, portion/mass estimation, and nutrition calculation are separate tasks.<br><br>A correct dish label does not reveal cooked mass, oil, sauce, recipe, or hidden ingredients.

#### Market and product: supported_platforms

Evaluation should use phone RGB images on iOS/Android device cohorts, with optional depth/AR as a separate condition.

#### Market and product: pricing

Accuracy work is a data/annotation/evaluation cost, not an inference API price.<br><br>A consented weighed-meal set is more valuable than a large unmeasured photo set for nutrition claims.

#### Recognition pipeline: recognition_behavior

Benchmark the full assistive flow: photo -> top-k candidates/masks -> user correction -> serving/recipe confirmation -> deterministic database nutrients.

#### Recognition pipeline: identified_services

Not applicable; the evidence concerns datasets and models rather than a vendor attribution.

#### Recognition pipeline: model_or_provider_confidence

Dataset facts and published benchmark values are high-confidence within their stated protocols; transfer to SuperNutrition's cuisines/devices is not guaranteed.

#### Recognition pipeline: local_inference_evidence

Food-101 supports dish classification but has no mass/calorie labels.<br><br>Nutrition5k has 5,006 plates with ingredient masses/nutrients but controlled California cafeteria capture and RGB-D/multi-view signals.<br><br>MyFoodRepo-273 has 24,119 real-world images, 39,325 polygons, and 273 categories but no calorie/weight ground truth.<br><br>FoodSAM reports segmentation results, not calorie accuracy.

#### Recognition pipeline: portion_and_nutrition_method

Nutrition5k reported 26.1% mean-relative MAE for direct 2D calorie prediction and 16.5% for a volume-assisted pipeline; the 9.5% portion-independent result still depends on reliable mass information.<br><br>These values should not be presented as phone-photo guarantees.

#### Static analysis: artifact_source

Primary sources: https://github.com/google-research-datasets/Nutrition5k, https://openaccess.thecvf.com/content/CVPR2021/html/Thames_Nutrition5k_Towards_Automatic_Nutritional_Understanding_of_Generic_Food_CVPR_2021_paper.html, https://www.frontiersin.org/journals/nutrition/articles/10.3389/fnut.2022.875143/full, https://data.vision.ee.ethz.ch/cvl/datasets_extra/food-101/, https://arxiv.org/abs/2308.05938.

#### Static analysis: static_findings

No consumer APK static evidence can substitute for a ground-truth benchmark.<br><br>Model assets and provider strings do not reveal accuracy or calibration.

#### Static analysis: reverse_engineering_limits

Random image splits leak plate/person/device context; all views/frames of a meal must stay in one split.<br><br>Dataset taxonomy, cuisine, capture rig, and labels limit cross-product comparison.

#### Mobile model evaluation: candidate_models

Use a detector/segmenter for visible items and a classifier/retriever for food candidates.<br><br>VLMs may add open-vocabulary candidates but must be calibrated/abstain.<br><br>No generic VLM should be allowed to invent nutrition values.

#### Mobile model evaluation: mobile_runtime_support

Use the same evaluation harness across Core ML, LiteRT, ONNX Runtime Mobile, and cloud providers so accuracy and device cost are compared fairly.

#### Mobile model evaluation: device_requirements

Measure latency, peak RAM, battery/thermal impact, model download size, offline behavior, and accelerator fallback on a representative device matrix.

#### Economics and recommendation: privacy_and_compliance

Food photos may expose faces, homes, locations, routines, and sensitive diet/health data.<br><br>Obtain consent, strip metadata, minimize retention, support deletion/export, avoid allergy-safe or medical claims, and never use image estimates alone for insulin dosing.

#### Economics and recommendation: recommendation

Build a consented, culturally diverse 300-500+ meal pilot with exact weights, recipes/brands, raw/cooked state, oil/sauce, device/cuisine/lighting slices, and person/restaurant/device/time-separated splits.<br><br>Compare local-only, cloud-only, and hybrid against manual logging, and surface uncertainty/user correction.

### 10. Recommended hybrid architecture for SuperNutrition

*Synthesize evidence into an implementation recommendation covering privacy, latency, fallback routing, model selection, observability, unit economics, and staged validation.*

#### Market and product: product_scope

Recommended product promise: photo recognition is an assisted logging shortcut that proposes visible foods and a portion range; the user confirms recipe/brand/serving before nutrition is saved.<br><br>It is not a medical measurement or allergy guarantee.

#### Market and product: supported_platforms

iOS and Android with a shared backend contract and platform-specific local inference adapters.

#### Market and product: pricing

MVP should budget a local prefilter for every photo plus cloud calls only for ambiguous/complex cases.<br><br>Candidate cloud list-price anchors: Passio $99/$599/$2,999 monthly tiers with token refills; LogMeal credit-based; Gemini 3.1 Flash-Lite $0.25/M input and $1.50/M output; OpenAI GPT-5.4 mini $0.75/M input and $4.50/M output.<br><br>Actual spend depends on image resolution, outputs, retries, retention, and vendor contract.

#### Recognition pipeline: recognition_behavior

1) local quality/food gate; 2) local detector/classifier/segmenter; 3) return candidates and confidence; 4) route ambiguous/recipe-level cases to Passio/LogMeal or a generic multimodal API; 5) resolve confirmed items against a versioned nutrition database; 6) display assumptions/ranges; 7) persist corrections as training/evaluation events without raw-photo retention by default.

#### Recognition pipeline: local_inference_evidence

Local first-pass is recommended for privacy, latency, offline feedback, and cost control; cloud is retained for long-tail dishes and richer reasoning.<br><br>Apple Foundation Models/Gemini Nano are optional platform fallbacks, not universal backends.

#### Recognition pipeline: portion_and_nutrition_method

Ask for reference object/depth/second image or serving input where possible.<br><br>Return ranges and an explicit assumption list for grams/calories; map the confirmed item to a canonical database record and preserve source/version/unit conversion.

#### Static analysis: artifact_source

Evidence sources are listed in the app, service, static-analysis, model, and accuracy result files.<br><br>Key public sources: MFP FAQ, Passio pricing/SDK docs, YAZIO privacy/help, Lose It Google case study, HealthifyMe policy, LogMeal pricing, Gemma 3n, Apple Foundation Models, ONNX Runtime Mobile.

#### Static analysis: static_findings

Four public XAPK inventories showed no obvious embedded food VLM model; this supports an explicit cloud/hybrid hypothesis but is not proof.<br><br>Build observability in SuperNutrition rather than relying on reverse engineering of other apps.

#### Static analysis: reverse_engineering_limits

Third-party app analysis has legal/terms constraints and cannot reveal private backend contracts.<br><br>For SuperNutrition, use authorized vendor tests, contract review, and own telemetry instead.

#### Mobile model evaluation: candidate_models

Phase 1 local: compact food detector/classifier/segmenter.<br><br>Phase 2: SmolVLM-256M/500M cross-platform baseline, Gemma 3n E2B high-end Android, Apple Foundation Models eligible iOS, Gemini Nano supported Android.<br><br>Keep Qwen2.5-VL-3B and current Moondream 3.x as research benchmarks, not initial production defaults.

#### Mobile model evaluation: mobile_runtime_support

iOS: Core ML/Vision plus optional Foundation Models.<br><br>Android: LiteRT/MediaPipe vision tasks plus optional Gemini Nano/LiteRT-LM.<br><br>Shared fallback: ONNX Runtime Mobile.<br><br>Use ExecuTorch if PyTorch export/backends are already strategic.

#### Mobile model evaluation: device_requirements

Define tiers: broad tier with small classifier; high-end tier with local VLM; cloud fallback for unsupported/low-memory devices.<br><br>Gate by measured RAM/latency/thermal/battery, not model parameter count alone.

#### Mobile model evaluation: expected_food_inference_quality

Success criterion is reduced time-to-log and acceptable corrected nutrition error, not perfect first-pass calories.<br><br>Enforce abstention for low confidence, mixed/hidden recipes, allergens, and portion ambiguity.

#### Economics and recommendation: privacy_and_compliance

Default to no raw-photo retention, strip EXIF, encrypt transport/storage, disclose cloud transfer before upload, provide regional routing/consent/deletion/export, sign DPAs, prohibit vendor training on user photos unless explicitly opted in, and avoid medical/allergy guarantees.

#### Economics and recommendation: recommendation

Build a two-week spike: (a) local quality gate + compact classifier; (b) Passio and LogMeal integrations behind one interface; (c) one Gemini/OpenAI fallback; (d) 300-500 weighed-meal benchmark; (e) UX for confirmation/ranges.<br><br>Select the production path only after comparing corrected-item accuracy, calorie/gram error, latency, privacy, and cost per accepted meal.

## Confidence and limits

The JSON result files retain an `uncertain` array for fields that are not directly disclosed or are derived from public list prices. The generated field summaries omit those values by design. In particular, a missing model file in an APK does not prove cloud-only inference; a vendor's public integration claim does not prove the current app build uses that exact path; and public API list prices do not reveal private app contracts.

Static analysis was limited to publicly obtainable Android artifacts. No application was installed or executed, no paid account was used, and no DRM/authentication bypass or network interception was attempted.
