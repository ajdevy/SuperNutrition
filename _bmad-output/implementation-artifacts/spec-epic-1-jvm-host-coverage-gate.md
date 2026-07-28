---
title: 'Make the JVM host coverage denominator executable'
type: 'chore'
created: '2026-07-28'
status: 'done'
baseline_revision: '1c12dd4'
final_revision: '1c12dd4'
review_loop_iteration: 1
followup_review_recommended: true
context:
  - /Users/aj/workspace_ai/SuperNutrition/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md
  - /Users/aj/workspace_ai/SuperNutrition/mobile/config/quality/coverage.toml
warnings: []
---

<intent-contract>

## Intent

**Problem:** The mobile quality plugin configures Kover's root aggregate over a mixed KMP graph, but the custom JVM variant is not reliably available during Gradle configuration and the total report can include platform-only sources. This leaves the E1.4 host denominator either broken or too broad.

**Approach:** Make `qualityCoverageJvm` consume the explicitly generated JVM reports only from denominator entries classified as `coverage_kind = "jvm"`, aggregate their report-level line and branch counters, and fail closed below the policy threshold. Production entries classified as `native-equivalent` remain in the inventory and require fresh successful native evidence bindings; they are never silently omitted or represented by an empty JVM report.

## Boundaries & Constraints

**Always:** Preserve the 85% line/branch and 100% changed-code policy; use only existing production modules from `coverage.toml`; keep scaffold-pending read-only and non-claiming; use strict dependency verification and configuration-cache-compatible task wiring.

**Block If:** A JVM denominator report is missing, has no report-level counter, or has an empty denominator; or any native-equivalent evidence binding is missing, pending, malformed, or stale. Do not infer, silently exclude, or reclassify coverage.

**Never:** Do not activate the lifecycle, weaken thresholds, add broad/wildcard exclusions, claim physical Apple/device evidence, or change external CI/ruleset state.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| HAPPY_PATH | Active lifecycle; every production module has `reportJvm.xml` | Aggregate line and branch percentages are calculated | Pass only when both are at least 85% |
| MISSING_REPORT | Active lifecycle; one report is absent | Verification cannot produce a claim | Fail with the affected project path |
| EMPTY_REPORT | Report has no positive line/branch denominator | Verification cannot produce a claim | Fail closed with an empty-denominator error |
| NATIVE_PENDING | A denominator is `native-equivalent` but its evidence binding is pending or stale | JVM aggregation cannot make a cross-platform claim | Fail closed before report aggregation |
| SCAFFOLD_PENDING | Lifecycle is `scaffold-pending` | No coverage reports or active threshold are required | `qualityCoverageJvm` is skipped |

</intent-contract>

## Code Map

- `mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/QualityPlugin.kt` -- registers JVM report dependencies and the fail-closed aggregate.
- `mobile/build-logic/src/test/kotlin/com/supernutrition/buildlogic/JvmCoverageVerifierTest.kt` -- covers report-level counter selection and empty-denominator rejection.
- `mobile/build-logic/src/test/kotlin/com/supernutrition/buildlogic/NativeEquivalentEvidenceVerifierTest.kt` -- covers pending, malformed, stale, and successful native evidence attestations.
- `mobile/config/quality/coverage.toml` -- source of truth for production modules and thresholds.
- `mobile/product-lifecycle.toml` -- controls whether active coverage verification is allowed to claim.

## Tasks & Acceptance

**Execution:**
- [x] `QualityPlugin.kt` -- replace unreliable mixed-variant root merge with explicit `reportJvm.xml` aggregation -- keep JVM host coverage separate from platform/device evidence.
- [x] `JvmCoverageVerifierTest.kt` -- test report-level counters and fail-closed empty denominators -- prevent silent false-positive coverage.
- [x] `product-lifecycle.toml` -- retain `scaffold-pending` after active-path audit -- do not claim unproven activation.

