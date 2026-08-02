# Review handoff — Uncertainty-Aware Dish and Ingredient Fusion research package

**Review type:** BMAD adversarial review (`bmad-review-adversarial-general`)
**Review date:** 2026-08-02
**Reviewer:** Claude Code (read-only pass; no artifact was modified)
**Status:** B1, B2, and B4 addressed in the research package; B3, M1–M13, and H1–H11 remain open unless noted below.

**Follow-up note (2026-08-02):** The research and presentation now reference the
existing `inference-evidence/v2` contract and the measured G-3 baseline. B4 was
reclassified from an inconsistency to a direct contradiction after B1: the
legacy state lists in the adoption sections have now been replaced with the v2
visual contract plus the separate fusion/review envelope. The review-time
provenance below remains historical; re-read current files before acting on any
remaining finding.

This file is a self-contained handoff. Another agent should be able to act on it
without the originating conversation. Every claim below is marked as either
**verified** (checked against the network or the repository during this review)
or **asserted by the artifact** (taken from the reviewed documents, not
independently confirmed).

---

## 1. Provenance

| Field | Value |
| --- | --- |
| Worktree | `/Users/aj/.codex/worktrees/d550/SuperNutrition` |
| Branch | `codex/epic-3` |
| HEAD at review time | `d1357b4fdef9b6cd9671fda9b196116e3dc27938` |
| Working tree state | **dirty** — 30+ modified tracked files, including the inference v2 contract and its domain models |
| Review scope | 3 artifacts (below); no code was read for correctness, only for cross-reference |

> The dirty state matters: several files this review points at (`contracts/inference/v2/schema.toml`,
> `InferenceV2Models.kt`, `InferenceV2Contract.kt`) were modified but uncommitted at review time.
> Re-read them before acting — they may have moved since.

### Artifacts under review

| # | Path | Size |
| --- | --- | --- |
| A1 | `_bmad-output/planning-artifacts/research/technical-uncertainty-aware-dish-ingredient-fusion-research-2026-08-02.md` | 57 205 B / 6 860 words |
| A2 | `_bmad-output/presentations/uncertainty-aware-dish-fusion/technical-research-report.html` | 37 851 B / 645 lines |
| A3 | `_bmad-output/presentations/uncertainty-aware-dish-fusion/assets/README.md` | 153 B |

All line numbers below refer to these files at the HEAD above.

---

## 2. Verdict in one paragraph

The central thesis is correct and well argued: dish-composition knowledge and
visual detection are different evidence streams, and one `confidence` value
cannot honestly describe both. The external sourcing is honest — every citation
and dataset figure spot-checked came back accurate, which is not the usual
outcome. The package fails on a different axis: **it is written as if the
project were greenfield.** It does not reference the `inference-evidence/v2`
contract or the G-3 benchmark artifact, both of which live in this same worktree
and cover the same problem space, the latter dated the same day. As a result R1
("freeze semantics") would stand up a second, parallel contract, and the report
asserts that no baseline exists while a measured one sits two directories away.
Secondary but blocking: the evidence-state enum appears in three mutually
incompatible redactions inside a document whose whole thesis is "freeze
semantics before weights".

---

## 3. Already verified — do not re-check

This section exists so the next agent does not burn effort re-verifying what
this pass already confirmed.

### 3.1 External sources fetched and confirmed accurate

| Claim in A1 | URL | Result |
| --- | --- | --- |
| "Food Ingredients Recognition through Multi-label Learning" (A1:135) | `https://arxiv.org/abs/2210.14147` | **Verified.** Exact title match. Authors Rameez Ismail, Zhaorui Yuan. Submitted 2022-10-24. |
| Systematic review of image-based food recognition (A1:583) | `https://pubmed.ncbi.nlm.nih.gov/35803496/` | **Verified.** "Applying Image-Based Food-Recognition Systems on Dietary Assessment: A Systematic Review", *Advances in Nutrition*, 2022. 159 studies screened, 78 full-text, 45 (58%) deep learning. |
| NIST AI RMF Core, govern/map/measure/manage (A1:522, 632) | `https://airc.nist.gov/airmf-resources/airmf/5-sec-core/` | **Verified.** Resolves, title "5 AI RMF Core", all four functions present. Not a 404 or redirect. |
| ONNX Runtime Mobile get-started (A1:119) | `https://onnxruntime.ai/docs/get-started/with-mobile.html` | **Verified.** Live, title "Get started with ONNX Runtime Mobile". |
| Nutrition5k: 5 006 plates, RGB-D/side-angle, per-ingredient mass, official split, coverage limits (A1:141) | `https://github.com/google-research-datasets/Nutrition5k` | **Verified, all parts.** 5 006 plates; 4 rotating side-angle videos; overhead RGB-D *(when available)*; per-ingredient mass; official train/test split. README states: dataset "was only collected in a few select cafeterias in California, USA." |
| UECFoodPixComplete: 9 000 train / 1 000 test, manual masks (A1:143) | `https://mm.cs.uec.ac.jp/uecfoodpix/` | **Verified.** 9 000 / 1 000. Masks in Complete are manual, unlike base UECFoodPix (bounding box + GrabCut). |

