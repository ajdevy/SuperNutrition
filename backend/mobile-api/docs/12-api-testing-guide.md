# API Testing Guide

## Prerequisites

```bash
cd backend/mobile-api
npm install
# or if lock changed: npm ci
```

## Unit tests

```bash
npm test              # 537 tests, ~2.5s
npm run coverage      # 94%+ lines
npm run typecheck     # tsc --noEmit
npm run lint          # eslint
```

## Start server locally

```bash
# env (one time)
export APP_ENVIRONMENT=development
export DATABASE_URL=postgresql://localhost:5432/db
export REDIS_URL=redis://localhost:6380/0
export JWT_PRIVATE_KEY="dev-key-at-least-32-chars-here!!!!"
export JWT_PUBLIC_KEY="dev-key-at-least-32-chars-here!!!!!"
export JWT_ISSUER=supernutrition-dev
export TEST_MODE=true

# start
npm run dev
```

OpenRouter key is optional (without it inference returns `provider-unavailable`):

```bash
export OPENROUTER_API_KEY="sk-..."
```

## Health

```bash
curl http://localhost:3000/health/live     # {"status":"ok"}
curl http://localhost:3000/health/ready    # shows env + auth mode
```

## Session (test mode only)

```bash
curl -X POST http://localhost:3000/gw/v1/session
# → {"accessToken":"test.…","authMode":"test-mode",…}
```

## Market policy

```bash
# fixture market (remote permitted)
curl "http://localhost:3000/gw/v1/policy/market?jurisdiction=ZZ&modality=photo"

# unresolved market (remote denied)
curl "http://localhost:3000/gw/v1/policy/market?jurisdiction=US&modality=photo"
```

## Photo inference

The consent receipt must be **fresh** (expires ≤ 15 min after recordedAt) and match the exact photo digest.

Three headers are **required** and all three are enforced: `Authorization`
(test mode skips attestation, not authentication), `X-Contract-Version`, and a
one-time `X-Request-Id` matching `^req_[A-Za-z0-9]{16,64}$`. Reusing a request
ID is `401 token-replayed` and cannot be retried — generate a fresh one per
call. Helper script:

```bash
#!/usr/bin/env bash
# test-inference.sh
set -euo pipefail

NOW=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")
EXPIRE=$(python3 -c "from datetime import datetime, timedelta, timezone; print((datetime.now(timezone.utc) + timedelta(minutes=10)).strftime('%Y-%m-%dT%H:%M:%S.000Z'))")
ATTEMPT_ID="att_$(openssl rand -hex 16)"
EFFECT_ID="eff_$(openssl rand -hex 16)"
REQUEST_ID="req_$(openssl rand -hex 16)"
DIGEST="sha256:$(sha256sum "$1" | cut -d' ' -f1)"
TOKEN=$(curl -sX POST http://localhost:3000/gw/v1/session | jq -r .accessToken)

curl -s -X POST http://localhost:3000/gw/v1/inference/photo \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Contract-Version: mobile-api/v1" \
  -H "X-Request-Id: ${REQUEST_ID}" \
  -F "payload=@$1;type=image/jpeg" \
  -F "context={\"contractVersion\":\"mobile-api/v1\",\"attemptId\":\"${ATTEMPT_ID}\",\"effectId\":\"${EFFECT_ID}\",\"modality\":\"photo\",\"claimedMarketJurisdiction\":\"ZZ\",\"consentReceipt\":{\"version\":\"1\",\"attemptId\":\"${ATTEMPT_ID}\",\"digest\":\"${DIGEST}\",\"modality\":\"photo\",\"purpose\":\"food-identification\",\"decision\":\"granted\",\"marketJurisdiction\":\"ZZ\",\"processingRegion\":\"fixture-region\",\"consentCopyVersion\":\"photo-consent/fixture-1\",\"retentionVersion\":\"retention/2\",\"retentionDays\":0,\"configurationVersion\":\"config/1\",\"recordedAt\":\"${NOW}\",\"expiresAt\":\"${EXPIRE}\"},\"inferenceContext\":{\"catalogCountry\":\"US\",\"mealType\":\"lunch\",\"locale\":\"en-US\",\"measurementSystem\":\"imperial\",\"configurationVersion\":\"config/1\"}}"
```

```bash
chmod +x test-inference.sh
./test-inference.sh photo.jpg
```

Success (with OpenRouter key):
```json
{"outcomeType":"candidates","candidates":[{"label":"pizza",…}],"requiresConfirmation":false}
```

Failure (no key / network issue):
```json
{"outcomeType":"failure","failureCode":"provider-unavailable",…}
```

## Common error codes

Surface A only. `/api/v1` has its own taxonomy (docs/06 §6.2) and never returns
a `gatewayCode`.

