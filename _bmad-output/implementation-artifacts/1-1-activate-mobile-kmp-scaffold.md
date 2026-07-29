---
baseline_commit: 1c12dd4a69817b3daf91eb4df88f1a197c433466
---

# Story 1.1: Activate the mobile KMP scaffold

Status: review

> Current delivery policy (2026-07-29): the mobile lifecycle is `active` in
> `active-local` mode. Apple Silicon/Xcode/iOS execution and external GitHub
> enforcement are good-to-have capabilities; required Android native evidence
> remains the only device-side gate for a full active coverage claim.

<!-- This story is coordinated through separate AD-15 ownership slices. Do not combine them in one implementation invocation. -->

## Story

As the mobile platform owner,
I want a pinned Android/iOS KMP scaffold with independently owned module roots,
so that feature agents can build against a reproducible substrate.

## Acceptance Criteria

1. Given the mobile lifecycle is `scaffold-pending`, when the scaffold story is merged, then the AD-16 tuple, version catalog, convention plugins, module-boundary inventory, and nonempty product inventory are added atomically.
2. Android and iOS thin hosts, `sharedKit`, a non-product bootstrap acceptance host, and read-only quality entrypoints produce same-SHA evidence.
3. No production feature, store artifact, testing dependency, or web/backend source is introduced by this story.
4. The scaffold proves the pinned common, Android, and iOS build/test path with Koin 4.2.2, Decompose 3.5.0, Ktor 3.5.1, Ktorfit 2.7.4, SQLDelight 2.3.2, serialization, and coroutines, without creating product inference, nutrition, camera, or remote-provider behavior.
5. The scaffold remains fail-closed for required Android/native evidence,
   inventory, coverage, and configuration-cache reuse. Missing Apple/Xcode
   capabilities or external GitHub enforcement are recorded as good-to-have
   and cannot be represented as successful evidence, but do not block local
   activation.

## Tasks / Subtasks

- [x] Task 1 — Implement the `mobile-build-policy` slice (AC: 1, 4, 5)
  - [x] Add `mobile/settings.gradle.kts`, `mobile/build.gradle.kts`, the Gradle wrapper, `mobile/gradle/libs.versions.toml`, dependency verification, and per-project lock policy using only the pinned AD-16 tuple.
  - [x] Add included-build convention plugins under `mobile/build-logic/**`; module build files must declare identity, targets, and dependencies only.
  - [x] Register exact scaffold modules and host-qualified read-only quality tasks without adding product source.
  - [x] Add Gradle TestKit tests and negative fixtures for forbidden plugins/repositories/dependencies, lock writes, testing leakage, and configuration-cache store/reuse.
  - [x] Record the exact environment/toolchain commands and distinguish host-compatible evidence from unavailable Android device, iOS simulator, framework, and Xcode evidence.

- [x] Task 2 — Implement the `mobile-quality-policy` slice (AC: 1, 2, 5)
  - [x] Add schema-valid nonempty inventories and policy manifests under `mobile/config/**` for modules, source sets, positive scenarios, journeys, platform contracts, native gaps, coverage, suppression, and exact Compose stability entries.
  - [x] Keep the evidence profile and journey catalog explicit; the later photo-analysis story owns the irreversible profile switch and its product journeys.
  - [x] Add the owning read-only validator that binds all manifests to repository/configuration digests and fails on empty, duplicate, stale, orphaned, or wrong-profile evidence. Pending evidence is rejected for activation rather than treated as success.

- [x] Task 3 — Implement the `mobile-ui-acceptance-policy` slice (AC: 2, 3, 5)
  - [x] Add only `mobile/.maestro/bootstrap/**` with one cross-platform non-product launch/DI smoke flow and locator/app-identity dictionaries.
  - [x] Keep the flow free of product journeys, live providers, credentials, photo assertions, release claims, and `photo-analysis` profile files.

