# Merge and CI verification — 2026-10-23

The current feature work is in PR55 on `codex/home-profile`: Home→Profile, Profile→Meal history, existing-screen Figma alignment and grounded Home capybara. Website legal documents are already integrated through the separate website repository. Historical closed/superseded branches and unrelated dirty worktrees remain preserved.

## Confirmed CI repairs

The previous main [iOS run39863651736](https://github.com/flexsent-labs/SuperNutrition/actions/runs/39863651736) ran7 XCTest cases:1 passed,6 failed. Exported stderr proves a missing Compose `capture_close.png`; see [summary](ios-before-summary.json) and [crash excerpt](ios-before-crash.txt).

SharedKit now aggregates transitive resources through the pinned Compose plugin. Both native hosts call one helper that links the selected framework and synchronizes resources into the Xcode bundle. Supported arm64 Xcode inputs are required; standalone framework linking is retained. The Compose compiler's three existing JVM/metadata lock configurations were refreshed after reproducing the strict-lock failure.

The JVM workflow uses `--continue`, retains strict policy failure, has30minutes for independent work, runs report checks after non-cancelled failures, and uploads test results plus Gradle problem diagnostics. The outdated README snapshot was regenerated without changing coverage policy.

## Local verification

- Intel x86_64 macOS15.8, Android Studio JDK, process-local Xcode26.6. Global Xcode selection unchanged.
- New resource/helper regression tests failed before the fix (7 cases,13 failing subtest observations reported by implementation agent); all39 script tests now pass.
- Actual SharedKit iosArm64 link and resource synchronization passed with strict dependency verification in5m39s. All261 original Compose assets, including Fraunces/Nunito, are packaged byte-for-byte: [inventory](ios-resource-inventory.json).
- Actual unsigned production Xcode host build passed in66.76s. Its bundle contains270files including all261 Compose resources: [command/result](ios-production-host-build.json). This proves compilation and packaging; local simulator/device execution is unavailable on Intel.
- Contract/schema/market/root/release checks passed. CI policy and owner-decision strict validation passed. `git diff --check` passed.
- `graphify update .` completed,21727nodes/29590edges; its extractor warns of two SQL files missing the optional SQL parser and498files yielding no graph nodes.
- Full JVM/Android quality and report execution is in progress with `--continue`; hosted Android execution started as [run39986624811](https://github.com/flexsent-labs/SuperNutrition/actions/runs/39986624811). No pending run is represented as passed.

## Preserved prerequisite

All six required successful Android evidence records expired on2026-10-12T08:04:52Z. Five explicitly require a physical Android device. The user has only an emulator; that cannot renew physical evidence. [Typed manifest snapshot](policy-blockers.json). Full policy remains red until genuine fresh physical execution is bound; no dates, requirements, skips, floors or exclusions were changed.

See [findings](findings.md) and [independent review triage](review-triage.md). Final hosted results and merge identity will be appended after execution.

## Follow-up candidate

Hosted Android run39986624811 completed50uiAcceptance cases with1failure/0errors/0skips. Logcat proves an asynchronous test precondition: Back was injected14ms after requesting IME hide, before its animation ended. The test now waits actual visible/hidden insets plus native animation completion, handles API30 pre-start cancellation, and restores the hardware-keyboard setting. Explicit Core1.16.0 test compile dependency uses the existing runtime version. Three variant javac builds passed; the final focused native run is in progress. [Original XML counts](android-before-tests.json).

Hosted iOS run39988860538 confirms the resource crash is gone:8cases executed,7assertion failures/1pass/0skips, all at the obsolete camera status selector. [Diagnosis](ios-candidate-diagnosis.json). XCTest now verifies the current canonical stage/value and capture controls with product-owned localization in its own test bundle. All progress/recovery/navigation assertions remain. Actual hosted rerun remains required.

SharedKit JVM compilation initially exposed missing lock associations and missing direct Compose Runtime. Both are corrected; portable common metadata locks were also regenerated following independent review. A complete gate rerun is still required after the final changes. Source reviews, partial runs and successful compilation are not a full policy pass.
