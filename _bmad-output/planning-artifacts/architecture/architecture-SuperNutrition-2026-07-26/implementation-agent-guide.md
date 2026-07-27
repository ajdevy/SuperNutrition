---
title: SuperNutrition Implementation Agent Guide
status: final
date: 2026-07-26
authoritative_inputs:
  - ARCHITECTURE-SPINE.md
  - ../../prds/prd-SuperNutrition-2026-07-26/prd.md
  - ../../ux-designs/ux-SuperNutrition-2026-07-26/DESIGN.md
  - ../../ux-designs/ux-SuperNutrition-2026-07-26/EXPERIENCE.md
  - giphysearch-practices-adoption.md
vendor:
  repository: vendor/claude-code-agents
  primary_agent: agents/coding/kotlin/kotlin-multiplatform-developer.md
---

# SuperNutrition Implementation Agent Guide

## 1. Authority order

Every implementation session follows this order:

1. The current story and its acceptance criteria.
2. Cited architecture decisions in `ARCHITECTURE-SPINE.md`.
3. PRD functional and non-functional requirements.
4. UX state and accessibility specifications.
5. This project override guide.
6. The vendored agent prompt.

If a vendored prompt conflicts with a higher item, the higher item wins. An agent must report the conflict rather than silently merge both interpretations.

## 2. Primary agent

Use the vendored `kotlin-multiplatform-developer` for bounded Kotlin Multiplatform implementation slices. It is well suited to:

- shared contracts and Kotlin models;
- common state machines and orchestration;
- platform `actual` adapters;
- Ktor remote clients;
- SQLDelight-backed KMP repositories;
- Koin module definitions;
- common and platform tests.

Do not ask it to create the whole application in one invocation. One invocation owns one story and exactly one AD-15 named ownership slice; a story is never sufficient scope by itself.

## 3. Project answers to the agent's setup questions

| Prompt topic | SuperNutrition answer |
| --- | --- |
| DI framework | Koin 4.2.2, constructor injection, app-root startup, explicit `localEngine` and `remoteEngine` qualifiers |
| Shared UI | Compose Multiplatform for product screens on Android and iOS |
| Native UI | Only camera/photo bridges and platform hosting surfaces |
| Navigation | Decompose 3.5.0 for lifecycle and screen boundaries; photo workflow remains one reducer |
| Networking | Ktor 3.5.1; OkHttp engine on Android and Darwin engine on iOS |
| Persistence | SQLDelight 2.3.2 for nutrition catalog and provenance |
| Remote data source | Yes only in the remote inference story/module |
| Local inference data source | A platform service/engine adapter, not a repository pretending a model is remote data |
| Platforms | Exact story target only: common, Android, iOS, or remote gateway |
| Package namespace | Frozen by the scaffold story before source generation; subsequent stories read the checked-in namespace |
| Source layout | Standard Gradle source roots under `src/commonMain`, `src/commonTest`, `src/jvmTest`, `src/androidMain`, `src/androidUnitTest`, `src/androidDeviceTest`, `src/iosMain`, and `src/iosTest` as declared by each module |
| Coverage | Every product source has one denominator; each product's modules and aggregate stay at 85% line/branch; changed production lines/branches are 100%; mobile Native/bridge evidence is executable |
| Toolchain | Use the complete AD-16 tuple; the scaffold story must prove common, Android, and iOS builds before feature generation |
| Monorepo roots | `mobile/`, `web/landing/`, and `backend/mobile-api/`; integration artifacts only under `contracts/` |
| Mobile module layout | `mobile/apps`, `core`, `domain`, `data`, `inference`, `platform`, `feature`, `testing`, and `evaluation`; exact seed is in the spine |
| Build policy | Included `mobile/build-logic` convention plugins plus `mobile/gradle/libs.versions.toml`; mobile module scripts declare identity, targets, and dependencies only |
| Quality entrypoints | `qualityCheckJvmAndroid` and `qualityCheckApple` verify their manifest-defined host lanes without writes; local `qualityCheck` reports the other lane outstanding; `qualityFormat` is the only autocorrect path |
| Shared Compose UI contracts | Compose Multiplatform v2 test API behind the project robot DSL for common semantics, state, and bounded layout; execute `jvmTest`, `connectedAndroidTest`, and `iosSimulatorArm64Test` |
| Cross-platform UI acceptance | Bootstrap runs one non-product packaged launch/DI smoke; the irreversible photo-analysis profile runs the positive UJ-1 through UJ-4 Maestro CLI 2.7.0 inventory on Android and iOS |
| Native UI evidence | Android instrumentation and XCTest/XCUIAutomation only for platform bridge, OS UI, accessibility, lifecycle, or physical-device gaps |
| CI | GitHub Actions on every branch push and pull request with no path filters and an always-running same-SHA aggregate; merge-group and externally enforced trusted-policy controls become required only after AD-38 proves hosting support |

