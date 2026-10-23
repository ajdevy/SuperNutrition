---
title: Retain actionable Maestro diagnostics and finish main CI verification
type: bugfix
created: 2026-10-23
status: in-review
baseline_commit: e1f74eccc06cb4175f9c157ce8da418d6e30ca85
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="User explicitly requested finishing main CI and fixing failures except expired device evidence">

## Intent

**Problem:** The latest main Android workflow passed all 50 instrumentation tests and then failed its Maestro journey before launching the app. Its JUnit report contains only `Unknown error`; the runner does not preserve the Maestro diagnostic log in uploaded artifacts, preventing a supported diagnosis. The same executable tree passed the preceding main run, so one successful rerun alone cannot establish the underlying cause.

**Approach:** Preserve detailed Maestro diagnostics on the first failed execution, identify and repair any demonstrated automation or product failure, and wait for all workflows on the final main commit to finish. Keep expired physical-device records visible as the accepted limitation.

## Boundaries & Constraints

**Always:** Preserve complete Android instrumentation, native startup checks, and the entire Maestro journey. Keep the pinned, checksum-verified Maestro 2.7.0 toolchain. Preserve the command's exit status and original failure. Keep reports beneath the workflow's dedicated task directory and upload failure diagnostics even when the test step fails. Use current run artifacts and exact commit identities for final claims. Keep the dirty primary checkout untouched.

**Ask First:** Changes to a published contract or privacy decision beyond the existing user authorization.

**Never:** Fabricate or refresh device evidence timestamps; weaken assertions, disable native services, skip tests, accept retries as a root-cause fix, or claim an emulator renews physical evidence. Do not commit generated build output or tokens. Do not leave the final main workflows pending.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Successful Maestro | Pinned CLI exits zero | JUnit and debug outputs are in the uploaded report directory | Existing report verifier still runs |
| Maestro exception | CLI fails before the first flow command | Detailed diagnostic output is retained and uploaded | Preserve the nonzero command result |
| Artifact upload | Test step fails | Native diagnostics, JUnit, and Maestro debug files are available together | Existing always-upload behavior remains |
| Final main policy gate | Required device records remain expired | Report the known policy failure explicitly | No policy waiver or hidden skip |

</frozen-after-approval>

## Code Map

- `scripts/run-ui-journeys-android.sh` — API36 full AOSP boot, instrumentation, APK reinstall, and Maestro invocation. `--test-output-dir` alone does not redirect the detailed Maestro log.
- `.github/workflows/android-ui-coverage.yml` — uploads an explicit allowlist of report paths, currently omitting Maestro's debug directory.
- `scripts/tests/` — device-free regression tests for CI tooling.
- `mobile/.maestro/photo-analysis/` — unchanged application journey and shared selectors; no evidence currently demonstrates a locator defect.
- `scripts/verify_ui_test_reports.py` — fail-closed report checks which must remain in place.

## Tasks & Acceptance

**Execution:**
- [x] `scripts/tests/` — first demonstrate the lost-diagnostic path with a failing regression covering command failure and upload retention.
- [x] `scripts/run-ui-journeys-android.sh` and `.github/workflows/android-ui-coverage.yml` — redirect detailed Maestro logs into an uploaded directory without intercepting or relaxing its failure status.
- [x] This spec and the verification record — record the latest failed-run evidence before fixes and distinguish diagnosed defects from hypotheses.
- [ ] Hosted Android workflow — reproduce with diagnostics, fix any demonstrated underlying failure, and retain its executable verification evidence.
- [ ] Final main workflows — wait for completion and report Android/iOS results plus the sole accepted policy limitation.

**Acceptance Criteria:**
- Given a CLI failure before app launch, its diagnostic log is available in the always-uploaded artifact and CI remains failed.
- Given the repaired final main commit, Android instrumentation and Maestro complete successfully, iOS completes successfully, and the JVM failure contains only accepted expired device evidence.
- No production mobile code, test assertions, evidence dates, or native startup checks change without a separately demonstrated defect.

## Spec Change Log

## Design Notes

Failed main Android run `40014984372` took seven seconds in Maestro. The native log contains no app process start after reinstall, no app crash, and no ANR. Its driver starts gRPC/accessibility; the captured log ends before Maestro reports failure. The terminal output and JUnit cannot distinguish driver initialization, a launch request, or another exception. Main iOS run `40014984509` succeeded; JVM run `40014984391` failed only at `:verifyMobilePolicy` for expired `bootstrap-android-host`. Preserve these observations without inventing a causal fix.

The pinned CLI's own help supports `--debug-output` together with `--test-output-dir`. Both should target a directory the artifact step actually includes. The original shell `set -e` propagation and downstream report verification are adequate and should remain simple.

## Verification

**Commands:**
- Focused device-free regression, before and after the diagnostic fix — demonstrate red then green with the intended reason.
- `python3 -m unittest discover -s scripts/tests -v` — all CI-tooling tests pass.
- `bash -n scripts/run-ui-journeys-android.sh` and `git diff --check` — clean shell syntax and whitespace.
- `graphify update .` — update local graph after code changes; keep generated graph files out of the commit.
- Hosted workflow dispatch on the candidate followed by final main runs — inspect original exit status, native/JUnit counts, detailed logs if failed, and terminal conclusions of all three workflows.
