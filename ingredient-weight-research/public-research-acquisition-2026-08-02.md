# Public research sample acquisition — 2026-08-02

Status: downloaded for pre-release research only. This is not the consented
SuperNutrition corpus and cannot close Product/Engineering G-3.

## Downloaded sources

| Dataset | Research use | Sample | Source and terms | Decision |
| --- | --- | --- | --- | --- |
| FoodSeg103 | visible ingredient segmentation and label/mask experiments | 300 lossless image/mask pairs from the first training shard | [official repository](https://github.com/LARC-CMU-SMU/FoodSeg103-Benchmark-v1), project Apache-2.0; dataset provenance/terms still require legal review | Research benchmark only; never production evidence |
| Nutrition5k | RGB-D plate and mass-regression baseline | 300 plates from the official `rgb_test_ids` split, all with RGB and raw depth | [official repository](https://github.com/google-research-datasets/Nutrition5k), [official GCS data](https://storage.googleapis.com/storage/v1/b/nutrition5k_dataset/o?prefix=nutrition5k_dataset%2Fmetadata%2F); official page states Creative Commons v4.0, exact variant and product use still require legal confirmation | Research mass baseline only; cafeteria scanning-rig domain gap |
| NutritionVerse-Real | phone-photo ingredient-weight/mask benchmark screen | 889 images across 251 dishes and 45 food types; ten iPhone 13 Pro Max views per scene, with four randomly selected views manually mask-labelled | [dataset paper](https://arxiv.org/abs/2401.08598), [Kaggle dataset](https://www.kaggle.com/datasets/nutritionverse/nutritionverse-real); Kaggle API metadata observed 2026-08-02 reports `CC BY-NC-SA 4.0` and a 1,126,515,500-byte v2 archive | Research-only; non-commercial license blocks direct product use, and the public package does not expose the consent/scale-QC/split manifest required by G3 |

FoodInsSeg and UECFoodPix were not downloaded: their published terms restrict
use to non-commercial/non-profit research. They cannot be a safe product-data
source without a separate permission decision.

## Local cache and integrity

The raw research cache is outside Git at:

`/tmp/supernutrition-public-research-20260802/`

It contains approximately 405 MB of FoodSeg103 data and 173 MB of Nutrition5k
data. The FoodSeg103 Parquet source shard is:

`sha256:7288a62861c06f1c0f677674fd9564aae277a727a146bf31688cf86befa452b7`

The extracted sample manifests are:

- FoodSeg103 300-pair manifest:
  `sha256:d84c346a61e1468683c8858df6304f7f130b9d160110fffee7473dd0b70b492c`
- Nutrition5k 300-plate manifest:
  `sha256:c4bda20a9408103f090fd817a2f8f28da045d8abe33482d1ef3e828fcac4b779`

The NutritionVerse-Real archive was also downloaded outside Git for a bounded
research inventory at:

`/tmp/supernutrition-public-research-20260802/nutritionverse-real/nutritionverse-real.zip`

The archive is retained only for research because Kaggle reports
`CC BY-NC-SA 4.0`. It cannot be copied into the app, used as the production
corpus, or used to select a commercial model without a separate written
permission/licensing decision.

The downloaded archive inventory is reproducible without extracting photos:

- archive SHA-256: `22910bc84cb5840cb4a66be20d71ce2b7ba51f1d1a7f62108debf78fe7180780`;
- 892 ZIP entries and 1,126,515,500 uncompressed bytes;
- dish metadata: 251 rows and 1,184 non-zero per-ingredient weight records;
- image/split CSV: 889 image rows (`624 Train`, `265 Val`);
- COCO annotation file: 905 image records, 4,392 annotations, and 44 food
  categories plus background; 229 dish groups have four annotated images;
- metadata component weights range from 5 g to 701 g (median 43 g); this is a
  source inventory, not a model-quality result;
- the published Train/Val split keeps dish IDs separate, but it provides no
  calibration partition and is not the SuperNutrition participant/device/
  venue/session split manifest.

FoodSeg103 masks were extracted from Parquet-embedded lossless PNG bytes and
verified across all 300 samples: grayscale PNG masks with class IDs in `0–103`.
The public row API was rejected for masks because it served JPEG-converted
labels with out-of-range values.

The sample inventory observed 78 of the 103 available class IDs, with mean
foreground mask coverage `0.5123` and median `0.5026`. Nutrition5k contains 300
RGB-D plates, total-mass range `1–871 g` (median `178 g`), and an average of
`7.1` ingredient records per plate. These are dataset statistics, not
SuperNutrition quality scores.

## What this enables

- segmentation/ingredient-label baseline experiments on FoodSeg103;
- total/per-ingredient mass baseline experiments on Nutrition5k;
- comparison of public-dataset behavior before user-generated data exists.

Neither sample has the full SuperNutrition acceptance contract: consent refs,
scrubbed photo/mask digests, phone capture metadata, independent tared readings
for every visible component, privacy QC, and the project's split-isolation
groups. Do not run these samples through `--require-g3` or bind them as a
production `corpusRef`.

NutritionVerse-Real is a stronger research reference than the first two public
samples because its paper reports iPhone capture, per-ingredient scale weights,
and human masks. It still does not close G3: its public license is
non-commercial, it has 251 distinct dishes rather than 300 verified independent
cases, and the published package does not expose opaque consent references,
scale identity/resolution/calibration records, withdrawal/QC status, or the
required leakage-isolated manifest.

## 2026-08-02 public model screen refresh

The web research pass checked two additional candidates against the shared v2
contract:

- [FoodSAM](https://github.com/jamesjg/FoodSAM) is Apache-2.0 and combines SAM,
  a semantic segmenter, and an object detector. It has no mass head or
  calibrated component grams and is not an Android/iOS production artifact.
- [Boba Food VLM 0.8B](https://huggingface.co/Doses-AI/boba-0.8b-food-GGUF) is
  marked Apache-2.0 and reports per-ingredient grams from Nutrition5k-derived
  training/evaluation. Its published Q4 model is 505 MB with a 196 MB vision
  projector; it is a VLM output path, not the required mask/region,
  abstention, calibration, and rollback-bound v2 contract.

Both remain research-only. Apache-2.0 on a repository/model card does not by
itself clear the training data, dependent weights, mobile runtime, or product
claims. No artifact digest, legal clearance ref, calibration evidence, or G-3
manifest was created from this screen.
