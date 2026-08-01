---
title: Sprint Change Proposal — Visible Ingredients and Gram Estimates
status: approved
date: 2026-08-01
mode: batch
scope_classification: major
trigger: Product direction changed from dish-only recognition to ingredients and grams as the immediate outcome.
approved_by: Anastasia, Product owner
approved_at: 2026-08-01
---

# Sprint Change Proposal — Visible Ingredients and Gram Estimates

## 1. Issue summary

The current Epic 3 implementation and G-3 evidence prove only a local
single-dish classifier.  The product outcome is now immediate recognition of
visible meal components and their gram estimates, including mixed plates such
as steak with sides, fish with salad, and Buddha bowls.

This is a stakeholder-driven scope change, not a classifier threshold defect.
The current Food-101/Swin runner returns exactly one dish label and has no
ingredient regions, masks, instance identities, or calibrated scale truth.  Its
current benchmark correctly reports visible ingredients as `unsupported` and
plated mass as `not-evaluable`.  It must not be relabelled as ingredient or
weight recognition.

The decision preserves one hard product boundary: the app may identify only
visibly separable components.  It must not invent hidden recipe ingredients,
oil, sauce composition, allergens, calories, or kitchen-scale-exact mass from a
single photo.  Numeric nutrition remains catalog-grounded after explicit user
confirmation.

## 2. Checklist and impact analysis

| Checklist item | Status | Finding |
| --- | --- | --- |
| 1.1 Triggering story | [x] Done | E3.2/E3.3 local engines exposed dish-only output while product requested ingredients/grams. |
| 1.2 Core problem | [x] Done | New requirement plus technical limitation: a classifier cannot detect components or infer calibrated mass. |
| 1.3 Evidence | [x] Done | Food-101 lacks ingredients/mass; G-3 has 0 component predictions and 0 calibrated weight cases; FPB smoke is research-only and AGPL-bound. |
| 2.1 Existing epic viability | [x] Done | E3 remains useful as the local-inference foundation, but v1 cannot be its product-complete outcome. |
| 2.2 Epic-level change | [x] Done | Extend E3 with a v2 component/mass track and amend E7.1 so there is one future inference v2, not competing contracts. |
| 2.3–2.5 Remaining epics/order | [x] Done | E5 needs component/portion confirmation; E6 needs a weighed corpus and v2 evaluation. Prioritize the v2 track ahead of closing dish-only E3. |
| 3.1 PRD conflicts | [x] Done | The current non-goal excludes automatic exact mass. Replace it with bounded visible-component *estimates* and an explicit uncertainty/confirmation rule. |
| 3.2 Architecture conflicts | [x] Done | `PhotoInferenceEngine` v1 is immutable. A v2 release, model manifests, parity fixtures, and independent Android/iOS adapters are required. |
| 3.3 UX conflicts | [x] Done | One indeterminate label is insufficient for a permitted long local wait. Add truthful stage copy, bounded progress refresh, Cancel, component review, and editable gram ranges. |
| 3.4 Secondary artifacts | [x] Done | G-3, G-5, corpus protocol, benchmark manifest/evaluator, app wiring, device matrix, privacy/consent, model-license review, journeys, and tests change. |

### Affected product commitments

1. **PRD:** Sections 1, 2.2, 4.2, 4.4, 5, 6, 7, 8, 10, 11, and 12 require
   amendments.  Exact automatic weight remains out of scope; a calibrated,
   clearly labelled estimate/range for visible components becomes in scope.
2. **Architecture:** AD-2/17/22 and the capability map require a v2 contract,
   model/taxonomy/mass-calibration manifests, output parity, rollback, and
   physical-device evidence.  AD-7 stays unchanged: no engine supplies
   nutrition values.
3. **UX:** `Analyzing` becomes truthful staged progress; Candidate review gains
   component rows and editable ranges; no gram value auto-confirms a serving.
4. **Epics:** E3, E5, E6, and the planned E7.1 contract-v2 story change.  No
   completed story is rolled back and v1 remains an immutable fallback.
5. **Gates:** the present G-3 Engineering approval remains a dish-only research
   budget only.  It is not Product acceptance of ingredients/grams and cannot
   close the component-capable G-3 or G-5 gates.