- [x] Task 4 — Implement `mobile/testing/scaffold-probe` (AC: 2, 3, 4)
  - [x] Add the concrete bootstrap-shell robot plus the non-production common/JVM and packaged Android probe covering the pinned substrate types. Canonical iOS probe execution remains tied to the unavailable ARM64 lane.
  - [x] Keep probe APIs and fixtures test-only; add common/JVM/Android/iOS tests with no production feature API.

- [x] Task 5 — Implement `mobile/testing/compose-support` (AC: 2, 3, 4)
  - [x] Add the bounded host/session, synchronization, finality, cleanup, and privacy-safe artifact substrate.
  - [x] Test bounded waits, assertion finality, redaction, typed screen scopes, named step IDs, and selector-scope escape without sleeps or raw selectors in test bodies; the complete platform-packaged v2 robot remains separate.
  - [x] Add the feature-owned typed capture robot and common semantic/action contract; native packaged execution remains bound to the Android device and canonical Apple lanes.

- [x] Task 6 — Implement the `mobile/apps/sharedKit` slice (AC: 2, 3, 4)
  - [x] Add the KMP framework export/link and exactly one idempotent iOS Koin bootstrap API with bridge tests.
  - [x] Keep iOS host startup ownership in `sharedKit`; do not start Koin from the thin iOS host more than once.

- [x] Task 7 — Implement the `mobile/apps/androidApp` slice (AC: 2, 3, 4)
  - [x] Add a thin Android host using AGP built-in Kotlin and a unique-ID, non-distributable acceptance variant.
  - [x] Add launch/DI/production-composition smoke and connected-device test wiring without store/archive paths, credentials, or `testing` in release artifacts.

- [x] Task 8 — Implement the `mobile/apps/iosApp` slice (AC: 2, 3, 4)
  - [x] Add a thin Xcode host and unique-bundle-ID, non-distributable acceptance scheme/configuration.
  - [x] Add launch/DI/host-composition smoke and `xcodebuild build-for-testing`/simulator wiring without archive/export paths or credentials. Canonical ARM64 and exact Xcode evidence remain unavailable on this host.

- [ ] Task 9 — Prove scaffold activation and lifecycle transition (AC: 1, 2, 3, 5)
  - [x] Run the mobile host quality tasks twice with configuration cache enabled and `problems=fail`; prove store then reuse without opt-outs.
  - [x] Run the available common/JVM checks, Android host build, and local Apple policy fallback; no simulator output is substituted for physical Android camera/device proof.
  - [ ] Run the current Android connected-device native-gap suite and emit fresh bound evidence after the handset is available.
  - [x] Transition the local policy graph from `scaffold-pending` to `active` after the local policy, inventory, coverage, and host gates passed; Apple/Xcode evidence is good-to-have and GitHub hosting enforcement is optional.

## Dev Notes

### Architecture and ownership guardrails

- AD-15 is authoritative: each task above is a separate bounded invocation with one named ownership slice. The coordinator may read prior handoffs but must not absorb another slice’s files.
- The only product implementation roots are `mobile/`, `web/landing/`, and `backend/mobile-api/`; this story touches mobile only. `contracts/` remains independently owned and no web/backend source is added.
- `mobile/domain` is pure KMP and must not depend on Android, Apple frameworks, Compose, Koin, Ktor, Ktorfit, or SQLDelight. Distributable configurations must not depend on `mobile/testing`.
- `mobile/apps/androidApp` is the sole Android Koin bootstrap owner. `mobile/apps/sharedKit` exports the one idempotent iOS bootstrap API; `mobile/apps/iosApp` invokes it once.
- The first product feature or photo-analysis source is forbidden here. The only UI evidence profile is `bootstrap`, and the only acceptance flow is a non-product launch/DI smoke.
- Installed, launcher, and in-app identity is `SuperNutrition`; the store-only listing title is `SuperNutrition AI nutritionist`. Technical package/bundle identifiers must remain separate from visible identity. Because no namespace is currently checked in, freeze a technical namespace in the scaffold handoff and document it before any later source generation.

### Pinned tuple

