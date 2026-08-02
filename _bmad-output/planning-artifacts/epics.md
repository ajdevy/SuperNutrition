---
stepsCompleted:
  - 1
  - 2
  - 3
  - 4
inputDocuments:
  - prds/prd-SuperNutrition-2026-07-26/prd.md
  - architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md
  - architecture/architecture-SuperNutrition-2026-07-26/solution-design.md
  - architecture/architecture-SuperNutrition-2026-07-26/implementation-agent-guide.md
  - architecture/architecture-SuperNutrition-2026-07-26/giphysearch-practices-adoption.md
  - ux-designs/ux-SuperNutrition-2026-07-26/DESIGN.md
  - ux-designs/ux-SuperNutrition-2026-07-26/EXPERIENCE.md
  - docs/food-photo-recognition-report.md
  - /Users/aj/Downloads/NutriArch
  - /Users/aj/Downloads/report.md
  - ../specs/spec-text-meal-identification/SPEC.md
  - ../specs/spec-text-meal-identification/search-mechanics.md
  - ../specs/spec-text-meal-identification/contract-v2.md
  - ../specs/spec-text-meal-identification/failure-modes.md
---

# SuperNutrition - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for SuperNutrition, decomposing the requirements from the PRD, UX Design if it exists, and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

FR-1: The user can open SuperNutrition directly into the capture surface and take a still photo with one primary action after permissions are resolved.

FR-2: The user can select one image through the platform system picker without granting broader media access where the platform supports selected-only access.

FR-3: The user can understand why camera access is requested and recover from denial, restriction, unavailable hardware, or capture failure through settings, import, or manual search.

FR-4: The system can normalize orientation, resolution, and metadata into a session-scoped Photo Input suitable for Engines and remove temporary artifacts when the Analysis Session ends.

FR-5: Every Engine can accept the same versioned inference request and return the same Candidate, Abstention, provenance, and typed-error shapes without returning numeric nutrition.

FR-6: The system can obtain machine-readable and user-safe Engine Capability before choosing an inference path, including unavailable, model-required, downloading, ready, temporarily unavailable, and unsupported states where applicable.

FR-7: The system can run a Local Engine on eligible Android and iOS devices without transmitting the Photo Input, while permitting different platform adapters, runtimes, models, and device tiers.

FR-8: The system can run a Remote Engine through a first-party gateway only after valid Remote Upload Consent, without embedding provider credentials or provider-specific contracts in the mobile app.

FR-9: The system attempts a ready Local Engine before offering remote analysis unless the user explicitly selects a different permitted route for that Analysis Session.

FR-10: The system can offer remote analysis after local unavailability, Abstention, or a policy-defined low-confidence outcome, explain the transfer and alternatives, and preserve a no-upload recovery path.

FR-11: The user can cancel an Analysis Session, retry the same route, retake the photo, or choose another permitted route without stale results or duplicate completed uploads.

FR-12: The system preserves Inference Route, Engine/model contract version, Confidence, Abstention reason, and unresolved assumptions through Confirmation and results.

FR-13: The user can review ranked Candidates, alternatives, Confidence, and unresolved assumptions, and remove false Candidates before Confirmation.

FR-14: The user can replace or add a Candidate by manually searching available versioned Nutrition Source records, including when all Engines are unavailable.

FR-15: The user can confirm or adjust the amount, unit or range, and relevant recipe assumptions used as the Serving Assumption.

FR-16: The system reproducibly calculates the supported nutrient, vitamin, and mineral profile plus catalog-backed descriptive food properties from the confirmed versioned Nutrition Source record, serving conversion, and Serving Assumption while preserving per-item and aggregate provenance. The core UI omits a calorie total.

FR-17: The system never displays model-generated calories, macronutrients, or other numeric nutrition as a Grounded Nutrition Result; unresolved matches remain partial or unknown.

FR-18: The user can view confirmed food, Serving Assumption, supported nutrients, vitamins/minerals, catalog-backed descriptive food properties, source/version, uncertainty, and Inference Route with estimate—not medical, allergen, health-effect, or exact-measurement—language, on the same photo-first review screen.

FR-19: The user can end the current Analysis Session, clear session-scoped photo and inference state, and return to capture with one primary Scan again action.

FR-20: The user receives an outcome-specific recovery path for no-food, poor-image, model unavailable/download, offline, timeout, rate-limit, consent, parsing, grounding, and unexpected failures, with Retry only when it can change the outcome.

FR-21: The user can use ready local analysis and manual nutrition search while offline and can always end or restart the Analysis Session.

FR-22: The system can record redacted route, capability, timing, Candidate decision, correction, source coverage, error, version, and resource events without raw photos, free-form sensitive payloads, secrets, headers, or transport bodies.

FR-23: The team can run local-only, remote-only, hybrid, and manual baselines against one versioned Evaluation Corpus and report quality, correction, grounding error, latency, resource, cost, and representative slice results.

FR-24: The team can compare Android and iOS model exports or adapters against golden images and activate only versions whose hash, taxonomy, preprocessing, runtime compatibility, and parity evidence pass, retaining a previous compatible route.

FR-25: Authorized operators can select versioned, allow-listed, observable Engine configurations without a mobile feature rewrite or weakening consent, retention, grounding, or contract requirements.

FR-26: The user can find a canonical Nutrition Source record by typing food text, on the lowest supported device tier, while offline, with no inference model present, and can build a multi-item meal by adding records one at a time into the same Analysis Session.

FR-27: The user can describe a whole meal in natural language and receive one ranked Candidate group per detected food item, in the same Candidate, Abstention, provenance, and typed-error shapes the photo path returns and containing no numeric nutrition.

FR-28: The system can run an on-device text identification engine where device capability permits, report the same capability states as other Engines, and leave manual search reachable in one action whenever that engine is not ready.

FR-29: The user can correct how a description was split into items — merging, splitting, removing, or adding one — before confirming any item, without restarting the Analysis Session or discarding already-confirmed items.

FR-30: The user can send a typed description for remote identification after local identification is unavailable, abstains, or returns a policy-defined low-confidence result, and only after consenting to a disclosure written for text rather than photos.

FR-31: The user can search and describe food in the declared languages of each supported market, including where the app language differs from the catalog language, where the catalog language is inflected, and where entry uses a different script from the records.

### NonFunctional Requirements

NFR-1: Evaluate the provisional camera-ready and capture-to-Candidate hypotheses on named physical-device and network conditions and replace them with Gate G-3 release budgets; simulator or emulator results are not performance proof.

NFR-2: Long-running inference is cancellable and cannot publish stale state after cancellation or Photo Input replacement.

NFR-3: Model storage, download, memory, thermal, and battery budgets are measured per device tier before activation; parameter count alone is not accepted as a resource proxy.

NFR-4: Raw photos, thumbnails, base64 bodies, provider prompts/responses, credentials, and authorization headers are excluded from application, gateway, analytics, crash, and HTTP logs.

NFR-5: Remote photos are minimized, encrypted in transit, not retained beyond request processing by default, and not used for training without separate explicit opt-in.

NFR-6: Remote processing supports country-configurable disclosures, subprocessors, deletion/retention policy, and regional routing required for selected Tier 1 launch markets, including GDPR and UK GDPR where applicable.

NFR-7: Downloaded models and manifests use allow-listed origins, integrity verification, compatibility metadata, atomic activation, and rollback.

NFR-8: Secrets exist only in authorized server-side configuration and can be rotated without a mobile release.

NFR-9: Android and iOS platform adapters pass shared contract and lifecycle tests, and camera and Local Engine release proof uses physical devices.

NFR-10: The first release supports the minimum OS/device matrix selected by the capability spike and provides an understandable unsupported-device path.

NFR-11: Remote retry is bounded, idempotent where applicable, and honors timeout, rate-limit, offline, and cancellation semantics.

NFR-12: Nutrition Source and model updates are versioned and reproducible; an incompatible update cannot silently replace the active version.

NFR-13: Interactive controls meet platform target-size guidance, expose semantic labels, roles, and state to VoiceOver and TalkBack, follow logical focus order, and remain usable with large text and reduced motion.

NFR-14: Color is not the only carrier of Confidence, failure, route, or consent state, and selected themes meet WCAG 2.2 AA contrast where applicable.

NFR-15: User-visible text is externalized, and domain/storage models do not hard-code language, country, decimal format, serving unit, or Nutrition Source.

NFR-16: Copy and presentation are calm, minimal, neutral, nonjudgmental, and uncertainty-aware; they avoid medical, weight-shaming, guilt-inducing, celebratory, or alarmist framing.

NFR-17: Every compiled production source belongs to exactly one coverage denominator. Tool-measurable mobile, web-landing, and backend production code maintains at least 85% unit-test line and branch coverage per module and per product aggregate; changed production lines and branch outcomes maintain 100%. Unavoidable Native, Swift, framework, or hardware behavior requires explicit executable equivalent evidence and documented physical-device acceptance rather than an optional exclusion. Apple/Xcode evidence may be a good-to-have native record when the checked-in platform policy marks that gap optional; required Android evidence remains fail-closed.

NFR-18: Local Android, local iOS, remote, and orchestration modules can build and run their own tests without compiling, importing, or editing another Engine implementation.

NFR-19: Dependency and tool versions are pinned centrally within each monorepo product boundary; repository-root pins are limited to policy and CI tooling and cannot become a shared product dependency graph. Implementation agents do not perform unrelated upgrades.

NFR-20: A closed, reviewable catalog derived from every UJ-1 through UJ-4 main, alternative, and recovery path that reaches a valid result or privacy-preserving cancellation, plus every SM-9 dimension, defines completeness. Every catalog scenario has one shared journey definition executed as deterministic automated UI acceptance on Android and iOS using controlled fixtures and linked privacy/network oracles rather than live model/provider output.

NFR-21: Local product entrypoints run the complete host-compatible automated quality, unit, build, and UI-acceptance matrix. GitHub Actions is an optional adapter that may run a reduced mobile JVM coverage workflow; hosted six-job aggregates, rulesets, same-SHA governance, and merge-queue enforcement are not product gates. A missing, skipped, neutral, cancelled, timed-out, action-required, stale, startup-failed, runner-unavailable, or failed required local/device claim cannot be represented as success.

NFR-22: The repository is a monorepo with separate root product boundaries at `mobile/`, `web/landing/`, and `backend/mobile-api/`. Each product independently owns its source, tests, dependency installation and lock state, cache, quality entrypoint, artifact, deployment, credentials, rollback, and distinct CODEOWNERS principal; source, build, package-manager, generated-code, runtime, and deployment dependencies cannot cross product boundaries. Cross-product integration uses only immutable, versioned, language-neutral releases independently owned under `contracts/`; producers and consumers test every supported released-version combination rather than only repository HEAD. `web/landing/` has no runtime dependency on the mobile backend. Root policy and contract compatibility checks are unavoidable inputs to the six-job aggregate on every branch push, pull request, and enabled merge-queue commit.

NFR-23: Product identity is exact and testable: installed app, launcher label, and in-app brand are `SuperNutrition`; the Apple App Store and Google Play listing title is `SuperNutrition AI nutritionist`. The descriptor is store metadata only. Protected identity metadata and CI assertions prevent platform, build-profile, locale, landing-page, and store-listing drift.

