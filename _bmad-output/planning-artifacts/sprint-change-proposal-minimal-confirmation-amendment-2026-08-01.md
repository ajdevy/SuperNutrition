---
title: Sprint Change Proposal Amendment — Minimal Meal Confirmation
status: approved
date: 2026-08-01
mode: batch
scope_classification: moderate
approved_by: Anastasia, Product owner
approved_at: 2026-08-01
---

# Sprint Change Proposal Amendment — Minimal Meal Confirmation

## Issue and evidence

Product requires the app to identify as much as possible, then ask the person to
select a main-dish name from a small ranked set and tap only the ingredient
icons they accept. A later epic will introduce a database of dishes,
ingredients, and reference weights.

The existing major proposal remains valid: v1 produces ranked dish candidates;
v2 may produce only visibly separable components and optional estimated mass
evidence. Current device research does not establish an ingredients-and-grams
production model. Recipe data cannot be presented as `From this photo`, a
measured mass, or an automatically accepted ingredient.

## Impact and recommendation

Use a moderate direct adjustment; no rollback, model selection, contract change,
or database dependency is needed now.

| Story | Clarified responsibility |
| --- | --- |
| E3.9 | Truthful local stages, Cancel, and evidence display: v1 candidates become meal-name proposals; v2 `VisibleComponent`s become photo-evidence ingredients. It does not save selections or access a dish database. Its implementation closes at the coordinator/UI/lifecycle seam; production runtime activation and physical staged-result evidence remain Epic 3 G3 exit gates. |
| E5.1 | Explicit confirmation: up to three unselected main-dish options, unselected `From this photo` ingredient chips, `None of these`/manual search, reversible edits, then one visible `Confirm foods` action. |
| E5.3 | Catalog/serving/nutrition only after explicit user choices; photo ranges remain editable evidence, never defaults or facts. |
| E5.4 (new) | Versioned, licensed dish-composition reference source. Only after an explicit dish selection it may show unselected `Also common with this dish` suggestions and clearly labelled recipe-reference weights. |
| E7.5 | Advanced merge/split repair; it extends rather than duplicates baseline E5.1 chip confirmation. |

### Required safeguards

- No meal, ingredient, recipe, weight, serving, or nutrition value is
  preselected. Food identity and recipe remain forbidden defaults.
- A v1 dish label is never decomposed into ingredient chips.
- A database ingredient/weight is not photo evidence: label it separately,
  keep it optional and editable, and never call its weight measured or inferred
  from the plate.
- A missing component engine or missing database keeps the manual/photo-only
  path usable and does not fabricate items.
- Deterministic Android/iOS acceptance fixtures cover success, correction,
  abstention, unavailable model, no components, cancellation, accessibility,
  interaction budgets, and provenance separation. No live model output is a UI
  test oracle or telemetry payload.

## UX gate

The architecture's existing precondition remains: before visual Compose work,
Product/Design must approve UX-A1 and responsive/accessibility-annotated mocks
for Capture, Remote consent, Candidate review, Serving confirmation, and
Grounded result. The Candidate review mock must include the three unselected
meal alternatives, unselected photo-evidence icons, `None of these`, the
no-gram-evidence state, and the future-only separately provenanced database
group.

## Requested approval

Approve this amendment to update E3.9/E5.1 ownership, add E5.4 to the Epic 5
backlog, and start visual implementation only after the UX gate is signed.