## 4. Mandatory project overrides

The following overrides address assumptions in the generic vendored prompt:

- A domain port may be an interface. `PhotoInferenceEngine` is intentionally a shared domain contract implemented by outer adapters.
- Repository dependencies must point inward. Domain or API modules never depend on concrete data or platform modules.
- The real Gradle source tree always includes `src/`; generated paths must use it.
- Compose owns iOS product screens. UIKit and AVFoundation are bridge details, not a second product UI.
- Decompose does not duplicate the feature state machine. Navigation callbacks and reducer effects must not both perform the same transition.
- `CancellationException` is always rethrown. Broad exception mapping cannot swallow cancellation.
- HTTP logging must never log photo bodies, multipart bytes, filenames, paths, user labels, or raw provider responses.
- Adapter, data-source, serialization, and error-mapping tests are required. The generic suggestion that these tests are unnecessary does not apply.
- The checked-in version catalog is authoritative. Agents do not select newer dependency versions during feature work.
- A generated change may touch only the files and module paths allowed by the story.
- Local and remote inference never import one another.
- Serialized Engine fields and identifiers come only from `mobile/domain/inference`; agents consume its golden fixtures rather than recreating DTOs.
- `mobile/domain/inference` contains no routing or consent policy. `mobile/domain/photo-analysis` owns RoutePolicy, orchestration, and attempt coordination through a one-way dependency on the Engine contract.
- Android and iOS camera/picker/permission implementations belong in `mobile/platform/photo-android` and `mobile/platform/photo-ios`, not app hosts.
- `mobile/apps/androidApp` solely starts Android Koin; `mobile/apps/sharedKit` exports the one idempotent iOS bootstrap and the thin iOS host invokes it once.
- Canonical Engine fixtures are immutable `mobile/domain/inference` artifacts; `mobile/testing/inference` owns only suite runners/fakes/builders, while adapter-specific fixtures stay with their adapter tests.
- `PhotoRef` is an attempt lease. Transfer-start effects use stable IDs and at-most-once app-root execution.
- `CatalogCountry`, `MarketJurisdiction`, `ProcessingRegion`, model, provider,
  and route policy come from one immutable `ConfigurationSnapshot` for the
  attempt; a catalog choice never selects a processing region.
- System-managed models can be policy-disabled but are never described as app-rollback-capable.
- `domain` is pure KMP: no Android, Apple framework, Compose, Koin, Ktor, or SQLDelight dependency.
- Production source sets never depend on `testing` modules; reusable fixtures and hosts are test-only dependencies.
- Module build files use convention plugins and catalog aliases. Agents do not add repositories, literal versions, or duplicate SDK/compiler settings.
- Host quality tasks must leave the worktree unchanged. Agents do not enable Detekt autocorrect or formatting writes in checks or CI.
- The source-set quality matrix and module-boundary manifest are authoritative; agents do not disable, skip, filter, rename, or omit their required tasks, source sets, packages, edges, evidence, or owners.
- Low-friction interaction is mandatory for every user-interaction-affecting
  story: before adding a picker,
  field, scroll surface, or confirmation tap, determine whether an allowlisted
  safe default or inline edit can remove it. The protected
  `mobile/config/quality/interaction-policy.toml` defines defaultable fields,
  sources, country validity, provenance, persistence scope, and invalidation
  keys. Context defaults come from the immutable ConfigurationSnapshot;
  `CatalogServingDefaultSource` may provide one atomic unit-and-amount serving
  proposal only after canonical-item confirmation. Defaults are visible with
  provenance, editable, reversible, and preserved only within their scope.
  `MarketPolicyResolver` alone resolves and displays market jurisdiction and
  processing region before remote consent; users do not select provider routing.
  Never preselect remote consent/upload or materially uncertain food identity.
  Medical/allergen conclusions are prohibited. Meal-specific preparation/oil/sauce/recipe
  corrections remain session-scoped in the MVP and are not persisted as future
  defaults. Story acceptance criteria and tests record the interaction budget,
  proposal/decision IDs, revision/digest preconditions, bounded auxiliary events,
  and default/override/forbidden-default paths; non-UI stories attest that they
  do not alter the policy or interaction budget.
- `contracts/interaction-policy/v1` is the shared language-neutral schema. The
  mobile policy file and any product-equivalent policy must conform to it. The
  `interaction-policy-contract` slice owns the schema, immutable fixtures,
  compatibility metadata, release digest, and validator. `InteractionDefaultPolicy`,
  `ConfigurationSnapshot`, `DefaultResolutionInput`, `DefaultResolution`,
  `MarketPolicyResolver`, and `DefaultResolver` in `mobile/core/config` are
  contract-neutral and never import domain types. `DefaultProposal`/
  `DefaultDecision` and their complete reducer-owned lifecycle belong to
  `mobile/domain/photo-analysis`; the reducer rejects stale revision/digest or
  duplicate events and invalidation wins a same-revision race. Compose presenters
  never resolve defaults or mutate the reducer. Evidence is
  `InteractionBudgetEvidence`, bound to policy/config versions and digests,
  proposal/decision IDs, resolved-value digest, field/source identity, scenario,
  platform, locale, accessibility, provenance, lifecycle, event sequence, the
  four tap/scroll/field/choice dimensions, and bounded text-entry,
  choice-surface, Back, Undo, cancellation, and permission-prompt counts with
  per-journey approved maxima. The policy also enumerates the closed
  locale/accessibility evidence matrix and invalidation keys.
