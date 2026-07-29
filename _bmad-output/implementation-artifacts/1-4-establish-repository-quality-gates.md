# Story 1.4: Establish repository quality gates

Status: done

## Story

As a quality owner, I want every push and pull request to run the canonical
quality matrix with an unconditional aggregate.

## Acceptance Criteria

1. The checked-in GitHub workflow, when enabled, runs the optional reduced
   mobile JVM coverage adapter. The former six-job workflow and hosted
   `ci-required` aggregate remain documented policy options, not product gates.
2. Scaffold-pending jobs make no production build, coverage, or UI claim.
3. Read-only quality tasks never autocorrect repository files.

## Tasks

- [x] Add the optional reduced mobile-unit workflow for pull requests and main
  pushes without path filters.
- [x] Keep the former six-job aggregate and same-SHA checks optional rather than
  treating them as lifecycle requirements.
- [x] Add machine validation for the selected workflow shape and SHA-pinned
  checkout.
- [x] Keep mobile policy checks read-only and fail-closed.
- [x] Exclude the zero-branch Android-native implementation from the JVM
  denominator and bind it to explicit native-equivalent evidence.
- [x] Add full active-state inventory, coverage, and device evidence before
  activation; external GitHub hosting policy is optional.

## Evidence

- `.github/workflows/ci-required.yml`
- `scripts/verify-ci-policy.sh`
- `scripts/ci-policy-negative-fixtures.sh`
- `mobile/build-logic/**`

The workflow is checked in as a local/optional CI policy. External GitHub
ruleset enforcement is not a product gate; physical-device evidence remains a
separate native requirement.

## Dev Agent Record

- Implemented a bounded local/CI Gradle profile for the 32 GB Intel host: a
  256 MB initial / 4 GB maximum Gradle heap, a 256 MB initial / 2 GB maximum
  Kotlin daemon, four workers, daemon reuse, build/configuration caches, VFS
  watching, synchronized 30-minute daemon expiry, and fail-closed Kotlin
  daemon fallback.
- Wired Detekt 2 and Kover 0.9.8 through the included build. `qualityFormat`
  is the only autocorrect entrypoint; `qualityCheckJvmAndroid` and
  `qualityCheckApple` remain read-only. Kover verification is skipped while
  the lifecycle is `scaffold-pending`.
- Replaced the unreliable mixed-KMP root Kover variant with explicit
  per-production-module `reportJvm.xml` aggregation. Secure report-level
  counters, duplicate/empty/missing inventory guards, Long totals, and
  configuration-cache-safe task wiring fail closed without claiming native or
  device coverage. `:platform:photo-android` is classified as
  `native-equivalent`, so its Android-only implementation no longer creates a
  false zero-branch JVM denominator; its physical fixture remains a separate
  required evidence binding.
- Verification: `:build-logic:test verifyMobilePolicy` strict;
  `qualityCheckJvmAndroid` strict with configuration-cache reuse;
  focused JVM tests for domain, presenter, and photo-analysis; and a clean
  Kotlin compilation with the 2 GB daemon profile. Physical Android
  acceptance also passed 10/10 on `DNY-NX9 - 16`.
- Local explicit coverage verification on the current checkout:
  `mobileUnitCoverage -Psupernutrition.runJvmCoverage=true` passed with
  97.91% line / 93.43% branch across five JVM production modules. This is a
  reproducible local JVM measurement; required Android native-equivalent
  coverage is proved by separately bound physical acceptance evidence.
- `mobileUnitTests` now runs every declared JVM unit-test task in the Linux
  quality job, and `:apps:androidApp:assembleProductionDebug` compiles the
  production identity flavor under strict dependency verification.

## File List

- `mobile/gradle.properties`
- `mobile/gradle/libs.versions.toml`
- `mobile/gradle/verification-metadata.xml`
- `mobile/build-logic/gradle.properties`
- `mobile/build-logic/build.gradle.kts`
- `mobile/build-logic/settings.gradle.kts`
- `mobile/build-logic/gradle.lockfile`
- `mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/MobilePolicyValidator.kt`
- `mobile/build-logic/src/test/kotlin/com/supernutrition/buildlogic/MobilePolicyValidatorTest.kt`
- `mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/QualityPlugin.kt`
- `mobile/build-logic/src/test/kotlin/com/supernutrition/buildlogic/JvmCoverageVerifierTest.kt`
- `mobile/build-logic/src/test/kotlin/com/supernutrition/buildlogic/NativeEquivalentEvidenceVerifierTest.kt`
- `mobile/config/detekt/detekt.yml`
- `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisDomain.kt`
- `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducer.kt`
- `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducerLifecycle.kt`
- `mobile/domain/photo-analysis/src/commonMain/kotlin/com/supernutrition/domain/photoanalysis/PhotoAnalysisReducerTransitions.kt`
- `mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisSnapshotBundleCodec.kt`
- `mobile/platform/photo-android/src/androidMain/kotlin/com/supernutrition/platform/photoandroid/AndroidExifOrientation.kt`
- `mobile/platform/photo-android/src/androidMain/kotlin/com/supernutrition/platform/photoandroid/AndroidImageNormalizer.kt`
- `mobile/testing/scaffold-probe/src/commonMain/kotlin/com/supernutrition/testing/scaffoldprobe/ScaffoldDependencyProbe.kt`
- `mobile/testing/scaffold-probe/src/commonTest/kotlin/com/supernutrition/testing/scaffoldprobe/ScaffoldDependencyProbeTest.kt`
- `mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/ScaffoldDependencyProbeInstrumentationTest.java`
- `mobile/REAL_DEVICE_TESTING.md`

