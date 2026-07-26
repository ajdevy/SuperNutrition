---
stepsCompleted: []
inputDocuments:
  - prds/prd-SuperNutrition-2026-07-26/prd.md
  - architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md
  - architecture/architecture-SuperNutrition-2026-07-26/solution-design.md
  - architecture/architecture-SuperNutrition-2026-07-26/implementation-agent-guide.md
  - architecture/architecture-SuperNutrition-2026-07-26/giphysearch-practices-adoption.md
  - ux-designs/ux-SuperNutrition-2026-07-26/DESIGN.md
  - ux-designs/ux-SuperNutrition-2026-07-26/EXPERIENCE.md
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

FR-16: The system reproducibly calculates calories and supported nutrients from the confirmed versioned Nutrition Source record, serving conversion, and Serving Assumption while preserving per-item and aggregate provenance.

FR-17: The system never displays model-generated calories, macronutrients, or other numeric nutrition as a Grounded Nutrition Result; unresolved matches remain partial or unknown.

FR-18: The user can view confirmed food, Serving Assumption, calories, supported macronutrients, source/version, uncertainty, and Inference Route with estimate—not medical, allergen, or exact-measurement—language.

FR-19: The user can end the current Analysis Session, clear session-scoped photo and inference state, and return to capture with one primary Scan again action.

FR-20: The user receives an outcome-specific recovery path for no-food, poor-image, model unavailable/download, offline, timeout, rate-limit, consent, parsing, grounding, and unexpected failures, with Retry only when it can change the outcome.

FR-21: The user can use ready local analysis and manual nutrition search while offline and can always end or restart the Analysis Session.

FR-22: The system can record redacted route, capability, timing, Candidate decision, correction, source coverage, error, version, and resource events without raw photos, free-form sensitive payloads, secrets, headers, or transport bodies.

FR-23: The team can run local-only, remote-only, hybrid, and manual baselines against one versioned Evaluation Corpus and report quality, correction, grounding error, latency, resource, cost, and representative slice results.

FR-24: The team can compare Android and iOS model exports or adapters against golden images and activate only versions whose hash, taxonomy, preprocessing, runtime compatibility, and parity evidence pass, retaining a previous compatible route.

FR-25: Authorized operators can select versioned, allow-listed, observable Engine configurations without a mobile feature rewrite or weakening consent, retention, grounding, or contract requirements.

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

NFR-17: Every compiled production source belongs to exactly one coverage denominator. Tool-measurable mobile, web-landing, and backend production code maintains at least 85% unit-test line and branch coverage per module and per product aggregate; changed production lines and branch outcomes maintain 100%. Unavoidable Native, Swift, framework, or hardware behavior requires explicit executable equivalent evidence and documented physical-device acceptance rather than an optional exclusion.

NFR-18: Local Android, local iOS, remote, and orchestration modules can build and run their own tests without compiling, importing, or editing another Engine implementation.

NFR-19: Dependency and tool versions are pinned centrally within each monorepo product boundary; repository-root pins are limited to policy and CI tooling and cannot become a shared product dependency graph. Implementation agents do not perform unrelated upgrades.

NFR-20: A closed, reviewable catalog derived from every UJ-1 through UJ-4 main, alternative, and recovery path that reaches a valid result or privacy-preserving cancellation, plus every SM-9 dimension, defines completeness. Every catalog scenario has one shared journey definition executed as deterministic automated UI acceptance on Android and iOS using controlled fixtures and linked privacy/network oracles rather than live model/provider output.

NFR-21: Repository CI runs the complete host-compatible automated quality, unit, build, and UI-acceptance matrix for every push to every branch, every pull request, and every merge-queue commit when enabled; a missing, skipped, neutral, cancelled, timed-out, action-required, stale, startup-failed, runner-unavailable, or failed required job cannot produce a passing aggregate status.

NFR-22: The repository is a monorepo with separate root product boundaries at `mobile/`, `web/landing/`, and `backend/mobile-api/`. Each product independently owns its source, tests, dependency installation and lock state, cache, quality entrypoint, artifact, deployment, credentials, rollback, and distinct CODEOWNERS principal; source, build, package-manager, generated-code, runtime, and deployment dependencies cannot cross product boundaries. Cross-product integration uses only immutable, versioned, language-neutral releases independently owned under `contracts/`; producers and consumers test every supported released-version combination rather than only repository HEAD. `web/landing/` has no runtime dependency on the mobile backend. Root policy and contract compatibility checks are unavoidable inputs to the six-job aggregate on every branch push, pull request, and enabled merge-queue commit.

