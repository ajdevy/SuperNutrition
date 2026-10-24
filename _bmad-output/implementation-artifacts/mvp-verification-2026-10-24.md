# MVP implementation verification — 2026-10-24

Status:local implementation and available verification complete. Release/owner
dependencies remain below;no physical-device or iOS-execution claim.

## Source and environment

- Worktree:`/Users/anastasia/.codex/worktrees/f2ff/SuperNutrition`.
- Branch:`codex/mvp-completion`; baseline:`0f1fbb4e599e0a71accaef5c074466436b9e8e0b`.
- Reference:Figma Screens MVP604:293; individual contexts are in the gap audit.
- Android:`emulator-5554`,Android15/API35,x86_64,720×1560 at320dpi (360×780dp).
  One ADB endpoint; font scale restored to1.0. No physical Android device.
- Host:Intel x86_64,macOS15.8.1; Xcode26.6/build17F113 selected per command using
  DEVELOPER_DIR. Global xcode-select remains unchanged. devicectl reports no devices.

## Implemented local journeys

- Explicit reviewed-photo confirmation saves one identity-only record; retry is
  idempotent. Failed saves retain choices/note and show accessible feedback.
- Camera pencil opens Add by text with name,optional positive portion and explicit
  ingredients. Draft remains in session memory across background/analysis changes.
- Profile and history use the same observable diary. Details support serving/per100g
  sections,ingredients,editing notes/name/portion/ingredients and confirmed deletion.
  Search survives details→Back. Missing nutrition remains visibly unknown.
- All seven reward cards open designed details. Recording dates count toward a
  consecutive streak with yesterday grace; the detail uses the same domain helper.
  Authored names do not manufacture nutrient/category evidence. Corrections and
  deletion hide superseded facts consistently without mutating the raw ledger.
- Home shows actual date/count with unavailable nutrient progress. iOS arm64 uses
  the same designed Home/Rewards/Profile/diary; Intel has an explicit renderer notice.

## Final checks

`full-gates-v4.log`:both quality entrypoints invoked with --continue and strict
verification,plus both Android APK/test variants,arm64 framework link and explicitly
connected iosTest compilation. Exit1 in4m52s;596tasks (44executed,552up-to-date).
The ONLY failed task is verifyMobilePolicy:expired bootstrap-android-host evidence.
All executed source checks,test tasks,APK builds and native compiles/link succeeded.
Unchanged inputs legitimately reused their prior passing outputs.

| Affected JVM module | Tests / suites | Failures / errors / skipped |
| --- | --- | --- |
| domain:nutrition |280 /35|0 /0 /0|
| feature:photo-analysis-presenter |41 /7|0 /0 /0|
| feature:photo-analysis |66 /10|0 /0 /0|
| feature:photo-analysis-ui |266 /60|0 /0 /0|

Counts come from module XML under build/test-results/jvmTest,not BUILD SUCCESSFUL.
Presenter/domain/UI inputs unchanged by the last test-format/iOS-only cleanup;
the edited feature suite reran. Fresh V3 UI output is266 tests with no failures.

Kover XML observations:domain478/478 branches and12043/12044 lines. The new
RecordingStreakDays source has7/7 lines and12/12 branches; new presenter sources
have no uncovered lines/branches. The one missed domain line remains the verified
CatalogServingDefaultSource fun-interface attribution artifact. These counters
meet the domain99.99%line/100%branch minima. However,QualityPlugin makes
qualityCoverageJvm depend on verifyMobilePolicy,so the aggregate/strict verifier
task DOES NOT EXECUTE while that policy fails. Neither full quality gate nor
aggregate coverage is called green. No skip,baseline,evidence renewal or bypass.

