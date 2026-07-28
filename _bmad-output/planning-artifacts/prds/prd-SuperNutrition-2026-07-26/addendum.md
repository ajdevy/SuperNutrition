---
title: SuperNutrition PRD Addendum
status: final
created: 2026-07-26
updated: 2026-07-26
---

# PRD Addendum

## Architecture Inputs

- The repository is a monorepo with root product boundaries at `mobile/`,
  `web/landing/`, and `backend/mobile-api/`. Shared cross-product API/schema
  artifacts are immutable releases independently owned under `contracts/`;
  neither producer nor consumer may authoritatively generate or publish them.
  Product source, build, package-manager, generated-code, runtime, cache, and
  deployment dependencies never cross those boundaries. The public landing
  remains functional with `backend/mobile-api/` absent and has no runtime
  dependency on it.
- Kotlin Multiplatform with Android and iOS targets replaces the historical
  React Native/Expo implementation direction.
- Compose Multiplatform is the shared UI default. Platform camera/import and
  Local Engine implementations remain platform adapters.
- Use a dedicated shared Engine API, separate local and remote Gradle modules,
  and a separate orchestration module. The Engine API is an intentional
  dependency-inversion boundary even if a generic implementation agent prefers
  concrete repositories.
- SQLDelight is the planned shared database mechanism for versioned nutrition
  data and future local correction state.
- Ktor 3.5.1 is the planned common HTTP transport/client-engine layer and Ktorfit
  2.7.4 is the initial compatibility pin for typed, generated KMP endpoint APIs.
  Ktorfit declarations are isolated to the remote adapter and know only the
  first-party contract; platform engines are provided by the application
  composition root. Any Ktorfit/KSP upgrade is a dedicated compatibility change
  with common, Android, and iOS compile proof.
- Koin is the selected DI framework for implementation consistency.
- Decompose may own component lifecycle and true screen navigation. Capture,
  analyzing, consent, confirmation, result, and retry are one Photo Analysis
  component state machine rather than navigation destinations.
- Navigation callbacks navigate. One-shot effects are UI-only and must not
  duplicate navigation callbacks.
- Shared Compose semantics, state behavior, and bounded layout relationships are
  tested from `commonTest` through a versioned project robot DSL over
  `androidx.compose.ui.test.v2.runComposeUiTest`. The same authored suite runs
  as `jvmTest`, Android device test, and `iosSimulatorArm64Test`; pixel identity
  across platforms is not implied.
- Maestro CLI 2.7.0 drives the same platform-agnostic positive-journey flows
  against declared non-distributable UI-acceptance variants of the packaged
  Android and iOS apps. Those variants retain production UI, state, navigation,
  and composition code and replace only manifest-listed Engine, gateway,
  catalog, configuration, and photo-input ports. Native instrumentation/XCTest
  remains for the exhaustive, versioned platform gaps that common Compose tests
  or Maestro cannot establish.
- GitHub Actions is the repository CI mechanism. Workflows trigger on every
  branch push and pull request without path filters; merge-group runs become
  required only after the private-repository hosting capability is provisioned
  and verified. They
  aggregate mobile Linux, Android UI, Apple, iOS UI, web-landing, and backend
  result manifests plus unconditional root-boundary and released-contract
  fingerprints for the same commit. Production work remains blocked until a
  separately enforced trusted policy audit and required aggregate are proven,
  not merely checked in.

## Interaction Policy

Every product agent and implementation story follows AD-40/NFR-24/UX-DR37 and
the protected interaction-default policy: minimize taps, scrolls, fields, and
choice surfaces on the safe happy path; prefill only allowlisted deterministic
values from the immutable configuration snapshot;
show each default with provenance; and keep it editable and reversible within
scope. Policy-resolved jurisdiction and processing region are shown before
remote consent; the user authorizes the resulting transfer rather than selecting
provider routing. Remote upload/consent is never silent; medical/allergen
conclusions are prohibited, and materially uncertain food identity is never
silently preselected. Journey
acceptance records the interaction budget and tests default, override, and
forbidden-default paths.

## Implementation Agent Overrides

When `kotlin-multiplatform-developer` is used:

- Real source paths include `src/` even though the generic prompt's logical tree
  omits it.
- The project Engine API and module graph override the prompt's contradictory
  concrete-repository/data dependency rule.
- Platform APIs stay outside `commonMain`; `expect/actual` is limited to core
  platform facilities, while Engine adapters use platform source sets and DI.
- Cancellation exceptions are rethrown; transport/runtime exceptions are mapped
  at adapter boundaries.
- HTTP body logging is prohibited for photo and inference traffic.
- Data-source and adapter tests are required wherever behavior is non-trivial.
  Every compiled production source must belong to exactly one coverage or
  approved equivalent-evidence denominator. JVM/Android modules and the
  deduplicated aggregate remain at or above 85% unit-test line and branch
  coverage; changed production lines and branch outcomes remain at 100%.
- Existing version-catalog pins win. Dependency upgrades are separate stories.
- Every invocation names one story and exactly one AD-15 ownership slice,
  allowed paths, exact platform/UI, local and remote data-source requirements,
  contract version, acceptance criteria, test commands, privacy rules, and
  forbidden changes. A story never authorizes a broad invocation.
- Mobile implementation agents may not edit `web/landing/`,
  `backend/mobile-api/`, or cross-product contracts. An integration story
  coordinates separate owner-scoped invocations and requires every affected
  distinct owner; it does not grant one mobile invocation ownership of
  `contracts/` or another product root.
- The versioned acceptance-scenario catalog, derived from UJ-1 through UJ-4 and
  SM-9, defines completeness. Every catalog entry has one Maestro journey ID and
  shared flow that runs on Android and iOS using deterministic fixtures and
  linked non-visual oracles. Platform files may supply only app identity and
  locator dictionaries behind the Maestro JavaScript `output` page-object
  loader; they cannot change actions, assertions, waits, fixtures, or outcomes.
- The platform-contract catalog derived from accessibility, lifecycle, bridge,
  and UX requirements is also closed. Each platform maps to shared evidence or
  fresh binary-bound native evidence; separate Android/iOS network oracles prove
  transfer behavior for the installed acceptance executable.

## Historical Prototype Status

The downloaded historical NutriArch prototype demonstrates a cloud-first
camera-to-result loop using React Native/Expo, a Cloudflare Worker, OpenRouter,
and a small TypeScript food table. It is evidence for UX behavior and API
failure handling, not the selected production stack.

Known prototype behavior that must not be carried forward unchanged:

- every photo is uploaded despite privacy copy that implies nothing is saved;
- DB misses can display model-generated macros;
- no local Engine, shared Engine contract, SQLite integration, correction flow,
  automated test suite, CI, or device validation exists.
