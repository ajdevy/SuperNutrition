---
title: 'Home compact header and consistent section spacing'
type: 'bugfix'
created: '2026-10-19'
status: 'done'
baseline_commit: '262811616f9a2415c5135c23b74597033d788120'
approval_basis: 'Explicit user request to remove the Home wordmark and weekday/date and regularize its spacing'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="User explicitly requested this visual change; routine implementation choices are within the authorized scope">

## Intent

**Problem:** Your next helpful step sits too close to Today's rhythm. The current measured layout allocates decorative mascot height before spacing and can collapse section gaps toward zero. The Home wordmark and weekday/date also consume space the user now wants freed.

**Approach:** Remove the visible Super Nutrition wordmark and weekday/date from shared Home. Retain the rhythm badge and profile action in a compact, readable header row. Apply a shared 16dp gap between independent Home sections, reserving it before assigning decorative mascot height. Keep the mascot's deliberate foreground overlap with the rhythm card.

## Boundaries & Constraints

**Always:** Preserve the Home root's `home_title` accessibility identity and localized screen label, badge content, profile/goals/meal callbacks, all four complete metrics, helpful-step action, typography, system font scale, 48dp touch targets and navigation clearance. Keep symmetric 24dp outside gutters and consistent card/row insets; the mascot/card overlap is decorative rather than a section gap. Use a single measured composition. On a genuinely infeasible viewport preserve readable overflow, rather than collapsing spacing, shrinking text or claiming a fitting first frame.

**Ask First:** Any new business/privacy behavior, changes to unrelated screens, or publication outside the currently requested local implementation.

