# Home and Rewards spacing — 2026-10-10

Worktree: `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`; branch
`codex/figma-camera-flow`; base `5ee5de077866feed70b418ee2760af24322b1d22`.
Existing camera/review work remains part of this local, uncommitted branch.

## Findings recorded before implementation

1. Home uses a fixed 375×812 foreground and scales it to cover the viewport.
   A 375×932 production render clips the rhythm card's right edge: its bounds
   are `Rect(36.7, 506.2, 375.0, 701.3)`, leaving unequal 36.7/0 gutters.
2. Home's lower content cannot scroll into view on a 320×480 phone. The new
   `Do it` regression fails with “Semantic Node has no parent layout with a
   Scroll SemanticsAction.”
3. Shared navigation has unequal side-tab distances: Home bounds
   `(65,21,117,79)`, camera `(163,3,213,53)`, Rewards `(256,21,308,79)`.
   The camera-to-tab-center distances are 97 and 94.
4. Android overlays its bottom bar on Rewards without reducing the list
   viewport, and does not apply the bottom navigation inset. The baseline
   render shows a reward card behind the bar.
5. Rhythm/next-step outer gutters and widths differ slightly; metric inner
   padding and value/denominator baselines differ; Rewards uses a different
   outer gutter. Its transparent background also obscures the title in an
   unthemed render.
6. iOS owns Home in Swift and already reserves bottom navigation using
   `safeAreaInset` (80pt + 12pt). Swift Home has title-only top padding and no
   trailing content padding; its 20pt outer gutter differs from Rewards's
   16pt Intel fallback. Native tab horizontal positions are already symmetric.

