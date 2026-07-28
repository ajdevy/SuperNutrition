# Architecture Reviewer Gate — Good-Spine Rubric

Reviewed: 2026-07-26
Target: `../ARCHITECTURE-SPINE.md`
Lens: independent rubric walker
Intent: validate only; the spine was not edited

## Gate verdict

**Changes required before handoff.** The spine has a good core paradigm and fixes
most local/remote ownership seams, but it currently fails the gate on capability
traceability and verified stack pins. Several otherwise strong rules are not
enforceable for platform-managed models, observability, remote abuse control, and
the operational envelope as written.

The deterministic lint passed with zero findings. That confirms mechanical shape,
not semantic readiness.

## Good-spine checklist

| Checklist item | Judgment | Evidence |
| --- | --- | --- |
| Fixes the real divergence points and misses none | **Partial** | AD-1 through AD-15 cover dependency direction, engine isolation, platform purity, state ownership, routing, grounding, image lifecycle, DI, country policy, gateway, telemetry, and agent boundaries. Missing or under-bound seams remain for preprocessing parity, gateway identity/abuse control, catalog packaging/update, accessibility across Compose/native interop, and environment operations. |
| Every AD Rule is enforceable and prevents its stated divergence | **Partial** | AD-1, AD-3 through AD-8, AD-10, AD-12, AD-13, and AD-15 are directionally enforceable. AD-2 leaves evidence semantics partly open; AD-9 requires model controls unavailable for some platform-managed runtimes; AD-11 does not name measurable tasks/evidence; AD-14 prevents required evaluation telemetry. |
| Nothing under Deferred permits incompatible lower-level choices | **Partial** | G-1 through G-6 identify real gates, but do not consistently name the blocking story, owner, entry evidence, or exit artifact. G-3, G-4, and G-6 currently defer decisions that real local, remote, and catalog stories need before implementation. |
| Named technology is verified-current and fit | **Fail** | `Compose Multiplatform 1.11.1` and `Koin 4.2.2` are not published stable versions in their official release records. Compatibility of the whole Kotlin/Compose/AGP/Decompose/Koin slab is not evidenced. |
| Ratifies rather than contradicts brownfield reality | **Pass with scope note** | The production direction is greenfield KMP and intentionally supersedes the historical Expo prototype. The spine does not attempt to preserve prototype implementation structure. |
| Covers the capabilities of the driving PRD/UX | **Fail** | The capability map shifts multiple FR and NFR ranges, binds nonexistent `SM-9` and `SM-10`, and omits an architectural accessibility/native-interop invariant. AD-14 contradicts FR-22 evaluation diagnostics. |
| Preserves inherited parent invariants | **Not applicable** | No parent architecture spine is cited as inherited. |
| Every owned dimension is decided, deferred, or open | **Partial** | Device/app structure and first-party gateway environments are shown. Deployment/promotion, secret ownership, config distribution, availability objectives, incident operations, provider retention evidence, and environment isolation are neither decided nor fully deferred. |

## Critical findings

### Capability traceability is materially wrong

The spine declares `SM-1..SM-10` in frontmatter and repeatedly binds
`SM-7..SM-10`, but the PRD defines SM-1 through SM-8 plus three counter-metrics;
SM-9 and SM-10 do not exist.

The Capability → Architecture Map also shifts the PRD:

- FR-5..FR-8 are shared inference/capability/local/remote engines, while FR-9..FR-12
  are orchestration.
- FR-13..FR-17 are confirmation and deterministic grounding.
- FR-18..FR-21 are result/session/recovery/offline continuation.
- FR-22..FR-25 are diagnostics, corpus evaluation, parity, and provider/model
  configuration.
- NFR-13..NFR-16 are accessibility, localization, and tone, not
  “security, privacy and operations.”
- NFR-17..NFR-19 are testing and maintainability.

As written, downstream stories can cite the map and implement the wrong governing
ADs while still appearing traceable.

**Disposition:** Autofix the frontmatter, every affected `Binds` range, and the
Capability → Architecture Map from the stable PRD IDs. Add explicit rows for
accessibility/localization and testing/maintainability.

Evidence: spine lines 11–14, 64, 82, 88, 94, 100, 106, 118, 124, 130, 136,
238–247; PRD sections 4.1–4.6, 7, and 8.

### Two stack pins are nonexistent and the compatibility slab is unproven

The official Compose Multiplatform release feed reports `1.11.0` as the current
stable release, not `1.11.1`. Koin's official releases page reports `4.2.1` as
stable, not `4.2.2`. Kotlin 2.4.10, Gradle 9.5.0, Ktor 3.5.1, Ktorfit 2.7.4,
SQLDelight 2.3.2,
and Decompose 3.5.0 do have official releases. AGP 9.1.0 exists, although 9.1.1
is the current patch in that line. Xcode 26.4 exists, but Apple currently lists a
newer stable Xcode 26.6; an older baseline can be deliberate, but must be stated
as such.