**Conclusion: no fabricated citation was found.** Source verification in A1's
frontmatter (`source_verification: true`) is, on this sample, honest.

### 3.2 External sources cited but NOT independently fetched

These were not checked in this pass. Treat as unverified if a claim depends on
them: FoodSeg103 CVPR 2021 PDF · Nutrition5k CVPR 2021 PDF · Guo et al. PMLR v70 ·
SelectiveNet PMLR v97 · Selective Classification NeurIPS 2017 · Küppers CVPRW 2020 ·
Zero-Shot Ingredient Recognition AAAI 6626 · ONNX RT `tutorials/mobile/` ·
Android architecture docs · Kotlin coroutines/StateFlow · JSON Schema ·
Protobuf editions · W3C PROV-DM · OWASP MASVS · Google Rules of ML ·
The ML Test Score · TFMA metrics.

### 3.3 Repository symbols confirmed to exist

| Symbol named in A1 | Actual location | Result |
| --- | --- | --- |
| `PocInferenceEngine` (A1:212) | `tools/g3-benchmark/android-runner/app/src/main/java/com/supernutrition/g3benchmark/poc/PocInferenceEngine.java` | Exists |
| `PocAnalysis` (A1:212) | `.../g3benchmark/poc/PocAnalysis.java` | Exists |
| `IngredientSuggestion` (A1:212) | `.../g3benchmark/poc/PocAnalysis.java`, `PocInferenceEngine.java`, `PocActivity.java` | Exists |
| Epic-3 research record | `docs/research/epic-3-food-recognition-preprocessing-camera.md` | Exists, 8 598 B |

### 3.4 Confirmed absences

```bash
# 0 hits — A1 never references the existing contract or the measured baseline
grep -c "g3-benchmark\|G-3\|YOLO-World\|swin-finetuned\|deferred-work\|inference-evidence\|contracts/inference" \
  _bmad-output/planning-artifacts/research/technical-uncertainty-aware-dish-ingredient-fusion-research-2026-08-02.md

# 0 hits — A2 contains no SVG and no raster images, contradicting A3
grep -c "<svg" _bmad-output/presentations/uncertainty-aware-dish-fusion/technical-research-report.html
grep -c "<img\|<picture" _bmad-output/presentations/uncertainty-aware-dish-fusion/technical-research-report.html
```

---

## 4. Blockers

Fix these before this package feeds epics or stories.

### B1 — A1 does not know the `inference-evidence/v2` contract exists

**Location:** whole of A1; zero occurrences of `contracts/inference`, `inference-evidence`, `InferenceV2Models`.

**Evidence (verified in repo):** `contracts/inference/v2/schema.toml` declares
`schema = "supernutrition.inference-evidence-contract.v2"` with ports including
`result = "visible-component-evidence-or-abstention-or-typed-failure"` and
`component = "localized-candidate-calibrated-confidence-visibility-ambiguity-optional-normalized-region"`.
`mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/InferenceV2Models.kt`
already defines:

| Enum | Line | Values |
| --- | --- | --- |
| `ComponentVisibility` | 181 | `VISIBLE`, `PARTIALLY_OCCLUDED` |
| `ComponentAmbiguity` | 190 | `NONE`, `IDENTITY_AMBIGUOUS`, `MIXED_CONTENT` |
| `V2AbstentionReason` | 355 | `IDENTITY_AMBIGUOUS`, `NO_FOOD_DETECTED`, `IMAGE_QUALITY`, `UNSUPPORTED_CONTENT` |
| `V2InferenceFailureCode` | 370 | `INVALID_INPUT`, `MODEL_UNAVAILABLE`, `RESOURCE_LIMIT`, `TEMPORARY_UNAVAILABLE` |
| `MassNotEvaluableReason` | 307 | incl. `TEXT_HAS_NO_VISIBLE_PORTION` |
| `MassAbstentionReason` | 325 | — |
| `MassCalibrationAssumption` | 295 | — |
| `InferenceInputModality` | 79 | — |

