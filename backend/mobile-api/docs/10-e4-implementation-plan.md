# 10. E4 Implementation Plan

Epic E4 — *Remote inference and consent* — delivers the optional remote path
through the first-party gateway with explicit consent, jurisdiction controls, and
provider isolation.

Two of the three stories are client-side. This backend owns E4.3 outright and
owns the server half of the contract E4.1 and E4.2 depend on. Reading the epic as
"E4.3 is our story" and starting there is the sequencing mistake this document
exists to prevent.

## 10.1 Mapping

| Story | Owner | Backend responsibility |
| --- | --- | --- |
| **E4.1** Remote consent and transfer states | Client | Consent receipt schema and verification; market policy resolution endpoint; the three transfer-state semantics on the server side; receipt persistence |
| **E4.2** Remote mobile adapter | Client | The gateway envelope the adapter codes against; error taxonomy; rate-limit and version-mismatch responses the adapter's contract tests assert against |
| **E4.3** First-party gateway boundary | **Backend** | Everything: validation, authentication, replay, rate, region, retention, deletion, provider invocation, contract-v1 normalization |

The dependency runs backwards from the epic's numbering. E4.2's acceptance
criteria require Ktor MockEngine contract tests covering upload, cancellation,
timeout, redaction, version mismatch, rate limiting, and malformed responses —
the client cannot write those until the gateway's envelope and error taxonomy are
frozen. So the backend's first deliverable is a **published contract release**,
not a running service.

## 10.2 E4.1 — Remote consent and transfer states

### Backend responsibilities

1. **Consent receipt schema** — the fields in §5.3, as part of the contract
   release so both sides bind the same shape.
2. **Digest verification** — SHA-256 of the received payload must equal the
   receipt's `digest`; mismatch is `400 MALFORMED_CONSENT`. Server-side
   verification is what makes AD-8's binding real rather than a client promise.
3. **Market policy endpoint** — `GET /gw/v1/policy/market` returns resolved
   jurisdiction, processing region, posture, and per-modality permission with
   consent copy version, so the client can display them **before** either choice
   (MP-7).
4. **Transfer-state semantics** — `DELETE /gw/v1/inference/{attemptId}` behaves
   per §6.5: `PreTransfer` → zero bytes, `Transferring` → abort transport,
   `Transferred` → suppress result, request downstream deletion, and report
   `recallClaimed: false`.
5. **Duplicate effect rejection** — `(attemptId, effectId)` claimed once in
   Redis; a duplicate is `409 CONFLICT` and performs no provider call.
6. **Receipt persistence** — for audit and user-rights evidence.

### Components

`auth` (token, replay), `users` (receipt persistence), gateway route group
(digest verification, transfer states), `MarketPolicyResolver`,
`NonceStore` / `EffectStore` (Redis).

### Risks

| Risk | Mitigation |
| --- | --- |
| Digest computed over different bytes on each side | Digest is over the **scrubbed normalized payload**; the definition lives in the contract release with canonical fixtures, not in prose |
| Consent copy version drifts from the reviewed text | `consentCopyVersion` is required and validated against the policy entry; an unknown version fails closed |
| `Transferred` cancellation implies recall | Response field is literally `recallClaimed: false`; a test asserts no user-facing copy claims otherwise |
| Market policy absent at MVP | Fail-closed default means no market permits remote inference until reviewed. **This is correct behaviour, and it means E4 ships with remote off until G-1/G-4 supply postures** |

### Acceptance criteria (backend half)

- **Given** a receipt whose digest does not match the received payload, **when**
  the gateway validates, **then** it returns `403 CONSENT_REQUIRED` and makes
  zero provider calls.
- **Given** a market that is absent, `unresolved`, expired, or `prohibited`,
  **when** a transfer is attempted for either modality, **then** it returns
  `403 MARKET_NOT_PERMITTED` and makes zero provider calls, proven by a negative
  test over the transfer path rather than by interface state.
- **Given** a photo-permitted, text-prohibited market, **when** a text transfer
  is attempted, **then** it fails closed; and a transfer presenting the wrong
  modality's consent copy fails closed.
- **Given** an entry past `expiresAt`, **when** resolution runs, **then** it
  behaves as `unresolved` at resolution time.