NFR-23: Product identity is exact and testable: installed app, launcher label, and in-app brand are `SuperNutrition`; the Apple App Store and Google Play listing title is `SuperNutrition AI nutritionist`. The descriptor is store metadata only. Protected identity metadata and CI assertions prevent platform, build-profile, locale, landing-page, and store-listing drift.

### Additional Requirements

- AR-1 (AD-16): The mobile scaffold story must create and prove the greenfield KMP scaffold as one atomic tuple: Kotlin/KMP 2.4.10, Compose Multiplatform 1.11.1, Gradle 9.5.0, AGP 9.1.0, JDK 21, Android compile/target SDK 36 with API 23 floor, Xcode 26.4.1 on macOS 26.2+, iOS 15 floor, Detekt/ktlint wrapper 2.0.0-alpha.5, and Kover 0.9.8. The story publishes exact common, Android, iOS, Xcode, lint, and coverage evidence; any tuple change is a dedicated compatibility change.
- AR-2 (AD-25): Inside `mobile/`, create `apps`, `core`, `domain`, `data`, `inference`, `platform`, `feature`, `testing`, and `evaluation`, plus mobile `build-logic`, `config`, and `gradle`, using the exact structural seed in the architecture spine. The mobile backend is `backend/mobile-api/`, not a mobile Gradle group.
- AR-3 (AD-1, AD-25): Enforce inward mobile dependencies. `mobile/domain` is pure KMP and cannot depend on Android, Apple frameworks, Compose, Koin, Ktor, or SQLDelight; distributable production configurations cannot depend on `mobile/testing`. Only the two manifest-listed UI-acceptance configurations may resolve exact allowlisted test-support edges.
- AR-4 (AD-26): Centralize mobile versions and SDK/JVM floors in `mobile/gradle/libs.versions.toml`; use `mobile/build-logic` convention plugins so module scripts declare only identity, targets, and dependencies. Each monorepo product owns one central dependency/tool manifest, frozen install, lock/verification state, and product-local resolution tree; no root workspace, lockfile, virtual environment, or hoisted dependency tree may span products.
- AR-5 (AD-26): Settings reject project repositories and enforce strict dependency verification and per-project/configuration lock state across Gradle, included builds, native/Xcode, gateway/container, model, and catalog inputs. CI never writes metadata; dynamic/range/snapshot/file/URL dependencies are forbidden.
- AR-6 (AD-27): Provide read-only mobile `qualityCheckJvmAndroid` and `qualityCheckApple`, plus lifecycle-aware `web/landing/quality-check` and `backend/mobile-api/quality-check` entrypoints. Active products own complete compile/build, lint, unit, coverage, dependency, artifact, and inventory evidence; preselection products prove no production material exists. `quality-linux` unconditionally owns the same-SHA root-boundary/catalog result and mobile released-contract matrix; backend quality owns its matching released-contract matrix.
- AR-7 (AD-27): Provide `qualityFormat` as the only mutating formatter/Detekt-autocorrect entrypoint, using the same pinned ruleset as checks. Suppression registry entries require exact rule/symbol, independent approval, issue, creation/expiry, count, and removal condition; safety-sensitive areas cannot add suppressions.
- AR-8 (AD-27): Use exact fully qualified, evidence-backed entries only in the Compose stability configuration; wildcard stability entries are forbidden.
- AR-9 (AD-16, AD-27): Run each Linux and Apple host task twice with configuration-cache problems failing and prove store then reuse. Device behavior remains a separate real-device gate, and a skipped, missing, neutral, timed-out, or unavailable required status blocks merge.
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
- AR-21 (AD-8, AD-18): Scope image bytes to an attempt lease, encrypt unavoidable temporary persistence, log no image data, revoke and clean up idempotently on every terminal path, and prevent duplicate transfer-start effects with stable attempt/effect IDs.
- AR-22 (AD-8): Bind remote consent to the photo digest, purpose, provider/region policy, retention version, configuration version, and transfer state. Cover deletion across device, gateway, provider, temporary files, caches, queues, logs, backups, and failure paths with honest limits and evidence.
- AR-23 (AD-9, AD-22): Identify every model/provider configuration by immutable version, checksum/deployment digest, compatibility predicates, activation time, and rollback target. Fix preprocessing, color space, dimensions, taxonomy, and golden pixel/output parity in the model manifest.
- AR-24 (AD-10): Start Koin only in app roots, use constructor injection and explicit `localEngine`/`remoteEngine` qualifiers, and prohibit global Koin access in domain, feature, and Engine code.
- AR-25 (AD-11): Every Engine and gateway adapter passes the same contract suite. Physical-device evidence is required for camera, performance, memory, energy, accessibility, and local-inference claims.
- AR-26 (AD-12, AD-20): Treat country independently from language. A signed `ConfigurationSnapshot` resolves bundled safe default, global release, environment, then country override and remains atomic for one attempt.
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
- AR-42 (AD-35): Check in GitHub Actions workflows triggered by every branch push and pull request without filters; require merge-group runs after AD-38 proves hosting support. Run four mobile jobs plus `quality-web-landing` and `quality-backend` regardless of changed paths. `ci-required` directly needs exactly all six, uses only `always()`, demands exact success conclusions, and unconditionally validates same-SHA root boundary/tool inventory, product lifecycle/inventory, contract catalog/released-version matrix, and product/flow evidence. Policy tests reject skipped/neutralized work, unsafe permissions/events, floating dependencies, quarantine, and assertion retries.
- AR-43 (AD-36): Enforce the only product roots as `mobile/`, `web/landing/`, and `backend/mobile-api/`, with independent dependency installs, locks, product-ID-scoped caches, artifacts, deployment workflows/environments/credentials, rollback, and distinct owners. `contracts/` is non-executable data authored and published only by an independent contract owner; mobile and backend consume the same immutable release and test the complete supported version-skew matrix. Reject cross-root source, workspace/package, generated-code, runtime/config, build, cache, artifact, and deployment edges. The landing has no backend runtime dependency. Root tooling is policy-only and inventoried; every executable is mapped and tested. Incompatible contracts require a major version, coexistence, server-first activation, minimum-version policy, rollback, and removal story.
- AR-44 (AD-37): Start mobile in protected `scaffold-pending` and web/backend in `preselection`. Every named job proves no production source, dependency installation/lock, runtime, deployable artifact, or deployment has appeared and emits same-SHA exact-state evidence without claiming build/test/coverage/UI. Mobile moves irreversibly and atomically to `active` through its reviewed scaffold story with a nonempty inventory and real non-product bootstrap host/device/app evidence under the AD-34 bootstrap profile; full product journeys become mandatory only with the irreversible feature-profile transition. Web/backend activate through reviewed pinned-toolchain stories adding frozen dependencies, nonempty inventories, real build/lint/test/coverage/artifact gates, owners, and deployment/rollback skeletons. All six jobs pass each transition.
- AR-45 (AD-38): Treat private-repository enforcement as an infrastructure gate. Before production implementation, prove hosting support and active controls for private rulesets, distinct-owner approval, required `ci-required`, immutable `trusted-policy-audit`, and a blocked bypass; also prove `merge_group` if merge queue is enabled. Checked-in workflow YAML is evidence, not enforcement. If capabilities are not provisioned, require a new reviewed implementable governance decision.
- AR-46 (AD-39): Add protected `mobile/config/product-identity.toml` with exact canonical installed/in-app name `SuperNutrition` and exact Apple App Store and Google Play listing title `SuperNutrition AI nutritionist`. Generate or validate Android labels, iOS display-name metadata, `SuperNutritionUIAcceptance`, web metadata, and store-delivery metadata from it; test all profiles/locales and reject independently hard-coded variants. Select package and bundle identifiers in the scaffold story without changing visible identity.

