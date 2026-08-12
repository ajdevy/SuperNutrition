#!/usr/bin/env bash
set -euo pipefail

if test "$#" -ne 1; then
  printf 'usage: %s <output-directory>\n' "$0" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
source "$script_dir/lib/quality-common.sh"
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

ui_acceptance_apk_path="$(find "$repo_root/mobile/apps/androidApp/build/outputs/apk" -type f \
  -path '*/uiAcceptance/debug/*-uiAcceptance-debug.apk' -print -quit)"
ui_acceptance_test_apk_path="$(find "$repo_root/mobile/apps/androidApp/build/outputs/apk" -type f \
  -path '*/androidTest/uiAcceptance/debug/*-uiAcceptance-debug-androidTest.apk' -print -quit)"
poc_apk_path="$(find "$repo_root/mobile/apps/androidApp/build/outputs/apk" -type f \
  -path '*/poc/debug/*-poc-debug.apk' -print -quit)"
poc_test_apk_path="$(find "$repo_root/mobile/apps/androidApp/build/outputs/apk" -type f \
  -path '*/androidTest/poc/debug/*-poc-debug-androidTest.apk' -print -quit)"
test -n "$ui_acceptance_apk_path" && test -n "$ui_acceptance_test_apk_path" && \
  test -n "$poc_apk_path" && test -n "$poc_test_apk_path" || {
  printf 'ui-acceptance and PoC APK/test-APK artifacts must be built before evidence emission\n' >&2
  exit 1
}

find_result_xml() {
  local variant="$1"
  python3 - "$repo_root/mobile/apps/androidApp/build/outputs/androidTest-results/connected" "$variant" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
variant = sys.argv[2]
files = sorted(
    root.glob(f"debug/flavors/{variant}/TEST-*.xml"),
    key=lambda path: path.stat().st_mtime,
    reverse=True,
)
print(files[0] if files else "")
PY
}

read_xml_counts() {
  python3 - "$1" <<'PY'
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
}

ui_acceptance_result_xml="$(find_result_xml uiAcceptance)"
poc_result_xml="$(find_result_xml poc)"
test -n "$ui_acceptance_result_xml" && test -n "$poc_result_xml" || {
  printf 'connected ui-acceptance and PoC XML results are required; run connectedAndroidTest on the physical device first\n' >&2
  exit 1
}

read -r ui_acceptance_test_count ui_acceptance_failures ui_acceptance_errors ui_acceptance_skipped < \
  <(read_xml_counts "$ui_acceptance_result_xml")
test "$ui_acceptance_test_count" -gt 0 && test "$ui_acceptance_failures" = 0 && \
  test "$ui_acceptance_errors" = 0 && test "$ui_acceptance_skipped" = 0 || {
  printf 'connected ui-acceptance XML result is not a complete passing run: tests=%s failures=%s errors=%s skipped=%s\n' \
    "$ui_acceptance_test_count" "$ui_acceptance_failures" "$ui_acceptance_errors" "$ui_acceptance_skipped" >&2
  exit 1
}

read -r poc_test_count poc_failures poc_errors poc_skipped < <(read_xml_counts "$poc_result_xml")
test "$poc_test_count" -gt 0 && test "$poc_failures" = 0 && test "$poc_errors" = 0 && test "$poc_skipped" = 0 || {
  printf 'connected PoC XML result is not a complete passing run: tests=%s failures=%s errors=%s skipped=%s\n' \
    "$poc_test_count" "$poc_failures" "$poc_errors" "$poc_skipped" >&2
  exit 1
}

commit_sha="$(git -C "$repo_root" rev-parse HEAD)"
worktree_state="clean"
if test -n "$(git -C "$repo_root" status --porcelain)"; then
  worktree_state="dirty"
fi

ui_acceptance_artifact_digest="$(digest_files "$repo_root" \
  "${ui_acceptance_apk_path#"$repo_root/"}" \
  "${ui_acceptance_test_apk_path#"$repo_root/"}")"
poc_artifact_digest="$(digest_files "$repo_root" \
  "${poc_apk_path#"$repo_root/"}" \
  "${poc_test_apk_path#"$repo_root/"}")"
fixture_digest="$(digest_tree "$repo_root" mobile/apps/androidApp/src/androidTest/resources/fixtures)"
policy_digest="$(digest_tree "$repo_root" mobile/config/quality)"
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
artifact_digest=$(toml_string "$ui_acceptance_artifact_digest")
fixture_digest=$(toml_string "$fixture_digest")
policy_digest=$(toml_string "$policy_digest")
device_serial=$(toml_string "$android_serial")
device_os=$(toml_string "$device_os")
task_version=$(toml_string "connectedUiAcceptanceDebugAndroidTest-gradle-$gradle_version")
executed_at=$(toml_string "$executed_at")
test_result=$(toml_string "$ui_acceptance_result_xml")
test_count=$ui_acceptance_test_count
failures=$ui_acceptance_failures
errors=$ui_acceptance_errors
skipped=$ui_acceptance_skipped
EOF
done
cat >"$temporary_root/photo-android-inference-poc.evidence" <<EOF
schema=$(toml_string supernutrition.mobile.native-evidence.v1)
id=$(toml_string photo-android-inference-poc)
status=$(toml_string "$status")
worktree_state=$(toml_string "$worktree_state")
commit_sha=$(toml_string "$commit_sha")
artifact_digest=$(toml_string "$poc_artifact_digest")
fixture_digest=$(toml_string "$fixture_digest")
policy_digest=$(toml_string "$policy_digest")
device_serial=$(toml_string "$android_serial")
device_os=$(toml_string "$device_os")
task_version=$(toml_string "connectedPocDebugAndroidTest-gradle-$gradle_version")
executed_at=$(toml_string "$executed_at")
test_result=$(toml_string "$poc_result_xml")
test_count=$poc_test_count
failures=$poc_failures
errors=$poc_errors
skipped=$poc_skipped
EOF
mv "$temporary_root"/* "$output_root/"
rmdir "$temporary_root"

printf 'physical Android evidence: %s (%s, ui-acceptance=%s tests, PoC=%s tests, device=%s)\n' \
  "$status" "$worktree_state" "$ui_acceptance_test_count" "$poc_test_count" "$device_os"