- Detekt baselines, rule suppressions, test/coverage exclusions, and waivers require the exact independently approved expiring registry entry defined by AD-27/AD-29; safety-sensitive modules cannot add them.
- Compose stability configuration accepts only reviewed exact fully qualified types backed by compiler evidence; wildcard entries are forbidden.
- No distributable Android, iOS, framework, gateway, release, or internal-beta artifact may contain `testing` code, resources, manifests, fakes, or DI bindings. Only the two manifest-listed, unique-ID, non-distributable UI-acceptance executables may replace the AD-28 allowlisted ports, and they have no publish/archive/export path or production credentials.
- Coverage must pass the 85% per-module and per-product aggregate floors plus the 100% changed-line/changed-branch rule; every compiled production source has one denominator or independently approved expiring executable-equivalent entry.
- `mobile/config/quality/ui-evidence-profile.toml` selects a monotonic UI evidence profile. `bootstrap` requires only the non-product scaffold robot/smoke; the first photo-analysis product source atomically switches to `photo-analysis`, where `positive-scenarios.toml` derived from UJ-1 through UJ-4 and SM-9 plus its platform/native siblings defines completeness.
- The bootstrap robot lives only in `mobile/testing/scaffold-probe`; product screen robots live with `mobile/feature/photo-analysis/src/commonTest`. `mobile/testing/compose-support` owns only the v2 base DSL/host/synchronization/reporting utilities. Maestro page objects under the profile-selected `mobile/.maestro/<profile>/` are JavaScript `output` locator dictionaries loaded through `runScript`; platform loaders cannot contain actions, assertions, waits, retries, fixtures, skips, or alternate outcomes.
- Mobile agents cannot edit or depend on `web/landing/`, `backend/mobile-api/`, or
  another product's source/contracts. They may consume the independently owned,
  immutable `contracts/interaction-policy/v1` schema through the dedicated
  policy story. All other cross-product work uses an immutable contract release
  and an explicit integration story that coordinates separate owner-scoped
  invocations and distinct approvals; it never widens one invocation across
  roots.
- GitHub Actions workflows may not use path filters, commit-message skip directives, `pull_request_target`, floating action tags, or unnecessary token permissions.

## 5. Standard invocation envelope

Wrap every agent task with this context:

```text
Project: SuperNutrition in the SuperNutrition repository.
Identity: installed/launcher/in-app name is SuperNutrition; Apple App Store and Google Play listing title is SuperNutrition AI nutritionist; obey AD-39 and never put the store-only descriptor in the installed display name.
Story: <story id and title>.
Owned slice: <one exact AD-15 module, platform host/source-set, root-policy, or enumerated infrastructure-policy slice>.
Allowed files: <explicit paths>.
Forbidden files: every path not listed above.
Ownership: this invocation owns exactly one AD-15 slice; an integration or scaffold story still uses separate invocations for every slice and owner.
Architecture: read ARCHITECTURE-SPINE.md and obey <AD ids>.
Requirements: obey <FR/NFR/SM ids>.
Interaction budget: obey AD-40/NFR-24/UX-DR37 and the protected interaction-default policy; minimize taps, scrolls, fields, and choice points, make allowlisted defaults visible/editable/reversible with provenance, show policy-resolved jurisdiction before consent, and keep sensitive or uncertain decisions explicit.
UX: obey the cited DESIGN.md or EXPERIENCE.md states where applicable.
Versions: use the checked-in version catalog; do not upgrade dependencies.
Inference contract: version 1; candidates contain no numeric nutrition.
Privacy: never log photo bodies, filenames, paths, user labels, or provider bodies.
Coroutines: rethrow CancellationException.
Tests: write tests first; keep each affected module and its product aggregate at or above 85% line and branch coverage, changed production lines and branches at 100%, and all Native/hardware equivalents executable and inventoried.
Journeys: update the positive-scenario catalog first when a bound UJ/SM-9 path changes, then its one shared Maestro flow, both platform mappings/fingerprint, non-visual oracles, native-gap links, and affected common Compose robot contracts.
Quality: run the focused tests and the repository's read-only host quality task; report the other required host/device statuses as outstanding, never as skipped or passed.
Deliverable: implementation, focused tests, and a list of assumptions or blocked gates.
Do not refactor outside the owned slice.
```

