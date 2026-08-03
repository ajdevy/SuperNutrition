# Exploratory Model Comparison: Local G-3 PoC and Desktop Vision Baselines

**Date:** 2026-08-03
**Artifact type:** BMAD technical research / evaluation record
**Status:** Exploratory evidence; not a release gate or production validation
**Repository:** SuperNutrition
**Worktree:** /Users/aj/.codex/worktrees/d550/SuperNutrition
**Branch:** codex/epic-3
**Repository HEAD at test time:** 666b69d937745958b1d328bd579fd4c0e7d53d1c
**Primary purpose:** Compare the current local Android research PoC with independent desktop vision-reasoning baselines on the same five food photographs.

## Executive summary

The comparison shows that the current local PoC is executable on a physical Android device, but its current model composition is not yet sufficient for reliable dish decomposition:

- The local PoC completed analysis successfully for all five photographs.
- The local PoC produced a dish ranking for all five photographs, but only one top-3 ranking was strongly aligned with the visible meal composition.
- The local PoC produced zero or very sparse semantic regions on several photos. Repeated carrot proposals and dish-prior rejections show that the current region path is constrained by a very small detector vocabulary and a hard top-1 dish prior.
- The independent gpt-5.6-sol baseline described more of the visibly present food components and produced more useful approximate regions for human comparison.
- The gpt-5.6-sol output is not a calibrated detector or segmenter result. Its percentages are approximate visual confidence estimates, and its boxes are approximate human-readable normalized regions. Its numbers must not be compared directly with local softmax or detector scores.
- Three Codex desktop subagent baselines completed with all five image inputs: GPT-5.5 at `high`, GPT-5.6 Luna at `xhigh`, and GPT-5.6 Terra at `xhigh`. Their outputs are approximate qualitative vision references, not calibrated detector results.
- This five-image run is useful as a failure-discovery and product-design probe, not as a model accuracy benchmark. There is no ground-truth annotation, no weighed serving data, and no statistically meaningful sample size.

The most important engineering conclusion is that the local system needs a separate, explicit distinction between:

1. visually detected component evidence;
2. dish-composition hypotheses;
3. class-agnostic masks or user grid prompts; and
4. unsupported or abstained hypotheses.

The current PoC already exposes some of this distinction in its debug output, but the inference path still lets a single top-1 dish prior determine which detector proposals can become accepted regions. That behavior is the main reason that plausible ingredients such as broccoli, tomato, onion, or poultry can disappear from the accepted result.

## 1. Scope and provenance

### 1.1 What was compared

The same five user-provided photographs were evaluated in two ways:

| System | Runtime | Role in this comparison |
|---|---|---|
| Local G-3 visible-component research PoC | Android physical device | Actual on-device inference and UI behavior |
| gpt-5.6-sol vision baseline | Read-only Codex subagent analysis | Independent qualitative visual reference |
| GPT-5.5 / GPT-5.6 Luna / GPT-5.6 Terra | Codex desktop subagents; all five JPEGs attached | Completed qualitative visual baselines at the requested reasoning settings |

The local path was the PoC used in the previous device session, not the production photo-analysis Activity. Its application id and Activity were:

~~~text
Application ID: com.supernutrition.g3benchmark
Activity:       com.supernutrition.g3benchmark/.PocActivity
Version name:   g3-spike
Purpose:        research-only G-3 visible-component PoC
~~~

The production application was not used as the comparison target. The PoC was launched from the gallery flow on a physical Android device and automatically analyzed each selected image without requiring a separate Analyze button.

### 1.2 Device evidence

| Field | Value |
|---|---|
| Device class | Physical Honor DNY-NX9 |
| Android | Android 16 / API 36 |
| ADB serial | Omitted from this artifact |
| Installed PoC package | com.supernutrition.g3benchmark |
| Installed PoC version | g3-spike |
| Installed package update time | 2026-08-02 17:13:39 |
| Local run result | 5 of 5 analyses reached success |
| Grid interaction | Cell 5 selected and re-run on photograph 1 |

The installed APK was the existing g3-spike build used for the preceding PoC session. This report records the source revision and the model assets defined by the harness, but it does not claim that a fresh APK rebuild or fresh asset download was performed as part of this comparison session.

### 1.3 Repository state

The worktree contained unrelated uncommitted photo-analysis UI and presenter changes, plus the user-sample fixture directory. Those changes were preserved and were not staged or modified for this report. The comparison report itself is the only intended repository change from this task.

The user-sample fixture directory is:

mobile/fixtures/photo-analysis/user-samples/

It contains renamed copies of the five input images and a fixture README. The original downloads remain outside the repository under /Users/aj/Downloads/.

### 1.4 Input image mapping

| Comparison id | Original input | Repository fixture |
|---|---|---|
| Photo 1 | /Users/aj/Downloads/2026-08-02 22.30.57.jpg | 2026-08-02_22-30-57-mixed-meal-beef-rice-salad-vegetables.jpg |
| Photo 2 | /Users/aj/Downloads/2026-08-02 22.30.52.jpg | 2026-08-02_22-30-52-green-salad-protein.jpg |
| Photo 3 | /Users/aj/Downloads/2026-08-02 22.30.47.jpg | 2026-08-02_22-30-47-fruit-cottage-cheese-roasted-vegetables.jpg |
| Photo 4 | /Users/aj/Downloads/2026-08-02 22.30.34.jpg | 2026-08-02_22-30-34-breakfast-bacon-eggs-fruit.jpg |
| Photo 5 | /Users/aj/Downloads/2026-08-02 22.30.42.jpg | 2026-08-02_22-30-42-chicken-salad-vegetables.jpg |

The filenames describe the analyst's working interpretation of the images; they are not labels or ground truth.

## 2. Local G-3 PoC

### 2.1 Pipeline

The PoC is a sequential research harness with separate dish, proposal, and mask paths:

~~~mermaid
flowchart LR
    A[Selected Bitmap] --> B[Food-101 dish classifier]
    A --> C[YOLO-World v2 COCO proposal detector]
    B --> D[Top-1 dish prior]
    C --> E[Component vocabulary filter]
    D --> F[Accepted ingredient proposals]
    E --> F
    F --> G[MobileSAM mask prompt]
    H[Optional 3x3 grid cell] --> G
    G --> I[PocAnalysis result and UI]
    B --> I
    C --> I
    F --> I
~~~

The important behavior is that the prior is derived from only the top-1 Food-101 dish candidate. A detector proposal can be accepted as an ingredient region only when its label belongs to the prior for that dish. Rejected detector proposals remain visible in debug suggestions but do not become accepted regions.

The grid prompt is a separate path. It can create a mask for a selected area but it is class-agnostic; it does not identify the ingredient inside the selected cell.

### 2.2 Model assets and formats

| Stage | Asset | Runtime format | Input | Output role |
|---|---|---|---|---|
| Dish recognition | swin-finetuned-food101-matmul-int8.onnx | ONNX, device int8 | RGB tensor [1,3,224,224] | Food-101 dish logits and top-5 candidates |
| Dish labels | food101-labels.txt | UTF-8 text, one label per line | N/A | Maps output indices to Food-101 names |
| Region proposals | yolov8s-worldv2-coco.onnx | ONNX | RGB tensor [1,3,640,640] | YOLO-World v2 COCO-80 proposals |
| Semantic masks | mobile-sam-encoder.onnx | ONNX | RGB tensor at 1024px | MobileSAM image embedding |
| Semantic masks | mobile-sam-decoder.onnx | ONNX | Prompt plus encoder embedding; mask input [1,1,256,256] | Mask logits converted to full-image ARGB mask |

The Android runtime is ONNX Runtime Android 1.28.0 on CPU, as specified by the G-3 harness.

Pinned asset provenance documented by the harness:

| Asset | Source or digest |
|---|---|
| Food-101 source model | https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX/resolve/main/onnx/model.onnx?download=true |
| Food-101 source model SHA-256 | f79aefaa7af43b7a11ae47267a8f733cb1152dec223d4d3db8fc95ab5dc44a83 |
| Device Food-101 int8 SHA-256 | 9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df |
| Food-101 labels SHA-256 | eb6b396455a281445a698d8af22b2142be9a6eb9401520ee702746545f1c8820 |
| MobileSAM encoder SHA-256 | 580f5fb648ea1062c0aabc26217aed56921985f03f0cbbd852bba81d760cc749 |
| MobileSAM decoder SHA-256 | 8976b90a87ba50a6a72217a5ff994f7d25ce16f2229fcc1ed259e1294c622ffe |
| YOLO-World v2 640px SHA-256 | ef272175d4db2216cde08082f1accf120bdf5ee03b5bc2ba5f51735cda1e9cbc |