NFR-24: Product journeys minimize interaction while preserving safe user control. Happy paths use the fewest practical taps, scrolls, fields, and choice surfaces; only fields named by the protected `mobile/config/quality/interaction-policy.toml` allowlist may be prefilled from the immutable `ConfigurationSnapshot` or the approved post-confirmation catalog serving source. The snapshot materializes locale, catalog, device capability, and policy values before the reducer runs; the catalog source may supply one atomic standard-serving unit/amount pair only after canonical confirmation and never mutates the snapshot. The policy defines source precedence, independent CatalogCountry/MarketJurisdiction/ProcessingRegion/locale validity, provenance, and persistence scope. Every default is visible, labeled with its source/provenance, editable, reversible, and distinct from measured or inferred facts. Food/preparation/serving corrections remain session-scoped in the MVP; no future persisted correction preference is defined by this contract. A first-use catalog value is an unaccepted proposal until the user confirms it and cannot auto-advance into grounding or remote transfer. Auto-advance is limited to reversible local/UI steps. Approved policy may resolve market jurisdiction and processing region, but the app must show those values before transfer and must not derive or silently change them from locale or catalog; the user consents to the resulting upload rather than selecting a provider region. Medical/allergen conclusions are prohibited and cannot be enabled by confirmation; materially uncertain food identity requires an honest question or Abstention. Journey tests record interaction budgets, default provenance, override paths, bounded auxiliary events, and negative cases for forbidden defaults.

NFR-25: Deterministic catalog search is reproducible and bounded. The same query against the same package version returns byte-identical result ordering across platforms and runs, with no personalization, history, or learned component; a declared latency budget is met on the lowest supported device tier with network disabled and every Engine reporting unavailable. Golden query fixtures pin expected canonical IDs and order per package version.

NFR-26: Language support is a per-market matrix, not a global switch. The first three targets are English, Spanish, and Russian, which between them exercise every normalization profile: none beyond plurals, light stemming with diacritics, and full lemmatization with cross-script entry. Each supported market declares catalog language, accepted input languages, script, morphological normalization, and synonym data as versioned package data rather than search-code conditionals, and ships its own query fixtures keyed per catalog-and-input-language pair, because an accepted input language does not imply a catalog written in it. Regional name variants of one food resolve to one canonical ID rather than selecting a dialect per market. English-only behaviour is not a valid default for a market whose catalog is not in English, and a market added without fixtures is treated as untested. Accepted input languages are independent of market processing position in both directions: supporting a language is a product capability, operating in a market is a legal position, and neither implies the other.

NFR-27: Remote processing of user content is permitted only where a named, approved, expiring legal position says it is. Every market defaults to unresolved and permits no remote transfer of any modality; absence, unresolved, and expiry are equivalent at resolution time. Photo and text permissions are independent, each bound to its own reviewed consent copy. Catalog choice cannot derive or change jurisdiction, posture, permitted regions, or permissions, and the resolved jurisdiction and processing region are shown before every consent decision. The deterministic search floor and on-device identification never depend on a market posture, so a fail-closed market remains usable.

### Additional Requirements

