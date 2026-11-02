# Suggestion-Oriented Food Fusion: Evaluation and Improvement Research

**Date:** 2026-08-03
**Status:** Research complete; implementation plan pending product decision
**Scope:** Local Epic 3 PoC, Nutrition5k evaluation, dish/component suggestions, and the next fusion iteration
**Worktree:** `REDACTED_WORKTREE/d550/SuperNutrition`
**Branch:** `codex/epic-3`
**Production code changed in this research pass:** No

## Executive conclusion

The product does not need to identify the exact dish before it can be useful. It needs to provide a small, honest set of plausible dish choices and visible-food hints that the user can confirm or correct.

The current 100-image result does not support shipping the hard top-1 dish prior as an unconditional improvement. It removes too many visual proposals when the dish classifier is wrong. The next iteration should therefore be **suggestion-first late fusion**:

1. preserve raw visual detections;
2. use the dish model as a soft composition prior, never as a veto;
3. separate “visible in this photo” from “plausible for this meal”; and
4. abstain or ask for correction when evidence conflicts.

Another 200 Nutrition5k images are worthwhile, but only as a **frozen, disjoint holdout** after replaying the existing 100 images and auditing the disagreements. A second 200-image pass scored only against recipe metadata would create more numbers without establishing that the UI suggestions are visually helpful.

## Question being answered

Should we compare another 200 Nutrition5k images now, and which additional models or fusion strategy are needed if the main objective is useful visual guidance rather than exact dish classification?

## Subagent research

Four independent read-only Codex subagents reviewed the current benchmark, PoC seams, evaluation design, product behavior, and candidate model families. They were asked to reason from the repository and the existing 100-image evidence rather than modify code.

| Subagent | Model / reasoning | Focus | Conclusion |
|---|---|---|---|
| Newton | GPT-5.5 / high | Evaluation design and statistical power | Annotate and replay the existing 100 first; add 200 only as a reviewed, disjoint expansion. |
| Zeno | GPT-5.6 Terra / xhigh | Vision and fusion architecture | Stop hard top-1 filtering; move to top-k soft support and late fusion with abstention. |
| Cicero | GPT-5.6 Luna / xhigh | Product UX and safety | Make evidence sources explicit: visible foods, possible meal, and unconfirmed meal-prior suggestions. |
| Mill | GPT-5.6-sol / high | Repository and PoC implementation audit | The required seams already exist for a policy replay; model expansion should follow evidence from that replay. |

The recommendations converged: **research GO, current hard-gate product NO-GO, policy replay next, 200-image holdout after the protocol is fixed.**

## What the current 100-image benchmark actually proves

The benchmark uses official Nutrition5k `rgb_test` overhead images, a canonical plate-interior crop, and an ellipse mask. The crop is an evaluation preprocessing choice; it is not ground-truth food segmentation. The weak target comes from recipe-level metadata and currently scores only five labels: `apple`, `banana`, `orange`, `broccoli`, and `carrot`.

The local pipeline is:

- Food-101 Swin INT8 ONNX dish classification;
- YOLO-World v2 proposals at 640×640;
- a five-label detector vocabulary with confidence threshold `0.10`, NMS `0.70`, and maximum 16 proposals;
- an exact Food-101 top-1 dish-to-ingredient prior;
- class-agnostic MobileSAM/grid support where available.

| Arm | Regions | Images with at least one region | Weak metadata precision | Weak metadata recall | Weak metadata F1 | Macro F1 on 30 non-empty weak-target cases |
|---|---:|---:|---:|---:|---:|---:|
| No fusion | 200 | 69/100 | 16.84% | 48.48% | 25.00% | 35.56% |
| Hard top-1 dish prior | 153 | 54/100 | 15.79% | 36.36% | 22.02% | 26.22% |
| Hard-prior delta | -47 | -15 images | -1.05 pp | -12.12 pp | -2.98 pp | -9.34 pp |

The hard prior changed 16 cases. Under the weak proxy it reduced coverage and F1; this is not proof that the underlying visual model is worse, because recipe metadata is not a visible-region annotation. It is enough to reject the current hard filtering policy as a user-facing default: a wrong dish top-1 can suppress a real visible component.

