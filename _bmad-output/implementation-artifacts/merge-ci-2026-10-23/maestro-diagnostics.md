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

## Reproduction with detailed diagnostics, before transport changes

[Candidate Android 40021687118](https://github.com/flexsent-labs/SuperNutrition/actions/runs/40021687118), commit `2f9a844d7a365ef9e4b7e3b00e42945482274117`, passed 50/50 instrumentation tests and reproduced the Maestro failure in six seconds. Both the initial `maestro.log` and per-flow logs, manifest, and `commands.json` are now present in the uploaded artifact.

The completed steps are variable definition, apply configuration, and the shared locator script. `launchApp` fails before starting the application: `Command failed (host:transport:emulator-5554): device offline`. The subsequent hierarchy/screenshot diagnostics fail with `DeviceServerDiedException` and gRPC `UNAVAILABLE`, caused by the same ADB offline transport. Logcat ends at 21:00:09.449 UTC; Maestro reports the failing transport at 21:00:12.274 UTC. Cleanup can still obtain the launcher hierarchy, so this is an interruption of the host/device connection rather than evidence that the application crashed. The exact trigger for the disconnect still needs to be established before selecting a transport fix.

[Candidate JVM 40021717907](https://github.com/flexsent-labs/SuperNutrition/actions/runs/40021717907) completed with 892 tests and zero failures/errors/skips. Every other check, including the new script regression and README snapshot, passed. Its sole failed task remains `:verifyMobilePolicy` with expired `bootstrap-android-host`.

## Bounded permission-discovery change, planned before implementation

The per-flow device log captures the missing native interval: adbd's `host-17` connection reports `write failed` at 21:00:11.382 and reconnects as `host-18` at 21:00:11.949. The pinned driver implicitly grants `all` permissions by pulling the entire acceptance APK and parsing its manifest in `setAllPermissions`; a broad upstream `runCatching` discards errors from that extraction. This runs between the completed locator script and the logged `stopApp` operation. A transfer-related disconnect is a hypothesis grounded in that interval, not yet a proven cause.

Declare `camera: allow` on the shared `launchApp` command. The pinned Android driver then maps directly to one `pm grant android.permission.CAMERA` without APK extraction; the pinned iOS driver explicitly supports the same camera declaration. The journey's actions and assertions remain identical, including camera entry and cancellation. Record ADB server transport logs from first startup so any recurring disconnect can be diagnosed without another lost native interval. Do not restart a running shared ADB server, reconnect/retry failed journeys, or change native readiness criteria.

## Follow-up review finding, before correction

Fresh Blind Hunter identified that the additional script regression and diagnostics change the tracked source line count, while README still contains the previous 325,804-line snapshot. Regenerate it before pushing; acceptance is the same dashboard generator and `update_readme_metrics.py --check` used by CI returning zero on the final candidate. This is a mechanical integration fix, with no coverage denominator or threshold change.

Correction: the exact CI generators now produce 1,040 source files / 325,882 code lines, and README `--check` passes. Fresh Edge Case Hunter found no supported defect; Blind Hunter found only this corrected README drift. Both reran the seven focused diagnostics tests successfully.

Follow-up local evidence: the new ADB diagnostic regression ran red before implementation (four existing cases passed, three new cases exposed missing diagnostics), then 7/7 passed. The full script suite now passes 67/67, zero failures/errors/skips, 47.848 seconds. The actual pinned Maestro `check-syntax` accepts the explicit camera map. Shell syntax and whitespace checks pass. The build-logic conformance suite is running separately; hosted Android/iOS validation of the updated shared flow remains pending.