## 6. Recommended agent sequence

| Stage | Vendored agent | Purpose |
| --- | --- | --- |
| Before a story | `system-analytics` | Map current product root/modules and detect existing conventions without writing code |
| Monorepo/CI bootstrap | `devops-orchestrator` | Establish root boundaries, product quality entrypoints, six-job CI, and trusted audit |
| KMP implementation | `kotlin-multiplatform-developer` | Implement the bounded common or platform slice |
| Compose UI story | `builder-compose-feature` | Implement approved mocks and state rendering after UX-A1 |
| Compiler/build failure | `kotlin-diagnostics` | Diagnose focused Kotlin/Gradle issues |
| Post-generation audit | `refactor-mobile` | Check architecture, cancellation, lifecycle, and scope without widening behavior |
| CI and gateway delivery | `devops-orchestrator` | Implement story-scoped CI or service delivery work |

Spring-focused agents are not the default for this mobile client. The gateway technology is not selected until G-4.

## 7. Story-specific invocation templates

### 7.0 Monorepo policy and CI bootstrap

```text
Use devops-orchestrator.
Owned slice: root product-directory placeholders, protected root policy/inventory schemas and validators, CODEOWNERS/ownership manifest, and GitHub Actions only.
Forbidden: mobile Gradle/settings/build logic or production source; web/backend source, dependency manifests, runtime, or deployment; contract contents beyond empty independently owned release structure.
Obey AD-15, AD-27, AD-29, and AD-35 through AD-38.
Create the three exact product roots, independent contracts root, policy-only tooling inventory, common product-inventory schema, distinct ownership roles, mobile scaffold-pending plus web/backend preselection lifecycle records and gates, and six-job CI.
Run quality-linux root-boundary/catalog validation unconditionally; make quality-backend emit its contract-side fingerprint; make ci-required directly need exactly six jobs and always require matching root, inventory, lifecycle, contract-catalog, and released-version-matrix fingerprints.
Trigger every branch push and pull request without filters. Add merge_group only when AD-38 evidence proves it can be enforced. Never claim ruleset, trusted required workflow, or merge-queue enforcement before that gate passes.
Do not choose web/backend frameworks or create a root package-manager workspace, shared cache, shared dependency install, product build plugin, product source generator, or multi-product deployment.
```

### 7.0a Hosting enforcement capability

```text
Use devops-orchestrator.
Owned slice: hosting/ruleset/trusted-policy configuration and its immutable evidence only.
Forbidden: product source, product build files, contracts, and unrelated repository settings.
Obey AD-35 and AD-38.
Provision and prove private-repository rulesets, distinct-owner approvals, required ci-required, immutable trusted-policy-audit, and a blocked bypass attempt. Require and prove merge_group only if merge queue remains enabled.
Do not report this story complete from checked-in workflow YAML alone.
```

### 7.0b Mobile build-policy scaffold slice

```text
Use kotlin-multiplatform-developer.
Owned slice: AD-15 mobile-build-policy only.
Allowed files: mobile/settings.gradle.kts, mobile/build.gradle.kts, mobile/build-logic/**, and mobile/gradle/**.
Forbidden: root policy/CI/CODEOWNERS, contracts/, web/landing/, backend/mobile-api/, and production features/models/providers/catalogs.
Obey AD-1, AD-10, AD-11, AD-15, AD-16, and AD-25 through AD-35.
Freeze the package namespace and complete mobile toolchain tuple from the spine.
Do not change mobile/product-lifecycle.toml; activation belongs to the final mobile-scaffold-activation invocation.
Create included-build convention plugins, strict repository/dependency verification and per-project locks, host-qualified read-only quality task wiring, and mutating qualityFormat. Create manifests, reusable testing modules, and app modules through the separate slices below.
Use AGP built-in Kotlin for androidApp; use org.jetbrains.kotlin.multiplatform plus com.android.kotlin.multiplatform.library for Android-bearing KMP libraries; never combine legacy Android and KMP plugin identities.
Prove convention behavior with mobile/build-logic TestKit fixtures, dependency verification/locks, generated task topology, read-only quality behavior, and configuration-cache store then reuse; do not claim application, robot, device, framework, or Xcode evidence from this invocation.
Use Detekt and its ktlint-wrapper ruleset at 2.0.0-alpha.5, Kover 0.9.8 with one explicit JVM/Android-host merge project and report variant, org.jetbrains.compose.ui:ui-test:1.11.1 with the v2 API, AndroidX Compose device-test artifacts 1.11.2, and checksum-pinned Maestro CLI 2.7.0.
Add negative Gradle TestKit policy fixtures. The app-host, testing-support, UI-acceptance executable, and release-composition work cannot be folded into this slice.
Publish exact commands and environment versions; do not claim Native/device coverage from Kover.
```

### 7.0c Mobile quality-policy scaffold slice

