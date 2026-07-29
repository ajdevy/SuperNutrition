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
for trigger in ("push", "pull_request"):
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

actual = {job for _, job in job_starts}
if actual == {"mobile-unit-coverage"}:
    block = "\n".join(lines[job_starts[0][0]:])
    if not re.search(r"uses:\s+actions/checkout@[0-9a-f]{40}(?:\s+#.*)?$", block, re.MULTILINE):
        fail("mobile-unit-coverage must use a SHA-pinned actions/checkout")
    for required in (
        "mobileUnitCoverage",
        "-Psupernutrition.runJvmCoverage=true",
        "--dependency-verification=strict",
        "--no-daemon",
        "--no-configuration-cache",
        "--max-workers=1",
    ):
        if required not in block:
            fail(f"reduced mobile workflow is missing {required}")
    for flow_relative in (
        "mobile/.maestro/bootstrap/bootstrap-launch-di.yaml",
        "mobile/.maestro/photo-analysis/photo-analysis-capture-recovery.yaml",
    ):
        flow_path = path.parents[2] / flow_relative
        if not flow_path.is_file():
            fail(f"required Maestro flow is missing: {flow_relative}")
        flow = flow_path.read_text(encoding="utf-8")
        if not re.search(r"^appId:\s+\$\{APP_ID\}\s*$\n^---\s*$", flow, re.MULTILINE):
            fail(f"{flow_relative} must have a parameterized appId header followed by YAML front matter")
    print("ci-policy: OK (optional reduced mobile-unit coverage workflow, SHA-pinned checkout, no path filters)")
    raise SystemExit(0)

fail(f"unsupported workflow job set: {sorted(actual)}; only the optional reduced mobile-unit-coverage adapter is active")
PY