## Product target: useful suggestions, not forced certainty

The result should be structured into independent evidence channels.

| UI channel | Meaning | Allowed evidence | User-facing behavior |
|---|---|---|---|
| Possible meal | What the entire image may represent | Dish classifier top-k, composition similarity, open-set/abstention state | Show up to three unselected candidates, plus “None of these” and manual search. |
| Foods we can see | Components supported by visual evidence and a usable region | Detector score, localization quality, visual calibration | Show “From this photo” with an approximate region; require confirmation. |
| Possible with this meal | Ingredients suggested by the dish prior but not visually confirmed | Soft dish mixture and recipe/ontology support | Show separately; never draw a box, never label as seen, never auto-add to nutrition. |
| Selected area | A class-agnostic grid or MobileSAM region | User-selected area or unlabeled mask | Describe as an area until a model or user names it. |

Recommended copy:

- Dish section: **“What this meal might be”**
- Visual section: **“Foods we can see”**
- Prior section: **“Possible with this meal — not confirmed in the photo”**
- Conflict: **“The meal type and visible foods do not fully agree. Check the suggestions before continuing.”**
- Abstention: **“We couldn’t identify this confidently. Try another photo or choose foods manually.”**

This prevents the two most important failure modes: turning a dish guess into a false ingredient claim, and treating “not detected” as “not present.”

## Recommended fusion direction

### Stop using hard top-1 as a veto

The current seam effectively allows the top-1 Food-101 dish to decide which detector labels survive for regions. That makes dish classification an upstream gate for component visibility. It is unsafe for a suggestion product because a dish error can remove a correct visual hint.

### First replacement: top-k soft composition support

For each candidate component `i`, compute composition support from the dish distribution rather than one dish:

```text
compositionSupport(i) = Σdish P(dish | image) × support(i, dish)
```

Use this score to rank or annotate suggestions. Do not delete raw visual proposals. A prior-only component may appear in the separate “possible with this meal” list, but it cannot become a “Foods we can see” item without visual evidence.

### Preferred production shape: calibrated late fusion

Keep these signals separate until ranking:

- visual detector confidence;
- localization/box or mask quality;
- dish composition support;
- disagreement between dish and detector;
- image quality and out-of-ontology state;
- calibration and abstention thresholds.

Only the visual channel can create an overlay. The dish prior can rerank an existing visual candidate, or create a clearly marked composition hypothesis, but it cannot create a region.

### Required provenance for every suggestion

Each suggestion should retain:

- `source`: visual, composition, or both;
- visual score and composition support separately;
- region linkage, if any;
- decision/rejection reason;
- dish, ontology, policy, and calibration versions;
- abstention state;
- raw detector candidate before filtering.

This makes the fusion auditable and prevents future model tuning from hiding evidence by deleting it.

## Evaluation plan

### E0 — replay the existing 100 images

Run the same fixed records through these arms without redownloading or changing crops:

1. no prior;
2. current hard top-1 prior;
3. top-1 soft support;
4. top-k union/soft support;
5. bounded calibrated late fusion.

The replay should measure ranking, retention, provenance, abstention, and overlay decisions. It should preserve full raw proposals and the reason each item is visible, composition-only, prior-inconsistent, or suppressed as an artifact.

### E1 — conflict audit before expanding the corpus

Manually review:

- all 16 images changed by the hard gate;
- a stratified sample of unchanged images;
- empty-prior and no-region cases;
- mixed/composed plates and low-quality images.

Review labels should be image-first, not recipe-first:

- visibly present;
- not visibly present;
- ambiguous/occluded;
- out of current ontology;
- suggestion useful to a user;
- suggestion misleading or requiring correction.

Use two reviewers for at least the conflict slice and adjudicate disagreements. A practical first audit is 50 images: all 16 changed plus 34 prespecified controls.

### E2 — add 200 images as a locked holdout

Proceed to another 200 only after the policy and annotation schema are frozen. Keep the original 100 as development/replay data and do not retune thresholds on the holdout.

