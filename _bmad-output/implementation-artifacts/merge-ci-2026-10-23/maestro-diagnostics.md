# Last-main CI follow-up: Maestro diagnostics

## Observed failure, before changes

Main commit `e1f74eccc06cb4175f9c157ce8da418d6e30ca85` completed all three workflows:

- [Android 40014984372](https://github.com/flexsent-labs/SuperNutrition/actions/runs/40014984372): 50 instrumentation tests, zero failures/errors/skips. The subsequent Maestro journey failed in seven seconds. Its sole JUnit failure was `Unknown error`.
- [iOS 40014984509](https://github.com/flexsent-labs/SuperNutrition/actions/runs/40014984509): 8 XCTest cases and 1 Maestro journey passed; zero failures or skips. The hosted simulator was arm64 iPhone 17 Pro, iOS 26.4.1, macOS 26.6.2.
- [JVM 40014984391](https://github.com/flexsent-labs/SuperNutrition/actions/runs/40014984391): 892 tests across 12 modules, zero failures/errors/skips. The only failed task was `:verifyMobilePolicy`, reporting expired `bootstrap-android-host` evidence.

The failed Android log shows successful completion and intentional instrumentation teardown, followed by APK reinstall. There is no subsequent application process start or activity launch, app crash, or ANR. The Maestro driver starts its gRPC service and accessibility listener. The final hierarchy is the launcher. These observations localize the failure to automation before app launch, without identifying the actual exception. The logcat capture ends before Maestro emits its failure; absence of a native error is therefore bounded evidence.

## Findings recorded before the fix

1. `--test-output-dir` does not select the initial detailed Maestro log path. A CLI failure can retain its useful exception outside the task report directory.
2. The workflow's explicit upload allowlist omits `report/debug/`, so even debug artifacts written there are discarded when the hosted runner exits.

The pinned Maestro 2.7.0 CLI help and upstream `TestDebugReporter` implementation confirm support for `--debug-output` and `--flatten-debug-output`. Flattening keeps the initial `maestro.log` outside hidden `.maestro/` subdirectories, which `upload-artifact` excludes by default. The same existing task directory can then hold the initial detailed log and subsequent per-flow artifacts.

This is a diagnosed observability defect. It is not evidence that changing output paths fixes the original automation exception. A fresh hosted execution is required; any recurrence must be diagnosed using its preserved exception before changing behavior.

## Local verification

- New regression against the baseline: 4 cases, 2 expected failures. One demonstrates the early detailed log escaping the report directory; the other demonstrates the upload allowlist discarding a nested debug file. Exit-status and JUnit verification cases already pass.
- Fixed focused regression: 4/4 pass, including original nonzero exit `37`, early log preservation, upload retention, success-report verification, and rejection of zero-exit/no-report results.
- Full `python3 -m unittest discover -s scripts/tests -v`: 64/64 pass, zero failures/errors/skips, 25.134 seconds.
- `bash -n scripts/run-ui-journeys-android.sh`, `git diff --check`, and `scripts/verify-owner-decisions.sh --strict`: pass.

No flow assertion, native startup guard, evidence timestamp, mobile production source, or pinned tool version changes in this diagnostic slice. Hosted validation and final main conclusions will be recorded after they exist. Final run metadata can be retained outside the Git tree or in the PR description to avoid triggering another unobserved main run with a docs-only follow-up.

## Independent review

Fresh Blind Hunter and Edge Case Hunter reviewed the full diagnostic slice independently. Both reported no concrete defect. Blind Hunter additionally checked the installed 2.7.0 bytecode: initial logging precedes device sessions, and flattening writes directly beneath the supplied directory. The four focused tests passed during review. This review covers diagnostic retention; hosted reproduction and final CI completion remain separate acceptance conditions.