- AR-1 (AD-16): The mobile scaffold story must create the greenfield KMP scaffold as one atomic pinned tuple: Kotlin/KMP 2.4.10, Compose Multiplatform 1.11.1, Gradle 9.5.0, AGP 9.1.0, JDK 21, Android compile/target SDK 36 with API 23 floor, the documented Xcode/iOS compatibility target, Detekt/ktlint wrapper 2.0.0-alpha.5, and Kover 0.9.8. Common, Android, lint, and local coverage evidence are required for activation; Apple/Xcode execution is an explicit good-to-have record when the host supports it. Any tuple change is a dedicated compatibility change.
- AR-2 (AD-25): Inside `mobile/`, create `apps`, `core`, `domain`, `data`, `inference`, `platform`, `feature`, `testing`, and `evaluation`, plus mobile `build-logic`, `config`, and `gradle`, using the exact structural seed in the architecture spine. The mobile backend is `backend/mobile-api/`, not a mobile Gradle group.
- AR-3 (AD-1, AD-25): Enforce inward mobile dependencies. `mobile/domain` is pure KMP and cannot depend on Android, Apple frameworks, Compose, Koin, Ktor, Ktorfit, or SQLDelight; distributable production configurations cannot depend on `mobile/testing`. Only the two manifest-listed UI-acceptance configurations may resolve exact allowlisted test-support edges.
- AR-4 (AD-26): Centralize mobile versions and SDK/JVM floors in `mobile/gradle/libs.versions.toml`; use `mobile/build-logic` convention plugins so module scripts declare only identity, targets, and dependencies. Each monorepo product owns one central dependency/tool manifest, frozen install, lock/verification state, and product-local resolution tree; no root workspace, lockfile, virtual environment, or hoisted dependency tree may span products.
- AR-5 (AD-26): Settings reject project repositories and enforce strict dependency verification and per-project/configuration lock state across Gradle, included builds, native/Xcode, gateway/container, model, and catalog inputs. CI never writes metadata; dynamic/range/snapshot/file/URL dependencies are forbidden.
- AR-6 (AD-27): Provide read-only mobile `qualityCheckJvmAndroid` and `qualityCheckApple`, plus lifecycle-aware `web/landing/quality-check` and `backend/mobile-api/quality-check` entrypoints. Active products own complete compile/build, lint, unit, coverage, dependency, artifact, and inventory evidence; preselection products prove no production material exists. `quality-linux` unconditionally owns the same-SHA root-boundary/catalog result and mobile released-contract matrix; backend quality owns its matching released-contract matrix.
- AR-7 (AD-27): Provide `qualityFormat` as the only mutating formatter/Detekt-autocorrect entrypoint, using the same pinned ruleset as checks. Suppression registry entries require exact rule/symbol, independent approval, issue, creation/expiry, count, and removal condition; safety-sensitive areas cannot add suppressions.
- AR-8 (AD-27): Use exact fully qualified, evidence-backed entries only in the Compose stability configuration; wildcard stability entries are forbidden.
- AR-9 (AD-16, AD-27): Run each available host task twice with configuration-cache problems failing and prove store then reuse. Required Android device behavior remains a separate real-device gate; Apple/Xcode execution is good-to-have and unavailable Apple capability is recorded without becoming a local activation blocker. A skipped, missing, neutral, timed-out, or unavailable required status blocks the corresponding claim.
- AR-10: Use Detekt and its ktlint-wrapper ruleset at 2.0.0-alpha.5 because the GiphySearch 1.23.8 version targets the older Kotlin 2.0/Gradle 8 generation; track replacement by the first compatible stable Detekt 2.x. Use Kover 0.9.8 only for an explicit JVM/Android-host merge project/variant and publish Native/device evidence separately.
- AR-11 (AD-17, AD-28): `mobile/domain/inference` owns immutable versioned canonical fixtures; `mobile/testing/inference` owns only the suite runner, fakes, and builders; adapter-specific fixtures stay with each adapter. `mobile/testing/compose-support` owns only the v2 base robot DSL/host/synchronization/reporting utilities; concrete robots remain in `mobile/feature/photo-analysis/src/commonTest`.
- AR-12 (AD-2, AD-17): `mobile/domain/inference` owns only `PhotoInferenceEngine` contract v1, immutable evidence/capability types, serialized fields, compatibility, canonical fixtures, and telemetry schema; it contains no routing or consent policy.
- AR-13 (AD-3): Keep `mobile/inference/local-android`, `mobile/inference/local-ios`, and `mobile/inference/remote` independent with separate builds, tests, fixtures, owners, stories, and pull requests; they depend on the shared Engine contract but never on one another.
- AR-14 (AD-4): Implement independently testable `mobile/platform/photo-android` and `mobile/platform/photo-ios` camera/picker/permission adapters. Keep platform image/framework objects out of app hosts and shared code; common code uses an opaque leased `PhotoRef` and `PhotoContentSource`.
- AR-15 (AD-5): Implement one sealed immutable reducer in `mobile/domain/photo-analysis` as the sole workflow mutator and owner of `RoutePolicy`, consent/fallback decisions, and attempt coordination through a one-way dependency on `mobile/domain/inference`; Decompose owns lifecycle/routes only.
- AR-16 (AD-6): Make routing a pure decision over capability, policy, one configuration snapshot, explicit action-scoped remote consent, and attempt token. Ready local runs first by default, one Engine is active at a time, and exactly one terminal outcome wins.
- AR-17 (AD-6): Model remote work as `PreTransfer`, `Transferring`, and `Transferred` with exact cancellation behavior, no retry/upload loops, and suppression of late or cancelled results.
- AR-18 (AD-7, AD-19): Inference emits identity evidence only. `mobile/domain/nutrition` owns canonical vocabulary and math contracts; `mobile/data/nutrition` is the SQLDelight adapter and sole numeric nutrition authority.
- AR-19 (AD-7, AD-19): Nutrition packages are signed, checksummed, licensed, schema-compatible, staged, atomically activated, rollback-capable, and fixture-tested; each active country profile retains an approved bundled or cached offline-searchable package.
- AR-20 (AD-19): Use namespace-qualified food/serving IDs, SI-normalized decimal conversion, defined source and calculation precision, display rounding, aggregation, and golden fixtures for units, ranges, boundaries, and partial items.
- AR-21 (AD-8, AD-18): Scope image bytes to an attempt lease, strip EXIF/GPS and non-essential metadata before creating a consent receipt or remote payload, encrypt unavoidable temporary persistence, log no image data, revoke and clean up idempotently on every terminal path, and prevent duplicate transfer-start effects with stable attempt/effect IDs.
- AR-22 (AD-8): Bind remote consent to the digest of the scrubbed normalized photo payload, purpose, market jurisdiction, processing-region/provider policy, retention version, configuration version, and transfer state. Cover deletion across device, gateway, provider, temporary files, caches, queues, logs, backups, and failure paths with honest limits and evidence.
- AR-23 (AD-9, AD-22): Identify every model/provider configuration by immutable version, checksum/deployment digest, compatibility predicates, activation time, and rollback target. Fix preprocessing, color space, dimensions, taxonomy, and golden pixel/output parity in the model manifest.
- AR-24 (AD-10): Start Koin only in app roots, use constructor injection and explicit `localEngine`/`remoteEngine` qualifiers, and prohibit global Koin access in domain, feature, and Engine code.
- AR-25 (AD-11): Every Engine and gateway adapter passes the same contract suite. Physical-device evidence is required for camera, performance, memory, energy, accessibility, and local-inference claims.
- AR-26 (AD-12, AD-20): Treat catalog country, market jurisdiction, processing region, and language as independent axes. A signed `ConfigurationSnapshot` resolves bundled safe default, global release, environment, then independent catalog/jurisdiction overrides and remains atomic for one attempt; a catalog choice cannot select a provider region.
- AR-27 (AD-13, AD-21): Mobile clients call only the environment-bound first-party gateway. Use short-lived scoped authenticated sessions, request digests/nonces, one-time IDs, replay and abuse protection, key rotation/revocation, and no provider credentials or schemas in mobile code.
- AR-28 (AD-14): Use a versioned telemetry allowlist only. Server-side correction telemetry remains disabled until G-6; filenames, paths, images, free-form labels, secrets, headers, and request/response bodies are forbidden.
- AR-29 (AD-23): The Compose/native bridge contract includes lifecycle, disposal, permission, focus/zoom, rotation, spoken framing/quality cues, captured-image description, focus restoration, semantic roles, contrast, text expansion, bidi, localized values, reduced motion, and target sizes.
- AR-30 (AD-24): Production gateway enablement requires named ownership, SLOs, alerts, deletion and user-rights evidence, cost/rate dashboards, jurisdictional data-flow inventory, incident runbook, provider kill switch, configuration rollback, and a recorded rollback drill.
- AR-31 (AD-15): Agent work is bounded to one story and exactly one closed-set ownership slice: a product module with colocated tests, platform host/source-set with colocated tests, protected root-policy slice, or one enumerated mobile infrastructure-policy exception. No story-wide invocation is valid. The current story and cited architecture decisions override vendored prompts; cancellation is rethrown, photo bodies are never logged, and generated adapters/data sources include tests.
- AR-32: Use the vendored `kotlin-multiplatform-developer` as the primary implementation agent and `builder-compose-feature` only after UX-A1 and the five load-bearing mocks are approved.
- AR-33: Implement one pull request per story. Land the shared contract before concrete Engine work; contract-breaking changes require a new contract version and migration story.
- AR-34: Do not begin release-ready country behavior, nutrition packages, local engines, remote provider work, confidence routing/accuracy claims, or correction retention until Gates G-1 through G-6 respectively provide their named evidence.
- AR-35 (AD-10, AD-25): Keep mobile app modules thin: `mobile/apps/androidApp` solely bootstraps Android Koin, `mobile/apps/sharedKit` exports the one idempotent iOS composition/bootstrap API, and the thin iOS host invokes it once without owning adapters or starting Koin.
- AR-36 (AD-29): Define one protected root product-inventory schema, with `mobile/config/quality/module-matrix.toml` as the mobile instance and schema-conforming web/backend inventories on activation. Reconcile every deployable module, package, worker/function, migration executable, generated runtime source, test, coverage report, artifact, owner, and quality task. CI fails undeclared/merged-away units, source-to-artifact reclassification, missing, disabled, ignored, filtered-to-zero, skipped, `NO-SOURCE`, empty-module, zero-test, zero-denominator, unmapped, or artifact-less work.
- AR-37 (AD-30): Create `mobile/config/architecture/module-boundaries.toml` for every Gradle/Xcode module, test executable, owner, package/path, edge, and forbidden import. Root boundary checks and a protected ownership manifest separately protect mobile, web landing, mobile backend, contracts, and policy/CI; mobile, web, backend, contract, and repository-policy principals are distinct and one approval cannot satisfy multiple roles.
- AR-38 (AD-31): Run a non-waivable production-adapter safety suite for state transitions, late emissions, fault/property cases, transfer-state cancellation, duplicate effects, restoration, retry/idempotency, consent revocation, zero-byte local paths, forbidden logs, lease cleanup, replay, and deletion requests; coverage cannot replace these assertions.
- AR-39 (AD-28, AD-32): Add negative Gradle TestKit fixtures for every build-policy invariant, reconcile compiled production sources/classes with coverage input, and scan every distributable Android, iOS, framework, and gateway artifact to prove no test support ships. Create unique-ID, non-distributable Android/iOS UI-acceptance executables that retain production UI/state/navigation/composition and replace only allowlisted ports, plus a real release-composition smoke and scans for credentials/forbidden endpoints.
- AR-40 (AD-33): Assign every compiled production source/class in all three product roots to exactly one denominator. Enforce at least 85% unit-test line and branch coverage per module and per product's deduplicated aggregate, plus 100% for cumulative changed production lines and branch outcomes. Every executable outside a product root belongs to the protected policy-tool inventory with tests and 100% changed line/branch coverage. Reject missing/zero/duplicate/unmapped reports, optional flags, shallow/ambiguous diffs, line-only results, and unapproved Native/hardware classifications.
- AR-41 (AD-34): Use a protected monotonic mobile UI evidence profile. Scaffold activation selects `bootstrap`, requiring only a non-product scaffold-probe robot and cross-platform packaged launch/DI smoke with no store artifact or UJ claim. After reducer/ports and dependencies land, the first photo-analysis source atomically selects `photo-analysis` through separate invocations for the feature, core UI-ID registry, mobile-build-policy registration, mobile-quality-policy profile/catalogs, mobile-ui-acceptance-policy flows, sharedKit, Android host, and iOS host. The combined candidate must compile/install/test the inventoried graph on both platforms. That profile executes feature robots through JVM/Android/iOS, maps every qualifying UJ-1 through UJ-4 and SM-9 scenario once to Maestro 2.7.0 on both platforms, and links binary-bound/native-gap evidence. CI enforces final assertions, mappings, fingerprints, and freshness.
- AR-42 (AD-35): Check in an optional GitHub Actions adapter for every branch push and pull request without filters. When hosted, it may run four mobile jobs plus `quality-web-landing` and `quality-backend` regardless of changed paths, with `ci-required` directly needing exactly all six and validating same-SHA root boundary/tool inventory, product lifecycle/inventory, contract catalog/released-version matrix, and product/flow evidence. Local policy tests reject skipped/neutralized work, unsafe permissions/events, floating dependencies, quarantine, and assertion retries; hosted required-check enforcement is optional.
- AR-43 (AD-36): Enforce the only product roots as `mobile/`, `web/landing/`, and `backend/mobile-api/`, with independent dependency installs, locks, product-ID-scoped caches, artifacts, deployment workflows/environments/credentials, rollback, and distinct owners. `contracts/` is non-executable data authored and published only by an independent contract owner; mobile and backend consume the same immutable release and test the complete supported version-skew matrix. Reject cross-root source, workspace/package, generated-code, runtime/config, build, cache, artifact, and deployment edges. The landing has no backend runtime dependency. Root tooling is policy-only and inventoried; every executable is mapped and tested. Incompatible contracts require a major version, coexistence, server-first activation, minimum-version policy, rollback, and removal story.
- AR-44 (AD-37): Start mobile in local fail-closed `scaffold-pending` and web/backend in `preselection`. Every local quality entrypoint proves no production source, dependency installation/lock, runtime, deployable artifact, or deployment has appeared and may emit exact-state evidence without claiming build/test/coverage/UI. Mobile moves irreversibly and atomically to `active` through its reviewed scaffold story with a nonempty inventory and real non-product bootstrap host/device/app evidence under the AD-34 bootstrap profile; full product journeys become mandatory only with the irreversible feature-profile transition. Web/backend activate through reviewed pinned-toolchain stories adding frozen dependencies, nonempty inventories, real build/lint/test/coverage/artifact gates, owners, and deployment/rollback skeletons. Hosted GitHub execution is optional.
- AR-45 (AD-38): Treat GitHub rulesets, required checks, merge queues, and trusted policy auditors as optional hosting enhancements. Checked-in workflow YAML, CODEOWNERS, and local validators are declarations and must not claim external enforcement. No GitHub plan capability is required for lifecycle activation, production implementation, or release readiness.
- AR-46 (AD-39): Add protected `mobile/config/product-identity.toml` with exact canonical installed/in-app name `SuperNutrition` and exact Apple App Store and Google Play listing title `SuperNutrition AI nutritionist`. Generate or validate Android labels, iOS display-name metadata, `SuperNutritionUIAcceptance`, web metadata, and store-delivery metadata from it; test all profiles/locales and reject independently hard-coded variants. Select package and bundle identifiers in the scaffold story without changing visible identity.
- AR-47 (AD-40): Make interaction budget a protected cross-product contract. Every product conforms to immutable `contracts/interaction-policy/v1` and creates/validates its product policy (mobile: `mobile/config/quality/interaction-policy.toml`) with an allowlist of defaultable fields, source precedence, independent CatalogCountry/MarketJurisdiction/ProcessingRegion/locale validity, provenance types, persistence scope, invalidation keys, complete lifecycle and atomic serving rules, cumulative required dimensions, bounded auxiliary events, and locale/accessibility evidence matrix. Every user-interaction-affecting story records tap, scroll, field, choice, text-entry, choice-surface, recovery-event, and default/override budgets; non-UI stories attest that they do not alter the budget or default policy. Food/preparation/serving corrections are session-scoped in the MVP. Approved policy may resolve jurisdiction/processing values, which must be shown before consent, but locale/catalog cannot derive or silently change them and users do not select provider routing. Auto-advance is limited to reversible local/UI steps; medical/allergen conclusions are prohibited and materially uncertain identity requires an honest question or Abstention. Negative tests reject unallowlisted, stale, hidden, digest/value-mismatched, non-atomic, or persisted sensitive defaults.

- AR-48 (AD-2, AD-17, AD-36): Carry identification modality in a new major contract release. `contracts/inference/v1` shipped in story 3.1 as an immutable digest-pinned release whose `contract_id` is `photo-inference-engine`, whose request port is `opaque-photo-ref-plus-immutable-attempt-context`, and whose own compatibility rule declares `incompatible_change = "new-major-release"`. Modality is therefore added as `contracts/inference/v2` with a migration story under AR-33; v1 is never edited and its digest never changes. v2 takes a modality-neutral `contract_id`, and its input is exactly one of a `PhotoRef` or a text description plus input locale, selected by a closed sealed discriminator, with neither-nor-both failing closed as a typed contract error. Output shapes, capability states, abstention, provenance, and the no-numeric-nutrition rule are identical across modalities and unchanged from v1. The compatibility matrix declares both releases; every v1 canonical fixture continues to pass against v1; producers and consumers migrate release by release, and v1 is retired only when no implementation remains.
- AR-49 (AD-7, AD-19): No Engine binds a canonical catalog record. Text and photo Engines alike return identification evidence only; mapping evidence to a canonical record requires an explicit user confirmation event, including at maximum confidence. Negative tests prove no path reaches a Grounded Nutrition Result from Engine output alone, and that a description the Engine cannot segment abstains rather than returning a confident subset as a whole meal.
- AR-50 (AD-12, AD-19): Own the per-market language matrix as signed package data. Each market declares catalog language, accepted input languages, script, morphology, and synonyms; normalization is applied identically at index build and query time; markets with inflected catalog languages declare full morphological normalization because synonym enumeration per inflected form is not maintainable. Cross-script entry feeds the fuzzy tier only and never outranks an exact match in the record's own script.
- AR-51 (AD-8, AD-12, AD-21): Bind text transfer to text-specific consent copy. `consentCopyVersion` currently denotes the reviewed photo-transfer explanation, whose approved copy states a smaller copy of the photo is sent; that disclosure cannot authorize sending a description. A text transfer presents its own reviewed copy version and fails closed on mismatch. Processing region resolves from market policy and never from the catalog; where a market's data-residency position is unresolved, remote text is disabled for that market rather than defaulting to the nearest provider region.
- AR-52 (AD-8, AD-14, AD-31): Treat user-authored text on the photo lifecycle. A description is attempt-scoped, cleared with the attempt, never logged, never placed in analytics, and never sent remotely without the AR-51 consent receipt. Redacted-artifact and forbidden-log capture tests extend to the text field.

