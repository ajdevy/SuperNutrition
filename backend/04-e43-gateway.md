# Stage 4: E4.3 — First-Party Gateway Boundary

> Contract release: `contracts/mobile-api/v1/` (B-5)
> Architecture spine: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`
> E4 plan: `backend/mobile-api/docs/10-e4-implementation-plan.md`
> REST API spec: `backend/mobile-api/docs/06-rest-api-specification.md`
> Security design: `backend/mobile-api/docs/09-security-design.md`
> OpenRouter design: `backend/mobile-api/docs/07-openrouter-integration.md`
> Model benchmark: `backend/benchmark/results/BENCHMARK-REPORT.md`
> Model config: `backend/mobile-api/config/model-profiles.toml`
>
> Previous stage: `backend/03-contracts.md`
> Stage type: gateway implementation (first E4 backend story)

## Role

You are a Senior Backend Engineer implementing the first-party inference gateway.

The contract is published at `contracts/mobile-api/v1/`. The architecture is approved. The project skeleton exists at `backend/mobile-api/` with all ports, DTOs, schemas, and test infrastructure.

Your task: implement the inference gateway. Every response shape, error code, and state machine must match the contract release exactly.

No one is waiting for mobile. The contract defines the boundary — implement both sides of it (what the gateway accepts and what it returns) and verify with contract tests.

## Context

SuperNutrition users take photos of food. The photo can be analyzed locally on device or remotely through this gateway.

The gateway is:
- A stateless HTTP service
- Responsible for auth, consent, rate limiting, AI provider routing, telemetry
- Forbidden from persisting photo bytes (AD-8)
- Forbidden from returning nutrients in responses (AD-7)
- Deployed with a kill switch (AD-24)

### Two surfaces reminder

The monolith serves two surfaces on the same process:

| Surface | Base path | Purpose |
|---------|-----------|---------|
| Inference Gateway | `/gw/v1` | Photo upload, consent, inference — THIS STAGE |
| Product API | `/api/v1` | Meals, progress, achievements — NOT this stage |

Both mount in the same Fastify instance. This stage implements only the gateway surface.

## What to implement

### 1. HTTP Server — `apps/api/src/server.ts`

Replace the Fastify stub with a working server:

- Create Fastify instance with logger (pino)
- Register middleware in order: correlation ID → request logging → auth → rate limiter → error handler
- Mount gateway routes at `/gw/v1`
- Mount product routes at `/api/v1` (stub — returns 501 Not Implemented)
- Error handler maps every thrown/returned error to the B-5 error taxonomy
- Start on `PORT` from config (default 3000)

Dependencies: `fastify`, `@fastify/cors`, `@fastify/multipart` (for photo upload)

### 2. Gateway Routes — `apps/api/src/routes/gateway/`

Implement:

**`POST /gw/v1/inference/photo`** — the main endpoint
1. Validate request against B-5 `gateway-request.toml`
2. Authenticate (JWT or test mode)
3. Check rate limit
4. Verify consent receipt (digest match, not expired, modality match)
5. Deduplicate by `effectId`
6. Route to inference orchestrator
7. Record telemetry to `inference_runs` table
8. Return response matching B-5 `gateway-response.toml`

**`DELETE /gw/v1/inference/{attemptId}`** — cancellation
1. Return current transfer state
2. If `transferring` or `transferred`: send abort signal to provider
3. Always return `recallClaimed: false`

**`POST /gw/v1/session`** — attestation exchange (test mode: hand out session immediately)

**`GET /gw/v1/policy/market`** — market policy resolution

### 3. Auth — `apps/api/src/security/`

Implement JWT auth with test mode:

**Test mode** (`TEST_MODE=true`, default for development):
- Any request with a valid-format token or no token gets authenticated
- Session is handed out immediately without verification
- Test tokens are tagged internally so they can never authorize a real remote transfer
- Server refuses to start with `TEST_MODE=true` when `NODE_ENV=production`

**Production mode**:
- RS256 JWT verification with JWKS
- 15-minute access tokens
- Token carries `sub`, `installationId`, `environment`, `scope` claims
- Refresh tokens: opaque 256-bit, SHA-256 hashed at rest, 30-day validity

### 4. Inference Orchestrator — `apps/api/src/modules/inference/domain/`

Implement the orchestrator from `docs/07-openrouter-integration.md §7.5`:

```
analyze(request):
  snapshot   = modelProfiles.current()
  budget     = Deadline(snapshot.total_deadline_ms)

  pass1 = run(pass=1, snapshot, budget)
  if pass1 is failure and not retryable: return pass1
  if pass1.confidence >= snapshot.confidence_threshold: return accept(pass1)

  if snapshot.max_passes < 2 or budget.exhausted: return needsConfirmation(pass1)

  pass2 = run(pass=2, snapshot, budget, context = enrich(request.context))
  if pass2 is failure: return needsConfirmation(pass1)
  if pass2.confidence >= snapshot.confidence_threshold: return accept(pass2)
  return needsConfirmation(best(pass1, pass2))
