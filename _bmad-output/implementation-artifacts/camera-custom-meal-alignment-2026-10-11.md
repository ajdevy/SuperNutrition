# Camera review custom meal label and symbol

The later request to display only a centered plus supersedes the visual caption/symbol arrangement below. See [camera-custom-meal-plus-2026-10-11.md](camera-custom-meal-plus-2026-10-11.md) for the current implementation and checks.

User follow-up on 2026-10-11: the Custom meal label is off center relative
to the other buttons, and the plus should sit below it. Finding recorded
before changing production code.

The existing custom tile stacks its plus/check above the label. Centering
the stack moves the label below the middle of the tile, unlike the ordinary
dish choices. The original review reference also puts the plus above; the
user's latest preference supersedes that part of the reference.

Acceptance: center Custom meal horizontally and vertically in its tile,
aligned with neighboring dish titles. Place the plus below the label, with
clear separation. When selected, keep the existing check symbol in that
same lower position. Let the whole choice row grow naturally for wrapping
and larger fonts, with no clipped or overlapping content. Preserve labels,
selection behavior, confirmation requirements, callbacks, automation IDs,
colors, retained photo, and the preceding Home/Rewards corrections.

Scope: the shared review choice layout, a focused visual regression, and
camera verification notes. Observe the regression fail before changing the
layout, then run the full shared UI suite, JVM/Android quality and APK checks,
and changed shared UI iosArm64 compilation. Inspect the rendered review.

Worktree: `REDACTED_WORKTREE/a704/SuperNutrition`, branch
`codex/figma-camera-flow`, HEAD/base
`5ee5de077866feed70b418ee2760af24322b1d22`. Existing camera and layout work is
local and uncommitted. No commit, push, or remote changes are requested.

## Regression before the fix

The focused visual regression fails on the existing layout: one test, one
failure, zero errors/skips; exit 1, 17s; XML timestamp
2026-10-11T08:15:09.119Z. The custom label is below the tile's center and
therefore below the ordinary dish titles. Log:
`/tmp/supernutrition-custom-meal-red.log`.

The regression checks label and symbol geometry in unselected and selected
states at 375×812 and 320×568, including font scale 1.5. It requires the
label's center to match its tile and the neighboring title, with the plus or
check below the label and separated from both the text and tile bottom.

## Implementation and shared UI verification

The custom tile now places its plus/check below the label and reserves an
equal amount of space above. The label stays on the same center line as
the other dish titles. The symbol slot scales with text size, and the choice
row's existing intrinsic height lets every tile grow together. The 16sp
symbol has room within its 20sp slot, with a 2dp gap to the label.
The standard dish labels and selection behavior are preserved.

