# 6. REST API Specification

Two surfaces (§1.2). Both are JSON over HTTPS; both require a bearer token; they
differ in contract, versioning, and what they are allowed to persist.

## 6.1 Versioning

| Surface | Scheme | Breaking change |
| --- | --- | --- |
| Gateway `/gw/v1` | Version is the **contract release**. `contractVersion` is in the envelope; the path segment mirrors the major. | New major contract release, coexistence window, server-before-client activation, minimum-supported-version policy, rollback plan, removal story (AD-36) |
| Product `/api/v1` | URL major version | New `/api/v2` mounted alongside; `/api/v1` supported for a stated window |

Additive changes — new optional request fields, new response fields — are minor
and require no version bump. Unknown request fields are ignored; missing required
fields are rejected. That asymmetry is the contract's stated compatibility rule
and both surfaces follow it.

**Deprecation:** a deprecated version returns `Deprecation` and `Sunset` headers
and is tracked by a usage metric. Removal requires the metric to reach zero for
a stated period. Removing a version because a calendar date passed, while
clients still call it, is a client outage with a changelog entry.

## 6.2 Envelope and errors

Product API responses are the resource directly; errors use one shape:

```json
{
  "error": {
    "code": "MEAL_NOT_FOUND",
    "message": "Meal event not found.",
    "details": [{ "field": "id", "issue": "not_found" }],
    "correlationId": "01J8X4Q2K7R9M3P5T6V8W0Y2A1"
  }
}
```

`code` is a stable SCREAMING_SNAKE identifier clients may branch on. `message` is
human-readable, English, for developers — it is **not** user-facing copy, because
user-facing copy is localized on the client. `correlationId` appears in logs and
in every error, so a user report is traceable without asking for a screenshot.

**Error codes (Surface B only)**

This table is the Product API taxonomy, and it mirrors `ERROR_CODES` /
`ERROR_STATUS` in `packages/shared`. Surface A does **not** use it: the gateway
has its own envelope (`error.gatewayCode`) and its own released catalog in
`contracts/mobile-api/v1/error-taxonomy.toml`. The two are separate on purpose —
a product client branches on `code` and has never heard of a `gatewayCode` — so
a code from one surface must never appear on the other.

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | Request failed schema validation; `details` lists fields |
| 401 | `UNAUTHENTICATED` | Missing, expired, or malformed token |
| 401 | `TOKEN_REPLAYED` | Nonce or request ID already used |
| 403 | `FORBIDDEN` | Authenticated but not permitted |
| 403 | `MARKET_NOT_PERMITTED` | Posture absent, `unresolved`, expired, or `prohibited` |
| 403 | `CONSENT_REQUIRED` | Receipt valid but consent policy not met (modality, region, purpose, decision) |
| 403 | `TEST_MODE_FORBIDDEN` | Test-mode token attempted a real remote transfer |
| 404 | `*_NOT_FOUND` | `MEAL_NOT_FOUND`, `USER_NOT_FOUND`, `GOAL_NOT_FOUND`, `CATALOG_ITEM_NOT_FOUND` |
| 409 | `CONFLICT` | Idempotency or state conflict |
| 413 | `PAYLOAD_TOO_LARGE` | Upload exceeds the configured limit |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Content type not allowlisted |
| 422 | `CONTRACT_VERSION_UNSUPPORTED` | Contract version not in the supported matrix |
| 429 | `RATE_LIMITED` | Throttled; `Retry-After` and `X-RateLimit-*` set |
| 499 | `CLIENT_CANCELLED` | Client aborted; recorded for transfer-state semantics |
| 500 | `INTERNAL_ERROR` | Unexpected; no internal detail leaked |
| 501 | `NOT_IMPLEMENTED` | Route is declared by this spec but its stage has not shipped |
| 502 | `PROVIDER_UNAVAILABLE` | Upstream provider failed after retries |
| 503 | `PROVIDER_KILL_SWITCH` | Provider disabled by operator |
| 504 | `PROVIDER_TIMEOUT` | Upstream deadline exceeded |