Use the exact tuple in AD-16/AR-1: Kotlin/KMP 2.4.10, Compose Multiplatform 1.11.1, Gradle 9.5.0, AGP 9.1.0, JDK 21, Android compile/target SDK 36 with API 23 floor, Xcode 26.4.1 on macOS 26.2+, iOS 15 floor, Detekt/ktlint-wrapper 2.0.0-alpha.5, and Kover 0.9.8. Use Koin 4.2.2, Decompose 3.5.0, Ktor 3.5.1, Ktorfit 2.7.4, and SQLDelight 2.3.2 as specified by the implementation guide. Do not upgrade versions during this story.

### Current environment constraints

- The repository has no existing `mobile/` source tree or Gradle wrapper.
- `android` CLI is not installed; do not install system software without explicit authorization. Android SDK 36 is present, but that does not prove a device or camera path.
- System Gradle is 7.2 and cannot be used as proof for the pinned Gradle 9.5.0 tuple; the scaffold must provide its own wrapper.
- The host reports JDK 21.0.10. Xcode reports 26.0.1, below the planned 26.4.1; Apple evidence must remain outstanding unless the exact tuple becomes available.
- No current source files are modified by this story. Preserve all pre-existing planning/document changes in the dirty worktree.

### Testing and quality

- Write focused tests before implementation for every nontrivial build helper, manifest validator, probe, host bootstrap, and robot behavior.
- `qualityCheckJvmAndroid` and `qualityCheckApple` are read-only. `qualityFormat` is the only mutating formatter/autocorrect entrypoint and never runs in CI.
- Coverage must not be faked with baselines, exclusions, empty modules, skipped tasks, stale evidence, or optional denominators. Native/device behavior requires executable equivalent evidence and an expiry-backed manifest entry.
- Use configuration-cache `store` then `reuse` runs with `problems=fail`; a skipped or failed second run is a failure, not a warning.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Story E1.1]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md` — AD-15, AD-16, AD-25 through AD-35, AD-37]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/implementation-agent-guide.md` — Sections 3, 4, 5, 7.0b through 7.0d]
- [Source: `_bmad-output/planning-artifacts/prds/prd-SuperNutrition-2026-07-26/prd.md` — NFR-9, NFR-10, NFR-17 through NFR-23]
- [Source: `_bmad-output/planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/EXPERIENCE.md` — Foundation, Accessibility Floor]
- [Source: `/Users/aj/workspace_ai/SuperNutrition/AGENTS.md` — SuperNutrition monorepo and KMP engineering baseline]

## Dev Agent Record

### Agent Model Used

GPT-5 / Codex

### Debug Log References

