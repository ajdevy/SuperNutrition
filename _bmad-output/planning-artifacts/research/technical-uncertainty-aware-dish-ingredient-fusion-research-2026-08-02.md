---
stepsCompleted: [1, 2, 3, 4, 5, 6]
inputDocuments: []
workflowType: 'research'
lastStep: 6
research_type: 'technical'
research_topic: 'uncertainty-aware dish and ingredient fusion for food photo analysis'
research_goals: 'avoid brittle hard filtering, preserve plausible ingredients, distinguish visual evidence from dish priors, and define a benchmarkable mobile-safe strategy'
user_name: 'Anastasia'
date: '2026-08-02'
web_research_enabled: true
source_verification: true
---

# Uncertainty-Aware Dish and Ingredient Fusion for Mobile Food Photo Analysis

**Date:** 2026-08-02
**Author:** Anastasia
**Research Type:** technical

---

## Research Overview

This research evaluates how SuperNutrition should combine dish identification with visible ingredient/component evidence on Android and iOS. The motivating failure is a `pork_chop` dish candidate accompanied by a detector proposal such as `orange`, while a plausible ingredient such as broccoli may be present in the dish but not localized by the current detector. The central conclusion is that a dish prior is useful context, but a hard whitelist is not valid evidence of presence or absence.

The recommended architecture is a calibrated two-channel, top-k late-fusion pipeline: a dish classifier supplies a calibrated distribution over hypotheses; a component detector/segmenter supplies independently calibrated visual evidence; an ontology supplies composition support; and a deterministic fusion layer emits separate evidence states. The rollout should begin in shadow mode with fixed public-data and conflict-slice benchmarks, then graduate only when visible-claim precision, calibration, correction burden, and mobile runtime gates are met. The full evidence, implementation roadmap, and source documentation follow below.

## Executive Summary

### Decision

Replace hard dish-prior filtering with calibrated late fusion. A prior may rerank or support a candidate, but it must never convert a recipe expectation into a visually detected ingredient and must never delete a plausible candidate from the audit/review contract.

### Why the Current Behavior Is Flaky

The current pipeline conflates three different situations:

1. an ingredient is absent;
2. an ingredient is present but hidden, blended, occluded, small, or missed by the detector; and
3. a detector produced a false proposal.