## 3. Options considered

| Option | Assessment | Decision |
| --- | --- | --- |
| Direct adjustment: add v2 stories beside v1 and run the current classifier as a fallback | High effort, medium delivery risk; preserves working evidence and gives the requested capability a path | **Recommended** |
| Roll back E3.1–E3.4 and replace v1 | High effort and high risk; removes valid single-dish evidence without solving segmentation/mass | Rejected |
| Keep the existing MVP unchanged and defer grams | Low immediate effort but contradicts the stated product direction | Rejected |

The recommended path is a **major, parallel v2 capability track**.  It avoids
breaking the immutable v1 contract, does not present FPB/YOLOv12-FoodWeight as a
shipping choice before legal review, and lets product receive visible
ingredients/grams rather than a disguised dish label.

## 4. Recommended v2 product and technical contract

### Product behavior

- Return one row per **visibly separable component**.  A row contains a
  localized candidate label, confidence, visible-region evidence, model/taxonomy
  provenance, and one of `estimated`, `not_evaluable`, or `abstained` mass
  states.
- When a validated mass model can score the component, show an editable
  **estimated range in grams**, its uncertainty/provenance, and an explicit
  confirmation before grounding nutrition.  Never label the result as measured
  weight.
- When geometry, confidence, model capability, or composition is insufficient,
  show `Not enough visual evidence for a gram estimate` and leave manual serving
  confirmation available.  Do not substitute a single confident-looking number.
- Keep hidden ingredients, oil, sauce composition, allergen claims, exact
  recipes, calories, and macros out of image inference.  E5 catalog grounding
  remains the only path to displayed nutrition.
- The longer local path is allowed under the existing **High** envelope only
  when it shows factual stages, bounded refreshes, and Cancel:
  `Preparing photo` → `Finding visible foods` → `Estimating portions` →
  `Checking results`.  Stages reflect real work, never a fake countdown.

### Contract and architecture

- Preserve `contracts/inference/v1` and its digest unchanged.
- Use the already-planned **`contracts/inference/v2`** as the sole future,
  modality-neutral contract.  Amend E7.1 rather than creating a rival v2.
- In v2 photo output, add a versioned `VisibleComponent` evidence collection
  (candidate identity, confidence, optional box/mask reference, visibility and
  ambiguity state) and optional `MassEstimate` (range, unit, calibration/model
  provenance, assumptions, and `estimated`/`not_evaluable`/`abstained` state).
  Text modality always returns mass `not_evaluable` unless a later separately
  approved measurement modality is introduced.
- Keep all numeric nutrition absent from v2 engine output.  Candidate-to-catalog
  binding and grams/serving confirmation remain explicit user events.
- Extend model manifests with taxonomy digest, preprocessing/postprocessing,
  instance/mask output contract, mass-calibration version, runtime/EP,
  model checksum, compatibility, activation, and rollback target.
- Keep Android and iOS adapters in their separate modules.  Select a commercial
  candidate only after the same component artifact passes both physical-device
  paths.  FPB/YOLOv12-FoodWeight is a research baseline, not a production
  dependency, until its AGPL-3.0/code/checkpoint/dataset terms have a written
  legal decision.

### Corpus and evidence

- First collect a consented pilot, then a versioned 300–500+ meal corpus with a
  held-out test split.  Every scored capture tared the empty vessel, weighs each
  visible component before plating, records the final plated mass, the scale
  identity/resolution/verification, overview and oblique device photos, consent
  reference, taxonomy, masks/boxes, and all quality-control decisions.
- Do not train/calibrate/test on the same meal, participant, restaurant,
  capture session, or device group.  Split and report by device, view,
  lighting, cuisine, ingredient family, portion range, occlusion, mixed-dish
  state, and depth availability.
- Evaluate visible-component set precision/recall/F1, instance/mask quality,
  per-component and total mass MAE, relative MAE, p90 absolute error, signed
  bias, calibration, and coverage-risk/abstention.  Only valid scale-linked
  records can score grams.