```text
Use kotlin-multiplatform-developer.
Owned slice: AD-15 mobile-quality-policy only.
Allowed files: mobile/config/**.
Forbidden: every source module, mobile settings/build logic/catalog, lifecycle state, root CI/policy, contracts, web, and backend.
Create the module-boundary, product/source-set inventory, positive-scenario, journey, platform-contract, native-gap, suppression, coverage, and exact-FQN Compose stability manifests required by AD-27 through AD-34.
Set `ui-evidence-profile.toml` to `bootstrap` and map only the architecture-required bootstrap-shell robot and launch/DI smoke IDs. Do not populate UJ/SM-9 product journeys, mark absent evidence successful, or activate mobile.
```

### 7.0c2 Mobile UI-acceptance policy scaffold slice

```text
Use kotlin-multiplatform-developer.
Owned slice: AD-15 mobile-ui-acceptance-policy only.
Allowed files: mobile/.maestro/**.
Forbidden: mobile/config, every source/app/testing module, lifecycle state, root CI/policy, contracts, web, and backend.
Create only mobile/.maestro/bootstrap/ with one cross-platform non-product launch/DI smoke and its locator/app-identity dictionaries. It must target the two non-distributable scaffold acceptance hosts and must not claim UJ, feature, inference, nutrition, privacy-route, or release behavior.
Do not create mobile/.maestro/photo-analysis/ until the product feature story atomically selects that profile through a separate mobile-quality-policy invocation.
```

### 7.0d Mobile scaffold module sequence and proof ownership

The scaffold story is one pull request coordinated as the following committed,
sequential invocations. Every row is a separate invocation with the exact
allowed path shown; no invocation may absorb another row.

| Order | AD-15 owned slice / allowed path | Required committed handoff |
| --- | --- | --- |
| 1 | `mobile-build-policy` — paths in 7.0b | pinned tuple, conventions, locks, TestKit proof, host task topology |
| 2 | `mobile-quality-policy` — `mobile/config/**` | schema-valid nonempty bootstrap inventories and policy manifests |
| 3 | `mobile-ui-acceptance-policy` — `mobile/.maestro/**` | bootstrap-only cross-platform launch/DI smoke flow and locator dictionaries |
| 4 | module `mobile/testing/scaffold-probe/**` | common/Android/iOS compile-and-test probe for Koin, Decompose, Ktor engines, SQLDelight plugin/runtime, serialization, coroutines, and the concrete bootstrap-shell robot; no production API |
| 5 | module `mobile/testing/compose-support/**` | v2 robot DSL/host/synchronization tests with nonzero JVM, Android-device, and iOS-simulator probe IDs |
| 6 | platform host `mobile/apps/sharedKit/**` | idempotent Koin bootstrap, KMP framework export/link, and Swift bridge tests |
| 7 | platform host `mobile/apps/androidApp/**` | thin Android host, fake-only non-distributable acceptance app, launch/DI/release-composition smoke, `connectedAndroidTest` |
| 8 | platform host `mobile/apps/iosApp/**` | thin Xcode host, fake-only non-distributable acceptance scheme, launch/DI/release-composition smoke, `xcodebuild build-for-testing` and simulator test |
| 9 | `mobile-scaffold-activation` — `mobile/product-lifecycle.toml` only | change `scaffold-pending` to `active` only after all prior committed handoffs and the evidence matrix below pass |

The story-level evidence matrix has exactly one owning invocation per proof:

| AD-16 / AR-1 proof | Owner |
| --- | --- |
| Kotlin/Compose/Gradle/AGP/JDK tuple, repository policy, locks, Detekt, Kover, configuration-cache store/reuse | `mobile-build-policy` |
| Complete module/source/evidence inventories, `bootstrap` UI profile, and stability/quality policy | `mobile-quality-policy` |
| Cross-platform non-product bootstrap launch/DI smoke flow | `mobile-ui-acceptance-policy` |
| Common, Android, and iOS KMP compilation with Koin, Decompose, Ktor, SQLDelight, and the bootstrap-shell robot | `mobile/testing/scaffold-probe` |
| Compose v2 robot execution substrate on JVM, Android device, and iOS simulator | `mobile/testing/compose-support` |
| iOS framework export/link and native bootstrap bridge | `mobile/apps/sharedKit` |
| Android app/package/device and non-distributable acceptance evidence | `mobile/apps/androidApp` |
| iOS Xcode app/scheme/simulator and non-distributable acceptance evidence | `mobile/apps/iosApp` |
| Aggregate `qualityCheckJvmAndroid`, `qualityCheckApple`, four mobile jobs, and all-six-job same-SHA success | activation invocation writes only `mobile/product-lifecycle.toml`; the candidate active-state CI execution is read-only |

