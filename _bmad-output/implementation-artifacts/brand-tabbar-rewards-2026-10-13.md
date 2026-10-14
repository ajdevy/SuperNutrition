# Brand accent and floating navigation — 2026-10-13

Implemented the three requested Compose UI corrections in an isolated worktree, then completed coverage and physical verification under the user's 2026-10-14 follow-up. Current evidence is below; the later sections retain the initial verification history.

## Completed follow-up — 2026-10-14

Current worktree: `/Users/anastasia/.codex/worktrees/brand-tabs-rewards/SuperNutrition`, branch `codex/brand-tabs-rewards`, HEAD/base `5ee5de077866feed70b418ee2760af24322b1d22`. No new commit was created. The staged index retains the 558-file imported parent baseline; the correction and test delta are unstaged (new files use intent-to-add). Do not blanket-commit that index. A fresh SHA-256 comparison confirms all 211 imported parent UI module files remain unchanged.

- Full UI suite: **117 tests, 0 failures, 0 errors, 0 skipped**, 23 XML suites. Execution timestamps: `2026-10-14T06:29:50.841Z` through `2026-10-14T06:31:03.855Z`.
- Kover: **3,422 / 3,438 lines = 99.5346%; 768 / 903 branches = 85.0498%**. This closes the earlier module branch gap. All **58 added executable correction lines** and their branch outcomes are covered. No exclusions, suppressions, baselines or coverage policy changes were introduced.
- Current full module Detekt reports: **zero findings**. Formatting used the explicit `qualityFormat` entrypoint scoped to the six new test files; read-only check tasks retained their complete source scope.
- Production debug app and instrumentation APKs built successfully. The app certificate SHA-256 is `d3d9dcfb73aa1f7dfc8e96f25646f3678dcaf712bb96a9e727833a8d6184f0ec`, matching the installed signature. App APK SHA-256: `075255c17520283a7e5a5e20a2ea872397e099d3ee723d6c5339b14ef673244c`. Updating the 464,951,979-byte APK over Wi-Fi took 509.33 seconds and preserved app data.
- Physical device: **HONOR DNY_NX9**, Android API **36**, ABI **arm64-v8a**, 1264×2736 px, density 560, font scale 1.0. Serial: `adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp`.
- Focused native execution: **2 existing launch/navigation tests + 3 new visual tests = 5 passed**. The visual suite ran for 25.816 seconds and compared label/icon pixels, injected an actual touchscreen press/release, checked unchanged press pixels, sampled Rewards paper beside the pill, and scrolled the final reward above the camera. Home/sprout, Rewards and final-card PNGs were visually inspected.