- **Given** cancellation in each of the three transfer states, **then** the
  documented behaviour holds and `recallClaimed` is never `true`.
- **Given** a duplicate `(attemptId, effectId)`, **then** `409` and no provider
  call.
- **And** the resolved jurisdiction and processing region are retrievable before
  any consent decision.

## 10.3 E4.2 — Remote mobile adapter

### Backend responsibilities

The client owns the adapter. The backend owns what the adapter is written
against, and owes it **early**:

1. **Gateway envelope** in the contract release: request shape, response shape,
   `contractVersion`, `requestId`, sealed success/failure payload.
2. **Error taxonomy** (§6.2) — every code the adapter's contract tests assert.
3. **Canonical fixtures** for upload, cancellation, timeout, redaction, version
   mismatch, rate limiting, malformed response, and at-most-once effect
   behaviour. E4.2's acceptance criteria name these exact cases; they must exist
   as immutable released fixtures both sides consume unchanged (AD-17).
4. **Version-mismatch behaviour** — unsupported `contractVersion` is
   `422 CONTRACT_VERSION_UNSUPPORTED`; unknown fields ignored, missing required
   fields rejected.
5. **Rate-limit response shape** — `429` with `Retry-After` and `X-RateLimit-*`.

### Risks

| Risk | Mitigation |
| --- | --- |
| Client and backend both "at HEAD" and calling it compatible | AD-36 is explicit that updating both at HEAD is never sufficient. Every change tests new backend behaviour against every supported released client contract, and vice versa |
| Provider schema leaking into the mobile contract | The envelope is first-party only; the response mapper is the sole translation point, and no provider type is exported from `infrastructure/openrouter/` |
| Fixtures authored by the backend become backend-shaped | `contracts/mobile-api/` is authored by an independent contract principal (AD-36). The backend proposes; the contract owner publishes |

### Acceptance criteria (backend half)

- **Given** each named fixture case, **when** the gateway is exercised, **then**
  it produces exactly the released fixture response.
- **Given** every supported released contract version, **when** backend quality
  runs, **then** it validates its side of each one (AD-27).
- **Given** an unknown request field, **then** it is ignored; **given** a missing
  required field, **then** the request is rejected.

## 10.4 E4.3 — First-party gateway boundary

The backend's own story. Acceptance criteria from the epic: on a contract-v1
request, size/type, authentication, nonce/replay, rate, processing-region,
retention, and deletion controls are enforced **before provider invocation**;
raw photos are not durably stored or logged by default; responses are normalized
to contract-v1 evidence.

### Component build order

1. **Validation chain** (§9.4) — content-length, content-type, streamed size,
   magic bytes, dimensions. Cheapest rejection first.
2. **Authentication and replay** — token verification, `environment` claim
   match, one-time `X-Request-Id`, nonce claim in Redis.
3. **Rate limiting** — per-installation, per-environment, spend caps.
4. **Consent and market gate** — §9.5, all six conditions.
5. **Effect deduplication** — `(attemptId, effectId)` claimed once.
6. **Orchestrator invocation** — provider selection, two-pass confidence
   routing, retry, deadlines (document 7).
7. **Response normalization** — provider shape → contract-v1 evidence, with no
   nutrient fields.
8. **Disposal** — `finally` path on success, error, timeout, cancellation.
9. **Telemetry** — one `inference_runs` row per pass, allowlisted fields only.
10. **Kill switch** — operator flag returning `503`.

Order matters: every step from 1 to 5 must be able to reject the request without
having called a provider, because a provider call is the expensive, externally
visible, privacy-relevant act.

### Risks

| Risk | Severity | Mitigation |
| --- | --- | --- |
| A refactor introduces a persistence path for photos | High | Gateway route group has no `packages/database` dependency; lint rule enforces it; AD-31 conformance test asserts zero disk writes and zero payload rows across all terminal paths |
| Provider outage takes the product down | Medium | Client local engine and manual entry work without this service; kill switch; typed `503`; the product is designed to be useful with remote off |
| Cost runaway | Medium | Per-request, per-principal, per-environment caps; kill switch; cost dashboards required before production (AD-24) |
| Prompt injection via voice transcript | Medium | Untrusted text is a delimited data field, never concatenated into instructions; strict response schema; malformed output is a typed failure (§7.6) |
| `TEST_MODE` reaching production | High | Boot assertion exits non-zero; CI test asserts the process refuses to start; test-mode tokens cannot authorize a real transfer |
| Telemetry leaking payload data | High | Allowlist-driven serializer; negative test feeds every forbidden field and asserts none is emitted |
| Latency budget exceeded by two passes | Medium | Total deadline caps the whole attempt; pass 2 runs only if budget remains; second-pass yield is measured so its cost is reviewable |

