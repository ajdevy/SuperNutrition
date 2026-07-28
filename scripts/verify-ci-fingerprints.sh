#!/usr/bin/env bash
set -euo pipefail

fingerprint_root="${SUPERNUTRITION_CI_FINGERPRINT_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
expected_sha="${SUPERNUTRITION_EXPECTED_SHA:-${GITHUB_SHA:-}}"
jobs=(quality-linux ui-journeys-android quality-apple ui-journeys-ios quality-web-landing quality-backend)
shared_fields=(
  schema
  status
  commit_sha
  workflow_sha
  mobile_lifecycle
  web_lifecycle
  backend_lifecycle
  root_boundary_digest
  lifecycle_digest
  contract_catalog_digest
  product_identity_digest
  inventory_digest
  flow_fingerprint
)

field() {
  local name="$1"
  local file="$2"
  sed -n "s/^${name}=//p" "$file" | head -n 1
}

fail() {
  printf 'ci-fingerprint: %s\n' "$1" >&2
  exit 1
}

test -d "$fingerprint_root" || fail "missing fingerprint directory: $fingerprint_root"
for job in "${jobs[@]}"; do
  test -f "$fingerprint_root/$job.fingerprint" || fail "missing fingerprint for $job"
done

report_count="$(find "$fingerprint_root" -maxdepth 1 -type f -name '*.fingerprint' -print | wc -l | tr -d ' ')"
test "$report_count" = "6" || fail "fingerprint directory must contain exactly six job reports"

if test -z "$expected_sha"; then
  expected_sha="$(field commit_sha "$fingerprint_root/${jobs[0]}.fingerprint")"
fi
printf '%s' "$expected_sha" | grep -Eq '^[0-9a-f]{40}$' || fail "expected SHA is not a full commit SHA"

for job in "${jobs[@]}"; do
  report="$fingerprint_root/$job.fingerprint"
  field job_id "$report" | grep -Fxq "$job" || fail "$report has the wrong job id"
  field schema "$report" | grep -Fxq 'supernutrition.ci.fingerprint.v1' || fail "$report has an unknown schema"
  field status "$report" | grep -Fxq success || fail "$report is not successful"
  field commit_sha "$report" | grep -Fxq "$expected_sha" || fail "$job is bound to a different commit SHA"
  if test -n "${GITHUB_SHA:-}"; then
    field workflow_sha "$report" | grep -Fxq "$GITHUB_SHA" || fail "$job is bound to a different workflow SHA"
  fi
  for digest_name in root_boundary_digest lifecycle_digest contract_catalog_digest product_identity_digest inventory_digest flow_fingerprint; do
    field "$digest_name" "$report" | grep -Eq '^[0-9a-f]{64}$' || fail "$job has an invalid $digest_name"
  done
done

baseline="${fingerprint_root}/${jobs[0]}.fingerprint"
for job in "${jobs[@]:1}"; do
  report="$fingerprint_root/$job.fingerprint"
  for name in "${shared_fields[@]}"; do
    test "$(field "$name" "$report")" = "$(field "$name" "$baseline")" ||
      fail "$job disagrees with ${jobs[0]} on $name"
  done
done

printf '%s\n' 'ci-fingerprint: OK (six successful jobs share one commit and policy fingerprint set)'