```

- Pass 1 models from `config/model-profiles.toml` (`fast-identify`, `fast-identify-fallback`)
- Pass 2 models from `config/model-profiles.toml` (`deep-identify`, `deep-identify-alt`)
- Retry logic: exponential backoff with full jitter, max 2 attempts per pass
- AbortSignal support for cancellation
- Deadline enforcement (pass_deadline_ms = 8000, total_deadline_ms = 20000)

### 5. OpenRouter Provider — `apps/api/src/modules/inference/infrastructure/openrouter/`

Replace the skeleton in `openrouter.provider.ts` with real HTTP calls:

- POST to `https://openrouter.ai/api/v1/chat/completions`
- Send photo as data URL in a multimodal message
- Use the food identification prompt from B-5 fixture patterns
- Parse structured JSON response matching `CandidateDto` shape
- Map OpenRouter errors to `InferenceFailure` types (timeout, rate-limited, provider-unavailable, content-rejected, malformed-response, cancelled, budget-exceeded)
- Report token usage and model info

Keep OpenRouter key isolation: key read once into adapter's closure, never on a config object.

### 6. Rate Limiter — `apps/api/src/middleware/`

Implement token bucket rate limiter with Redis:

| Layer | Limit | Scope |
|-------|-------|-------|
| IP | 100/min | Unauthenticated |
| Principal (gateway) | 20/min | Per user, gateway only |
| Principal (product) | 100/min | Per user, product only |
| Daily spend | Configurable | Per principal |
| Environment | Configurable | Per environment (staging/prod) |
| Auth | 5/min | Login endpoint |

- Use Redis with sliding window
- Return B-5 rate-limit response shape on 429
- Set `Retry-After` header

### 7. Consent Verification — `apps/api/src/security/`

Implement consent receipt verification pipeline:

1. Digest match — SHA-256 of received bytes matches receipt digest
2. Modality match — receipt modality equals request modality
3. Market posture — resolve via market policy, fail if `remote-prohibited`
4. Not expired — `expiresAt` in the future
5. Retention config — receipt declares retention days
6. Processing region — resolved server-side, not from client

Zero provider calls on verification failure.

### 8. Telemetry — `packages/database/src/repositories/inference-run.repository.ts`

Replace the stub `DrizzleInferenceRunRecorder` with real implementation:

- Each pass writes one `inference_runs` row
- Record: `attempt_id`, `pass_number`, `provider`, `model_id`, `model_profile_version`, `outcome`, `success`, `confidence`, `duration_ms`, `duration_bucket`, token counts, `estimated_cost_micros`, `failure_code`, `market_jurisdiction`, `processing_region`, `environment`
- No user ID, no prompt text, no response body in the telemetry row (AD-14)

### 9. Kill Switch — config/env

- `REMOTE_INFERENCE_ENABLED` env var (default `true`)
- When `false`: return `503 gatewayCode: "kill-switch"` with `retryAfter: 300`
- No deploy needed to toggle
- Log kill switch state at startup and on every state change

### 10. Gateway Pipeline — `apps/api/src/routes/gateway/index.ts`

The `GATEWAY_PIPELINE` array must execute in order:

1. `parse` — multipart parsing, size validation (8MB max), content-type validation
2. `authenticate` — JWT or test mode
3. `rate-limit` — check principal + IP limits
4. `consent` — verify consent receipt
5. `deduplicate` — check effectId cache
6. `orchestrate` — inference orchestrator
7. `normalize` — map internal types to contract response
8. `dispose` — delete photo bytes from memory
9. `telemetry` — record inference run (async, never blocks response)
10. `respond` — serialise contract response

## Test expectations

The existing 278 tests must remain green. Add:

- Contract tests: every B-5 fixture must pass against the running gateway
- Invariant tests: nutrients never appear in gateway responses
- Auth tests: test mode works, production mode rejects unauthenticated
- Rate limit tests: hitting limit returns B-5 shape
- Kill switch test: disabled returns 503 with kill-switch code
- Consent tests: expired, mismatched digest, wrong modality all rejected
- Pipeline tests: each step can reject independently

## Constraints

- Do NOT implement the Product API (`/api/v1`) — stub only
- Do NOT implement USDA integration — stub only
- Do NOT implement Coach, Gamification, Progress domains — not this stage
- Do NOT implement migration or seed scripts — database stage is complete
- Every error must map to a B-5 error code — no raw provider errors exposed
- Photo bytes never written to disk, database, or log
- OpenRouter API key isolated in provider adapter only
- Test mode must be impossible in production (boot assertion)
- `npm test` must pass after implementation

## Files to modify

```
apps/api/src/server.ts                          — Fastify bootstrap
apps/api/src/routes/gateway/index.ts            — pipeline + route registration
apps/api/src/routes/gateway/inference.routes.ts — NEW: POST + DELETE handlers
apps/api/src/routes/gateway/session.routes.ts   — NEW: POST /session
apps/api/src/routes/gateway/policy.routes.ts    — NEW: GET /policy/market
apps/api/src/security/index.ts                  — JWT + test mode implementation
apps/api/src/security/consent.ts                — NEW: consent verification
apps/api/src/middleware/index.ts                — middleware implementations
apps/api/src/middleware/rate-limiter.ts          — NEW: Redis token bucket
apps/api/src/middleware/error-handler.ts         — NEW: B-5 error mapper
apps/api/src/middleware/correlation-id.ts        — NEW: correlation ID
apps/api/src/modules/inference/domain/inference-orchestrator.ts         — NEW: orchestrator
apps/api/src/modules/inference/infrastructure/openrouter/openrouter.provider.ts  — real impl
packages/database/src/repositories/inference-run.repository.ts          — real impl
config/model-profiles.toml                      — verify model config
```