The final full shared UI suite passes after component extraction (exit 0,
1m6s): 50 tests, zero failures/errors/skips. XML timestamps:
2026-10-11T08:25:52.712Z through 08:26:28.194Z. The new regression checks centered titles, symbol placement,
and clear separation in both states at normal and larger text sizes.
Existing camera, review, Home, Rewards, and navigation tests also pass.

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest --tests '*PhotoAnalysisCameraVisualTest.custom meal*' --no-configuration-cache --console=plain
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest --no-configuration-cache --console=plain
```

Log: `/tmp/supernutrition-custom-meal-ui-final.log`.
Complete review preview: `mobile/feature/photo-analysis-ui/build/reports/camera-design/review-custom-meal-centered.png`.
Regular preview: `mobile/feature/photo-analysis-ui/build/reports/camera-design/custom-meal-unselected-375x812-1.0.png`.
Larger-text selected preview: `mobile/feature/photo-analysis-ui/build/reports/camera-design/custom-meal-selected-320x568-1.5.png`.
Both were visually inspected. These are JVM/Skiko production-composable
renders, not native device screenshots. The retained fixture photo and food
artwork are unchanged; the reference screen remains a comparison fixture.

## Quality finding and correction

The first JVM/Android quality run exits 1 after 4m36s: the new helper puts
PhotoAnalysisMealReview.kt at 12 functions, above the existing file limit of
11. CommonMain, Android, and JVM Detekt each report this same finding. The
APK task completes, but that run is not a green quality result. Log:
`/tmp/supernutrition-custom-meal-android-verified.log`.

Recorded before fixing: move the custom label/symbol layout into its own
module-local component file, keeping the review's orchestration separate.
Do not relax the rule or suppress the finding. Rerun the full UI suite and
the full relevant quality/package task after the move.

The helper is now `CustomMealChoiceLabel.kt`, an internal component using
the existing review text style. The final full UI rerun above verifies the
same geometry and interactions after the extraction.

## Final build and quality verification

```sh
./mobile/gradlew -p mobile qualityCheckJvmAndroid :apps:androidApp:assembleProductionDebug --continue --no-configuration-cache --console=plain
./mobile/gradlew -p mobile :feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true --continue --no-configuration-cache --console=plain
graphify update .
git diff --check
~/Library/Android/sdk/platform-tools/adb devices -l
```

The final JVM/Android quality and APK run succeeds (exit 0, 1m30s).
Shared UI Detekt reports contain zero issues. The refreshed commonMain report
is dated 08:26:55.763 UTC, mainAndroid 08:27:35.893, mainJvm 08:27:19.184,
and testJvm 08:27:45.061 on 2026-10-11. The jvmTest source report is dated
08:20:01.028 and legitimately up-to-date: its source did not change during
component extraction. No rules or baselines were weakened.

Per-module XML results, all with zero failures/errors/skips:

| Module | Tests | XML timestamp range (UTC) | Input status |
|---|---:|---|---|
| core/ui | 4 | 2026-10-10 08:13:17.904 | unchanged, up-to-date |
| photo-analysis-presenter | 17 | 2026-10-10 09:55:13.459–09:55:16.518 | unchanged, up-to-date |
| photo-analysis-ui | 50 | 2026-10-11 08:25:52.712–08:26:28.194 | final full suite rerun |
| photo-analysis | 23 | 2026-10-11 08:26:53.157–08:27:06.166 | rerun after UI dependency change |

JVM host coverage reports 99.55% line / 89.25% branch across nine production
modules; nutrition reports 99.99% line / 100% branch. This is JVM host
coverage, not a full native coverage claim.

The changed shared UI compiles for iosArm64 (exit 0, 35s) on the Intel
x86_64 macOS 15.7.3 host. This is actual Kotlin/Native compilation, not an
iOS app launch. Android SDK 36 is configured, and the fresh ADB inventory
lists no connected devices. Xcode selection remains
`/Library/Developer/CommandLineTools`. No device execution or full Apple
quality is claimed; preceding camera/layout evidence records the existing
native tooling and Apple lint limitations. No system software was installed.

APK: `mobile/apps/androidApp/build/outputs/apk/production/debug/androidApp-production-debug.apk`,
250,154,763 bytes, modified 2026-10-11T08:28:01.013Z; SHA-256
`51c8851dc343ed2ab0826df45d2f9143012aa109593c1085a327a6cd634a8cf5`.
Logs: `/tmp/supernutrition-custom-meal-android-final.log` and
`/tmp/supernutrition-custom-meal-ios-ui-final.log`.

The final graph update succeeds (exit 0): 20,245 nodes, 26,895 edges,
1,947 communities. JSON and the graph report update. The existing 111
empty-source/2 missing SQL-parser warnings and HTML graph-size limit remain.
Log: `/tmp/supernutrition-custom-meal-graphify-final.log`.

Whitespace checks pass and the preview is saved. Final worktree state:
local and uncommitted on `codex/figma-camera-flow`, HEAD/base
`5ee5de077866feed70b418ee2760af24322b1d22`. This follow-up changes the shared
review choice layout, its new internal helper and visual regression, and
camera evidence notes. Existing camera flow, Home, and Rewards work remains
intact.
