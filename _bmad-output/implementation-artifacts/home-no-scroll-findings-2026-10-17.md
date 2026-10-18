# Home no-scroll findings — 2026-10-17

Checkpoint: owned clean worktree `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`, branch `codex/figma-camera-flow`, HEAD `7ec6150`. The separate primary main checkout remains unrelated and preserved.

## Finding recorded before edits

`HomeScreen.kt` always applies `verticalScroll` and its mascot is bounded to 160–182dp. The current installed main-integration APK's first native 360×780dp frame clips the helpful-step card under navigation. Existing tests often scroll to the end before asserting clearance; that verifies reachability, not the user's new initial-frame requirement.

Read-only source exploration estimates even removing the mascot cannot fit the current single-column composition on 320×568dp or at 200% text on ordinary phones. These estimates are not native evidence. Exact clipping and fit outcomes must be measured from new screenshots and focused tests. On these cases a smaller-font solution is explicitly forbidden by the user; preserve readable content and hand the design constraint to the designer. Native API35 nonlinear font scaling must not be equated with linear JVM 200% height estimates.

Acceptance: feasible variants show complete initial content above floating navigation with zero scroll range, using actual child measurements and unchanged typography. Infeasible variants remain clearly labelled as designer-required and are not called fixed. Red/green attempts, native runtime metadata and every original screenshot are retained separately.

## Verified baseline before production correction

The valid focused red is **4 tests / 1 failure / 0 errors / 0 skipped**. At 360×780dp with 68dp top and 48dp bottom native safe insets, the initial helpful-step card is `(24,621)-(336,696)` while the highest navigation control starts at y=652; it overlaps by 44dp and misses the required 8dp clearance by 52dp. Tall-phone natural fit and the two readable-overflow cases already pass. The immutable native before screenshots confirm clipping at 360×780/100%, 320×568/100%, 320×426.67/100%, 360×780/200% and 600×960/200%.

Three earlier 4/4 attempts are preserved as invalid test-helper diagnostics, not three product defects. `TextLayoutResult.hasVisualOverflow` in this renderer compares the full paragraph alignment canvas to an intrinsic measured text frame: the visible centered date has an 87px text frame but a 272px canvas, with actual line span 86.95px. The corrected helper checks actual ink spans, complete last-line character coverage, natural line height, ellipsis and fontScale; its 1px tolerance applies only to raster rounding. It retains strict actual initial-frame bounds and 48dp controls. No production font/date change was needed. Evidence is under `mobile/build/home-no-scroll-2026-10-17/focused-corrected-red/`.

## First measured-layout focused green

The same four focused cases now pass **4/0/0/0**, exit 0, after actual child measurement replaced fixed art allocation. The native-inset phone case proves complete initial-frame card/text, zero scroll range, preserved font scale/tokens, meaningful artwork/foreground overlap and 48dp actionable controls. Tall natural fit remains green; short and 200% cases retain readable overflow rather than being scaled to a false fit. Source changes are restricted to HomeScreen and one small measured-layout helper. Evidence is `focused-first-green/`; this is JVM evidence, not yet an updated native APK result. The now-authorized staged Rewards redesign was integrated into these compilation inputs, while its full UI/source verification remains pending.

The first combined full attempt stopped during task resolution in 8s: this KMP module does not expose `detektAndroidUnitTestSourceSet`. No full tests or source gate ran. The helper now uses the canonical `qualityCheckJvmAndroid` aggregate, which inventories the actual JVM/Android source sets, plus explicit changed UI/feature/sharedKit `iosArm64` compilation and Android production assembly. The failed configuration attempt remains preserved; this does not remove a required check or change policy. Native WindowManager confirms Home uses its real 136px status/cutout and 96px navigation insets exactly once in the baseline case; the additional loss of height is not a duplicated host padding bug. SDK viewport overrides inherit the explicitly recorded Pixel5 emulation overlays, so they are size/density evidence and not device-model equivalence evidence.

## Readable reflow regression recorded before width changes

The extended focused red is **6 tests / 2 failures / 0 errors / 0 skipped**, saved in `mobile/build/home-no-scroll-2026-10-17/focused-wide-red/`. The existing four fit/overflow cases still pass. At 412×915dp and fontScale 1.3, the initial `home_next_step` is not displayed. At 800×1280dp and fontScale 2, the helpful-step card is `(209,1177)-(591,1232)` while the highest navigation control starts at y=1152: the card overlaps by 80dp and misses the required 8dp clearance by 88dp. These failures were witnessed before adaptive width/reflow changes.

Acceptance for both enlarged-text cases is complete initial content with zero scroll range, unchanged exact system fontScale and text tokens, complete line/character coverage, 48dp controls and at least 120dp proportional artwork with foreground overlap. Home must use available readable width and actual child measurements to choose rows/columns before declaring a designer stop. Normal-text tablet content retains its existing 430dp cap. Existing narrow/short readable-overflow assertions remain required; shrinking fonts or dropping content cannot satisfy the new fit cases.


### Measured reflow green

`focused-wide-green` passed6/0/0/0 in fresh`HomeNoScrollLayoutTest` XML after the widened enlarged-text container and shared-child measured layouts. The preceding `focused-wide-red` was6/2/0/0: the412×915/130% initial helpful card was not displayed; the800×1280/200% card extended80dp past the highest control. Both now pass initial-frame, exact font-scale, complete text,48dp actions and120dp art assertions. The original320×568/100% and360×780/200% readable-overflow safeguards remain green.

A native diagnostic with the first measured implementation shows the360×780/100% full Home initial frame clearing the controls by11.5dp, with native scrollable=false. The old capture helper incorrectly required the `Do it` description child itself to be clickable. UI Automator exposes the identical48dp clickable frame on its parent; the helper now resolves that real ancestor, preserving touch and clearance checks. Raw evidence is unchanged;`diagnostic/720x1560-320dpi/font-100/classification-audit.json` records the correction. Final native matrix must use the final APK, not this diagnostic digest.

### Full-candidate Home findings recorded before assertion and lint fixes

The fresh full-final-candidate run keeps all six `HomeNoScrollLayoutTest` cases green. Two older Home assertions fail: the 320×720dp/150% wrapped-value case now forms readable measured pairs, with Fiber `(36,464)-(156,540)` and Water `(164,464)-(284,540)`; its old single-column requirement incorrectly labels these separate aligned cards as overlapping. The short-wide navigation probe samples a `HomeCard` metric surface after reflow while expecting the surrounding `HomeCream` panel. Acceptance remains nonoverlapping aligned row groups, complete readable values and visible Home content beside the floating controls; a probe in the panel's own padding must detect any opaque navigation strip.

The same run reports actual lint findings: `HomeMeasuredLayout` and `HomeContent` each take six parameters where the permitted maximum is five, and the fit-test expression-body signature needs single-line formatting followed by a newline. Fix these by sharing the actual Home viewport/navigation/artwork/spacing/width settings and formatting the helper. Do not suppress lint, revert measured reflow or relax touch/text visibility assertions.

### Post-review test-helper lint

The post-review run reports 167 UI tests with zero failures. Fresh lint identifies only the Home fit helper's length in this slice: `assertFittingHome` has 62 lines against the 60-line limit. Extract the coherent initial-text readability/bounds checks into a helper, retaining every assertion and changing no production behavior or APK inputs.
