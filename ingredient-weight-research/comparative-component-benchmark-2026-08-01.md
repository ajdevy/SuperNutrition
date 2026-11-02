# Comparative visible-component benchmark — 2026-08-01

## Scope

This was a research-only screen for Epic 3 v2. It did not alter the mobile
app, activate a model, or sign G-3. The public fixtures contain five composite
meals with conservative visible-component annotations and no calibrated scale
records, so they can evaluate labels but not gram estimates.

The original detector-specific source, prediction records, and generated
outputs have been removed under release cleanup decision D-13. This document
retains only the remaining non-detector findings; it is not current product
model approval.

## Remaining candidate observations

| Candidate | Execution evidence | Ingredient result | Mass result | Decision |
| --- | --- | --- | --- | --- |
| Food-101 Swin INT8 | Existing physical Android/iOS dish-classification runs | One dish label only; no component predictions | Not evaluable | Retain as dish-classification baseline |
| FoodSAM | No supported Android artifact for the released research stack | No Android score; no mass head | No mass head | Offline research only, subject to data terms |
| FoodSeg103 / ReLeM | No Android-compatible artifact in the project | Semantic masks do not separate repeated instances | No mass head | Research/pretraining candidate subject to data terms |
| MobileSAM | Physical Android run on five fixtures | Prompted class-agnostic masks only | No mass head | Annotation aid only; not ingredient recognition |
| YOLOX-Nano | Physical Android five-fixture deployment screen | Limited COCO-food vocabulary; not the product ingredient contract | No mass head | Deployment evidence only; separate checkpoint/data review remains |

## Decision

No retained candidate proves visible ingredient identity and calibrated grams on
the same meal. The next meaningful benchmark needs a legally cleared model plus
a consented, tared multi-component corpus. No G-3 product approval is implied.
