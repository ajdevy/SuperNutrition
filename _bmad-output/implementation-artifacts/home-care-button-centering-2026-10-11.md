# Home care button icon and label centering

User follow-up on 2026-10-11: “Add a meal” and “View goals” have too much
empty space on their right. Finding recorded before changing production code.

The two buttons already have equal outer widths. Their internal labels use
the remaining width with `weight(1f)` and start alignment, so their icon and
visible text sit left of the button center.

Acceptance: center the icon and label together within each button, keep the
existing 4dp gap, and center wrapped label lines at larger text sizes. Both
labels must remain fully readable. Preserve equal button widths, callbacks,
automation identifiers, illustrations, colors, responsive layout, and the
capybara's foreground feet.

Scope: HomeCarePrompt.kt, a focused shared UI layout regression, and layout
spec/evidence. Observe the new regression fail before changing production
layout, then run the full shared UI suite, JVM/Android quality and package
checks, and the changed shared UI's iosArm64 compilation.

Worktree: `REDACTED_WORKTREE/a704/SuperNutrition`, branch
`codex/figma-camera-flow`, HEAD/base
`5ee5de077866feed70b418ee2760af24322b1d22`. Existing camera and layout work
is local and uncommitted. No commit or push is requested.

## Regression before the fix

The new focused layout regression fails on the existing implementation:
one test, one failure, zero errors/skips; exit 1, 16s. XML timestamp:
2026-10-11T07:55:15.262Z. “Add a meal” has visible group bounds x=76.0–154.06
inside button bounds x=70.0–184.0: its content center is almost 12 pixels
left of the button center, leaving about 6 pixels on the left and 30 on the
right. Log: `/tmp/supernutrition-care-buttons-red.log`.

The regression checks each icon/label group against its button center and
checks each rendered text line against the label's center. It exercises
375×812 at regular text size and 320×568 at font scale 1.5, where “Add a meal”
wraps. It uses production composables and text-layout geometry on JVM/Skiko.

The initial overflow check was refined after a diagnostic run showed a
paragraph measurement constraint of 78px with a valid, naturally measured
55px text box. Width checks now use actual line widths rather than the
paragraph constraint; centering uses button/group bounds and paragraph line
alignment. The final regression was rechecked against the original layout:
one test, one failure, zero errors/skips; exit 1, 14s; XML timestamp
2026-10-11T08:02:07.745Z. It reports that “Add a meal” reserves empty space
beside its text. Log: `/tmp/supernutrition-care-buttons-red-final.log`.

## Implementation and shared UI verification

The chip's row centers its icon and naturally measured label as one group.
The label no longer fills unused width; wrapped lines use center alignment.
Both outer buttons retain equal widths, their original assets, and callbacks.
Regular and larger-text production renders were inspected, including the
previously corrected foreground paw tips.

The full shared UI suite succeeds (exit 0, 37s): 49 tests, zero
failures/errors/skips. XML timestamps: 2026-10-11T08:03:00.903Z through
08:03:29.536Z. The new regression checks both labels at normal size and
font scale 1.5, including a wrapped “Add a meal” label. Existing tests cover
camera/review behavior, responsive Home/Rewards layouts, navigation, scrolling,
and the capybara's foreground feet.

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest --no-configuration-cache --console=plain
```

Log: `/tmp/supernutrition-care-buttons-ui-verified.log`.
Preview: `mobile/feature/photo-analysis-ui/build/reports/layout-design/home-care-buttons-centered.png`.
These are JVM/Skiko production-composable renders, not device screenshots.

## Final build and quality checks

```sh
./mobile/gradlew -p mobile qualityCheckJvmAndroid :apps:androidApp:assembleProductionDebug --continue --no-configuration-cache --console=plain
./mobile/gradlew -p mobile :feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true --continue --no-configuration-cache --console=plain
graphify update .
git diff --check
~/Library/Android/sdk/platform-tools/adb devices -l
```

The JVM/Android quality gate and production APK build succeed (exit 0,
3m6s). Fresh shared UI Detekt reports contain zero issues: commonMain at
08:05:05.773 UTC, jvmTest at 08:05:11.918, mainAndroid at 08:05:39.455,
mainJvm at 08:06:19.477, and testJvm at 08:06:50.804 on 2026-10-11.
JVM host coverage reports 99.55% line / 89.25% branch across nine production
modules; nutrition reports 99.99% line / 100% branch. This is JVM host
coverage, not a complete native coverage claim.

Per-module XML results, all with zero failures/errors/skips:

| Module | Tests | XML timestamp range (UTC) | Input status |
|---|---:|---|---|
| core/ui | 4 | 2026-10-10 08:13:17.904 | unchanged, up-to-date |
| photo-analysis-presenter | 17 | 2026-10-10 09:55:13.459–09:55:16.518 | unchanged, up-to-date |
| photo-analysis-ui | 49 | 2026-10-11 08:03:00.903–08:03:29.536 | full suite rerun |
| photo-analysis | 23 | 2026-10-11 08:04:16.463–08:04:37.161 | rerun after UI dependency change |

The changed shared UI compiles for iosArm64 (exit 0, 26s). This is actual
Kotlin/Native compilation on the Intel x86_64 macOS 15.7.3 host, not a native
app launch or device test. Android SDK 36 is configured; the fresh ADB
inventory lists no connected devices. Xcode selection remains
`/Library/Developer/CommandLineTools`. Device execution and full Apple quality
are not claimed; preceding layout evidence records the existing Apple
lint/toolchain limitations. No system software was installed.

APK: `mobile/apps/androidApp/build/outputs/apk/production/debug/androidApp-production-debug.apk`,
250,152,121 bytes, modified 2026-10-11T08:07:03.936Z; SHA-256
`1e3e8c1dcdc9397b1e7e4fed05e2a58b855527445a75cf650f127edca0e3e079`.
Logs: `/tmp/supernutrition-care-buttons-android-verified.log` and
`/tmp/supernutrition-care-buttons-ios-ui-verified.log`.

Graphify succeeds (exit 0): 20,233 nodes, 26,875 edges, 1,946 communities.
JSON and the graph report update. The existing 111 empty-source/2 SQL-parser
warnings and HTML graph-size limit remain. Log:
`/tmp/supernutrition-care-buttons-graphify.log`.

All 17 spec source links resolve, and whitespace checks pass. Final worktree
state: local and uncommitted on `codex/figma-camera-flow`, HEAD/base
`5ee5de077866feed70b418ee2760af24322b1d22`. This follow-up changes only the care
button content alignment, its focused regression, the layout spec, and this
evidence file. Existing camera, Rewards, and mascot work is preserved.