The expanded set should have:

- unique image IDs and a recorded exclusion manifest;
- disjoint images from the first 100;
- preserved source/model/crop hashes;
- stratification by dish confidence, detector output, image quality, mixed plate, and prior conflict;
- at least a reviewed subset rather than only recipe metadata;
- image-level bootstrap confidence intervals, because ingredient claims within one plate are correlated.

### Metrics that match the product

Exact dish accuracy remains a diagnostic, not the primary product metric.

| Metric | Product question |
|---|---|
| Visible-component precision | When we say “we can see X,” is X actually visible? |
| Visible-component recall / useful coverage | Does the user receive useful hints for the foods that are visible? |
| Candidate recall@3 | Does the correct dish or a useful equivalent appear among choices? |
| Prior-only false-claim rate | How often does a dish prior look like a visual assertion? Target: zero in the UI contract. |
| Wrong-dish veto rate | How often does a wrong dish remove a component a reviewer would keep? |
| Accepted useful coverage | How often does the user retain a suggestion judged useful? |
| Correction burden and time | How much work is needed to reach a usable meal description? |
| Abstention recovery | Can the user recover through retake, manual search, or area selection? |
| Calibration/risk-coverage curve | Does higher confidence actually correspond to safer visible claims? |

The primary go/no-go comparison should be against no-fusion: improve useful coverage and candidate recall without a statistically meaningful increase in false visual claims or correction burden.

## E0 result: policy replay on the existing 100

E0 was completed as a deterministic host-side replay over the saved
`comparison.json`; no ONNX inference was rerun and no new images were added.
Focused policy tests passed, the replay covered all 100 cases across five
arms, and the hard/no-fusion metrics matched the existing benchmark exactly.

| Strategy | Overlay regions | Images with overlays | Visual weak-proxy F1 | Visual + composition weak-proxy F1 | Prior-only suggestions |
|---|---:|---:|---:|---:|---:|
| `no_fusion` | 200 | 69 | 25.00% | 25.00% | 0 |
| `hard_top1_dish_prior` | 153 | 54 | 22.02% | 22.02% | 0 |
| `top1_soft_support` | 200 | 69 | 25.00% | 28.99% | 10 |
| `topk_union` | 200 | 69 | 25.00% | 28.97% | 17 |
| `bounded_late_fusion` | 200 | 69 | 25.00% | 28.97% | 17 |

The union uplift is only a weak recipe-level proxy because it includes
composition-only hypotheses; it is not evidence of visible ingredient
accuracy. The actionable result is that all soft/late arms preserve the 200
raw visual regions, while the hard prior removes 47. The prior-only candidates
are explicitly separated from visual overlays and remain uncalibrated.

The E0 replay outputs belonged to the detector-fusion benchmark and were removed under release cleanup decision D-13.

## E1 result: visible-evidence audit

The E1 slice contains 50 unique cases: all 16 hard-gate conflicts plus 34
controls. Two independent model-assisted reviewers annotated the conflict
group and one reviewer annotated the controls. The artifact passed structural
verification for 50/50 coverage, required fields, and normalized regions in
`[0,1]`.

This is not human ground truth. The status is
`model_assisted_pending_human_adjudication`; all 16 conflict cases remain
flagged because exact string agreement is too strict for safe labels such as
“carrot rounds” versus “carrot slices” or “long green stalk vegetables” versus
“asparagus.” One control received an explicit abstention, which confirms that
the audit can represent uncertainty rather than forcing an ingredient.

Qualitatively, both conflict reviews repeatedly describe visible components
outside the current five-label detector vocabulary and identify cases where a
hard dish prior would suppress otherwise plausible visual evidence. This is
directional evidence for keeping raw proposals and expanding the ontology; it
is not a measured false-negative rate until the conflict slice is human-
adjudicated.

The E1 audit outputs belonged to the detector-fusion benchmark and were removed under release cleanup decision D-13.

## E2 result: disjoint 200-image holdout