The coordinator may read all prior outputs but writes only its current slice.
The final invocation proposes `active` on the story branch; that candidate
causes all four mobile jobs to run their active paths. The scaffold story and
Bootstrap proof remain incomplete, and the protected branch remains
`scaffold-pending`, unless the final all-six-job aggregate succeeds and merges.

### 7.0e Interaction-policy contract slice

```text
Use devops-orchestrator for the language-neutral contract and validator, then
use a separate kotlin-multiplatform-developer invocation for the mobile policy
consumer. The contract invocation owns only contracts/interaction-policy/v1/**
and its schema fixtures/compatibility metadata; it may not edit mobile/config or
any product source. Create the immutable v1 schema, release digest, field
allowlist, source/provenance vocabulary, atomic standardServing pair, lifecycle
event vocabulary, invalidation keys, four required dimensions, auxiliary
interaction counts, locale/accessibility matrix, and negative fixtures. The
validator must reject forbidden defaults, missing digests/values, stale or
duplicate events, non-atomic serving pairs, unbounded auxiliary events, and
schema drift. The mobile policy invocation consumes the released digest in
mobile/config/quality/interaction-policy.toml and owns no replacement schema.
```

### 7.1 Shared inference contract — separate invocations

```text
Invocation A:
Use kotlin-multiplatform-developer.
Owned slice: mobile/domain/inference and its own tests only.
Obey AD-1, AD-2, AD-4, AD-8, AD-9, AD-11, AD-14, AD-17, AD-21, AD-25, AD-28.
Create contract v1 immutable Kotlin types, engine port, capability states, candidate evidence, typed failures, canonical identifiers, versioned telemetry schema, and canonical Engine fixtures.
Do not add Ktor, SQLDelight, camera APIs, provider schemas, or nutrition values.

Invocation B, only after Invocation A is committed:
Use kotlin-multiplatform-developer.
Owned slice: mobile/testing/inference and its own tests only.
Consume the committed mobile/domain/inference API and immutable fixtures without editing them.
Create suite runners, fakes, and builders only.
Apply one shared contract test suite to all provided fakes.
Never assign both modules or contracts/ to one invocation.
```

### 7.2 Android local inference

```text
Use kotlin-multiplatform-developer.
Owned slice: mobile/inference/local-android and its tests.
Obey AD-2, AD-3, AD-4, AD-8, AD-9, AD-11, AD-14, AD-17, AD-22, AD-25.
Implement only the G-3-approved Android runtime/model adapter.
Read image bytes through PhotoContentSource and return contract-v1 evidence.
Do not modify remote inference, orchestration policy, nutrition, or iOS code.
Include cancellation, cleanup, preprocessing parity, incompatible-model, resource-pressure, model-lifecycle, and contract-suite tests.
```

### 7.3 iOS local inference

```text
Use kotlin-multiplatform-developer.
Owned slice: mobile/inference/local-ios and its tests.
Obey AD-2, AD-3, AD-4, AD-8, AD-9, AD-11, AD-14, AD-17, AD-22, AD-25.
Implement only the G-3-approved Vision/Core ML or other selected adapter.
Confine UIImage, NSURL, Vision, and Core ML objects to the iOS adapter.
Do not modify remote inference, orchestration policy, nutrition, or Android code.
Include cancellation, cleanup, preprocessing parity, incompatible-model, resource-pressure, model-lifecycle, and contract-suite tests.
```

### 7.4 Remote mobile adapter

```text
Use kotlin-multiplatform-developer.
Owned slice: mobile/inference/remote and its common/platform tests.
Obey AD-2, AD-3, AD-6, AD-8, AD-9, AD-11, AD-13, AD-14, AD-17, AD-18, AD-20, AD-21.
Implement Ktor contract-v1 upload to the first-party gateway only.
Require preprocessing to strip EXIF, GPS, and non-essential metadata before
creating the scrubbed-payload digest. Bind the consent receipt to that digest,
purpose, market jurisdiction, processing-region/provider policy, retention
version, configuration version, and transfer state.
Use Android OkHttp and iOS Darwin engines through core-network factories.
Use MockEngine tests for at-most-once upload, the three transfer/cancellation states, timeout, redaction, token scope/expiry, replay, rotation/revocation, environment binding, version mismatch, rate limit, deletion request, and malformed responses.
Do not add provider credentials or provider-specific mobile schemas.
```

### 7.5 Orchestrator

```text
Use kotlin-multiplatform-developer.
Owned slice: mobile/domain/photo-analysis RoutePolicy and attempt coordination only; consume mobile/domain/inference without editing it.
Obey AD-2, AD-3, AD-5, AD-6, AD-8, AD-9, AD-11, AD-12, AD-14, AD-18, AD-20, AD-25.
Implement a pure route decision and cancellable attempt coordinator over injected localEngine and remoteEngine ports.
Before G-5, support named conservative policies and fake thresholds only in test fixtures.
Guarantee at most one active engine and one terminal outcome per attempt.
Test late callbacks, at-most-once effects, photo-lease revocation, metadata
scrubbing, Back, background/restore, replacement, pre-transfer cancellation,
transferring cancellation, transferred cancellation, bounded fallback
eligibility, consent decline/no-nag behavior, retry eligibility,
catalog/jurisdiction restart, and atomic configuration snapshots.
```

