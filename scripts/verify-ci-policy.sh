#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
workflow="${SUPERNUTRITION_CI_WORKFLOW:-$repo_root/.github/workflows/ci-required.yml}"

test -f "$workflow" || {
  printf 'missing CI workflow: %s\n' "$workflow" >&2
  exit 1
}

WORKFLOW_PATH="$workflow" python3 - <<'PY'
import os
import re
import sys
from pathlib import Path

path = Path(os.environ["WORKFLOW_PATH"])
text = path.read_text(encoding="utf-8")
lines = text.splitlines()

def fail(message: str) -> None:
    print(f"ci-policy: {message}", file=sys.stderr)
    raise SystemExit(1)

if not re.search(r"^on:\s*$", text, re.MULTILINE):
    fail("workflow must declare a top-level on block")
for trigger in ("push", "pull_request", "merge_group"):
    if not re.search(rf"^  {re.escape(trigger)}:\s*$", text, re.MULTILINE):
        fail(f"workflow is missing the {trigger} trigger")
if re.search(r"^\s+paths(?:-ignore)?:\s*$", text, re.MULTILINE):
    fail("path filters are forbidden")
if re.search(r"^\s+continue-on-error:\s*", text, re.MULTILINE):
    fail("continue-on-error is forbidden")
for line in lines:
    if "uses:" in line and not re.search(r"@[0-9a-f]{40}(?:\s+#.*)?$", line):
        fail(f"every GitHub Action reference must be full-SHA pinned: {line.strip()}")

jobs_start = next((index for index, line in enumerate(lines) if line == "jobs:"), None)
if jobs_start is None:
    fail("workflow must declare a top-level jobs block")

job_starts = [
    (index, match.group(1))
    for index, line in enumerate(lines[jobs_start + 1:], jobs_start + 1)
    if (match := re.match(r"^  ([a-z0-9-]+):\s*$", line))
]
expected = {
    "quality-linux",
    "ui-journeys-android",
    "quality-apple",
    "ui-journeys-ios",
    "quality-web-landing",
    "quality-backend",
    "ci-required",
}
actual = {job for _, job in job_starts}
if actual != expected:
    fail(f"canonical job set mismatch: expected {sorted(expected)}, got {sorted(actual)}")

blocks = {}
for offset, (start, job) in enumerate(job_starts):
    end = job_starts[offset + 1][0] if offset + 1 < len(job_starts) else len(lines)
    blocks[job] = "\n".join(lines[start:end])

for job in sorted(expected - {"ci-required"}):
    block = blocks[job]
    if not re.search(r"uses:\s+actions/checkout@[0-9a-f]{40}(?:\s+#.*)?$", block, re.MULTILINE):
        fail(f"{job} must use a SHA-pinned actions/checkout")
    if re.search(r"^    runs-on:\s+[^\n]*latest\s*$", block, re.MULTILINE):
        fail(f"{job} must use a concrete runner image, not a floating latest label")

for job, script, artifact in (
    ("ui-journeys-android", "scripts/run-ui-journeys-android.sh", "supernutrition-ui-android-evidence"),
    ("ui-journeys-ios", "scripts/run-ui-journeys-ios.sh", "supernutrition-ui-ios-evidence"),
):
    block = blocks[job]
    if f"run: {script}" not in block:
        fail(f"{job} must invoke its lifecycle-aware acceptance runner")
    if not re.search(r"uses:\s+actions/upload-artifact@[0-9a-f]{40}(?:\s+#.*)?$", block, re.MULTILINE):
        fail(f"{job} must upload its allowlisted evidence artifact")
    if f"name: {artifact}" not in block or "retention-days: 3" not in block:
        fail(f"{job} must use the short-retention evidence artifact contract")

for job in sorted(expected - {"ci-required"}):
    block = blocks[job]
    if f"scripts/emit-ci-fingerprint.sh {job} " not in block:
        fail(f"{job} must emit a same-SHA CI fingerprint")
    if f"name: supernutrition-ci-fingerprint-{job}" not in block:
        fail(f"{job} must upload its same-SHA CI fingerprint")
    if "retention-days: 3" not in block:
        fail(f"{job} fingerprint must use short retention")

for script in ("scripts/run-ui-journeys-android.sh", "scripts/run-ui-journeys-ios.sh"):
    runner = (path.parents[2] / script).read_text(encoding="utf-8")
    if "cli-2.7.0/maestro.zip" not in runner:
        fail(f"{script} must download the exact Maestro 2.7.0 archive")
    if "mobile/.maestro/maestro-cli-2.7.0.sha256" not in runner:
        fail(f"{script} must consume the checked-in Maestro checksum pin")
    if "--format junit" not in runner or "--test-output-dir" not in runner:
        fail(f"{script} must emit JUnit and debug output separately")
    if "check-syntax" not in runner:
        fail(f"{script} must validate the checked-in Maestro flow syntax before execution")

flow = (path.parents[2] / "mobile/.maestro/photo-analysis/photo-analysis-capture-recovery.yaml").read_text(
    encoding="utf-8"
)
if not re.search(r"^appId:\s+\$\{APP_ID\}\s*$\n^---\s*$", flow, re.MULTILINE):
    fail("photo-analysis Maestro flow must have a parameterized appId header followed by YAML front matter")

aggregate = blocks["ci-required"]
if not re.search(r"^    if:\s*\$\{\{\s*always\(\)\s*\}\}\s*$", aggregate, re.MULTILINE):
    fail("ci-required must run with if: always()")
if not re.search(r"uses:\s+actions/checkout@[0-9a-f]{40}(?:\s+#.*)?$", aggregate, re.MULTILINE):
    fail("ci-required must check out the candidate before validating fingerprints")
if not re.search(r"uses:\s+actions/download-artifact@[0-9a-f]{40}(?:\s+#.*)?$", aggregate, re.MULTILINE):
    fail("ci-required must download the six same-SHA fingerprint artifacts")
if "scripts/verify-ci-fingerprints.sh" not in aggregate:
    fail("ci-required must validate the downloaded same-SHA fingerprints")

needs_match = re.search(
    r"^    needs:\s*\n(?P<body>(?:^      - [a-z0-9-]+\s*$\n?)+)",
    aggregate,
    re.MULTILINE,
)
if needs_match is None:
    fail("ci-required must declare a direct needs list")
needs = re.findall(r"^      - ([a-z0-9-]+)\s*$", needs_match.group("body"), re.MULTILINE)
required_needs = sorted(expected - {"ci-required"})
if sorted(needs) != required_needs or len(needs) != len(required_needs):
    fail(f"ci-required needs must be exactly {required_needs}, got {needs}")

for job in sorted(expected - {"ci-required"}):
    if "quality-check" in blocks[job] and not re.search(r"run:\s+\./(?:web/landing|backend/mobile-api)/quality-check", blocks[job]):
        fail(f"{job} has an unexpected quality-check command")

print("ci-policy: OK (canonical six jobs, direct aggregate, same-SHA fingerprints, SHA-pinned checkout, no path filters)")
PY
