#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
fixture_root=$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-inference-contract.XXXXXX")
trap 'rm -rf "$fixture_root"' EXIT

copy_release() {
    local release="$1"
    local destination="$2"
    cp -R "${repo_root}/contracts/inference/${release}" "${fixture_root}/${destination}"
}

refresh_digest() {
    python3 - "$1" <<'PY'
from hashlib import sha256
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
matrix_path = root / "compatibility-matrix.toml"
matrix = re.sub(
    rb'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|pending)"\s*$',
    b'digest = "<release-digest>"',
    matrix_path.read_bytes(),
)
release_files = [
    ("RELEASE.md", (root / "RELEASE.md").read_bytes()),
    ("compatibility-matrix.toml", matrix),
    ("schema.toml", (root / "schema.toml").read_bytes()),
]
release_files.extend(
    (str(path.relative_to(root)), path.read_bytes())
    for path in sorted((root / "fixtures").glob("*.json"))
)
digest = sha256()
for relative, content in sorted(release_files):
    digest.update(relative.encode("utf-8"))
    digest.update(b"\0")
    digest.update(content)
matrix_path.write_text(re.sub(
    r'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|pending)"\s*$',
    f'digest = "{digest.hexdigest()}"',
    matrix_path.read_text(),
))
PY
}

expect_rejected() {
    local label="$1"
    local root="$2"
    if SUPERNUTRITION_INFERENCE_CONTRACT="$root" "${repo_root}/scripts/verify-inference-contract.sh" >/dev/null 2>&1; then
        echo "negative fixture unexpectedly passed: $label" >&2
        exit 1
    fi
}

copy_release v1 v1-incompatible-pair
python3 - "$fixture_root/v1-incompatible-pair" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
matrix = root / "compatibility-matrix.toml"
matrix.write_text(matrix.read_text().replace('expected = "accepted"', 'expected = "rejected"', 1))
PY
refresh_digest "$fixture_root/v1-incompatible-pair"
expect_rejected "v1 incompatible inference pair" "$fixture_root/v1-incompatible-pair"

copy_release v1 v1-nutrition
python3 - "$fixture_root/v1-nutrition/fixtures/candidate-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["candidates"][0]["calories"] = 42
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
refresh_digest "$fixture_root/v1-nutrition"
expect_rejected "v1 numeric nutrition" "$fixture_root/v1-nutrition"

copy_release v1 v1-duplicate-keys
python3 - "$fixture_root/v1-duplicate-keys/fixtures/candidate-result.json" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
needle = '"contractVersion":"photo-inference-engine/v1",'
path.write_text(text.replace(needle, needle + '"contractVersion":"photo-inference-engine/v1",', 1))
PY
expect_rejected "v1 duplicate JSON keys" "$fixture_root/v1-duplicate-keys"

copy_release v1 v1-nonfinite
python3 - "$fixture_root/v1-nonfinite/fixtures/candidate-result.json" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
path.write_text(path.read_text().replace('"score":0.93', '"score":NaN', 1))
PY
expect_rejected "v1 non-finite confidence" "$fixture_root/v1-nonfinite"

copy_release v1 v1-unknown-outcome
python3 - "$fixture_root/v1-unknown-outcome/fixtures/candidate-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["outcomeType"] = "unsupported"
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
refresh_digest "$fixture_root/v1-unknown-outcome"
expect_rejected "v1 unknown outcome type" "$fixture_root/v1-unknown-outcome"

copy_release v1 v1-noncontiguous-rank
python3 - "$fixture_root/v1-noncontiguous-rank/fixtures/candidate-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["candidates"][0]["rank"] = 2
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
refresh_digest "$fixture_root/v1-noncontiguous-rank"
expect_rejected "v1 non-contiguous candidate rank" "$fixture_root/v1-noncontiguous-rank"

copy_release v1 v1-duplicate-candidate-id
python3 - "$fixture_root/v1-duplicate-candidate-id/fixtures/candidate-result.json" <<'PY'
from pathlib import Path
import copy
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
candidate = copy.deepcopy(data["outcome"]["candidates"][0])
candidate["rank"] = 2
data["outcome"]["candidates"].append(candidate)
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
refresh_digest "$fixture_root/v1-duplicate-candidate-id"
expect_rejected "v1 duplicate candidate ID" "$fixture_root/v1-duplicate-candidate-id"

copy_release v1 v1-blank-message-key
python3 - "$fixture_root/v1-blank-message-key/fixtures/abstention-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["userMessageKey"] = " "
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
refresh_digest "$fixture_root/v1-blank-message-key"
expect_rejected "v1 blank outcome message key" "$fixture_root/v1-blank-message-key"

copy_release v1 v1-ready-capability
python3 - "$fixture_root/v1-ready-capability/fixtures/capability-ready.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["reason"] = "model-missing"
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
refresh_digest "$fixture_root/v1-ready-capability"
expect_rejected "v1 ready capability with unavailable reason" "$fixture_root/v1-ready-capability"

copy_release v1 v1-stale-digest
python3 - "$fixture_root/v1-stale-digest" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1]) / "compatibility-matrix.toml"
path.write_text(re.sub(r'digest = "[a-f0-9]{64}"', 'digest = "' + ('0' * 64) + '"', path.read_text()))
PY
expect_rejected "v1 stale digest" "$fixture_root/v1-stale-digest"

copy_release v1 v1-unhashed-json
printf '{}\n' > "$fixture_root/v1-unhashed-json/unhashed.json"
expect_rejected "v1 unhashed release JSON" "$fixture_root/v1-unhashed-json"

copy_release v1 v1-symlinked-directory
ln -s "${repo_root}/contracts/inference/v1/fixtures" "$fixture_root/v1-symlinked-directory/fixture-link"
expect_rejected "v1 symlinked release directory" "$fixture_root/v1-symlinked-directory"

# The v2 mutation groups ship with the mobile slice.
#
# They mutate contracts/inference-evidence/v2, which this tree does not publish.
# Leaving them here would make the required check red by construction; leaving
# them and skipping on a missing directory would leave a harness that reports OK
# whether or not the release is present. Both are worse than keeping the
# mutations in the change that ships the release they mutate.

echo "inference contract negative fixtures: OK (v1)"
