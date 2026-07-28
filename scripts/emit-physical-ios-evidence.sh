#!/usr/bin/env bash
set -euo pipefail

if test "$#" -ne 1; then
  printf 'usage: %s <output-directory>\n' "$0" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
output_root="$1"
ios_udid="${IOS_UDID:-}"
derived_data_root="${IOS_DERIVED_DATA_ROOT:-$repo_root/mobile/apps/iosApp/build/DerivedData}"
ios_arm64_test_results_root="${IOS_ARM64_TEST_RESULTS_ROOT:-$repo_root/mobile/platform/photo-ios/build/test-results/iosArm64Test}"
framework_binary="$repo_root/mobile/apps/sharedKit/build/bin/iosArm64/debugFramework/SharedKit.framework/SharedKit"

command -v xcrun >/dev/null 2>&1 || {
  printf 'missing xcrun\n' >&2
  exit 1
}
command -v xcodebuild >/dev/null 2>&1 || {
  printf 'missing xcodebuild\n' >&2
  exit 1
}
command -v python3 >/dev/null 2>&1 || {
  printf 'missing python3\n' >&2
  exit 1
}
test -x "$repo_root/mobile/gradlew" || {
  printf 'missing mobile Gradle wrapper: %s\n' "$repo_root/mobile/gradlew" >&2
  exit 1
}
test -n "$ios_udid" || {
  printf 'IOS_UDID must name a physical iPhone\n' >&2
  exit 2
}

if xcrun simctl list devices 2>/dev/null | grep -Fq "$ios_udid"; then
  printf 'simulator UDID cannot emit physical iOS evidence: %s\n' "$ios_udid" >&2
  exit 1
fi
device_line="$(xcrun xctrace list devices 2>/dev/null | awk -v udid="$ios_udid" '
  /^== Devices ==$/ { online = 1; next }
  /^== / { online = 0 }
  online && index($0, udid) { print; exit }
' || true)"
test -n "$device_line" || {
  printf 'UDID is not an attached physical iOS device: %s\n' "$ios_udid" >&2
  exit 1
}

latest_result="$(python3 - "$derived_data_root/Logs/Test" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
files = sorted(root.rglob("*.xcresult"), key=lambda path: path.stat().st_mtime, reverse=True)
print(files[0] if files else "")
PY
)"
test -n "$latest_result" || {
  printf 'physical iOS xcresult is missing; run the physical xcodebuild test first\n' >&2
  exit 1
}

summary_json="$(xcrun xcresulttool get test-results summary --path "$latest_result" 2>/dev/null)" || {
  printf 'unable to read iOS test result: %s\n' "$latest_result" >&2
  exit 1
}
read -r test_count passed failed skipped < <(printf '%s' "$summary_json" | python3 -c '
import json
import sys

summary = json.load(sys.stdin)
print(
    summary.get("totalTestCount", 0),
    summary.get("passedTests", 0),
    summary.get("failedTests", 0),
    summary.get("skippedTests", 0),
)
')
test "$test_count" -gt 0 && test "$passed" -gt 0 && test "$failed" = 0 && test "$skipped" = 0 || {
  printf 'physical iOS xcresult is not a complete passing run: total=%s passed=%s failed=%s skipped=%s\n' \
    "$test_count" "$passed" "$failed" "$skipped" >&2
  exit 1
}

fixture_result="$(python3 - "$ios_arm64_test_results_root" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
files = sorted(
    root.rglob("*IosImageNormalizerFixtureTest.xml"),
    key=lambda path: path.stat().st_mtime,
    reverse=True,
)
print(files[0] if files else "")
PY
)"
test -n "$fixture_result" || {
  printf 'iosArm64 normalization fixture XML is missing; run :platform:photo-ios:iosArm64Test first\n' >&2
  exit 1
}

read -r fixture_test_count fixture_failures fixture_errors fixture_skipped < <(
  python3 - "$fixture_result" <<'PY'
from xml.etree import ElementTree
import sys

root = ElementTree.parse(sys.argv[1]).getroot()
print(
    root.attrib.get("tests", "0"),
    root.attrib.get("failures", "0"),
    root.attrib.get("errors", "0"),
    root.attrib.get("skipped", "0"),
)
PY
)
test "$fixture_test_count" -eq 3 && test "$fixture_failures" = 0 && \
  test "$fixture_errors" = 0 && test "$fixture_skipped" = 0 || {
  printf 'iosArm64 normalization fixture is not a complete passing run: tests=%s failures=%s errors=%s skipped=%s\n' \
    "$fixture_test_count" "$fixture_failures" "$fixture_errors" "$fixture_skipped" >&2
  exit 1
}