These digests are the harness's pinned asset records. The exact installed APK's packaged assets were not independently extracted and re-hashed during this comparison run.

### 2.3 Preprocessing and postprocessing

#### Food-101 dish classifier

- Resize the selected image to a square 224 x 224 image.
- Convert to RGB.
- Normalize using ImageNet mean and standard deviation.
- Build ONNX tensor [1,3,224,224].
- Run the Swin Food-101 model.
- Apply softmax to logits.
- Retain top 5 internally.
- Display only top 3 in the PoC UI.

The displayed dish percentages are model softmax scores. They are not presented here as calibrated probabilities.

#### YOLO-World v2 proposal detector

- Resize the selected image to 640 x 640.
- Convert to RGB.
- Build ONNX tensor [1,3,640,640].
- Accept the model output in either [1,84,N] or [1,N,84] layout.
- Filter to the embedded COCO-80 vocabulary.
- Retain only component-like labels:
  - banana
  - apple
  - orange
  - broccoli
  - carrot
- Use confidence threshold 0.10.
- Apply per-label non-maximum suppression with IoU 0.70.
- Cap accepted raw region proposals at 16.
- Exclude composite labels such as pizza and sandwich from the component path.

This is not a complete ingredient detector. The five retained labels are the reason many visibly present foods cannot appear as accepted local regions.

#### Dish-conditioned ingredient prior

The prior is selected as:

~~~text
PocDishIngredientPriors.forDish(dishes.get(0).label)
~~~

Only the top-1 dish candidate is used. A raw proposal is accepted when its label is present in the prior for that dish. Otherwise it is retained in debug output as rejected by dish prior.

This creates a hard gate:

~~~text
visual proposal + allowed by top-1 dish prior -> accepted region
visual proposal + not allowed by top-1 dish prior -> debug-only
no visual proposal + present in prior -> dish-prior suggestion, not a detected region
~~~

#### MobileSAM

- Resize the image to 1024px for the encoder.
- Run the encoder with a 1024 x 1024 RGB input.
- Use the selected detector proposal or grid prompt as the decoder prompt.
- Use a positive center prompt and padding negative prompts for the proposal path.
- Decode mask input [1,1,256,256].
- Convert the output to a full-image ARGB mask.
- Cap the mask working side at 512px for UI rendering.

MobileSAM provides a class-agnostic mask. It does not decide whether a mask is broccoli, chicken, tomato, or another ingredient.

### 2.4 Local output contract

The local result is represented by PocAnalysis with these logical fields:

~~~json
{
  "status": "success | partial | fallback",
  "dishCandidates": [
    {
      "label": "food101_label",
      "confidence": 0.965
    }
  ],
  "regions": [
    {
      "label": "carrot",
      "confidence": 0.344,
      "left": 0.52,
      "top": 0.00,
      "right": 0.98,
      "bottom": 0.40,
      "mask": "Bitmap",
      "gridCellIndex": null
    }
  ],
  "ingredientSuggestions": [
    {
      "label": "broccoli",
      "detectorConfidence": 0.687,
      "fromDishPrior": true,
      "acceptedForRegions": false
    }
  ],
  "warnings": [
    "research-only output: dish labels and limited COCO proposals are not full ingredients; grams are never estimated"
  ]
}
~~~

In the actual Android UI:

- Top-3 dish candidates displays the top three classifier labels.
- Regions displays accepted detector or grid regions.
- Debug ingredient suggestions (full) displays the complete prior and detector-debug list.
- SUCCESS means the pipeline completed; it does not mean that the result is semantically correct.

## 3. Independent gpt-5.6-sol baseline

### 3.1 Runtime and role

The independent baseline was run as a read-only vision analysis using model identifier gpt-5.6-sol. It received the original five JPEG files as visual inputs and did not inspect or modify the repository.

This baseline is not equivalent to an exported on-device model stack:

- It is a vision-reasoning model, not the local Food-101 classifier.
- It did not run the local YOLO-World or MobileSAM ONNX assets.
- It did not produce device telemetry, ONNX tensors, detector logits, or model masks.
- It returned approximate human-interpretable boxes, not a formally defined segmentation output.
- It used approximate visual confidence values where useful; those values are not calibration evidence.
- It can use semantic knowledge about common dishes and ingredients, so its dish labels can be more expressive than the Food-101 label set.

The baseline is therefore used as an independent qualitative reference and candidate generator. It must not be treated as ground truth.

### 3.2 Exact input files

The baseline received these five files:

~~~text
/Users/aj/Downloads/2026-08-02 22.30.57.jpg
/Users/aj/Downloads/2026-08-02 22.30.52.jpg
/Users/aj/Downloads/2026-08-02 22.30.47.jpg
/Users/aj/Downloads/2026-08-02 22.30.34.jpg
/Users/aj/Downloads/2026-08-02 22.30.42.jpg
~~~

### 3.3 Exact comparison request

The following prompt was used:

~~~text
Analyze the five attached food photos as an independent vision-model baseline. Do not inspect or modify the repository. Return one structured result per photo in exactly this comparable format: photo filename; top-3 dish candidates with percentages (use approximate confidence only if justified); visible ingredient/component candidates with confidence; approximate normalized regions/bounding boxes [left,top,right,bottom] with labels; full debug ingredient hypothesis list, marking each as visual evidence or dish-prior/common-ingredient inference; uncertainty and abstentions. This is for comparison with the local G-3 PoC, which outputs Food-101 dish candidates, limited detector proposals, dish priors, and grid/mask regions. Explicitly distinguish visually detected objects from dish-inferred ingredients. Do not invent exact detections when the image is ambiguous.
~~~

### 3.4 Baseline output format

The requested comparison record can be represented as:

~~~json
{
  "photo_filename": "original filename",
  "top3_dish_candidates": [
    {
      "label": "human-readable dish hypothesis",
      "confidence_approx": 0.72
    }
  ],
  "visible_components": [
    {
      "label": "visually supported component",
      "confidence_approx": 0.96
    }
  ],
  "regions": [
    {
      "label": "human-readable component or cluster",
      "bbox_norm": [0.09, 0.07, 0.56, 0.64],
      "confidence_approx": 0.90
    }
  ],
  "ingredient_hypotheses": [
    {
      "label": "ingredient",
      "source": "visual_evidence | dish_prior_or_common_inference",
      "confidence_approx": 0.70
    }
  ],
  "uncertainty_and_abstentions": [
    "ambiguous label or unsupported preparation detail"
  ]
}
~~~

The normalized box convention is [left, top, right, bottom], with each coordinate in the range 0.0 to 1.0, measured from the original image. These boxes are approximate visual regions and are not pixel-accurate masks.

### 3.5 Codex desktop subagent baselines

The same five JPEGs and the same read-only vision prompt were sent as image
inputs to three Codex desktop subagents in parallel. No repository access or
mutation was requested or used.

| Model | Reasoning variant | Image inputs | Status | Output boundary |
|---|---|---:|---|---|
| GPT-5.5 | `high` | 5/5 | Completed | Approximate qualitative vision baseline |
| GPT-5.6 Luna | `xhigh` / extra high | 5/5 | Completed | Approximate qualitative vision baseline |
| GPT-5.6 Terra | `xhigh` / extra high | 5/5 | Completed | Approximate qualitative vision baseline |

The exact prompt was:

~~~text
Analyze these five attached food photos as a read-only independent vision baseline. Do not inspect or modify any repository. For each photo return:
- filename;
- top-3 dish candidates with clearly approximate confidence percentages;
- visible ingredients/components with approximate confidence;
- approximate normalized regions [left, top, right, bottom] with labels;
- full ingredient hypothesis list, marking each item as visual evidence or dish-prior/common-ingredient inference;
- uncertainty and abstentions.

Explicitly distinguish visually detected components from dish inference. This is being compared with the local G-3 PoC using Food-101 dish classification, limited YOLO-World proposals, a dish prior, and class-agnostic MobileSAM/grid masks, plus the existing gpt-5.6-sol qualitative baseline. Do not invent exact detections or calibrated probabilities. Return concise Markdown tables.
~~~

