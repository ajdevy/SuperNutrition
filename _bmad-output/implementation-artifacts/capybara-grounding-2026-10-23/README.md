# Home capybara grounding — 2026-10-23

The capybara now sits on the existing garden floor, with a restrained contact shadow. Foreground content positions, text, card spacing, actions and sprite timing are unchanged. The full garden and frosted card use one measured cover/crop transform; the scene follows the paws when scrolling.

Worktree: `/Users/anastasia/.codex/worktrees/f2ff/SuperNutrition`.
Branch: `codex/home-profile`; baseline `465f1933f36f4b8631fa923c8d299f693b2da1a7`.
Host: Intel x86_64 macOS15.8, Android Studio JBR; process-local Xcode26.6 for shared arm64 compilation.

## Actual Android evidence

Task-owned AVD `SN_Screenshots_20261023`, API35/x86_64, density320, serial `emulator-5554`. Physical viewport720×1560px (360×780dp), with a temporary824×1830px override for412×915dp. Existing AVDs were preserved.

| Case | Before | After |
|---|---|---|
| Ordinary360×780 |[PNG](native-before-360x780.png)|[PNG](native-after-360x780.png)|
| Tall412×915 |[PNG](native-before-412x915.png)|[PNG](native-after-412x915.png)|
| Text200%, initial | — |[PNG](native-after-360x780-font200.png)|
| Text200%, scrolling with capybara visible | — |[PNG](native-after-font200-scrolled.png)|

All after images are real screenshots of the newly installed production APK, visually inspected immediately after capture. Its SHA-256 is `195b8e8e63e47b3945cf31a03e31f2f471e6db1b88c5d6345eb0f0b377c3a266`, matching the actual installed package. [Hash comparison](installed-apk.json), [source digests and manual results](native-smoke.json).

Manual checks verified sand and contact shadow under the paws at both sizes, Profile opening and Back returning to Home, and grounding through200% text and scrolling. Display override and font scale were restored, and the task-owned emulator was stopped. These are manual native checks; the full Android instrumentation suite was not rerun for this narrow visual change. No physical-device or iOS runtime result is claimed.

## Regression and module verification

The actual pre-fix412×915 rendered regression failed1/1 by finding sky beside the paw contact. Its [red XML](jvm-grounding-red.xml) and [log](jvm-grounding-red.log) are preserved. A preliminary360px synthetic case already had sand and was not counted as red.

The first focused green run passed41/41. After the final missing-coordinate fallback correction, the full UI module passed **237 tests,0 failures/errors/skips**, using fresh per-suite XML. [Full results](jvm-full-results.json), [XML directory](jvm-full/). Geometry, native-like insets, regular/tall/wide/short screens, larger text, readable scrolling, exact section spacing, animation/reduced motion and glass-coordinate pixel checks pass.

The combined final command exited0 in8m56s. Production Android packaging and explicit shared-UI iosArm64 compilation passed, with original strict dependency verification. [Full log](full-validation.log).

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
DEVELOPER_DIR='/Applications/Xcode-26.6.0.app/Contents/Developer' \
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest \
  :feature:photo-analysis-ui:detekt :apps:androidApp:assembleProductionDebug \
  :feature:photo-analysis-ui:compileKotlinIosArm64 \
  -Psupernutrition.enableIosDeviceTarget=true --dependency-verification=strict \
  --continue --no-configuration-cache --no-daemon --max-workers=2 --console=plain
```

## Coverage and remaining quality boundaries

A fresh `:feature:photo-analysis-ui:koverXmlReportJvm` report covers all19 executable lines and all24 branches of the new pure garden geometry. [Coverage summary](coverage-summary.json), [XML](coverage-jvm.xml).

The whole UI module raw report has4635/5357 lines (86.52%) and1274/1923 branches (66.25%), below the85% branch floor. This broader gap remains open; this task does not establish a green full coverage/quality gate. No exclusions, baselines or test skips were added.

Fresh changed-source-set commonMain/commonTest/jvmTest Detekt reports show0 findings. The separate typed `detektMainJvm` analysis warns of3 compiler-analysis errors, limiting its accuracy; actual JVM/Android/arm64 compilation succeeded. Required physical Android policy evidence remains unavailable. The accepted Intel-only iOS runtime limitation and the separately recorded iOS resource-packaging defect are unchanged.

`git diff --check` passes. `graphify update . --no-cluster` refreshed the local code graph to21699 nodes/31332 edges; it reports496 zero-node inputs and the missing optional SQL parser for two SQL files. No semantic document/image refresh is claimed. [Graph log](graphify-update.log).

## Findings

[Recorded acceptance conditions and limitations](findings.md). [Independent review triage](review-triage.md) records both reviewer outcomes and the bounded visibility-assertion correction. The full unfiltered UI suite and changed-test lint reran after that correction:237/237,0 findings, exit0 in2m58s. [Final validation log](review-validation.log). Production source and the verified installed APK are unchanged. Stored logs normalize trailing whitespace and surplus final blank lines only.
