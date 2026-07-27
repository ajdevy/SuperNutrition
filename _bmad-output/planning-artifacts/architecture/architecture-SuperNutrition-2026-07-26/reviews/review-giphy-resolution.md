# GiphySearch and Quality Update — Review Resolution

Date: 2026-07-26

## Outcome

All clear high-severity findings from the GiphySearch fidelity, current build-tool, and adversarial quality-seam reviews were incorporated before finalization. The deterministic spine linter passes after the update.

## Applied dispositions

| Review finding | Disposition |
| --- | --- |
| Routing was split between `domain/inference` and `domain/photo-analysis` | Accepted. `domain/inference` now owns only Engine contract/evidence artifacts; `domain/photo-analysis` owns routing, consent/fallback, reducer, and attempt coordination through one-way dependency. |
| App hosts could absorb native bridges and iOS bootstrap was ambiguous | Accepted. Added `platform/photo-android` and `platform/photo-ios`; made app hosts thin; `androidApp` and `sharedKit` are the sole Android/iOS Koin bootstrap owners respectively. |
| Canonical and adapter fixture ownership conflicted | Accepted. Immutable released canonical fixtures belong to `domain/inference`; `testing/inference` owns suite runners/fakes/builders; adapter-specific fixtures stay with adapters. |
| Allowed dependency graph was not derivable | Accepted. AD-30 defines the baseline graph and requires a machine-readable module/owner/package/edge manifest, generated checks, and CODEOWNERS. |
| Detekt 1.23.8 mismatched Kotlin 2.4/Gradle 9 | Accepted. Seed changed to Detekt and ktlint wrapper 2.0.0-alpha.5, with the prerelease risk explicit and a tracked stable-2.x replacement. |
| AGP 9 plugin topology was unspecified | Accepted. AD-26 fixes built-in Kotlin for Android apps and the Android-KMP-library plugin combination for Android-bearing KMP modules. |
| Cross-host and configuration-cache proof was ambiguous | Accepted. Added separate Linux/Apple tasks and statuses, fail-closed branch protection, and store-then-reuse configuration-cache evidence. |
| “Eligible” tasks, empty modules, zero tests, and coverage exclusions could bypass quality | Accepted. AD-29 adds a source-set quality matrix; AD-30 adds placement/ownership enforcement; AD-32 adds negative TestKit and coverage-inventory checks. |
| Coverage did not prove privacy/cancellation semantics | Accepted. AD-31 adds a non-waivable fault and behavior conformance suite. |
| Test support could ship in internal variants | Accepted. AD-28 now scans every installable/deployable Android, iOS, and gateway artifact transitively. |
| Gradle dependency policy omitted native/service/model/catalog inputs | Accepted. AD-26 now binds strict owned verification/lock/provenance policy across those ecosystems while keeping `libs.versions.toml` scoped to Gradle versions. |

## Deliberate trade-off

Detekt 2.0.0-alpha.5 is pre-release. Lowering the entire current Kotlin/KMP/Gradle/AGP tuple solely to retain Detekt 1.23.8 would create a larger platform cost. The scaffold must prove the selected Detekt line, keep lint isolated from product runtime, and replace it with the first compatible stable 2.x through the atomic tuple process.