All three agents returned a non-empty answer for all five photos. Their
percentages are rough recognition strength, not calibration; their normalized
regions are approximate visual clusters, not pixel masks.

## 4. Side-by-side result summary

The table below keeps the two systems' output types separate. Local percentages are Food-101 softmax or detector scores. Baseline percentages are approximate visual confidence estimates.

| Photo | Local top-3 dish candidates | Local accepted regions | Independent baseline dish interpretation | Baseline approximate regions |
|---|---|---:|---|---:|
| 1 | greek_salad 96.5%; caesar_salad 1.1%; bibimbap 1.0% | 0 | Mixed buffet plate with meat, rice, salad, roasted vegetables 72%; roast meat with rice and vegetables 18%; composed salad with meat and roasted vegetables 10% | 5 cluster boxes |
| 2 | greek_salad 92.5%; beet_salad 5.4%; caesar_salad 0.8% | 0 | Mixed green salad with cheese or tofu 50%; Greek-style salad 32%; Mediterranean mixed salad 18% | 5 component/cluster boxes |
| 3 | beet_salad 20.1%; cheese_plate 19.9%; gnocchi 19.1% | 1 | Fruit-and-cottage-cheese plate with roasted vegetables 64%; mixed fruit and cottage-cheese breakfast plate 24%; fruit salad with cottage cheese 12% | 5 component/cluster boxes |
| 4 | french_toast 39.7%; pancakes 25.4%; omelette 18.2% | 3 | Bacon, scrambled eggs, and fruit breakfast plate 91%; bacon/scrambled eggs 7%; fruit-and-egg breakfast 2% | 4 component boxes |
| 5 | pork_chop 50.1%; grilled_salmon 41.2%; caesar_salad 2.1% | 3 | Grilled/roasted chicken salad 64%; roasted poultry with salad/vegetables 24%; turkey-breast salad 12% | 5 component boxes |

The same comparison in one model-level table is:

| System / model | Input and execution mode | Photo 1 | Photo 2 | Photo 3 | Photo 4 | Photo 5 | Comparable conclusion |
|---|---|---|---|---|---|---|---|
| Local G-3 PoC | Physical Android; Food-101 + YOLO-World v2 + MobileSAM; automatic gallery analysis | greek_salad 96.5%; 0 regions | greek_salad 92.5%; 0 regions | beet_salad 20.1%; 1 carrot region | french_toast 39.7%; 3 carrot regions | pork_chop 50.1%; 3 carrot regions | Executed 5/5; limited vocabulary and hard top-1 prior caused sparse or misleading component output |
| gpt-5.6-sol | Vision subagent; same five JPEGs; qualitative baseline prompt | Mixed buffet plate 72%; 5 approximate boxes | Green salad with cheese/tofu 50%; 5 approximate boxes | Fruit/cottage-cheese plate 64%; 5 approximate boxes | Bacon, scrambled eggs, fruit 91%; 4 approximate boxes | Grilled/roasted chicken salad 64%; 5 approximate boxes | Semantically richer qualitative reference; approximate confidence and boxes are not calibrated runtime evidence |
| gpt-5.5 | Codex desktop subagent; `high`; five image inputs | Mixed buffet 45%; rice, meat, salad, vegetables; 5 boxes | Green salad with protein/cheese cubes 35%; greens, onion, cucumber; 4 boxes | Fruit + cottage cheese 45%; fruit, curds, roasted cubes; 5 boxes | Bacon, eggs, berries 70%; 5 visible components; 5 boxes | Chicken salad 50%; chicken, lettuce, tomato, carrot, pepper; 6 boxes | Completed 5/5; useful visual component baseline; approximate only |
| gpt-5.6-luna | Codex desktop subagent; `xhigh`; five image inputs | Mixed buffet 55%; greens, meat, rice, broccoli, sprouts; 11 visual hypotheses | Green salad with cheese/tofu 45%; greens, onion, cucumber, cubes; 5 boxes | Cottage cheese + fruit 55%; fruit, curds, roasted chunks; 7 visual hypotheses | Bacon, eggs, berries 90%; 4 boxes | Protein plate 45%; protein, lettuce, carrot, tomatoes, pepper; 7 visual hypotheses | Completed 5/5; broadest ingredient hypothesis lists; approximate only |
| gpt-5.6-terra | Codex desktop subagent; `xhigh`; five image inputs | Mixed buffet 75%; greens, meat, rice, sprouts, broccoli; 7 boxes | Green salad with tofu 75%; greens, onion, cucumber, pale cubes; 5 boxes | Cottage cheese + fruit 80%; fruit, curds, roasted cubes; 5 boxes | Bacon, eggs, berries 90%; 4 boxes | Chicken vegetable salad 85%; chicken, lettuce, carrots, tomatoes, pepper; 6 boxes | Completed 5/5; strongest dish/component alignment in this qualitative set |

The local region count is not a measure of recall. A region may be absent because the detector did not produce one of the five retained labels, because the hard dish prior rejected it, or because the proposal path failed. Conversely, a region can exist without a correct semantic identity.

The three desktop rows are scored only as qualitative visual references. They
must not be compared numerically with local softmax or detector confidence.

## 4.1 Separate desktop baseline results

The following tables preserve the required separation between dish recognition,
pixel-supported component hypotheses, approximate regions, and dish-prior or
common-ingredient inference. `Visual` means the agent judged that the pixels
support the label; it does not mean calibrated certainty or ground truth.

### GPT-5.5 — `high` — completed 5/5

| Photo | Dish top-3, approximate | Visual components | Approximate regions | Inference / abstention |
|---|---|---|---|---|
| 1 | Mixed buffet plate 45%; rice pilaf/biryani-style 25%; roasted vegetables + salad 15% | Rice, shredded meat, lettuce, corn, tomato/red pepper, cucumber, Brussels sprouts, broccoli/cauliflower, red onion, nuts, flatbread edge | 5 clusters: rice/meat; rice/vegetables; chopped salad; sprouts/broccoli/cucumber; flatbread | Meat species, sauce, dressing, seasoning, and plain-vs-pilaf rice unresolved; lamb/chicken/beef is inference only |
| 2 | Grilled chicken green salad 35%; tofu/feta greens 30%; garden salad with protein 20% | Mixed leafy greens, pale protein/cheese cubes, red onion, cucumber, darker greens | 4 clusters: greens; pale cubes; cucumber/onion; leaves/onion | Chicken vs tofu vs cheese and dressing not visually confirmed |
| 3 | Fruit plate + cottage cheese 45%; fruit + cottage cheese + roasted side 30%; mixed fruit platter 15% | Watermelon, berries, pineapple, cottage cheese/curds, roasted orange/brown cubes | 5 clusters: watermelon; berries; pineapple; cottage cheese; roasted cubes | Cube identity and exact dairy type unresolved; honey/syrup and seasoning are inference only |
| 4 | Bacon, eggs, berries 70%; American breakfast 20%; egg/bacon + fruit 10% | Bacon, scrambled eggs, blueberries, strawberries, egg crumbs | 5 clusters: bacon; eggs; blueberries; strawberries; crumbs/berries | Butter/oil, milk/cream, salt/pepper and curing details not visible |
| 5 | Chicken salad plate 50%; grilled/roasted chicken + salad 30%; protein + vegetables 15% | Chicken, lettuce, cherry tomatoes, carrots, red pepper, green vegetables, possible yellow chunk | 6 clusters: chicken pieces; chicken breast; greens; tomatoes; carrots; pepper/carrot | Chicken preparation, dressing, cheese/egg/pineapple and exact yellow item unresolved |

### GPT-5.6 Luna — `xhigh` / extra high — completed 5/5