- Nutrition5k may be used only as an attributed, license-reviewed pretraining
  and benchmark-design source. The 181.4 GB public archive is never an app
  asset: a training environment may keep a compact metadata index and fetch
  explicitly selected shards from an approved mirror, while product acceptance
  remains the separate consented phone-photo corpus above.

## 5. Detailed change proposals

### 5.1 PRD

**Section: Vision / non-users / non-goals**

OLD:

> The product does not claim that a single image can reveal exact weight.
>
> Users who expect exact portion mass … from one RGB image are non-users.
>
> Exact automatic portion mass … from a single photo is a non-goal.

NEW:

> The product may offer a calibrated, explicitly estimated gram range for a
> visibly separable component only when a versioned component/mass model and
> capture evidence support it. It never represents the estimate as measured or
> exact weight, and it never infers hidden ingredients, oil, sauces, allergens,
> recipe composition, or numeric nutrition from the image.
>
> Users needing kitchen-scale-exact portions, hidden-recipe reconstruction, or
> nutrition without confirmation remain out of scope.

**Rationale:** permits the requested product outcome while retaining the
truthfulness and safety boundary.

**Section: FR-5 / FR-7 / FR-13 / FR-15 / FR-23 / FR-24**

OLD:

> Engines return Candidates without numeric nutrition; Candidate review and
> Serving Assumption confirmation follow; evaluation reports candidate and
> gram/calorie error after grounding.

NEW:

> Contract-v2 photo engines may return visible-component evidence and an
> optional estimated mass range with calibration/provenance and an explicit
> `not_evaluable`/`abstained` state. Users can remove, correct, merge, add, and
> confirm components and their gram/serving assumptions. Numeric nutrition is
> still calculated only from confirmed catalog records and confirmed servings.
> Evaluation reports component-set/mask quality and scale-linked component and
> total-mass error separately from nutrition-grounding error.

**Rationale:** defines what “ingredients/grams” means without granting an
engine authority over nutrition.

**Section: SM-6, SM-8, NFR-1..NFR-3, G-3/G-5**

NEW addition:

> A component/mass model is not activated until the named device matrix,
> runtime/model/taxonomy/calibration manifest, consented weighed corpus,
> physical-device parity/resource report, component and mass thresholds, and
> rollback target are approved. The Product decision approves claim wording and
> error/range policy; Engineering approves runtime/model/device budgets.

### 5.2 Epics and stories

| Change | Proposed new text / outcome | Rationale |
| --- | --- | --- |
| Amend E3 objective | “Freeze v1, then deliver v2 visible-component and optional mass-estimate local inference on Android and iOS.” | Makes the immediate product outcome explicit. |
| Add E3.5 — Release inference v2 component/mass evidence | Immutable v2 release, fixtures, schema, migration from v1, `VisibleComponent`, `MassEstimate`, no numeric nutrition, typed `not_evaluable`/`abstained` states. | Contract-breaking work is isolated and backwards compatible. |
| Add E3.6 — Establish consented weighed component corpus | Pilot, consent/lifecycle protocol, tared scale records, masks/boxes, taxonomy, leakage-resistant splits, validators. | There is no ground truth for grams otherwise. |
| Add E3.7 — Local Android component/mass spike | Detector/segmenter plus optional mass head behind v2; manifest, cancellation, rollback, parity and Honor device evidence. | Gives Android an independent implementation slice. |
| Add E3.8 — Local iOS component/mass spike | Same v2 artifact/evidence contract; iPhone device/depth-capability evidence and fallback. | Gives iOS an independent implementation slice. |
| Add E3.9 — Staged analysis and component review integration | Real stage progress, Cancel, component rows, confidence/range/no-estimate language, accessible review and manual correction. | Converts the model output into a safe usable experience. |
| Amend E5.1/E5.3 | Candidate review and serving confirmation operate per visible component; model grams are editable estimates, not defaults or facts. | Keeps catalog/nutrition authority intact. |
| Amend E6.3/E6.4 | v2 corpus runner, component/mask/mass metrics, Android/iOS device evidence, new G-3 artifact and G-5 threshold decision. | Makes final evidence reproducible. |
| Amend E7.1 | It remains the sole modality-neutral v2 release but incorporates E3.5 photo component/mass output. Text identification explicitly yields no mass estimate. | Avoids two incompatible v2 contracts. |