- AR-53 (AD-41): Own market processing position as a protected cross-product contract. `MarketPolicyResolver` resolves only against signed data conforming to immutable `contracts/market-policy/v1`, created by a separately owned `market-policy-contract` slice, with the mobile instance at protected `mobile/config/quality/market-policy.toml`. Each market declares jurisdiction, residency posture, permitted processing regions, per-modality remote permission and consent-copy version, accepted input languages, and a named legal review with approver and expiry. Unresolved is the default and equivalent to absent and expired; no posture is inferred from a neighbouring market, shared language, currency, or region grouping, and none is widened as a side effect of a story, configuration change, or provider migration. Negative tests prove an absent, unresolved, or expired market cannot reach transfer for either modality; that a photo-permitted, text-prohibited market rejects text transfer; that presenting one modality's consent copy for another fails closed; that catalog change leaves posture and regions unchanged; and that input languages resolve identically regardless of posture. CI never writes policy state.

### UX Design Requirements

UX-DR1: Compose Multiplatform owns every product screen on Android and iOS; native CameraX/AVFoundation/PhotosUI, permission, picker, and host surfaces are bridges only and must not become parallel product UIs.

UX-DR2: Implement one stateful photo-analysis surface whose capture state becomes preparation/analyzing, Candidate review, and its in-place nutrient profile without replacing the captured photo. Permission rationale, Food search, Country and catalog, optional Serving editor, Recovery, and debug-only diagnostics support that free/default flow; no tab bar, drawer, account, diary, or general settings hierarchy. Paid Remote consent is a separately deferred E4 surface and is absent from this flow.

UX-DR3: Implement the Photo Analysis experience as one component state machine. Native Back/edge gestures return to the last safe state or require confirmation before discarding data and never submit, upload, or save.

UX-DR4: Externalize calm, direct, nonjudgmental copy that names uncertainty, estimates, upload, route, and recovery plainly and avoids medical claims, blame, praise, guilt, exaggerated privacy, or “AI knows” language.

UX-DR5: Treat the muted natural-green palette as UX-A1, a working assumption that Product/Design must approve through brand, contrast, and usability review before the first visual UI story is ready.

UX-DR6: Implement the semantic light and dark surface, ink, accent, border, warning, error, scrim, and on-photo color tokens in DESIGN.md, including explicit dark component variants; never use accent as a health/correctness grade.

UX-DR7: Test on-photo controls over representative images for 4.5:1 text and 3:1 essential-icon-boundary contrast and move failing controls to opaque surfaces rather than weakening contrast.

UX-DR8: Use Compose Material 3 semantic typography with platform system fonts, dynamic type/font scaling, body-scale tabular nutrition figures, sentence case, and no ellipsis of essential labels, actions, or nutrition values.

UX-DR9: Implement the 4/8/12/16/24/32 spacing scale, at least 16dp phone content padding, at least 24dp between decision groups, edge-to-edge Capture, readable single-column decision surfaces, safe insets, and keyboard-safe primary actions.

UX-DR10: Layouts reflow for large text, at least 30% English text expansion, and bidirectional leading/trailing alignment; photos, numeric values, and capture orientation do not mirror semantically.

UX-DR11: Implement a labeled circular Camera shutter as the primary Capture action, a secondary system-import action, and visible initializing, permission, loading, ready, disabled, and unavailable states without a dead viewfinder.

UX-DR12: Implement Primary action and Secondary action components from DESIGN.md with 48dp minimum Android height and platform target guidance, at most one primary action per decision surface, and multi-line label growth.

UX-DR13: Implement Candidate cards/lists with food name, plain-language Confidence, route, serving assumption, alternatives, remove/edit actions, initial ranked order, and user-owned order after edits.

UX-DR14: Implement Nutrition cards/result summaries with confirmed foods and servings before nutrients, body-scale numbers, visible estimate language, source/version, uncertainty, and accessible provenance.

UX-DR15: Implement text-semantic State chips/route indicators for Local, Remote, Manual, Needs review, and Offline; state can never rely on color or technical IDs.

UX-DR16: Implement Analyzing as a frozen photo, neutral low-opacity scrim, one announced indeterminate progress label, visible route, and Cancel, without countdown, pulsing urgency, repeated announcements, or anxious animation.

UX-DR17: For the later paid E4 release only, implement the Remote consent sheet with purpose, data sent, retention, provider category/region, alternatives, cancellation limits, and policy detail. It is unreachable without entitlement and an explicit paid-route action; “Continue without upload” precedes “Send photo for remote analysis,” both use equal unfilled visual/focus treatment, and neither is preselected.

UX-DR18: Implement the Country and catalog sheet as contextual configuration available on first use, Capture, and Food search. Suggest a system-region catalog proposal, require confirmation, persist only the confirmed `CatalogCountry` for the next attempt, show current catalog, and provide Not listed/manual recovery without silently choosing the United States; never persist or let the user choose `MarketJurisdiction` or `ProcessingRegion`.

UX-DR19: Implement country-aware Food search that retains source/version, supports correction and no-Engine recovery, exposes None of these/no-match paths, and withholds numeric nutrition until a record is selected.

UX-DR20: Implement the Serving editor for amount, unit, optional range, and contextually relevant recipe/oil/sauce questions; validation preserves user input and never describes an inferred portion as measured fact.

UX-DR21: Implement one-level Privacy detail and Source detail sheets. Privacy detail covers purpose, data categories, retention, provider category/region, cancellation limits, and policy link; Source detail covers source name/version, country coverage, serving conversion, and estimate disclaimer.

UX-DR22: Implement a debug/internal-only diagnostics panel containing redacted route, capability, version, timing, and error fields; its copy action excludes photos and sensitive payloads.

UX-DR23: Implement typed Recovery panels with no more than three relevant next actions and no generic Retry when state cannot change; every poor image, capability, model, network, consent, parsing, grounding, and unexpected state has a non-dead-end route.

UX-DR24: Implement explicit poor-image, model-required/downloading, local-unavailable, low-Confidence/Abstention, remote-declined, timeout/rate-limit/offline, Candidate ambiguity, source miss, empty Candidate list, cancellation, and completed treatments exactly as specified in EXPERIENCE.md.

UX-DR25: For the later paid E4 release only, implement action-scoped transfer behavior across consent accepted before transfer, transfer in progress, and transfer completed, using copy and effects that never claim already-sent bytes were recalled.

UX-DR26: Implement the full transition table in EXPERIENCE.md, including single invocation of native permission/local analysis/remote transfer, request-ID invalidation, old-Candidate clearing on replacement, preservation of user edits, and cleanup of unnecessary Photo Input artifacts after grounding.

UX-DR27: Support tap, native pinch-to-zoom/tap-to-focus, keyboard Next/Done, explicit accessible removal, and optional restrained confirmation haptics; no required swipe or gesture-only action.

UX-DR28: Ban automatic remote upload, preselected consent, progress countdowns, calorie totals and score rings in the core UI, infinite carousels, gesture-only controls, modal stacks deeper than one, gradients, confetti, streaks, and celebratory or disciplinary nutrition feedback.

UX-DR29: Label every capture control, Candidate, edit action, route, uncertainty, progress state, and nutrition value for VoiceOver and TalkBack; focus follows reading order and moves to the first changed heading when state advances.

UX-DR30: Meet at least 44pt iOS and 48dp Android targets, support Reduce Motion, test high contrast/dark mode over real photos, and provide fully labeled import/manual-search alternatives to live camera use.

UX-DR31: After capture, screen readers announce photo readiness and specific blur, darkness, or no-food guidance; Analyzing announces once and animations do not repeatedly speak.

UX-DR32: Preserve native preview, picker, permission, Back, safe-area/inset, font-scale, and Dynamic Type behavior while sharing unit/locale formatting and the product workflow.

UX-DR33: Keep phone portrait primary, but make landscape, tablet, and foldable layouts functional through reflow and safe insets without requiring custom large-screen layouts in the first release.

UX-DR34: Store canonical quantities separately from localized display units, support metric weights throughout the Tier 1 reference cohort, use configuration-backed jurisdictional privacy copy, and include regional foods in evaluation and empty-state examples.

UX-DR35: Before visual Compose implementation, approve responsive/accessibility-annotated mocks for the unified photo-analysis surface (Capture → retained photo → Candidate review → in-place nutrient profile) and the optional Serving editor; DESIGN.md and EXPERIENCE.md remain authoritative if a mock conflicts. Paid Remote consent needs its own later E4 approval and is not part of the free/default visual gate.

UX-DR36: Render `SuperNutrition` as the installed, launcher, accessibility-product, and in-app brand. Use `SuperNutrition AI nutritionist` only in Apple App Store and Google Play acquisition metadata; never repeat the store-only descriptor in app chrome or ordinary product copy.

UX-DR37: Apply a low-friction interaction budget to every screen and positive journey. Prefer one clear primary action on ordinary decision surfaces, inline visible defaults, and Edit/Undo over choice-first sheets, unnecessary scrolling, or repeated typing. Later paid Remote consent is an explicit exception: its actions remain equal and unselected as specified in DESIGN.md. Label allowlisted deterministic defaults with their source, preserve user overrides within approved scope, and auto-advance only reversible local/UI steps. A standard serving default is one atomic unit-and-amount proposal and is available only after canonical-item confirmation from the catalog source. Never preselect remote consent/upload or materially uncertain food identity; medical/allergen conclusions and health-effect claims are prohibited; show policy-resolved processing jurisdiction before later paid consent, never derive or silently change it from locale/catalog, and do not make users select provider routing. Test defaulted, override, and forbidden-default paths on Android/iOS with accessibility semantics intact.

UX-DR38: Implement a text-entry path into identification from the photo-analysis capture/review states and every Recovery surface that currently offers manual search. Show which language the catalog is being searched in rather than implying it follows the app language, and keep entry reachable in one action whenever an Engine is unavailable, abstains, or is declined.

UX-DR39: Present search results with a stated maximum count rather than infinite scroll, showing for each result the qualifier that distinguishes it from its near-neighbours. On no match, withhold numbers and offer at most three next actions — refine, change country/catalog, or end — and never a nearest guess or a Retry that cannot change the outcome.

UX-DR40: Present a proposed multi-item split as an editable review, showing which words produced which item, with merge, split, remove, and add available before any item is confirmed. A dropped or invented item must be visibly correctable; silent reduction of a described meal is forbidden.

UX-DR41: Give remote text transfer its own reviewed consent copy naming what a description is and where it is processed; the photo-transfer copy must not appear on a text transfer. Consent stays per-attempt, unselected, and equal-prominence with continue-without-upload, and the resolved processing jurisdiction is shown before transfer.

### FR Coverage Map