Baseline command:

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest --tests '*PhotoAnalysisLayoutVisualTest*' --no-configuration-cache --console=plain
```

Fresh XML timestamp `2026-10-10T07:47:45.637Z`: 5 tests, 3 failures, 0 errors,
0 skipped. Both baseline screenshot tests passed; all three targeted defects
failed for the reasons above. Baseline images are in
`mobile/feature/photo-analysis-ui/build/reports/layout-design/home-before.png`
and `rewards-before.png`.

## Implementation and final verification

The first production revision compiled and ran 32 UI tests: 31 passed. The
remaining enlarged-text check scrolled only its title, so it was corrected to
scroll the complete next-step card before checking its description and action.
Regular, tall, short, wide, and Rewards renders confirm the initial three
geometry defects are corrected.

A separate enlarged-text review found another concrete grid defect: at
320×568/font scale 1.5, Protein's cell ends at y=417 while Movement ends at
y=445 because its units wrap. The new geometry regression was run before
changing measurement; fresh XML timestamp `2026-10-10T08:09:43.756Z` reports
1 test, 1 failure, 0 errors/skipped with those exact bounds. Both cells must
have the same row height and aligned progress bars while text wraps naturally.

Source review also found the new Android safeDrawing modifier padded the
whole backdrop. Move insets to the foreground/navigation content so the
existing art paints the full viewport, including system bars, with inset
content applied once.

Swift source syntax was parsed successfully with:

```sh
xcrun swiftc -frontend -parse mobile/apps/iosApp/PhotoAnalysisHostSupport.swift
```

The complete shared UI suite passed after these fixes: 35 tests, 0 failures,
0 errors, 0 skipped. Fresh XML timestamps span `2026-10-10T08:36:52.591Z`
through `08:37:31.142Z`. The unchanged-input automation registry result is
4 tests, 0 failures/errors/skipped (`08:13:17.904Z`, Gradle up-to-date).
Native visual execution remains unavailable on this Intel host without full
Xcode or a device.

The responsive review also found fixed pixel-width progress fills. The same
metric's bar visually fills more of its track as the viewport narrows; this
must preserve the existing relative visual fill instead. A pixel-based
regression compares the actual rendered accent/track proportions at widths
375 and 320, avoiding an implementation-shaped expected size. It was watched
fail before changing the bar: 1 test, 1 failure, 0 errors/skipped. Correct
the rendering using fractions derived from the original 132.75dp track,
keeping the original fill ratios, copy, and metric data.

The final large-text PNG also exposes a navigation spacing issue: at width
320/font scale 1.5 the shared pill artwork shrinks vertically, while its
fixed tab offsets and larger labels place the text below the pill body.
Acceptance: both labels and icons must remain inside the visible pill body
with symmetric top/bottom clearance, including larger text, without changing
tab semantics or shrinking touch targets. Record a failing geometry check
before adjusting tab arrangement/artwork measurement.

The corrected asset-based regression was watched fail with fresh XML
timestamp `2026-10-10T08:44:33.820Z`: 1 test, 1 failure, 0 errors/skipped.
Home's label bounds are `(43,55,115,73)` against the actual flat pill fill
`y=20.55..67.66` with required 4dp clearance. The original 1388×344 export
has flat fill rows 45..266 at both tab centers; the test measures that body
from the displayed artwork's bounds rather than a presumed shadow margin.

Verification before this final navigation adjustment:

```sh
./mobile/gradlew -p mobile qualityCheckJvmAndroid qualityCheckApple qualityCoverageJvm :apps:androidApp:assembleProductionDebug -Psupernutrition.runJvmCoverage=true --continue --no-configuration-cache --console=plain
./mobile/gradlew -p mobile :feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true --continue --no-configuration-cache --console=plain
```

The first command exits 1 solely because `qualityCheckApple` cannot execute
`xcodebuild -list` without full Xcode. JVM/Android quality, JVM coverage, and
production Debug APK assembly pass. Aggregate JVM coverage is 99.51% line /
89.21% branch across nine production modules; nutrition's strict floor passes
at 99.99% line / 100% branch. The separate shared iOS UI compile exits 0
(`BUILD SUCCESSFUL`, 50s), including the changed common UI and automation IDs.
This does not establish Swift/native execution or Apple lint evidence for
source sets absent from the Intel host's default target registration.

`graphify update .` completed after the main source changes: 20,144 nodes,
26,650 edges. Its existing warnings concern 111 empty JSON/code extractions
and two SQL files lacking `tree_sitter_sql`; the graph updated successfully.
It will be refreshed after the last navigation adjustment.

## Final layout result

- Home uses naturally measured, scrollable content with 24dp outer gutters,
  a centered 430dp width cap, 12dp section spacing, equal grid cells, shared
  value/unit baselines, and proportionate progress tracks. Decorative artwork
  fills the viewport independently of content and system insets.
- Rewards uses the same outer gutter and width cap, a legible themed title
  and background, and trailing list space. Android reserves the measured bar
  and consumes safeDrawing insets once; neither final reward nor Home's action
  is covered by navigation.
- Both shared navigation tabs have mirrored centers and equal icon/label
  baselines. The pill retains its 86dp height on narrow phones and the tab
  stack centers vertically as text grows. Default artwork is still 347×86.
- Swift Home and Intel Rewards use 24pt outer gutters, 16pt vertical content
  padding, matching 430pt caps, and grouped heading/body spacing. The existing
  native safe-area reservation remains the sole owner of bottom space.
- Existing content, reward order, IDs, callbacks, and the preceding camera
  implementation remain intact. No business or reward policy changed.

Final Android/UI command:

```sh
./mobile/gradlew -p mobile qualityCheckJvmAndroid :apps:androidApp:assembleProductionDebug --continue --no-configuration-cache --console=plain
```

Exit 0, `BUILD SUCCESSFUL` in 4m5s. All 36 shared UI tests pass with 0
failures/errors/skipped; XML timestamps are `2026-10-10T08:50:08.779Z` through
`08:51:23.315Z`. This includes 13 responsive layout tests plus the existing
camera, Home, Rewards, and navigation contracts. The four automation registry
tests remain passing with unchanged inputs. UI detekt common/JVM/Android
reports have zero errors and are regenerated for changed sources; unchanged
reports are explicitly Gradle up-to-date.

Production APK: `mobile/apps/androidApp/build/outputs/apk/production/debug/androidApp-production-debug.apk`.
Screenshots: `mobile/feature/photo-analysis-ui/build/reports/layout-design/`.
Inspected regular/tall/short/wide/larger-text Home, larger-text grid/navigation,
Rewards, and final scrolled rewards. Before PNGs are retained for comparison.
These are production-composable renders, not emulator/device execution.

Host: macOS15.7.3 Intel x86_64, Android SDK36; Xcode selection is
`/Library/Developer/CommandLineTools`. No device/simulator execution is claimed.
The nine-module JVM coverage result excludes the UI/native-equivalence
denominators; their device evidence remains pending. No manifests, baselines,
exclusions, or coverage denominators were relaxed for this spacing correction.

Final shared iOS UI compile exits 0 (`BUILD SUCCESSFUL`, 58s) after the
navigation fix, using the explicit Intel-host iOS target flag above. Swift
syntax parse remains passing with unchanged source since that parse.
Final `graphify update .` exits 0: 20,145 nodes, 26,656 edges. Its known empty
extraction/SQL dependency warnings remain; `git diff --check` also exits 0.
Adversarial and edge-case review results are recorded below when complete.

Related suites included by the final JVM/Android gate are also passing:

| Module | Tests | Failures / errors / skipped | XML timestamp range (UTC) |
|---|---:|---|---|
| core/ui | 4 | 0 / 0 / 0 | 08:13:17.904; unchanged inputs, up-to-date |
| photo-analysis-presenter | 14 | 0 / 0 / 0 | 08:19:27.306–08:19:32.128; unchanged inputs, up-to-date |
| photo-analysis-ui | 36 | 0 / 0 / 0 | 08:50:08.779–08:51:23.315; rerun |
| photo-analysis | 19 | 0 / 0 / 0 | 08:49:57.849–08:50:09.244; rerun |

All timestamps above are on 2026-10-10. Reports live in each module's
`build/test-results/jvmTest/`; quality XML lives in `build/reports/detekt/`.

## Findings from full-diff review, before fixes

Both independent reviewers read the complete tracked/untracked diff (5,474
lines) and source/resource paths. Concrete camera integration findings exposed
while reviewing the earlier camera work alongside this spacing correction:

1. Review choices live in `remember` inside a conditionally rendered review.
   Backgrounding disposes that composition, and Android settings/configuration
   recreation also discards it while the feature/photo/note are retained.
   Acceptance: selections and custom meal survive transient background and
   supported restoration; replacing the photo resets them.
2. Overlapping square click regions include transparent sticker pixels. At
   375dp, palette-local `(232.5,78.5)` contains visible broccoli but falls in
   the later carrot box whose pixel alpha is zero. Acceptance: real pointer
   input on visible broccoli toggles broccoli only, while semantic/keyboard
   selection still works for every sticker and component ID.
3. The public feature's `ReplacePhoto` action does not reset a previously
   confirmed draft/note, although ScanAgain/PhotoCaptured do. Acceptance:
   a valid replacement clears old confirmation and note without introducing
   inferred quantities or altering the reducer's accepted transition.

The source/resource findings above are verified by reading/decode, not native
execution. Regression failures and final full checks are recorded after fixes.

Additional acceptance conditions from review, recorded before fixes:

- Long dish names at font scale1.5 must grow naturally, without text layout
  overflow. All unfamiliar/duplicate components must scroll into view on a
  regular phone, rather than being constrained by the fixed spacer.
- Recovery/status actions must remain reachable on short landscape viewports
  with larger text. Close's original artwork must have a 48dp touch area.
- iOS success must call completion independently of cancellation cleanup.
  Production currently wires Close to `session.captureCancelled()`, clearing
  native preview while successful Ready/CandidateReview ignores its failure
  event. Separate the two callbacks and retain the confirmed session draft.
- Provided degradation information and unfamiliar evidence uncertainty must
  remain available. Pantry display names must be localizable while sticker
  IDs and matching aliases remain stable.

The accepted ReplacePhoto regression was watched fail before production edits:
fresh XML `2026-10-10T09:18:44.677Z`,1 test / 1 failure/0 errors/skipped. The old
confirmed draft (`Previous meal`) remained nonnull after a valid replacement.
The first UI regression command stopped at a missing `click` extension import;
that compile failure is not runtime evidence and the corrected UI run follows.

The corrected camera edge-case run compiled and was watched fail before any
production fixes: fresh XML `2026-10-10T09:24:50.363Z`,7 tests / 7 failures,
0 errors/skipped. It reproduced both selection-loss paths, the transparent
sticker interception, missing scroll reachability for excess evidence and
short recovery screens, dish text overflow, and Close's41dp touch target.

Evidence/localization regressions were watched fail before rendering fixes: XML `2026-10-10T09:34:42.032Z`,3 tests / 3 failures / 0 errors/skipped. Supplied degradation text was absent, uncertainty descriptions were incomplete, and host-localized Carota still rendered as Carrot in accessibility. The new optional copy map alone did not change rendering.

## Review triage and corrections

Deduplicated camera findings are small implementation patches within the
previously requested camera flow; no frozen product intent, reward policy, or
Home navigation design changes were needed. The review-loop count stays 0.

| Finding | Severity | Category | Correction |
|---|---|---|---|
| Choices lost on background/restoration | high | patch | Session-owned host selection flow; saveable standalone fallback |
| Transparent sticker rectangles choose another food | high | patch | Loaded artwork alpha and draw-order pointer dispatch; semantic clicks retained |
| Accepted replacement retains old draft/note | high | patch | Reset only after the reducer accepts the transition; ignored events preserve state |
| Extra evidence clipped | medium | patch | Naturally growing, always-scrollable review content |
| Long dish names clipped | medium | patch | Equal measured card heights with a minimum, unrestricted wrapping |
| Recovery controls unreachable | medium | patch | Scrollable recovery column on short/larger-text viewports |
| Close touch target too small | medium | patch | Centered 48dp minimum hit bounds; original artwork anchor/size retained |
| iOS success calls cancellation cleanup | high | patch | Separate completed/closed callbacks; success keeps photo and draft |
| Degraded review omits its supplied explanation | medium | patch | Conditional degradation caption, absent for ordinary Figma review |
| Food evidence semantics omit uncertainty/provenance | medium | patch | Complete supplied description for familiar and unfamiliar evidence |
| Pantry names fixed to English | low | patch | Host-owned localized copy map, stable IDs/aliases and English fallback |

The native callback/touch-area correction has source-level evidence and Swift
syntax checks. UIKit camera execution and Kotlin native interop cannot be
verified on this host without full Xcode; shared/JVM renders do not cover that
boundary. Native success acknowledges the reviewed selections without creating
nutrition values or claiming a persistent meal record.

The first patch compile found two integration errors: artwork size is a scalar
Dp, and Compose's loading-placeholder extension is internal. They were corrected
with scalar sizing and public ImageBitmap dimensions; installed Compose 1.11.1
uses a 1×1 loading bitmap, while all exported palette/badge images exceed that.
Neither compile failure is described as a successful runtime check.

The production iOS capture-only navigation gap is pre-existing (medium/defer), recorded in `deferred-work.md`. The existing Swift Home/Intel Rewards spacing lives in the UIAcceptance target; this change does not claim a production iOS Home rollout.

## Verification after all review patches

The final read-only JVM/Android gate and production APK assembly both succeed
(exit 0,33s), including fresh presenter payload/copy checks:

```sh
./mobile/gradlew -p mobile qualityCheckJvmAndroid :apps:androidApp:assembleProductionDebug --continue --no-configuration-cache --console=plain
```

All affected suites pass with 0 failures/errors/skips. The earlier layout counts
above describe the pre-review checkpoint; these are the final results:

| Module | Tests | XML timestamps on 2026-10-10 UTC | Input status |
|---|---:|---|---|
| core/ui | 4 | 08:13:17.904 | unchanged; Gradle up-to-date |
| photo-analysis-presenter | 17 | 09:55:13.459–09:55:16.518 | final payload/copy tests rerun |
| photo-analysis-ui | 47 | 09:51:19.101–09:52:20.336 | all layout/camera/evidence regressions rerun |
| photo-analysis | 23 | 09:51:06.546–09:51:15.419 | accepted/ignored replacement and selection lifecycle rerun |

Current changed JVM/Android source reports contain zero issues. The coverage
gate passes at 99.55% line / 89.25% branch across the nine declared host production
modules. Domain/nutrition remains 99.99% line / 100% branch. The new shared selection
and confirmation payloads have no missed lines or branches in the presenter
Kover XML. UI/native-equivalence denominators remain pending device execution.

The combined platform gate reaches Android success but exits1solely because
`qualityCheckApple` cannot run `xcodebuild -list` with CommandLineTools selected.
No Apple gate success or native camera/device execution is claimed.

The actual changed shared UI compiled for iosArm64 in the explicit-target run.
A following unchanged-input compile-only command confirms success (exit 0,5s):

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true --continue --no-configuration-cache --console=plain
```