app_path="${IOS_APP_PATH:-}"
if test -z "$app_path"; then
  app_path="$(find "$derived_data_root" -type d -path '*/Debug-iphoneos/SuperNutrition.app' -print -quit)"
fi
test -d "$app_path" || {
  printf 'physical iOS app artifact is missing under Debug-iphoneos\n' >&2
  exit 1
}
test -f "$framework_binary" || {
  printf 'iosArm64 SharedKit framework binary is missing: %s\n' "$framework_binary" >&2
  exit 1
}

hash_stream() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  else
    shasum --algorithm 256 | awk '{print $1}'
  fi
}

hash_file() {
  local path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$path" | awk '{print $1}'
  else
    shasum --algorithm 256 "$path" | awk '{print $1}'
  fi
}

digest_tree() {
  local root_path="$1"
  test -d "$root_path" || {
    printf 'missing evidence directory: %s\n' "$root_path" >&2
    exit 1
  }
  {
    local absolute
    while IFS= read -r absolute; do
      printf '%s\0%s\0' "${absolute#"$repo_root/"}" "$(hash_file "$absolute")"
    done < <(find "$root_path" -type f -print | LC_ALL=C sort)
  } | hash_stream
}

toml_string() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  printf '"%s"' "$value"
}

artifact_digest="$({
  printf '%s\0%s\0' "mobile/apps/sharedKit/build/bin/iosArm64/debugFramework/SharedKit.framework/SharedKit" "$(hash_file "$framework_binary")"
  while IFS= read -r absolute; do
    printf '%s\0%s\0' "${absolute#"$repo_root/"}" "$(hash_file "$absolute")"
  done < <(find "$app_path" -type f -print | LC_ALL=C sort)
} | hash_stream)"
fixture_digest="$(digest_tree "$repo_root/mobile/platform/photo-ios/src")"
policy_digest="$(digest_tree "$repo_root/mobile/config/quality")"
commit_sha="$(git -C "$repo_root" rev-parse HEAD)"
worktree_state="clean"
if test -n "$(git -C "$repo_root" status --porcelain)"; then
  worktree_state="dirty"
fi
xcode_version="$(xcodebuild -version | sed -n '1p')"
gradle_version="$("$repo_root/mobile/gradlew" --version | sed -n 's/^Gradle \([0-9][0-9.]*\)$/\1/p' | head -n 1)"
executed_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

# A dirty checkout can produce useful local diagnostics but cannot become
# activation evidence. The explicit override preserves that distinction.
status="success"
if test "$worktree_state" != "clean"; then
  if test "${SUPERNUTRITION_ALLOW_DIRTY_EVIDENCE:-0}" = "1"; then
    status="local-only"
  else
    printf 'worktree is dirty; set SUPERNUTRITION_ALLOW_DIRTY_EVIDENCE=1 only for local-only output\n' >&2
    exit 1
  fi
fi

mkdir -p "$output_root"
temporary_root="${output_root}.tmp.$$"
rm -rf "$temporary_root"
mkdir -p "$temporary_root"
trap 'rm -rf "$temporary_root"' EXIT

for evidence_id in \
  photo-ios-camera-picker \
  photo-ios-normalization \
  photo-ios-lifecycle \
  photo-ios-accessibility; do
  cat >"$temporary_root/$evidence_id.evidence" <<EOF
schema=$(toml_string supernutrition.mobile.native-evidence.v1)
id=$(toml_string "$evidence_id")
status=$(toml_string "$status")
worktree_state=$(toml_string "$worktree_state")
commit_sha=$(toml_string "$commit_sha")
artifact_digest=$(toml_string "$artifact_digest")
fixture_digest=$(toml_string "$fixture_digest")
policy_digest=$(toml_string "$policy_digest")
device_serial=$(toml_string "$ios_udid")
device_os=$(toml_string "$device_line")
task_version=$(toml_string "xcodebuild-test-iosArm64-$xcode_version")
executed_at=$(toml_string "$executed_at")
test_result=$(toml_string "$latest_result")
test_count=$test_count
passed=$passed
failed=$failed
skipped=$skipped
fixture_task_version=$(toml_string "iosArm64Test-gradle-$gradle_version")
fixture_test_result=$(toml_string "$fixture_result")
fixture_test_count=$fixture_test_count
fixture_failures=$fixture_failures
fixture_errors=$fixture_errors
fixture_skipped=$fixture_skipped
EOF
done
mv "$temporary_root"/* "$output_root/"
rmdir "$temporary_root"

printf 'physical iOS evidence: %s (%s, %s tests, device=%s)\n' \
  "$status" "$worktree_state" "$test_count" "$ios_udid"