| Epic | Primary requirements | Outcome |
| --- | --- | --- |
| E1 Foundation and product shell | FR-8, FR-22, FR-25, FR-30, NFR-17..NFR-24, NFR-26..NFR-27, AR-1..AR-7, AR-31..AR-47, AR-51, AR-53, UX-DR35..UX-DR37, UX-DR41 | A bounded, identity-safe monorepo, reproducible KMP substrate, and fail-closed market policy |
| E2 Capture and photo preparation | FR-1..FR-4, FR-19..FR-21, NFR-1..NFR-3, NFR-9..NFR-16, NFR-24, AR-47, UX-DR1..UX-DR12, UX-DR26..UX-DR34, UX-DR37 | A privacy-preserving camera/import session with deterministic recovery |
| E3 Shared inference and local engines | FR-5..FR-7, FR-9, FR-12, FR-23..FR-24, NFR-2..NFR-3, NFR-7, NFR-9..NFR-12, NFR-24, AR-11..AR-23, AR-47, UX-DR37 | Interchangeable contract-v1 local inference on Android and iOS |
| E4 Remote inference and consent | FR-8, FR-10..FR-12, FR-22, FR-25, NFR-4..NFR-8, NFR-11, NFR-24, AR-8..AR-14, AR-47, UX-DR17, UX-DR25, UX-DR37 | Explicit, bounded, region-aware remote analysis through the gateway |
| E5 Confirmation and grounded nutrition | FR-13..FR-18, FR-20..FR-21, NFR-6, NFR-10, NFR-12, NFR-15..NFR-16, NFR-24, AR-19..AR-30, AR-47, UX-DR13..UX-DR24, UX-DR37 | User-confirmed foods and serving assumptions become sourced estimates |
| E6 Journey evidence and evaluation | FR-22..FR-24, NFR-17, NFR-20..NFR-21, NFR-24, SM-1..SM-10, AR-38..AR-47, UX-DR26..UX-DR37 | Shared Android/iOS acceptance journeys and evidence gates |
| E7 Typed meal identification | FR-3, FR-14, FR-20..FR-21, FR-26..FR-31, NFR-24..NFR-27, AR-48..AR-53, UX-DR19, UX-DR38..UX-DR41 | A specified manual-search floor on every device, with optional on-device and remote typed identification above it |

### Delivery order

E1 freezes the shared contract boundaries, toolchain, identity manifest, and
quality policy. E2 can then deliver capture and photo preparation independently
of model providers. E3 and E4 are parallel implementation tracks that integrate
only through the E1 contract. E5 consumes candidate evidence and the approved
catalog. E6 can build the fixture and journey harness alongside E2-E5, but its
profile transition is only allowed when the production graph is present. Each
story is one bounded agent slice and one pull request, as required by AR-31 and
AR-33.

The approved ingredient/grams direction prioritizes E3.5 before further engine
work, E3.6 before any mass claim, E3.7 and E3.8 as independent physical-device
spikes, then E3.9, E5 component confirmation, and E6 v2 evaluation. The v1
Food-101 adapter may remain an explicit single-dish fallback but cannot close
these stories.

E7 is sequenced around one product decision and one decaying cost. The decision:
E7.2 delivers the deterministic search floor first, depends on the catalog
rather than on any Engine, and is therefore unblocked by the contract question
entirely; it proceeds in parallel with E3 and E4 and ships before the model
path. E7.3 through E7.6 build above the floor and degrade to it whenever an
Engine is unavailable, abstains, or is declined.

The decaying cost: E7.1 was scoped to amend the contract before story 3.1 froze
the ports. Story 3.1 has shipped, `contracts/inference/v1` is immutable and
digest-pinned, and its own compatibility rule requires a new major release for
an incompatible change — so E7.1 is now a v2 release plus migration. Exactly one
consumer implements v1 today. Every subsequent Engine story adds another, so the
migration cost rises monotonically until v2 lands. Deciding whether typed
identification is happening is therefore time-sensitive even though no story is
blocked on it.

## Epic E1: Foundation and product shell

Create the fail-closed monorepo substrate, KMP toolchain, product identity, and
quality policy that every later story must consume.

#### 2026-07-29 delivery-policy amendment

The Apple Silicon runner, exact Xcode tuple, iOS simulator execution, and
physical-iOS native gaps are good-to-have evidence rather than activation
requirements. Local policy, inventory, JVM coverage, and required Android
device evidence remain authoritative; optional Apple records stay explicit and
never count as successful evidence while pending.

### Story E1.1: Activate the mobile KMP scaffold

As the mobile platform owner,
I want a pinned Android/iOS KMP scaffold with independently owned module roots,
so that feature agents can build against a reproducible substrate.

**Traceability:** NFR-9..NFR-10, NFR-17..NFR-21, AR-1, AR-15..AR-16,
AR-31, AR-35, AR-44.

**Acceptance Criteria:**

- **Given** the mobile lifecycle is `scaffold-pending`, **when** the scaffold
  story is merged, **then** the AD-16 tuple, version catalog, convention
  plugins, module-boundary inventory, and nonempty product inventory are added
  atomically.
- **Then** Android and iOS thin hosts, sharedKit, a non-product bootstrap
  acceptance host, and read-only quality entrypoints produce same-SHA evidence.
- **And** no production feature, store artifact, testing dependency, or web/
  backend source is introduced by this story.

### Story E1.2: Enforce monorepo boundaries and contracts

As a repository-policy owner,
I want root product boundaries and immutable contract releases enforced,
so that mobile, landing, and backend can be developed independently.

**Traceability:** FR-22, FR-25, NFR-17..NFR-18, NFR-22, AR-3, AR-15,
AR-36..AR-38, AR-43..AR-46, UX-DR35..UX-DR36.

**Acceptance Criteria:**

- **Given** `mobile/`, `web/landing/`, `backend/mobile-api/`, and `contracts/`
  are the only declared roots, **when** a forbidden source, workspace, cache,
  build, artifact, deployment, or credential edge is added, **then** the root
  boundary suite fails.
- **Then** the released mobile-api compatibility matrix tests supported
  producer/consumer version skew without importing another product's source.
- **And** root executables are enumerated in the protected policy inventory.

### Story E1.3: Establish product identity metadata

As a release owner,
I want one machine-readable identity manifest,
so that visible app names and store metadata cannot drift between platforms.

**Traceability:** FR-25, NFR-23, AR-39, UX-DR36.

**Acceptance Criteria:**

- **Given** AD-39, **when** `mobile/config/product-identity.toml` is added,
  **then** installed/launcher/in-app identity is `SuperNutrition` and both
  store listing titles are `SuperNutrition AI nutritionist`.
- **Then** Android labels, iOS display metadata, acceptance identities, web
  metadata, and store-delivery checks validate against the manifest.
- **And** package/bundle identifiers remain separate technical values.

### Story E1.4: Establish repository quality gates

As a quality owner,
I want read-only quality checks and every-push CI policy,
so that missing, skipped, or under-covered work cannot appear green.

**Traceability:** FR-22..FR-24, NFR-17..NFR-21, AR-10..AR-11, AR-27..AR-42,
UX-DR4..UX-DR12, UX-DR37.

**Acceptance Criteria:**

- **Given** the six product jobs are declared, **when** a branch push or pull
  request runs, **then** all six jobs and the unconditional aggregate execute
  without path filters.
- **Then** every compiled production source has one denominator, each module
  and product aggregate meets 85% line and branch coverage, and changed code
  meets 100% line and branch coverage.
- **And** `qualityFormat` is the only mutating formatter entrypoint.

### Story E1.5: Establish the low-friction interaction policy

As a product and UX owner,
I want every agent and screen to optimize the shortest safe path,
so that common meal scans do not become form-filling workflows.

**Traceability:** NFR-24, AR-47, UX-DR37.

**Acceptance Criteria:**

- **Given** AD-40/NFR-24/UX-DR37, **when** a story adds a screen or choice,
  **then** its acceptance criteria state tap, scroll, field, and choice budgets,
  the safe defaults used, and the default provenance/override path.
- **Then** `mobile/config/quality/interaction-policy.toml` defines the closed
  allowlist, source precedence, independent CatalogCountry/MarketJurisdiction/
  ProcessingRegion/locale validity, provenance types, and
  session/persistence scope for every defaultable field.
- **And** the initial allowlist is only `defaultLocale`, `measurementSystem`, a
  `catalogProposal`, and one atomic `standardServing` (unit plus amount) after a
  canonical item is confirmed; food identity, preparation, oil, sauce, recipe,
  remote consent, provider-routing choice, and medical/allergen fields are
  rejected as defaultable.
- **And** the immutable `ConfigurationSnapshot` materializes context defaults;
  the post-confirmation catalog serving source supplies only the atomic serving
  proposal and never mutates the snapshot. All allowlisted values are visible
  inline, labeled as defaults, editable, reversible, and retained only within
  their approved scope after correction; a separate picker is not required to
  accept them.
- **And** auto-advance is limited to reversible local/UI work. Remote consent or
  upload and materially uncertain identity remain explicit and have no
  preselected value; medical/allergen conclusions are prohibited; policy-resolved jurisdiction and
  processing region are shown before consent and cannot be derived or changed by
  locale/catalog or selected through a provider-routing picker.
- **And** the separately owned `interaction-policy-contract` invocation creates
  and versions `contracts/interaction-policy/v1`, its immutable fixtures,
  compatibility metadata, release digest, and validator; the mobile quality
  invocation consumes that release in `mobile/config/quality/interaction-policy.toml`.
- **And** core contract-neutral types (`ConfigurationSnapshot`,
  `MarketPolicyResolver`, `DefaultResolutionInput`, `DefaultResolution`, and
  `DefaultResolver`), domain lifecycle types (`DefaultProposal`,
  `DefaultDecision`), the atomic serving source port, and
  `InteractionBudgetEvidence` are delivered through separate AD-15 ownership
  slices. Tests cover proposal IDs, revision/digest preconditions, idempotency,
  invalidation precedence, complete lifecycle/Undo behavior, atomic serving,
  allowlist, provenance, persistence scope, first-use catalog confirmation,
  invalidation keys, cumulative counting taxonomy, bounded auxiliary events,
  per-journey maxima, schema/policy/config/value digest binding, and
  forbidden-default negative cases. The policy also closes the supported
  locale/accessibility evidence matrix. Concrete Android/iOS defaulted and
  override journey evidence is owned by affected feature/profile-transition
  stories, not by the bootstrap scaffold story.

### Story E1.6: Establish the market processing policy

As a privacy and market owner,
I want each market's remote processing position declared, approved, and expiring,
so that no user's content is processed somewhere their market does not permit.

**Traceability:** FR-8, FR-30, NFR-26..NFR-27, AR-51, AR-53, UX-DR41.

**Acceptance Criteria:**

- **Given** the policy contract, **when** it is created, **then** it conforms to
  immutable `contracts/market-policy/v1`, is owned by a principal independent of
  the products consuming it, and the mobile instance is protected.
- **Given** a market that is absent, `unresolved`, or past `expiresAt`, **when**
  a remote transfer is attempted for either modality, **then** it fails closed at
  the transfer path, not merely in the interface.
- **Given** a market permitting photo but not text, **when** a text transfer is
  attempted, **then** it is rejected; and presenting one modality's consent copy
  for the other fails closed.
- **Given** a catalog country change, **then** jurisdiction, posture, permitted
  regions, and per-modality permissions are unchanged.