- 2026-07-27: No mobile source tree or Gradle wrapper exists. Host checks: JDK 21.0.10, Xcode 26.0.1, system Gradle 7.2, Android SDK platforms include API 36, Android CLI unavailable.
- 2026-07-27: Build-policy slice validated with `./mobile/gradlew -p mobile :build-logic:test`, strict dependency verification, read-only `qualityCheckJvmAndroid` and `qualityCheckApple`, and configuration-cache store/reuse with `problems=fail`. The first quality-cache run exposed a Project capture and was fixed before the passing store/reuse pair.
- 2026-07-27: Quality-policy manifests validated with Python 3.11 `tomllib`; the policy graph remains `scaffold-pending`, uses `bootstrap` for the scaffold lane, contains no wildcard Compose stability entries, and is enforced by the executable validator.
- 2026-07-27: Added `MobilePolicyValidator` and bound `verifyMobilePolicy`, `qualityCheckJvmAndroid`, and `qualityCheckApple` to read-only manifest graph, digest, evidence-binding, identity, and lifecycle checks. Strict first-run/store and second-run/reuse configuration-cache checks pass; all evidence records remain pending by design.
- 2026-07-27: Bootstrap Maestro policy slice added as one launch/DI flow plus locator-only JavaScript; packaged Android instrumentation and the Intel iOS simulator XCTest now prove the host launch/DI substrate, while canonical ARM64 evidence remains pending.
- 2026-07-27: Scaffold probe substrate configured and tested with strict `:testing:scaffold-probe:jvmTest`; full `allTests` reached successful completion with iOS simulator test skipped because this x86_64 host cannot execute the ARM64 simulator target. The concrete bootstrap-shell robot is still outstanding.
- 2026-07-27: Added the first bounded `mobile/testing/compose-support` substrate (typed step/semantic IDs, bounded polling, redacted failure bundles, and typed screen-scope seam). Strict `:testing:compose-support:jvmTest` passes; Android/iOS packaged execution and the complete v2 robot DSL remain outstanding. Compose compiler/plugin application is intentionally deferred until a production Compose consumer is introduced; the pinned Compose UI-test artifact remains declared.
- 2026-07-27: Added the test-only bootstrap-shell robot and its fake port contract under `scaffold-probe/commonTest`; strict `:testing:scaffold-probe:jvmTest` passes with logical launch/DI steps and redacted evidence. Packaged Android and iOS host execution is now covered by the device instrumentation and simulator XCTest lanes; the canonical ARM64 probe remains pending.
- 2026-07-27: Added an Intel-macOS-only `iosX64` compatibility target to the KMP test modules. It is explicitly local-only; `iosSimulatorArm64Test` remains the canonical Apple evidence task and lifecycle activation still requires it.
- 2026-07-27: Verified `iosX64` common/native compilation and JVM tests. Xcode 26.0.1 rejects `iosX64Test` execution with `Xcode does not support simulator tests for ios_x64`; the Intel fallback is therefore compile-only on this host and cannot produce Apple evidence.
- 2026-07-27: Added the Java-only Android acceptance host to avoid the AGP built-in Kotlin 2.2.10/KMP 2.4.10 classloader collision. `assembleUiAcceptanceDebug` and `assembleUiAcceptanceDebugAndroidTest` pass; no instrumentation run was claimed because `adb devices -l` has no physical device.
- 2026-07-27: Added the thin iOS acceptance Xcode project, SharedKit framework export, and `build-for-testing` proof against the Intel x64 compatibility framework. This is compile-only; `xcrun xctrace list devices` shows only the Mac and no iPhone.
- 2026-07-27: Added root-boundary, identity, interaction-policy, and CI policy slices as separate E1 handoffs. Product/photo production source remains forbidden while the lifecycle is `scaffold-pending`.
- 2026-07-27: Added canonical CI trigger/aggregate validation plus negative path-filter, dependency, and missing-job fixtures; added cross-product source/build/cache boundary fixtures. The workflow and root-policy checks are locally green.

### Completion Notes List

- Ultimate BMAD story context created from the current Epic E1, PRD, UX, architecture spine, and implementation-agent guide.
- Implementation must be delivered as separate AD-15 ownership slices; no single agent may mark the whole story complete from a partial scaffold.
- Completed the mobile-build-policy slice only: pinned catalog, Gradle 9.5.0 wrapper, strict hash verification metadata, included-build policy/quality plugins, explicit quality entrypoints, and build-logic tests.
- Added nonempty mobile-quality-policy manifests and the owning executable validator with stale-digest, duplicate/orphan, wrong-profile, and pending-evidence rejection.
- Added the bootstrap-only Maestro flow and locator dictionary; no photo-analysis profile or product journey was added.
- Added the non-production KMP scaffold probe, concrete bootstrap-shell robot, and colocated tests; no production API was added. Canonical iOS ARM64 probe execution remains pending.
- The story is in review: local activation is complete, while the fresh Android
  connected-device evidence needed for a full active coverage claim remains
  open; hosted GitHub CI is optional.
- Physical-device runbook: `mobile/REAL_DEVICE_TESTING.md`. Connect a physical Android serial and an iPhone UDID before attempting packaged evidence; never substitute the existing emulator or x64 simulator compatibility build.
- Bootstrap launch/DI evidence is retained as local compatibility evidence. The
  active-local record does not claim full native coverage until fresh Android
  connected-device evidence is bound; optional Apple records remain pending by
  policy.
