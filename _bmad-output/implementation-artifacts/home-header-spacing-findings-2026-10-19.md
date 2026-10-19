# Home header/spacing findings before implementation

1. `HomeMeasuredLayout.homeVerticalPlan` spends its budget on the mascot before section spacing and allows `itemGap` to fall to zero. Acceptance: reserve the shared 16dp section gaps first, then allocate decoration; retain readable overflow if the budget is insufficient.
2. Care-to-mascot placement currently has no section gap, while header/care and rhythm/helpful-step use a variable 0–12dp gap. Acceptance: measure and assert the same section spacing at all three boundaries, keeping the intentional mascot/rhythm overlap.
3. `HomeHeader` renders the obsolete wordmark and weekday/date; the user explicitly removed both from the desired Home. Acceptance: neither appears in the Home text tree at any tested font size, while badge/profile and root `home_title` accessibility remain.

The existing dirty files belong to the preceding, completed designer handoff and its capture-helper output-directory option. The ongoing branch is the same user-authorized app work; preserve those files and old galleries, without switching branches or staging unrelated changes. Planning authorization is the user's explicit current request, not a new publication approval.

## Fresh regression evidence

- Native before image `../design-review/home-spacing-2026-10-19/before/720x1560-320dpi/font-100/home.png` was inspected immediately after capture. Its XML shows a 0dp rhythm/helpful-step gap. The same installed APK is the prior verified production digest `7f7296bd7282a86dc63fe4feaad0d4a19706c8b4ca231c39607ff0e3abc1f26c`.
- `mobile/build/home-header-spacing-2026-10-19/focused-red/`: 9 fresh JVM tests, 8 failures, 0 errors/skips. Both removal assertions fail; the six spacing assertions measure 0, 8 or 12dp rather than the required 16dp. The retained identity/badge/profile test passes.
- First green attempt compiled production and passed all 4 core UI tests, but the UI test compiler rejected `DpRect.center`; zero UI tests ran. The assertion was corrected to calculate the midpoint from measured left/right edges.
- `focused-green-2/`: all 9 focused UI tests pass with the completed implementation, including all three 16dp boundaries. This is focused evidence, not a full module result. The complete UI module and scoped lint are running next.

Reports and logs remain in the ignored build folder; source inputs are SHA-256 recorded for each invocation. No overlapping Gradle invocation was started.

## Complete UI candidate: concrete findings before follow-up fixes

The `full-ui` invocation completed with 176 fresh UI tests, 5 failures, 0 errors/skips. The unchanged ordinary-phone no-scroll assertion measured the final card at y=645–720 while navigation starts at y=652; the native-inset artwork assertion measured a 131dp mascot, below its existing 160dp expectation. Do not weaken either assertion. The retained 260dp care width cap needlessly wraps the question on a 360dp display, while 16dp top/bottom page padding additionally consumes room. Candidate correction: let the care bubble share the 24dp-gutter content column and use 8dp page edge padding, keeping every inter-section gap 16dp and all typography/actions unchanged.

Three new compact-header tests also fail raw `TextLayoutResult.hasVisualOverflow` checks. Investigate actual paragraph/line ink dimensions; the existing Home no-scroll tests already distinguish Compose intrinsic-canvas rounding from lost glyphs. Reuse that physical readability assertion rather than duplicating or weakening the check, and retain full text-scale/line-end/width/height assertions plus diagnostics.

Scoped detekt found ten unique introduced source/style findings (repeated across source-set tasks): a 62-line care composable, two now-short function signatures requiring one-line formatting, and one missing blank line between multiline when branches. Fix source structure/style directly, without suppressions or baselines. Fresh source-set XML and the complete failing task output are in `full-ui/` and the corresponding module detekt report folder.

The care bubble's decorative 16dp tail is currently counted as content height followed by a separate 16dp gap. This doubles the visual panel-to-mascot spacing while other independent panels use one gap. Draw that tail within the existing reserved 16dp care-to-mascot gap; keep the panel's measured frame and all three actual section gaps consistent, with no lost tail artwork or text/action compression.

## Corrected candidate verification

- Care fills the existing column, page edges use 8dp, and the 16dp tail paints inside the reserved 16dp section gap. Strict ordinary-phone first-frame and 160dp artwork assertions are unchanged and pass. The new pixel check also proves the tail is retained below the measured panel rather than clipped or counted twice.
- Badge diagnostics establish the intrinsic-canvas mismatch: at 100%, the badge has a 60×13 integer frame, 59.625px of actual line ink and a 13px paragraph height, but its paragraph canvas is 278px wide. All characters and line ends fit. The shared physical readability helper passes at normal, enlarged and RTL text while keeping complete-glyph, line, scale and frame assertions.
- `focused-layout-2`: 26 tests across five affected UI suites, 0 failures/errors/skips.
- `full-final-candidate`: Gradle exit 0; 177 UI tests across 31 suites, 4 core UI tests across one suite, 0 failures/errors/skips. Scoped detekt, production Android assembly and explicit shared UI/core iOS Arm64 source compilation all pass. This is not iOS runtime evidence or a whole-root quality-gate pass.
- Core UI Kover XML: 142/151 covered lines (94.04%) and 25/28 covered branch outcomes (89.29%); all four executable changed source lines and their branch outcomes are covered. The feature UI's declared denominator remains native-equivalent; JVM layout tests are not substituted for that policy record.
- Production APK SHA-256: `81d6e595d545c8379d85f828cf2b1256c92a115b61a4d5b498a307aaca705012` (478,036,374 bytes). Full native instrumentation is now running on the existing isolated API35 x86_64 guest. No physical-device availability is inferred from old chat replies.

## Fresh native suite

`android-final` completed with Gradle exit 0 on `SN_Final_Verification_35_20261018`, serial `emulator-5590`, API35 x86_64, isolated ADB5041. Fresh XML reports contain 43/0/0/0 tests/failures/errors/skipped in **each** of `poc`, `production` and `uiAcceptance` (129 tests total). The recorded source digests match the verified shared implementation. This is emulator evidence only. Production installation and the 18-case visual matrix follow this successful run.

## Current visual matrix

All 18 new after PNGs (nine popular portrait compositions at 100% and 200%) were viewed immediately after capture and journaled with their exact SHA-256 and dimensions. The previous 456-file full gallery and 86-file focused gallery remain byte-identical. The new gallery contains 12 fitting and six designer-required cases, no pending/missing/warnings. Every normal-text profile fits; 200% phone cases retain readable scrolling. Measurable independent gaps are 16dp in all cases; two enlarged compact frames place the lower boundary wholly offscreen and do not claim a native measurement for it. Tablet proportions remain documented for design review, without changing the established width policy or text size. Emulator settings were restored and verified against the immutable baseline.
