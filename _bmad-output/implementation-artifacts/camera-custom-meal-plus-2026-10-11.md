# Camera review: a centered plus for a custom meal

## Request and acceptance conditions

The user requested on 2026-10-11: remove the visible “Custom meal” caption and leave only “+”. This supersedes the earlier caption-and-symbol alignment preference recorded in `camera-custom-meal-alignment-2026-10-11.md`.

- The custom meal card displays only a plus, centered horizontally and vertically.
- The plus remains visible when selected; the selected background and radio-button semantics indicate the choice.
- The custom meal action, localized accessibility description, and confirmation data remain intact.
- The card works at 375 × 812 and 320 × 568, including 1.5× text scaling.

## Verification

Before changing production code, the updated existing visual regression ran and failed because the “Custom meal” text was still present: 1 test, 1 failure, 0 errors/skipped. XML timestamp: `2026-10-11T08:35:04.811Z`; log: `/tmp/supernutrition-custom-meal-plus-red.log`. A test import compilation error was corrected before this run and is not counted as regression evidence.

The card now renders a 24sp plus within its existing centered layout. The obsolete caption/symbol helper was removed. Selection callbacks, radio-button semantics, localized accessibility description, automation ID, and confirmation label remain unchanged.

Final commands, all exit 0:

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest --continue --no-configuration-cache --console=plain
./mobile/gradlew -p mobile qualityCheckJvmAndroid :apps:androidApp:assembleProductionDebug --continue --no-configuration-cache --console=plain
./mobile/gradlew -p mobile :feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true --continue --no-configuration-cache --console=plain
graphify update .
git diff --check
/Users/anastasia/Library/Android/sdk/platform-tools/adb devices -l
```

The full UI suite passes in 51s: 50 tests, 0 failures/errors/skips. XML timestamps range from `2026-10-11T08:36:06.113Z` to `2026-10-11T08:36:40.555Z`. This includes the updated visual regression at three viewport/text-size combinations, in both selected and unselected states, and the existing custom meal text-entry/confirmation test. Log: `/tmp/supernutrition-custom-meal-plus-ui.log`.

The JVM/Android quality and APK run passes in 10m4s. Fresh shared UI Detekt reports contain zero issues: commonMain at 08:39:47.580 UTC, jvmTest at 08:42:10.884, mainAndroid at 08:44:57.823, mainJvm at 08:45:46.260, and testJvm at 08:46:17.022 on 2026-10-11. The unchanged commonTest report is up-to-date. The older `format.xml` was not regenerated and is not evidence for this check. This gate provides JVM/Android static-analysis evidence, not a native or Apple lint claim. Log: `/tmp/supernutrition-custom-meal-plus-android.log`.

Per-module XML results, all with zero failures/errors/skips:

| Module | Tests | XML timestamps (UTC) | Input status |
|---|---:|---|---|
| core/ui | 4 | 2026-10-10 08:13:17.904 | unchanged, up-to-date |
| photo-analysis-presenter | 17 | 2026-10-10 09:55:13.459–09:55:16.518 | unchanged, up-to-date |
| photo-analysis-ui | 50 | 2026-10-11 08:36:06.113–08:36:40.555 | full suite rerun |
| photo-analysis | 23 | 2026-10-11 08:37:18.018–08:37:47.655 | rerun after UI dependency change |

The changed shared UI compiles for iosArm64 in 30s with the explicit device-target flag on the Intel x86_64 macOS host. Log: `/tmp/supernutrition-custom-meal-plus-ios.log`. This is Kotlin/Native compilation, not an Xcode app build or device/simulator run. Full Apple lint has not been rerun; the earlier documented Apple findings are outside this visual change. ADB lists no connected devices, so no native camera or Android instrumentation execution is claimed.

The production APK is `mobile/apps/androidApp/build/outputs/apk/production/debug/androidApp-production-debug.apk`, 250,152,048 bytes, refreshed at `2026-10-11T08:46:59.942816Z`. SHA-256: `98a10c2af3f03d99cbbc06949f65317595466531b95eebd0627b31b13404f1f1`.

Full review preview: `mobile/feature/photo-analysis-ui/build/reports/camera-design/review-custom-meal-plus.png`. Larger-text selected preview: `mobile/feature/photo-analysis-ui/build/reports/camera-design/custom-meal-plus-selected-320x568-1.5.png`. Both were visually inspected and show only a centered plus. These are production-composable JVM/Skiko renders using the retained fixture photo, not native device screenshots.

Graphify updated the code graph successfully: 20,248 nodes, 26,897 edges, 1,937 communities. Existing empty-JSON and missing-SQL-parser warnings remain; HTML visualization is skipped above its 5,000-node limit. Log: `/tmp/supernutrition-custom-meal-plus-graphify.log`. All 17 source links in the Home/Rewards spacing spec resolve, and `git diff --check` passes.

Worktree: `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`. Branch: `codex/figma-camera-flow`, tracking `origin/main`; HEAD/base: `5ee5de077866feed70b418ee2760af24322b1d22`. The camera, Home/Rewards, and follow-up changes remain local and uncommitted: 91 dirty entries, 0 staged entries. No commit or push was requested. Full status is saved at `/tmp/supernutrition-custom-meal-plus-git-status.txt`.