**Never:** Change camera/gallery/Rewards behavior, installed branding, orientations, native-evidence policy, unrelated staged primary-checkout changes or existing immutable galleries. Do not touch the physical phone. Use one Gradle build and one task-owned emulator at a time. Do not commit/push this new slice unless requested.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
| --- | --- | --- | --- |
| Standard phone | 360×780, normal text, native safe insets | Header contains badge/profile only; sections remain readable with matching 16dp gaps; all fitting content above navigation | Measure actual children |
| Large phone / tablet | 412×915 or 800×1280 | Same section rhythm and symmetric gutters; mascot art remains proportional | No screenshot/font scaling |
| Enlarged text | 130% or 200%, including 320px width | Header badge wraps safely if needed; profile stays reachable; all words/actions remain available | Scroll readable content when necessary |
| Live data / RTL | Badge/profile labels change or layout direction reverses | Current content and callbacks work; no removed brand/date reappear; header actions do not overlap | Recompose natural measurements |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeHeader.kt` — replace brand/date composition with the retained badge/profile row.
- `HomeMeasuredLayout.kt`, `HomeScreen.kt`, `HomeScreenStyle.kt` in the same package — shared section spacing and reserve-first measured height budget.
- `HomeCarePrompt.kt`, `HomeRhythmCard.kt`, `HomeNextStep.kt` — consistent insets within their existing composition where needed; preserve button centering and readable wrapping.
- `mobile/core/ui/src/commonMain/kotlin/com/supernutrition/core/ui/UiAutomationId.kt` — only if stable additive header/section tags are needed for genuine geometry checks.
- UI `HomeScreenComposeTest`, `HomeNoScrollLayoutTest`, `BrandNavigationVisualTest`, `AdditionalInteractionLayoutsTest`, `MainScreenRefreshTest` — replace assumptions about the removed visible text with the new behavior, retaining navigation/accessibility/callback assertions.
- `scripts/review-home-viewport.py` — stop requiring removed visible brand/date, retain readiness, dimensions, text, action and fit checks; preserve previous task's output-directory option.

## Tasks & Acceptance

**Execution:**
- [x] Add focused header/spacing regressions and observe the existing implementation fail for the reported defects before production edits.
- [x] Simplify the header, reserve uniform section gaps before mascot space, and align section/card insets without changing typography or existing interactions.
- [x] Update obsolete test/capture expectations while retaining root accessibility identity and all relevant reachability, readability and navigation checks.
- [x] Run the complete shared UI module test task, relevant compile/lint, production assembly and shared Apple compilation sequentially; record fresh per-module reports and any limitations.
- [x] Install the production APK on the owned API35 x86_64 emulator and inspect fresh compact/large/tablet screenshots at normal and enlarged text; save a separate current review, restore settings and preserve old galleries.
- [x] Complete independent introduced-change review, fix actionable findings, update graph and run diff checks; hand off screenshots and actual verification results.

**Acceptance Criteria:**
- Given Home opens, when inspecting visible text and semantics, then the wordmark and weekday/date are absent while `home_title`, the badge and profile remain available.
- Given a fitting viewport, when measuring independent section containers, then the same 16dp gap separates header/care, care/mascot and rhythm/helpful-step sections, with complete metric/action text and no navigation collision.
- Given a short or enlarged-text viewport, when the layout cannot fit with readable typography and fixed gaps, then text is not reduced and the entire helpful action remains reachable by scrolling.
- Given a live model update, RTL or profile/goals/meal interaction, when using Home, then the current badge and existing callbacks/dialogs continue to work.

## Design Notes

The retained rhythm badge is functional progress context; only the redundant visible wordmark and weekday/date were rejected by the user. No presenter/model field needs removal because other hosts and existing data contracts can retain those fields. The root screen identifier is distinct from the visible brand.

Page-edge vertical padding is distinct from inter-section spacing: use 8dp at the page edges and retain 16dp at all three section boundaries. The care bubble shares the available content-column width instead of its old 260dp cap; this also aligns its horizontal edges and avoids needless question wrapping on ordinary phones.

The decorative bubble tail paints inside the already reserved 16dp care-to-mascot gap rather than adding a second 16dp height. Section measurements refer to the care panel, while its tail remains visible and proportional in that decorative space.

The complete module test run exposed the old intrinsic-canvas issue in newly authored badge checks. Extract the existing physical line/paragraph readability assertion into one JVM test helper and reuse it in Home and badge tests, retaining glyph completeness, scale and overflow diagnostics.

## Verification

Use fresh focused red/green, the full `:feature:photo-analysis-ui:jvmTest` task, relevant source-set lint, `:apps:androidApp:assembleProductionDebug`, and explicit shared `compileKotlinIosArm64` with the Intel-host opt-in. Record native observations as Android emulator evidence, not physical-device or iOS execution. Old screenshots remain historical rather than being relabeled as current.

## Spec Change Log


## Suggested Review Order

**Layout**

- Reserve section spacing before allocating decorative artwork.
  [HomeMeasuredLayout.kt:32](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeMeasuredLayout.kt#L32)

- Share section, page, card and metric spacing tokens.
  [HomeScreenStyle.kt:14](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreenStyle.kt#L14)

**Header and care**

- Keep the progress badge and a readable, RTL-aware profile action.
  [HomeHeader.kt:37](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeHeader.kt#L37)

- Align the care column and draw its tail within the reserved gap.
  [HomeCarePrompt.kt:46](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeCarePrompt.kt#L46)

**Evidence**

- Retain baseline checks and require updated spacing only after the change.
  [review-home-viewport.py:197](../../scripts/review-home-viewport.py#L197)

- Require the complete selected matrix and one expected production digest.
  [generate-popular-home-design-review.py:23](../../scripts/generate-popular-home-design-review.py#L23)

**Supporting contracts and tests**

- Add stable section identities without removing published automation IDs.
  [UiAutomationId.kt:77](../../mobile/core/ui/src/commonMain/kotlin/com/supernutrition/core/ui/UiAutomationId.kt#L77)

- Measure actual boundaries and preserve header interactions across viewports.
  [HomeHeaderSpacingTest.kt:24](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/HomeHeaderSpacingTest.kt#L24)

- Prove the bubble tail remains visible below its measured panel.
  [HomeCarePromptLayoutTest.kt:33](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/HomeCarePromptLayoutTest.kt#L33)

- Reject incomplete or mixed-build reports and retain fitting legacy baselines.
  [test_home_review_contracts.py:26](../../scripts/tests/test_home_review_contracts.py#L26)