### Acceptance criteria

- **Given** a contract-v1 request, **when** the gateway validates it, **then**
  size/type, authentication, nonce/replay, rate, processing-region, retention,
  and deletion controls are all enforced **before** any provider invocation,
  proven by a test asserting zero provider calls for each rejection cause.
- **Then** raw photos are not durably stored or logged: zero disk writes, zero
  database rows containing payload data, zero log lines containing payload
  content — across success, error, timeout, and cancellation.
- **Then** responses are normalized to contract-v1 evidence and contain no
  calories, macros, or micronutrients, asserted by a schema test.
- **And** a replayed `X-Request-Id` or nonce returns `401 TOKEN_REPLAYED` with
  zero provider calls.
- **And** an over-limit payload returns `413` before the body is read.
- **And** every terminal path disposes the payload buffer, proven by the AD-31
  conformance suite.
- **And** the kill switch disables remote inference without a deploy and returns
  `503 PROVIDER_KILL_SWITCH`.
- **And** each pass writes exactly one `inference_runs` row containing no user
  foreign key, no prompt text, and no response body.

## 10.5 Implementation order

The ordering constraint is that **nothing can be built until the product is
`active`**, and activation is its own atomic AD-37 story.

| # | Step | Gate before starting |
| --- | --- | --- |
| 0 | **Backend activation story** — pinned toolchain tuple, frozen deps and lock, nonempty inventory, read-only `quality-check`, nonzero tests and coverage, artifact, owners, deploy/rollback skeleton | AD-37 reviewed toolchain story |
| 1 | Shared kernel, config, logging, error mapper, correlation IDs | Step 0 |
| 2 | Database package: schema, migrations, repositories | Step 1 |
| 3 | Auth module including the `TEST_MODE` fence and its boot-assertion test | Step 1 |
| 4 | **Contract release proposal** — gateway envelope, error taxonomy, consent receipt schema, canonical fixtures → to the contract principal | Step 1; **unblocks client E4.2** |
| 5 | Market policy resolver and `GET /gw/v1/policy/market` | Steps 3–4; **unblocks client E4.1** |
| 6 | Inference module: ports, orchestrator, model profiles | Steps 1–2 |
| 7 | OpenRouter adapter and response mapper | Step 6 |
| 8 | Gateway route group: validation → auth → rate → consent → dedup → orchestrate → normalize → dispose | Steps 3–7 |
| 9 | Telemetry: `inference_runs`, allowlist serializer, negative tests | Step 8 |
| 10 | AD-31 conformance suite: disposal, consent, replay, logging, transfer states, duplicate effects | Steps 8–9 |
| 11 | Operations: SLOs, alerts, dashboards, kill switch, runbooks, **recorded rollback drill** | Step 10 |
| 12 | Production enablement | Step 11 + G-4 signed + privacy/legal approval |

**Step 4 is the critical path for the whole epic**, not step 8. Two client
stories are blocked on it, and it is cheap — a schema proposal, not a service.
Pull it as early as step 1 allows.

Steps 0–10 can proceed with markets fail-closed, exercised against fixture
markets. Steps 11–12 are where G-4 becomes binding.

## 10.6 What E4 does not deliver

Stated so the epic's boundary is not quietly widened:

- **No product API.** Meals, journey, progress, achievements, and coaching are
  Surface B and belong to later epics. E4 is the gateway only.
- **No public accuracy claim.** Confidence thresholds are G-5. Until then the
  routing threshold is a development placeholder and no marketing or in-product
  claim rests on it.
- **No server-side nutrition calculation.** Gated on G-2 and on a shared
  calculation contract release (§8.4).
- **No correction telemetry.** Off until G-6 closes.
- **No production remote path** until the AD-24 checklist and G-4 are complete.
  Shipping E4 with markets fail-closed is the expected, correct outcome — the
  code path is proven and no market has yet been reviewed to permit it.