Both changed Swift hosts pass `xcrun swiftc -frontend -parse`. This is syntax
validation, not framework linking or device execution. Read-only raw Apple
source checks report zero issues in changed files; their remaining eight
issues belong to RewardsViewController.kt and SharedKitPhotoAnalysisSession.kt,
verified byte-for-byte unchanged from 5ee5de0. Feature/sharedKit reports reran at
09:59:25.721 and 09:59:31.501 UTC; native photo's 09:56:58.754 report is up-to-date
with unchanged inputs. The new callback type was moved to its matching file
before the final source check, resolving the new naming finding.

The current camera review PNG was inspected after the patches: original
artwork/dimming/zoom, normal dish heights, palette positions, and footer anchor
remain intact. Home/Rewards regular and larger-text/short/wide previews and
last-item visibility checks remain passing. All are production-composable
renders with fixture data, rather than device screenshots.

No commits, pushes, deployments, coverage relaxations, or broad baselines were
made. The user can review the local changes on `codex/figma-camera-flow`.

Final graphify update succeeds after all code changes: 20,211 nodes and 26,831 edges. Its existing empty JSON extraction and missing SQL-parser warnings remain. Final whitespace checks pass.

Production APK: 250,151,080 bytes; SHA-256 `6985c4680e92bbfc80ca40a884723ca40bf650d5f3c056816f5df0b5753dec28`.

