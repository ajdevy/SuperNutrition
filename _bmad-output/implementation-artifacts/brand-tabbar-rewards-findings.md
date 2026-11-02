# Brand and navigation findings — 2026-10-13

Recorded before production corrections in the isolated worktree.

1. **Wordmark:** `HomeHeader.kt` renders both dotted i glyphs in Nutrition. The sprout is offset a fixed 37dp from the centered box and 7dp down. Acceptance: replace the first ordinary dot and anchor the accent using the actual glyph, including wrapping and font scaling; retain the second dot and the accessible brand name.
2. **Selected/pressed tabs:** `PhotoAnalysisMainBottomBar.kt` changes both icon and label color, while default `clickable` indication draws on the 72×58dp touch surface. Acceptance: labels and touch background remain constant during selection/holding, icon tints change, touch target/callback/selected semantics survive.
3. **Rewards footer:** `PhotoAnalysisMainLayout.kt` reserves a separate 83dp row over the Material backdrop. `RewardsScreen.kt` paints its own paper only above that row. Acceptance: Rewards fills the inset viewport behind floating navigation, the surrounding surface continues its paper, and bottom scroll padding allows the entire final card to clear the camera/pill on small screens and enlarged text.

## Verification approach

Fresh JVM production-composable render tests exercise the three defects before the fixes and afterward. Existing navigation/geometry tests guard Home clearance, gutters, tab callbacks and last-reward reachability. Host evidence does not establish physical-device acceptance. Independent review agents are prohibited in this side conversation; the implementation will receive an inline review, without calling it independent.

## Confirmed pre-fix regressions

After placing Skiko cache in a writable directory and selecting headless AWT, all four checks executed and failed for their stated defects:

- First i: expected U+0131, rendered ordinary i.
- Tab label: 121 pixels changed upon selection.
- Held tab: all 4,176 pixels of the 72×58dp area changed.
- Rewards list bottom with a 20dp host inset: 709px instead of 792px, exactly the reserved 83dp navigation row.

The original report and rendered PNGs are retained in `mobile/feature/photo-analysis-ui/build/reports/brand-navigation-before`. Earlier setup-only failures (read-only Gradle/Skiko caches and AWT registration under the macOS sandbox) are not treated as product regressions.

## Inline review

Reviewed the isolated delta for navigation state, accessible brand text, click-area semantics, safe-area ownership and scroll occlusion. Existing Home geometry is preserved; Rewards receives its navigation scroll inset only when the shared shell actually overlays the bar, leaving standalone/native-reserved Rewards at its original 16dp padding.

A concrete alignment concern remains to reproduce before closing: glyph bounds use physical left-origin coordinates, while `Modifier.offset` and a Box's TopStart child alignment follow layout direction. The first-i sprout can therefore detach in an RTL host. A dedicated render check is being run before changing that placement.

The RTL concern was reproduced: the first-i glyph was at x=221.9..228.5px, but no sprout pixels existed above it. The other four checks stayed green. Before the correction, the report and PNG were retained as `regression-rtl-before.xml` and `brand-rtl.png` in the before folder. The brand Box now uses absolute top-left alignment and the accent uses absolute offset, matching the text API's physical coordinate origin. The final verification re-executes the full UI suite, Android/iOS shared compilation and all module source-set lint tasks after this patch.

## Coverage and physical follow-up — 2026-10-14

Recorded before adding follow-up tests. Fresh UI XML from the managed worktree reports 3,353 covered / 85 missed lines and 541 covered / 362 missed branches. HomeBrand has 100% lines and branches; remaining misses are spread across existing composables and their generated recomposition guards. Static render cases do not establish correctness after a model/selection/callback changes in an existing composition. Acceptance: assert live updates, unchanged sibling state, callback freshness, recovery/diagnostic transitions and empty/nonempty evidence with the production composables; reach the unchanged 85% floor without policy changes or coverage exclusions.

Physical acceptance is still unverified for this correction. The currently connected single endpoint is `adb-PHYSICAL_DEVICE_SERIAL-97BOdK._adb-tls-connect._tcp` (DNY_NX9, API 36, Wi-Fi). Acceptance: install the worktree's compatible build without wiping data, inspect real Home/Rewards screens, verify tab selection and held press appearance, and scroll the final reward clear of navigation. Host-render evidence must remain separate from this new device evidence.

## Follow-up verification closed — 2026-10-14

The new refresh tests cover live models, independent callback changes, unrelated host-header refresh, session-flow replacement, recovery, null/empty evidence, research prompts, manual-source labels and row reindexing. Fresh full UI execution: 117 tests in 23 classes, zero failures/errors/skips. Raw coverage is 3,422 / 3,438 lines (99.5346%) and 768 / 903 branches (85.0498%). All 58 added executable correction lines and their branch outcomes are covered. No policy or coverage filtering changed. Current module Detekt reports contain zero findings.

Physical production-debug evidence on HONOR DNY_NX9 / API 36: two existing route/launch tests and all three new native visual tests passed. Native screenshots confirm the measured sprout, fixed label pixels, icon-only selection, unchanged tab pixels while held, Rewards paper beside the pill, and final-card clearance. The held test also asserts that releasing its injected touchscreen gesture actually selects Rewards, preventing a false pass from an undelivered press.

An earlier CLI capture lost the app foreground and was discarded from the report. Screenshot helpers now validate the package before and after capture; final acceptance images come from package-guarded Android instrumentation. This tooling interruption is separate from the successful native test runs. The parent UI module's 211 imported files still match the baseline SHA-256 inventory.
