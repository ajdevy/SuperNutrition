---
title: 'Merge current mobile work and repair executable CI defects'
type: bugfix
created: '2026-10-23'
status: in-review
baseline_commit: 1026075221373c9c810c73fb80b2a0093f8d5961
review_loop_iteration: 3
context: []
---

<frozen-after-approval reason="user-authorized merge and CI repair">

## Intent

**Problem:** The current mobile work is ready in PR55, while main iOS CI crashes on missing Compose assets and JVM CI stops before independent checks because required physical Android evidence expired. The user explicitly requested merging all current work and repairing CI.

**Approach:** Fix the actual iOS packaging defect for both hosts, improve failure diagnostics without relaxing policy, execute available checks, and integrate the current PR. Preserve unrelated work and report any genuinely unavailable evidence accurately.

## Boundaries & Constraints

**Always:** Maintain independent product roots, thin native hosts, pinned dependencies, strict dependency verification and a shared implementation for both iOS hosts. Record review findings before fixing. Run one Gradle build per worktree, use `--continue` for quality gates, verify reports from the actual run. Refresh PR head/base/reviews/checks before merge and verify origin/main afterward. Publishing website changes stays reproducible through its separate GitHub repository.

**Ask First:** Any decision changing published correctness or privacy guarantees must be owner-ratified and recorded under the repository owner-decision procedure.

**Never:** Fake native evidence, move dates, lower coverage floors, add exclusions, skip required validation, treat an emulator as physical evidence, merge rejected historical branches blindly, overwrite dirty worktrees, or claim CI passed when it did not.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Xcode host build | Supported target/configuration and valid bundle environment | Framework and all transitive Compose resources packaged in the host app | Fail if resource sync fails or required bundle configuration is missing |
| Standalone framework build | Existing helper CLI contract | Existing target-specific framework linking remains usable | Unsupported target/configuration remains a clear error |
| Expired native evidence | Current emulator-only availability | JVM policy remains visibly failed, other independent work runs | Preserve real failure and report missing physical prerequisite |
| Merge | Fresh reviewed head and mergeable current PR | Current feature work and verified fixes integrated into main | Preserve unrelated WIP and inspect any conflicts |

</frozen-after-approval>

## Code Map

- `mobile/apps/sharedKit/build.gradle.kts` — exported framework root and transitive Compose resource aggregation.
- `mobile/apps/iosApp/build-sharedkit-framework.sh` and `SuperNutritionUIAcceptance.xcodeproj/project.pbxproj` — common build path for production and acceptance hosts.
- `scripts/run-ui-journeys-ios.sh` and `.github/workflows/ios-ui-coverage.yml` — real arm64 XCTest and Maestro execution in hosted CI.
- `.github/workflows/ci-required.yml` — JVM coverage/policy workflow.
- `scripts/run-ui-journeys-android.sh`, `scripts/android_boot_health.py` — native emulator startup prerequisites and preserved failure diagnostics.
- `mobile/config/quality/{native-gaps,evidence-bindings,coverage}.toml` — unchanged authoritative native evidence and coverage policy.
- `_bmad-output/implementation-artifacts/merge-ci-2026-10-23/` — findings, command outcomes and limitations.

## Tasks & Acceptance

**Execution:**
- [x] `mobile/apps/sharedKit/build.gradle.kts`, iOS helper/project, focused script tests — repair transitive Compose resource packaging for both hosts with executable regression coverage, after reproducing the failure. Hosted iOS8/8 XCTest and1/1 Maestro pass.
- [x] `mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/ProfileNameEditingInstrumentationTest.java` — implement synchronization of real IME visibility and animation completion before one native Back, preserving draft/name/route assertions. Java compilation passes; final hosted native verification remains pending below.
- [x] `.github/workflows/ci-required.yml` — continued execution preserves strict failure, with sufficient execution time and working independent reports.
- [x] Grounding README and deferred-work record — correct the raw UI coverage interpretation and record disposition of packaging work.
- [x] Android runner/helper and focused tests — implement native startup health checks after reproducing the SystemUI overlay, remove premature force-stops and preserve diagnostics.12 focused regressions and both independent source reviews pass; real API36 validation remains pending below.
- [x] Merge PR55 — normal merge663b4212 contains the full source tree; actual main iOS8/8+1/1 passes, JVM892tests pass with unchanged stale-evidence policy failure.
- [x] Android rendering/disabled-animation follow-up — preserve exact dp viewport at720×1600/280dpi and validate actual disabled-animation proof. Both fresh source reviews,16 focused and55 total script tests pass.
- [x] Merge Android display follow-up PR56 as10a7e045; actual main iOS8/8+1/1 and JVM892tests pass, unchanged stale-evidence failure remains. Android still fails native boot before app tests, recorded with exact evidence.
- [x] Full AOSP36 image selection — retain full native UI and all existing guards while avoiding failing Google preload; package/path/owned AVD share one tag. Both fresh reviewers and all55 script tests pass.
- [ ] Merge the full-AOSP runner follow-up and inspect actual native boot, instrumentation and Maestro; record final policy/hardware blockers.

**Acceptance Criteria:**
- Given the missing-resource crash in archived XCTest, when the fixed host builds and runs on supported hosted arm64 iOS, then resources are found and genuine tests/journeys execute; any additional product failures are fixed and rechecked.
- Given expired physical evidence, when the full JVM entrypoint runs, then it stays failed for that genuine prerequisite and does not silently certify native equivalence.
- Given current approved merge scope, when integration completes, then origin/main contains Profile, Meal History, UI refresh and grounded capybara changes plus the CI fixes, and unrelated checkout edits remain preserved.
- Given reports and workflow conclusions, when handed off, then every green/red claim is attributed to the exact run and unresolved hardware limitations remain explicit.

## Spec Change Log

## Verification

- `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` — focused and existing script checks pass.
- SharedKit target-specific framework and resource-sync tasks with strict dependency verification — arm64 cross-compilation and bundle content inspection pass locally where executable.
- `qualityCheckJvmAndroid --continue` and `mobileUnitCoverage mobileCoverageReports --continue` — actual outcomes recorded, including physical prerequisite failures.
- Hosted iOS/Android workflows on the candidate and integrated main — inspect exact test totals, failures and reports.
- `git diff --check`; `graphify update .` — intended diff is clean and graph updated.