| Code | Status | Why |
|---|---|---|
| `authentication-failed` | 401 | `Authorization` missing or too short to be a credential |
| `token-replayed` | 401 | `X-Request-Id` already used; retry needs a fresh one |
| `unsupported-version` | 400 | `X-Contract-Version` missing or outside the matrix |
| `validation-error` | 400 | malformed `X-Request-Id`, bad ids, duplicate effect |
| `invalid-modality` | 400 | `modality: text` — reserved in v1 |
| `kill-switch` | 503 | `REMOTE_INFERENCE_ENABLED=false` |
| `malformed-consent` | 400 | digest mismatch / bad timestamps |
| `consent-expired` | 400 | receipt older than 15 min |
| `consent-required` | 403 | posture denies / wrong modality |
| `market-not-permitted` | 403 | jurisdiction is unresolved |
| `rate-limited` | 429 | too many requests; carries `Retry-After` + `X-RateLimit-*` |
| `content-too-large` | 413 | photo > 8 MB |
| `unsupported-media-type` | 415 | not JPEG/PNG/WebP |
| `client-cancelled` | 499 | `DELETE /gw/v1/inference/:id` |

A provider failure is **not** an HTTP error: the request was valid and fully
processed, so it comes back inside a `200` envelope as
`outcome.outcomeType = "failure"` with `failureCode` set to one of the codes
above. A provider refusing the content is an `abstention` with
`reasonCode: "content-rejected-by-provider"`, not a failure.

## Contract validation

```bash
scripts/verify-mobile-api-contract.sh
scripts/mobile-api-contract-negative-fixtures.sh
```

## Product API — meals

Every `/api/v1` request needs a bearer token. In test mode the token is also the
installation identity, so reuse ONE token for a session: a new token is a new
account, and the meals you just logged belong to the old one.

```bash
TOKEN=$(curl -sX POST http://localhost:3000/gw/v1/session | jq -r .accessToken)
AUTH="Authorization: Bearer $TOKEN"
```

An unauthenticated request is `401 UNAUTHENTICATED`, not an empty list.

```bash
# Create. Nutrition is required and persisted verbatim; a snapshot without
# catalogSource or datasetVersion is 400 VALIDATION_FAILED (AD-7).
curl -sX POST http://localhost:3000/api/v1/meals/manual -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{
    "occurredAt":"2026-07-29T12:30:00Z","localDate":"2026-07-29",
    "timezone":"Europe/Berlin","mealType":"lunch",
    "items":[{"foodRef":"usda:fdc:171705","displayName":"Banana, raw",
              "servingUnit":"g","servingAmount":"118","confirmedByUser":true}],
    "nutrition":{"origin":"client-grounded","catalogSource":"usda",
      "datasetVersion":"usda-2026.1",
      "servingBasis":{"unit":"g","amount":"118","gramWeight":"118"},
      "calories":"105.000","proteinG":"1.290","fatG":null,"carbG":null,
      "fiberG":null,"sugarG":null,"sodiumMg":null,
      "perItem":[{"foodRef":"usda:fdc:171705","calories":"105.000",
                  "proteinG":null,"fatG":null,"carbG":null,"partial":false}]}}'
# → 201, rewards and coachMessage null until the subscribers exist

# List. Cursor paging; filters from, to, mealType, source.
curl -s "http://localhost:3000/api/v1/meals?limit=20" -H "$AUTH"
curl -s "http://localhost:3000/api/v1/meals?mealType=lunch&from=2026-07-01" -H "$AUTH"

curl -s http://localhost:3000/api/v1/meals/$ID -H "$AUTH"     # → 200, or 404 MEAL_NOT_FOUND

# Correct. Writes revision N+1; the original is retained but leaves the feed.
curl -sX PATCH http://localhost:3000/api/v1/meals/$ID -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"items":[...],"nutrition":{...}}'

curl -sX DELETE http://localhost:3000/api/v1/meals/$ID -H "$AUTH"   # → 204, then 404
```

`POST /api/v1/meals/photo` adds `attemptId` and `configurationVersion`;
`POST /api/v1/meals/voice` adds `transcript`. Both accept an `evidence` array,
which is validated and not yet persisted. Photo bytes never reach this surface —
they went to the gateway.

Product errors use the Product API taxonomy (`{"error":{"code":…}}`), not the
gateway one. A meal owned by another user answers `404 MEAL_NOT_FOUND`, never
403.

Every authenticated `/api/v1` response carries `X-RateLimit-Limit`,
`X-RateLimit-Remaining`, and `X-RateLimit-Reset` (per-user, 120/min), so a
client can back off before it is rejected. Exceeding it is
`429 RATE_LIMITED` with `Retry-After`. The layer fails **open**: if the limiter
is unavailable the request is served, because this surface is unpaid and a
cache outage should not stop a user seeing yesterday's lunch. The gateway fails
closed.

## The rest of Surface B (stub)

```bash
curl http://localhost:3000/api/v1/goals -H "$AUTH"
# → 501 {"error":{"code":"NOT_IMPLEMENTED",…}}
```

Declared-but-unshipped routes answer `501 NOT_IMPLEMENTED` rather than 404,
which would be indistinguishable from a typo. An unknown `/api/v1` path is a
`404 VALIDATION_FAILED` in the product taxonomy.