E2 added 200 official Nutrition5k `rgb_test` dish IDs after excluding every
`dish_id` in the original 100-image manifest. Verification passed for 200/200
raw frames, 200/200 plate crops, zero ID overlap, source SHA, model hashes,
and identical thresholds/crop policy. The run used the same weak recipe-level
proxy and therefore cannot be read as per-region visual ground truth.

| Strategy | Regions | Images with regions | Visual weak-proxy F1 | Visual + composition weak-proxy F1 | Prior-only suggestions |
|---|---:|---:|---:|---:|---:|
| `no_fusion` | 420 | 133 | 28.67% | 28.67% | 0 |
| `hard_top1_dish_prior` | 355 | 114 | 26.77% | 26.77% | 0 |
| `top1_soft_support` | 420 | 133 | 28.67% | 29.45% | 13 |
| `topk_union` | 420 | 133 | 28.67% | 30.23% | 32 |
| `bounded_late_fusion` | 420 | 133 | 28.67% | 30.23% | 32 |

The holdout makes the policy decision clearer: bounded late fusion preserves
all raw visual proposals and does not improve the visual weak-proxy F1 over
no-fusion, while the composition-only union rises from 28.67% to 30.23%.
That increase is a recipe hypothesis signal, not evidence that the image
contains those ingredients. The hard prior removes 65 regions on this holdout
and is therefore still unsuitable as a visual-evidence veto.

The E2 holdout outputs belonged to the detector-fusion benchmark and were removed under release cleanup decision D-13.

## E3 result: physical Android PoC

The selected `bounded_late_fusion` policy was rebuilt and verified on physical
DNY-NX9 (`PHYSICAL_DEVICE_ENDPOINT`). The research PoC unit suite passed (22 Gradle
tasks), the APK assembled and installed, and all five supplied user photos
completed with `success`:

| Photo | Top-3 dish candidates | Visible proposal labels | Regions |
|---|---|---|---:|
| breakfast bacon/eggs/fruit | `french_toast` 39.7%; `pancakes` 25.4%; `omelette` 18.2% | carrot ×3 | 3 |
| chicken/salad/vegetables | `pork_chop` 50.1%; `grilled_salmon` 41.2%; `caesar_salad` 2.1% | carrot, broccoli, orange | 9 |
| fruit/cottage-cheese/vegetables | `beet_salad` 20.1%; `cheese_plate` 19.9%; `gnocchi` 19.1% | carrot | 1 |
| green salad/protein | `greek_salad` 92.5%; `beet_salad` 5.4%; `caesar_salad` 0.8% | broccoli ×3 | 3 |
| mixed beef/rice/salad/vegetables | `greek_salad` 96.5%; `caesar_salad` 1.1%; `bibimbap` 1.0% | broccoli ×4, carrot | 5 |

The UI exposed the full dish-prior hypothesis list independently from visual
proposals and retained detector-only proposals for visual review. A grid-cell
5 smoke test on the mixed meal completed successfully, added a class-agnostic
`grid prompt` region, and displayed the explicit warning that its mask does
not identify an ingredient. These are real device observations, not human
labels: the five photos have no per-region adjudication in this run, dish
scores are uncalibrated, and the detector vocabulary is limited.

The E3 physical-device evidence was recorded separately and is not retained in this repository.

## Should we add other models?

Not as a model zoo before E0/E1. The current detector vocabulary and fusion policy are more immediate constraints than the absence of another general-purpose model.

### Immediate: no new runtime model

Reuse the pinned Food-101 and YOLO-World assets while changing only the policy. This isolates whether fusion is the problem and keeps Android/iOS parity tractable.

### Next local model screen: food-specific semantic segmentation