Existence alone does not prove that Compose 1.11.0, Kotlin 2.4.10, AGP 9.1.x,
Koin 4.2.1, and Decompose 3.5.0 work together across Android and iOS.

**Disposition:** Autofix the invalid versions, then require a bootstrap
compatibility build on both targets before freezing the stack. Record why Xcode
26.4 is a baseline if it remains pinned.

Official evidence:

- [Kotlin releases](https://kotlinlang.org/docs/releases.html)
- [Compose Multiplatform releases](https://github.com/JetBrains/compose-multiplatform/releases)
- [Gradle 9.5.0 release notes](https://docs.gradle.org/9.5.0/release-notes.html)
- [AGP 9.1 release notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [Ktor releases](https://ktor.io/docs/releases.html)
- [Ktorfit compatibility and installation](https://foso.github.io/Ktorfit/)
- [SQLDelight releases](https://github.com/sqldelight/sqldelight/releases)
- [Koin releases](https://insert-koin.io/docs/support/releases/)
- [Decompose releases](https://github.com/arkivanov/Decompose/releases)
- [Xcode versions](https://developer.apple.com/support/xcode/)

### AD-14 forbids data required by FR-22 and the evaluation gates

FR-22 requires privacy-safe recording of Candidate decisions, correction actions,
source coverage, errors, model/provider version, and resource measurements.
AD-14 allowlists only attempt ID, route, capability tier, duration bucket,
terminal outcome, and version IDs. Its prohibition on user-entered labels is
correct, but its narrow allowlist also eliminates derived correction counts,
candidate-rank decisions, source-match status, typed error category, memory,
thermal, battery, and remote-cost evidence needed by FR-22, FR-23, SM-6, and SM-8.

This is a direct source contradiction, not a detail to leave to implementation.

**Disposition:** Discuss and amend AD-14 with a privacy-reviewed derived-event
schema. Preserve the raw-data prohibitions while explicitly allowing enumerated,
non-free-form evaluation fields, aggregation thresholds, retention, consent mode,
and route-cost/resource measurements.

Evidence: spine lines 134–138 and 247; PRD FR-22/FR-23 and SM-6/SM-8.

## High findings

### AD-9 is not enforceable for every platform-managed local model

AD-9 requires every active model to expose an immutable version, checksum,
compatibility predicates, activation time, and rollback target. Apple- or
OS-managed models may expose only OS/runtime/build capability and may not allow
the app to checksum, select, retain, or roll back a model. The same problem can
occur with device-managed Android runtimes.

The rule therefore makes compliance impossible for some engine families that
G-3 explicitly leaves open.

**Disposition:** Discuss. Split provenance and activation policy into
`AppManagedModel` and `PlatformManagedModel` profiles. Require checksum and
rollback only where the app controls artifacts; otherwise require the strongest
available runtime/OS/capability provenance plus a kill switch and remote/manual
fallback.

Evidence: spine lines 104–108 and G-3; PRD FR-24 and NFR-7/NFR-12.

### The operational and environmental envelope is incomplete

The diagram names development, staging, and production, but the spine does not
bind:

- environment-to-build selection and prevention of production builds calling
  development or arbitrary hosts;
- gateway deployment/runtime ownership and promotion/rollback;
- secret storage, rotation, and operator access;
- config-signing key custody, distribution, cache, expiry, rollback, and
  compromise response;
- service availability objectives, alerting, incident ownership, deletion
  verification, or provider retention evidence;
- abuse prevention and rate-limit identity for an account-free application.

AD-13 says the gateway applies “authenticated rate limits,” while the product has
no account identity. Without a device/app-attestation or anonymous-token decision,
different gateway stories can implement incompatible and ineffective schemes.

**Disposition:** Add an Operations gate or expand G-4 with explicit exit
artifacts for environment isolation, deployment/promotion, secret/config custody,
anonymous-client abuse control, retention verification, observability, and
incident response. Do not permit a real remote-engine story to start before its
mobile-gateway envelope and idempotency contract are frozen.

Evidence: spine lines 128–132, 157, 216–233, and G-4; PRD NFR-5/NFR-6/NFR-8/NFR-11
and Cost and Operations guardrails.

### Accessibility and Compose/native interop are not architectural invariants

The UX requires cross-platform semantic labels/state, focus transfer on state
changes, non-repeating progress announcements, large-text reflow, reduced motion,
real-image scrim testing, 44pt/48dp targets, and equivalent manual recovery.
The spine's map misclassifies NFR-13..NFR-16 and no AD binds how shared Compose
semantics combine with embedded CameraX/AVFoundation views.

This allows Android and iOS stories to choose incompatible focus ownership,
accessibility trees, scaling, and camera-overlay semantics.

**Disposition:** Autofix with an accessibility/native-interop AD or extend AD-4
and AD-5. Bind semantic ownership, focus handoff, target-size mapping, text-scale
reflow, reduced motion, and platform accessibility acceptance evidence.

Evidence: spine AD-4/AD-5 and lines 244–246; UX Experience sections
“Accessibility Floor” and “Responsive and Platform Behavior”; PRD NFR-13..NFR-16.

### Photo normalization parity is under-specified

AD-4 makes `PhotoRef` opaque and introduces `PhotoContentSource`, but “normalized
bytes and metadata” does not fix the preprocessing contract that Android local,
iOS local, remote upload, and evaluation-corpus runners must share. Orientation,
color space, crop policy, maximum dimensions, encoding quality, MIME, metadata
stripping, and content digest can diverge and invalidate FR-24 parity.

**Disposition:** Add a rule or Deferred gate for a versioned
`PhotoNormalizationProfile`. Require adapters and the evaluation harness to
record that profile version and run common golden fixtures.

Evidence: spine lines 74–78 and AD-2/AD-11; PRD FR-4 and FR-24.

### Deferred gates need executable entry and exit contracts

G-3, G-4, and G-6 are legitimate deferred decisions, but the spine does not state
which stories are blocked or what artifact closes each gate. Local and remote
implementation can therefore begin with incompatible assumptions about model
runtime, capability tiers, gateway schema, retry policy, provider region,
catalog packaging, or source identity.

**Disposition:** Convert each into a named spike/decision gate with owner, blocked
stories, evidence inputs, decision output, and revisit trigger. At minimum:

- G-3 blocks real Android/iOS local adapters, but not contract fakes.
- G-4 blocks the real remote adapter and gateway, but not a frozen fake gateway.
- G-6 blocks deterministic grounding and offline manual-search acceptance.

Evidence: spine lines 249–258 and AD-3/AD-11.

## Additional findings

- **Catalog delivery remains a divergence point.** AD-7 selects SQLDelight, but
  does not decide whether the first catalog is bundled, downloaded, or hybrid;
  how schema/data migrations are atomic; how storage budgets work; or how offline
  manual search survives failed updates. **Disposition:** extend G-6 with package
  delivery, safe default, update/rollback, schema migration, and storage evidence.

- **Image cleanup omits crash/process-death recovery.** AD-8 deletes on normal
  terminal events and expiry but does not bind startup scavenging, OS backup
  exclusion, crash recovery, or a maximum expiry. **Disposition:** amend AD-8 with
  bounded expiry, backup exclusion, and startup/periodic orphan cleanup.

- **AD-11 contains an enforcement loophole.** “Where the tooling can measure it”
  can make code disappear from the denominator, and no coverage tasks, contract
  report, device-evidence schema, or CI gate is named. **Disposition:** define the
  canonical per-module tasks and evidence manifest in the bootstrap/testing story;
  unsupported measurement requires documented physical-device evidence, not an
  untracked exclusion.

- **The environment diagram can be read as runtime-selectable arbitrary
  environments.** The same mobile client points to dev, staging, and prod.
  **Disposition:** bind endpoints to build variants/configuration signed by a
  trusted root, with production builds allowlisting production hosts and failing
  closed on invalid redirects.

## Capability coverage summary

| Capability | Coverage |
| --- | --- |
| Capture/import/photo lifecycle | **Partial:** ports and lifecycle exist; normalization and crash cleanup need binding. |
| Shared engine contract and independent local/remote work | **Strong:** AD-1 through AD-4 and AD-10/AD-15 establish the right dependency and ownership spine. Real work remains gated by G-3/G-4. |
| Routing, consent, cancellation, stale-result prevention | **Strong with gateway gap:** device orchestration is coherent; gateway idempotency, anonymous abuse control, and consent evidence remain open. |
| Confirmation and nutrition grounding | **Partial:** authority is correct; catalog delivery/update/offline packaging is deferred too broadly. |
| Results/recovery/offline continuation | **Covered structurally:** state and grounding rules support it, subject to the catalog package decision. |
| Diagnostics/evaluation/parity | **Fail:** AD-14 conflicts with required evaluation fields; preprocessing parity and evidence artifacts are not fixed. |
| Country/Tier-1 configuration | **Partial:** country/language independence is strong; signing/distribution operations and G-1 exit artifacts are incomplete. |
| Accessibility/localization/tone | **Fail in traceability:** UX requirements exist, but the spine misclassifies them and does not bind the Compose/native seam. |
| Testing/maintainability/agent boundaries | **Strong direction, partial enforcement:** AD-11 and AD-15 are correct but need canonical CI tasks and evidence formats. |
| Deployment and operations | **Fail:** environments are pictured, but the operating model is not decided or sufficiently deferred. |

## Handoff condition

Do not mark the spine final until the three critical findings are corrected and
the operational, accessibility/native-interop, preprocessing, and deferred-gate
findings are either amended into enforceable ADs or moved into Deferred with
explicit owners, blockers, and exit artifacts.
