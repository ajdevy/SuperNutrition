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

copy_release v2 v2-stale-digest
python3 - "$fixture_root/v2-stale-digest" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1]) / "compatibility-matrix.toml"
path.write_text(re.sub(r'digest = "[a-f0-9]{64}"', 'digest = "' + ('0' * 64) + '"', path.read_text()))
PY
expect_rejected "v2 stale digest" "$fixture_root/v2-stale-digest"

copy_release v2 v2-malformed-json
printf '{\n' > "$fixture_root/v2-malformed-json/fixtures/photo-component-estimated-result.json"
expect_rejected "v2 malformed JSON" "$fixture_root/v2-malformed-json"

copy_release v2 v2-invalid-grams
python3 - "$fixture_root/v2-invalid-grams/fixtures/photo-component-estimated-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["components"][0]["mass"]["range"]["upperGrams"] = -1
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "v2 invalid gram range" "$fixture_root/v2-invalid-grams"

copy_release v2 v2-duplicate-component-id
python3 - "$fixture_root/v2-duplicate-component-id/fixtures/photo-component-estimated-result.json" <<'PY'
from pathlib import Path
import copy
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["components"].append(copy.deepcopy(data["outcome"]["components"][0]))
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "v2 duplicate component ID" "$fixture_root/v2-duplicate-component-id"

copy_release v2 v2-text-estimated-mass
python3 - "$fixture_root/v2-text-estimated-mass/fixtures/text-component-not-evaluable-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["components"][0]["mass"] = {
    "massType": "estimated",
    "range": {"lowerGrams": 1, "upperGrams": 2},
    "calibrationAssumptions": ["single-view-approximation"],
}
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "v2 text estimated mass" "$fixture_root/v2-text-estimated-mass"

copy_release v2 v2-unknown-mass-state
python3 - "$fixture_root/v2-unknown-mass-state/fixtures/photo-component-estimated-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["components"][0]["mass"]["massType"] = "exact"
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "v2 unsupported mass state" "$fixture_root/v2-unknown-mass-state"

copy_release v2 v2-nutrition-injection
python3 - "$fixture_root/v2-nutrition-injection/fixtures/photo-component-estimated-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["components"][0]["nutrition"] = {"calories": 42}
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "v2 nutrition injection" "$fixture_root/v2-nutrition-injection"

copy_release v2 v2-missing-provenance
python3 - "$fixture_root/v2-missing-provenance/fixtures/photo-component-estimated-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
del data["outcome"]["components"][0]["provenance"]
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "v2 missing provenance" "$fixture_root/v2-missing-provenance"

copy_release v2 v2-invalid-coexistence
python3 - "$fixture_root/v2-invalid-coexistence" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1]) / "compatibility-matrix.toml"
path.write_text(path.read_text().replace('requirement = "separate-ports-required"', 'requirement = "implicit-adapter"', 1))
PY
expect_rejected "v2 invalid coexistence requirement" "$fixture_root/v2-invalid-coexistence"

copy_release v2 v2-symlinked-directory
ln -s "${repo_root}/contracts/inference/v2/fixtures" "$fixture_root/v2-symlinked-directory/fixture-link"
expect_rejected "v2 symlinked release directory" "$fixture_root/v2-symlinked-directory"

echo "inference contract negative fixtures: OK (v1 and v2)"
