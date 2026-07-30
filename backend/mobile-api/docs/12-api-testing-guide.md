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

The consent receipt must be **fresh** (expires ≤ 15 min after recordedAt) and match the exact photo digest. Helper script:

```bash
#!/usr/bin/env bash
# test-inference.sh
set -euo pipefail

NOW=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")
EXPIRE=$(python3 -c "from datetime import datetime, timedelta; print((datetime.utcnow() + timedelta(minutes=10)).strftime('%Y-%m-%dT%H:%M:%S.000Z'))")
ATTEMPT_ID="att_$(openssl rand -hex 16)"
EFFECT_ID="eff_$(openssl rand -hex 16)"
DIGEST="sha256:$(sha256sum "$1" | cut -d' ' -f1)"

curl -s -X POST http://localhost:3000/gw/v1/inference/photo \
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

| Code | Status | Why |
|---|---|---|
| `kill-switch` | 503 | `REMOTE_INFERENCE_ENABLED=false` |
| `malformed-consent` | 400 | digest mismatch / bad timestamps |
| `consent-expired` | 400 | receipt older than 15 min |
| `consent-required` | 403 | posture denies / wrong modality |
| `market-not-permitted` | 403 | jurisdiction is unresolved |
| `rate-limited` | 429 | too many requests |
| `content-too-large` | 413 | photo > 8 MB |
| `unsupported-media-type` | 415 | not JPEG/PNG/WebP |
| `client-cancelled` | 499 | `DELETE /gw/v1/inference/:id` |

## Contract validation

```bash
scripts/verify-mobile-api-contract.sh
scripts/mobile-api-contract-negative-fixtures.sh
```

## Product API (stub)

```bash
curl http://localhost:3000/api/v1/meals
# → 501 Not Implemented
```