**Acceptance Criteria:**
- Given scaffold-pending lifecycle, when the read-only quality matrix runs, then `qualityCoverageJvm` is skipped and policy validation remains successful.
- Given active lifecycle, when every production JVM report is available, then the task aggregates exactly those reports and enforces both 85% thresholds.
- Given active lifecycle, when native-equivalent denominators have fresh successful evidence bindings, then they remain part of the inventory while the JVM task aggregates only the explicit `jvm` denominators.
- Given a missing, malformed, or zero-denominator report, when coverage verification runs, then it fails with a project-specific reason.

## Design Notes

The implementation intentionally uses per-module Kover JVM XML reports rather than a root custom variant. The selected reports are generated by Kover's JVM target and therefore cannot pull in iOS/native or Android-only source sets. The denominator manifest explicitly classifies platform/Compose/hardware units as `native-equivalent` and binds them to the native-gap evidence contract, so excluding them from the JVM arithmetic is an auditable classification rather than an implicit exemption.

## Verification

**Commands:**
- `cd mobile && ./gradlew :build-logic:test verifyMobilePolicy qualityCheckJvmAndroid qualityCheckApple --dependency-verification=strict --configuration-cache --console=plain` -- expected: successful scaffold quality run.
- `cd mobile && ./gradlew qualityCoverageJvm --dependency-verification=strict --configuration-cache --console=plain` with a temporary local audit fixture -- expected: JVM aggregate passes at 97.87% line / 93.09% branch across the five `jvm` denominators; synthetic local attestation is not activation evidence and is reverted after the audit.
- `cd mobile && ADB_MDNS_AUTO_CONNECT=0 ANDROID_SERIAL=192.168.0.225:40531 ./gradlew :apps:androidApp:connectedUiAcceptanceDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.deviceSerial=192.168.0.225:40531 --dependency-verification=strict --configuration-cache --console=plain` -- expected: 10/10 physical Android acceptance tests pass.

## Review Triage Log

| Category | Count | Disposition |
|----------|------:|-------------|
| intent_gap | 0 | The implementation matches the bounded JVM-host denominator intent. |
| bad_spec | 0 | The spec covers scaffold-pending, active, missing, malformed, and empty-report behavior. |
| patch | 6 | Fixed strict inventory resolution, active authorization, configuration-cache-safe task actions, exact report-level counter selection, Long counter totals, and the minimal ProjectBuilder fixture. |
| defer | 4 | Pre-existing Detekt task discovery, qualityFormat mutation behavior, changed-code 100% enforcement, and external report provenance stay outside this bounded slice. |
| reject | 0 | No review finding was rejected as invalid. |

The adversarial and edge-case reviews found no intent or specification defect.
The implementation patches are material, so a follow-up review is recommended
after the first active inventory/coverage evidence is available.

## Auto Run Result

- Result: bounded implementation complete; lifecycle activation intentionally not claimed.
- Changed files: `QualityPlugin.kt`, `JvmCoverageVerifierTest.kt`, `MobilePolicyPluginTest.kt`, `coverage.toml`, and `product-lifecycle.toml`.
- Local verification: strict build-logic/policy/quality suite passed with configuration-cache reuse; the temporary active audit aggregated five JVM denominators at 97.87% line / 93.09% branch; physical Android acceptance passed 10/10.
- Residual risks: canonical Apple ARM64 simulator/Xcode evidence, trusted GitHub ruleset and same-SHA six-job evidence, and real fresh native-equivalent attestations remain activation gates.
- Commit: not created because the checkout contains unrelated user work and this run preserves the existing dirty-worktree boundary.

## Change Log

- 2026-07-28: Added explicit JVM versus native-equivalent denominator
  classification, a fail-closed freshness/attestation verifier for native
  evidence, and focused common/JVM/Compose/recovery tests. A temporary local
  audit passed the five JVM denominators at 97.87% line / 93.09% branch; no
  synthetic evidence was retained in the pending manifests.