Where the same condition exists on both surfaces the statuses may differ, and
the difference is the surface's own: the gateway answers `provider-unavailable`
with 503 because the release fixes it there, while Surface B answers 502. Only
Surface A is contract-frozen.

A 404 is returned for resources that exist but belong to another user. A 403
would confirm the resource exists.

## 6.3 Validation

Zod at the boundary, one schema per endpoint, in `presentation/`. The parsed
output is the **only** thing passed inward; raw `req.body` never crosses into
application code.

Order, and it matters: content-length → content-type → auth → rate limit →
schema → business rules. Rejecting an oversized body before parsing it is the
difference between a cheap 413 and an expensive one.

Shared rules: strings trimmed and length-bounded; numbers finite and ranged;
dates ISO-8601 with explicit timezone; enums closed; unknown keys stripped, not
rejected; arrays bounded (`meal_items` ≤ 50). Every bound is a named constant in
`shared/constants`, not a literal.

## 6.4 Pagination

Cursor-based on every collection. Offset pagination drifts when rows are
inserted mid-scroll, and the journey feed is exactly a list that grows at the
end the user is reading from.

Request: `?limit=20&cursor=<opaque>`; `limit` 1–100, default 20.

```json
{
  "data": [ /* … */ ],
  "page": { "nextCursor": "eyJ0IjoiMjAyNi0wNy0yOVQxMjowMDowMFoiLCJpIjoiMDFKOFg0In0", "hasMore": true }
}
```

The cursor encodes the sort key and tiebreaker ID, is opaque, and is validated
on receipt. It is not a base64 offset wearing a costume.

## 6.5 Surface A — Gateway `/gw/v1`

Contract-bound. Persists nothing about the payload.

### `POST /gw/v1/inference/photo`

Headers: `Authorization: Bearer`, `X-Request-Id` (one-time), `X-Contract-Version`,
`Idempotency-Key` (optional). `attemptId` and `effectId` travel inside the
`context` part, never as headers (contracts/mobile-api/v1/gateway-request.toml).

All three non-optional headers are enforced, in the release's
`validation_order`: contract version, then authentication, then request-id
replay. An absent header is as much a rejection as a wrong one — a caller who
omits `X-Contract-Version` is not asserting compatibility with anything.
`X-Request-Id` must match `^req_[A-Za-z0-9]{16,64}$` and is spent on first use;
a repeat is `401 TOKEN_REPLAYED` and is **not** recoverable, because a retry has
to carry a fresh identifier. The claim is made after authentication, so a
rejected caller cannot burn a legitimate client's request ID.

Body: `multipart/form-data` — `payload` (scrubbed normalized bytes) plus
`context` (JSON).

```jsonc
// context
{
  "contractVersion": "mobile-api/v1",
  "attemptId": "att_01J8X4Q2K7R9M3P5T6V8W0Y2A1",
  "effectId": "eff_01J8X4Q2K7R9M3P5T6V8W0Y2A2",
  "consentReceipt": {
    "version": "1",
    "attemptId": "att_01J8X4Q2K7R9M3P5T6V8W0Y2A1",
    "digest": "sha256:9f2b…",
    "modality": "photo",
    "purpose": "food-identification",
    "decision": "granted",
    "marketJurisdiction": "ZZ",
    "processingRegion": "fixture-region",
    "consentCopyVersion": "photo-consent/fixture-1",
    "retentionVersion": "retention/2",
    "retentionDays": 0,
    "configurationVersion": "cfg/2026-07-26.4",
    "recordedAt": "2026-07-29T10:14:02Z",
    "expiresAt": "2026-07-29T10:29:02Z"
  },
  "inferenceContext": {
    "catalogCountry": "ZZ",
    "mealType": "lunch",
    "locale": "de-DE",
    "measurementSystem": "metric"
  }
}
```

Response `200`:

```jsonc
{
  "contractVersion": "mobile-api/v1",
  "requestId": "req_01J8X4…",
  "attemptId": "att_01J8X4…",
  "resolvedMarketJurisdiction": "ZZ",
  "resolvedProcessingRegion": "fixture-region",
  "outcome": {
    "outcomeType": "candidates",
    "candidates": [{
      "candidateId": "food:banana",
      "label": "Banana",
      "rank": 1,
      "confidence": { "score": 0.93, "band": "high" },
      "unresolvedAssumptions": [
        { "code": "portion-unknown", "userMessageKey": "inference.assumption.portion_unknown" }
      ],
      "provenance": {
        "route": "remote",
        "engineId": "gateway-openrouter",
        "modelVersion": "openai/gpt-4o-mini",
        "calibrationVersion": "cal-2026-07",
        "contractVersion": "photo-inference-engine/v1"
      }
    }]
  }
}
```

`outcomeType` is `candidates`, `abstention`, or `failure`. **No response on this
endpoint carries calories, macros, or micronutrients** — asserted by a schema
test, mirroring `contracts/inference/v1`.

Errors: 400 `MALFORMED_CONSENT` when the receipt digest does not match the
computed digest of the received payload, or the receipt is structurally invalid;
403 `CONSENT_REQUIRED` when the receipt is valid but consent policy is not met;
403 `MARKET_NOT_PERMITTED` when the resolved posture forbids this modality;
400 `UNSUPPORTED_VERSION` on a contract version outside the supported matrix;
413 `CONTENT_TOO_LARGE` above the limit (taxonomy: contracts/mobile-api/v1/error-taxonomy.toml).

### `POST /gw/v1/inference/text`

Same envelope, `modality: "text"`, body is a bounded description string. Photo
and text permissions are resolved independently and a receipt carrying the wrong
modality's consent copy fails closed (MP-4).

**Not active in v1.** `schema.toml` lists `text` as a *reserved* modality, so the
route answers `400 invalid-modality` — "not supported by this release". It is
registered rather than absent deliberately: a 404 would say the path does not
exist and send a client hunting for a typo instead of falling back to manual
entry.

### `DELETE /gw/v1/inference/{attemptId}`

Cancellation and downstream deletion request. Semantics follow AD-6's three
transfer states:

| State at cancellation | Behavior | Response |
| --- | --- | --- |
| `PreTransfer` | Zero bytes sent | `204`, `bytesTransferred: 0` |
| `Transferring` | Transport aborted, downstream work cancelled | `202` |
| `Transferred` | Result suppressed, provider deletion requested | `202`, `recallClaimed: false` |

The `Transferred` response never claims sent bytes were recalled. Reporting a
deletion request as a deletion guarantee is a claim the gateway cannot keep, and
saying so plainly is a requirement rather than a caveat.

### `POST /gw/v1/session`

Exchanges an installation attestation for a short-lived token pair. Returns
`authMode` so the client knows whether it holds an attested or test-mode token.

### `GET /gw/v1/policy/market`

Returns the resolved `marketJurisdiction`, `processingRegion`, posture, and
per-modality permission with its consent copy version. Clients call this
**before** presenting a consent sheet, because MP-7 requires the resolved values
to be displayed before either choice.

`modality` is **required** (`photo` or `text`); `jurisdiction` is an optional
advisory hint. A missing modality is `400 VALIDATION_FAILED` rather than a
default: answering the photo posture to a client that asked about nothing is
exactly the cross-modality inference MP-4 forbids. Responses are `no-store` —
a posture expires at resolution time, so a cached permissive answer would
outlive the permission it reports.

## 6.6 Surface B — Product `/api/v1`