| Photo | Dish top-3, approximate | Visual components | Approximate regions | Inference / abstention |
|---|---|---|---|---|
| 1 | Mixed buffet/cafeteria plate 55%; roast meat + rice + vegetables 25%; composed meat/grain/salad 10% | Lettuce/greens, shredded meat, rice/grain, broccoli, Brussels sprouts, tomato, cucumber, corn/yellow pepper, red onion, cauliflower-like pieces, tan legumes/nuts | 11 visual clusters across greens, meat, grain, vegetables, onion, garnish | Exact meat/grain, white mound, garnish, cuisine, dressing, and preparation unresolved |
| 2 | Mixed green salad with cheese/tofu 45%; Greek-style salad 25%; vegetarian chopped salad 20% | Leafy greens, red onion, cucumber, white/tan cubes, dark leafy/olive-like pieces, pale garnish | 5 clusters across greens, onion, cubes, dark pieces, garnish | Cheese/tofu/chicken/crouton identity and dressing not confirmed |
| 3 | Cottage cheese/quark + fruit + roasted pieces 55%; fruit + cottage cheese 25%; fruit + cooked side 10% | Strawberries, raspberries, blackberries, watermelon, pineapple, cottage cheese, mixed cooked chunks | 7 visual clusters across fruit, curds, and central cooked mixture | Carrot/sweet potato, potato, squash/pepper/cooked fruit, sweetener, and dairy composition are inference only |
| 4 | Bacon, scrambled eggs, berries 90%; American breakfast 7%; egg breakfast + fruit 2% | Bacon, scrambled egg, blueberries, strawberries | 4 component regions | Bacon type and egg additives not visible; no toast/syrup claim |
| 5 | Seasoned protein plate + salad 45%; breaded fish/chicken + vegetables 30%; grilled white fish 15% | Cooked protein, possible fillet/skin, breaded crust, lettuce, tomatoes, carrots, red pepper, yellow cob-like piece | 7 visual clusters across protein and sides | Exact protein, whether pieces match, dressing, marinade, and yellow item unresolved |

### GPT-5.6 Terra — `xhigh` / extra high — completed 5/5

| Photo | Dish top-3, approximate | Visual components | Approximate regions | Inference / abstention |
|---|---|---|---|---|
| 1 | Mixed buffet/composed plate 75%; roast meat + rice + vegetables 55%; Mediterranean/Middle-Eastern mixed plate 40% | Chopped leafy salad with yellow pepper/tomato, shredded meat, rice/grain, Brussels sprouts, broccoli/cauliflower, cucumber, red onion | 7 clusters covering salad, meat, grain, sprouts, broccoli, cucumber, onion | Meat species, grain type, sauces, dressing, and cooking method unresolved; background flatbread excluded |
| 2 | Mixed green salad with tofu 75%; tofu-avocado salad 55%; vegan composed salad 45% | Leafy greens, tofu-like pale cubes, red onion, avocado-like wedges, dark cooked pieces | 5 clusters across greens, cubes, onion, wedges, dark pieces | Cubes could be cheese; dark pieces and wedges are uncertain; no dressing claim |
| 3 | Cottage cheese + mixed fruit 80%; fruit-and-cottage-cheese breakfast 65%; fruit + roasted side 45% | Cottage cheese/curd, watermelon, pineapple, raspberries/blackberries, roasted brown/orange cubes | 5 clusters across dairy, fruit, and cooked cubes | Cube mixture, sweetening, dairy type, and portion composition unresolved |
| 4 | Bacon, scrambled eggs, berries 90%; bacon-and-eggs 75%; American breakfast + fruit 65% | Bacon strips, scrambled eggs, blueberries, strawberries | 4 component regions | Bacon type, egg additives, portions, and sweetener not visible |
| 5 | Chicken + vegetable salad 85%; grilled/roast chicken salad 75%; chicken + vegetables 60% | Cooked chicken, lettuce, carrots, cherry tomatoes, red/orange pepper, yellow piece | 6 component regions across protein and sides | Exact protein preparation, yellow item, dressing, and marinade unresolved |

### 4.1.1 Normalized region appendix

The following are the approximate normalized `[left, top, right, bottom]`
regions returned by the desktop agents. They are human-readable clusters, may
overlap, and are not masks or localization ground truth.

| Model | Photo | Approximate visual regions |
|---|---|---|
| GPT-5.5 / `high` | 1 | rice/meat `[0.05,0.18,0.42,0.65]`; rice/veg `[0.32,0.12,0.68,0.42]`; salad `[0.24,0.42,0.69,0.92]`; sprouts/broccoli `[0.64,0.25,0.91,0.58]`; flatbread `[0.00,0.12,0.14,0.30]` |
| GPT-5.5 / `high` | 2 | greens `[0.03,0.05,0.55,0.90]`; pale cubes `[0.40,0.28,0.70,0.64]`; cucumber/onion `[0.57,0.09,0.86,0.73]`; leaves/onion `[0.28,0.05,0.70,0.30]` |
| GPT-5.5 / `high` | 3 | watermelon `[0.16,0.28,0.45,0.72]`; berries `[0.12,0.14,0.33,0.34]`; pineapple `[0.35,0.62,0.76,0.88]`; curds `[0.55,0.48,0.86,0.76]`; roasted cubes `[0.45,0.10,0.75,0.45]` |
| GPT-5.5 / `high` | 4 | bacon `[0.08,0.20,0.37,0.72]`; eggs `[0.33,0.34,0.56,0.66]`; blueberries `[0.58,0.30,0.82,0.64]`; strawberries `[0.61,0.14,0.88,0.50]`; crumbs `[0.47,0.62,0.68,0.78]` |
| GPT-5.5 / `high` | 5 | chicken `[0.08,0.08,0.43,0.35]`; breast `[0.36,0.38,0.63,0.70]`; greens `[0.05,0.38,0.45,0.88]`; tomatoes `[0.62,0.46,0.90,0.76]`; carrots `[0.62,0.03,0.93,0.35]`; pepper `[0.62,0.35,0.95,0.61]` |
| GPT-5.6 Luna / `xhigh` | 1 | greens `[0.20,0.02,0.85,0.96]`; meat `[0.10,0.17,0.47,0.70]`; rice `[0.25,0.08,0.65,0.55]`; broccoli `[0.58,0.13,0.77,0.44]`; sprouts `[0.66,0.25,0.90,0.64]`; tomato `[0.30,0.32,0.86,0.96]`; cucumber `[0.68,0.24,0.86,0.90]`; onion `[0.40,0.08,0.68,0.55]` |
| GPT-5.6 Luna / `xhigh` | 2 | greens `[0.06,0.08,0.87,0.88]`; onion `[0.34,0.22,0.91,0.76]`; cucumber `[0.58,0.43,0.85,0.69]`; cubes `[0.45,0.27,0.82,0.68]`; dark pieces `[0.47,0.48,0.83,0.82]`; garnish `[0.68,0.13,0.94,0.40]` |
| GPT-5.6 Luna / `xhigh` | 3 | strawberries `[0.08,0.06,0.40,0.63]`; raspberries `[0.18,0.05,0.38,0.25]`; blackberries `[0.10,0.24,0.32,0.51]`; watermelon `[0.20,0.27,0.50,0.70]`; pineapple `[0.30,0.55,0.72,0.88]`; curds `[0.53,0.43,0.90,0.81]`; cooked chunks `[0.37,0.07,0.83,0.56]` |
| GPT-5.6 Luna / `xhigh` | 4 | bacon `[0.09,0.10,0.42,0.79]`; eggs `[0.29,0.27,0.62,0.73]`; blueberries `[0.50,0.16,0.86,0.70]`; strawberries `[0.53,0.05,0.94,0.62]` |
| GPT-5.6 Luna / `xhigh` | 5 | protein `[0.10,0.14,0.70,0.81]`; fillet `[0.35,0.47,0.63,0.82]`; crust `[0.08,0.14,0.70,0.65]`; lettuce `[0.04,0.34,0.70,0.96]`; tomatoes `[0.55,0.55,0.91,0.90]`; carrot `[0.57,0.02,0.97,0.42]`; pepper `[0.76,0.18,0.99,0.67]` |
| GPT-5.6 Terra / `xhigh` | 1 | salad `[0.22,0.39,0.84,0.95]`; meat `[0.10,0.16,0.51,0.63]`; rice `[0.29,0.08,0.64,0.43]`; sprouts `[0.61,0.29,0.90,0.57]`; broccoli `[0.39,0.03,0.67,0.30]`; cucumber `[0.69,0.26,0.82,0.42]`; onion `[0.40,0.13,0.61,0.42]` |
| GPT-5.6 Terra / `xhigh` | 2 | greens `[0.07,0.05,0.88,0.95]`; cubes `[0.39,0.34,0.74,0.64]`; onion `[0.31,0.10,0.93,0.78]`; avocado-like wedges `[0.60,0.52,0.82,0.71]`; dark pieces `[0.43,0.31,0.86,0.80]` |
| GPT-5.6 Terra / `xhigh` | 3 | curds `[0.57,0.54,0.84,0.83]`; watermelon `[0.13,0.27,0.51,0.80]`; pineapple `[0.28,0.49,0.90,0.89]`; berries `[0.09,0.06,0.37,0.44]`; cooked cubes `[0.43,0.12,0.75,0.56]` |
| GPT-5.6 Terra / `xhigh` | 4 | bacon `[0.08,0.17,0.38,0.69]`; eggs `[0.31,0.33,0.61,0.78]`; blueberries `[0.50,0.20,0.85,0.80]`; strawberries `[0.58,0.12,0.94,0.64]` |
| GPT-5.6 Terra / `xhigh` | 5 | chicken `[0.18,0.04,0.76,0.71]`; lettuce `[0.06,0.13,0.62,0.96]`; carrots `[0.55,0.03,0.96,0.36]`; tomatoes `[0.65,0.43,0.97,0.74]`; pepper `[0.66,0.27,0.99,0.62]`; yellow piece `[0.10,0.32,0.26,0.51]` |