Host commands (using the build environment below; UI test runs use the runtime init script):

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest --rerun :feature:photo-analysis-ui:koverXmlReportJvm :feature:photo-analysis-ui:detekt :apps:androidApp:assembleProductionDebugAndroidTest --continue --no-build-cache --no-configuration-cache --console=plain --no-watch-fs -Pkotlin.compiler.execution.strategy=in-process --max-workers=2 --init-script /private/tmp/supernutrition-brand-tabs-runtime.gradle
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest --rerun :feature:photo-analysis-ui:koverXmlReportJvm :feature:photo-analysis-ui:detekt :apps:androidApp:assembleProductionDebug :apps:androidApp:assembleProductionDebugAndroidTest --continue --no-build-cache --no-configuration-cache --console=plain --no-watch-fs -Pkotlin.compiler.execution.strategy=in-process --max-workers=2 --init-script /private/tmp/supernutrition-brand-tabs-runtime.gradle
./mobile/gradlew -p mobile :apps:androidApp:assembleProductionDebugAndroidTest --no-configuration-cache --console=plain --no-watch-fs -Pkotlin.compiler.execution.strategy=in-process --max-workers=2
```

Exact final logs are ignored `mobile/build/brand-tabs-verified.log` (successful full UI/lint/coverage and initial native APK build) and `brand-tabs-native-build.log` (successful instrumentation rebuild after strengthening the press assertion). The earlier combined build stopped at a missing test import/lint findings while its app packaging succeeded; its stale coverage was never counted as fresh. All relevant tasks were subsequently rerun successfully.

Device commands, using the selected serial with `adb -s`:

```sh
adb install --no-streaming -r mobile/apps/androidApp/build/outputs/apk/production/debug/androidApp-production-debug.apk
adb install --no-streaming -r -t mobile/apps/androidApp/build/outputs/apk/androidTest/production/debug/androidApp-production-debug-androidTest.apk
adb shell am instrument -w -r -e class 'com.supernutrition.android.PhotoAnalysisComposeSemanticsInstrumentationTest#initialLaunchOpensHomeSurface,com.supernutrition.android.PhotoAnalysisComposeSemanticsInstrumentationTest#firstTabOpensHomeAfterRewardsSelection' app.supernutrition.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e class com.supernutrition.android.BrandNavigationVisualInstrumentationTest app.supernutrition.test/androidx.test.runner.AndroidJUnitRunner
```

Device records and six native images are under `mobile/apps/androidApp/build/reports/brand-tabs-device-2026-10-14/`; the screenshots come from `device-visual/`. Each native screenshot checks the owning package before and after capture. An interrupted earlier CLI attempt was discarded; its foreign-app screenshot is absent from these deliverables. The initial four regressions/RTL failure remain preserved as failed-before evidence.

This is full UI module host evidence and focused physical **productionDebug** evidence. It is not the complete `connectedAndroidTest` suite across all flavors, Apple device execution, or a claim that every product quality/merge gate ran. Independent review agents remain prohibited in this side conversation. `graphify update .` completed structurally: 20,592 nodes and 26,120 edges; SQL dependency and zero-node JSON warnings remain documented, with no dependency installation or semantic-document extraction.

## Initial correction history — 2026-10-13

## Result

- Home uses a dotless first i and the existing sprout in its measured dot position. Placement follows physical glyph coordinates, so narrow/wrapped text, larger fonts and an RTL host retain the same attachment. Accessible text remains “Super Nutrition”, and the second i keeps its ordinary dot.
- Selecting a Home/Rewards tab changes icon tint only. Labels retain their existing dark color; holding a tab no longer paints its rectangular touch area. The original 72×58dp touch targets, tab role/selected state and callbacks remain.
- Rewards extends behind the floating shaped navigation pill, with its paper covering system inset regions. The final scroll padding is the shared 83dp navigation canvas plus 16dp, so the complete final reward can clear navigation. Home retains its original reserved navigation arrangement; standalone/native-reserved Rewards receives only its original 16dp padding.

## Worktree and scope

- Parent source: `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`, branch `codex/figma-camera-flow`.
- Isolated worktree: `/private/tmp/supernutrition-brand-tabs-rewards`, branch `codex/brand-tabs-rewards`.
- HEAD/base: `5ee5de077866feed70b418ee2760af24322b1d22`; no new commits or remote operations.
- The desktop created `/Users/anastasia/.codex/worktrees/brand-tabs-rewards/SuperNutrition` but could not attach it to this side conversation (`thread not found`). Its shared Git metadata was read-only. The implemented checkout is therefore a real worktree of a separate local clone under `/private/tmp`, with its own writable metadata and build caches.
- The index contains an exact import of 558 existing parent WIP files. This task's corrections remain unstaged; `git diff` shows only the correction delta. Do not blanket-commit the index. The immutable import is `/private/tmp/supernutrition-brand-tabs-baseline`; its SHA-256 manifest is ignored `mobile/build/side-conversation-baseline.json`.
- A final hash comparison found all 210 imported parent UI source/resource files unchanged. The correction patch targets that current parent working copy, including its existing untracked Home helpers; it is not a patch against bare main.

## Verification

Host: macOS Intel (`x86_64`), Eclipse Adoptium JDK 21.0.7, Gradle 9.5.0, Kotlin 2.4.10. No physical phone, simulator, install or permission operation was performed in this side task.

Use these local build environment variables (they do not change the parent configuration):

```sh
GRADLE_USER_HOME=/private/tmp/supernutrition-brand-tabs-gradle
JAVA_HOME=/private/tmp/supernutrition-brand-tabs-gradle/jdks/eclipse_adoptium-21-x86_64-os_x.2/jdk-21.0.7+6/Contents/Home
ANDROID_USER_HOME=/private/tmp/supernutrition-brand-tabs-android
KONAN_DATA_DIR=/private/tmp/supernutrition-brand-tabs-konan
```

The ignored runtime init script `/private/tmp/supernutrition-brand-tabs-runtime.gradle` sets only `skiko.data.path` to a local writable cache and `java.awt.headless=true` for JVM test tasks. Every evidence command below used `--no-configuration-cache --console=plain --no-watch-fs -Pkotlin.compiler.execution.strategy=in-process --max-workers=2 --init-script /private/tmp/supernutrition-brand-tabs-runtime.gradle` and ran from the isolated worktree.

1. `./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest --tests '*BrandNavigationVisualTest*'` — the original four tests ran and failed for the precise defects before production fixes: dotted i, 121 changed label pixels, 4,176 tinted touch-area pixels, and Rewards ending exactly 83px above the inset viewport. Original XML and PNGs are in `mobile/feature/photo-analysis-ui/build/reports/brand-navigation-before`.
2. The added RTL regression failed before the absolute-coordinate fix and passed after it. A live font-change/header-refresh check then exposed an out-of-image pixel probe in the test helper; the helper now bounds pixel iteration to the rendered image while retaining the actual accent/alignment assertions. No production change was required for that test-helper repair.
3. `./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest :feature:photo-analysis-ui:compileAndroidMain :feature:photo-analysis-ui:compileKotlinIosArm64 :feature:photo-analysis-ui:detekt :feature:photo-analysis-ui:koverXmlReportJvm -Psupernutrition.enableIosDeviceTarget=true --continue` — passed after the final production correction. Actual UI source was compiled for Android and iOS arm64 on Intel; this is shared-module compilation, not a packaged iOS app/device result.
4. After the final test-only change: `./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest :feature:photo-analysis-ui:koverXmlReportJvm :feature:photo-analysis-ui:detektJvmTestSourceSet :feature:photo-analysis-ui:detektTestJvm --continue` — passed; fresh XML: **89 tests, 0 failures, 0 errors, 0 skipped**, across 17 classes. The six new regressions cover the three corrections, press behavior, RTL and live font/header refresh. XML timestamps range from `2026-10-13T11:05:57.033Z` to `2026-10-13T11:06:51.830Z`.
5. Module lint reports `commonMainSourceSet.xml`, `commonTestSourceSet.xml`, `jvmTestSourceSet.xml`, `mainAndroid.xml`, `mainJvm.xml` and `testJvm.xml` contain zero findings. Unchanged common-source reports remain valid after the test-only repair; JVM test reports were refreshed by the last command.
6. Rendered production composables were inspected for normal, narrow, wide and enlarged-text layouts, RTL, live font changes, tab selection and final-reward clearance. PNGs are under `mobile/feature/photo-analysis-ui/build/reports/brand-navigation`.
7. `git diff --check` — passed. `graphify update .` updates only the isolated graph.

## Coverage and quality limits

Fresh Kover XML reports 97.53% module line coverage and 59.91% module branch coverage. The new `HomeBrand.kt` is covered at 100% of instructions, lines and all six branches; the other added executable lines/branch outcomes in the correction delta are also covered. The **whole UI module branch counter remains below the 85% repository target**. No exclusions, suppressions, baselines or policy changes were added. The full `qualityCheckJvmAndroid`/`qualityCheckApple` product gates and native device evidence are not claimed green; these corrections are implementation-ready for review, with the wider UI branch-coverage gate still outstanding.

Independent review agents are forbidden in this side conversation, so the review was inline, not an independent subagent review. Graphify's structural update reports 253 JSON sources producing no nodes, two SQL sources lacking `tree_sitter_sql`, and skips HTML for the graph above 5,000 nodes; no semantic document/image extraction or dependency installation was performed.

## Review order

1. `PhotoAnalysisMainLayout.kt` — Rewards overlay, host insets and scroll inset ownership.
2. `RewardsScreen.kt` — final-card clearance using that shared inset.
3. `HomeBrand.kt` / `HomeHeader.kt` — physical glyph anchoring and accessible name.
4. `PhotoAnalysisMainBottomBar.kt` — fixed label color and transparent press surface.
5. `BrandNavigationVisualTest.kt` — reproduced failures, selection/press pixels, viewport reachability, font updates and RTL.

The exported `corrections.patch` contains only this task's delta, leaving imported parent work out. Its applicability is checked with read-only `git apply --check` against the parent working copy.

## Worktree handoff — 2026-10-14

After write access became available, the checked correction was copied into the existing, clean worktree of the original repository at `/Users/anastasia/.codex/worktrees/brand-tabs-rewards/SuperNutrition`. Its branch is now `codex/brand-tabs-rewards`, based on the same `5ee5de077866feed70b418ee2760af24322b1d22`. All nine correction files were byte-identical to the previously verified temporary checkout before this documentation update. The copied parent baseline remains staged separately from the unstaged corrections. The parent's 210 UI source/resource files still match the original snapshot. No parent code, install, device state, commit or remote operation changed.

The 2026-10-13 test evidence above was executed in the temporary checkout, not in the destination. A provenance-marked copy is retained in ignored `mobile/build/brand-navigation-proof-from-temporary-worktree`. The working copy under the regular repository is now the handoff destination; the temporary copy is retained as verification history.

### Destination verification — 2026-10-14

A first destination invocation restored the JVM result from cache and was not called new test execution. The follow-up command `:feature:photo-analysis-ui:jvmTest --rerun --no-build-cache` explicitly executed the full UI suite again in the regular repository worktree. Fresh XML shows **89 tests, 0 failures, 0 errors, 0 skipped** in 17 classes, timestamps `2026-10-14T04:33:38.338Z` through `2026-10-14T04:34:42.657Z`. New renders under the destination's `build/reports/brand-navigation` were inspected for the wordmark and Rewards overlay. The destination `koverXmlReportJvm` was regenerated; `graphify update .` completed there as well. `git diff --check` and read-only patch applicability against the parent pass. Full product quality and physical-device acceptance remain unclaimed, for the limits recorded above.
