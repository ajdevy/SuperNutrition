# Slice B defect — "Photo capture failed" after a successful gateway 200

Worktree `/Users/jj/Work/SuperNutrition-integration`, branch `integration-test`, base `78c918e`.
Primary checkout untouched. No push, no PR. Device: `Pixel_9_Pro` AVD. Backend: `TEST_MODE=true`,
**no** `STUB_INFERENCE`, real `OPENROUTER_API_KEY` from `.env`.

**The end-to-end flow now completes.** Capture → consent → grant → gateway HTTP 200 → review renders.

---

## Root cause

`BoundedDishIngredientFusion.merge` did not bound. It passed the engine's candidate list straight into
`CandidateReviewDraft`, whose initializer admits at most `MAX_MAIN_DISH_PROPOSALS = 3`:

```kotlin
fun merge(dishes: List<InferenceCandidate>, photoEvidence: List<VisibleComponent>) =
    CandidateReviewDraft(mainDishProposals = dishes, …)   // unbounded
```

The backend stub returned **2** candidates, so Slice A never crossed the limit. The real model returns
up to **5** — the gateway prompt permits it — and the first real photo produced **4**. The draft threw
`IllegalArgumentException`, which `PhotoAnalysisAnalysisCoordinator`'s `catch (_: Exception)` converted
into `recover(…, RecoveryReason.CAPTURE_FAILED)`.

**The earlier "lost `Analyzing` state" hypothesis was wrong**, and the code disproves it: `recover()`
only dispatches when `isActive(requestId, ref)` holds, otherwise it returns `Ignored` silently. So the
visible "Photo capture failed" is itself proof that the state *was* still `Analyzing` and that a
non-cancellation exception was thrown. Two of the three CAPTURE_FAILED dispatch sites
(`PhotoAnalysisAndroidHostController:87` and `:152`) are pre-analysis capture paths and cannot fire
after a 200, leaving coordinator `catch` as the only candidate — which the device then confirmed.

### Device evidence for the cause

Temporary probe in the coordinator's catch, real model, real photo:

```
SNPROBE-COORD IllegalArgumentException: Candidate review must contain at most 3 main-dish proposals
```

The probe was removed before committing (`grep -c SNPROBE` → 0).

---

## Fix

Two files, both in `domain/photo-analysis`:

1. `CandidateReviewDraft.MAX_MAIN_DISH_PROPOSALS` is no longer `private`. The fusion must bound to
   exactly this number; duplicating it is what let the two drift. Per AGENTS.md, when two paths must
   agree they share the implementation rather than being kept in step.
2. `BoundedDishIngredientFusion.merge` bounds:

```kotlin
mainDishProposals = dishes
    .sortedBy(InferenceCandidate::rank)
    .take(CandidateReviewDraft.MAX_MAIN_DISH_PROPOSALS),
```

Sorted before taking, so the *best-ranked* proposals survive and the draft's
strictly-increasing-rank rule holds by construction instead of by trusting the engine's ordering.

Nothing else changed. Consent, market policy, the backend chain and contracts are untouched.

---

## Verification

**Regression test watched failing first.** With the fix reverted and everything else identical, all
three tests in `BoundedDishIngredientFusionBoundTest` failed with the exact device exception:

```
theBestRankedProposalsAreTheOnesKept          -> IllegalArgumentException: Candidate review must contain at most 3 main-dish prop…
mergeBoundsAnyEngineResultToWhatTheReviewAdmits -> IllegalArgumentException: …
aFourCandidateResultMergesRatherThanThrowing  -> IllegalArgumentException: …
```

The tests run to the contract ceiling (1..5 candidates), not just the observed 4 — a fix that handled
only "four" would miss five.

**On-device E2E with the real model.** Gateway request count 2 → 3, last `"statusCode":200`, review
rendered:

```
Review candidate
Dish candidates:  paella · shrimp · lemon      ← real model, top 3 by rank
Detected in photo: Leafy greens, Estimated: 100–140 g   ← fixture component engine
```

Evidence: `e2e-review.png`, `e2e-review-hierarchy.xml` in this directory.

**Gates** (counts from XML, not BUILD SUCCESSFUL):

| | |
|---|---|
| `qualityCheckJvmAndroid --continue` | **BUILD SUCCESSFUL** |
| JVM host aggregate | line 99.63%, branch 90.57% across 11 modules |
| `:domain:nutrition` | line 99.99%, branch 100.00% (strict floor, untouched) |
| `:domain:inference` | branch 88.02%, line 99.58% |
| `:domain:photo-analysis` | branch 87.26%, line 98.20% |
| `jvmTest` | **714 / 714**, 0 failures (was 711; +3) |

`git diff --check` clean. No floor lowered, no exclusion, baseline or suppression;
`coverage.toml` and `evidence-bindings.toml` untouched.

---

## Carried items closed after the fix

- **The connected suite is green for the first time: 21/21 on BOTH flavours, 0 failures.**
  `captureSurfaceExposesStableAutomationIdsAndLabels` failed because the capture screen applied no
  window insets, so its first element — the title — drew under the status bar. An occluded node is
  dropped from the accessibility tree, so `photo_analysis_title` was invisible to UI Automator and to a
  screen reader alike, while `photo_analysis_status` just below it was fine. Proven with a temporary
  instrumentation probe that listed every node UiAutomator could see: 14 nodes, title absent, the other
  four ids present. The probe was removed.
  The inset is applied in the **Android host**, not the shared screen: `safeDrawingPadding()` in
  commonMain resolved to the skiko variant and crashed on launch with
  `NoClassDefFoundError: WindowInsetsPadding_skikoKt`. That attempt was reverted.
- **Hint ordering.** `BoundedDishIngredientFusion.hintsFor` took 24 hints and then sorted them, so once
  more than 24 accumulated the ones shown were an arbitrary subset rather than the best-supported. Now
  sorted before taking. Invisible before, because the draft's descending-order rule still held over
  whatever survived.

## Second real-model E2E (regression check)

A different photo through the same flow: gateway 200, review rendered
`shrimp pasta · prawn pasta · cherry tomatoes` fused with the local component evidence.

## What remains

- **`graphify update .` not run** — there is no `graphify-out/graph.json` in this worktree and no
  `graphify` on PATH, so there is nothing to update here.
- **No physical device.** Camera and native-gap evidence still require one; the emulator is not that.
- **Maestro flows remain written but not executed** — the CLI is not installed on this host.
- **Consent is still reachable only in test mode**, and the v2 component slot is still the
  honestly-labelled fixture. Both unfence together with the engines, in their own story.
- **Contract-owner / iOS-owner items unchanged:** `verify-release-immutability` on
  `contracts/mobile-api/v1`; the two market-policy fixtures that disagree on consent copy version and
  processing region; the dropped iOS `TrackedPhotoPickerViewController`.