- **Given** any posture, **then** accepted input languages resolve identically,
  and deterministic search and on-device identification remain fully available.
- **And** the resolved jurisdiction and processing region are displayed before
  every consent decision, and CI never writes policy state.

## Epic E2: Capture and photo preparation

Deliver the camera-first, import-capable session and normalized Photo Input
without selecting a model provider.

### Story E2.1: Implement camera and picker bridges

As a user,
I want to capture or import one meal photo with clear permission recovery,
so that I can start analysis without navigating a diary or account flow.

**Traceability:** FR-1, FR-2, FR-3, NFR-13..NFR-16, AR-4, AR-23, UX-DR1..UX-DR3,
UX-DR4, UX-DR9, UX-DR11..UX-DR12, UX-DR26..UX-DR34.

**Acceptance Criteria:**

- **Given** the app opens, **when** camera permission is resolved, **then** one
  labeled shutter and one system-import action are available with native
  platform behavior.
- **Then** denial, unavailable hardware, restricted access, and capture failure
  each expose settings, import, or manual-search recovery.
- **And** platform image objects stay inside the native bridge modules.
- **And** the ready capture surface does not add a setup choice beyond any
  required first-use catalog confirmation for deterministic locale/measurement/
  catalog context; any suggested value is visible and editable with its source.
- **And** this user-interaction-affecting story records its tap, scroll, field,
  and choice budget, including the required first-use catalog confirmation.

### Story E2.2: Normalize and quality-check Photo Input

As an inference engine,
I want one orientation-normalized, size-bounded, metadata-scrubbed Photo Input,
so that Android and iOS receive equivalent safe bytes.

**Traceability:** FR-4, NFR-4..NFR-5, AR-8, AR-18, AR-21..AR-22,
UX-DR4, UX-DR8, UX-DR10, UX-DR30..UX-DR32.

**Acceptance Criteria:**

- **Given** a PhotoRef, **when** preparation runs, **then** orientation,
  dimensions, color space, and metadata policy are deterministic and fixture-
  tested.
- **Then** EXIF, GPS, and all non-essential metadata are stripped before any
  remote payload or consent digest is created.
- **And** the attempt lease is encrypted when persistence is unavoidable and
  deleted on every terminal path.

### Story E2.3: Implement capture recovery and session cleanup

As a user,
I want retake, cancel, offline, and manual recovery,
so that a failed photo analysis never leaves a dead end or stale result.

**Traceability:** FR-11, FR-19..FR-21, NFR-2, AR-5, AR-18, UX-DR3,
UX-DR10..UX-DR11, UX-DR19..UX-DR20, UX-DR23..UX-DR24.

**Acceptance Criteria:**

- **Given** poor image, no-food, offline, cancellation, or replacement,
  **when** the user chooses a recovery action, **then** the reducer clears or
  preserves exactly the state defined by EXPERIENCE.md.
- **Then** a stale engine callback cannot reopen a result after replacement or
  cancellation.
- **And** Scan again returns to Capture with no attempt photo bytes retained.
- **And** this user-interaction-affecting recovery story records the four
  required dimensions plus bounded Back, Undo, cancellation, permission-prompt,
  text-entry, and choice-surface counts for each positive recovery path; stale
  proposal/decision IDs and snapshot/policy digests are rejected.

## Epic E3: Shared inference and local engines

Preserve the contract-v1 evidence vocabulary and deliver capability-aware local
inference as independent Android and iOS tracks. The approved v2 track adds
visible-component evidence and bounded gram estimates without reinterpreting a
single dish label as ingredients or mass.

### Story E3.1: Freeze contract-v1 inference ports

As the contract owner,
I want versioned request, capability, candidate, abstention, and failure types,
so that local, remote, gateway, and evaluation implementations are replaceable.

**Traceability:** FR-5..FR-6, FR-12, FR-24..FR-25, NFR-9..NFR-12, AR-2,
AR-3, AR-9, AR-11, AR-17, AR-33.

**Acceptance Criteria:**

- **Given** the contract module is pure KMP, **when** contract-v1 is published,
  **then** it contains no platform, UI, DI, provider, or nutrition-catalog
  dependency.
- **Then** canonical JSON, in-memory fixtures, unknown-field handling, and
  compatibility tests are published under `contracts/`.
- **And** no result shape contains numeric nutrition from an Engine.

### Story E3.2: Implement local Android inference

As an Android local-inference owner,
I want an Android-specific Local Engine behind the shared contract,
so that eligible devices can analyze without transmitting Photo Input.

**Traceability:** FR-6..FR-7, FR-12, FR-24, NFR-2..NFR-3, NFR-7,
NFR-9..NFR-12, AR-2..AR-3, AR-7, AR-9, AR-17.

**Acceptance Criteria:**

- **Given** a ready Android capability, **when** an attempt starts, **then** no
  network attempt or image transfer occurs and the result includes model,
  route, contract, confidence, and abstention provenance.
- **Then** model package integrity, compatibility, resource budgets, cancellation,
  and rollback are covered by focused tests and physical-device evidence.

### Story E3.3: Implement local iOS inference

As an iOS local-inference owner,
I want an iOS-specific Local Engine behind the same contract,
so that iOS devices receive the same user-facing evidence vocabulary.

**Traceability:** FR-6..FR-7, FR-12, FR-24, NFR-2..NFR-3, NFR-7,
NFR-9..NFR-12, AR-2..AR-3, AR-7, AR-9, AR-17.

**Acceptance Criteria:**

- **Given** a ready iOS capability, **when** an attempt starts, **then** the
  engine has the same contract-v1 semantics and no remote transfer occurs.
- **Then** preprocessing parity, lifecycle cancellation, resource budgets,
  model rollback, and physical-device evidence are recorded separately.

### Story E3.4: Integrate deterministic local-first routing

As a product owner,
I want a local/manual default route with paid-only remote escalation,
so that a free/default user never sees or triggers a remote transfer.

**Traceability:** FR-9..FR-10, FR-12, NFR-2, NFR-5, NFR-11, AR-6, AR-13,
AR-20, AR-25, UX-DR15..UX-DR16, UX-DR37.

**Acceptance Criteria:**

- **Given** a free/default session or a user who has not explicitly invoked an
  entitled paid route, **when** local analysis completes, abstains, or is
  unavailable, **then** the app stays local/manual and does not show Remote
  consent, an upgrade prompt, or initiate transfer.
- **Then** the later paid remote path is limited to an explicit entitled user
  action after local unavailability, Abstention, or a Gate G-5 low-confidence
  threshold; a successful high-confidence local result cannot be escalated by
  arbitrary configuration.
- **And** every route decision exposes a typed eligibility reason.

### Story E3.5: Release v2 visible-component and mass-evidence contract

As the contract owner,
I want one immutable v2 release for photo component evidence and optional mass
estimates,
so that Android, iOS, text identification, and evaluation cannot create
competing schemas or fabricate nutrition.

**Traceability:** FR-5..FR-7, FR-12, FR-32..FR-36, NFR-2..NFR-3, NFR-7,
NFR-9..NFR-12, NFR-28, AR-33, AR-48..AR-49.

**Acceptance Criteria:**

- **Given** the immutable `contracts/inference/v1` release, **when** component
  and mass evidence is added, **then** it ships in the already planned,
  modality-neutral `contracts/inference/v2`; v1 files and digest remain
  unchanged and both releases verify in the compatibility matrix.
- **Then** a v2 photo result has `VisibleComponent` evidence (candidate,
  confidence, optional box/mask reference, visibility/ambiguity, provenance)
  and an optional `MassEstimate` (gram range, calibration/model assumptions,
  and `estimated`/`not_evaluable`/`abstained` state).
- **And** no v2 Engine result contains calories, macros, or a canonical catalog
  binding; text identification returns mass `not_evaluable` unless a separately
  approved measurement modality exists.
- **And** fixtures fail closed for invalid mass ranges, absent provenance,
  unsupported/unknown values, stale attempt output, and an attempt to map an
  Engine result directly to nutrition.

### Story E3.6: Establish the consented weighed component corpus

As an evaluation owner,
I want a consented, tared, leakage-resistant component corpus,
so that grams and visible ingredients have valid ground truth.

**Traceability:** FR-23..FR-24, FR-32..FR-36, NFR-1..NFR-4, NFR-9..NFR-12,
NFR-28.

**Acceptance Criteria:**

- **Given** a scored meal, **when** it enters the corpus, **then** the empty
  vessel, each visible component before plating, and final plate are weighed;
  scale identity, resolution, verification, consent reference, photo digest,
  taxonomy, and QC status are recorded.
- **Then** component boxes/masks label only visibly separable food; hidden
  ingredients and inseparable mixtures are explicit `not_evaluable` states.
- **And** train, calibration, and held-out sets prevent meal, participant,
  restaurant, capture-session, and device leakage and report all declared
  device/cuisine/view/lighting/occlusion/portion strata.
- **And** Nutrition5k, when license-reviewed, is used only as attributed
  pretraining/benchmark-design data through a training-side metadata index and
  selected-shard mirror; it is never shipped to the app or treated as product
  acceptance evidence.

**2026-08-02 pre-release scope amendment:** E3.6's schema, validator, and
collection handoff close the pre-release implementation. The actual consented
300+ meal pilot is deferred to a separate post-release upload/consent feature;
its corpus remains mandatory before production v2 G3 activation.

### Story E3.7: Spike local Android visible-component and mass inference

As the Android local-inference owner,
I want a compact v2 detector/segmenter and optional mass head behind the shared
contract,
so that a compatible Android phone returns components and bounded estimates
without network transfer.

**Traceability:** FR-6..FR-7, FR-32..FR-36, NFR-1..NFR-3, NFR-7,
NFR-9..NFR-12, NFR-28.

**Acceptance Criteria:**

- **Given** a selected commercial-use-cleared model manifest, **when** Android
  analyzes a valid photo, **then** it emits v2 component/mass evidence or a
  typed abstention with no network attempt.
- **Then** cancellation, preprocessing/output parity, checksum activation,
  rollback, low-memory gating, and both valid/invalid mass states are covered
  by tests and Honor physical-device evidence.

Under the approved 2026-08-02 post-release scope amendment, E3.7 closes its
manifest, activation, adapter, and fail-closed pre-release seams. The selected
commercial model and physical component/mass benchmark are post-release G3
gates and are not replaced by public research results.

### Story E3.8: Spike local iOS visible-component and mass inference

As the iOS local-inference owner,
I want the same v2 component/mass evidence on iOS,
so that iOS is not a semantic-only fork.

**Traceability:** FR-6..FR-7, FR-32..FR-36, NFR-1..NFR-3, NFR-7,
NFR-9..NFR-12, NFR-28.

**Acceptance Criteria:**

- **Given** the same selected component artifact and v2 fixtures, **when** iOS
  analyzes a valid photo, **then** it emits parity-equivalent evidence or a
  typed abstention without remote transfer.
- **Then** depth availability, runtime/EP selection, cancellation, rollback,
  and iPhone physical-device resource evidence are recorded separately from
  Android.

Under the approved 2026-08-02 post-release scope amendment, E3.8 closes its
independent adapter and host correctness seam before model activation. The
selected artifact and physical component/mass benchmark remain post-release
G3 gates.