A compact food segmentation model such as SegFormer-B0, trained or adapted on a food segmentation corpus such as FoodSeg103, is a reasonable offline candidate for improving component pixels and ontology coverage. FoodSeg103 is a food-image segmentation benchmark, and SegFormer is a semantic segmentation architecture; neither by itself solves instance separation, mass estimation, calibration, or phone-domain shift. Use it first in shadow/offline evaluation, not as an automatic production claim. See the [FoodSeg103 CVPR paper](https://openaccess.thecvf.com/content/CVPR2021/papers/Wu_A_Large-Scale_Benchmark_for_Food_Image_Segmentation_CVPR_2021_paper.pdf) and the [SegFormer paper](https://proceedings.neurips.cc/paper_files/paper/2021/hash/64f1f27bf1b4ec22924fd0acb550c235-Abstract.html).

### Later research option: open-vocabulary grounding

Grounding DINO can test whether text-guided food queries improve ontology coverage, especially for foods outside the current five-label detector vocabulary. It requires food-domain calibration and should initially run offline or as an explicitly consented remote fallback; it must not silently upload user photos or turn a weak text match into a confirmed visual ingredient. See the [Grounding DINO paper](https://arxiv.org/abs/2303.05499) and [official implementation](https://github.com/IDEA-Research/GroundingDINO).

### Not first-line solutions

- MobileSAM remains class-agnostic: a mask is an area, not an ingredient identity.
- Heavy instance/panoptic food models are better offline teachers than first mobile runtime candidates.
- GPT/remote multimodel baselines are useful for qualitative audits and ontology discovery, not as an uncalibrated production oracle.
- More dish classifiers will not fix the distinction between “visible” and “plausible with this dish.”

## Stop/go criteria

### Stop

Stop the 200-image expansion if it would be another run with:

- recipe metadata as the only truth;
- the same five-label vocabulary with no human visible-component audit;
- only no-fusion versus hard-fusion arms;
- no exclusion manifest or disjoint holdout;
- “more regions” or “higher dish accuracy” as the definition of success.

### Go

Go to the 200-image holdout when:

- E0 policy replay is deterministic and provenance-complete;
- E1 has reviewed all hard-gate conflicts and a control slice;
- the annotation schema and primary metrics are frozen;
- image IDs are disjoint and model/crop versions are recorded;
- the surviving policy is tested as suggestion ranking, not as a visual truth generator.

## Proposed implementation sequence

| Phase | Deliverable | Device requirement |
|---|---|---|
| E0 | Pure policy replay for five fusion arms over the existing 100 | Host-only first |
| E1 | 50-image conflict/control audit and metric report | Host-only; reviewer evidence |
| E2 | 200-image frozen holdout and confidence intervals | Host-only benchmark |
| E3 | Android/iOS parity for the selected policy, including overlay/source semantics | Physical-device smoke and relevant UI gates |
| E4 | One compact food-specific segmentation screen or open-vocabulary offline screen, only if E2 identifies localization/ontology as the limiting factor | Separate model/package/latency evidence |

E0 through E3 are now complete as research evidence. Do not interpret the
holdout proxy uplift or the successful device run as production activation.
The next decision is whether to adjudicate the visible-evidence conflicts and
run one food-specific semantic-segmentation/open-vocabulary model in shadow
mode; keep the current bounded policy as a research candidate until that
comparison has human-visible evidence.

## Dataset and evidence limitations

Nutrition5k is valuable for scale and nutrition-linked recipe metadata, but its labels are not a substitute for per-image visible-component truth. The official dataset contains roughly 5,000 cafeteria plates with ingredient masses and multiple camera views, and its own documentation notes a California-cafeteria collection bias. See the [official Nutrition5k repository](https://github.com/google-research-datasets/Nutrition5k) and [CVPR paper](https://openaccess.thecvf.com/content/CVPR2021/papers/Thames_Nutrition5k_Towards_Automatic_Nutritional_Understanding_of_Generic_Food_CVPR_2021_paper.pdf).

The benchmark therefore supports a policy decision, not a production accuracy claim. A later consented user corpus remains necessary for phone-photo domain shift, UI usefulness, and safety validation.

## Decision requested

Recommended decision: keep **bounded late fusion** as the research candidate and reject the hard top-1 prior as a visual-evidence veto. E0-E3 show preserved visual coverage and a small uncalibrated composition-proxy uplift, but not proven visual accuracy improvement. Before production, adjudicate the E1 conflict slice and screen one compact food-specific segmentation or open-vocabulary model offline/shadow-mode; then repeat the physical-device smoke with the winner.