### UX Design Requirements

UX-DR1: Compose Multiplatform owns every product screen on Android and iOS; native CameraX/AVFoundation/PhotosUI, permission, picker, and host surfaces are bridges only and must not become parallel product UIs.

UX-DR2: Implement Capture, permission rationale, preparation/analyzing, remote consent, Candidate review, Food search, Country and catalog, Serving confirmation, Grounded result, Recovery, and debug-only diagnostics as the complete first-release information architecture, with no tab bar, drawer, account, diary, or general settings hierarchy.

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

UX-DR17: Implement the Remote consent sheet with purpose, data sent, retention, provider category/region, alternatives, cancellation limits, and policy detail; “Continue without upload” precedes “Send photo for remote analysis,” both use equal unfilled visual/focus treatment, and neither is preselected.

UX-DR18: Implement the Country and catalog sheet as contextual configuration available on first use, Capture, and Food search. Suggest system region, require confirmation, persist locally, show current catalog, and provide Not listed/manual recovery without silently choosing the United States.

UX-DR19: Implement country-aware Food search that retains source/version, supports correction and no-Engine recovery, exposes None of these/no-match paths, and withholds numeric nutrition until a record is selected.

UX-DR20: Implement the Serving editor for amount, unit, optional range, and contextually relevant recipe/oil/sauce questions; validation preserves user input and never describes an inferred portion as measured fact.

