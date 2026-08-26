#!/usr/bin/env bash
# End-to-end photo flow against a running mobile-api (TEST_MODE).
# Steps 0-5: session -> policy -> inference -> catalog -> meals/photo -> meals.
# Conforms to contracts/mobile-api/v1: gateway headers, context envelope,
# consent-receipt fields, and meals/photo evidence shape.
# Requires: curl, python3, openssl, shasum. BASE defaults to 127.0.0.1:3000.
set -euo pipefail
BASE=${BASE:-http://127.0.0.1:3000}
IMG=${IMG:-/opt/claude/SuperNutrition/backend/benchmark/images/01-salad-bowl.jpg}

echo "== STEP 0: POST /gw/v1/session =="
curl -s -X POST "$BASE/gw/v1/session" | tee /tmp/sn-session.json
echo

echo "== STEP 1: GET /gw/v1/policy/market =="
curl -s "$BASE/gw/v1/policy/market?jurisdiction=ZZ&modality=photo"
echo

ATT=att_$(openssl rand -hex 12)
EFF=eff_$(openssl rand -hex 12)
REQ=req_$(openssl rand -hex 12)
DIGEST=sha256:$(shasum -a 256 "$IMG" | cut -d" " -f1)

ATT="$ATT" EFF="$EFF" DG="$DIGEST" python3 > /tmp/sn-ctx.json <<"PY"
import os,json,datetime
now=datetime.datetime.now(datetime.timezone.utc)
rec=(now-datetime.timedelta(minutes=5)).strftime("%Y-%m-%dT%H:%M:%SZ")
exp=(now+datetime.timedelta(minutes=10)).strftime("%Y-%m-%dT%H:%M:%SZ")
ctx={"contractVersion":"mobile-api/v1","attemptId":os.environ["ATT"],"effectId":os.environ["EFF"],"modality":"photo",
 "claimedMarketJurisdiction":"ZZ",
 "consentReceipt":{"version":"1","attemptId":os.environ["ATT"],"digest":os.environ["DG"],"modality":"photo",
  "purpose":"food-identification","decision":"granted","marketJurisdiction":"ZZ",
  "processingRegion":"fixture-region","consentCopyVersion":"photo-consent/fixture-1",
  "retentionVersion":"retention/2","retentionDays":0,"configurationVersion":"cfg/1",
  "recordedAt":rec,"expiresAt":exp},
 "inferenceContext":{"catalogCountry":"ZZ","mealType":"lunch","locale":"en-GB",
  "measurementSystem":"metric","configurationVersion":"cfg/1","priorMealSummary":None}}
print(json.dumps(ctx))
PY

echo
echo "== STEP 2: POST /gw/v1/inference/photo =="
TOKEN=$(python3 -c "import json;print(json.load(open(\"/tmp/sn-session.json\"))[\"accessToken\"])")
curl -s -X POST "$BASE/gw/v1/inference/photo" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Request-Id: $REQ" \
  -H "X-Contract-Version: mobile-api/v1" \
  -H "X-Correlation-Id: corr-$REQ" \
  -F "context=$(cat /tmp/sn-ctx.json)" \
  -F "payload=@$IMG;type=image/jpeg" | tee /tmp/sn-inference.json
echo

echo
echo "== STEP 3: GET /api/v1/catalog/search?q=avocado =="
curl -s "$BASE/api/v1/catalog/search?q=avocado&country=ZZ&limit=2" \
  -H "Authorization: Bearer $TOKEN" -H "X-Correlation-Id: corr-$REQ"
echo

echo
echo "== STEP 4: POST /api/v1/meals/photo =="
ATT="$ATT" TOKEN="$TOKEN" python3 > /tmp/sn-meal.json <<"PY"
import os,json
body={"occurredAt":"2026-08-02T08:00:00+00:00","localDate":"2026-08-02",
 "timezone":"Europe/London","mealType":"lunch",
 "items":[{"foodRef":"usda:fdc:171706","displayName":"Avocados, raw, California",
   "servingUnit":"g","servingAmount":"100","gramWeight":"100","confidence":"0.900","confirmedByUser":True}],
 "nutrition":{"origin":"client-grounded","catalogSource":"usda","datasetVersion":"SR Legacy 2021-10",
   "servingBasis":{"unit":"g","amount":"100","gramWeight":"100"},
   "calories":"167","proteinG":"1.96","fatG":"15.4","carbG":"8.64","fiberG":"6.8","sugarG":"0.3","sodiumMg":"8",
   "perItem":[{"foodRef":"usda:fdc:171706","calories":"167","proteinG":"1.96","fatG":"15.4","carbG":"8.64","partial":False}]},
 "attemptId":os.environ["ATT"],"configurationVersion":"cfg/1",
 "evidence":[{"mealItemId":None,"attemptId":os.environ["ATT"],"type":"candidate",
   "payload":{"label":"avocado"},
   "provenance":{"route":"remote","engineId":"gateway-openrouter","modelVersion":"openai/gpt-4o-mini",
     "calibrationVersion":"uncalibrated-g5-pending","contractVersion":"photo-inference-engine/v1"}}]}
print(json.dumps(body))
PY
curl -s -X POST "$BASE/api/v1/meals/photo" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: idem-$REQ" \
  -d @/tmp/sn-meal.json
echo

echo
echo "== STEP 5: GET /api/v1/meals =="
curl -s "$BASE/api/v1/meals" -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json;d=json.load(sys.stdin);print([(m[\"id\"],m[\"mealType\"],[i[\"displayName\"] for i in m[\"items\"]]) for m in d.get(\"data\",[])])"
echo
