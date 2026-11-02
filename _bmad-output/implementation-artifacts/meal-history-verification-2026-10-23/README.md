# Profile → Meal history verification

Worktree: `REDACTED_WORKTREE/f2ff/SuperNutrition`
Branch: `codex/home-profile`
Baseline: `527e08d08d5cfa35cde0529e3412812d3a5ca7f9`
Design: Figma file `08zQiW2IIHfQUBN3JDRUcZ`, node `604:893`.

## Results

| Check | Result | Evidence |
|---|---|---|
| Complete UI JVM module, final source | 205 tests,38 suites,0 failures/errors/skips | `results.json`; `mobile/feature/photo-analysis-ui/build/test-results/jvmTest/` |
| Complete Android instrumentation before final typography patches | poc45,production45,uiAcceptance45; all0 failures/errors/skips | `full-android-*.xml` |
| Final production navigation after typography/search/contrast patches | poc1,production1,uiAcceptance1; all0 failures/errors/skips | `final-navigation-*.xml` |
| Actual Apple sources | UI,photo-analysis,SharedKit compileKotlinIosArm64 completed | `/tmp/supernutrition-meal-history-evidence/final-verified.log` |
| Final read-only gates and coverage reports | Exit1: only fatal failure is stale bootstrap-android-host evidence | `/tmp/supernutrition-meal-history-evidence/final-checks.log` |
| Manual production app | Home→Profile→History; native empty state at100%/200%; scale restored1.0 | local visualizations below; APK digest matches installed base.apk |

Host is macOS x86_64 with Android Studio JDK21; Apple toolchain `/Applications/Xcode-26.6.0.app/Contents/Developer`. Device is only emulator-5554, Android15/API35 x86_64,720x1560px,dpi320 (360x780dp). This is not physical Android evidence. Apple device/simulator execution and linking/signing are not claimed. The human's emulator-only decision is preserved; no physical-evidence record, expiry, quality policy or baseline was changed. Aggregate coverage gates are blocked by policy, so no aggregate/changed-line coverage pass is claimed.

Typed Detekt additionally reports three compiler-analysis errors: common `expect currentHistoryReferenceDay` and platform `actual` are treated as declarations in the same analysis module. Real target compilation succeeds. This limits typed-analysis accuracy; it is recorded in deferred-work.md and is not hidden by a suppression or task skip. The targeted diagnostic uses the checked-in `detekt-diagnostic.gradle` artifact with `--init-script`; debug log is `/tmp/supernutrition-meal-history-evidence/detekt-diagnostic-fixed.log`.

## Reproduce

Full suite command from the repository root:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
DEVELOPER_DIR='/Applications/Xcode-26.6.0.app/Contents/Developer' \
./mobile/gradlew -p mobile \
  qualityCheckJvmAndroid qualityCheckApple mobileUnitCoverage mobileCoverageReports \
  :feature:photo-analysis:compileKotlinIosArm64 :apps:sharedKit:compileKotlinIosArm64 \
  :apps:androidApp:connectedAndroidTest \
  -Psupernutrition.runJvmCoverage=true -Psupernutrition.enableIosDeviceTarget=true \
  --dependency-verification=strict --continue --no-parallel --max-workers=2 \
  --no-configuration-cache --console=plain
```

The completed full Android run is `/tmp/supernutrition-meal-history-evidence/full-verification.log` (exit1,16m50s; native135 passed, earlier UI-test/Apple compile issues were repaired). Final patched navigation added `-Pandroid.testInstrumentationRunnerArguments.class=com.supernutrition.android.PhotoAnalysisComposeSemanticsInstrumentationTest#profileOpensMealHistoryAndBackPreservesProfile`; all three variants ran. Its `/tmp/supernutrition-meal-history-evidence/final-verified.log` confirms Apple compilation and three passing native cases; the test-format finding in that run was then fixed.

The final post-format run used the same strict options and both quality entrypoints plus mobileUnitCoverage,mobileCoverageReports and `:feature:photo-analysis-ui:jvmTest`, without native tasks or the instrumentation-class filter. It finished in4m11s with205 passing UI tests and only the policy failure. Production sources did not change between that native/Apple run and this final test-format-only rerun.

