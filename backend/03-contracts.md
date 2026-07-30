# Stage 3: Contract Release (B-5)

> Architecture spine: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`
> REST API spec: `backend/mobile-api/docs/06-rest-api-specification.md`
> Security design: `backend/mobile-api/docs/09-security-design.md`
> OpenRouter design: `backend/mobile-api/docs/07-openrouter-integration.md`
> E4 plan: `backend/mobile-api/docs/10-e4-implementation-plan.md`
> Model benchmark: `backend/benchmark/results/BENCHMARK-REPORT.md`
> Database: `backend/mobile-api/packages/database/src/schema/`
>
> Previous stage: `backend/02-skeleton.md`
> Next stage: `backend/04-e43-gateway.md`
> Stage type: contract release (language-neutral, versioned, immutable)

## Role

You are a Staff Backend Engineer responsible for API contracts and gateway design.

Architecture is approved. The system design, database schemas, and project skeleton are complete.

Your task is to produce the **contract release** — the single source of truth that both the mobile client and backend gateway implement against.

The mobile client has no remote inference module yet. The backend gateway is a skeleton. Both will implement from this contract.

## Context

The product is SuperNutrition — an AI-powered nutrition companion.

The core flow: user takes a photo of food → photo is sent to a remote inference gateway → gateway identifies foods via AI providers → returns candidates with confidence → user confirms.

Stage 1 (architecture) defined two API surfaces:
- **Surface A** — Inference Gateway (`/gw/v1`): photo upload, consent, transfer states
- **Surface B** — Product API (`/api/v1`): meals, progress, achievements

This contract release covers **Surface A only** (E4 epic).

## What must be produced

All output goes to `contracts/mobile-api/v1/`. The existing files (`schema.toml`, `compatibility-matrix.toml`, `RELEASE.md`, `fixtures/empty-analysis-request.toml`) must be read and updated.

### 1. Gateway Envelope Schema — `gateway-request.toml`, `gateway-response.toml`

The gateway envelope wraps every request and response. Design:

**Request (`POST /gw/v1/inference/photo`):**
- `attemptId: string` — client-generated UUIDv7, idempotency key
- `effectId: string` — deduplication key (if user retakes same photo, same effectId)
- `modality: "photo"` — enum, extensible for future text modality
- `payload`: scrubbed photo bytes, normalized size, max 8MB
- `consentReceipt`: consent object (see below)
- `marketJurisdiction: string` — ISO country code detected on device
- `processingRegion: string | null` — preferred region, null = auto

**Response (`201 Created`):**
- `contractVersion: string` — pinned to this release
- `requestId: string` — server-generated
- `attemptId: string` — echoed from request
- `outcome`: union of:
  - `CandidatesOutcome` — `outcomeType: "candidates"`, `candidates: CandidateDto[]`, `requiresConfirmation: boolean`
  - `AbstentionOutcome` — `outcomeType: "abstention"`, `reasonCode: string`, `userMessageKey: string`
  - `FailureOutcome` — `outcomeType: "failure"`, `failureCode: string`, `recoverable: boolean`

**Candidate shape (no nutrients — docs/06 §6.5):**
- `candidateId: string`
- `label: string` — food name
- `rank: number` — 1 is best
- `confidence: { score: number (0-1), band: string }` — band = "high" | "medium" | "low"
- `unresolvedAssumptions: { code: string, userMessageKey: string }[]`
- `provenance: { route, engineId, modelVersion, calibrationVersion, contractVersion }`

### 2. Error Taxonomy — `error-taxonomy.toml`

Produce a complete error catalog. Every error has:
- `httpStatus: number` — 400, 401, 402, 403, 413, 429, 500, 503
- `gatewayCode: string` — machine-readable, stable
- `userMessageKey: string` — i18n key for client to display
- `recoverable: boolean` — client may retry
- `retryAfter: number | null` — seconds, if applicable

Error codes from docs/06 §6.2:
```
validation-error         | 400 | recoverable
invalid-modality         | 400 | not recoverable
malformed-consent        | 400 | not recoverable
consent-expired          | 400 | not recoverable
unsupported-version      | 400 | not recoverable
authentication-failed    | 401 | not recoverable
session-expired          | 401 | recoverable (refresh)
budget-exceeded          | 402 | not recoverable (top-up)
consent-required         | 403 | not recoverable
insufficient-scope       | 403 | not recoverable
kill-switch              | 403 | not recoverable
content-too-large        | 413 | not recoverable (reduce)
rate-limited             | 429 | recoverable (retry-after)
provider-unavailable     | 503 | recoverable (retry)
provider-timeout         | 504 | recoverable (retry)
internal-error           | 500 | not recoverable
```

### 3. Consent Receipt Schema — `consent-receipt.toml`

```
consentReceipt {
  version: "1"
  digest: string          // SHA-256 of the photo payload
  modality: "photo"
  decision: "granted" | "declined"
  marketJurisdiction: string
  processingRegion: string | null
  recordedAt: timestamp (ISO 8601)
  expiresAt: timestamp (ISO 8601)
  consentCopyVersion: string | null
  retentionDays: number
}
```

Rules:
- Consent is per-attempt, per-modality
- digest mismatch → 400 malformed-consent
- expired consent → 400 consent-expired
- Consent receipt is recorded server-side for audit

### 4. Rate Limit Response — `rate-limit.toml`

```
HTTP 429:
{
  "gatewayCode": "rate-limited",
  "userMessageKey": "error.rate_limited",
  "recoverable": true,
  "retryAfter": 30          // seconds
}
Headers: Retry-After, X-RateLimit-Remaining, X-RateLimit-Reset
```

### 5. Transfer States — `transfer-states.toml`

Document the three transfer states and the cancellation endpoint:

**States:**
- `pre-transfer` — bytes not yet received
- `transferring` — bytes in flight
- `transferred` — bytes received, inference in progress

**Cancellation (`DELETE /gw/v1/inference/{attemptId}`):**
- Returns transfer state at time of cancellation
- `recallClaimed: false` — always false, gateway never guarantees deletion at provider
- `downstreamDeletionRequested: boolean` — true if gateway asked provider to delete
- If `pre-transfer` or `transferring`: server discards bytes, no provider call
- If `transferred`: server sends abort signal to provider, result discarded

### 6. Market Policy Response — `market-policy.toml`

```
GET /gw/v1/policy/market?jurisdiction={ISO}
Response:
{
  "marketJurisdiction": "DE",
  "processingRegion": "EU" | null,
  "residencyPosture": "consent-required" | "remote-prohibited" | "remote-permitted",
  "remotePermitted": boolean,
  "consentCopyVersion": "v1-de" | null,
  "acceptedInputLanguages": ["de", "en"]
}
```

### 7. Fixtures — `fixtures/` directory

Create one TOML fixture per scenario. Each fixture is a complete request/response pair:

- `photo-analysis-success.toml` — normal flow, candidates returned
- `photo-analysis-low-confidence.toml` — requiresConfirmation: true
- `photo-analysis-abstention.toml` — model could not identify
- `photo-analysis-consent-expired.toml` — consent-expired error
- `photo-analysis-rate-limited.toml` — rate-limited error
- `cancellation-pre-transfer.toml` — cancel before bytes sent
- `cancellation-transferred.toml` — cancel after transfer complete

### 8. Update Existing Files

- `schema.toml` — add version, modality support, consent policy fields
- `compatibility-matrix.toml` — verify both `mobile-v1 → backend-v1` and `backend-v1 → mobile-v1` pairs
- `RELEASE.md` — update with new digest, list all contract files

## Constraints

- Do NOT generate backend application code
- Do NOT generate HTTP handlers, controllers, or middleware
- Do NOT generate mobile client code
- This stage produces only contract files in `contracts/mobile-api/v1/`
- Every field must have an explicit type and optionality marker
- Every error code must have a stable machine-readable key (never change the key, change the message)
- The release is immutable once published
- Language-neutral: TOML for schemas, no TypeScript types in contract directory

## Output Files

Generate or update:

```
contracts/mobile-api/v1/
├── schema.toml                          # Updated
├── compatibility-matrix.toml             # Verified
├── RELEASE.md                            # Updated
├── gateway-request.toml                  # NEW
├── gateway-response.toml                 # NEW
├── error-taxonomy.toml                   # NEW
├── consent-receipt.toml                  # NEW
├── rate-limit.toml                       # NEW
├── transfer-states.toml                  # NEW
├── market-policy.toml                    # NEW
└── fixtures/
    ├── photo-analysis-success.toml       # NEW
    ├── photo-analysis-low-confidence.toml # NEW
    ├── photo-analysis-abstention.toml    # NEW
    ├── photo-analysis-consent-expired.toml # NEW
    ├── photo-analysis-rate-limited.toml   # NEW
    ├── cancellation-pre-transfer.toml     # NEW
    └── cancellation-transferred.toml      # NEW
```

## Verification

After generation, the release must satisfy:

1. Every field in every schema is typed and documented
2. No nutrients or nutrition data in any gateway response
3. Every error in the taxonomy maps to exactly one gatewayCode
4. Consent receipt schema covers all fields required by `09-security-design.md` §9.5
5. Each fixture is structurally valid (matches its schema)
6. `compatibility-matrix.toml` digest matches computed SHA-256 over sorted release files
7. The release is parseable by both backend and mobile without any language-specific tooling