Detekt's typed checks retain the known3-per-pair KMP expect/actual compiler-analysis
warnings (feature3/UI6). Focused debug checks confirm currentMealRecordContext,
currentHistoryReferenceDay and automationRoot source-set flattening; actual target
compilers succeed. [Upstream issue9602](https://github.com/detekt/detekt/issues/9602).
No claim of complete type-resolution fidelity and no warning suppression.

- Final Xcode builds:production target and acceptance/XCTest target both exit0,
  BUILD SUCCEEDED. Actual Swift,new BootstrapLaunchTests and bundled resources
  compiled/linked with arm64 iphoneos and CODE_SIGNING_ALLOWED=NO. This is build
  evidence only;no device/simulator tests executed.
Final clean connectedAndroidTest plus both quality entrypoints completed in
10m54s (`connected-clean-final.log`),588tasks (45executed,543up-to-date).
Exit1 ONLY for expired bootstrap-android-host policy. All three connected variant
tasks completed successfully. Fresh XML mtimes are after the run start
2026-10-24T02:26:44Z;counts are read directly from those reports:

| Android variant | Tests | Failures / errors / skipped | Time |
| --- | --- | --- | --- |
| pocDebug |51|0 /0 /0|170.636s|
| productionDebug |51|0 /0 /0|156.684s|
| uiAcceptanceDebug |51|0 /0 /0|156.333s|

Reports:`mobile/apps/androidApp/build/outputs/androidTest-results/connected/debug/flavors/`.
No class/task filter or assertion retry was used in this final run.
- Arm64 sharedKit framework link and iosTest compile:passed in V4. Compilation is
  not iOS execution. The generic scheme invocation was blocked before building by
  an unavailable iOS26.5 platform component; direct -target/-sdk iphoneos works.

## Regression proof

Detailed findings precede fixes in mvp-review-findings-2026-10-24.md.
- First review run:UI8/8 intended assertion failures;feature3 new supersession
  failures among6 tests;domain helper structural RED for missing API.
- Domain green:280 tests;new helper fully covered in XML.
- Additional photo-save tests first hit an invalid scroll-to-fixed-footer harness;
  those are not product RED. Corrected the harness,then both failed specifically
  for missing meal_review_save_error. Final UI266/0 verifies feedback,preserved
  choices/note,retry and all other regressions.
- Feature green66 includes correction/deletion success,failure,cancellation,stale
  IDs,unrelated facts and observer ordering. Raw grounded ledger remains unchanged.
- Initial packaged journey failed because modal controls exported no native IDs.
  Added per-window automation roots;direct production runner passed1 test in50.689s
  on V2. Full suites then exposed a hardcoded Kotlin-constructor arity in the
  permission fixture. Marker/default-mask construction preserves its four explicit
  dependencies;all permission assertions remain intact.
- An interrupted native RED left a device-side runner active after host shutdown;
  logcat proves two TestRunners overlapped. Subsequent full rerun recorded those
  foreground failures honestly. Final clean run verified no active instrumentation
  before starting and completed without interruption.
- The new journey also clicked Delete before scroll motion settled. Read-only
  diagnostics proved no dialog existed;an explicitly failing diagnostic probe
  confirmed a stationary click opens it. Temporary probe removed. Driver now uses
  slower swipes and stable fully visible targets,then exactly one click. Original
  confirmation/deletion assertions retained;focused green1 test in36.531s and
  final complete three-variant suites pass.
- Two fresh independent reviewers inspected the full tracked+untracked diff.
  R1–R8 patched;R9 grounded-only correction flow deferred explicitly (pre-existing,
  outside SavedMeal correction and dependent on approved G-2 grounding contracts).

## Installed APK and manual checks

APK:`mobile/apps/androidApp/build/outputs/apk/production/debug/androidApp-production-debug.apk`.
SHA256:`d5b3705e072559421656eebebdd6c0e3d2faf6a9094403901b67ec95f67f38ce`.
Android CLI install/activation exit0. A fresh post-install pm path/sha256sum matched
this exact device base.apk. The later lint cleanup does not change Android code;
the APK hash remains identical. An earlier query during installation returned the
old package and is excluded from final evidence.

On this APK,real keyboard entry saved Apple oatmeal/250g/Oats,apple,milk. Home and
Profile count1,history name/ingredients/local time agree. Search oat→details→Back
retained oat. Added note Less sugar,please,saved and reopened editor:note,portion
and ingredients agree. Rhythm detail opened with1/7 and Apple oatmeal datedToday;
nutrient cards remained zero. Home capybara rests on the scene's floor.

On V2,the same text form also passed at200% font with IME visible:scrolled to Add
meal above the keyboard and saved a second record; font restored to1.0. A real
imported exploratory chicken-salad fixture eventually reached photo review,then
explicit Custom meal confirmation saved a third record and reopening capture
started fresh. It took minutes and proposed incorrect labels;they were NOT silently
accepted. This is routing/save evidence,NOT model accuracy,G-3 calibration or
physical-performance evidence. The owned gallery copy/row1000000021 was removed;
pre-existing chicken-salad row1000000019 was preserved.

Visually inspected,unedited final screenshots:
- [Home](/Users/anastasia/.codex/visualizations/2026/10/22/01a16557-276b-7002-b45e-4a79f11f1c89/mvp-20261024/home-final.png)
- [Profile](/Users/anastasia/.codex/visualizations/2026/10/22/01a16557-276b-7002-b45e-4a79f11f1c89/mvp-20261024/profile-final.png)
- [Meal history](/Users/anastasia/.codex/visualizations/2026/10/22/01a16557-276b-7002-b45e-4a79f11f1c89/mvp-20261024/meal-history-final.png)
- [Add by text](/Users/anastasia/.codex/visualizations/2026/10/22/01a16557-276b-7002-b45e-4a79f11f1c89/mvp-20261024/add-by-text-final.png)
- [Editor with note](/Users/anastasia/.codex/visualizations/2026/10/22/01a16557-276b-7002-b45e-4a79f11f1c89/mvp-20261024/meal-edit-final.png)
- [Rhythm detail](/Users/anastasia/.codex/visualizations/2026/10/22/01a16557-276b-7002-b45e-4a79f11f1c89/mvp-20261024/rhythm-final.png)

## Remaining owner/release dependencies

1. Retention decision unanswered:saved meals and optional name are session-only.
   The bounded codec/storage port exists;no disk adapter or raw-photo retention.
2. G-2 source approval/package remains pending;the USDA proposal is separate.
   Names alone cannot supply nutrition or nutrient rewards.
3. Production iOS inference still selects PhotoAnalysisAnalysisLauncher.None.
   Local inference activation/calibration/device performance remain G-3 work.
4. Required physical Android evidence is expired;emulator tests cannot renew it.
   Intel cannot execute the main Compose iOS renderer;Apple execution remains optional.
5. Other published G-1/G-4/G-5/G-6 decisions/evidence remain under the canonical
   architecture. This local UI/diary change does not authorize remote activation.

## Reproducibility and cleanup

Logs/helpers under ignored mobile/build/verification/mvp-audit. Full gate command:

```sh
DEVELOPER_DIR=/Applications/Xcode-26.6.0.app/Contents/Developer \
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./mobile/gradlew -p mobile \
 :apps:androidApp:assembleProductionDebug :apps:androidApp:assembleProductionDebugAndroidTest \
 :apps:androidApp:assembleUiAcceptanceDebug :apps:androidApp:assembleUiAcceptanceDebugAndroidTest \
 qualityCheckJvmAndroid qualityCheckApple \
 :apps:sharedKit:linkDebugFrameworkIosArm64 :apps:sharedKit:compileTestKotlinIosArm64 \
 -Psupernutrition.enableIosDeviceTarget=true -Psupernutrition.enableSharedKitFramework=true \
 -Psupernutrition.enableTestingAppleTargets=true --dependency-verification=strict \
 --no-daemon --no-configuration-cache --max-workers=1 --continue --console=plain
```

Final graphify update after Java driver fixes exit0:22367 nodes,31014 edges,2077 communities. CLI code
AST graph only;HTML skipped for >5000 nodes,524 no-AST files and2 SQL files lack
optional parser support. No semantic-document extraction claim. git diff --check
passes. Primary checkout/unrelated WIP preserved;no remote push/merge in this task.

Xcode command correction:the combined -target SuperNutrition plus -target
SuperNutritionUIAcceptanceTests invocation failed before compilation because both
hosts declare the same framework output. No source defect was inferred. Separate
sequential target invocations are used for final evidence; -jobs1 alone cannot
resolve an Xcode dependency-graph output conflict.


Final connected command (same environment as the full source gate):

```sh
./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest \
 qualityCheckJvmAndroid qualityCheckApple \
 -Pandroid.testInstrumentationRunnerArguments.deviceSerial=emulator-5554 \
 -Psupernutrition.enableIosDeviceTarget=true -Psupernutrition.enableSharedKitFramework=true \
 -Psupernutrition.enableTestingAppleTargets=true --dependency-verification=strict \
 --no-daemon --no-configuration-cache --max-workers=1 --continue --console=plain
```

Xcode targets were built separately with the same flags:

```sh
for mvp_xcode_target in SuperNutrition SuperNutritionUIAcceptanceTests
do
 DEVELOPER_DIR=/Applications/Xcode-26.6.0.app/Contents/Developer \
 JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
 xcodebuild -project mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj \
 -target "$mvp_xcode_target" -configuration Debug -sdk iphoneos ARCHS=arm64 \
 CODE_SIGNING_ALLOWED=NO SYMROOT="$PWD/mobile/apps/iosApp/build/MvpProducts" \
 -jobs 1 -hideShellScriptEnvironment build
done
```

Native XML reports:

- [poc XML](</Users/anastasia/.codex/worktrees/f2ff/SuperNutrition/mobile/apps/androidApp/build/outputs/androidTest-results/connected/debug/flavors/poc/TEST-SN_Screenshots_20261023(AVD) - 15-_apps_androidApp-poc.xml>)

- [production XML](</Users/anastasia/.codex/worktrees/f2ff/SuperNutrition/mobile/apps/androidApp/build/outputs/androidTest-results/connected/debug/flavors/production/TEST-SN_Screenshots_20261023(AVD) - 15-_apps_androidApp-production.xml>)

- [uiAcceptance XML](</Users/anastasia/.codex/worktrees/f2ff/SuperNutrition/mobile/apps/androidApp/build/outputs/androidTest-results/connected/debug/flavors/uiAcceptance/TEST-SN_Screenshots_20261023(AVD) - 15-_apps_androidApp-uiAcceptance.xml>)

## Delivery state

After AGP removed its test targets,Android CLI reinstalled and activated production
successfully. Fresh device base.apk SHA256 matches d5b3705e072559421656eebebdd6c0e3d2faf6a9094403901b67ec95f67f38ce.
Font scale1.0;Home reports0 records in a fresh session,without QA entries.
[Final installed Home screenshot](/Users/anastasia/.codex/visualizations/2026/10/22/01a16557-276b-7002-b45e-4a79f11f1c89/mvp-20261024/home-installed-final.png)
was visually inspected. Earlier populated screenshots demonstrate manually entered
QA records on this identical APK;they are not seeded production data.
