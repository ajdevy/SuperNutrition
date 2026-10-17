# Home mascot feet in front of the panel

User follow-up on 2026-10-11: the rhythm panel is above the capybara and cuts
off the paw tips; the feet should hang slightly over the panel edge.
Finding recorded before changing production code.

Acceptance: retain the current measured overlap, mascot size, card gutters,
scroll behavior, and text clearance. Paint the complete mascot above the
rhythm panel, with both opaque paw tips visible inside the panel's top edge.
The user's latest layering preference supersedes the preceding requirement
that the panel cover the mascot's lower edge. Keep the original PNG intact.

The existing 1254×1254 RGBA asset ends near source row 1164. Opaque paw probes
near source x=430 and x=830, y=1151, project slightly below the panel top at
the existing 16.2/182.8 overlap. Update the viewport regression to verify
these visible paw pixels instead of expecting panel fill to cover them;
observe the regression fail before changing draw order.

Scope: HomeScreen.kt, its existing layout visual regression, and layout
spec/evidence documents. Preserve the earlier camera, Rewards, and native
work. No commit or push is requested.

Worktree: `/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`, branch
`codex/figma-camera-flow`, base commit
`5ee5de077866feed70b418ee2760af24322b1d22`; existing local work is dirty and
uncommitted.

## Regression before the fix

The updated foreground-paw regression fails on the existing implementation:
one test, one failure, zero errors/skips; exit 1, 59s; XML timestamp
2026-10-11T07:38:35.297Z. At the left paw probe (165,365), the renderer returns
the panel's cream fill rather than the paw. The preceding run also reproduced
the clipping one pixel lower. Probe rounding was refined to remain inside
the opaque foot on the smallest 80-point mascot before the final red run.
Logs: `/tmp/supernutrition-home-feet-red.log` and
`/tmp/supernutrition-home-feet-red-final.log`.

## Implementation and shared UI verification

The mascot image now has a higher drawing order than the rhythm card. Its
original image, size, bounds, and proportional overlap are unchanged. Only
the paw tips extend over the panel; they leave the heading and metrics clear.
Regular and short-phone production renders were inspected after the change.

The full shared UI suite passes (exit 0, 2m17s): 48 tests, zero
failures/errors/skips. XML timestamps: 2026-10-11T07:41:30.204Z through
07:42:21.269Z. The updated regression verifies both visible paw tips at
375×812, 375×932, 320×480, 320×568 with font scale 1.5, and 600×800.

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest --no-configuration-cache --console=plain
```

Log: `/tmp/supernutrition-home-feet-ui-green.log`. The regular render is
`mobile/feature/photo-analysis-ui/build/reports/layout-design/home-feet-foreground.png`.
These are production-composable renders on JVM/Skiko, not device screenshots.

## Final build and quality checks

```sh
./mobile/gradlew -p mobile qualityCheckJvmAndroid :apps:androidApp:assembleProductionDebug --continue --no-configuration-cache --console=plain
./mobile/gradlew -p mobile :feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true --continue --no-configuration-cache --console=plain
graphify update .
git diff --check
/Users/REDACTED/Library/Android/sdk/platform-tools/adb devices -l
```

The JVM/Android quality gate and production APK build succeed (exit 0,
2m12s). Fresh shared UI reports contain zero issues: commonMain at
07:44:28.393 UTC, jvmTest at 07:44:35.088, mainAndroid at 07:45:01.529,
mainJvm at 07:45:27.169, and testJvm at 07:45:39.941 on 2026-10-11.
JVM host coverage remains 99.55% line / 89.25% branch across nine production
modules; nutrition remains 99.99% line / 100% branch.

Per-module results, all with zero failures/errors/skips:

| Module | Tests | XML timestamp range (UTC) | Input status |
|---|---:|---|---|
| core/ui | 4 | 2026-10-10 08:13:17.904 | unchanged, up-to-date |
| photo-analysis-presenter | 17 | 2026-10-10 09:55:13.459–09:55:16.518 | unchanged, up-to-date |
| photo-analysis-ui | 48 | 2026-10-11 07:41:30.204–07:42:21.269 | full suite rerun |
| photo-analysis | 23 | 2026-10-11 07:43:53.287–07:44:04.919 | rerun after UI dependency change |

The actual changed shared UI compiles for iosArm64 (exit 0,32s). This is
Kotlin/Native compilation, not a native app launch or device test. Full Apple
quality is not claimed; the preceding layout evidence records the existing
Apple lint/toolchain limitations.

Host checks confirm Intel x86_64 macOS 15.7.3 with Android SDK 36 and
`/Library/Developer/CommandLineTools` selected. Android CLI is installed;
the fresh `adb devices -l` result lists no connected device. No Android or
iOS device execution ran, and no system software was installed.

APK: `mobile/apps/androidApp/build/outputs/apk/production/debug/androidApp-production-debug.apk`,
250,152,173 bytes, modified 2026-10-11T07:45:48.600Z; SHA-256
`4c97c64c195e5b47f6ac70bffc9bf7bc4253d94749175611d2e99cd83ce37b0f`.
Logs: `/tmp/supernutrition-home-feet-android-verified.log` and
`/tmp/supernutrition-home-feet-ios-ui-verified.log`.

Graphify succeeds (exit 0): 20,222 nodes, 26,858 edges, 1,934 communities.
JSON and the graph report update; the existing empty-JSON/SQL-parser warnings
and HTML graph-size limit remain. Log:
`/tmp/supernutrition-home-feet-graphify.log`.

All 16 spec source links resolve, and whitespace checks pass. The change
remains local and uncommitted on `codex/figma-camera-flow`, at base
`5ee5de077866feed70b418ee2760af24322b1d22`. This follow-up modifies only Home's
image drawing order, the existing visual regression, the layout spec, and
this evidence file; earlier local camera and Rewards work is preserved.