## Follow-up: anchor the capybara to the rhythm panel

User finding on 2026-10-10: the capybara floats above the panel, unlike the
Home design. Recorded before changing production code.

Live Figma Home/Main (`14:125`) confirms that Capybara (`14:153`) is at
`x=92.8, y=262.4`, with a `182.8 × 182.8` image slot. Card/Rhythm (`14:174`)
begins at `x=24, y=429`, so the image extends 16.2 points behind the card.
The card is painted in front of the mascot's lower edge. The authenticated
browser screenshot and property panel establish these measurements; after
one successful Home design-context retrieval, further MCP calls reached the
seat limit.

The current natural layout puts a 12-point gap between the mascot slot and
the card, leaving the paws visibly suspended above the panel. Acceptance:
the centered mascot and card form one measured composition, with the design's
16.2/182.8 overlap ratio retained as the mascot shrinks on shorter screens;
the panel covers the lower artwork edge and text/actions remain scrollable.
Keep the existing asset, responsive sizing, symmetric gutters, and card copy.
Add a render/layout regression, observe its failure, then fix the composition
and rerun the complete shared UI suite and affected build/quality gates.

The focused regression failed before the fix (exit 1; one test, one failure,
zero errors/skips; XML timestamp 11:03:00.733 UTC). At 375×812 the mascot slot
ended at y=377 and the panel began at y=389, confirming the unwanted 12-point
gap. Log: `/tmp/supernutrition-home-mascot-red.log`.