UX-DR21: Implement one-level Privacy detail and Source detail sheets. Privacy detail covers purpose, data categories, retention, provider category/region, cancellation limits, and policy link; Source detail covers source name/version, country coverage, serving conversion, and estimate disclaimer.

UX-DR22: Implement a debug/internal-only diagnostics panel containing redacted route, capability, version, timing, and error fields; its copy action excludes photos and sensitive payloads.

UX-DR23: Implement typed Recovery panels with no more than three relevant next actions and no generic Retry when state cannot change; every poor image, capability, model, network, consent, parsing, grounding, and unexpected state has a non-dead-end route.

UX-DR24: Implement explicit poor-image, model-required/downloading, local-unavailable, low-Confidence/Abstention, remote-declined, timeout/rate-limit/offline, Candidate ambiguity, source miss, empty Candidate list, cancellation, and completed treatments exactly as specified in EXPERIENCE.md.

UX-DR25: Implement action-scoped transfer behavior across consent accepted before transfer, transfer in progress, and transfer completed, using copy and effects that never claim already-sent bytes were recalled.

UX-DR26: Implement the full transition table in EXPERIENCE.md, including single invocation of native permission/local analysis/remote transfer, request-ID invalidation, old-Candidate clearing on replacement, preservation of user edits, and cleanup of unnecessary Photo Input artifacts after grounding.

UX-DR27: Support tap, native pinch-to-zoom/tap-to-focus, keyboard Next/Done, explicit accessible removal, and optional restrained confirmation haptics; no required swipe or gesture-only action.

UX-DR28: Ban automatic remote upload, preselected consent, progress countdowns, calorie score rings, infinite carousels, gesture-only controls, modal stacks deeper than one, gradients, confetti, streaks, and celebratory or disciplinary nutrition feedback.

UX-DR29: Label every capture control, Candidate, edit action, route, uncertainty, progress state, and nutrition value for VoiceOver and TalkBack; focus follows reading order and moves to the first changed heading when state advances.

UX-DR30: Meet at least 44pt iOS and 48dp Android targets, support Reduce Motion, test high contrast/dark mode over real photos, and provide fully labeled import/manual-search alternatives to live camera use.

UX-DR31: After capture, screen readers announce photo readiness and specific blur, darkness, or no-food guidance; Analyzing announces once and animations do not repeatedly speak.

UX-DR32: Preserve native preview, picker, permission, Back, safe-area/inset, font-scale, and Dynamic Type behavior while sharing unit/locale formatting and the product workflow.

UX-DR33: Keep phone portrait primary, but make landscape, tablet, and foldable layouts functional through reflow and safe insets without requiring custom large-screen layouts in the first release.

UX-DR34: Store canonical quantities separately from localized display units, support metric weights throughout the Tier 1 reference cohort, use configuration-backed jurisdictional privacy copy, and include regional foods in evaluation and empty-state examples.

UX-DR35: Before visual Compose implementation, approve responsive/accessibility-annotated mocks for Capture, Remote consent, Candidate review, Serving confirmation, and Grounded result; DESIGN.md and EXPERIENCE.md remain authoritative if a mock conflicts.

UX-DR36: Render `SuperNutrition` as the installed, launcher, accessibility-product, and in-app brand. Use `SuperNutrition AI nutritionist` only in Apple App Store and Google Play acquisition metadata; never repeat the store-only descriptor in app chrome or ordinary product copy.

### FR Coverage Map

{{requirements_coverage_map}}

## Epic List

{{epics_list}}

<!-- Repeat for each epic in epics_list (N = 1, 2, 3...) -->

## Epic {{N}}: {{epic_title_N}}

{{epic_goal_N}}

<!-- Repeat for each story (M = 1, 2, 3...) within epic N -->

### Story {{N}}.{{M}}: {{story_title_N_M}}

As a {{user_type}},
I want {{capability}},
So that {{value_benefit}}.

**Acceptance Criteria:**

<!-- for each AC on this story -->

**Given** {{precondition}}
**When** {{action}}
**Then** {{expected_outcome}}
**And** {{additional_criteria}}

<!-- End story repeat -->
