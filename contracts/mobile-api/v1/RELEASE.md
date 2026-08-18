# mobile-api/v1

Immutable, language-neutral, data-only contract release for **Surface A — the
first-party inference gateway** (`/gw/v1`).

Both the mobile remote-inference adapter and the backend gateway implement
against this release. Neither product's types are authoritative; this is.

The product API (`/api/v1` — meals, journey, progress, achievements) is a
separate surface and needs its own release before any client consumes it.

## Contents

| File | Defines |
| --- | --- |
| `schema.toml` | Release identity, modality support, identifier formats, resolution authority, forbidden nutrition keys, file manifest |
| `gateway-request.toml` | Request envelope, multipart transport, headers, inference context, validation order |
| `gateway-response.toml` | Response envelope, the sealed outcome union, `Candidate`, confidence, provenance |
| `error-taxonomy.toml` | 21 error codes with HTTP status, i18n key, recoverability, retry-after |
| `consent-receipt.toml` | Consent receipt fields, validation mapping, fail-closed conditions |
| `rate-limit.toml` | 429 body and headers, limiter layers and their failure modes |
| `transfer-states.toml` | The three transfer states and cancellation semantics |
| `market-policy.toml` | Market policy response shape and posture semantics |
| `compatibility-matrix.toml` | Release digest, supported skew pairs, change policy |
| `fixtures/` | 8 canonical request/response pairs both products consume unchanged |

### Fixtures

| Fixture | Scenario |
| --- | --- |
| `empty-analysis-request` | Rejected: no normalized photo payload |
| `photo-analysis-success` | Candidates above threshold, `requiresConfirmation: false` |
| `photo-analysis-low-confidence` | Two passes, `requiresConfirmation: true` |
| `photo-analysis-abstention` | Model declines to identify; not a failure |
| `photo-analysis-consent-expired` | 400, expiry evaluated at resolution time |
| `photo-analysis-rate-limited` | 429 with headers, zero provider calls |
| `cancellation-pre-transfer` | 204, zero bytes sent |
| `cancellation-transferred` | 202, result suppressed, NO deletion requested, `recallClaimed: false` |

## Properties this release guarantees

- **No nutrition anywhere.** No gateway response carries calories, macros,
  micronutrients, serving basis, or gram weight. Nutrition comes from the
  versioned on-device catalog after the user confirms a canonical item (AD-7).
  The validator asserts the absence rather than trusting review.
- **Fail-closed markets.** Every market defaults to `unresolved` and permits no
  remote inference of any modality. Absence, `unresolved`, and expiry are
  equivalent *at resolution time* (AD-41 MP-1, MP-3).
- **Consent is bound to bytes.** The receipt carries the SHA-256 of the
  scrubbed, normalized payload. The gateway recomputes it over what it actually
  received; a mismatch means no provider call (AD-8).
- **Recall is never claimed.** `recallClaimed` is constrained to `false` in
  every transfer state, because reporting a deletion request as a deletion
  guarantee is a claim the gateway cannot keep.
- **Every gate precedes the provider call.** Size, type, auth, replay, rate,
  consent, market, and effect deduplication all reject without contacting a
  provider.

## Deliberate divergences from the stage brief

Recorded here because a reader comparing the brief to this release will notice
them, and each was a decision rather than an oversight.

| Brief | Release | Why |
| --- | --- | --- |
| `residencyPosture` has 3 values | 4 values including `unresolved` | The brief's set cannot express "not yet reviewed", so every unreviewed market would need a permissive value — the exact fail-open behaviour AD-41 prevents |
| `kill-switch` → 403, not recoverable | 503, recoverable, `retryAfter: 300` | `recoverable: false` tells clients to stop retrying permanently, so lifting the switch would never reach the installed base without an app update |
| `201 Created` | `200 OK` | The gateway durably creates nothing; a 201 with no resource and no `Location` describes something that does not exist |
| `attemptId` is a UUIDv7 | Opaque `att_` token | UUIDv7 embeds its creation time, weakening the opacity AD-14 requires of an identifier that appears in telemetry |
| `payload` inside the envelope | Multipart part | Binary in a JSON envelope inflates by a third and puts image bytes inside a structure that is logged elsewhere |
| Client sends `marketJurisdiction` and preferred `processingRegion` | Jurisdiction is an advisory hint; region is not accepted | Users consent to the resulting transfer, not to a routing picker, and a client must not be able to widen its own market by asserting a permissive one (AD-41 MP-7) |

## Release state

`v1` was a four-file stub. This release completes it **before** either product
implements gateway behaviour against it — the mobile remote-inference module
does not exist, and the backend gateway is route declarations only.

That window is now closed. Once either side ships behaviour bound to these
shapes, an incompatible change requires a new major release with a coexistence
window, server-before-client activation, a minimum-supported-version policy, a
rollback plan, and a removal story (AD-36).

## Digest

`compatibility-matrix.toml` carries a SHA-256 over **every sorted TOML file** in
this release. Each entry contributes its release-relative path, a zero byte, and
its bytes. While hashing `compatibility-matrix.toml`, its digest value is
replaced by the literal `<release-digest>` to avoid a self-reference.
`RELEASE.md` is documentation and is excluded.

Any edit to any contract file changes the digest, which is what makes a silent
edit to a released file detectable rather than merely discouraged.

## Validation

```bash
scripts/verify-mobile-api-contract.sh
```

Checks: required members present; every field explicitly typed, marked optional
or required, and documented; one `gatewayCode` per error with consistent
recoverability and retry semantics; no forbidden nutrition key in any response
surface or fixture; consent receipt covers every binding field from
`docs/09-security-design.md` §9.5; markets fail closed; `recallClaimed`
constrained to `false`; every fixture structurally valid and agreeing with the
taxonomy on status and recoverability; digest current; nothing executable in the
release.

Drift detection:

```bash
scripts/mobile-api-contract-negative-fixtures.sh
```

## Ownership

Per AD-36, `contracts/mobile-api/` is authored, versioned, approved, and
published by a contract principal independent of the mobile, backend, web, and
repository-policy owners. This release was drafted from the backend side and
**requires contract-owner approval before it is treated as published.**
