---
title: 'Seat the Home capybara on the garden floor'
type: 'bugfix'
created: '2026-10-23'
status: 'done'
baseline_commit: '465f1933f36f4b8631fa923c8d299f693b2da1a7'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="The user directly requested this visual correction; authorized routine implementation needs no extra approval">

## Intent

**Problem:** The current Home mascot appears to float near the backdrop horizon. Its natural layout position follows text, insets and available height, while the garden is independently stretched to the screen. The user prefers the capybara seated on the floor of the picture; sitting on the lower panel edge is an acceptable fallback.

**Approach:** Give the existing mascot a stable relationship to the existing garden floor. Prefer preserving the measured foreground flow while positioning the decorative garden from the measured paw/contact position, and draw a restrained contact shadow beneath the paws. Both full garden and frosted glass must use exactly the same transform. Do not merely add an arbitrary device-specific vertical offset.

## Boundaries & Constraints

**Always:** Preserve current artwork, sprite timeline/reduced-motion behavior, text, safe-inset ownership, automation IDs, typography, all four metrics, current foreground spacing and working Home/Profile/history navigation. Keep foreground measurement and scrolling readable on short phones and enlarged text; preserve the ordinary phone no-scroll behavior. Use one source of truth for scene geometry and share it between the visible background and translated glass. Resolve geometry in one coordinate space, and avoid self-sustaining measure/state feedback loops. Any scene crop must cover its canvas and remain finite when its mascot is outside the viewport during scrolling. User-provided visual intent takes precedence over stale historical panel-overlap specifications.

**Ask First:** Replacing the garden/mascot artwork or making a broad layout/product redesign.

**Never:** Change the installed product name, business/session behavior, dependency versions, lifecycle/coverage policy, iOS architecture or remote repositories. Do not generate replacement raster images, rewrite the UI as a screenshot, shrink text, hide controls or claim iOS runtime execution on the accepted Intel-only host. Preserve unrelated work.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected behavior | Boundary handling |
|---|---|---|---|
| Native ordinary phone |360×780, actual system insets, font1.0| Visible paws rest below the horizon on the garden floor, with contact shadow; complete metrics remain above navigation | Preserve no-scroll first frame |
| Tall/wide phone |412×915 or600×800| Same physical relationship to the floor, centered mascot and covered backdrop | Use measured geometry rather than one viewport offset |
| Short phone / enlarged text |320×568 or360×780/font2.0| Readable scrolling retains the scene relationship when mascot is visible | Clamp decorative transforms for offscreen contact points |
| Initial layout / missing bounds | Before coordinates exist | A valid filled fallback garden renders | No invalid scale/division or layout loop |
| Animation / reduced motion | Existing71-frame sheet, reduced motion | Paws and shadow stay grounded across frames | Preserve existing animation schedule |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis-ui/.../HomeBackdrop.kt` and `HomeBackdropState.kt` — measured full-garden bounds and background-only glass rendering.
- `mobile/feature/photo-analysis-ui/.../HomeAnimatedMascot.kt` — sprite cropping, timeline and mascot canvas; measure contact and add restrained shadow without altering frame schedule.
- `mobile/feature/photo-analysis-ui/.../HomeScreen.kt`, `HomeMeasuredLayout.kt` — existing natural foreground layout; preserve its flow and callback ownership.
- `mobile/feature/photo-analysis-ui/src/jvmTest/.../HomeHeaderSpacingTest.kt`, `HomeNoScrollLayoutTest.kt`, `NativePhoneHomeLayoutTest.kt` and `PhotoAnalysisLayoutVisualTest.kt` — responsive geometry and visual checks.
- `_bmad-output/implementation-artifacts/capybara-grounding-2026-10-23/` — findings, red/green evidence and actual emulator screenshots.

## Tasks & Acceptance

**Execution:**
- [x] Shared Home backdrop/mascot files — bind decorative floor and paw contact through one reusable finite scene transform; keep glass aligned and add a contact shadow.
- [x] Focused UI/geometry tests — demonstrate the floating relationship before fixing, cover regular/tall/wide/large-text layouts and offscreen/missing geometry, then rerun the relevant full UI module.
- [x] Verification evidence — build/install the new production APK, inspect native normal and enlarged-text results, record source/hash/device, run diff checks and graphify update.

**Acceptance Criteria:**
- Given the ordinary Home screen, when rendered, then the mascot visibly sits on floor pixels with a shadow touching the paws instead of hovering at the horizon.
- Given background and frosted metric panel, when the scene position changes, then both render the same garden coordinates without an offset seam.
- Given existing phone layout and actions, when interacting or changing font size, then controls remain usable and the existing measured spacing/no-scroll or readable-overflow behavior survives.
- Given the Intel-only host, when reporting verification, then Android evidence is fresh and no iOS runtime result is claimed.

## Spec Change Log

## Verification

Record the existing screenshot as the immutable before reference. Watch a focused regression fail before the fix, then run the complete `:feature:photo-analysis-ui:jvmTest` with strict verification and no configuration cache. Compile/package production Android and the changed shared UI for iosArm64 with the explicit device flag, sequentially. Only one Gradle process per worktree. Capture the real task-owned API35 emulator at font1.0 and2.0, inspect each returned PNG immediately, restore font settings, and stop the owned emulator. Run `git diff --check` and `graphify update . --no-cluster`. Full physical Android policy evidence remains unavailable; accepted Intel iOS runtime restriction is unchanged.

Verification outcome: full UI237/237; new geometry19/19 lines and24/24 branches; changed-source lint reports0 findings; Android package and explicit shared iosArm64 compilation pass. Native screenshots/manual checks cover360×780,412×915 and200% text including scroll, with installed APK hash verified. Broader UI branch coverage66.25%, typed-analyzer warnings and physical/Intel runtime limitations are explicitly retained in the report. Graphify code update completed.

Review outcome: Blind Hunter found one medium test-visibility issue; Edge Case Hunter found none. The reference now shares measured backdrop state and samples opaque body pixels above the shadow. Bounded re-review confirms closure; the full237-test module rerun passes. No production change was needed after native capture.

## Suggested Review Order

**Scene grounding**

- Align the existing garden floor to measured paws with bounded, aspect-preserving coverage.
  [HomeGardenGeometry.kt:16](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeGardenGeometry.kt#L16)

- Measure the sprite contact and draw its shadow without changing foreground layout.
  [HomeAnimatedMascot.kt:50](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeAnimatedMascot.kt#L50)

**Shared backdrop**

- Use the same absolute garden coordinates for full scene and frosted glass.
  [HomeBackdrop.kt:73](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeBackdrop.kt#L73)

**Evidence**

- Reproduce sky beneath tall-screen paws and verify floor/glass pixels.
  [HomeGardenGroundingTest.kt:48](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/HomeGardenGroundingTest.kt#L48)

- Compare sprite body pixels against an identically positioned garden.
  [PhotoAnalysisLayoutVisualTest.kt:54](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisLayoutVisualTest.kt#L54)

- Inspect real before/after screenshots, final tests and explicit quality limitations.
  [README.md:1](capybara-grounding-2026-10-23/README.md#L1)
