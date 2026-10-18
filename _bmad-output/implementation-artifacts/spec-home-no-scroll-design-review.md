---
title: 'Home first-frame fitting and designer screen matrix'
type: 'bugfix'
created: '2026-10-17'
status: 'done'
completion_mode: 'measured fit with explicit designer stops'
whole_quality_gate: 'red: expired native evidence'
baseline_commit: '7ec615063ac166d69e7f4a81495e20098f25be3c'
review_loop_iteration: 1
context: []
---

<frozen-after-approval reason="Explicit user asks implementation and screenshots; stop squeezing when text becomes unreadable">

## Intent

Home currently needs scrolling and its first frame may hide the helpful-step card behind navigation. The user wants every view visible without scrolling on every emulator screen size, with readable typography. Where this cannot be achieved without making text too small, stop the fitting attempt and provide original screenshots and measurements to the designer.

Measure actual children rather than scaling a screenshot or font sizes. For feasible screens, use the available viewport to allocate decorative mascot space and spacing, preserving all text and actions. For infeasible screens, retain the readable existing screen, mark the case as needing a designer decision in the evidence package, and do not claim the no-scroll goal passed. Capture all declared mobile screen geometries in the installed SDK, plus prior app checkpoints, with normal and enlarged system text. These are finite viewport cases, not every hypothetical emulator/device/API combination.

## Boundaries & Constraints

**Always:** Every header, date, badge, care question, both care actions, mascot, all four full metric labels/values, helpful-step copy/action and main controls remain available. Existing typography tokens, system text scale and 48dp interactive frames are preserved. Keep the background behind floating navigation clear, mascot foreground overlap, proportional art, existing callbacks and dialog state. Measure safe insets and the highest floating control. Compare the actual initial frame, never scroll to manufacture a fitting pass. Save immutable before images separately from after images, native bounds, APK digest, exact viewport/density/font scale and outcome.

**Stop condition:** When natural text/action height plus minimum meaningful artwork and navigation cannot fit, stop compacting that case. Do not shrink text further, truncate, omit information, or fabricate success. Preserve a readable overflow screenshot, mark it designer-required and explain what has to change. A universal all-size pass is not expected from a one-column design with fixed 48dp controls on arbitrarily short displays.

**Never:** Change camera/gallery/Rewards behavior, data or consent policy, screen-orientation contract, native evidence dates/TTL, unrelated staged main changes, or the physical phone. Do not force every case through a scaled graphics layer. Do not delete existing accessibility or selection assertions. Do not claim overriding viewport dimensions is running a separate device model. Run one Gradle invocation and one emulator at a time; preserve the existing useful baseline emulator and unrelated Pixel AVD. Delete only stopped AVDs created by this task after preserving config/evidence. No new commit/push is required by this request.

## I/O & Edge-Case Matrix