### 4.2 Consolidated model comparison

| System | Status / reasoning | Dish recognition | Ingredient/component detection | Approximate regions | Overall readout |
|---|---|---|---|---|---|
| Local G-3 PoC | Completed 5/5 on physical Android; on-device reasoning not user-selectable | Often overconfident Food-101 dish labels; Photo 1/2 Greek salad, Photo 5 pork chop | Sparse 0–3 accepted regions/photo; limited five-label vocabulary; repeated carrot outputs | Device detector/grid regions only; 7 accepted across five photos | Operationally real, semantically weak |
| gpt-5.6-sol | Completed 5/5; qualitative variant not recorded | Mixed/composite dish descriptions generally closer to visible plate | Broad semantic component lists, with visual vs inference distinction | Approximate cluster boxes for all five | Strong qualitative audit, not calibrated runtime evidence |
| GPT-5.5 | Completed 5/5; `high` | Reasonable composite/breakfast descriptions; less specific on ambiguous plates | Broad visible lists; conservative uncertainty | 4–6 approximate regions/photo | Useful baseline; lower specificity than Terra on dish composition |
| GPT-5.6 Luna | Completed 5/5; `xhigh` | Broad and cautious; strong breakfast recognition | Broadest hypotheses, including explicit alternatives | 4–11 approximate regions/photo | Good uncertainty inventory; some labels remain ambiguous |
| GPT-5.6 Terra | Completed 5/5; `xhigh` | Strongest qualitative dish alignment on this five-photo set | Consistent visible component lists with explicit abstentions | 4–7 approximate regions/photo | Best overall qualitative description in this run; still not ground truth |

### 4.3 Which model best described visible composition?

On this tiny qualitative set, GPT-5.6 Terra is the strongest overall reference:
it identified the breakfast and poultry/vegetable plates cleanly, described the
composite plates with more appropriate dish-level uncertainty, and kept visual
components separate from inferred preparation details. GPT-5.6 Luna produced
the broadest alternative hypotheses and was useful for uncertainty discovery.
GPT-5.5 was also materially richer than the local accepted output, but was less
specific on ambiguous composite plates.

This is not a model-quality score. The three desktop agents are reasoning
models, not calibrated detector/segmenter implementations, and they were not
evaluated against human-labeled boxes or ingredient ground truth. Their main
value here is to expose where the local vocabulary and hard prior are missing
visible composition.

## 5. Detailed results

### 5.1 Photo 1 — mixed meat, rice, salad, and roasted vegetables

Input: 2026-08-02 22.30.57.jpg
Fixture: 2026-08-02_22-30-57-mixed-meal-beef-rice-salad-vegetables.jpg

#### Local PoC

Top-3 dish candidates:

~~~text
1. greek_salad — 96.5%
2. caesar_salad — 1.1%
3. bibimbap — 1.0%
~~~

Accepted regions: 0.

Debug ingredient suggestions:

| Label | Debug evidence | Local outcome |
|---|---:|---|
| cucumber | Dish prior | Not detected |
| tomato | Dish prior | Not detected |
| red_onion | Dish prior | Not detected |
| bell_pepper | Dish prior | Not detected |
| feta_cheese | Dish prior | Not detected |
| olives | Dish prior | Not detected |
| olive_oil | Dish prior | Not detected |
| broccoli | Detector confidence 68.7% | Rejected by dish prior |
| carrot | Detector confidence 14.9% | Rejected by dish prior |

Warnings:

~~~text
dish-conditioned prior applied; detector labels outside prior are debug-only
no valid COCO food region proposals
~~~

#### Independent baseline

Dish candidates:

~~~text
1. mixed buffet plate with meat, rice, salad, and roasted vegetables — 72%
2. roast meat with rice and vegetables — 18%
3. composed salad with meat and roasted vegetables — 10%
~~~

Visually supported components:

| Component | Approximate confidence |
|---|---:|
| Cooked white rice | 99% |
| Mixed leafy salad | 99% |
| Tomato | 96% |
| Broccoli | 96% |
| Brussels sprouts | 95% |
| Red onion | 94% |
| Cauliflower | 94% |
| Cooked shredded or sliced meat | 94% |
| Walnut | 86% |
| Cucumber | 85% |
| Green bell pepper | 82% |
| Orange root, likely carrot | 78% |
| Diced yellow vegetable, possibly yellow pepper or corn | 70% |
| Pale creamy or cheese-like component | 63% |

Approximate normalized regions:

| Region | Box [left, top, right, bottom] |
|---|---|
| Rice-and-meat cluster | [0.09, 0.07, 0.56, 0.64] |
| Fresh mixed salad | [0.22, 0.37, 0.87, 0.97] |
| Brussels-sprout cluster | [0.58, 0.27, 0.87, 0.64] |
| Broccoli, cauliflower, and onion roast-vegetable cluster | [0.37, 0.01, 0.72, 0.42] |
| Pale component with walnut | [0.31, 0.06, 0.47, 0.24] |

Full baseline hypothesis list:

- Visual evidence: rice.
- Visual evidence: cooked meat.
- Visual evidence: lettuce and mixed leafy salad.
- Visual evidence: tomato.
- Visual evidence: broccoli.
- Visual evidence: Brussels sprouts.
- Visual evidence: cauliflower.
- Visual evidence: red onion.
- Visual evidence: cucumber.
- Visual evidence: green pepper.
- Visual evidence: orange root, likely carrot.
- Visual evidence: yellow pieces, exact class uncertain.
- Visual evidence: walnut.
- Visual evidence: pale creamy or cheese-like component.
- Dish/common-ingredient inference only: cooking oil or butter.
- Dish/common-ingredient inference only: dressing.
- Dish/common-ingredient inference only: salt and pepper.

Abstentions:

- Meat species was not identified; lamb, pork, beef, and poultry remained possible.
- The pale walnut-bearing component was not assigned a precise class.
- Yellow corn versus yellow pepper was unresolved.

### 5.2 Photo 2 — green salad with protein-like cubes

Input: 2026-08-02 22.30.52.jpg
Fixture: 2026-08-02_22-30-52-green-salad-protein.jpg

#### Local PoC

Top-3 dish candidates:

~~~text
1. greek_salad — 92.5%
2. beet_salad — 5.4%
3. caesar_salad — 0.8%
~~~

Accepted regions: 0.

Debug ingredient suggestions:

| Label | Debug evidence | Local outcome |
|---|---:|---|
| cucumber | Dish prior | Not detected |
| tomato | Dish prior | Not detected |
| red_onion | Dish prior | Not detected |
| bell_pepper | Dish prior | Not detected |
| feta_cheese | Dish prior | Not detected |
| olives | Dish prior | Not detected |
| olive_oil | Dish prior | Not detected |
| broccoli | Detector confidence 24.5% | Rejected by dish prior |

Warnings:

~~~text
dish-conditioned prior applied; detector labels outside prior are debug-only
no valid COCO food region proposals
~~~

#### Independent baseline

Dish candidates:

~~~text
1. mixed green salad with cheese or tofu — 50%
2. Greek-style salad — 32%
3. Mediterranean mixed salad — 18%
~~~

Visually supported components:

