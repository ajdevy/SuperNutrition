#!/usr/bin/env bash
set -euo pipefail

if test "$#" -ne 1; then
  printf 'usage: %s <output-directory>\n' "$0" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
output_root="$1"
android_bin="${ANDROID_ADB:-adb}"
gradle_bin="$repo_root/mobile/gradlew"
android_serial="${ANDROID_SERIAL:-}"

test -x "$gradle_bin" || {
  printf 'missing mobile Gradle wrapper: %s\n' "$gradle_bin" >&2
  exit 1
}
command -v "$android_bin" >/dev/null 2>&1 || {
  printf 'missing Android adb executable: %s\n' "$android_bin" >&2
  exit 1
}
test -n "$android_serial" || {
  printf 'ANDROID_SERIAL must name exactly one physical device\n' >&2
  exit 2
}

device_state="$($android_bin -s "$android_serial" get-state 2>/dev/null | tr -d '\r\n')"
test "$device_state" = "device" || {
  printf 'Android serial is not online: %s (%s)\n' "$android_serial" "$device_state" >&2
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

digest_files() {
  {
    local path
    for path in "$@"; do
      test -f "$repo_root/$path" || {
        printf 'missing evidence input: %s\n' "$path" >&2
        exit 1
      }
      printf '%s\0%s\0' "$path" "$(hash_file "$repo_root/$path")"
    done
  } | hash_stream
}

digest_tree() {
  local root_relative="$1"
  test -d "$repo_root/$root_relative" || {
    printf 'missing evidence directory: %s\n' "$root_relative" >&2
    exit 1
  }
  {
    local absolute relative
    while IFS= read -r absolute; do
      relative="${absolute#"$repo_root/"}"
      printf '%s\0%s\0' "$relative" "$(hash_file "$absolute")"
    done < <(find "$repo_root/$root_relative" -type f -print | LC_ALL=C sort)
  } | hash_stream
}

toml_string() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  printf '"%s"' "$value"
}

apk_path="$(find "$repo_root/mobile/apps/androidApp/build/outputs/apk" -type f \
  -path '*/uiAcceptance/debug/*-uiAcceptance-debug.apk' -print -quit)"
test_apk_path="$(find "$repo_root/mobile/apps/androidApp/build/outputs/apk" -type f \
  -path '*/androidTest/uiAcceptance/debug/*-uiAcceptance-debug-androidTest.apk' -print -quit)"
test -n "$apk_path" && test -n "$test_apk_path" || {
  printf 'acceptance APK and test APK must be built before evidence emission\n' >&2
  exit 1
}

result_xml="$(python3 - "$repo_root/mobile/apps/androidApp/build/outputs/androidTest-results/connected" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
files = sorted(root.rglob("TEST-*.xml"), key=lambda path: path.stat().st_mtime, reverse=True)
print(files[0] if files else "")
PY
)"
test -n "$result_xml" || {
  printf 'connected Android XML result is missing; run the physical test task first\n' >&2
  exit 1
}

read -r test_count failures errors skipped < <(python3 - "$result_xml" <<'PY'
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
test "$test_count" -gt 0 && test "$failures" = 0 && test "$errors" = 0 && test "$skipped" = 0 || {
  printf 'connected Android XML result is not a complete passing run: tests=%s failures=%s errors=%s skipped=%s\n' \
    "$test_count" "$failures" "$errors" "$skipped" >&2
  exit 1
}

commit_sha="$(git -C "$repo_root" rev-parse HEAD)"
worktree_state="clean"
if test -n "$(git -C "$repo_root" status --porcelain)"; then
  worktree_state="dirty"
fi

artifact_digest="$(digest_files \
  "mobile/apps/androidApp/build/outputs/apk/uiAcceptance/debug/androidApp-uiAcceptance-debug.apk" \
  "mobile/apps/androidApp/build/outputs/apk/androidTest/uiAcceptance/debug/androidApp-uiAcceptance-debug-androidTest.apk")"
fixture_digest="$(digest_tree mobile/apps/androidApp/src/androidTest/resources/fixtures)"
policy_digest="$(digest_tree mobile/config/quality)"
gradle_version="$("$gradle_bin" --version | sed -n 's/^Gradle \([0-9][0-9.]*\)$/\1/p' | head -n 1)"
android_model="$($android_bin -s "$android_serial" shell getprop ro.product.model | tr -d '\r')"
android_manufacturer="$($android_bin -s "$android_serial" shell getprop ro.product.manufacturer | tr -d '\r')"
android_release="$($android_bin -s "$android_serial" shell getprop ro.build.version.release | tr -d '\r')"
android_sdk="$($android_bin -s "$android_serial" shell getprop ro.build.version.sdk | tr -d '\r')"
device_os="${android_manufacturer}-${android_model}-android-${android_release}-api-${android_sdk}"
executed_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

# Dirty worktrees are useful for local diagnostics but cannot be activation
# evidence. A trusted producer must emit from a clean checkout; the explicit
# override keeps local validation possible without turning it into a claim.
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
  bootstrap-android-host \
  photo-android-camera-picker \
  photo-android-normalization \
  photo-android-lifecycle \
  photo-android-accessibility; do
  cat >"$temporary_root/$evidence_id.evidence" <<EOF
schema=$(toml_string supernutrition.mobile.native-evidence.v1)
id=$(toml_string "$evidence_id")
status=$(toml_string "$status")
worktree_state=$(toml_string "$worktree_state")
commit_sha=$(toml_string "$commit_sha")
artifact_digest=$(toml_string "$artifact_digest")
fixture_digest=$(toml_string "$fixture_digest")
policy_digest=$(toml_string "$policy_digest")
device_serial=$(toml_string "$android_serial")
device_os=$(toml_string "$device_os")
task_version=$(toml_string "connectedUiAcceptanceDebugAndroidTest-gradle-$gradle_version")
executed_at=$(toml_string "$executed_at")
test_result=$(toml_string "$result_xml")
test_count=$test_count
failures=$failures
errors=$errors
skipped=$skipped
EOF
done
mv "$temporary_root"/* "$output_root/"
rmdir "$temporary_root"

printf 'physical Android evidence: %s (%s, %s tests, device=%s)\n' \
  "$status" "$worktree_state" "$test_count" "$device_os"