The fix groups the image and rhythm card in one naturally measured column,
using a negative gap proportional to the Figma overlap. The later card paints
over the mascot's lower edge. Parent section spacing no longer separates this
composition; the following next-step card retains its 12-point clearance.
The regular and larger-text production renders were inspected and the paws
now meet the panel edge.

The full shared UI suite succeeds (exit 0, 1m24s): 48 tests, zero
failures/errors/skips, with fresh XML timestamps 11:04:43.541–11:05:23.539 UTC.
The new regression verifies overlap, centering, and foreground panel paint
at 375×812, 375×932, 320×480, 320×568/font 1.5, and 600×800.

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest --no-configuration-cache --console=plain
```

Log: `/tmp/supernutrition-home-mascot-ui-green.log`. Previews are under
`mobile/feature/photo-analysis-ui/build/reports/layout-design/`; the corrected
regular image is `home-mascot-375x812-1.0.png`. These are JVM/Skiko production
composable renders, not device execution.

Follow-up final verification:

```sh
./mobile/gradlew -p mobile qualityCheckJvmAndroid :apps:androidApp:assembleProductionDebug --continue --no-configuration-cache --console=plain
./mobile/gradlew -p mobile :feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true --continue --no-configuration-cache --console=plain
graphify update .
git diff --check
```

The JVM/Android gate succeeds (exit 0, 13s), with zero shared UI source issues
and coverage unchanged at 99.55% line / 89.25% branch; nutrition remains
99.99% line / 100% branch. The Android APK was rebuilt in the preceding
combined run and is legitimately up-to-date in the final Android gate.
The feature suite also reran: 23 tests, zero failures/errors/skips, with XML
timestamps 11:07:11.742–11:07:32.936 UTC. Core/UI's four tests and the
presenter's 17 tests are unchanged-input, up-to-date results from the earlier
verified checkpoint. Shared UI remains 48 fresh passing tests.

The initial combined command included the iOS device-target flag with the
quality gate. That configuration also selected Apple source analysis and
exited 1 solely on the same eight pre-existing issues in
RewardsViewController.kt and SharedKitPhotoAnalysisSession.kt; neither file
differs from base 5ee5de0. Android assembly and the actual changed shared UI
iOS compilation both completed. The separate iOS compile succeeds (exit 0,
4s, now up-to-date with the compiled change). Full Apple lint and iOS/device
execution are not claimed. Logs:
`/tmp/supernutrition-home-mascot-final-gates.log`,
`/tmp/supernutrition-home-mascot-android-verified.log`, and
`/tmp/supernutrition-home-mascot-ios-ui-verified.log`.

Host: Intel macOS 15.7.3 with Android SDK 36; no Android device or full
Xcode/iOS runtime available. The corrected production APK is 250,152,074
bytes, modified at 11:10:54.552 UTC, SHA-256
`e3c9b9836dde05f3c327d1da6d6213645aedf48a3530008da0e375e169d25949`.

Graphify succeeds (exit 0), updating the code graph to 20,218 nodes / 26,851
edges / 1,936 communities. Existing empty-JSON and SQL dependency warnings
remain; the HTML size limit skips regeneration of the large graph preview.
The JSON and graph report update correctly. Log:
`/tmp/supernutrition-home-mascot-graphify.log`.

All 16 spec source links resolve, and final whitespace checks pass. Work
remains local and uncommitted on `codex/figma-camera-flow`, base
`5ee5de077866feed70b418ee2760af24322b1d22`, in
`/Users/anastasia/.codex/worktrees/a704/SuperNutrition`. This follow-up changes
only the Home composition, its visual regression, and the two layout
spec/evidence documents, preserving the existing camera/Rewards work.