| Component | Approximate confidence |
|---|---:|
| Mixed leafy greens | 99% |
| Red onion | 96% |
| Cucumber | 88% |
| White cheese or tofu cubes | 87% |
| Dark olive-like component | 68% |
| Pale or tan chunks | 60% |

Approximate normalized regions:

| Region | Box [left, top, right, bottom] |
|---|---|
| Mixed leafy greens | [0.03, 0.00, 0.86, 0.94] |
| White cubes and pale components | [0.31, 0.27, 0.75, 0.72] |
| Red onion arcs | [0.31, 0.15, 0.97, 0.73] |
| Cucumber wedge | [0.67, 0.49, 0.92, 0.66] |
| Dark olive-like component | [0.60, 0.40, 0.86, 0.70] |

Full baseline hypothesis list:

- Visual evidence: mixed leafy greens.
- Visual evidence: red onion.
- Visual evidence: cucumber.
- Visual evidence: white cubes, likely cheese or tofu.
- Visual evidence: dark olive-like pieces.
- Visual evidence: pale or tan chunks.
- Dish/common-ingredient inference only: feta, with tofu or another firm cheese still possible.
- Dish/common-ingredient inference only: black olives are plausible.
- Dish/common-ingredient inference only: olive oil, vinaigrette, or herbs are plausible but unconfirmed.

Abstentions:

- The image is dim and low resolution.
- Pale chunks could be artichoke, mushroom, cheese, or another protein.
- Tomato was not visually established.

### 5.3 Photo 3 — fruit, cottage cheese, and roasted vegetables

Input: 2026-08-02 22.30.47.jpg
Fixture: 2026-08-02_22-30-47-fruit-cottage-cheese-roasted-vegetables.jpg

#### Local PoC

Top-3 dish candidates:

~~~text
1. beet_salad — 20.1%
2. cheese_plate — 19.9%
3. gnocchi — 19.1%
~~~

Accepted regions: 1.

Accepted region:

~~~text
carrot — 15.8%
~~~

Debug detector evidence:

~~~text
carrot — 15.8%
~~~

No dish-specific prior was applied to the accepted detector result.

#### Independent baseline

Dish candidates:

~~~text
1. fruit-and-cottage-cheese plate with roasted vegetables — 64%
2. mixed fruit and cottage-cheese breakfast plate — 24%
3. fruit salad with cottage cheese — 12%
~~~

Visually supported components:

| Component | Approximate confidence |
|---|---:|
| Watermelon | 99% |
| Cottage or curd cheese | 97% |
| Pineapple | 95% |
| Raspberries | 95% |
| Blackberries | 94% |
| Roasted mixed vegetable cubes | 89% |
| Orange cubes, likely sweet potato or carrot | 76% |
| Pale yellow or green cubes, possibly squash, potato, or zucchini | 66% |
| Dark-skinned component, possibly eggplant | 55% |

Approximate normalized regions:

| Region | Box [left, top, right, bottom] |
|---|---|
| Berry cluster | [0.06, 0.04, 0.31, 0.45] |
| Watermelon | [0.16, 0.18, 0.49, 0.76] |
| Roasted mixed vegetables | [0.38, 0.05, 0.75, 0.58] |
| Pineapple | [0.31, 0.39, 0.83, 0.92] |
| Cottage cheese | [0.51, 0.41, 0.91, 0.79] |

Full baseline hypothesis list:

- Visual evidence: watermelon.
- Visual evidence: cottage or curd cheese.
- Visual evidence: pineapple.
- Visual evidence: raspberries.
- Visual evidence: blackberries.
- Visual evidence: roasted mixed vegetables.
- Visual evidence: orange cubes, likely sweet potato or carrot.
- Visual evidence: pale yellow or green cubes, exact class uncertain.
- Visual evidence: possible dark-skinned eggplant-like piece.
- Dish/common-ingredient inference only: cottage cheese favored over ricotta because of the coarse curd appearance.
- Dish/common-ingredient inference only: oil or seasoning may have been used on the roasted vegetables, but it was not visually established.

Abstentions:

- The exact roasted vegetable classes remained uncertain.
- The combined fruit, dairy, and cooked-vegetable plate did not map cleanly to one standard dish class.

### 5.4 Photo 4 — bacon, scrambled eggs, and fruit breakfast

Input: 2026-08-02 22.30.34.jpg
Fixture: 2026-08-02_22-30-34-breakfast-bacon-eggs-fruit.jpg

#### Local PoC

Top-3 dish candidates:

~~~text
1. french_toast — 39.7%
2. pancakes — 25.4%
3. omelette — 18.2%
~~~

Accepted regions: 3.

Accepted regions:

~~~text
carrot — 12.2%
carrot — 11.2%
carrot — 10.5%
~~~

Debug detector evidence:

~~~text
carrot — 12.2%
carrot — 11.2%
carrot — 10.5%
~~~

No dish-specific prior was applied to the accepted detector results.

#### Independent baseline

Dish candidates:

~~~text
1. bacon, scrambled eggs, and fruit breakfast plate — 91%
2. bacon and scrambled eggs — 7%
3. fruit-and-egg breakfast — 2%
~~~

Visually supported components:

| Component | Approximate confidence |
|---|---:|
| Scrambled eggs | 99% |
| Bacon | 99% |
| Blueberries | 99% |
| Strawberries | 98% |

Approximate normalized regions:

| Region | Box [left, top, right, bottom] |
|---|---|
| Bacon strips | [0.07, 0.12, 0.45, 0.78] |
| Scrambled eggs | [0.28, 0.25, 0.67, 0.84] |
| Blueberry cluster | [0.52, 0.16, 0.89, 0.69] |
| Strawberry pieces | [0.57, 0.08, 0.97, 0.60] |

Full baseline hypothesis list:

- Visual evidence: bacon strips.
- Visual evidence: scrambled eggs.
- Visual evidence: blueberries.
- Visual evidence: strawberries.
- Dish/common-ingredient inference only: butter or oil.
- Dish/common-ingredient inference only: salt and pepper.
- Dish/common-ingredient inference only: milk or cream may have been used in the eggs, but this is unsupported by the image.

Abstentions:

- Additives, seasoning, and exact doneness were not inferred.

### 5.5 Photo 5 — poultry, salad, carrots, and tomatoes

Input: 2026-08-02 22.30.42.jpg
Fixture: 2026-08-02_22-30-42-chicken-salad-vegetables.jpg

#### Local PoC

Top-3 dish candidates:

~~~text
1. pork_chop — 50.1%
2. grilled_salmon — 41.2%
3. caesar_salad — 2.1%
~~~

Accepted regions: 3.

Accepted regions:

~~~text
carrot — 34.4%
carrot — 23.8%
carrot — 17.1%
~~~

Full debug ingredient suggestions:

| Label | Debug evidence | Local outcome |
|---|---:|---|
| pork_chop | Dish prior | Not detected |
| pork | Dish prior | Not detected |
| carrot | Dish prior and detector confidence 34.4% | Accepted region |
| potato | Dish prior | Not detected |
| green_beans | Dish prior | Not detected |
| apple_sauce | Dish prior | Not detected |
| orange | Detector confidence 19.8% | Rejected by dish prior |
| broccoli | Detector confidence 18.4% | Rejected by dish prior |

Warnings:

~~~text
dish-conditioned prior applied; detector labels outside prior are debug-only
~~~

#### Independent baseline

Dish candidates:

~~~text
1. grilled or roasted chicken salad — 64%
2. roasted poultry with salad and vegetables — 24%
3. turkey-breast salad — 12%
~~~

Visually supported components:

| Component | Approximate confidence |
|---|---:|
| Cooked light meat | 96% |
| Lettuce | 98% |
| Sliced carrots | 99% |
| Cherry tomatoes | 98% |
| Red pepper strips | 91% |
| Poultry-like meat | 82% |
| Pale yellow vegetable | 57% |
| Green herb flecks | 58% |

Approximate normalized regions:

| Region | Box [left, top, right, bottom] |
|---|---|
| Three cooked-meat portions | [0.06, 0.04, 0.77, 0.76] |
| Leafy salad | [0.04, 0.25, 0.57, 0.88] |
| Sliced carrots | [0.52, 0.00, 0.98, 0.40] |
| Cherry tomatoes | [0.57, 0.38, 0.96, 0.80] |
| Red pepper strips | [0.72, 0.13, 0.99, 0.63] |