### Auth

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/auth/session` | Create session (attested, or test-mode when fenced) |
| `POST` | `/api/v1/auth/refresh` | Rotate; reuse of a rotated token revokes the family |
| `POST` | `/api/v1/auth/logout` | Revoke current session |
| `DELETE` | `/api/v1/auth/sessions` | Revoke all sessions for the user |

### Meals

`POST /api/v1/meals/photo`, `/manual`, `/voice` all create a `MealEvent` and
converge on one response type. Distinct paths exist because their request shapes
genuinely differ; nothing downstream branches on which was used.

```jsonc
// POST /api/v1/meals/manual
{
  "occurredAt": "2026-07-29T12:30:00Z",
  "localDate": "2026-07-29",
  "timezone": "Europe/Berlin",
  "mealType": "lunch",
  "items": [{
    "foodRef": "usda:fdc:171705",
    "displayName": "Banana, raw",
    "servingUnit": "g",
    "servingAmount": 118,
    "confirmedByUser": true
  }],
  "nutrition": {
    "origin": "client-grounded",
    "catalogSource": "usda",
    "datasetVersion": "usda-2026.1",
    "servingBasis": { "unit": "g", "amount": 118 },
    "calories": 105.0, "proteinG": 1.29, "fatG": 0.39,
    "carbG": 26.95, "fiberG": 3.07, "sugarG": 14.43, "sodiumMg": 1.18,
    "perItem": [ /* … */ ]
  }
}
```

`nutrition` is **required** for a confirmed meal and is persisted verbatim
(§1.5). A request whose `nutrition` lacks `catalogSource` or `datasetVersion` is
rejected with `VALIDATION_FAILED` — an ungrounded number must not enter the
database, and the boundary is the last place to stop it.

`POST /api/v1/meals/photo` additionally carries `attemptId`, `configurationVersion`,
and the `evidence` array so provenance is preserved. It does **not** carry image
bytes; those went to the gateway and no longer exist.

`POST /api/v1/meals/voice` carries the transcript and, where the client
performed text identification, the same evidence array. The transcript is
user-authored text: it is stored on the meal event, and it is never written to
logs or telemetry.

Response (all three):

```jsonc
{
  "id": "01J8X4…", "userId": "01J8X4…", "source": "manual",
  "status": "confirmed", "occurredAt": "2026-07-29T12:30:00Z",
  "localDate": "2026-07-29", "timezone": "Europe/Berlin",
  "mealType": "lunch", "revision": 1,
  "items": [ /* … */ ],
  "nutrition": { /* snapshot as persisted, with provenance */ },
  "rewards": { "xpAwarded": 10, "achievementsUnlocked": ["first_meal"],
               "streak": { "type": "daily_logging", "currentCount": 3 } },
  "coachMessage": { "type": "motivation", "messageKey": "coach.streak.day_three",
                    "params": { "days": 3 } },
  "createdAt": "2026-07-29T12:30:04Z"
}
```

`rewards` and `coachMessage` are best-effort. If a subscriber fails, they are
`null` and the meal is still created — logging a meal is the primary KPI and
must not depend on the achievement engine (§2.5).

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/v1/meals` | Cursor page; filters `from`, `to`, `mealType`, `source` |
| `GET` | `/api/v1/meals/{id}` | Full aggregate including evidence |
| `PATCH` | `/api/v1/meals/{id}` | Correction — creates revision N+1, returns the new event |
| `DELETE` | `/api/v1/meals/{id}` | Soft delete; triggers progress rebuild for that local date |

### Goals

```jsonc
// PATCH /api/v1/goals
{ "goalType": "maintain", "targetCalories": 2100, "targetProteinG": 130,
  "targetFatG": 70, "targetCarbG": 240, "activityLevel": "moderate",
  "effectiveFrom": "2026-07-30" }
```

Returns the new goal version with `effectiveFrom`/`effectiveTo`. `PATCH` is
honest about intent — the client sends a partial change — while the server
closes the current period and opens a new one (§4.3). `GET /api/v1/goals`
returns the current version; `?at=2026-06-01` returns the version in force then.

### Journey, Progress, Achievements

| Method | Path | Returns |
| --- | --- | --- |
| `GET` | `/api/v1/journey` | Merged reverse-chronological feed of meals, achievements, streak milestones; cursor-paged |
| `GET` | `/api/v1/progress?date=` | One day: totals, goal in force, adherence, meal count |
| `GET` | `/api/v1/progress/weekly?weekStart=` | Weekly totals, per-day breakdown, trend |
| `GET` | `/api/v1/progress/trends?from=&to=&metric=` | Time series for one metric |
| `GET` | `/api/v1/achievements` | All achievements with per-user unlock state and progress |
| `GET` | `/api/v1/coach/messages` | Cursor page of messages |

