---
title: GiphySearch Practices Adoption
status: final
date: 2026-07-26
reference_repository: ajdevy/GiphySearch
reference_commit: 16bec3a193a2793db29e1f6f10f9a72e2b25656d
source_of_truth: ARCHITECTURE-SPINE.md
---

# GiphySearch Practices Adoption

## Reference boundary

SuperNutrition uses the pinned [GiphySearch repository snapshot](https://github.com/ajdevy/GiphySearch/tree/16bec3a193a2793db29e1f6f10f9a72e2b25656d) as an engineering-practices reference, not as a product template. The commit pin makes the comparison reproducible if GiphySearch later changes.

The architecture spine remains authoritative. GiphySearch patterns apply inside
`mobile/` and are adapted for KMP, iOS, privacy-sensitive photos, and the
monorepo rule that every product module and product aggregate maintains the 85%
line/branch floor while changed production code remains at 100%.

## Adopted patterns

| GiphySearch practice | SuperNutrition application |
| --- | --- |
| App as composition root | `mobile/apps/androidApp` and `mobile/apps/sharedKit` select concrete adapters and solely bootstrap Android/iOS respectively; the iOS app is a thin Xcode host. |
| Layered module groups | Inside `mobile/`, use `apps`, `core`, `domain`, `data`, `inference`, `platform`, `feature`, `testing`, and `evaluation`, with dependencies pointing inward. |
| Central version catalog | Mobile versions and SDK/JVM floors live in `mobile/gradle/libs.versions.toml`; story code does not introduce literal dependency versions. |
| Central repository policy | Settings enforce `RepositoriesMode.FAIL_ON_PROJECT_REPOS`. |
| Shared Detekt configuration | Keep reviewed mobile rules under `mobile/config/detekt`; include formatting rules and Compose-aware naming where justified. |
| Build performance defaults | Enable parallel execution, build cache, VFS watching, and configuration cache only after AD-16 proves the scaffold is compatible. |
| Feature-local tests | Tests remain beside the behavior each production module owns. |
| Dedicated testing modules | Reuse contract fixtures, fakes, test builders, the Compose v2 base robot DSL/host/synchronization/reporting, Koin test utilities, and platform runners; concrete screen robots remain feature-local and no fixture binding reaches distributable artifacts. |
| Compose stability configuration | Maintain one reviewed file of exact fully qualified stable types and measure compiler reports. |

The source comparison is grounded in GiphySearch's pinned [settings](https://github.com/ajdevy/GiphySearch/blob/16bec3a193a2793db29e1f6f10f9a72e2b25656d/settings.gradle.kts), [root build](https://github.com/ajdevy/GiphySearch/blob/16bec3a193a2793db29e1f6f10f9a72e2b25656d/build.gradle.kts), [Gradle properties](https://github.com/ajdevy/GiphySearch/blob/16bec3a193a2793db29e1f6f10f9a72e2b25656d/gradle.properties), and [Detekt configuration](https://github.com/ajdevy/GiphySearch/blob/16bec3a193a2793db29e1f6f10f9a72e2b25656d/config/detekt/detekt.yml).

## KMP adaptations

- GiphySearch practices apply inside the `mobile/` product boundary. The
  repository itself is a monorepo with independent `web/landing/` and
  `backend/mobile-api/` products; neither imports or depends at runtime on
  mobile or one another. All cross-product API integration uses independently
  owned, immutable, versioned releases under `contracts/`.
- `domain` is stricter than the reference: it is pure common Kotlin and cannot depend on Android, Apple frameworks, Compose, Koin, Ktor, Ktorfit, or SQLDelight. `domain/inference` owns only Engine contracts/evidence; `domain/photo-analysis` owns routing and orchestration through a one-way dependency.
- Local inference is split into `mobile/inference/local-android` and `mobile/inference/local-ios`. This gives the independent developers requested by the product one Gradle ownership boundary per platform.
- Native capture is split into independently tested `mobile/platform/photo-android` and `mobile/platform/photo-ios` adapters instead of accumulating in app hosts.
- `mobile/feature/photo-analysis` owns Compose Multiplatform UI and the Decompose component; `mobile/domain/photo-analysis` owns the reducer, policies, and ports.
- `mobile/apps/sharedKit` owns the Kotlin export and sole idempotent iOS composition/bootstrap API, while the thin iOS host invokes it once; platform modules own native framework integration.
- Included-build convention plugins replace broad root `subprojects` configuration so KMP target policy remains typed, composable, and testable.
- `data/nutrition` is the SQLDelight adapter; its pure contracts and math vocabulary live in `domain/nutrition`.
- Linux validates common, JVM, Android, static, contract, and dependency gates. macOS additionally compiles/links iOS, runs `iosSimulatorArm64Test`, and builds/tests the Xcode host. Platform behavior claims still require real-device evidence.

## Strengthened quality contract

GiphySearch supplies useful lint and testing structure but no checked-in CI workflow or coverage gate at the pinned commit. SuperNutrition therefore adds:

- host-qualified read-only `qualityCheckJvmAndroid` and `qualityCheckApple` tasks, with the CI workflow as the cross-host aggregate;
- `qualityFormat`, the only task permitted to apply formatter or Detekt autocorrections;
- Kover 0.9.8 line and branch verification for measurable JVM/Android code, while Kotlin/Native and iOS evidence is reported separately rather than implied by JVM coverage;
- exactly one denominator for every compiled production source, at least 85% unit-test line and branch coverage per module and per product aggregate across mobile/web/backend, and 100% for changed production lines and branch outcomes, with executable equivalent evidence for Native/hardware boundaries;
- common Compose Multiplatform v2 robot contracts executed through JVM, Android device, and iOS simulator tasks, plus closed UJ/SM-9 and platform-contract catalogs mapped to one fingerprinted Maestro CLI 2.7.0 inventory, separate binary-bound platform oracles, and fresh native-gap evidence;
- GitHub Actions on every branch push and pull request, with no path filters and an always-running same-commit aggregate; merge-group and externally enforced trusted-policy controls become required only after the AD-38 hosting-capability gate passes;
- dependency verification, dependency locking, and project-repository rejection;
- Linux and macOS CI lanes plus story-gated real-device evidence;
- narrow, owned, removal-bound suppressions and baselines;
- exact-FQN-only Compose stability entries backed by compiler evidence;
- versioned module-boundary and source-set quality manifests, so tasks, owners, and evidence cannot disappear through “eligibility”;
- non-waivable cancellation/privacy conformance scenarios and negative TestKit policy fixtures;
- non-distributable, unique-ID UI-acceptance executables with manifest-limited port substitutions, plus scans proving no distributable artifact contains test-support code.

GiphySearch's Detekt 1.23.8 pattern is adopted, but its version is not: the official [Detekt compatibility table](https://detekt.dev/docs/introduction/compatibility/) places 1.23.8 on Kotlin 2.0.21 and Gradle 8.12.1. SuperNutrition therefore seeds Detekt and its ktlint-wrapper ruleset at 2.0.0-alpha.5, the line compiled against Kotlin 2.4 and Gradle 9.5. This is an explicit pre-release tool risk; the first compatible stable 2.x replaces it through an AD-16 tuple change. The current Kover baseline follows the official [Kover Gradle plugin documentation](https://kotlin.github.io/kotlinx-kover/gradle-plugin/).

## Deliberately not adopted

- Giphy, Paging, and Coil dependencies: they solve the reference product, not SuperNutrition's architecture. Ktorfit is intentionally adopted for the remote adapter's typed, generated KMP API surface over Ktor.
- Android-only navigation assumptions: SuperNutrition uses Decompose lifecycle/route boundaries and Compose Multiplatform screens.
- App-shipped API keys or provider credentials: all remote provider secrets remain behind the first-party gateway.
- Detekt autocorrect in verification tasks: CI must detect a dirty result, never rewrite source.
- A broad `TooGenericExceptionCaught` allowance: cancellation, privacy, and transport boundaries require typed handling.
- Wildcard Compose stability entries: they can hide unstable mutable types.
- Checked-in release APKs or equivalent build artifacts.
- One local-inference module with platform source sets: separate platform modules are required so Android and iOS local-inference developers can work and review independently.

## Resulting verification matrix

| Lane | Required evidence |
| --- | --- |
| Developer pre-push | Local `qualityCheck` runs the manifest-required host slice, leaves the worktree clean, and reports the other host slice as outstanding |
| Every push/PR — Linux | `quality-linux`: while mobile is scaffold-pending, unconditional root/policy/lifecycle evidence with no quality claim; once active, also `qualityCheckJvmAndroid`, profile-selected robot `jvmTest`, coverage, every supported mobile contract release, dependency integrity, and policy checks |
| Every push/PR — Android UI | `ui-journeys-android`: scaffold-pending lifecycle evidence until activation; active bootstrap runs only the non-product scaffold robot and launch/DI smoke, while the irreversible photo-analysis profile runs the full common robot and catalog Maestro inventory |
| Every push/PR — macOS | `quality-apple`: scaffold-pending lifecycle evidence until activation; active bootstrap runs the KMP/Xcode/scaffold robot proof, while photo-analysis adds the feature-local `iosSimulatorArm64Test` IDs; both verify compile/link, framework export, native bridge tests, and dependency resolution |
| Every push/PR — iOS UI | `ui-journeys-ios`: scaffold-pending lifecycle evidence until activation; active bootstrap runs only the non-product launch/DI smoke, while photo-analysis runs the same fingerprinted product journey inventory and assertions as Android |
| Every push/PR — web | `quality-web-landing`: fail-closed no-production preselection evidence until the reviewed toolchain activation; afterward read-only build, lint, accessibility/unit tests, coverage, dependency integrity, artifact, and inventory evidence |
| Every push/PR — backend | `quality-backend`: fail-closed no-production preselection evidence until activation; afterward read-only build, lint, unit/contract/security tests, coverage, dependency integrity, artifact, and every supported backend contract release |
| Required aggregate | `ci-required`: `always()` with direct `needs` on exactly all six jobs, exact-success conclusions, and unconditional matching same-SHA root, lifecycle/inventory, contract catalog/version-matrix, and product evidence fingerprints; after AD-38, `trusted-policy-audit` independently validates candidate CI policy from protected code |
| Story-gated device | Camera bridge, local inference, latency, memory, thermal/battery, VoiceOver, TalkBack, and lifecycle behavior on the approved matrix |

The monorepo and product bootstrap stories create and prove these commands and
workflows. Shared mobile screen contracts use the experimental v2
[Compose Multiplatform UI test API](https://kotlinlang.org/docs/multiplatform/compose-test.html);
[Maestro](https://maestro.mobile.dev/) supplies reusable flows and its
[page-object pattern](https://docs.maestro.dev/examples/recipes/implementing-the-page-object-model-pom)
keeps locator differences outside the logical flow. Native suites remain
inventoried evidence for gaps, and triggers follow
[GitHub Actions workflow syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax).
