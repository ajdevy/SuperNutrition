# Architecture Reviewer-Gate Resolution

Date: 2026-07-26

## Verdict

All critical and high-confidence findings were incorporated before finalization. The spine linter reports zero findings. Two current-version objections were rejected because primary current sources superseded the reviewer’s stale secondary evidence.

## Incorporated

- Corrected every FR/NFR capability range and restored the PRD’s G-1 through G-6 identities.
- Made local-first routing enforceable and modeled pre-transfer, transferring, and transferred cancellation.
- Added canonical engine/wire/telemetry schemas and golden fixtures.
- Added leased `PhotoRef` lifetime and at-most-once external effects.
- Fixed nutrition ID, unit, precision, rounding, aggregation, offline-package, signing, activation, and rollback ownership.
- Added atomic configuration precedence and an explicit country/catalog restart transition.
- Bound environments, installation authentication, token replay protection, rotation/revocation, and abuse controls.
- Added preprocessing parity and a compatible fallback for failed platform-managed models.
- Extended accessibility/localization rules across Compose and native bridges.
- Added complete gateway/provider deletion scope, jurisdictional inventory, user-rights evidence, incident controls, and operational ownership.
- Added explicit blocked work and exit artifacts for every PRD and UX gate.
- Added an atomic build-toolchain tuple and mandatory Android+iOS bootstrap proof.

## Post-finalization inline-review follow-up

Four additional inline review threads were resolved after the original gate:

- `epics.md` now contains six concrete epics, 21 bounded stories, a delivery
  order, and a populated requirements coverage map.
- AD-8 and its implementation guidance require EXIF/GPS/non-essential metadata
  scrubbing before the consent and remote-payload digest boundary.
- AD-12 and AD-20 separate catalog country, market jurisdiction, and processing
  region, including the corresponding UX and configuration contracts.
- AD-6 and FR-10 make post-local remote escalation a closed trigger set and
  record a typed eligibility reason; Gate G-5 cannot override the local-first
  invariant.

The follow-up spine lint remains at zero findings.

## Rejected with primary evidence

- **Compose Multiplatform 1.11.1 unpublished:** rejected. JetBrains’ current compatibility page identifies 1.11.1 and its supported platforms.
- **Koin 4.2.2 unpublished:** rejected. The signed current GitHub release is 4.2.2 and explicitly recommends upgrading from 4.2.0/4.2.1 resolver issues.

## Primary version sources

- https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- https://github.com/InsertKoinIO/koin/releases/tag/4.2.2