### 7.6 Photo-analysis feature

```text
Use kotlin-multiplatform-developer for reducer and component logic.
Use builder-compose-feature only after UX-A1 and the five required mocks are approved.
Owned slice: one of mobile/domain/photo-analysis or mobile/feature/photo-analysis, as named by the story; never both implicitly.
Obey AD-4, AD-5, AD-6, AD-7, AD-8, AD-10, AD-11, AD-12, AD-14, AD-18, AD-23, AD-25.
The reducer is the only workflow-state mutator. Decompose owns lifecycle and screen boundaries.
Render consent choices with equal prominence and preserve a no-upload path.
Apply semantic roles, contrast and non-color cues, dynamic type/font scaling, text expansion, bidi, localized numbers/units, reduced motion, neutral externalized copy, and Compose-to-native focus restoration.
Do not implement model adapters, gateway code, or catalog ingestion here.
If this is the first product UI source, the reducer/ports and every production dependency must already be committed. Coordinate the complete 7.9 transition sequence; the feature invocation cannot register itself, edit core UI IDs or policy, create Maestro flows, or wire any app host.
```

### 7.7 Nutrition catalog

```text
Use kotlin-multiplatform-developer.
Owned slice: one of mobile/domain/nutrition or mobile/data/nutrition, as named by the story.
Obey AD-1, AD-7, AD-11, AD-12, AD-14, AD-19, AD-25.
Implement only G-2-approved schema, packages, mappings, provenance, migrations, offline search, package verification/rollback, and deterministic serving math.
Reject missing or ambiguous canonical mappings explicitly.
Do not accept nutrient values from any inference result or provider response.
Test queries, migrations, namespace-qualified IDs, country package selection, SI conversion, source/calculation precision, rounding, meal aggregation, partial items, missing mappings, and provenance.
```

### 7.8 Platform photo bridge

```text
Use kotlin-multiplatform-developer for the named platform slice.
Owned slice: exactly one of mobile/platform/photo-android or mobile/platform/photo-ios and its tests.
Obey AD-4, AD-8, AD-11, AD-18, AD-23, AD-25, AD-28 through AD-31.
Implement the story-approved camera, system picker, permission, leased PhotoRef, normalization, cleanup, lifecycle, and accessibility bridge behind core ports.
Keep Bitmap, Uri, UIImage, NSURL, AVFoundation, PhotosUI, CameraX, and framework controllers inside the adapter.
Do not place bridge behavior in app hosts or start Koin here.
Include contract/lifecycle tests and the story-required physical-device evidence; simulator or emulator output is not camera proof.
```

### 7.9 Positive-journey UI acceptance

```text
Invocation A — feature contracts:
Use kotlin-multiplatform-developer.
Owned slice: mobile/feature/photo-analysis and its colocated common Compose screen-contract tests only.
Do not edit mobile/config or mobile/.maestro.

Invocation B — quality profile and catalogs, after A is committed:
Use kotlin-multiplatform-developer.
Owned slice: AD-15 mobile-quality-policy only.
Allowed files: mobile/config/**.
Atomically switch ui-evidence-profile.toml from bootstrap to photo-analysis and populate positive-scenarios.toml, journeys.toml, platform-contracts.toml, and native-gaps.toml. Downgrade is forbidden.

Invocation C — packaged-app journeys, after B is committed:
Use kotlin-multiplatform-developer.
Owned slice: AD-15 mobile-ui-acceptance-policy only.
Allowed files: mobile/.maestro/**.
Create the mapped mobile/.maestro/photo-analysis/ shared flows and locator/app-identity dictionaries without editing catalogs or product source.

Invocation D — module registration:
Use kotlin-multiplatform-developer.
Owned slice: AD-15 mobile-build-policy only.
Register the already committed feature module and its allowed dependency edges without editing its source, policies, flows, or app hosts.

Invocation E — automation ID registry:
Use kotlin-multiplatform-developer.
Owned slice: mobile/core/ui and its colocated tests only.
Add only the feature's stable locale-neutral UiAutomationId entries and registry contract tests.

Invocations F, G, and H — composition, after A through E are committed:
Use kotlin-multiplatform-developer separately for each invocation.
Owned slices: respectively mobile/apps/sharedKit, mobile/apps/androidApp, and mobile/apps/iosApp, each with colocated tests only.
Wire the already committed feature/ports into that exact composition or host boundary. Do not edit another app host, the feature, build policy, catalogs, or Maestro. Preserve thin hosts and prove framework export/link, packaged acceptance composition, release-composition smoke, and the platform-specific build/test evidence owned by that slice.

The final combined candidate includes A through H and the proposed
`photo-analysis` profile switch. That candidate is mergeable only after all
four real mobile jobs plus `ci-required` pass; the protected branch remains
`bootstrap` on failure. Any platform-native gap implementation uses another
invocation owning exactly one named platform module/host slice; it cannot edit
the catalogs.
Obey AD-11, AD-15, AD-23, AD-28 through AD-35.
Use the project robot DSL over androidx.compose.ui.test.v2.runComposeUiTest for shared semantics, state, and bounded-layout behavior; keep concrete robots in mobile/feature/photo-analysis/src/commonTest and run the same IDs through jvmTest, connectedAndroidTest, and iosSimulatorArm64Test. Poll only for a declared asynchronous state before its deadline; once an assertion fails, never re-execute it into success.
Use Maestro CLI 2.7.0 and reusable subflows under mobile/.maestro/photo-analysis/ for catalog journeys. Page objects are JavaScript output locator dictionaries loaded with runScript and platform-conditional runFlow; the same logical actions, assertions, fixture, oracles, and terminal state must run on Android and iOS.
Prefer the shared UiAutomationId registry and localized visible semantics, enable testTagsAsResourceId at the Android semantics root, keep platform files limited to app identity/locators, and never use coordinate selectors. Implement every applicable native-gaps.toml entry with its named Android instrumentation, XCTest/XCUIAutomation, or physical-device evidence.
Inject deterministic Engine, gateway, country, and catalog fixtures; do not call a live model/provider or assert its accuracy. Emit separate Android/iOS non-visual oracle evidence bound to commit, installed-binary digest, process/network namespace, fixture/config digest, and scenario. Execute every applicable platform-contract/native-gap entry with freshness and invalidation attestations.
Use one stable journey ID and the same user-visible semantic assertions on both platforms.
Publish logs and screenshots only after the AD-14 privacy allowlist is applied.
```