Seven canonical fixtures exist under `contracts/inference/v2/fixtures/`:
`capability-ready.json`, `photo-component-estimated-result.json`,
`photo-component-not-evaluable-result.json`, `text-component-not-evaluable-result.json`,
`abstention-result.json`, `failure-result.json`, `telemetry-event.json`.

**Why it blocks:** A1 R1 (A1:530) says "Add the typed evidence states and
provenance fields". Executed literally, that creates a *second* evidence
contract beside a shipped v2 one, in a worktree where v2 files are currently
modified-uncommitted.

**Note the irony worth preserving:** A1:639 ("Current confidence bands are
presentation policy, not measured probabilities") is a precise and fair critique
of the `[confidence]` block in `schema.toml`
(`low_upper_exclusive = 0.5`, `medium_upper_exclusive = 0.8`, `high_lower_inclusive = 0.8`)
— but A1 never names the thing it is criticising.

**Fix:** rewrite R1 as an explicit **delta against `inference-evidence/v2`**:
which existing enum absorbs `COMPOSITION_PLAUSIBLE`, whether `ComponentVisibility`
gains a value or a sibling field is added, and whether this is a v2 minor
(`unknown_fields = "ignore"`, `missing_required_fields = "reject"`) or requires
`incompatible_change = "new-major-release"` per `schema.toml [compatibility]`.

---

### B2 — A1 ignores the project's own measured baseline, dated the same day

**Location:** whole of A1. Target file: `_bmad-output/implementation-artifacts/g3-benchmark-artifact.md`
(Artifact ID `G3-SN-2026-07-31-preliminary-01`, evidence date **2026-08-02** — same date as A1).

**Measured numbers A1 could have cited but did not (all verified in repo):**

| Item | Value | Source line |
| --- | --- | --- |
| YOLO-World v2, COCO-80 exact-label subset macro F1 | **`0.2233`** | g3:136, 150, 163 |
| YOLO-World v2 qualitative | *"it found some carrots/broccoli but also false food labels"* | g3:136 |
| YOLO-World v2 runtime | 640×640 ONNX, p95 `581 ms`, PSS `245 411 KB` | g3:136 |
| MobileSAM runtime | p95 `2 060 ms`, PSS `729 305 KB` | g3:135 |
| Android device | Honor DNY-NX9, Android 16 / API 36, `arm64-v8a`, ORT `1.28.0` CPU | g3:175 |
| Android Food-101 latency | init `2 611 ms`; p50 `182` / p95 `279` / max `334 ms`; PSS `27 520 → 273 547 KB` | g3:175 |
| iOS device | iPhone 14 Pro, iOS 26.5.2, `arm64e`, ORT ObjC `1.27.0`, XNNPACK, 2 threads | g3:176 |
| iOS Food-101 latency | init `560 ms`; p50 `83` / p95 `85` / max `85 ms` | g3:176 |
| **Existing latency budget** | **p95 ≤ 100 ms; max ≤ 150 ms after warm-up** → Android **fail** (279/334), iOS pass (85/85) | g3:259 |
| Pinned model | `swin-finetuned-food101-matmul-int8.onnx`, SHA-256 `9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df` | g3 "Candidate and semantic contract" |

**Why it blocks:** A1:579 states *"Threshold values should be set from a baseline
run and product risk tolerance, not invented before the first slice report."*
A baseline run exists, budgets exist, and the Android path already **fails**
one of them. A1 contains **zero measured numbers** across 6 860 words.

Two further consequences:
- The `carrot` row in A1's decision table (A1:703), which appears with no setup,
  is traceable: `carrot` appears at lines 31, 39, 73, 81, 132, 140 and `broccoli`
  at 53, 60 of `tools/g3-benchmark/meal-benchmark-manifest.json`. A1 does not cite it.
- A1:666 files open-vocabulary segmentation as a *longer-term* track, while the
  project has **already benchmarked YOLO-World v2**, an open-vocabulary detector.

**Fix:** cite g3 as the baseline in A1 §11 and in R2; restate "thresholds are not
yet set" so it applies only to the fusion thresholds that genuinely do not exist,
not to the device budgets that already do.

---

### B3 — Self-contradiction on subagent evidence, touching the headline recommendation

**Locations:** A1:100, A1:186–188, A1:585.

| Line | Text | Implication |
| --- | --- | --- |
| 100 | "Parallel specialist review by independent subagents" — listed under **Research Methodology** | claimed as executed method |
| 188 | "The four available subagent slots were occupied… new research agents could not be spawned. **No old-agent output is being represented as evidence for this report.**" | not executed, nothing used |
| 585 | "This research combined: … and **an independent subagent synthesis that recommended calibrated two-channel late fusion, top-k composition support, and claim-specific gates**." | the headline recommendation is attributed to that source |

Line 585 attributes the report's central recommendation to a source line 188
declares unused. Meanwhile frontmatter carries `stepsCompleted: [1, 2, 3, 4, 5, 6]`,
`lastStep: 6`, and A1:726 declares `Research Status: complete`.

**Fix:** pick one. Either the synthesis was used — then cite it as a real input
with its provenance and drop line 188's disclaimer — or it was not, and line 585
must be corrected and the methodology bullet at line 100 removed. A declared
methodology pillar that did not run should not co-exist with `complete`.

---

### B4 — Evidence-state enum exists in three incompatible redactions

| Location | Naming | Set |
| --- | --- | --- |
| A1:49–53 (normative table) | `SCREAMING_SNAKE` | `VISUAL_DETECTED`, `COMPOSITION_PLAUSIBLE`, `VISUAL_PROPOSAL_LOW_CONFIDENCE`, `REJECTED_ARTIFACT`, `UNKNOWN_OR_ABSTAINED` |
| A1:179 | `lower_snake` | `detected`, `dish_expected`, `not_localized`, `rejected_artifact`, `unknown` |
| A1:422 (R-stage 2) | `camelCase` | `visualDetected`, `compositionPlausible`, `notLocalized`, `unknown` |

Three notations, three different sets. `dish_expected` and `COMPOSITION_PLAUSIBLE`
are the same concept under two names; `not_localized` / `notLocalized` is a sixth
state that the normative table never defines; A1:422 silently drops
`VISUAL_PROPOSAL_LOW_CONFIDENCE` and `REJECTED_ARTIFACT`. Scattered later uses
mix redactions again (A1:292 `visualDetected`; A1:341, 443, 468, 548, 658 `SCREAMING_SNAKE`).

**Why it blocks:** A1's own thesis is R1 "Freeze semantics before weights". A
document that cannot hold its own enum stable for 400 lines cannot be the source
of a frozen contract.

**Fix:** one table, one casing, one set; every other mention becomes a reference
to it. Reconcile against B1 before choosing names.

**Resolution (follow-up):** The legacy lower-snake and camelCase state lists in
the Technology Adoption Trends and Evidence-contract release sections were
removed. Those sections now reference the canonical evidence-state table, the
existing v2 visual result/provenance, and additive optional composition/review
fields only; no parallel production enum is instructed.

---

## 5. Major findings

### M1 — The core fusion formula is underspecified, and its key input has no source

**Location:** A1:538–544.

```text
compositionSupport[z] = sum(pDish[d] * recipeSupport[z, d] for d in topK)
```

- **No renormalisation** over retained probability mass. When top-k covers 40 %
  of `pDish` mass versus 95 %, `compositionSupport` is systematically deflated in
  the first case. A1 never says whether to divide by `sum(pDish[d] for d in topK)`.
- **`k` is never given** — no integer, no mass threshold, no tie-break rule.
  A1:536 says only "a small capped top-k mixture".
- **`recipeSupport[z, d]` is never defined** — binary membership? co-occurrence
  frequency? normalised?
- **`recipeSupport` has no data source anywhere in A1.** This is the sharpest
  gap: A1:636 demands priors be "data-driven composition support" rather than
  hard-coded, but never says where dish→ingredient relations come from.
  Recipe1M / Recipe1M+ — the obvious candidate — is absent from the source list.
  No acquisition plan, no licence review, no coverage estimate.

R4 is the one place a concrete spec was required, and it is the least specified.

---

### M2 — The recommendation renames the hard prior rather than removing it

| Line | Statement |
| --- | --- |
| A1:33, 636 | a prior "must never delete a plausible candidate from the audit/review contract" |
| A1:131 | "Hard filtering should be reserved for suppressing known detector artifacts from the primary visual overlay" |
| A1:52 | `REJECTED_ARTIFACT` = "proposal conflicts with calibrated visual/**ontology** policy" |

So the ontology *may* suppress a candidate from the overlay after all. For the
motivating case A1 never answers the decisive question: is `orange` rejected
because its calibrated visual score is low, or because `pork_chop`'s recipe does
not contain orange? If the latter, this is the same whitelist with new labels.

**Fix:** state explicitly when ontology may contribute to *rejection* versus only
to *support*, and re-derive the `orange` outcome from that rule.

---

### M3 — R3 is infeasible as written and blocks R4 and R6

A1:534 requires dish and detector calibration fitted "on a held-out,
deployment-like set". A1:692 (Limitations) states no corpus was collected and
public datasets have domain/cuisine limits with components that "may not be
visually observable in a single photo". The set R3 depends on does not exist,
and there is no plan, owner, budget or timeline to create it. R4 and R6 both
depend on R3.

---

### M4 — `COMPOSITION_PLAUSIBLE` is unbounded

top-k dish mixture × a recipe relation table means essentially every ingredient
of every plausible dish becomes a composition candidate — salt, pepper, oil,
butter for each hypothesis. A1 specifies no threshold on `compositionSupport`,
no cap, no dedup, no ranking cutoff. This directly conflicts with the
product-wide **low-friction interaction** invariant in `AGENTS.md`
("defaults are visible, sourced, editable, reversible, and session-scoped").

---

### M5 — The most likely user-facing regression is never analysed: the empty overlay

Under R5 (A1:548) only `VISUAL_DETECTED` reaches the primary overlay. For A1's
own motivating example that yields **nothing on the photo**: `pork_chop` is a
dish hypothesis, `orange` is rejected, `broccoli` is composition-only. The risk
table (A1:511–520, 8 rows) contains no row for "new policy surfaces fewer claims
and reads as a broken app". Given B2's measured macro F1 of `0.2233`, the empty
overlay is not a corner case — it is the expected common outcome.

---

### M6 — Nutrition binding is suspended with no interim rule

A1:516 — "no nutrition binding without review". The product exists to produce
nutrition numbers. If `COMPOSITION_PLAUSIBLE` items are excluded from totals
until reviewed, calorie totals change materially. A1 gives no estimate of that
shift and no rule for what the total displays meanwhile.

---

### M7 — Küppers is listed as a source but never used, and it contradicts the implied method

`Multivariate Confidence Calibration for Object Detection` appears in the source
list at A1:679 and **nowhere in the body**. It is the only cited work about
calibrating *detection* (position- and size-dependent), and it argues against the
implied "temperature-scale both channels" — Guo et al. (A1:60, 153) is a
*classification* result. A source in the list but absent from the argument is a
verification smell in a document claiming multi-source validation.

---

### M8 — `openSetRisk` is a named variable with no mechanism

It appears in the fusion formula (A1:543). A1 defines an unknown/non-food slice
(A1:456) and the `UNKNOWN_OR_ABSTAINED` state (A1:53), but never says how
open-set risk is computed — MSP, energy, ODIN, or a separate non-food classifier.

---

### M9 — Currency gap for a 2026 document

Outside tool documentation, nothing cited is newer than ~2022. Specifically missing:

- **Conformal prediction** — the direct modern answer to A1's own risk–coverage
  goal, absent entirely while A1 spends significant space on selective prediction.
- **SAM2 / modern open-vocabulary detection** — while A1:666 files open-vocabulary
  as *longer term* and the project has already benchmarked YOLO-World v2 (see B2).
- **MobileSAM** is load-bearing in A1's architecture (A1:222, 369) with **no
  citation and no justification** — although it is measured in g3:135.

---

### M10 — Roughly 2× structural redundancy; the table of contents does not describe the document

Verified heading order in A1:

```
23  Research Overview          82  Technical Research Scope Confirmation
29  Executive Summary         107  Technology Stack Analysis
65  Table of Contents         190  Integration Patterns Analysis
                              296  Architectural Patterns and Design
                              415  Implementation Approaches and Technology Adoption
                              526  Technical Research Recommendations
581  ## 1. …  →  694  ## 12.  (the numbered report, which restates the above)
```

The TOC lists 12 numbered sections; six large **unnumbered** sections sit between
the TOC and section 1, and sections 1–12 largely restate them. The TOC has no
anchors, so it cannot navigate. Concrete duplication:

- Ablation ladder appears **4×**: A1:355–361, 471, 532, 599–606.
- Event sequence appears **2× with a name conflict**: A1:278 `DishCandidatesReady`
  versus A1:375 `DishReady` — two names for one event in a contract document.
- Security guidance appears **3×**: A1:288–294, 382–388, 628–632.

---

### M11 — Inputs are not reproducible

- Frontmatter `inputDocuments: []`.
- A1:25 and A1:43 refer to "the screenshot's `orange`" — no screenshot exists in
  the artifact, and A3 confirms the assets directory holds no images.
- No path to the device trace, no commit, no fixture id.
- `carrot` enters at A1:703 with no setup (traceable to the manifest — see B2 — but uncited).
- A1:250 ("`orange` must not be silently interpreted as carrot") presupposes an
  existing alias mapping that is never established anywhere in A1.

---

### M12 — The roadmap is not BMAD handoff-ready

R1–R6 (A1:530–550) plus Phases A–E (A1:644–662): no owners, no estimates, no
declared dependencies, no numeric exit criteria. Of the nine success metrics
(A1:569–577) only #9 ("zero stale-result or lifecycle regressions") is
falsifiable as stated. Roles are listed (A1:493–501) but never mapped to any R or Phase.

---

### M13 — No mapping onto the project's module structure

`AGENTS.md` fixes mobile module groups as `apps`, `core`, `domain`, `data`,
`inference`, `platform`, `feature`, `testing`, `evaluation`, and requires domain
to stay pure KMP. A1 introduces `DishModelPort`, `ComponentModelPort`,
`OntologyPort`, `CalibrationPort`, `LateFusionEngine`, `EvidenceContract`
(A1:311–324) with no mapping to any of them. The Python layer and
"offline Parquet/JSONL evaluation records" (A1:145) are not placed relative to
the product-root separation rule (`mobile/` vs `backend/mobile-api/` vs `tools/`).

---

## 6. Presentation and assets findings

### H1 — A3 is factually wrong

A3 states the report "uses inline, accessible SVG diagrams". A2 contains
**zero** `<svg>` and **zero** `<img>`/`<picture>` elements (verified, §3.4). All
diagrams are `div` structures with unicode arrows (`.pipeline`, `.funnel`,
`.roadmap`, `.ablation`). A3 also never says what the directory is for, what to
put in it, or why it is empty.

### H2 — Six `aria-label`s are silently ignored

`aria-label` on an element with no implicit role is not exposed by most assistive
technology. Affected, all bare `<div>`: A2:491 and A2:498 (`.pipeline` ×2),
A2:468 (`.funnel`), A2:539 (`.ablation`), A2:546 (`.metric-grid`),
A2:526 (`.example-strip`). The author believes these are labelled; they are not.
Only A2:415 `<aside class="hero-card">` works, because `aside` carries a role.
**Fix:** `role="group"` + label, or `<figure>`/`<figcaption>`, or a real heading.

### H3 — Global arrow-key hijack breaks scrolling

A2:633–640 calls `preventDefault()` on `ArrowUp/Down/Left/Right`. The guard at
A2:634 only exempts events *targeted at* `input, textarea, select, button, a,
summary`. When focus rests on `body` — the default — arrow-key scrolling is dead
for keyboard and screen-reader users. WCAG 2.1.1.

### H4 — `lang="ru"` with substantial untagged English

A2:2 declares `lang="ru"`. Untagged English strings include "Dish candidate",
"Detector proposal", "Plausible, but missed", "Evidence contract",
"show, separate, or abstain", "Definition of Done", and the ablation results
"baseline / reject / transitional / compare / recommended". A screen reader will
apply Russian phonetics. WCAG 3.1.2 Language of Parts, Level AA.

### H5 — Three contrast failures (computed)

Foreground `--muted` `#62717a` (relative luminance 0.1570) on the `.failure` card
backgrounds:

| Rule | Background | Luminance | Contrast | AA (4.5:1) |
| --- | --- | --- | --- | --- |
| `.failure.miss` (A2:276) | `#dbe9f4` | 0.7987 | **4.10** | fail |
| `.failure.false` (A2:277) | `#ffe3d4` | 0.8090 | **4.15** | fail |
| `.failure.prior` (A2:278) | `#dcefeb` | 0.8297 | **4.25** | fail |

Applies to `.failure p` at `.94rem` (A2:275) — normal-size text, so 4.5:1 is
required. Checked and **passing**: `.example p` on `#f8fbfa` (4.87),
`.definition p` `#315e59` on `#dcefeb` (6.12).

### H6 — Mobile breakpoint strips table semantics

A2:371 sets `display: block` on `.state-table` under `max-width: 680px`. This
drops the table role in several AT engines, on the page's most information-dense
element. It is also unnecessary: the parent `.panel` already carries
`overflow-x: auto` (A2:513).

### H7 — Placeholder shipped in the hero

A2:419: `<div class="confidence">dish</div>`. The element is styled as a
confidence badge (`--orange`, 1.3rem, weight 880) and contains the literal word
"dish" where a number belongs.

### H8 — A provenance claim lives in the stylesheet

A2:150: `.hero-card::before { content: "observed on device"; }`. A claim about
where the data came from is CSS generated content — not translatable, not
selectable, unreliably exposed to AT — and unsupported per M11.

### H9 — Deck drifted from the source on normative lists

- **R6 is missing.** A2:561–567 shows R1–R5 only; R6 "Promote only on evidence"
  (A1:550) — the promotion gate itself — is dropped from the roadmap. Partially
  recovered in the "Definition of Done" box (A2:568–571), but absent from the
  roadmap stakeholders will read.
- **Required slices do not match.** A2:552 drops the baseline slice
  "clear single-component meals" (A1:451) and splits raw/cooked and cuisine into
  two entries. The list is presented as mandatory but is not A1's mandatory list.
- **Deck is more specific than its source.** A2:549 names **ECE**; A1 says only
  "calibration error" and never uses the term.

### H10 — Typography untested against its own fallback

`font-family: Inter, …` (A2:34), but Inter is never loaded — no `@font-face`, by
design offline. Rendering therefore depends on whether the viewer happens to have
Inter installed. Combined with non-standard weights `880 / 850 / 780 / 750`
(A2:121, 61, 205, 349), a non-variable system fallback snaps these to 700/900 and
the intended hierarchy collapses.

### H11 — Progress indicator desynchronised from navigation; stray inline styles

`sections` collects all 7 `main > section[id]` (A2:616) including `#friction` and
`#sources`, while the TOC lists 5 (A2:394–398). `setActive('top')` (A2:631) yields
1/7 ≈ 14 % on first paint. Separately, inline `style` attributes at A2:457, 484,
498, 513 break an otherwise clean stylesheet, and A2:484's
`style="background: #1e4150"` duplicates the already-declared `--navy-2`.

---

## 7. Recommended order of work

1. **B1 + B2 together.** Rewrite R1 as a delta against `inference-evidence/v2`
   and cite `g3-benchmark-artifact.md` as the baseline in the same pass. One edit
   clears both blockers and makes A1's threshold claim true.
2. **B4.** Collapse the enum to one table, one casing — reconciled against
   whatever B1 decides.
3. **B3.** Resolve the subagent contradiction and re-derive `Research Status`.
4. **M2, then M1.** Answer "may ontology reject, or only support?" before
   specifying the formula — the answer changes what `recipeSupport` must be.
5. **M5 + M6.** Add the empty-overlay regression and the interim nutrition rule;
   these are the product decisions the roadmap currently assumes away.
6. **M3, M4, M12.** Make R3's corpus a real work item with an owner; bound
   `COMPOSITION_PLAUSIBLE`; add owners and exit criteria.
7. **H1, H7, H9.** Cheap and embarrassing: fix A3, remove the `dish` placeholder,
   restore R6 and the baseline slice to the deck.
8. **H2–H6.** Accessibility pass. H3 and H5 are the substantive ones.
9. **M7–M11, M13, H8, H10, H11.** Quality and rigour pass.

## 8. What not to change

- The central thesis (separate visual evidence from composition prior). Correct.
- The external source list. Every entry checked came back accurate (§3.1) —
  do not "clean up" citations without re-verification.
- The relative link from A2 to A1 (`../../planning-artifacts/research/…`) resolves
  correctly from the presentation directory.
- The print stylesheet (A2:378–387), `:focus-visible` handling (A2:38–41), and the
  visually-hidden `<caption>` (A2:303) are done carefully.

---

*Review performed read-only. No file in the `d550` worktree was modified; this
handoff is the only artifact produced.*
