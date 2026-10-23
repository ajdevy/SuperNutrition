---
title: 'Build the iOS host for its requested architecture'
type: 'bugfix'
created: '2026-10-23'
status: 'done'
baseline_commit: '779fc327405b19f6301874edc79d5f5c6536b15e'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="The existing user request authorizes necessary verification fixes; no additional intermediate approval is required">

## Intent

**Problem:** The user requests Android emulator screenshots and iOS verification after installing Xcode. Android now passes150 fresh native tests. iOS verification exposed an internal host build error: both Xcode phases always enable Intel simulator dependencies, including when the requested target is the iPhone arm64 framework. Those dependencies are unsupported by pinned Compose1.11.1, so the production target fails in its framework phase even though its arm64 SharedKit framework links successfully.

**Approach:** Make both Xcode phases share one host-local build helper that selects the existing framework architecture explicitly. Verify the actual arm64 production target with strict dependency verification, retain the already accepted Intel runtime limitation, and keep the runtime report reproducible. This is one host build correction; it does not implement a second UI renderer or change product routing.

## Boundaries & Constraints

**Always:** Preserve the canonical Compose UI, existing framework output paths, SDK selection through process-local DEVELOPER_DIR, the original verification metadata, and unsigned local build semantics. Select iosX64 only when that is the explicitly requested framework target. Share the implementation used by production and acceptance phases. Keep paths and command arguments quoted. Preserve the task-owned Android verification WIP already present in this checkout.

**Ask First:** A new device/account/upload contract or changes to the user’s unrelated checkout or global Xcode selection.

**Never:** Downgrade dependencies, remove or fabricate architecture targets, weaken dependency verification, filter production source files, change lifecycle/coverage/evidence policy, claim simulator execution from compilation, or push remote changes. An unsupported Intel Compose build remains an honest failure.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| iPhone build on Intel | iosArm64, Debug/Release | Existing IosArm64 framework task; Intel simulator disabled | Propagate Gradle failure |
| Apple Silicon simulator | iosSimulatorArm64 | Existing IosSimulatorArm64 task; Intel simulator disabled | Existing host target policy still applies |
| Explicit Intel compatibility attempt | iosX64 | Existing IosX64 task; Intel simulator enabled | Preserve unsupported Compose dependency failure |
| Missing/unknown configuration or target | Invalid Xcode environment | Fail clearly before invoking Gradle | Nonzero exit, no guessed architecture |
| Paths containing spaces | Valid PROJECT_DIR | Resolve mobile root and wrapper as one quoted path | No shell splitting or eval |

</frozen-after-approval>

## Code Map

- `mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj` — two duplicated framework build phases, both currently force iosX64.
- `mobile/apps/iosApp/build-sharedkit-framework.sh` — new shared host-local wrapper; target mapping, validation and strict Gradle invocation.
- `mobile/apps/iosApp/README.md` and `docs/ios-simulator-claude-runbook.md` — accurately distinguish current Intel runtime limitation from device compilation; retain historical command context where useful.
- `mobile/apps/sharedKit/build.gradle.kts` and `mobile/build-logic/.../KmpAppleTargetsPlugin.kt` — existing target policy; read only.
- `_bmad-output/implementation-artifacts/emulator-verification-2026-10-23/` — red output, native results, final build evidence and screenshots.

## Tasks & Acceptance

**Execution:**
- [x] `mobile/apps/iosApp/build-sharedkit-framework.sh` — select the exact registered task suffix, validate target/configuration and propagate exit status with strict verification.
- [x] `mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj` — make both phases run first, unconditionally call the same helper and track it as a build input; let Gradle track transitive inputs.
- [x] `mobile/apps/iosApp/README.md` and `docs/ios-simulator-claude-runbook.md` — record that current Compose has no Intel simulator publication; explain the shared target-selection helper.
- [x] `_bmad-output/implementation-artifacts/emulator-verification-2026-10-23/` — preserve the pre-fix production failure; verify shell/PBX syntax, target selection/error boundaries and the actual unsigned production build; inspect packaged resources and report remaining execution prerequisites.

**Acceptance Criteria:**
- Given the current source and original dependency verification metadata, when the iPhone production target is built on Intel, then its framework phase does not resolve iosX64 and the real Swift host can compile/link.
- Given both Xcode application targets, when their framework build phase runs, then the same helper determines target flags and task names.
- Given unsupported runtime prerequisites, when reporting results, then framework compilation, host packaging and device/simulator execution remain separate evidence categories.
- Given the Android APK/test reports, when the iOS build correction is made, then Android source and verified APK bytes remain unchanged.

## Spec Change Log

## Verification

Red: the direct production-target SDK build exited65 in its original framework script; the exact command and output are in ios-production-target-red.json/log. The separate strict arm64 framework build already passed in9m21s. The original log also shows the inherited phase order let Swift/link run before Gradle; an incremental timestamp probe later failed with a dependency cycle. The complete Android native suite has150 tests,0 failures/errors/skipped.

Run shell syntax and PBX lint checks; exercise supported architecture mappings, invalid input and path quoting against an isolated fake wrapper; rerun the direct production-target SDK command with Xcode26.6, ARCHS=arm64 and CODE_SIGNING_ALLOWED=NO. Compare the production APK SHA-256 with its recorded value. Run git diff --check and graphify update after source changes. Required physical Android evidence remains unavailable; iOS runtime on Intel is explicitly accepted as unavailable by the user.

Verification outcome:13/13 helper boundary probes, shell/PBX syntax and diff checks pass. The original unsigned SDK target failure and incremental dependency-cycle failure are retained; final production compilation/linking passes, its Gradle phase precedes Swift/link, and an unchanged repeat still invokes Gradle. Android APK bytes remain unchanged. Shared Compose resource packaging is a pre-existing open defect, recorded in deferred-work.md; no iOS runtime result is claimed. Local graphify code update completed with the documented parser limitations.

## Suggested Review Order

**Target selection and execution**

- Validate inputs before selecting the registered architecture task.
  [build-sharedkit-framework.sh:4](../../mobile/apps/iosApp/build-sharedkit-framework.sh#L4)

- Delegate strict build execution and preserve Gradle exit status.
  [build-sharedkit-framework.sh:35](../../mobile/apps/iosApp/build-sharedkit-framework.sh#L35)

**Xcode orchestration**

- Build the framework before compiling and linking the host.
  [project.pbxproj:190](../../mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj#L190)

- Run both shared helpers unconditionally; let Gradle track transitive inputs.
  [project.pbxproj:259](../../mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj#L259)

**Verification and limitations**

- Inspect fresh Android evidence and separate iOS compilation from missing runtime/resources.
  [README.md:1](emulator-verification-2026-10-23/README.md#L1)

- Check independent findings, bounded fixes and deferred resource packaging.
  [review-triage.md:1](emulator-verification-2026-10-23/review-triage.md#L1)

- Distinguish current Intel limitations from historical compatibility instructions.
  [README.md:8](../../mobile/apps/iosApp/README.md#L8)
