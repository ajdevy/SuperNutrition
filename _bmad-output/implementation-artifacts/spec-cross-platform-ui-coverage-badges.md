---
title: 'Cross-platform UI coverage reports and README badges'
type: 'feature'
created: '2026-07-31'
status: 'done'
baseline_commit: 'dd614e6f6b0387bc7a464b3c59bc1bf13bdd8516'
review_loop_iteration: 0
context:
  - '/Users/aj/.codex/worktrees/7479/SuperNutrition/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md'
  - '/Users/aj/.codex/worktrees/7479/SuperNutrition/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/reviews/review-ui-test-final.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The repository has Android and iOS UI acceptance suites, but they are only locally invocable and their results are not visible beside the README's JVM coverage and code-size snapshot.

**Approach:** Add explicit hosted Android and iOS UI-test workflows that execute the existing platform suites, upload machine-readable reports, and expose workflow status plus generated coverage/LOC metrics through a compact README badge strip and report links.

## Boundaries & Constraints

**Always:** Keep Android instrumentation and Maestro evidence distinct from JVM line/branch coverage; keep iOS simulator evidence distinct from physical-device evidence; pin every GitHub Action to a full commit SHA; upload diagnostics even when a test job fails; preserve read-only quality tasks and existing optional Apple local fallback behavior.

**Ask First:** None.

**Never:** Do not claim emulator/simulator UI results as physical-device or hardware evidence; do not use `continue-on-error` to make a failed UI suite look green; do not change product behavior, coverage thresholds, branch protection, or deploy GitHub Pages; do not add badges with hard-coded UI percentages that are not produced by a report.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| HAPPY_PATH | Hosted Android or macOS runner with its platform toolchain | Existing UI suite runs and JUnit/diagnostic artifacts are uploaded | Workflow succeeds |
| UI_FAILURE | A platform UI test fails | Workflow is red and the available JUnit, screenshots, logs, or xcresult remain downloadable | Upload step runs with `always()` |
| TOOLCHAIN_UNAVAILABLE | Required hosted Android SDK or Xcode/simulator is unavailable | The requested platform workflow fails explicitly | No fallback or false-positive badge |

</frozen-after-approval>

## Code Map

- `scripts/run-ui-journeys-android.sh` -- starts the emulator, runs Android instrumentation, and executes the shared Maestro journey.
- `scripts/run-ui-journeys-ios.sh` -- builds the iOS acceptance host, runs XCTest plus Maestro, and emits simulator diagnostics.
- `scripts/verify_ui_test_reports.py` -- rejects missing, empty, skipped, or failed JUnit/XCTest results before a workflow can report success.
- `.github/workflows/coverage-readme.yml` -- publishes the existing static coverage/LOC artifact.
- `.github/workflows/ci-required.yml` -- publishes JVM coverage artifacts from the unit gate.
- `scripts/update_readme_metrics.py` and `scripts/tests/test_update_readme_metrics.py` -- render and verify the generated README snapshot and metric badges.
- `README.md` -- presents workflow badges, generated metric badges, and links to each report source.

## Tasks & Acceptance

**Execution:**

- [x] `scripts/run-ui-journeys-android.sh` -- run the connected Android UI suite before Maestro and retain its report path -- cover both instrumentation and cross-platform journey evidence.
- [x] `scripts/run-ui-journeys-ios.sh` -- support required hosted execution and save an xcresult bundle -- prevent unavailable Apple tooling from becoming a false pass.
- [x] `.github/workflows/android-ui-coverage.yml` and `.github/workflows/ios-ui-coverage.yml` -- run each platform suite on pull requests and `main`, with pinned checkout/tool setup and always-uploaded artifacts -- make results discoverable.
- [x] `.github/workflows/coverage-readme.yml` and `.github/workflows/ci-required.yml` -- upload their generated reports -- make existing README badges actionable.
- [x] `scripts/verify_ui_test_reports.py`, `scripts/tests/test_verify_ui_test_reports.py`, `scripts/update_readme_metrics.py`, its tests, and `README.md` -- add fail-closed report validation plus polished status/metric badges and report links without stale generated values -- keep documentation validation deterministic.

**Acceptance Criteria:**

- Given a pull request or `main` push, when Android UI coverage runs, then the connected Android tests and the shared Maestro journey execute on the hosted emulator and the workflow exposes their reports.
- Given a pull request or `main` push, when iOS UI coverage runs, then XCTest and the shared Maestro journey execute on the hosted simulator and the workflow exposes JUnit plus xcresult diagnostics.
- Given any generated README snapshot, when the coverage workflow verifies it, then JVM line/branch, source-file, code-line, Android UI, iOS UI, and report workflow badges are present and their links resolve to the corresponding workflow/report.
- Given a platform suite fails or its required toolchain is unavailable, when artifacts are finalized, then the workflow remains failed and diagnostics are still uploaded.

## Design Notes

The UI badges represent executable workflow status, not source-code percentages. The generated numeric badges remain limited to the Kover JVM denominator and source-line counter already used by the README snapshot. This keeps acceptance evidence honest while giving each report a one-click path from the project front page.

## Verification

**Commands:**

- `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` -- expected: all script and README-rendering tests pass.
- `scripts/verify-root-boundaries.sh` and `scripts/verify-ci-policy.sh` -- expected: repository and existing CI policy remain valid.
- `scripts/run-ui-journeys-android.sh` on a configured Android host -- expected: instrumentation and Maestro reports pass.
- `SUPERNUTRITION_REQUIRE_UI_TESTS=1 scripts/run-ui-journeys-ios.sh` on a configured macOS/Xcode host -- expected: XCTest, Maestro, and xcresult output pass; on non-macOS, the required mode fails explicitly.
- `python3 scripts/build_coverage_site.py --output-dir _site && python3 scripts/update_readme_metrics.py --site-dir _site --check` -- expected: dashboard generation succeeds and README is current.

## Suggested Review Order

Start with the hosted Android job and its fail-closed report handoff.

  - [Android workflow](../../.github/workflows/android-ui-coverage.yml#L29)
  - [Android journey runner](../../scripts/run-ui-journeys-android.sh#L86)
  - [UI report validator](../../scripts/verify_ui_test_reports.py#L17)

Then inspect the hosted iOS job, simulator-only XCTest evidence, and diagnostics.

  - [iOS workflow](../../.github/workflows/ios-ui-coverage.yml#L31)
  - [iOS journey runner](../../scripts/run-ui-journeys-ios.sh#L70)
  - [XCTest summary validator](../../scripts/verify_ui_test_reports.py#L51)

Finish with the generated README contract and its regression tests.

  - [README metric renderer](../../scripts/update_readme_metrics.py#L83)
  - [README snapshot](../../README.md#L6)
  - [Report-rendering tests](../../scripts/tests/test_update_readme_metrics.py#L21)
  - [UI-report tests](../../scripts/tests/test_verify_ui_test_reports.py#L16)