| Scenario | Input | Expected |
| --- | --- | --- |
| Feasible portrait phone | Standard text, enough measured usable height | All content in initial frame above navigation; zero scroll range; artwork allocates spare height |
| Short/legacy phone | Smallest declared SDK height, normal text | Preserve readable content; record exact overflow and design stop |
| Enlarged text | System font 130% and 200% | Preserve text scale and full words; fit only when measured budget permits, otherwise design stop |
| Tablet/fold/wide container | SDK base display with portrait-locked app | Capture actual app/window geometry and letterboxing honestly; no unsupported posture/orientation claim |
| Model update/window resize | New text or font/viewport constraints | Re-measure actual content, keep controls/callbacks/dialog state and do not duplicate nodes |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreen.kt` — current unbounded scroll content and fixed mascot minimum.
- HomeHeader, HomeBrand, HomeCarePrompt, HomeRhythmCard and HomeNextStep — natural child sizes and preserved typography.
- `HomeNavigationOverlayTest.kt`, `NativePhoneHomeLayoutTest.kt`, and new `HomeNoScrollLayoutTest.kt` — actual initial geometry, zero scroll, controls and readable stop.
- `scripts/review-home-viewport.py` — owned-emulator capture/metadata/restore helper; parent owns this and the evidence package.
- `_bmad-output/design-review/home-no-scroll-2026-10-17/` — original PNGs, SDK inventory, actual runtime metadata and Russian designer gallery/notes.

## Tasks & Acceptance

- [x] Record current clipping and natural-budget finding before production edits; write focused initial-frame regression and observe it fail for actual clipping/scroll range.
- [x] Add measured adaptive Home arrangement for feasible viewports while preserving text tokens and minimum touch frames; keep unreadable/no-fit cases as explicit designer stops.
- [x] Run complete relevant UI tests, scoped lint, Android production assembly and explicit shared UI iOS Arm64 compilation; inspect fresh XML and source hashes.
- [x] Capture every one of the 45 declared unique mobile pixel/density geometries at normal and enlarged text, plus selected 130% boundaries; label equivalences and unsupported fold postures. Inspect each image immediately before further UI actions.
- [x] Verify boundary cases in separate temporary compact/large/tablet AVDs sequentially, restore baseline guest settings and remove only task-created AVDs.
- [x] Independent introduced-change review, graph update, diff check and designer-readable gallery/report with exact coverage/limitations.

Given a feasible measured viewport, when Home opens, all required children and actions must be fully within the safe content area and above navigation, without a scroll gesture or clipped text. Given a no-fit viewport, capture/report a design stop with unchanged font sizes rather than claiming success. Given resize/data changes, content must remeasure without losing callbacks or dialog state. Given delivery, the designer can open original full-resolution PNGs grouped by viewport/font and see which cases fit, overflow or require a different layout.

## Design Notes

SDK inventory initially parsed named density buckets only; numeric tokens such as 420dpi were then validated explicitly. Final catalog has 71 definitions and 45 exact pixel/density geometries including four prior app checkpoints. Wear, TV, automotive, XR and separate desktop-resource profiles are outside this mobile-screen request; generic freeform/rollable/fold base displays are included. The production Android launcher is portrait-locked. Do not remove that lock to invent landscape support.

## Verification

Use the existing task-local Gradle/Konan/Skiko runtime configuration, `--continue`, strict dependency verification and no configuration cache. Fresh focused red/green and complete UI XML counts are distinct evidence. The unchanged full-root native policy expiry is a known separate limitation. Native screenshots use the task-owned API35 x86_64 emulator on isolated ADB server 5041; never touch physical endpoints.

## Completion evidence

The finite review is complete with 40 fitting cases and 60 explicit designer stops; a universal no-scroll success is not claimed. All 106 original PNGs (100 final plus six historical) were inspected and hash-matched. The three matrix AVDs were removed, settings restored, the portable gallery/ZIP validated, and independent review dispositions documented. UI: 167/0/0/0; presenter: 19/0/0/0; native: 43/0/0/0 per variant, 129 total, on a clean API35 x86_64 emulator. The exact command, per-variant XML, input hashes, root-quality expiry and missing-Xcode boundary are recorded in `home-no-scroll-delivery-2026-10-18.md` and `home-no-scroll-verification-2026-10-18/`. Story key is unset, so sprint sync is not applicable.

## Suggested Review Order

**Home measurement**

- Start with one content tree and its measured viewport budget.
  [HomeScreen.kt:41](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreen.kt#L41)

- Reserve readable controls before allocating decorative height; stop compacting when the minimum cannot fit.
  [HomeMeasuredLayout.kt:33](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeMeasuredLayout.kt#L33)

- Use actual word widths to choose the metric columns.
  [HomeRhythmCard.kt:49](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeRhythmCard.kt#L49)

- Keep helpful-step text and action readable through natural row or column placement.
  [HomeNextStep.kt:52](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeNextStep.kt#L52)

**Approved Rewards integration**

- Retain ten prototype medals and choose readable content without reducing typography.
  [RewardsScreen.kt:59](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsScreen.kt#L59)

- Use natural height when the fixed illustration canvas cannot fit the actual copy.
  [RewardsReadableCanvas.kt:43](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsReadableCanvas.kt#L43)

**Verification and designer handoff**

- Exercise initial-frame fit, no scrolling, callbacks, text scale and explicit designer stops.
  [HomeNoScrollLayoutTest.kt:29](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/HomeNoScrollLayoutTest.kt#L29)

- Assert full characters, glyphs, touch areas and contrast against the actual rendered text.
  [RewardsTextReadabilityTest.kt:50](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/RewardsTextReadabilityTest.kt#L50)

- Keep the native Home semantic assertion unchanged while diagnosing external focus blockers.
  [PhotoAnalysisComposeSemanticsInstrumentationTest.java:33](../../mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PhotoAnalysisComposeSemanticsInstrumentationTest.java#L33)

- Review original screenshots and short-screen composition decisions.
  [DESIGNER-NOTES.md:1](../design-review/home-no-scroll-2026-10-17/DESIGNER-NOTES.md#L1)