The screenshot’s `orange` is a detector proposal, not proof that orange is in the meal. Likewise, missing broccoli is not proof that broccoli is absent. Food segmentation research identifies overlap and appearance variation as core localization challenges, and the Nutrition5k/FoodSeg103/UECFoodPix datasets separate image labels, component labels, masks, and mass evidence rather than treating a dish name as an ingredient mask ([FoodSeg103](https://openaccess.thecvf.com/content/CVPR2021/papers/Wu_A_Large-Scale_Benchmark_for_Food_Image_Segmentation_CVPR_2021_paper.pdf), [Nutrition5k](https://github.com/google-research-datasets/Nutrition5k), [UECFoodPix](https://mm.cs.uec.ac.jp/uecfoodpix/)).

### Recommended Result Semantics

| State | Meaning | Primary photo overlay | Candidate/review list |
|---|---|---|---|
| `VISUAL_DETECTED` | localized visual evidence passes calibrated precision gate | show | show as detected |
| `COMPOSITION_PLAUSIBLE` | dish/ontology support exists, no adequate localization | do not show as a box | show as “plausible, not visually confirmed” |
| `VISUAL_PROPOSAL_LOW_CONFIDENCE` | detector emitted a weak proposal | hide by default | retain for debug/evaluation |
| `REJECTED_ARTIFACT` | proposal conflicts with calibrated visual/ontology policy | hide | retain with reason |
| `UNKNOWN_OR_ABSTAINED` | insufficient evidence, unknown dish, poor image, or high disagreement | no forced claim | request review or more input |

For the current example, `pork_chop` is a dish hypothesis; broccoli can be `COMPOSITION_PLAUSIBLE` even when it is not visually detected; orange should remain an internal detector proposal unless its visual gate passes. A single overloaded confidence value is not acceptable: store `visualConfidence` and `compositionSupport` separately.

### Strategic Recommendations

- Keep top-k dish hypotheses and compute composition support as a mixture, instead of conditioning only on top-1.
- Calibrate dish and detector scores independently; raw Food-101 and COCO scores must not be added directly ([On Calibration of Modern Neural Networks](https://proceedings.mlr.press/v70/guo17a.html)).
- Add claim-specific abstention and risk–coverage evaluation ([SelectiveNet](https://proceedings.mlr.press/v97/geifman19a.html)).
- Preserve raw outputs, provenance, model/calibration versions, and rejection reasons in replayable fixtures.
- Roll out through shadow mode and ablation benchmarks before changing the user-facing policy.

## Table of Contents

1. Technical Research Introduction and Methodology
2. Technical Landscape and Architecture Analysis
3. Implementation Approaches and Best Practices
4. Technology Stack Evolution and Current Trends
5. Integration and Interoperability Patterns
6. Performance and Scalability Analysis
7. Security and Compliance Considerations
8. Strategic Technical Recommendations
9. Implementation Roadmap and Risk Assessment
10. Future Technical Outlook and Innovation Opportunities
11. Technical Research Methodology and Source Verification
12. Technical Appendices and Reference Materials

---

## Technical Research Scope Confirmation

**Research Topic:** uncertainty-aware dish and ingredient fusion for food photo analysis
**Research Goals:** avoid brittle hard filtering, preserve plausible ingredients, distinguish visual evidence from dish priors, and define a benchmarkable mobile-safe strategy

**Technical Research Scope:**

- Architecture Analysis - dish/ingredient candidate fusion, calibration, open-set handling, and abstention
- Implementation Approaches - soft priors, score fusion, reranking, thresholds, and evidence states
- Technology Stack - mobile-capable vision models, lightweight post-processing, and on-device constraints
- Integration Patterns - dish classifier, component detector/segmenter, ingredient ontology, and UI evidence contract
- Performance Considerations - latency, memory, determinism, and benchmark slices for Android/iOS

**Research Methodology:**

- Current web data with rigorous primary-source verification
- Multi-source validation for critical technical claims
- Confidence levels for uncertain technical information
- Parallel specialist review by independent subagents
- No production code changes until the recommendation is accepted

**Scope Confirmed:** 2026-08-02

<!-- Content will be appended sequentially through research workflow steps -->

## Current SuperNutrition Contract and Baseline

This recommendation is a delta against the current SuperNutrition artifacts, not
a proposal for a second production evidence contract.

### Existing `inference-evidence/v2` is the production boundary

The current v2 contract already defines the production result shape and
provenance boundary:

- `InferenceV2Result` returns `Components`, `Abstention`, or typed `Failure`;
- `VisibleComponent.confidence` is the existing `CalibratedConfidence` field
  for a visual component claim;
- `ComponentVisibility` currently means `VISIBLE` or `PARTIALLY_OCCLUDED`;
- `ComponentAmbiguity` and `V2AbstentionReason` already cover identity,
  mixed-content, image-quality, and unsupported-content outcomes; and
- `InferenceV2Provenance` already carries route, engine, model, taxonomy,
  calibration, and contract versions.

The fusion proposal must therefore not introduce a parallel production
`evidenceState` enum. `VISUAL_DETECTED` maps to a v2 `VisibleComponent` with a
calibrated `confidence` and the appropriate `ComponentVisibility`. It must not
be represented by a dish prior alone. `COMPOSITION_PLAUSIBLE`,
`VISUAL_PROPOSAL_LOW_CONFIDENCE`, and `REJECTED_ARTIFACT` are fusion/review
states for candidates that are not necessarily v2 visual components; they must
stay out of the primary visual overlay and retain v2 provenance when carried in
debug or review evidence.

`compositionSupport` is therefore a separate fusion feature, not a replacement
for `CalibratedConfidence`. If composition-only candidates must become part of
the canonical result, the implementation should propose an additive optional
v2-compatible extension (for example, a separate composition-candidate
collection) and update fixtures first. It must not add a required field or
reinterpret `ComponentVisibility`/`V2AbstentionReason`; the current compatibility
policy is `unknown_fields = "ignore"`, `missing_required_fields = "reject"`,
and incompatible changes require a new major release.

Authoritative references: [`contracts/inference/v2/schema.toml`](../../../contracts/inference/v2/schema.toml),
[`InferenceV2Models.kt`](../../../mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/InferenceV2Models.kt),
and [`InferenceV2Contract.kt`](../../../mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/InferenceV2Contract.kt).

### Existing G-3 baseline is the starting point for fusion evaluation

The project already has a measured baseline dated 2026-08-02. The current v2
runtime proves only single-dish Food-101 semantics: five exact semantic
fixtures pass at the declared `0.55` floor, while visible ingredients remain
`unsupported` and plated mass remains `not-evaluable`. The component/mass G-3
gate and production activation remain open.

| Evidence layer | Current measured result | Interpretation |
|---|---|---|
| Android Food-101 semantic runner | Honor DNY-NX9, CPU; p50/p95/max `182/279/334 ms`; PSS `27,520 -> 273,547 KB` | semantic baseline; Android misses the proposed p95/max runtime and memory comparison budgets |
| iOS Food-101 semantic runner | iPhone 14 Pro, XNNPACK; p50/p95/max `83/85/85 ms`; resident `261,360 -> 253,008 KB` | latency comparison passes; memory comparison budget still fails |
| YOLO-World v2 candidate screen | exact visible-component macro F1 `0.2233`; p95 `670 ms`; PSS `244,432 KB` | research candidate only; COCO-80 vocabulary and false positives prevent production selection |
| MobileSAM candidate screen | p95 `2,003 ms`; PSS `733,237 KB` | class-agnostic masks, no ingredient identity or mass head; research candidate only |

The current comparison targets are p95 `<= 100 ms`, max `<= 150 ms`, and
`<= 160 MB` process memory. These are existing G-3 comparison budgets, not new
fusion thresholds. Fusion-specific score thresholds and risk-coverage targets
still require the ablation/calibration run proposed below.

Authoritative references: [`g3-benchmark-artifact.md`](../../implementation-artifacts/g3-benchmark-artifact.md),
[`android-component-mass-benchmark-2026-08-02.json`](../../../ingredient-weight-research/android-component-mass-benchmark-2026-08-02.json),
and [`android-candidate-device-evidence-2026-08-02.json`](../../../ingredient-weight-research/android-candidate-device-evidence-2026-08-02.json).

## Technology Stack Analysis

### Programming Languages

The practical stack is intentionally split between an offline research/training layer and deterministic on-device inference. Python remains the appropriate language for dataset preparation, calibration fitting, benchmark evaluation, and model export. Kotlin/Java on Android and Swift/Kotlin/Native on iOS should remain thin host layers around a shared inference contract; they should not contain dish-specific nutrition rules.

The current PoC's Java Android host is suitable for a research runner, but the production direction should keep model outputs typed and platform-neutral. ONNX is a useful interchange boundary because the same exported model family can be evaluated in Python and hosted on Android/iOS through ONNX Runtime Mobile. Confidence values must be treated as model scores until calibrated on a held-out set, rather than as user-facing probabilities.

_Popular languages:_ Python for data/model work; Kotlin/Java and Swift/Kotlin Multiplatform for mobile orchestration.

_Performance characteristics:_ keep score fusion, ontology lookup, calibration transforms, and evidence-state construction lightweight and deterministic on-device; do not add a second large model just to implement priors.

_Source:_ [ONNX Runtime mobile deployment documentation](https://onnxruntime.ai/docs/tutorials/mobile/), [ONNX Runtime Mobile guide](https://onnxruntime.ai/docs/get-started/with-mobile.html).

### Development Frameworks and Libraries

The research stack should compare three separable model roles rather than force one model to answer every question:

1. a dish classifier producing top-k candidates;
2. a multi-label ingredient/component recognizer or segmentation model producing visual evidence; and
3. a small fusion/evidence layer that combines calibrated scores with dish–ingredient relations.

FoodSeg103's benchmark work shows why ingredient segmentation is a distinct problem: ingredients overlap, vary in appearance, and require pixel-level labels. The zero-shot ingredient-recognition literature also supports using relational information between a dish and ingredients, but that relationship is a prior or retrieval signal, not proof that an ingredient is visible in the current image.

The recommended near-term implementation is soft fusion: retain all detector candidates, add a bounded dish-prior score, and emit separate evidence states. Hard filtering should be reserved for suppressing known detector artifacts from the primary visual overlay, never for deleting candidates from the review/debug contract.

_Major frameworks/libraries:_ ONNX Runtime Mobile for shared inference; a FoodSeg103-style segmentation or multi-label ingredient model for future component evidence; a small deterministic Kotlin/Java/KMP fusion layer for current PoC integration.

_Source:_ [FoodSeg103 benchmark paper](https://openaccess.thecvf.com/content/CVPR2021/papers/Wu_A_Large-Scale_Benchmark_for_Food_Image_Segmentation_CVPR_2021_paper.pdf), [Zero-Shot Ingredient Recognition by Multi-Relational Graph Convolutional Network](https://ojs.aaai.org/index.php/AAAI/article/view/6626), [Food Ingredients Recognition through Multi-label Learning](https://arxiv.org/abs/2210.14147).

### Database and Storage Technologies

The inference path does not need a runtime database for the model decision. It needs versioned, immutable assets: model files, label maps, ontology/prior manifests, calibration parameters, and evidence-contract version. These should be bundled or downloaded through an explicit versioned model package, not regenerated from UI state.

For research, dataset storage must preserve the distinction between image-level dish labels, ingredient presence labels, pixel/region masks, and mass annotations. Nutrition5k is especially useful for component-level and mass-aware evaluation because its public release includes 5,006 plates, RGB-D/side-angle capture, ingredient lists, per-ingredient mass, and an official split. It is not a complete proxy for user meals: the maintainers explicitly describe cafeteria and geographic coverage limitations.

FoodSeg103 and UECFoodPixComplete are better suited to testing ingredient localization than Nutrition5k alone. UECFoodPixComplete provides 9,000 training and 1,000 test images with manually created pixel masks, while FoodSeg103 focuses on fine-grained ingredient-level masks. Dataset adapters should normalize labels into a canonical ontology and preserve aliases instead of silently treating dish labels as ingredient labels.

_Recommended storage:_ local immutable model/manifest bundle for the PoC; offline Parquet/JSONL evaluation records with image id, dish gold labels, ingredient gold labels, region/mask gold, detector outputs, calibrated scores, evidence state, and abstention reason.

_Source:_ [Nutrition5k official dataset repository](https://github.com/google-research-datasets/Nutrition5k), [Nutrition5k CVPR paper](https://openaccess.thecvf.com/content/CVPR2021/papers/Thames_Nutrition5k_Towards_Automatic_Nutritional_Understanding_of_Generic_Food_CVPR_2021_paper.pdf), [UECFoodPix/UECFoodPixComplete official site](https://mm.cs.uec.ac.jp/uecfoodpix/).

### Development Tools and Platforms

The benchmark should run in Python with reproducible manifests and fixed model versions. Mobile verification should run the same exported model and fusion manifest on the connected Android device and the iOS host when available. Required measurements are not only top-1 dish accuracy: ingredient multilabel precision/recall, per-class calibration, false-positive suppression, region IoU where masks exist, risk–coverage for abstention, latency, peak memory, and APK/model size.

Calibration must be fitted on held-out data. Guo et al. show that modern neural networks can be poorly calibrated and that temperature scaling is an effective post-processing baseline. Selective prediction literature frames the relevant tradeoff as risk versus coverage: the system should be allowed to say “not enough visual evidence” instead of turning a dish prior into a detection claim.

_Testing tools:_ unit tests for fusion/ontology branches; offline benchmark scripts; golden JSON fixtures; Android instrumentation for evidence semantics and latency; device smoke tests for model loading and lifecycle.

_Source:_ [On Calibration of Modern Neural Networks](https://proceedings.mlr.press/v70/guo17a.html), [SelectiveNet: A Deep Neural Network with an Integrated Reject Option](https://proceedings.mlr.press/v97/geifman19a.html), [Selective Classification for Deep Neural Networks](https://proceedings.neurips.cc/paper/7073-selective-classification-for-deep-neural-networks.pdf).

### Cloud Infrastructure and Deployment

Cloud inference is not required for the local-first PoC and would make this specific failure harder to debug because model output, calibration, and UI evidence could diverge by deployment. Cloud infrastructure is still useful for offline benchmark jobs, dataset conversion, calibration fitting, and artifact storage. The mobile runtime should load a pinned model plus pinned calibration/ontology manifest and produce deterministic evidence states offline.

ONNX Runtime's mobile documentation notes that CPU is the consistent baseline, while Android NNAPI/XNNPACK and iOS CoreML/XNNPACK are device/model-dependent. The project should therefore gate acceleration on measured latency and correctness, not assume that an accelerator improves the full pipeline; partitioning unsupported operators can degrade performance.

_Deployment recommendation:_ offline/cloud for training and evaluation; versioned on-device package for inference; no server-only fallback hidden behind the same confidence label.

_Source:_ [ONNX Runtime mobile deployment and execution providers](https://onnxruntime.ai/docs/tutorials/mobile/).

### Technology Adoption Trends

The research direction is moving from single-label dish classification toward fine-grained ingredient segmentation, multi-label recognition, relational/zero-shot reasoning, and selective outputs. The important architectural trend for SuperNutrition is not “pick a bigger classifier”; it is to preserve multiple evidence channels and expose their disagreement.

The safe adoption pattern is therefore:

- retain top-k dish candidates rather than condition only on top-1;
- calibrate dish and component scores separately;
- use dish–ingredient relations as a soft ranking feature;
- preserve raw detector outputs for audit/debug;
- emit `detected`, `dish_expected`, `not_localized`, `rejected_artifact`, or `unknown` rather than collapsing all states to “ingredient”; and
- measure risk–coverage before enabling automatic nutrition binding.

_Confidence:_ high for the separation of visual evidence and priors; medium for the exact fusion weights because they require a project-specific calibration set.

_Source:_ [FoodSeg103 benchmark paper](https://openaccess.thecvf.com/content/CVPR2021/papers/Wu_A_Large-Scale_Benchmark_for_Food_Image_Segmentation_CVPR_2021_paper.pdf), [Zero-Shot Ingredient Recognition](https://ojs.aaai.org/index.php/AAAI/article/view/6626), [On Calibration of Modern Neural Networks](https://proceedings.mlr.press/v70/guo17a.html), [SelectiveNet](https://proceedings.mlr.press/v97/geifman19a.html).

### Research Operations Note

The requested parallel subagent research was attempted after scope confirmation. The four available subagent slots were occupied by pre-existing tasks; three completed old tasks and new research agents could not be spawned. No old-agent output is being represented as evidence for this report. The findings above are based on live primary-source research and will be reconciled with independent agent findings if slots become available.

## Integration Patterns Analysis

### API Design Patterns

The core integration should be a local, versioned analysis contract rather than a network API. A request contains an image reference plus an optional grid prompt and model-package version. A result contains independent dish candidates, raw visual proposals, composition-support candidates, fused ranking, evidence state, calibration provenance, and warnings. The contract must preserve the distinction between “the detector emitted this label” and “the dish ontology says this label is plausible.”

Recommended shape:

```text
PhotoAnalysisRequest
  imageRef, gridPrompt?, modelManifestVersion

PhotoAnalysisResult
  dishCandidates[]
  visualProposals[]
  compositionCandidates[]
  fusedCandidates[]
  evidenceStates[]
  calibrationVersion
  abstentionReasons[]
```

The current `PocInferenceEngine` can implement this internally without adding a remote service. Its current `PocAnalysis` is a useful seam, but `IngredientSuggestion` should eventually carry separate `visualConfidence`, `compositionSupport`, `evidenceState`, `sourceModel`, and `calibrationVersion` fields instead of overloading one detector confidence.

_REST/GraphQL/RPC:_ unnecessary for the local-first path; if a backend is added later, expose the same immutable contract through a versioned API adapter rather than moving fusion rules into the server.

_Source:_ [JSON Schema Draft 2020-12](https://json-schema.org/specification), [Protocol Buffers language guide and compatibility guidance](https://protobuf.dev/programming-guides/editions/).

### Communication Protocols

The runtime communication is a local function boundary: image input to model sessions, model outputs to deterministic fusion, and fusion output to the presenter/UI. There is no need for WebSocket, message queue, or microservice choreography for a single photo analysis. The Android runner already uses an executor and a request id; the production equivalent should use a cancellable coroutine/Flow or platform task with stale-result suppression.

The model sessions should be treated as opaque adapters. Food-101 logits, COCO proposals, and MobileSAM masks must not leak directly into UI code. Each adapter translates its native output into a canonical intermediate record, then the fusion layer assigns evidence state. This makes Android and iOS interchangeable at the contract boundary even if their accelerator/runtime paths differ.

_Asynchronous rules:_ analysis requests are idempotent by request id; a newer photo or grid prompt invalidates older results; partial model failure yields a typed warning and does not silently upgrade a prior-only suggestion to visual evidence.

_Source:_ [ONNX Runtime mobile execution and platform providers](https://onnxruntime.ai/docs/tutorials/mobile/).

### Data Formats and Standards

Use typed Kotlin/KMP domain objects as the in-process contract and JSON fixtures for benchmark/evidence replay. TOML/JSON manifests are appropriate for model, ontology, and calibration metadata; every manifest needs a schema version, model digest, label-map digest, calibration version, and source-model identifiers.

JSON Schema Draft 2020-12 is a suitable external validation format for result fixtures and future backend interchange. Protocol Buffers is a reasonable later option for compact cross-platform or network transport, but it should not replace the human-readable JSON evidence artifacts used in debugging and benchmark review. Stable enum values and additive schema evolution are more important than serialization choice.

Provenance is part of the data model, not a UI string. The result should record which model produced a candidate, which calibration artifact transformed its score, which prior/ontology rule contributed composition support, and why a claim was suppressed or abstained. This follows the intent of the W3C PROV model: provenance describes the entities and activities involved in producing data and helps downstream users judge trust and integration.

_Required evidence fields:_ `source`, `visualConfidence`, `compositionSupport`, `evidenceState`, `localizedRegion?`, `calibrationVersion`, `modelManifestVersion`, `decisionReason`.

_Source:_ [JSON Schema specification](https://json-schema.org/specification), [Protocol Buffers schema evolution guide](https://protobuf.dev/programming-guides/editions/), [W3C PROV-DM](https://www.w3.org/TR/prov-dm/).

### System Interoperability Approaches

The interoperability boundary should be a canonical ingredient ontology, not a shared vocabulary copied from COCO or Food-101. Maintain separate namespaces for:

- dish labels (`food101:pork_chop`);
- detector labels (`coco:orange`, `coco:broccoli`);
- ingredient concepts (`ingredient:pork`, `ingredient:carrot`);
- recipe/composition relations (`recipe:pork_chop -> ingredient:pork`); and
- user-confirmed concepts (`user:confirmed:...`).

Adapters may map one namespace to another, but the mapping must be explicit and reversible in debug output. `orange` must not be silently interpreted as carrot; `pork_chop` must not be silently interpreted as a visually localized pork region. This prevents the exact class/ingredient mismatch observed on the device.

The integration pipeline should be:

```text
Food-101 adapter ─┐
COCO adapter ──────┼─> canonical evidence records ─> calibrated late fusion
MobileSAM adapter ┘                              └─> review/UI contract
```

_Confidence:_ high for separate namespaces and provenance; medium for the final ontology mapping because it needs product vocabulary and nutrition-catalog review.

_Source:_ [FoodSeg103 benchmark paper](https://openaccess.thecvf.com/content/CVPR2021/papers/Wu_A_Large-Scale_Benchmark_for_Food_Image_Segmentation_CVPR_2021_paper.pdf), [Zero-Shot Ingredient Recognition](https://ojs.aaai.org/index.php/AAAI/article/view/6626).

### Microservices Integration Patterns

Microservices patterns are not justified in the local-first PoC. Introducing an API gateway, service discovery, or a message broker would add failure modes without fixing the evidence problem. If a future server supports calibration updates or benchmark ingestion, keep it off the critical photo-analysis path and make model/ontology/calibration versions immutable and cacheable.

If remote inference is later offered, use a versioned request/response adapter with explicit timeout, retry, and provenance fields. A remote response must never be presented with the same “local detected” semantics unless it names the remote model and calibration version. Local and remote outputs should be comparable, not silently interchangeable.

_Recommended pattern:_ offline benchmark service and artifact registry first; optional remote inference second; no hidden server fallback in the MVP.

### Event-Driven Integration

The UI needs a small event/state contract rather than a message broker:

```text
PhotoSelected -> AnalysisStarted(requestId)
              -> DishCandidatesReady
              -> VisualProposalsReady
              -> FusionReady
              -> ReviewRequired | AnalysisCompleted | AnalysisAbstained
```

The spinner/loading overlay should bind to the request lifecycle, while evidence cards and image overlays bind to the latest `FusionReady` result. Late emissions from an older request must be ignored. A grid prompt is an input event and should create a new request, not mutate the prior result in place.

Review events should be explicit (`AcceptCandidate`, `RejectCandidate`, `AddMissingIngredient`, `MarkNotVisible`) and retained as evaluation data when consent permits. This creates a path from post-release user corrections to a future calibration/ontology update without turning a single correction into an automatic global rule.

### Integration Security and Integrity

Model manifests, label maps, calibration files, and ontology files should be integrity-checked by digest and tied to a release/version. User photo data should remain local by default; if future upload/consent is added, the evidence record must identify consent state and destination separately from model output.

No external integration should be allowed to replace `visualDetected` with a dish-prior assertion. API authentication, TLS, and key management matter only once remote services are introduced; the immediate integrity control is deterministic versioning and provenance.

_Source:_ [W3C PROV-DM](https://www.w3.org/TR/prov-dm/), [ONNX Runtime mobile deployment](https://onnxruntime.ai/docs/tutorials/mobile/).

## Architectural Patterns and Design

### System Architecture Patterns

Four architecture options were considered:

| Option | Benefit | Failure risk | Decision |
|---|---|---|---|
| Hard dish-prior filter | Very small implementation | Deletes plausible ingredients and conflates absence, detector miss, and false proposal | Reject |
| Top-1 soft reranking | Simple and improves consistency | Still brittle when dish top-1 is wrong or low-margin | Transitional baseline only |
| Calibrated top-k late fusion | Keeps independent evidence, supports abstention, measurable | Requires calibration/evaluation data and a richer contract | Recommended |
| End-to-end joint dish/ingredient/region model | Potentially strongest long-term semantics | Requires aligned multi-label/mask data, training, and larger mobile model | Future track |

The recommended architecture is ports-and-adapters with a reducer-driven unidirectional state machine:

```text
Photo input / grid prompt
        |
        v
Analysis coordinator (request id, cancellation, lifecycle)
        |
        +--> DishModelPort --------> DishCandidates
        +--> ComponentModelPort ---> VisualProposals/Masks
        +--> OntologyPort ----------> CompositionSupport
        +--> CalibrationPort -------> CalibratedScores
        |
        v
LateFusionEngine -> EvidenceContract -> Presenter/ViewModel -> UI
```

This keeps the current Android PoC runnable while giving production Android/iOS a stable seam. The dish prior becomes a data-driven `OntologyPort` input to fusion rather than a gate inside the detector pipeline.

_Source:_ [Android Guide to App Architecture](https://developer.android.com/topic/architecture), [Android UI layer guidance](https://developer.android.com/topic/architecture/ui-layer).

### Design Principles and Best Practices

The key design principles are separation of concerns, single responsibility, explicit provenance, and fail-closed claims. The Activity/ViewController should host UI and lifecycle only; it should not decide whether a detector label is a recipe ingredient. Model adapters should normalize native outputs, while domain fusion should be pure and testable.

The fusion layer should have no Android/iOS dependencies and should accept deterministic inputs:

```text
fuse(dishDistribution, visualProposals, ontology, calibration, policy)
    -> rankedCandidates + evidenceStates + abstentionReasons
```

No candidate should be removed solely because a prior says it is unusual. Instead, the policy may lower its rank, mark it `REJECTED_ARTIFACT` for the primary visual overlay, or retain it as `COMPOSITION_PLAUSIBLE` for review. Those are separate decisions.

The Android state holder should expose one immutable `PhotoAnalysisUiState` and accept events such as `PhotoSelected`, `GridPromptSelected`, `Retry`, `AcceptCandidate`, and `RejectCandidate`. Kotlin `StateFlow` is appropriate for the latest state because it is thread-safe and represents a continuously available state value; a request id or structured concurrency still needs to suppress stale work.

_Source:_ [Kotlin coroutines and StateFlow](https://kotlinlang.org/docs/coroutines-overview.html), [StateFlow API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/), [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations).

### Scalability and Performance Patterns

For on-device inference, scale by reducing model/package cost and controlling concurrency rather than introducing distributed services. Keep ONNX sessions lazy and reusable, avoid running multiple memory-heavy models concurrently on low-memory devices, and measure cold start, warm latency, peak RSS, model size, battery, and accelerator fallback behavior.

The first implementation can run dish and component models sequentially and fuse their outputs deterministically. Parallel execution is an optimization only if memory and thermal measurements show a benefit. Mobile acceleration must be measured per device/model because unsupported operators can partition graphs and degrade performance.

Benchmark the architecture with ablations:

```text
baseline detector
hard prior
top-1 soft prior
top-k soft prior
top-k calibrated fusion
```

The acceptance objective is visible-claim precision at useful coverage, not only dish top-1 accuracy. A model that claims fewer ingredients but has high precision may be preferable to a model that surfaces plausible but unsupported ingredients without labels.

_Source:_ [ONNX Runtime mobile deployment](https://onnxruntime.ai/docs/tutorials/mobile/), [SelectiveNet risk-coverage framing](https://proceedings.mlr.press/v97/geifman19a.html).

### Integration and Communication Patterns

Use a local pipeline with typed ports and immutable result objects. The public domain contract should not expose ONNX tensor shapes, COCO class indexes, or MobileSAM internals. Platform adapters translate those details into canonical records; the fusion engine consumes only canonical records.

The event sequence is:

```text
PhotoSelected -> AnalysisStarted(requestId)
              -> DishReady + VisualProposalsReady
              -> FusionReady
              -> ReviewRequired | Completed | Abstained
```

The UI can render partial progress, but only `FusionReady` may update the primary ingredient claims. An old `requestId` must be ignored. Review actions should become explicit events and, with consent, evaluation records rather than silent model mutations.

### Security Architecture Patterns

Local-first inference reduces photo-transfer and service-authentication exposure. Model, ontology, and calibration manifests should be pinned by digest and version. A future remote path must identify its model origin, calibration version, and consent/destination state; remote output must not inherit local “detected” semantics automatically.

The mobile security baseline should cover secure storage, network communication, platform interaction, code quality, and resilience against tampering. This is relevant to model manifests and user-consent evidence even though the current PoC is research-only.

_Source:_ [OWASP Mobile Application Security Verification Standard](https://mas.owasp.org/MASVS/02-Frontispiece/), [OWASP mobile security testing guidance](https://mas.owasp.org/MASTG/0x04b-Mobile-App-Security-Testing/).

### Data Architecture Patterns

Use four explicit data layers:

1. `RawModelOutput` — native detector/classifier output, retained for audit;
2. `CanonicalEvidence` — normalized labels, boxes/masks, calibrated scores, and source;
3. `FusionCandidate` — combined visual evidence and composition support;
4. `UserReviewRecord` — explicit accept/reject/add/not-visible action.

This prevents a recipe prior from overwriting the evidence that produced it. The same architecture supports future Nutrition5k/FoodSeg103 evaluation and post-release consented corrections without mixing ground truth, model output, and user preference.

### Deployment and Operations Architecture

Model packages should be released as immutable bundles containing model digests, label-map version, ontology/prior version, calibration artifact, contract version, and benchmark report. The app should fail closed if required manifest references are missing or mismatched. A model update is not complete until the same fixed evaluation slices are rerun on host and device.

Operational evidence must distinguish:

- host model benchmark;
- Android/iOS runtime latency and memory;
- UI/instrumentation semantics;
- calibration/fusion benchmark; and
- post-release user correction data.

These are complementary evidence layers, not interchangeable quality claims.

## Implementation Approaches and Technology Adoption

### Technology Adoption Strategies

Adopt the new fusion behavior in stages. Do not replace the current hard prior in one release:

1. **Shadow mode:** run hard-prior and candidate soft-fusion decisions in parallel; show the current UI but log the proposed evidence states in consented benchmark artifacts.
2. **Evidence-contract release:** add separate `visualDetected`, `compositionPlausible`, `notLocalized`, and `unknown` states while keeping the current ranking behavior behind a flag.
3. **Calibration release:** fit dish and detector calibration on a held-out, deployment-like set; record calibration version and risk-coverage curves.
4. **Soft-fusion release:** enable top-k dish mixture and learned/simple late fusion for a controlled cohort or research flag.
5. **Retirement:** remove hard filtering only after the soft-fusion benchmark improves visible-claim precision and does not regress review burden on required slices.

This is a strangler-style migration: the new fusion engine wraps the old model adapters and replaces one decision seam at a time. It keeps rollback possible and makes each change measurable. Google’s machine-learning guidance emphasizes validating serving behavior and monitoring rather than treating a trained model as the finished system.

_Source:_ [Google Rules of Machine Learning](https://developers.google.com/machine-learning/guides/rules-of-ml), [The ML Test Score](https://research.google.com/pubs/archive/aad9f93b86b7addfea4c419b9100c6cdd26cacea.pdf).

### Development Workflows and Tooling

Use one reproducible research-to-device workflow:

```text
research fixture -> host inference -> calibration/fusion benchmark
                 -> typed contract fixtures -> unit/property tests
                 -> Android/iOS runtime smoke -> device evidence artifact
```

The benchmark runner should pin image ids, model digests, label-map versions, ontology version, calibration version, and fusion policy. Every output should be replayable from JSONL/JSON fixtures. The device runner should report model load, cold/warm latency, peak memory, output contract version, and whether acceleration fell back to CPU.

The current Android PoC is a suitable experimental host, but model and fusion logic should move behind the shared domain contract before production. Reviewers should be able to inspect raw detector candidates and the exact reason a candidate became `VISUAL_DETECTED`, `COMPOSITION_PLAUSIBLE`, or `REJECTED_ARTIFACT`.

### Testing and Quality Assurance

The minimum benchmark matrix is:

| Slice | Purpose |
|---|---|
| clear single-component meals | visual precision/recall baseline |
| overlapping or mixed dishes | missing/occluded ingredient behavior |
| dish-prior conflict | broccoli plausible but not detected; orange false proposal |
| top-1/top-2 dish disagreement | top-k fusion robustness |
| unknown/non-food/poor quality | abstention and open-set behavior |
| device/camera/lighting variation | deployment shift |
| raw vs cooked and cuisine variation | ontology/generalization risk |

Required metrics:

- dish top-1 and top-k recall;
- ingredient multilabel precision, recall, and F1;
- visible-claim precision at selected coverage;
- composition-only suggestion precision and correction rate;
- calibration error, Brier score, and negative log-likelihood;
- risk-coverage curves for each claim type;
- detector region precision/recall and mask IoU where masks exist;
- false `VISUAL_DETECTED` claims per accepted image;
- latency, peak memory, model/package size, and battery/thermal observations.

The key ablation is `no prior -> hard prior -> top-1 soft prior -> top-k soft prior -> top-k calibrated fusion`. Do not accept a method solely because dish top-1 accuracy improves. TFMA’s sliced evaluation model is a useful pattern: aggregate metrics must be accompanied by slice metrics so the pork-chop/broccoli failure cannot disappear inside a global average.

_Source:_ [TensorFlow Model Analysis metrics and plots](https://www.tensorflow.org/tfx/model_analysis/metrics), [SelectiveNet](https://proceedings.mlr.press/v97/geifman19a.html), [FoodSeg103 benchmark](https://openaccess.thecvf.com/content/CVPR2021/papers/Wu_A_Large-Scale_Benchmark_for_Food_Image_Segmentation_CVPR_2021_paper.pdf).

### Deployment and Operations Practices

Every model package should ship with:

- model and label-map digests;
- ontology/prior manifest version;
- calibration artifact and calibration version;
- fusion-policy version;
- benchmark report with required slices;
- Android/iOS latency and memory evidence;
- known limitations and abstention behavior.

The runtime should fail closed when a model digest, label map, or calibration manifest is missing or mismatched. If a model update changes candidate semantics, it is a contract migration, not only an asset replacement.

Post-release monitoring should focus on drift and correction data: dish entropy, detector proposal rates, `REJECTED_ARTIFACT` frequency, user-added/missing ingredient rate, and slice-specific correction burden. User photos or corrections should only enter this loop with explicit consent and a separately versioned data-capture feature.

### Team Organization and Skills

The smallest effective ownership split is:

- **Inference/model owner:** model export, score calibration, detector/segmentation quality;
- **Evaluation owner:** datasets, ontology mapping, fixtures, slices, risk-coverage reports;
- **Mobile/domain owner:** typed contract, reducer/coordinator, lifecycle, cancellation, and Android/iOS parity;
- **Product/UX reviewer:** wording and evidence-state comprehension;
- **Release/quality owner:** model manifest integrity, device gates, and rollback evidence.

One person may hold multiple roles, but no single model author should be the only approver of both model quality and user-facing claim semantics.

### Cost Optimization and Resource Management

Before training a new joint model, spend effort on the lower-cost high-leverage steps: canonical ontology, held-out calibration set, shadow-mode logging, and deterministic fusion. A small logistic or isotonic/temperature-calibrated meta-model is cheaper to iterate and easier to audit than a new end-to-end model.

For device cost, use quantized/optimized model packages where accuracy permits, reuse sessions, avoid simultaneous memory-heavy inference, and measure CPU/NNAPI/CoreML paths on real devices. Keep the benchmark host separate from the app binary so a 400+ MB research APK is not treated as a production size target.

### Risk Assessment and Mitigation

| Risk | Mitigation |
|---|---|
| False dish top-1 creates confirmation bias | top-k mixture, dish entropy/margin gate, abstention |
| Detector misses occluded/small ingredient | never interpret absence as proof; keep composition candidate separate |
| Detector false proposal such as orange | calibrated visual precision gate; retain raw proposal only for audit |
| Recipe prior overstates hidden ingredients | `COMPOSITION_PLAUSIBLE` label; no nutrition binding without review |
| Dataset mismatch | public-data slices plus post-release consented corrections; report coverage limits |
| Calibration drift | versioned calibration artifact and periodic held-out refresh |
| UI misreads prior as detection | distinct visual styling/copy and evidence-state tests |
| Mobile runtime divergence | same contract fixtures on host, Android, and iOS; device gate for each model package |

NIST’s AI RMF frames this as continuous govern/map/measure/manage work rather than a one-time model approval. For SuperNutrition, the practical interpretation is that every claim type needs an owner, a metric, a review path, and a rollback condition.

_Source:_ [NIST AI RMF Core](https://airc.nist.gov/airmf-resources/airmf/5-sec-core/), [OWASP MASVS](https://mas.owasp.org/MASVS/02-Frontispiece/).

## Technical Research Recommendations

### Implementation Roadmap

**R1 — Freeze semantics as a delta against `inference-evidence/v2`.** Do not
create a second production evidence enum. Reuse v2 `CalibratedConfidence`,
`ComponentVisibility`, `ComponentAmbiguity`, `V2AbstentionReason`, and
`InferenceV2Provenance` for visual results. Keep composition-only and rejected
detector candidates in a versioned fusion/review envelope until an additive
optional v2 extension is designed and fixture-validated.

**R2 — Build the ablation harness from the existing G-3 baseline.** Pin the
current Food-101 semantic result, Android/iOS runtime measurements, and the
YOLO-World/MobileSAM candidate screen as the starting evidence boundary. Then
run no-prior, hard-prior, top-1-soft, top-k-soft, and calibrated-fusion policies
on fixed public-data and curated conflict slices; do not treat the existing
single-dish score or candidate screen as ingredient-quality evidence.

**R3 — Calibrate separately.** Fit dish calibration and detector/region calibration independently; never add raw Food-101 and COCO scores directly.

**R4 — Implement top-k late fusion.** Use a small capped top-k mixture:

```text
pDish = calibrateDish(dishLogits)
compositionSupport[z] = sum(pDish[d] * recipeSupport[z, d] for d in topK)
visualEvidence[z] = calibrated detector score only when localized
fusedRank[z] = calibrated meta-model(visualEvidence, compositionSupport,
                                     boxQuality, dishEntropy, openSetRisk)
```

The initial meta-model can be logistic regression or a deterministic weighted baseline, but its output must be calibrated and benchmarked. The final claim gate is per state, not one global threshold.

**R5 — Ship review-safe UI.** Primary overlay: only `VISUAL_DETECTED`. Candidate list: show `COMPOSITION_PLAUSIBLE` with “not visually confirmed.” Debug: retain all raw detector proposals and rejection reasons.

**R6 — Promote only on evidence.** Require host benchmark, Android device evidence, iOS device evidence when available, and contract/UI tests before enabling the new policy beyond research flag.

### Technology Stack Recommendations

- Python: calibration, offline fusion fitting, dataset adapters, metrics, and report generation;
- Kotlin Multiplatform/domain: canonical evidence contract, ontology/fusion policy, reducer/coordinator tests;
- Android/iOS thin adapters: model runtime, image/mask conversion, lifecycle, and device metrics;
- ONNX Runtime Mobile or equivalent pinned runtime for shared model execution;
- JSONL/JSON fixtures plus versioned TOML/JSON manifests for reproducibility;
- optional TFMA-like sliced evaluator or project-native equivalent for per-slice reporting.

### Skill Development Requirements

The team needs working knowledge of multilabel/segmentation metrics, calibration and selective prediction, ontology/data labeling, mobile model optimization, structured concurrency, and evidence-oriented UX. The most important skill is distinguishing “the model suggests this” from “the image proves this.”

### Success Metrics and KPIs

The release gate should track:

1. visible-ingredient precision at a predefined useful coverage;
2. prior-only candidate precision and user correction rate;
3. false visual-claim rate, especially prior-induced claims;
4. calibration error by dish and ingredient slice;
5. top-k dish recall and dish/ingredient consistency;
6. abstention quality on unknown/poor-quality images;
7. Android/iOS p50/p95 latency, peak memory, and model size;
8. percentage of outputs with complete provenance and contract-valid evidence states;
9. zero stale-result or lifecycle regressions in UI/device tests.

Fusion score thresholds should be set from the existing G-3 baseline plus a
fusion-specific slice report and product risk tolerance, not invented before
calibration. The runtime/resource comparison budgets are already recorded in
the G-3 artifact; they remain comparison targets and do not imply production
activation.

## 1. Technical Research Introduction and Methodology

Image-based dietary assessment is not one prediction task. A practical system must segment or localize food, classify dish/component concepts, estimate uncertainty, and only then bind claims to nutrition data. A systematic review of image-based food-recognition systems describes this multi-stage pipeline and its open issues, which is why a single dish label cannot serve as a complete ingredient explanation ([systematic review](https://pubmed.ncbi.nlm.nih.gov/35803496/)).

This research combined: current primary/official web sources; read-only inspection of the SuperNutrition PoC and its current contract seams; the device failure trace; the BMAD workflow; and an independent subagent synthesis that recommended calibrated two-channel late fusion, top-k composition support, and claim-specific gates. New research-agent slots were temporarily unavailable because existing agent slots were occupied; no unrelated agent output was used as evidence.

The scope covered architecture, model/ontology integration, calibration, selective prediction, mobile runtime constraints, UX evidence semantics, benchmark design, rollout, and operational risk. Confidence is high for the architectural separation and evidence-state recommendation; medium for the exact fusion weights and thresholds, which require a project-specific calibration set.

## 2. Technical Landscape and Architecture Analysis

The current landscape favors specialized model roles: dish classification, ingredient/component segmentation or detection, and relational/contextual reasoning. FoodSeg103 demonstrates the need for ingredient-level masks; Nutrition5k demonstrates the value of component and mass annotations; zero-shot ingredient recognition demonstrates that dish–ingredient relations can help when direct labels are sparse ([FoodSeg103](https://openaccess.thecvf.com/content/CVPR2021/papers/Wu_A_Large-Scale_Benchmark_for_Food_Image_Segmentation_CVPR_2021_paper.pdf), [Nutrition5k paper](https://openaccess.thecvf.com/content/CVPR2021/papers/Thames_Nutrition5k_Towards_Automatic_Nutritional_Understanding_of_Generic_Food_CVPR_2021_paper.pdf), [zero-shot ingredient recognition](https://ojs.aaai.org/index.php/AAAI/article/view/6626)).

The selected architecture is a ports-and-adapters pipeline with reducer/UDF state management. This aligns with Android’s separation-of-concerns guidance: UI hosts state, while business logic belongs in domain/data layers and Activity is not a durable state store ([Android app architecture](https://developer.android.com/topic/architecture)). The model adapters remain replaceable; the ontology and fusion policy remain pure and testable; the UI receives explicit evidence states instead of guessing provenance.

## 3. Implementation Approaches and Best Practices

The implementation should start with a deterministic baseline and incrementally add evidence:

```text
baseline raw detector
  -> hard-prior shadow comparison
  -> top-1 soft prior
  -> top-k soft prior
  -> calibrated top-k late fusion
  -> claim-specific gates and user review
```

Each stage must produce replayable outputs and be evaluated on the same slices. The first code change should be contract and fixture work, not more recipe entries. The `pork_chop` mapping can remain a composition prior, but it must no longer govern whether a detector output is deleted from the audit/review path.

## 4. Technology Stack Evolution and Current Trends

Python is the right layer for calibration and evaluation; Kotlin Multiplatform/domain is the right layer for canonical fusion semantics; Android/iOS hosts should remain thin runtime adapters. ONNX Runtime Mobile supports CPU plus platform-specific accelerator paths, but its documentation explicitly recommends measuring model/device behavior and notes that unsupported operators can partition graphs and reduce performance ([ONNX Runtime Mobile](https://onnxruntime.ai/docs/tutorials/mobile/)).

The technology trend that matters is evidence composition: multi-label segmentation, open-vocabulary/zero-shot relations, calibrated uncertainty, and selective outputs. A larger closed-set classifier alone does not address the broccoli/orange failure because the core issue is claim semantics and evidence fusion.

## 5. Integration and Interoperability Patterns

Use a versioned typed result contract with separate namespaces for dish labels, detector labels, ingredient concepts, recipe relations, and user-confirmed concepts. JSON Schema 2020-12 is suitable for external validation and fixtures; Protocol Buffers is a possible compact transport later; W3C PROV provides a useful provenance model for source/activity/derivation metadata ([JSON Schema](https://json-schema.org/specification), [Protobuf](https://protobuf.dev/programming-guides/editions/), [W3C PROV-DM](https://www.w3.org/TR/prov-dm/)).

The runtime should stay local-first. A grid prompt, new photo, or user correction is an explicit event; late results from an old request are ignored. Remote inference, if added later, must identify its model and calibration provenance and must not inherit local visual-detection semantics automatically.

## 6. Performance and Scalability Analysis

The main scalability axis is evidence quality per device resource, not service fan-out. Reuse model sessions, avoid concurrent memory-heavy inference unless measured beneficial, and report cold/warm latency, p50/p95 latency, peak memory, model size, battery/thermal behavior, and CPU/accelerator path. Keep the current research APK’s large embedded model package separate from production size targets.

Performance optimization must not remove evidence states. If a lower-cost detector is less capable, the result should become more abstaining or more composition-only, not silently more confident.

## 7. Security and Compliance Considerations

Local-first processing reduces photo-transfer exposure. Model/ontology/calibration manifests need digest and version checks. If consented user-upload and correction capture is added after release, consent state, destination, user correction, and model output must remain separate records. OWASP MASVS provides the appropriate mobile baseline for storage, cryptography, authentication/authorization, network communication, platform interaction, code quality, and tamper resilience ([OWASP MASVS](https://mas.owasp.org/MASVS/02-Frontispiece/)).

For AI risk management, the practical controls are continuous measurement, slice reporting, explicit abstention, and rollback criteria. NIST AI RMF’s govern/map/measure/manage framing is compatible with this staged rollout ([NIST AI RMF Core](https://airc.nist.gov/airmf-resources/airmf/5-sec-core/)).

## 8. Strategic Technical Recommendations

1. **Do not add more hard-coded dish priors as the main fix.** Priors should be data-driven composition support.
2. **Separate visual evidence from composition plausibility.** This is the most important contract change.
3. **Use top-k dish mixture, not top-1 whitelist.** This reduces confirmation bias when dish confidence is low or the image is mixed.
4. **Calibrate before exposing percentages.** Current confidence bands are presentation policy, not measured probabilities.
5. **Optimize for visible-claim precision at useful coverage.** A safe abstention is better than a false ingredient claim.

## 9. Implementation Roadmap and Risk Assessment

### Phase A: Contract and replay fixtures

Add evidence states, provenance, model/ontology/calibration versions, raw proposals, and decision reasons. Build fixtures for the current pork-chop trace and adversarial cases where a plausible ingredient is missed.

### Phase B: Evaluation and calibration

Run public datasets and curated conflict slices. Fit dish calibration and detector calibration separately. Produce ablation and risk-coverage reports. No UI behavior change yet.

### Phase C: Shadow mode

Compute old hard-prior and new soft-fusion outputs in parallel. Compare visible-claim precision, candidate recall, false visual claims, correction burden, and device latency.

### Phase D: Review-safe UI

Show only calibrated `VISUAL_DETECTED` overlays. Show `COMPOSITION_PLAUSIBLE` as a separate review candidate with “not visually confirmed.” Keep rejected/low-confidence raw proposals in debug only.

### Phase E: Controlled activation

Enable the policy behind a research flag, then promote only after Android/iOS evidence and contract/UI gates pass. Retain rollback to the prior policy until post-release correction data confirms behavior.

## 10. Future Technical Outlook and Innovation Opportunities

Near term, the highest-return work is calibrated fusion, ontology quality, and review UX. Medium term, train a joint multi-label/segmentation model using aligned FoodSeg103/Nutrition5k-style data plus consented user corrections. Longer term, explore open-vocabulary ingredient segmentation and multimodal retrieval, but keep claim-specific evidence semantics even if the model becomes more capable.

The future opportunity is not to make the app guess a longer ingredient list. It is to make the app state which parts are visually supported, which are composition hypotheses, which are uncertain, and what the user can correct.

## 11. Technical Research Methodology and Source Verification

### Primary Sources Used

- [FoodSeg103 benchmark paper](https://openaccess.thecvf.com/content/CVPR2021/papers/Wu_A_Large-Scale_Benchmark_for_Food_Image_Segmentation_CVPR_2021_paper.pdf)
- [Nutrition5k paper and official repository](https://openaccess.thecvf.com/content/CVPR2021/papers/Thames_Nutrition5k_Towards_Automatic_Nutritional_Understanding_of_Generic_Food_CVPR_2021_paper.pdf), [repository](https://github.com/google-research-datasets/Nutrition5k)
- [UECFoodPix official dataset](https://mm.cs.uec.ac.jp/uecfoodpix/)
- [On Calibration of Modern Neural Networks](https://proceedings.mlr.press/v70/guo17a.html)
- [SelectiveNet](https://proceedings.mlr.press/v97/geifman19a.html)
- [Multivariate Confidence Calibration for Object Detection](https://openaccess.thecvf.com/content_CVPRW_2020/html/w20/Kuppers_Multivariate_Confidence_Calibration_for_Object_Detection_CVPRW_2020_paper.html)
- [Zero-Shot Ingredient Recognition](https://ojs.aaai.org/index.php/AAAI/article/view/6626)
- [ONNX Runtime Mobile](https://onnxruntime.ai/docs/tutorials/mobile/)
- [Android architecture](https://developer.android.com/topic/architecture)
- [Kotlin coroutines/StateFlow](https://kotlinlang.org/docs/coroutines-overview.html)
- [JSON Schema](https://json-schema.org/specification)
- [Protocol Buffers](https://protobuf.dev/programming-guides/editions/)
- [W3C PROV-DM](https://www.w3.org/TR/prov-dm/)
- [OWASP MASVS](https://mas.owasp.org/MASVS/02-Frontispiece/)
- [NIST AI RMF Core](https://airc.nist.gov/airmf-resources/airmf/5-sec-core/)

### Project Evidence and Contract References

- [`inference-evidence/v2 schema`](../../../contracts/inference/v2/schema.toml)
- [`InferenceV2Models.kt`](../../../mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/InferenceV2Models.kt)
- [`InferenceV2Contract.kt`](../../../mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/InferenceV2Contract.kt)
- [`G-3 local capability and device matrix`](../../implementation-artifacts/g3-benchmark-artifact.md)
- [`Android component/mass benchmark`](../../../ingredient-weight-research/android-component-mass-benchmark-2026-08-02.json)
- [`Android candidate device evidence`](../../../ingredient-weight-research/android-candidate-device-evidence-2026-08-02.json)

### Limitations

No new 300-meal weighed corpus was collected for this research. Public datasets have domain and cuisine limitations, and some component labels represent recipe/measurement truth that may not be visually observable in a single photo. Exact fusion weights, thresholds, and production claims therefore remain unvalidated until the project-specific calibration and conflict-slice benchmark is run.

## 12. Technical Appendices and Reference Materials

### Decision Table for the Current Case

| Input | Current unsafe interpretation | Recommended interpretation |
|---|---|---|
| `pork_chop` top dish candidate | dish label treated as ingredient evidence | dish hypothesis with calibrated dish confidence |
| `broccoli` absent from detector | broccoli absent | no visual localization; retain as possible composition candidate if top-k support warrants |
| `orange` detector proposal | ingredient claim | raw visual proposal; show only if visual precision gate passes, otherwise rejected/debug |
| `carrot` detector proposal | automatically correct ingredient | visual proposal subject to detector calibration and region quality |

### Minimal Fusion Contract

```text
Candidate {
  conceptId
  source: DISH | DETECTOR | ONTOLOGY | USER
  visualConfidence?
  compositionSupport?
  evidenceState
  region?
  calibrationVersion
  modelManifestVersion
  decisionReason
}
```

### Technical Research Conclusion

The current `pork_chop`/`orange`/missing-broccoli case is not best solved by adding a longer whitelist. It exposes a category error: dish composition knowledge and visual detection are different evidence streams. The safest and most useful next implementation is a calibrated, top-k, late-fusion engine with explicit evidence states and a review-safe UI. It preserves plausible candidates without asserting that they are visible, suppresses detector artifacts without erasing audit data, and creates measurable gates for later model improvement.

**Technical Research Completion Date:** 2026-08-02
**Research Status:** complete
**Source Verification:** current public primary/official sources cited inline
**Technical Confidence:** high for architecture and evidence semantics; medium for thresholds and fusion weights pending calibration data