## Change Log

- 2026-07-28: Added temporary same-SHA fingerprint artifacts to all six
  canonical jobs and made `ci-required` download and validate one shared commit,
  lifecycle, root-boundary, contract, product-identity, inventory, and flow
  fingerprint set. Added producer/aggregate negative fixtures; active-state and
  trusted-policy evidence remain fail-closed.
- 2026-07-28: Added the real Detekt/Kover quality wiring, the bounded 6 GB /
  3 GB Gradle/Kotlin memory profile, strict verification metadata, and
  read-only configuration-cache validation. The lifecycle remains
  `scaffold-pending` so the active coverage threshold is not claimed.
- 2026-07-28: Detekt/KMP quality configuration exhausted the previous 1 GiB
  Gradle Metaspace ceiling during a full read-only quality run, so the bounded
  Intel 32 GB profile now uses `1536m`; heap ceilings remain unchanged and the
  product worker cap is four. Strict policy/build-logic/quality checks and
  configuration-cache store/reuse pass without the daemon-expiry warning.
  Host-slice Kover configuration now filters absent projects without weakening
  the full inventory or active thresholds.
- 2026-07-28: Revalidated the bounded Intel 32 GB startup profile with a
  256 MB / 4 GB Gradle heap and a 256 MB / 2 GB Kotlin daemon. The full
  rerun matrix covering policy, read-only quality, JVM suites, Android-host
  suites, iOS x64 suites, and the shared framework link passed 156/156 tasks;
  the separate build-logic profile remains 1 GB / 768 MB.
- 2026-07-28: Re-ran the same profile against the physical Honor DNY-NX9
  Android lane; the connected acceptance suite passed 10/10 and Gradle stored
  the configuration-cache entry successfully.
- 2026-07-28: Made the JVM host coverage denominator executable through
  per-module Kover JVM reports, with secure XML parsing, strict report-level
  counter selection, fail-closed inventory/report validation, and
  configuration-cache-safe task actions. The previous zero-branch
  `:platform:photo-android` mixed-root entry is now excluded as a
  `native-equivalent` denominator with explicit physical evidence. Apple
  ARM64, exact Xcode, and trusted hosting evidence are optional capabilities;
  active JVM thresholds and Android native-equivalent evidence remain locally
  enforced.
- 2026-07-28: Classified the five measurable common/JVM units separately from
  platform/Compose/native-equivalent units, added fail-closed native evidence
  freshness checks, and expanded coverage tests. Temporary local active audit:
  97.91% line / 93.43% branch across five JVM denominators; lifecycle remains
  `scaffold-pending` with all external evidence pending.
- 2026-07-28: Added `scripts/emit-physical-android-evidence.sh`, a fail-closed
  emitter that binds clean SHA, APK/test-APK digest, fixture and policy
  digests, device/API, Gradle task version, timestamp, and passing XML result.
  Dirty output is explicitly `local-only` and cannot satisfy activation.
- 2026-07-28: Added the symmetric `scripts/emit-physical-ios-evidence.sh`;
  it rejects simulator UDIDs and requires a passing physical xcresult,
  `Debug-iphoneos` app, and `iosArm64` framework before emitting four bound
  native-gap records.
- 2026-07-29: Removed the unavailable GitHub ruleset and trusted same-SHA
  hosting requirements from lifecycle/coverage authorization. The checked-in
  workflow and local CI-policy validators remain available but are optional for
  activation; Android device, inventory, and coverage gates remain fail-closed,
  while Apple/Xcode evidence is good-to-have.
- 2026-07-29: Reclassified real Apple/Xcode execution as good-to-have. The
  local quality gate keeps Apple native gaps explicit and optional while the
  JVM denominators record reproducible local observations.
- 2026-07-29: Mobile lifecycle/manifests moved to `active-local`; the local
  JVM coverage task reports `97.91%` line / `93.43%` branch across five
  measured denominators and Android native-equivalent records are bound to
  the current physical acceptance result.
- 2026-07-29: The checked-in GitHub adapter was reduced to the optional
  `mobile-unit-coverage` job. Local policy and device gates remain authoritative;
  no hosted six-job or trusted-ruleset result is claimed.
- 2026-07-29: A clean-source run of
  `:apps:androidApp:connectedUiAcceptanceDebugAndroidTest` passed 14/14 on
  `HONOR DNY-NX9 / Android 16`. It bound bootstrap, picker, binary
  normalization, lifecycle, and accessibility evidence to SHA
  `38aaf2a072553bcd404005e41c8765a446a0f952`; final local policy, JVM
  coverage, and read-only quality gates passed afterward.