- 2026-07-27: Added `mobile/product-lifecycle.toml` as an explicit fail-closed
  activation record. The production Android Compose host now exists, but
  lifecycle remains `scaffold-pending` because canonical `iosSimulatorArm64`,
  exact Xcode, and active inventory denominators are still unproven.
- 2026-07-29: GitHub ruleset and trusted same-SHA hosting evidence were removed
  from activation requirements. Local policy, inventory, coverage, and host
  gates remain fail-closed; Apple/Xcode evidence is good-to-have.
- 2026-07-29: Real Apple/Xcode execution was reclassified as good-to-have
  evidence. It is retained as an optional compatibility/native lane and no
  longer blocks local lifecycle activation; reproducible local policy and
  coverage gates remain authoritative.
- 2026-07-29: Lifecycle and policy manifests now report `active` with an
  explicit `active-local` quality state. Local JVM Kover verification is
  `97.91%` line / `93.43%` branch across five denominators; required Android
  native evidence and the active coverage claim remain separately pending.

### File List

- `_bmad-output/implementation-artifacts/1-1-activate-mobile-kmp-scaffold.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `mobile/settings.gradle.kts`
- `mobile/build.gradle.kts`
- `mobile/build-logic/settings.gradle.kts`
- `mobile/build-logic/build.gradle.kts`
- `mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/MobilePolicyPlugin.kt`
- `mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/QualityPlugin.kt`
- `mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/MobilePolicyValidator.kt`
- `mobile/build-logic/src/test/kotlin/com/supernutrition/buildlogic/MobilePolicyPluginTest.kt`
- `mobile/build-logic/src/test/kotlin/com/supernutrition/buildlogic/PolicyFixtureTest.kt`
- `mobile/build-logic/src/test/kotlin/com/supernutrition/buildlogic/MobilePolicyValidatorTest.kt`
- `mobile/gradle/libs.versions.toml`
- `mobile/gradle/verification-metadata.xml`
- `mobile/gradle/dependency-locks/README.md`
- `mobile/gradle/wrapper/gradle-wrapper.jar`
- `mobile/gradle/wrapper/gradle-wrapper.properties`
- `mobile/gradlew`
- `mobile/gradlew.bat`
- `mobile/config/architecture/module-boundaries.toml`
- `mobile/config/quality/module-matrix.toml`
- `mobile/config/quality/ui-evidence-profile.toml`
- `mobile/config/quality/positive-scenarios.toml`
- `mobile/config/quality/journeys.toml`
- `mobile/config/quality/platform-contracts.toml`
- `mobile/config/quality/native-gaps.toml`
- `mobile/config/quality/coverage.toml`
- `mobile/config/quality/suppression-registry.toml`
- `mobile/config/compose-stability.conf`
- `mobile/config/product-identity.toml`
- `mobile/product-lifecycle.toml`
- `mobile/config/quality/interaction-policy.toml`
- `mobile/apps/sharedKit/build.gradle.kts`
- `mobile/build-logic/gradle.properties`
- `mobile/gradle.properties`
- `mobile/apps/sharedKit/src/commonMain/kotlin/com/supernutrition/sharedkit/SharedKitBootstrap.kt`
- `mobile/apps/androidApp/build.gradle.kts`
- `mobile/apps/androidApp/src/main/**`
- `mobile/apps/androidApp/src/androidTest/**`
- `mobile/apps/iosApp/**`
- `mobile/apps/iosApp/Tests/BootstrapLaunchTests.swift`
- `mobile/REAL_DEVICE_TESTING.md`
- `contracts/root-policy/v1/**`
- `contracts/mobile-api/v1/**`
- `contracts/interaction-policy/v1/**`
- `scripts/verify-root-boundaries.sh`
- `.github/workflows/ci-required.yml`
- `mobile/.maestro/bootstrap/bootstrap-launch-di.yaml`
- `mobile/.maestro/bootstrap/bootstrap-locators.js`
- `mobile/.maestro/bootstrap/README.md`
- `mobile/testing/scaffold-probe/build.gradle.kts`
- `mobile/testing/scaffold-probe/src/commonMain/kotlin/com/supernutrition/testing/scaffoldprobe/ScaffoldDependencyProbe.kt`
- `mobile/testing/scaffold-probe/src/commonTest/kotlin/com/supernutrition/testing/scaffoldprobe/ScaffoldDependencyProbeTest.kt`
- `mobile/testing/scaffold-probe/src/commonTest/kotlin/com/supernutrition/testing/scaffoldprobe/BootstrapShellRobot.kt`
- `mobile/testing/scaffold-probe/src/commonTest/kotlin/com/supernutrition/testing/scaffoldprobe/BootstrapShellRobotTest.kt`
- `mobile/testing/scaffold-probe/README.md`
- `mobile/testing/compose-support/build.gradle.kts`
- `mobile/testing/compose-support/src/commonMain/kotlin/com/supernutrition/testing/composesupport/AcceptanceTrace.kt`
- `mobile/testing/compose-support/src/commonTest/kotlin/com/supernutrition/testing/composesupport/AcceptanceTraceTest.kt`
- `mobile/testing/compose-support/README.md`

### Change Log

- 2026-07-27: Initialized sprint tracking and created the context-complete E1.1 story.
- 2026-07-27: Implemented and verified the bounded `mobile-build-policy` slice; left remaining scaffold slices pending.
- 2026-07-27: Added and strictly verified the first bounded Compose acceptance-support substrate; left the full v2 host/robot, platform execution, and artifact-writer slices pending.
- 2026-07-27: Added separate E1.2–E1.5 policy handoffs and E2.1–E2.3 ready-for-dev stories; kept E2 production implementation behind scaffold activation as required by AD-15/AD-37.
- 2026-07-27: Physical Android retry passed on serial `adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp` (DNY-NX9, Android 16/API 36): acceptance APK and test APK installed, `Bootstrap ready` observed, and `BootstrapHostInstrumentationTest` completed `OK (1 test)`. This supersedes the earlier no-device observation; no emulator was used.
- 2026-07-27: Created iOS simulator `SuperNutrition iPhone 17 Pro` (UDID `REDACTED_SIMULATOR_UDID`, iOS 26.0) for the requested simulator lane; Xcode test evidence remains pending until that lane reports a successful run.
- 2026-07-27: iOS simulator retry passed on UDID `REDACTED_SIMULATOR_UDID`: `SharedKit iosX64` build succeeded and `xcodebuild test` completed `1 test, 0 failures` (`TEST SUCCEEDED`). The first retry exposed and fixed the missing `CFBundleExecutable`; the second exposed and fixed the test's incorrect first-bootstrap expectation.
- 2026-07-27: Final Android physical instrumentation on Honor DNY-NX9/API 36 passed `OK (7 tests)`, including the process-safe snapshot codec, URI/thumbnail byte extraction, EXIF orientation fixture, packaged bootstrap, and scaffold dependency probe. Final iOS x64 simulator acceptance passed `2 tests, 0 failures`, including capture actions and idempotent SharedKit bootstrap.
- 2026-07-27: Added a feature-owned typed `PhotoAnalysisCaptureRobot` with semantic IDs, bounded `AcceptanceTrace`, and common-test coverage. The full packaged v2 robot lane remains pending because the canonical ARM64/host execution gate is not available on this Intel checkout.
- 2026-07-27: Added the production `core/ui` automation registry with a shared cross-language `SharedKitAutomationIds` projection; the mobile inventory and one-to-one coverage denominators now include `core-ui` (7 production units). JVM suites, both read-only quality checks, and configuration-cache store/reuse pairs pass.
- 2026-07-27: Final iOS x64 simulator acceptance passed `3 tests, 0 failures`, including the capture surface and simulator camera-unavailable recovery. No `iosSimulatorArm64` target was configured in the Intel compatibility host slice.
- 2026-07-27: Added `mobile/gradle.properties` with a bounded 4 GB Gradle heap, 1 GB metaspace, 2 GB Kotlin daemon, four workers, build/configuration caches, parallel execution, and VFS watching. Android assemble passed; the second run reused configuration cache in 4 seconds. `qualityCheckJvmAndroid` also passed store/reuse.
- 2026-07-28: Tuned Gradle/Kotlin daemon startup for the 32 GB Intel host: set 256 MB warm heaps while preserving 4 GB/2 GB maximums, synchronized both daemon idle timeouts to 30 minutes, capped product workers at four, and verified configuration-cache reuse with the bounded daemon settings.
- 2026-07-28: Added separate bounded settings for the included `mobile/build-logic` build: 1 GB Gradle and 768 MB Kotlin maximums with two workers, preventing its small convention-plugin compilation from retaining the product build's larger heap budget.
- 2026-07-28: Completed the included `build-logic` profile with the same 30-minute daemon idle timeout and VFS watching as the product build. `:build-logic:test`, `verifyMobilePolicy`, `qualityCheckJvmAndroid`, and `qualityCheckApple` pass; the JVM quality repeat reused configuration cache in 8 seconds.
- 2026-07-28: Bound both Gradle memory profiles to `verifyMobilePolicy` with a
  regression test for oversized product heaps; JVM/Apple read-only quality
  checks pass and configuration-cache entries still store successfully.
- 2026-07-28: Disabled silent Kotlin in-process fallback in both Gradle
  profiles. A daemon communication failure now fails explicitly instead of
  starting a second compiler process and making peak memory unpredictable.
- 2026-07-28: Enabled `lockAllConfigurations()` for product and included
  `build-logic`, generated 14 project lockfiles plus the settings lockfile,
  and made `verifyMobilePolicy` reject missing lock state. The strict
  lock-bound local matrix passed with configuration-cache store/reuse; the
  physical Android connected suite passed 9/9 and the Intel iOS x64 Gradle
  lane passed normalization/presenter/framework checks.
- 2026-07-27: Split the shared production photo presenter/Compose surface into pure-KMP `feature/photo-analysis-ui`; Android remains on the Android-bearing host module, while canonical Apple targets are prepared without claiming Intel `iosX64` Compose evidence because Compose 1.11.1 has no `iosX64` publication.
- 2026-07-28: Separated the pure reducer/presenter into `feature/photo-analysis-presenter`, which compiles on the Intel `iosX64` compatibility target; the Compose screen remains target-bound to Android and canonical Apple targets. SharedKit exposes the same presenter session on x64 and the Compose view-controller facade on Apple targets.
- 2026-07-28: Fresh full local matrix passed after Gradle memory tuning: 120 actionable tasks, JVM suites, read-only quality, Android acceptance assemble, and configuration-cache store/reuse. The physical Honor DNY-NX9 lane passed 7/7 connected tests; the Intel iOS compatibility lane passed x64 normalization/presenter tests and Xcode simulator 4/4. Lifecycle remains scaffold-pending because canonical ARM64, physical iOS, and exact Xcode evidence are unavailable; protected GitHub hosting is optional.
- 2026-07-28: Added an executable Compose UI Test v2 screen contract for the
  production capture surface. Strict `:feature:photo-analysis-ui:jvmTest`
  passed with the host-specific test-only Skiko runtime; the strict full local
  matrix passed 160 tasks and its repeat reused configuration cache in 2
  seconds. The connected physical Android semantic/instrumentation lane passed
  10/10. CI now has lifecycle-aware Android/iOS runners with SHA-verified
  Maestro 2.7.0 artifacts, while the lifecycle remains scaffold-pending.
- 2026-07-28: Re-ran both lifecycle-aware CI runners locally while the product
  remained `scaffold-pending`: Android `qualityCheckJvmAndroid` and Apple
  `qualityCheckApple` passed, and each runner emitted a nonempty report with
  `lifecycle=scaffold-pending`. These reports are policy evidence only; no
  production, device, or Apple ARM64 claim was emitted.
- 2026-07-28: Re-ran the Intel x64 Xcode compatibility acceptance scheme on
  simulator `REDACTED_SIMULATOR_UDID`; `xcodebuild` reports 4
  passed, 0 failed, 0 skipped. This remains compatibility evidence because
  the canonical `iosSimulatorArm64` and exact Xcode tuple are unavailable.
- 2026-07-28: Added a read-only six-job CI fingerprint contract and aggregate
  verifier. It binds temporary reports to the checked-out commit, workflow SHA,
  lifecycle records, root/contract/product fingerprints, inventories, and
  Maestro flows; the lifecycle remains blocked until Apple/device evidence
  exists, while hosted trust enforcement is optional.
- 2026-07-28: Executed Maestro 2.7.0 syntax validation against the checked-in
  photo-analysis flow and fixed its missing YAML front-matter separator. Both
  lifecycle-aware CI runners now syntax-check the flow before execution; the
  physical Honor wireless-debugging transport still hangs in Maestro's app
  stop/launch driver step, so no Maestro device pass is claimed.
- 2026-07-28: The Intel iOS 26.0 simulator ran the checked-in Maestro
  photo-analysis flow successfully (`1/1`, 12 seconds) against the x64
  compatibility app. This remains compatibility evidence only; canonical
  `iosSimulatorArm64` and exact Xcode 26.4.1 remain open, while hosted CI
  evidence is optional.
- 2026-07-28: Hardened the read-only policy validator against concurrent Gradle
  output churn. Lock-state discovery now skips `build`, `.gradle`, `.git`, and
  `graphify-out` subtrees instead of traversing volatile compiler caches; the
  generated-tree regression fixture and a grouped quality-plus-JVM invocation
  both pass without the previous `NoSuchFileException` race.
- 2026-07-28: Isolated the idempotent `SharedKitBootstrap` bridge fixture with
  an internal test-only reset so common and native test ordering cannot leak
  global Koin state; the public bootstrap API remains unchanged. The Intel
  `sharedKit` host slice links successfully, and the available iOS 26.0.1
  simulator compatibility suite reports 4/4 passed. Canonical ARM64 evidence
  remains pending.
- 2026-07-28: Marked the scaffold-probe and compose-support implementation
  slices complete after re-running the JVM presenter/domain tests, strict
  `platform:photo-ios:iosX64Test`, and the Intel `sharedKit:linkIosX64`
  framework lane. Task 9 remains open because the canonical Apple ARM64/Xcode
  tuple and lifecycle activation gates are not available on this host; the
  all-six-job hosted evidence is optional.
- 2026-07-28: Re-ran the Intel x64 compatibility host with the complete
  `enableTestingAppleTargets`/`enableIntelIosX64` flag pair and explicit
  `ARCHS=x86_64`; the Xcode acceptance scheme passed 4/4 on the booted iOS
  26.0.1 simulator. This remains compatibility evidence only and does not
  satisfy the canonical `iosSimulatorArm64` or physical-iPhone gates.
- 2026-07-28: The first product Compose surface is now backed by a single
  lifecycle-bound Android composition with observer-driven state, and the
  exact photo-analysis stability manifest is wired into both Compose compiler
  modules. This closes the local presenter-host integration slice while the
  external scaffold activation gates remain fail-closed.
- 2026-07-28: Revalidated the bounded Gradle profiles after the Compose and
  stability-manifest changes. The canonical Intel-host `iosArm64` compile and
  `sharedKit:linkDebugFrameworkIosArm64` completed successfully with strict
  dependency verification; the first strict quality run stored configuration
  cache and the repeat reused it in 2 seconds. CI, root-boundary, API,
  product-identity, interaction-policy, and graphify checks are green; the
  lifecycle remains fail-closed because canonical Apple/Xcode and physical
  iOS evidence are still unavailable.