## 8. Pull-request and ownership boundaries

Implementation follows one pull request per story. The shared contract lands before concrete engine work. Once the contract is stable:

- Android local, iOS local, remote mobile, gateway, and nutrition work can use separate story branches.
- Each branch adds or updates its own contract-suite fixture.
- Orchestrator changes require contract-owner review.
- A contract-breaking change creates a new contract version and migration story; it is not hidden in an adapter PR.
- Country policy and model manifests are reviewed as code and carry provenance.

## 9. Definition of done for agent-authored changes

An agent-authored story is not complete until:

- allowed-path scope is clean;
- cited acceptance criteria are demonstrated;
- architecture decisions are satisfied;
- local and remote boundaries remain independent;
- deterministic new code meets the coverage rule;
- shared contract suites pass for affected engines;
- canonical serialization, telemetry, preprocessing, or nutrition fixtures pass where applicable;
- cancellation and cleanup paths are tested;
- logging and telemetry fields pass the allowlist review;
- documentation and manifests are updated when the contract or configuration changes;
- real-device evidence exists wherever the story makes a platform claim;
- no dependency version changed outside explicit story scope;
- the manifest-required host quality task passes, produces every declared artifact, and does not modify the worktree;
- Linux and macOS checks pass when the changed slice binds those lanes;
- every affected production source has one denominator, its module and product aggregate meet 85% line/branch coverage, changed production lines/branches meet 100%, and required equivalent evidence executes;
- every affected catalog journey passes through the same fingerprinted Maestro flow on Android and iOS, all linked non-visual oracles pass, affected common robot IDs execute on JVM/Android/iOS, and every applicable native-gap entry passes;
- every affected journey records its tap/scroll/field/choice budget, bounded text-entry/choice-surface/recovery maxima, proposal/decision IDs, value/digests, and default provenance, and verifies the shortest safe default path, each user-override/Undo path, and negative cases for hidden/forbidden defaults;
- every-push GitHub Actions jobs and the always-running same-SHA aggregate are present and fail closed; production work does not begin until AD-38 proves the externally enforced controls;
- no required task is skipped, disabled, filtered to zero, ignored, or absent;
- no new repository, literal/dynamic dependency version, broad suppression, wildcard stability entry, or production/shippable dependency on `testing` was introduced;
- mandatory cancellation, consent, logging, replay, cleanup, and deletion conformance scenarios remain present and pass.

## 10. Blocked invocation conditions

Do not invoke production implementation agents for:

- launch-country-specific behavior before G-1;
- nutrition source packages beyond fakes before G-2;
- concrete local runtime/model adapters before G-3;
- provider-specific gateway work before G-4;
- model evaluation, public accuracy claims, or confidence-based hybrid routing before G-5;
- server-side correction telemetry before G-6;
- visual Compose screens before UX-A1 and the five approved mocks.

Contracts, fakes, scaffold, state-machine tests, evaluation harness structure, and consent-safe no-upload flows may proceed before those gates when their stories say so.