Full baseline hypothesis list:

- Visual evidence: cooked light meat.
- Visual evidence: poultry-like meat.
- Visual evidence: lettuce.
- Visual evidence: sliced carrots.
- Visual evidence: cherry tomatoes.
- Visual evidence: red pepper strips.
- Visual evidence: pale yellow vegetable.
- Visual evidence: green herb flecks.
- Dish/common-ingredient inference only: chicken is favored, but turkey remains plausible.
- Dish/common-ingredient inference only: oil, marinade, seasoning, or dressing may be present but were not confirmed.

Abstentions:

- Chicken versus turkey was unresolved.
- The dark object beneath the carrots was too occluded to label.

## 6. Grid prompt evidence

After the automatic local analysis of Photo 1, grid cell 5 was selected in the PoC UI and the analysis was re-run.

Observed result:

~~~text
status: SUCCESS
top-3: greek_salad 96.5%, caesar_salad 1.1%, bibimbap 1.0%
regions: 1
region label: grid prompt
~~~

The warning explicitly stated:

~~~text
grid prompt is class-agnostic; its mask does not identify an ingredient
~~~

This is correct behavior for the current MobileSAM path. The grid is a useful interaction for selecting an area, but it must not be rendered as if the selected region were an identified ingredient.

The manual visual evidence screenshot was captured outside the repository at:

/tmp/supernutrition-g3-poc-grid5.png

It is not part of the committed BMAD artifact.

## 7. Findings from the comparison

### 7.1 Dish recognition is weak on composite plates

The local Food-101 classifier is trained around single dish labels. The five images contain multi-component plates rather than clean examples of one canonical Food-101 class. This creates predictable mismatches:

- Photo 1 was visually a mixed buffet plate but the local top-1 was greek_salad at 96.5%.
- Photo 3 was fruit, cottage cheese, and roasted vegetables, but the local top three were beet_salad, cheese_plate, and gnocchi.
- Photo 4 visibly contained bacon, scrambled eggs, blueberries, and strawberries, but the local ranking was french_toast, pancakes, and omelette.
- Photo 5 visibly contained poultry and salad, but the local top-1 was pork_chop, followed by grilled_salmon.

The classifier output can still provide a useful coarse prior, but it should not be treated as the identity of every component on a composite plate.

### 7.2 The hard top-1 prior creates false negatives

Photo 5 is the clearest example. The local classifier selected pork_chop, but the debug list showed:

- pork_chop prior not detected;
- pork prior not detected;
- carrot accepted;
- orange detector proposal rejected by the pork-chop prior;
- broccoli detector proposal rejected by the pork-chop prior.

The prior is therefore not merely re-ranking evidence. It decides which detector results are allowed into the accepted region set. A wrong dish top-1 can suppress visually plausible ingredients.

Photo 1 shows a similar pattern: a broccoli proposal at 68.7% was rejected while the independent baseline considered broccoli visually clear.

### 7.3 The detector vocabulary is too narrow for ingredient decomposition

The local component path retains only five COCO labels: banana, apple, orange, broccoli, and carrot. The baseline identified many additional visible components:

- rice;
- meat;
- lettuce;
- tomato;
- Brussels sprouts;
- cauliflower;
- red onion;
- cucumber;
- peppers;
- cheese or tofu;
- watermelon;
- pineapple;
- berries;
- bacon;
- eggs;
- poultry.

The absence of these labels in local accepted regions is primarily a capability limitation, not evidence that the corresponding foods are absent from the image.

### 7.4 Accepted region count does not equal semantic quality

The local results produced:

~~~text
Photo 1: 0 regions
Photo 2: 0 regions
Photo 3: 1 carrot region
Photo 4: 3 carrot regions
Photo 5: 3 carrot regions
~~~

This pattern is not a credible ingredient inventory. It is a measurement of what the current filtered proposal path happened to accept. Repeated carrot regions on Photos 4 and 5 demonstrate that a detector label can be present without being a useful description of the whole plate.

### 7.5 Class-agnostic segmentation is useful but insufficient

MobileSAM can provide a mask for a selected prompt, but it cannot provide an ingredient name. The grid path is therefore appropriate for:

- asking the user to select a component;
- creating an editable region;
- gathering future annotation data;
- supporting a human-in-the-loop correction flow.

It is not sufficient for autonomous ingredient identification.

### 7.6 The independent baseline is semantically richer but not an evaluation oracle

The gpt-5.6-sol baseline was substantially more useful for describing visible composition in these five photos. However, it has important limitations:

- confidence values are approximate;
- boxes are coarse and may cover clusters of multiple ingredients;
- labels may rely on semantic priors;
- it did not produce device-executable artifacts;
- it has no weighed mass or portion estimate;
- it was not compared against human-labeled ground truth;
- its output was not repeated enough to estimate run-to-run stability.

The correct use is qualitative auditing and hypothesis generation, not replacing the local evidence contract with unverified language-model output.

## 8. Recommended product and engineering response

### 8.1 Preserve separate evidence channels

The comparison supports an additive result contract with explicit provenance:

~~~json
{
  "visualEvidence": [
    {
      "label": "broccoli",
      "confidence": 0.687,
      "source": "detector",
      "region": "[normalized box or mask]",
      "accepted": false,
      "rejectionReason": "dish_prior_gate"
    }
  ],
  "dishCompositionHypotheses": [
    {
      "label": "greek_salad",
      "support": 0.965,
      "source": "dish_classifier"
    }
  ],
  "userPromptRegions": [
    {
      "gridCell": 5,
      "mask": "class_agnostic",
      "label": null
    }
  ],
  "abstentions": [
    {
      "label": "chicken_vs_turkey",
      "reason": "visual ambiguity"
    }
  ]
}
~~~

This prevents a dish prior from being confused with a visual detection and prevents a class-agnostic mask from being presented as an identified ingredient.

### 8.2 Replace hard top-1 gating with soft or top-k fusion

The next fusion experiment should not discard a detector proposal solely because it is absent from the top-1 dish prior. Candidate approaches, in increasing complexity:

1. retain raw visual proposals in the user-visible evidence list and mark them as prior-inconsistent;
2. use a top-k dish prior rather than only top-1;
3. use a soft prior multiplier rather than a boolean acceptance gate;
4. separate candidate ingredient from confirmed ingredient;
5. abstain when dish and visual evidence disagree instead of forcing a single interpretation.

This should be validated against fixed fixtures before being enabled in a production runtime.

### 8.3 Expand the component vocabulary deliberately

The next detector experiment needs a component ontology that includes at least the categories repeatedly present in this sample:

~~~text
rice, meat, poultry, bacon, egg, leafy greens, tomato, cucumber,
red onion, bell pepper, broccoli, cauliflower, Brussels sprouts,
carrot, potato, sweet potato, zucchini, eggplant, cheese, tofu,
watermelon, pineapple, strawberry, blueberry, raspberry, blackberry
~~~

The list above is a research candidate list, not an approved production ontology. It should be versioned, mapped to source model labels, and evaluated for false-positive behavior.

### 8.4 Use the language-model baseline as an audit tool

A safe short-term workflow is:

~~~text
local model output
    -> independent vision audit
    -> disagreement report
    -> human review of disagreement
    -> fixture and annotation update
    -> local model/fusion change
    -> repeat on fixed corpus
~~~

The baseline can help identify missing vocabulary and likely false negatives, but it should not silently feed inferred ingredients into nutrition calculations.

### 8.5 Build a labeled evaluation corpus before claiming model quality

For each image, the future benchmark record should contain:

- image identifier and provenance;
- consent status and license;
- dish-level labels, including multi-component plate labels;
- visible component labels;
- normalized boxes or segmentation masks;
- uncertainty and abstentions;
- optional mass/grams only when measured with a calibrated scale;
- expected UI behavior;
- model asset and contract versions.

The current five images are appropriate smoke fixtures. They are not enough for macro F1, calibration, or production quality claims.

## 9. Reproducibility

### 9.1 Local PoC build and install

The harness script is:

tools/g3-benchmark/run-poc-android.sh

It downloads or verifies the pinned assets, builds the Android runner, installs the APK, and launches .PocActivity. A fresh run should use an explicit physical-device serial when more than one ADB endpoint is connected. Do not commit the serial to an artifact.

Equivalent high-level commands:

~~~bash
cd /Users/aj/.codex/worktrees/d550/SuperNutrition
ANDROID_DEVICE_SERIAL=<physical-device-serial> tools/g3-benchmark/run-poc-android.sh
~~~

For the manual comparison:

1. Open the installed PoC.
2. Choose Gallery.
3. Select one image from mobile/fixtures/photo-analysis/user-samples/.
4. Wait for the automatic analysis to reach SUCCESS, PARTIAL, or FALLBACK.
5. Record top-3 dish candidates, accepted region count, warnings, and the full debug suggestion list.
6. Repeat for all five images.
7. On Photo 1, select grid cell 5 and record the class-agnostic mask warning.

### 9.2 Independent baseline request

The baseline run is reproducible as a prompt-and-input protocol, not as an Android shell command:

- Model: gpt-5.6-sol
- Inputs: the five original JPEG paths listed in section 3.2
- Repository access: prohibited
- Repository mutation: prohibited
- Output: one structured record per image containing top-3 dish hypotheses, visible components, approximate normalized regions, provenance-marked ingredient hypotheses, and abstentions
- Critical instruction: distinguish visual evidence from dish-prior or common-ingredient inference

The percentages and boxes should be treated as baseline notes unless the exact model call is repeated under controlled settings.

### 9.3 Source files

The relevant local implementation and documentation are:

- [G-3 benchmark README](../../../tools/g3-benchmark/README.md)
- [PoC activity](../../../tools/g3-benchmark/android-runner/app/src/main/java/com/supernutrition/g3benchmark/PocActivity.java)
- [PoC inference engine](../../../tools/g3-benchmark/android-runner/app/src/main/java/com/supernutrition/g3benchmark/poc/PocInferenceEngine.java)
- [PoC analysis contract](../../../tools/g3-benchmark/android-runner/app/src/main/java/com/supernutrition/g3benchmark/poc/PocAnalysis.java)
- [Pinned asset download script](../../../tools/g3-benchmark/download-assets.sh)
- [User-sample fixtures](../../../mobile/fixtures/photo-analysis/user-samples/README.md)
- [Uncertainty-aware fusion research](technical-uncertainty-aware-dish-ingredient-fusion-research-2026-08-02.md)
- [Research review and findings](review-uncertainty-aware-dish-fusion.md)

## 10. Decision record

### Current decision

Do not claim that the local PoC can autonomously identify all ingredients or reliably segment a composite plate. Do not use the five-photo comparison as a G-3 production signoff.

### Supported claim

The local PoC demonstrates:

- physical-device execution;
- automatic analysis after photo selection;
- a Food-101 dish ranking;
- a constrained component proposal path;
- dish-prior debug output;
- class-agnostic grid/mask interaction;
- explicit warnings about research-only limitations.

### Unsupported claim

The local PoC does not yet demonstrate:

- complete ingredient identification;
- reliable dish decomposition;
- calibrated ingredient confidence;
- correct semantic labels for every mask;
- mass or gram estimation;
- production runtime integration;
- nutrition-safe automatic conclusions;
- quality on a representative or consented corpus.

### Next implementation slice

The next logically bounded slice after this comparison is:

1. add a fixture-level comparison schema that stores visual evidence, prior hypotheses, accepted regions, and abstentions separately;
2. change the research fusion path from hard top-1 rejection to an auditable soft/top-k policy;
3. add regression fixtures for the broccoli-on-Photo-1 and poultry-on-Photo-5 disagreements;
4. expand or replace the component proposal vocabulary;
5. repeat the physical-device run and compare against a small human-reviewed annotation set.

Only after those steps should the team decide whether a richer local detector, a remote model, or a user-assisted correction workflow is the appropriate next product investment.

## 11. Nutrition5k 100-image fusion comparison — 2026-08-03

### 11.1 Dataset and dish-only preprocessing

To move beyond the five hand-selected smoke fixtures, a deterministic sample
of 100 eligible dish IDs was downloaded from the official Nutrition5k
`rgb_test` split. Nutrition5k publishes overhead RGB-D plate imagery together
with recipe/ingredient metadata; the benchmark uses only the official
overhead `rgb.png` object for each selected dish. See the
[official Nutrition5k repository](https://github.com/google-research-datasets/Nutrition5k)
for dataset provenance, splits, metadata, and license.

The source frames are 640x480. Each frame was converted to a canonical
plate-interior crop using normalized box `[0.25, 0.12, 0.92, 0.86]` plus an
elliptical mask. This removes the fixed rig border and QR markers while
retaining plate context. It is a deterministic input crop, not a food
segmentation ground truth. The raw source files and 100 crops are saved in the
project under `tools/nutrition5k-benchmark/data/nutrition5k-100/` (about 41 MB
including both raw and cropped images).

The exact seed, source object, source SHA-256, crop coordinates, and metadata
are recorded in
[manifest.json](../../../tools/nutrition5k-benchmark/data/nutrition5k-100/manifest.json).

### 11.2 Compared implementations

Both strategies use the same pinned PoC assets and host reproduction of the
PoC preprocessing/inference path:

- Food-101 dish classifier: `swin-finetuned-food101-matmul-int8.onnx`;
- YOLO-World v2 COCO region proposals: `yolov8s-worldv2-coco.onnx`;
- component vocabulary: `apple`, `banana`, `broccoli`, `carrot`, `orange`;
- confidence threshold 0.10, per-label NMS IoU 0.70, maximum 16 regions;
- CPUExecutionProvider through ONNX Runtime 1.23.2;
- exact dish prior map from `PocDishIngredientPriors.java`.

The comparison is intentionally a two-arm experiment:

1. `no_fusion`: retain filtered YOLO proposals without a dish-prior gate;
2. `hard_top1_dish_prior`: use the Food-101 top-1 dish and retain only
   proposals whose exact label is in that dish's prior. Rejected proposals
   remain available in the JSON debug record.

### 11.3 Results

The metadata-derived target is deliberately labeled a weak proxy: it is a
recipe-level ingredient name match for only the five detector labels, not a
claim that the ingredient is visible or localized in the image. There were 30
images with at least one non-empty weak target. `Macro F1` is averaged only on
those 30 cases; micro metrics include all 100 images.

| Strategy | Total regions | Images with regions | Weak precision | Weak recall | Weak micro F1 | Weak macro F1 (30 cases) |
|---|---:|---:|---:|---:|---:|---:|
| No fusion | 200 | 69/100 | 16.84% | 48.48% | 25.00% | 35.56% |
| Hard top-1 dish prior | 153 | 54/100 | 15.79% | 36.36% | 22.02% | 26.22% |
| Fusion delta | -47 (-23.5%) | -15 images | -1.05 pp | -12.12 pp | -2.98 pp | -9.33 pp |

The hard gate changed 16 cases and kept 76.5% of all raw regions. On this
sample it did not improve ingredient detection under the weak proxy: it
removed more proposals, including proposals whose labels matched the recipe
metadata, and every reported quality metric declined. This is evidence
against treating the current hard top-1 prior as a quality improvement. It is
still useful as an auditable precision filter in cases where the dish prior is
correct, but the benchmark does not support enabling it as an unconditional
improvement.

The complete per-image output, including dish top-3, prior, raw regions,
fused regions, metadata ingredients, and weak targets, is in
[comparison.json](../../../tools/nutrition5k-benchmark/data/nutrition5k-100/comparison.json).
The benchmark scripts and exact commands are documented in
[tools/nutrition5k-benchmark/README.md](../../../tools/nutrition5k-benchmark/README.md).

### 11.4 Interpretation and next experiment

This result does not prove that fusion is always harmful. It shows that the
current boolean top-1 policy is brittle on composite plates and on dish
classification errors: a wrong or incomplete dish prior can delete a visual
proposal before the user can review it. It also does not measure box or mask
quality because the dataset slice has no visible-component region labels in
this artifact.

The next controlled comparison should therefore keep the raw visual evidence
visible and test a soft or top-k prior:

- retain raw proposals and mark prior-inconsistent items instead of deleting
  them;
- apply a confidence-weighted prior or top-k dish union;
- add a manually reviewed visible-component annotation for a smaller subset;
- score semantic label precision/recall and localization separately;
- only then decide whether a fusion policy belongs in the user-facing path.

This 100-image run is host CPU evidence for the PoC, not a physical Android
device run and not a Product/Engineering G3 signoff.