**Delivery order:** E3.5 → E3.6 pilot → E3.7/E3.8 in parallel → E3.9 → E5
amendments → E6/G-3/G-5.  The existing v1 Food-101 path may remain as an
explicit single-dish fallback throughout; it cannot close the new stories.

### 5.3 Architecture and UX

**Architecture additions:** update AD-2/17/22 and the capability map to name
v2 component/mass evidence, model/calibration manifests, v1-v2 coexistence,
cross-platform output parity, and `MassEstimate` validity states.  The reducer
must retain the one-active-engine/one-terminal-result, ephemeral photo, and
no-upload invariants.

**UX additions:** replace the one generic analyzing label with four factual
stages, announce a stage change at most once, preserve one visible Cancel,
avoid progress countdowns, and show a component card with label, confidence,
“estimated range”/“not enough evidence”, provenance, Edit/Remove/Add, and a
manual path.  The existing interaction policy does not allow food identity or
grams to be silently defaulted; each correction is session-scoped.

**Required journeys:** a visible multi-component plate succeeds; one component
is missed/corrected; an ambiguous/occluded component abstains; mass is
not-evaluable; user cancels during each active stage; model is unavailable;
offline/local-only; and no image bytes leave the device.

## 6. Provisional decisions requiring approval

| Decision | Proposed value | Owner / exit |
| --- | --- | --- |
| Claim wording | “Estimated grams” as a range; never “measured” or “exact” | Product owner approves with E3.9 copy. |
| Initial model scope | Only visibly separable components; no hidden recipe ingredients or nutrition | Product + ML owner approve taxonomy after corpus pilot. |
| Commercial model | No production FPB/YOLOv12-FoodWeight use until legal review; evaluate an alternative compact detector/segmenter against the same corpus | Legal + Engineering G-3 decision. |
| Product experience budget | Existing High local envelope: init <=15 s, analysis <=45 s, peak <=512 MB, with actual stage copy and Cancel | Product accepts the UX trade-off; Engineering remeasures the selected v2 model. |
| Quality gates | Starting hypotheses only: macro ingredient F1 >=0.85, case F1 >=0.80, component/total relative MAE <=15%, p90 absolute mass error <=50 g; report slices and abstention | Product + ML/evaluation owner re-approve after pilot under G-5. |
| Corpus | Consent pilot followed by 300–500+ tared, annotated meals and frozen held-out set | Product data owner + privacy/legal + evaluation owner. |

## 7. Implementation handoff and success criteria

**Scope classification:** Major — product/architecture replan required before
implementation.  No rollback is needed.

| Recipient | Responsibility |
| --- | --- |
| Product owner | Approve this change, claim wording, first visible-component taxonomy, range/abstention policy, and High-wait UX trade-off. |
| Contract owner | Deliver the single `contracts/inference/v2` release and v1-v2 compatibility fixtures. |
| Evaluation + privacy/legal owners | Approve consented corpus protocol, scale/QC rule, licensing, model/checkpoint rights, and held-out split. |
| Android/iOS inference owners | Independently implement and benchmark v2 candidates on physical devices. |
| Feature + UX owner | Implement staged progress, component review/correction, accessibility, interaction budgets, and no-fake-progress behavior. |
| Engineering owner | Select runtime/model only from evidence; sign v2 G-3 device/budget artifact and rollback target. |

The implementation is successful only when the app produces observable
visible-component evidence and calibrated, user-confirmable gram estimates on
the approved device matrix; records `not_evaluable` or abstains honestly when it
cannot; passes the new corpus, privacy, compatibility, journey, and resource
evidence; and receives separate Engineering and Product signatures for the v2
G-3 decision.  The current dish-only approval remains historical baseline
evidence, not this acceptance.

## 8. Approval record

| Role | Decision | Date | Reference |
| --- | --- | --- | --- |
| Product owner | approved | 2026-08-01 | Anastasia; user approval in this task |
| Engineering owner | pending v2 remeasurement | pending | current v1 High budget is not a v2 signature |

**Requested decision:** approve / revise / reject this Sprint Change Proposal.