Visual snapshots regenerate with `:feature:photo-analysis-ui:jvmTest --tests '*MealHistoryLayoutTest*'`. Test photographs are fixture resources only, never ledger entries. Source assets are preserved in `mobile/feature/photo-analysis-ui/figma-assets-20261023-meal-history/`; four original vectors are rasterized at4x, and the byte-identical existing Profile Back export is reused. SHA256 comparison against every PNG in the production APK found none of the three fixture photographs.

## Red regressions and review

- Native regression failed before implementation:1 test/1 failure, exact missing dedicated search screen. `red-android-production.xml`.
- Layout regressions failed before fixes:3 tests/2 failures, first card y194 vs198 and search height48→72. `red-layout.xml`.
- Review regressions failed before patches:12 tests/2 failures, rendered contrast2.953:1 and NBSP search returning no match. `red-review-*.xml`.
- Fresh-context Blind Hunter and Edge Case Hunter completed independent source reviews. Three unique actionable findings were fixed: contrast, Unicode whitespace, and Apple relative-day alignment. Follow-up source reviews confirmed all three. No remaining review finding is presented as unverified.
- Final375x812 fixture matches list positions (first card y198,h77; Yesterday y368), uses loaded fonts and exact imagery slots. At360x780/200%, text wraps and every card/action remains reachable. Muted text is #7D6B5F for4.72:1 on the canvas and5.07:1 on white; the Figma #A08D80 did not meet normal-text contrast.

## Data boundary

Actual cards use the existing confirmed-food ledger, grouped by(day,mealId), which also supplies Profile count. Search stays local and accepts Unicode whitespace/case variants. Stored epoch days are formatted without timezone shifting; reference clocks follow existing Android-local/Apple-UTC ledger conventions and refresh on opening.

The ledger does not retain photos, times or authored dish titles. Runtime rows use grounded product names, the original placeholder and availability copy. Selection is read-only. Production camera confirmation currently does not ground/add records to this ledger, so its default history is honestly empty. That bridge, persistence and nutrition-authority changes are outside this screen request. No sample meals are seeded; no new data retention, telemetry or consent defaults are added. Only route/scroll state is saveable; query and food contents remain in memory.

## Local artifacts

Visualizations directory: `~/.codex/visualizations/2026/10/22/01a16557-276b-7002-b45e-4a79f11f1c89/`:

- `meal-history-375x812.png`: explicitly synthetic Figma fixture.
- `meal-history-360x780-200-top.png`, `meal-history-360x780-200-bottom.png`: synthetic enlarged-text fixture.
- `meal-history-native-history-empty.png`, `meal-history-native-history-200.png`: actual installed production app, empty ledger.

Production APK: `mobile/apps/androidApp/build/outputs/apk/production/debug/androidApp-production-debug.apk`.
SHA256: `ff7cdcbcb3f622fcce9e37740f13a7712365102a26c9530db6718e8f02b925a2` (identical on installed base.apk).

Source/date converter attribution is in the asset README; [Hinnant's primary algorithms](https://howardhinnant.github.io/date_algorithms.html#civil_from_days) are public domain. Resource font defaults were checked in [Compose1.11.1 FontResources.kt](https://github.com/JetBrains/compose-multiplatform/blob/v1.11.1/components/resources/library/src/commonMain/kotlin/org/jetbrains/compose/resources/FontResources.kt). The inherited global typography is unchanged.

The website publication remains independent: source/build pipeline is in flexsent-labs/supernutrition.app; Cloudflare→GitHub browser sign-in is still pending. No local upload or alternate website publishing path is used.

`git diff --check` and staged diff checks passed. Final `graphify update . --no-cluster` completed:21467nodes,30899edges. It reports460 zero-node source files and2 SQL files without tree_sitter_sql; generated graph/cache files are ignored and not staged. No semantic-completeness claim is made from the code graph.