### Story E3.9: Integrate staged component evidence and local wait UX

As a user,
I want the app to explain a longer local analysis and show me the evidence it
found,
so that I can trust, correct, or cancel the result.

**Traceability:** FR-11, FR-13..FR-15, FR-32..FR-35, NFR-2, NFR-13..NFR-16,
NFR-28, UX-DR13..UX-DR16, UX-DR37.

**Acceptance Criteria:**

- **Given** a local component attempt, **when** work changes stage, **then** the
  UI shows factual `Preparing photo`, `Finding visible foods`, `Estimating
  portions`, or `Checking results` copy with bounded updates and one accessible
  Cancel action, never a countdown.
- **Then** v1 ranked candidates appear only as main-dish proposals, while v2
  `VisibleComponent` instances appear only as visibly separable `From this
  photo` evidence. A dish label is never decomposed into ingredients, and the
  absence of v2 components produces an honest recovery/no-component state.
- **And** every visible component shows confidence, route/provenance, an
  editable estimated gram range or `not enough visual evidence`. E3.9 renders
  the evidence and hands explicit selection/correction authority to E5.1; it
  does not access a dish-composition database, create a serving, or calculate
  nutrition.
- **And** the story records Android/iOS accessibility and interaction-budget
  evidence for success, correction, abstention, model-unavailable, and
  cancellation paths.
- E3.9 closes at the typed coordinator, presenter/Compose evidence surface, and
  Android/iOS lifecycle seam. Production runtime composition and physical
  staged-result evidence remain Epic 3 G3 exit gates: hosts must remain
  unbound until a `ComponentModelManifest` passes the production activation
  requirements and the complete approval/corpus/benchmark/rollback bundle is
  approved.

## Epic E4: Paid remote inference and consent (deferred)

Deliver a later paid-only remote path through the first-party gateway with
explicit consent, jurisdiction controls, provider isolation, and entitlement
gating. It is disabled and absent from the free/default MVP.

### Story E4.1: Implement remote consent and transfer states

As a user,
I want to understand and control every paid remote photo upload,
so that it never happens by surprise or appears in the free/default flow.

**Traceability:** FR-8, FR-10..FR-12, FR-22, FR-25, NFR-4..NFR-8,
NFR-11, AR-6, AR-8, AR-13, AR-18, AR-20..AR-22, AR-24, UX-DR17,
UX-DR25, UX-DR37.

**Acceptance Criteria:**

- **Given** a permitted remote trigger, **when** the consent sheet opens, **then**
  purpose, data sent, provider category/region, retention, and offline/manual
  alternatives are visible before either choice.
- **And** the sheet is unreachable without a verified paid entitlement and an
  explicit paid-route action; it is not an automatic recovery after a local
  failure and is absent from free/default UI.
- **Then** PreTransfer cancellation sends zero bytes; Transferring cancellation
  aborts transport; Transferred cancellation requests downstream deletion
  without claiming already-sent bytes were recalled.
- **And** duplicate transfer effects are rejected by attempt/effect IDs.
- **And** the consent surface records a zero-default interaction budget: upload
  and no-upload actions are equal, unselected, and visually neutral; the
  policy-resolved jurisdiction/processing region is visible before either choice,
  and tap, scroll, field, choice, text-entry, choice-surface, cancellation, and
  permission-prompt dimensions are recorded for both outcomes with proposal/
  decision IDs and the applicable policy/configuration digests.

### Story E4.2: Implement the remote mobile adapter

As a remote mobile owner,
I want a typed Ktorfit API over a Ktor adapter that knows only the first-party contract,
so that provider changes do not require a mobile feature rewrite.

**Traceability:** FR-8, FR-12, FR-25, NFR-4..NFR-5, NFR-8, NFR-11, AR-2,
AR-13, AR-21, UX-DR17, UX-DR25.

**Acceptance Criteria:**

- **Given** a valid consent receipt and scrubbed Photo Input, **when** the
  adapter sends a request, **then** the Ktorfit-generated API uses the pinned
  gateway envelope over Ktor and no provider credentials or provider schema are
  present in mobile code.
- **Then** retries are bounded, idempotent where applicable, and cancellation
  invalidates the attempt token.
- **And** Ktor MockEngine and Ktorfit generated-client contract tests cover
  upload, cancellation, timeout, redaction, version mismatch, rate limiting,
  malformed responses, and at-most-once effect behavior.

### Story E4.3: Implement the first-party gateway boundary

As a backend owner,
I want provider adapters and secrets behind the gateway,
so that mobile clients cannot bypass privacy, rate, or region policy.

**Traceability:** FR-8, FR-10, FR-12, FR-22, FR-25, NFR-4..NFR-8,
NFR-11, AR-13, AR-21, AR-24, UX-DR17, UX-DR25.

**Acceptance Criteria:**

- **Given** a contract-v1 request, **when** the gateway validates it, **then**
  size/type, authentication, nonce/replay, rate, processing-region, retention,
  and deletion controls are enforced before provider invocation.
- **Then** raw photos are not durably stored or logged by default and responses
  are normalized to contract-v1 evidence.

## Epic E5: Confirmation and grounded nutrition

Turn model evidence into a user-confirmed, catalog-grounded result without
letting inference fabricate nutrient values.

### Story E5.1: Implement minimal candidate review and correction

As a user,
I want to explicitly confirm a proposed main dish and only the ingredients I
recognize,
so that I remain the authority over what the photo means.

**Traceability:** FR-13..FR-15, FR-17..FR-18, FR-20..FR-21, NFR-12,
NFR-15..NFR-16, AR-7, AR-19, UX-DR13..UX-DR20, UX-DR37.

**Acceptance Criteria:**

- **Given** v1 dish candidates or an abstention, **when** review opens, **then**
  at most three distinct ranked main-dish possibilities, confidence, route,
  assumptions, alternatives, and recovery actions are visible. No dish is
  selected by default; `None of these` and manual search remain available.
- **Given** v2 component evidence, **when** review opens, **then** only its
  `VisibleComponent` instances appear as separately labelled `From this photo`
  ingredient icons/chips. Each starts unselected, exposes confidence and
  ambiguity through text/semantics rather than colour alone, and is reversible
  within the active Analysis Session. A v1 dish candidate never fabricates a
  component chip.
- **Then** a single explicit `Confirm foods` action preserves only the person's
  selected dish and ingredients for E5.3. It does not auto-bind a catalog record
  or calculate nutrition.
- **Then** manual search remains available when engines are unavailable and
  numeric nutrition is withheld until a catalog record is selected.
- **And** only after a canonical catalog item is confirmed, the catalog serving
  source may propose one atomic standard serving unit-and-amount pair for the
  current snapshot; the user can edit or undo it inline without opening a
  choice-first sheet. Candidate, locale, device, and prior correction context
  alone cannot create the proposal.
- **And** the story records tap, scroll, field, choice, text-entry,
  choice-surface, and recovery-event budgets plus proposal/decision IDs and
  default provenance for default, override, Undo, and forbidden-default paths.
- **And** a v2 estimated gram range is visible editable evidence, never a
  `DefaultProposal`, measured fact, or canonical catalog binding.

### Story E5.2: Implement country-aware nutrition catalog selection

As a user,
I want to choose a food catalog independently from processing jurisdiction,
so that food lookup preferences never silently route my photo across borders.

**Traceability:** FR-14, FR-25, NFR-6, NFR-14..NFR-16, AR-12, AR-20,
AR-26, UX-DR18..UX-DR19, UX-DR34, UX-DR37.

**Acceptance Criteria:**

- **Given** a catalog selection and a separate market/jurisdiction policy,
  **when** a ConfigurationSnapshot is created, **then** `CatalogCountry`,
  `MarketJurisdiction`, and `ProcessingRegion` remain distinct values.
- **Then** a catalog change invalidates the attempt's mapped result but does not
  silently change the processing region or remote-consent policy. A confirmed
  `CatalogCountry` may be persisted only as the next-attempt catalog context;
  the active attempt remains immutable and a new snapshot is created after
  invalidation. `MarketJurisdiction` and `ProcessingRegion` remain
  policy-resolved and are never persisted as user-selectable routing values.
- **And** the story records the catalog-choice budget and no-provider-routing
  path, with all four dimensions and choice-surface/recovery maxima.

### Story E5.3: Implement optional serving editor and grounded nutrient profile

As a user,
I want to edit serving only when needed and then see catalog-grounded nutrients
on my photo-first review screen,
so that estimates are transparent, correctable, and not calorie-first.

**Traceability:** FR-15, FR-16, FR-17, FR-18, NFR-12, NFR-15..NFR-16, AR-7, AR-19,
AR-29, UX-DR14, UX-DR20..UX-DR21, UX-DR37.

**Acceptance Criteria:**

- **Given** selected canonical items and usable serving values, **when** an
  explicit calculation succeeds, **then** deterministic catalog math produces
  the supported nutrient/vitamin/mineral profile and catalog-backed descriptive
  food properties with source, version, serving basis, assumptions, and estimate
  language on the same photo-first review screen. The core UI omits calorie
  total and makes no health-effect, medical, allergen, or outcome claim.
- **Then** unresolved matches remain partial/unknown and no model-generated
  numeric value is displayed.
- **And** the defaulted serving path and an override/Undo path are both covered
  by journey evidence with tap/scroll/field/choice counts, auxiliary maxima,
  atomic proposal/decision IDs, resolved-value digest, and default provenance.
- **And** a component-mass estimate can seed an editable serving review only
  when its v2 provenance/status is valid; `not_evaluable` and `abstained` values
  leave manual serving available without a fabricated number.

### Story E5.4: Add versioned dish-composition reference suggestions

As a person reviewing a chosen dish,
I want to see optional ingredients commonly associated with that dish,
so that I can confirm a complex meal without typing every component.

**Traceability:** FR-13..FR-17, FR-20..FR-21, NFR-12, NFR-15..NFR-16,
NFR-24, AR-7, AR-19, UX-DR13..UX-DR20, UX-DR37.

**Acceptance Criteria:**

- **Given** a selected main dish, **when** a composition source is available,
  **then** a versioned, licensed, country-aware, attributable source can return
  stable dish, ingredient, and optional recipe-reference-weight IDs without
  carrying photo data, user corrections, or nutrition authority.
- **Then** suggestions appear only under `Also common with this dish`, show
  source/version, and start unselected. They are removable/editable within the
  active session and never claim to be visible in the photo.
- **And** a recipe-reference weight is explicitly labelled, editable, and never
  a measured mass, `DefaultProposal`, serving, or nutrition input until E5.3's
  explicit confirmation path completes.
- **And** no composition lookup runs after `None of these`, manual search,
  abstention, cancellation, or a missing source. The photo-only/manual path
  remains usable and deterministic fixtures prove provenance separation,
  invalidation, and telemetry redaction.

## Epic E6: Journey evidence and evaluation

Create the deterministic cross-platform journey catalog, policy oracles, and
evaluation harness needed to make acceptance and model claims reproducible.

### Story E6.1: Build the shared UI journey DSL

As a UI-acceptance owner,
I want one logical journey definition for Android and iOS,
so that platform flows cannot silently drift.

