# Camera review: explain choosing foods

## Finding and acceptance

Recorded before editing production copy. The user pointed out that “Tap anything that looks wrong” does not explain the actual task: select foods that match the photo and deselect incorrect choices. The replacement must be short and understandable to a school-age child.

Chosen copy: **Pick the foods in your photo. Uncheck the rest.**

Given a retained-photo review, the instruction identifies which foods to select and which to uncheck. Android, iOS, and the shared default display the same English copy. On narrower screens or with larger text, wrapped lines stay centered within the screen gutters. Existing selection, custom meal, and confirmation behavior remain intact.

The previous production render (`mobile/feature/photo-analysis-ui/build/reports/camera-design/review-custom-meal-plus.png`) and all three copy sources still show the obsolete instruction before the edit. This is a copy correction; use existing UI and presenter suites plus visual inspection, without adding tests that repeat a string literal.

## Verification

Commands, all exit 0:

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-presenter:jvmTest :feature:photo-analysis-ui:jvmTest --continue --no-configuration-cache --console=plain
./mobile/gradlew -p mobile qualityCheckJvmAndroid :apps:androidApp:assembleProductionDebug --continue --no-configuration-cache --console=plain
./mobile/gradlew -p mobile :feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true --continue --no-configuration-cache --console=plain
plutil -lint mobile/apps/iosApp/Localizable.strings
graphify update .
git diff --check
```

The existing presenter and UI suites pass in 46s, with no new tests added for the string edit. Counts are read per module from `build/test-results/jvmTest/TEST-*.xml`; all have zero failures/errors/skips:

| Module | Tests | XML timestamps (UTC) | Status |
|---|---:|---|---|
| core/ui | 4 | 2026-10-10 08:13:17.904 | unchanged, up-to-date |
| photo-analysis-presenter | 17 | 2026-10-11 08:54:02.888–08:54:04.352 | full suite rerun |
| photo-analysis-ui | 50 | 2026-10-11 08:54:09.677–08:54:38.984 | full suite rerun |
| photo-analysis | 23 | 2026-10-11 08:55:33.549–08:55:42.632 | rerun by quality task after dependency change |

Test log: `/tmp/supernutrition-review-instruction-tests.log`.

The JVM/Android quality and APK run passes in 4m33s. Refreshed presenter commonMain/mainJvm and UI commonMain/mainAndroid/mainJvm Detekt reports have zero issues, dated between 08:56:10.122 and 08:59:11.917 UTC on 2026-10-11. Unchanged commonTest and jvmTest sources remain legitimately up-to-date. The older formatting report was not regenerated and is not check evidence. No lint rules or baselines were changed. This gate provides JVM/Android static-analysis evidence, not an active coverage or Apple lint claim. Log: `/tmp/supernutrition-review-instruction-android.log`.

The Android APK is `mobile/apps/androidApp/build/outputs/apk/production/debug/androidApp-production-debug.apk`, 250,152,099 bytes, refreshed at `2026-10-11T08:59:56.325221Z`. SHA-256: `6e68b53baba01d48c27a482503926f1d9561ea31ae9c8ddb0464b5562bf6905c`.

The shared presenter and UI compile for iosArm64 in 40s on the Intel x86_64 macOS host, with the explicit target flag. Log: `/tmp/supernutrition-review-instruction-ios.log`. This is Kotlin/Native compilation, not an Xcode app build or device execution. Android resource XML parses, the iOS strings file passes `plutil`, and all three copy sources match exactly. Full Apple lint and native device/camera tests were not rerun; the previously documented unavailable Xcode/device prerequisites remain.

Production-composable preview: `mobile/feature/photo-analysis-ui/build/reports/camera-design/review-instruction.png`. Larger-text preview: `mobile/feature/photo-analysis-ui/build/reports/camera-design/custom-meal-plus-selected-320x568-1.5.png`. Both were visually inspected: the instruction fits one centered line at 375 × 812 and two centered lines at 320 × 568 with 1.5× text size. These are JVM/Skiko renders using a fixture photo, not native screenshots.

Graphify updates successfully: 20,252 nodes, 26,901 edges, 1,936 communities. Existing empty-JSON/missing-SQL-parser warnings remain; the graph exceeds the HTML visualization limit. Log: `/tmp/supernutrition-review-instruction-graphify.log`. All 17 source links in the Home/Rewards spec resolve. The obsolete instruction is absent from authored mobile source/resources, and `git diff --check` passes.

Worktree: `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`. Branch: `codex/figma-camera-flow`, tracking `origin/main`. HEAD/base: `5ee5de077866feed70b418ee2760af24322b1d22`. All camera, Home/Rewards, and follow-up changes remain local and uncommitted: 92 dirty entries, 0 staged entries. No commit or push was requested. Full status: `/tmp/supernutrition-review-instruction-git-status.txt`.
