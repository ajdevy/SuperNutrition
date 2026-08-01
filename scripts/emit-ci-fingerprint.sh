#!/usr/bin/env bash
set -euo pipefail

if test "$#" -ne 2; then
  printf 'usage: %s <job-id> <output-file>\n' "$0" >&2
  exit 2
fi

job_id="$1"
output_file="$2"
case "$job_id" in
  mobile-unit-coverage)
    ;;
  *)
    printf 'unsupported CI fingerprint job: %s\n' "$job_id" >&2
    exit 2
    ;;
esac

repo_root="${SUPERNUTRITION_CI_FINGERPRINT_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/quality-common.sh"
git -C "$repo_root" rev-parse --git-dir >/dev/null 2>&1 || {
  printf 'CI fingerprint root is not a Git checkout: %s\n' "$repo_root" >&2
  exit 1
}

digest_paths() {
  {
    local relative
    for relative in "$@"; do
      test -f "$repo_root/$relative" || {
        printf 'missing fingerprint input: %s\n' "$relative" >&2
        exit 1
      }
      printf '%s\0%s\0' "$relative" "$(hash_file "$repo_root/$relative")"
    done
  } | hash_stream
}

commit_sha="$(git -C "$repo_root" rev-parse HEAD)"
workflow_sha="${GITHUB_SHA:-local}"
if test -n "${GITHUB_SHA:-}" && test "$GITHUB_SHA" != "$commit_sha"; then
  printf 'checked out commit %s does not match workflow SHA %s\n' "$commit_sha" "$GITHUB_SHA" >&2
  exit 1
fi

mobile_lifecycle="$(lifecycle_value "$repo_root" mobile/product-lifecycle.toml)"
web_lifecycle="$(lifecycle_value "$repo_root" web/landing/product-lifecycle.toml)"
backend_lifecycle="$(lifecycle_value "$repo_root" backend/mobile-api/product-lifecycle.toml)"
test -n "$mobile_lifecycle" && test -n "$web_lifecycle" && test -n "$backend_lifecycle"

root_boundary_digest="$(digest_paths \
  contracts/root-policy/v1/schema.toml \
  contracts/root-policy/v1/boundaries.toml \
  contracts/root-policy/v1/ownership.toml \
  scripts/verify-root-boundaries.sh)"
lifecycle_digest="$(digest_paths \
  mobile/product-lifecycle.toml \
  web/landing/product-lifecycle.toml \
  backend/mobile-api/product-lifecycle.toml)"
contract_catalog_digest="$(digest_paths \
  contracts/mobile-api/v1/schema.toml \
  contracts/mobile-api/v1/compatibility-matrix.toml \
  contracts/mobile-api/v1/fixtures/empty-analysis-request.toml \
  contracts/inference/v1/schema.toml \
  contracts/inference/v1/compatibility-matrix.toml \
  contracts/inference/v1/RELEASE.md \
  contracts/inference/v1/fixtures/capability-ready.json \
  contracts/inference/v1/fixtures/abstention-result.json \
  contracts/inference/v1/fixtures/candidate-result.json \
  contracts/inference/v1/fixtures/failure-result.json \
  contracts/inference/v1/fixtures/telemetry-event.json \
  contracts/inference/v2/schema.toml \
  contracts/inference/v2/compatibility-matrix.toml \
  contracts/inference/v2/RELEASE.md \
  contracts/inference/v2/fixtures/capability-ready.json \
  contracts/inference/v2/fixtures/photo-component-estimated-result.json \
  contracts/inference/v2/fixtures/photo-component-not-evaluable-result.json \
  contracts/inference/v2/fixtures/text-component-not-evaluable-result.json \
  contracts/inference/v2/fixtures/abstention-result.json \
  contracts/inference/v2/fixtures/failure-result.json \
  contracts/inference/v2/fixtures/telemetry-event.json \
  contracts/interaction-policy/v1/schema.toml \
  contracts/interaction-policy/v1/compatibility.toml \
  contracts/interaction-policy/v1/fixtures/defaults.toml)"
product_identity_digest="$(digest_paths \
	contracts/product-identity/v1/schema.toml \
	contracts/product-identity/v1/compatibility.toml \
	contracts/product-identity/v1/fixtures/identities.toml \
	contracts/product-identity/v1/RELEASE.md \
	contracts/product-identity/v1/metadata.toml \
	mobile/config/product-identity.toml)"
inventory_digest="$(digest_paths \
  mobile/config/architecture/module-boundaries.toml \
  mobile/config/quality/module-matrix.toml \
  mobile/config/quality/ui-evidence-profile.toml \
  mobile/config/quality/positive-scenarios.toml \
  mobile/config/quality/journeys.toml \
  mobile/config/quality/platform-contracts.toml \
  mobile/config/quality/native-gaps.toml \
  mobile/config/quality/coverage.toml \
  mobile/config/quality/suppression-registry.toml \
  mobile/config/quality/interaction-policy.toml \
  mobile/config/quality/evidence-bindings.toml)"
flow_fingerprint="$(digest_tree "$repo_root" mobile/.maestro)"

mkdir -p "$(dirname "$output_file")"
temporary_file="${output_file}.tmp.$$"
trap 'rm -f "$temporary_file"' EXIT
cat >"$temporary_file" <<EOF
schema=supernutrition.ci.fingerprint.v1
job_id=$job_id
status=success
commit_sha=$commit_sha
workflow_sha=$workflow_sha
mobile_lifecycle=$mobile_lifecycle
web_lifecycle=$web_lifecycle
backend_lifecycle=$backend_lifecycle
root_boundary_digest=$root_boundary_digest
lifecycle_digest=$lifecycle_digest
contract_catalog_digest=$contract_catalog_digest
product_identity_digest=$product_identity_digest
inventory_digest=$inventory_digest
flow_fingerprint=$flow_fingerprint
EOF
mv "$temporary_file" "$output_file"