**Traceability:** FR-19, FR-22..FR-24, NFR-13..NFR-21, AR-11, AR-28,
AR-34, AR-38, AR-41, UX-DR26..UX-DR33, UX-DR37.

**Acceptance Criteria:**

- **Given** UJ-1..UJ-4 and SM-9, **when** the positive-scenario catalog is
  authored, **then** every qualifying main, alternative, and recovery path maps
  exactly once to one logical fingerprint on both platforms.
- **Then** shared Compose semantics are tested in common/JVM, Android, and iOS
  suites, with platform locators as the only allowed difference.

### Story E6.2: Add privacy/network and native-gap oracles

As a QA owner,
I want binary-bound transfer and platform evidence,
so that a green UI assertion cannot hide an upload or native failure.

**Traceability:** FR-4, FR-8, FR-11, FR-22..FR-23, NFR-2, NFR-4..NFR-6,
NFR-13..NFR-16, AR-8, AR-14, AR-18, AR-21, AR-23..AR-24, UX-DR17,
UX-DR25, UX-DR29, UX-DR32.

**Acceptance Criteria:**

- **Given** local, offline, declined-consent, and remote-consent fixtures,
  **when** a journey runs, **then** attempted connections, bytes, photo digest,
  consent/effect ID, and terminal transfer state are recorded.
- **Then** camera, permission, accessibility, lifecycle, and performance claims
  have platform-appropriate native or real-device evidence.

### Story E6.3: Establish the evaluation corpus and route baselines

As an evaluation owner,
I want local-only, remote-only, hybrid, and manual baselines,
so that routing and accuracy claims are evidence-based.

**Traceability:** FR-23..FR-24, NFR-1..NFR-3, NFR-7, NFR-9..NFR-12,
AR-2, AR-7, AR-9, AR-17, AR-19, AR-26, UX-DR34.

**Acceptance Criteria:**

- **Given** a consented, versioned, leakage-resistant corpus, **when** a route
  is evaluated, **then** corrected accuracy, grounding error, latency, resource,
  cost, and representative Tier-1 slices are reported.
- **Then** Gate G-5 approves thresholds before confidence-based hybrid routing
  becomes release-ready.
- **And** the v2 report separately records visible-component set precision,
  recall, F1, region/mask metrics, valid scale-linked component/total mass MAE,
  relative MAE, p90 absolute error, bias, calibration, coverage-risk, and all
  declared device/cuisine/view/lighting/occlusion/portion slices.

### Story E6.4: Maintain the quality and release evidence gates

As a release owner,
I want CI to reject stale or incomplete evidence,
so that planning claims remain enforceable when implementation begins.

**Traceability:** FR-22..FR-24, NFR-17..NFR-21, AR-10..AR-11, AR-27..AR-43,
UX-DR22..UX-DR37.

**Acceptance Criteria:**

- **Given** a changed requirement, source, manifest, workflow, or contract,
  **when** CI runs, **then** affected inventories, coverage, journeys, and
  policy fingerprints are required at the same commit.
- **Then** missing, skipped, neutral, stale, detached, zero-denominator, or
  non-success evidence fails the aggregate.

## Epic E7: Typed meal identification

Specify the manual-search fallback that every failure path already depends on,
then add typed identification above it. Manual search is the terminal recovery
for a wrong Candidate, an unavailable Engine, a permanently denied camera, and a
declined upload, yet FR-14 and UX-DR19 describe only the surface. This epic
defines the behaviour, guarantees it on every supported device, and makes typing
a meal an identification modality rather than a parallel subsystem.

### Story E7.1: Release inference contract v2 with identification modality

As the contract owner,
I want a v2 release that accepts either a photo or a text description,
so that typed identification is a modality rather than a second subsystem.

**Traceability:** FR-5, FR-27, FR-31, NFR-9..NFR-12, AR-33, AR-48, AR-49.

**Acceptance Criteria:**

- **Given** `contracts/inference/v1` is an immutable digest-pinned release,
  **when** modality is added, **then** it ships as a new `contracts/inference/v2`
  release; v1 files are untouched and its recorded digest still verifies.
- **Given** v1's `contract_id` is `photo-inference-engine`, **when** v2 is
  authored, **then** it takes a modality-neutral contract id and the Kotlin
  `PhotoInferenceRequest`/`PhotoInferenceEngine` types gain modality-neutral v2
  equivalents.
- **Given** a v2 request, **when** it carries neither or both of a `PhotoRef`
  and a text payload, **then** it fails closed as a typed contract error rather
  than defaulting to a modality.
- **Then** capability states, Candidate, Abstention, provenance, and typed-error
  shapes are identical across modalities and unchanged from v1, and no modality
  returns numeric nutrition.
- **And** v2 photo output is extended by E3.5 with visible-component evidence
  and optional bounded mass estimates; text output must state mass
  `not_evaluable` rather than inventing grams.
- **And** the compatibility matrix declares both releases, v2 publishes its own
  canonical fixtures for both modalities, every v1 fixture still passes against
  v1, and `scripts/verify-inference-contract.sh` verifies both.
- **And** v1 is retired only when no producer or consumer implements it, as a
  separate change.

**Cost note:** this story was originally scoped as an amendment landed before
story 3.1 froze the ports. Story 3.1 has since shipped, so the migration path is
now the only path. Each further Engine built against v1 — local Android, local
iOS, gateway, evaluation — adds one more consumer to migrate, so the cost rises
until v2 lands.

### Story E7.2: Deliver the deterministic catalog search floor

As a user whose Engines cannot help,
I want to find a food by typing its name and add several to one meal,
so that I can still reach a grounded result offline on any supported phone.

**Traceability:** FR-3, FR-14, FR-16, FR-20..FR-21, FR-26, NFR-25, AR-49,
UX-DR19, UX-DR38..UX-DR39.

**Acceptance Criteria:**

- **Given** the bundled country package, no network, and every Engine reporting
  unavailable, **when** the golden query set runs on the lowest supported device
  tier, **then** each query resolves to its expected canonical ID within the
  declared latency budget.
- **Given** the same query and package version, **when** search runs on Android
  and iOS and repeatedly, **then** result ordering is byte-identical, with no
  personalization, history, or learned component.
- **Given** a multi-item meal, **when** records are added one at a time, **then**
  already-confirmed items are preserved and per-item and aggregate provenance
  match the photo path.
- **Given** no match, **then** numbers are withheld, at most three next actions
  are offered, and no nearest guess or unchangeable Retry is presented.

### Story E7.3: Add the per-market language matrix

As a user whose food is not named in English,
I want to search in my own language, spelling, and script,
so that the catalog is usable in every market we support.

**Traceability:** FR-14, FR-31, NFR-26, AR-50, UX-DR38.

**Acceptance Criteria:**

- **Given** a supported market, **when** its package activates, **then** it
  declares catalog language, accepted input languages, script, morphological
  normalization, and synonyms as signed package data rather than search-code
  conditionals.
- **Given** an inflected catalog language, **when** a user types any declared
  inflected form, **then** the record resolves, and normalization is applied
  identically at index build and query time.
- **Given** a market declaring cross-script entry, **when** a record is typed in
  the other script, **then** it is reachable through the fuzzy tier only and
  never outranks an exact match in the record's own script.
- **Given** English, Spanish, and Russian as the first three targets, **when**
  their fixtures run, **then** each passes against the catalog it searches:
  Spanish resolves regional variants of one food to one canonical ID, and
  Russian resolves inflected noun forms and Latin-script entry of Cyrillic
  records.
- **Given** an accepted input language, **when** its fixtures are authored,
  **then** they are keyed per catalog-and-input-language pair, because an input
  language does not imply a catalog written in it; a language proven against one
  catalog is not proven against another, and a pair without fixtures fails the
  inventory as untested.
- **Given** any market processing posture, **when** input languages resolve,
  **then** they resolve identically; declaring a language grants no market
  position and an unresolved market still supports every language it declares.

### Story E7.4: Add on-device typed identification

As a user who would rather describe a meal than search for each part,
I want to type what I ate and get food suggestions per item,
so that correcting or replacing photo identification is faster.

**Traceability:** FR-6, FR-12, FR-17, FR-27..FR-28, NFR-2..NFR-3, AR-48,
AR-49, AR-52, UX-DR38.

**Acceptance Criteria:**

- **Given** a multi-item description, **when** identification succeeds, **then**
  one Candidate group is returned per detected item, each carrying the span of
  the description it came from and no numeric nutrition.
- **Given** a description the engine cannot segment, **then** it abstains with a
  plain-language reason rather than returning a confident subset as a whole meal.
- **Given** the identification fixture set, **then** it declares expected
  outcomes in both directions — entries that must resolve to a named canonical
  ID and entries that must abstain — so over-confidence fails a test rather than
  passing a suite built only from resolvable cases. Abstention rate is reported
  against the evaluation corpus as a measurement, not a release gate.
- **Given** the text engine is not `ready` for any reason, **then** the E7.2
  floor is reachable in one action and no journey dead-ends.
- **Given** a completed identification, **then** the result records a text
  Inference Route distinct from `local` and `remote`, preserved through
  confirmation and redacted telemetry.
- **And** model storage, memory, thermal, and battery budgets are measured per
  device tier, with parameter count not accepted as a proxy.

### Story E7.5: Add segmentation review and correction

As a user whose meal was split wrongly,
I want to merge, split, remove, or add items before confirming,
so that a bad split is visible and repairable rather than silent.

**Traceability:** FR-13, FR-17, FR-29, NFR-24, AR-49, UX-DR40.

**Acceptance Criteria:**

- **Given** a proposed split, **when** it is presented, **then** each item shows
  the words it came from and merge, split, remove, and add are available before
  any item is confirmed.
- **Given** an item the engine missed, **when** the user adds it, **then** the
  Analysis Session is not restarted and already-confirmed items are not
  discarded.
- **Given** any confidence level, **then** no item binds a canonical record
  without an explicit confirmation event.
- **And** food identity, preparation, and recipe remain outside the defaultable
  allowlist; user-authored text does not move them into it.

### Story E7.6: Add remote typed identification

As a user whose phone cannot identify a described meal,
I want the option to send the description for remote identification,
so that a weak device is not the end of the journey.

**Traceability:** FR-8, FR-10..FR-11, FR-22, FR-30, NFR-4..NFR-8, NFR-27,
AR-51, AR-52, AR-53, UX-DR41.

**Acceptance Criteria:**

- **Given** an attempt, **when** remote text is offered, **then** it is offered
  only on local unavailability, local abstention, or a policy-defined
  low-confidence result, and adds no new eligibility reason.
- **Given** a text transfer, **when** consent is requested, **then** it presents
  reviewed text-specific copy; the photo-transfer copy cannot authorize it and a
  mismatch fails closed.
- **Given** consent, **then** the receipt binds purpose, resolved processing
  region and provider policy, retention version, configuration version, and
  transfer state, and the resolved jurisdiction is shown before transfer.
- **Given** a market whose data-residency position is unresolved, **then**
  remote text is disabled for that market rather than defaulting to the nearest
  provider region.
- **And** the description is attempt-scoped, never logged, never placed in
  analytics, and reaches the gateway on no other path.