Journey is one endpoint rather than three client-merged lists because the merge
needs a stable total order across heterogeneous items, and doing that on the
client makes pagination incorrect at every boundary.

```jsonc
// GET /api/v1/progress?date=2026-07-29
{
  "localDate": "2026-07-29",
  "totals": { "calories": 1840.5, "proteinG": 122.3, "fatG": 61.2,
              "carbG": 198.7, "fiberG": 28.1 },
  "goal": { "id": "01J8X4…", "targetCalories": 2100, "targetProteinG": 130,
            "effectiveFrom": "2026-07-01" },
  "adherence": { "calories": 0.876, "protein": 0.941, "fat": 0.874, "carb": 0.828,
                 "status": "on_track" },
  "mealCount": 3,
  "computedAt": "2026-07-29T19:04:11Z"
}
```

### Users

| Method | Path | Notes |
| --- | --- | --- |
| `GET`/`PATCH` | `/api/v1/users/me` | Profile and preferences |
| `GET` | `/api/v1/users/me/consents` | Consent receipt history |
| `POST` | `/api/v1/users/me/export` | User-rights data export (AD-24) |
| `DELETE` | `/api/v1/users/me` | Erasure; returns an `ErasureReport` |

### Nutrition and Catalog

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/v1/catalog/search?q=&country=&limit=` | Catalog matches with source and dataset version |
| `GET` | `/api/v1/catalog/items/{foodId}` | Canonical item, nutrients per 100 g, serving options |
| `GET` | `/api/v1/catalog/packages?country=` | Active package metadata for client sync |

`/catalog/search` returns **matches, not verdicts**: candidates with provenance,
never a single auto-selected food. Auto-selecting a food identity is a forbidden
default under AD-40.

## 6.7 Idempotency

All POSTs that create or spend accept `Idempotency-Key`. The key, request digest,
and response are cached for 24 hours. A repeat with the same key and same digest
replays the stored status and body; a repeat with the same key and a different
digest is `409 CONFLICT`.

The digest is computed over the body with object keys canonically ordered, so
two serializations of the same request are the same request. A claim that has
neither completed nor failed is treated as **in flight** and also conflicts —
that is what stops two concurrent retries from both creating. A request that
fails releases its claim, so a retry may run.

The header is what a client uses to promise "this is the same request". Without
one the POST is a plain create: inventing a digest-based key for callers who did
not ask would silently collapse two genuinely separate identical meals into one.

This matters most on `POST /meals/*`: a retry over a flaky mobile connection
must not create two lunches, two XP grants, and a phantom streak day.

## 6.8 Headers

**Request:** `Authorization`, `Content-Type`, `X-Request-Id`, `X-Correlation-Id`,
`Idempotency-Key`, `Accept-Language`, `X-Client-Version`, and on the gateway
`X-Contract-Version`. `attemptId`/`effectId` are body fields of the gateway
`context`, never headers (contracts/mobile-api/v1).

**Response:** `X-Correlation-Id` (always), `X-RateLimit-Limit`,
`X-RateLimit-Remaining`, `X-RateLimit-Reset`, `Retry-After` on 429/503,
`Deprecation` and `Sunset` on deprecated versions.

The `X-RateLimit-*` trio is present on successful responses too, not only on a
429, so a client can back off *before* it is rejected rather than only learning
the limit by hitting it. On a 429 all four are required
(`contracts/mobile-api/v1/rate-limit.toml`). They report the numbers of the
layer that would reject, and never name the layer: one code (`rate-limited`)
regardless of which limiter fired, so a caller probing for headroom learns
nothing about the topology.

## 6.9 OpenAPI

An OpenAPI 3.1 document is **generated from the Zod schemas**, not written by
hand, and a CI check fails when the committed document and the generated one
differ. A hand-maintained spec describes the API as it was believed to be.
