#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
contract_root="${SUPERNUTRITION_MOBILE_API_CONTRACT:-${repo_root}/contracts/mobile-api/v1}"

# The digest algorithm is shared with scripts/emit-release-digest.sh rather than
# inlined here. Two implementations produced two different digests for the same
# release, and the workflow RELEASE.md documents followed the other one.
export SUPERNUTRITION_SCRIPT_LIB="${repo_root}/scripts/lib"

python3 - "$contract_root" <<'PY'
from hashlib import sha256
import os
import sys

sys.path.insert(0, os.environ["SUPERNUTRITION_SCRIPT_LIB"])
import release_digest
from pathlib import Path
import re
import sys
import tomllib

root = Path(sys.argv[1]).resolve()


def fail(message):
    raise SystemExit(f"mobile-api contract: {message}")


def load(relative):
    path = root / relative
    if not path.is_file():
        fail(f"missing contract file: {relative}")
    try:
        return tomllib.loads(path.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as error:
        fail(f"{relative} is not parseable TOML: {error}")


# --- required release members -------------------------------------------------

DOCUMENTATION = ["RELEASE.md"]
SCHEMAS = [
    "schema.toml",
    "gateway-request.toml",
    "gateway-response.toml",
    "error-taxonomy.toml",
    "consent-receipt.toml",
    "rate-limit.toml",
    "transfer-states.toml",
    "market-policy.toml",
    "compatibility-matrix.toml",
]
FIXTURES = [
    "fixtures/empty-analysis-request.toml",
    "fixtures/photo-analysis-success.toml",
    "fixtures/photo-analysis-low-confidence.toml",
    "fixtures/photo-analysis-abstention.toml",
    "fixtures/photo-analysis-consent-expired.toml",
    "fixtures/photo-analysis-rate-limited.toml",
    "fixtures/cancellation-pre-transfer.toml",
    "fixtures/cancellation-transferred.toml",
]

documents = {name: load(name) for name in SCHEMAS + FIXTURES}
schema = documents["schema.toml"]
matrix = documents["compatibility-matrix.toml"]

# --- release-wide invariants --------------------------------------------------

for name in SCHEMAS:
    doc = documents[name]
    if doc.get("release") != "v1":
        fail(f"{name} must target release v1")
    if name != "compatibility-matrix.toml":
        if doc.get("language_neutral") is not True or doc.get("executable") is not False:
            fail(f"{name} must remain language-neutral and non-executable")

if matrix.get("contract_id") != "mobile-api":
    fail("compatibility matrix does not bind the declared contract")

declared = schema.get("files", {})
if sorted(declared.get("schemas", [])) != sorted(SCHEMAS):
    fail("schema.toml [files].schemas disagrees with the required release members")
if sorted(declared.get("fixtures", [])) != sorted(FIXTURES):
    fail("schema.toml [files].fixtures disagrees with the required release members")

# --- verification 3: one gatewayCode per error, no duplicates -----------------

taxonomy = documents["error-taxonomy.toml"]
errors = taxonomy.get("error", [])
if not errors:
    fail("error taxonomy is empty")

codes = [entry.get("gatewayCode") for entry in errors]
if len(codes) != len(set(codes)):
    duplicates = sorted({code for code in codes if codes.count(code) > 1})
    fail(f"duplicate gatewayCode in taxonomy: {duplicates}")

for entry in errors:
    code = entry.get("gatewayCode")
    for required in ("httpStatus", "userMessageKey", "recoverable", "retryAfter"):
        if required not in entry:
            fail(f"taxonomy entry {code!r} is missing {required}")
    if not isinstance(entry["recoverable"], bool):
        fail(f"taxonomy entry {code!r} recoverable must be a boolean")
    if not entry.get("description"):
        fail(f"taxonomy entry {code!r} is undocumented")
    if entry["recoverable"] is False and entry["retryAfter"] != 0:
        fail(f"non-recoverable {code!r} must not advertise a retryAfter")
    if entry["recoverable"] and entry["httpStatus"] >= 500 and entry["retryAfter"] <= 0:
        fail(f"recoverable server-side {code!r} must advertise a positive retryAfter")

# --- verification 1: every declared field is typed and documented -------------

FIELD_TABLES = {
    "gateway-request.toml": ("header", "context", "inference_context"),
    "gateway-response.toml": (
        "header", "envelope", "outcome_candidates", "candidate", "confidence",
        "assumption", "provenance", "outcome_abstention", "outcome_failure",
    ),
    "consent-receipt.toml": ("field",),
    "rate-limit.toml": ("body_field", "header"),
    "transfer-states.toml": ("response_field",),
    "market-policy.toml": ("query_param", "response_field"),
}

for name, tables in FIELD_TABLES.items():
    doc = documents[name]
    for table in tables:
        for entry in doc.get(table, []):
            label = entry.get("field") or entry.get("name")
            if not label:
                fail(f"{name} [{table}] has an unnamed entry")
            if "type" not in entry:
                fail(f"{name} [{table}] {label!r} has no explicit type")
            if "optional" not in entry:
                fail(f"{name} [{table}] {label!r} has no explicit optionality marker")
            if not isinstance(entry["optional"], bool):
                fail(f"{name} [{table}] {label!r} optionality must be a boolean")
            if not (entry.get("description") or entry.get("object_schema") or entry.get("enum")):
                fail(f"{name} [{table}] {label!r} is undocumented")

# --- verification 2: no nutrition anywhere in gateway responses ---------------

forbidden = {key.lower() for key in schema.get("nutrition", {}).get("forbidden_response_keys", [])}
if not forbidden:
    fail("schema.toml must declare forbidden nutrition response keys")
if schema.get("nutrition", {}).get("numeric_nutrition") is not False:
    fail("schema.toml must declare numeric_nutrition = false (AD-7)")


def walk_keys(node, path=""):
    if isinstance(node, dict):
        for key, value in node.items():
            here = f"{path}.{key}" if path else key
            yield key, here
            yield from walk_keys(value, here)
    elif isinstance(node, list):
        for index, value in enumerate(node):
            yield from walk_keys(value, f"{path}[{index}]")


response_surfaces = {"gateway-response.toml": documents["gateway-response.toml"]}
for name in FIXTURES:
    fixture = documents[name]
    if "response" in fixture:
        response_surfaces[name] = fixture["response"]

for name, node in response_surfaces.items():
    for key, where in walk_keys(node):
        if key.lower() in forbidden:
            fail(f"{name} exposes forbidden nutrition key {key!r} at {where}")

for entry in documents["gateway-response.toml"].get("candidate", []):
    if str(entry.get("field", "")).lower() in forbidden:
        fail(f"candidate declares forbidden nutrition field {entry['field']!r}")

# --- verification 4: consent receipt covers docs/09 section 9.5 ---------------

receipt_fields = {entry["name"] for entry in documents["consent-receipt.toml"].get("field", [])}
required_receipt_fields = {
    "digest",
    "modality",
    "purpose",
    "decision",
    "marketJurisdiction",
    "processingRegion",
    "consentCopyVersion",
    "retentionVersion",
    "configurationVersion",
    "recordedAt",
    "expiresAt",
    "attemptId",
}
missing = required_receipt_fields - receipt_fields
if missing:
    fail(f"consent receipt is missing binding fields required by docs/09 section 9.5: {sorted(missing)}")

validation = documents["consent-receipt.toml"].get("validation", {})
if validation.get("evaluated_at") != "resolution-time":
    fail("consent expiry must be evaluated at resolution time (AD-41 MP-3)")
if validation.get("digest_mismatch") != "malformed-consent":
    fail("a consent digest mismatch must map to malformed-consent")

# --- market policy must stay fail-closed -------------------------------------

policy = documents["market-policy.toml"]
posture_values = {entry.get("id") for entry in policy.get("posture_value", [])}
if "unresolved" not in posture_values:
    fail("market policy must define the unresolved posture (AD-41 MP-1 default)")
if policy.get("posture", {}).get("default") != "unresolved":
    fail("market policy default posture must be unresolved")
if policy.get("posture", {}).get("fail_closed") is not True:
    fail("market policy must be fail-closed")
for entry in policy.get("posture_value", []):
    if entry.get("local_path_available") is not True:
        fail(f"posture {entry.get('id')!r} must leave the local path available")

# --- transfer states must never claim recall ---------------------------------

transfer = documents["transfer-states.toml"]
if transfer.get("invariants", {}).get("recall_never_claimed") is not True:
    fail("transfer states must never claim recall of sent bytes (AD-8)")
for entry in transfer.get("response_field", []):
    if entry.get("name") == "recallClaimed" and entry.get("enum") != [False]:
        fail("recallClaimed must be constrained to false")

# --- deletion-claim coherence (M-04) -----------------------------------------
#
# The state definition, the fixture body, the fixture's own assertions, and the
# release notes each state whether a downstream deletion request is issued. They
# drifted twice: the D-01 amendment corrected the state and the fixture body but
# left the fixture's assertions and the release table saying the opposite, so the
# field a client derives user-facing copy from still promised an erasure nobody
# had requested.
#
# One source decides, and everything else must agree with it.

transferred = next(
    (st for st in transfer.get("state", []) if st.get("id") == "transferred"), None
)
if transferred is None:
    fail("transfer-states.toml has no `transferred` state")

deletion_claimed = transferred.get("downstream_deletion_requested")
if not isinstance(deletion_claimed, bool):
    fail("transferred.downstream_deletion_requested must be a boolean")

claim_words = ("deletion was requested", "deletion requested", "a downstream deletion request is issued")

def asserts_deletion(text):
    lowered = text.lower()
    if "no deletion" in lowered or "not issue" in lowered or "no downstream deletion" in lowered:
        return False
    return any(word in lowered for word in claim_words)

# The state's own honest_claim.
if asserts_deletion(str(transferred.get("honest_claim", ""))) != deletion_claimed:
    fail(
        "transferred.honest_claim and downstream_deletion_requested disagree about "
        "whether a deletion request is issued"
    )

# The fixture body, its assertions, and its description.
fixture = documents["fixtures/cancellation-transferred.toml"]
body_value = fixture.get("response", {}).get("body", {}).get("downstreamDeletionRequested")
if body_value != deletion_claimed:
    fail(
        f"cancellation-transferred body says downstreamDeletionRequested={body_value!r} "
        f"but transfer-states.toml says {deletion_claimed!r}"
    )
assertion_value = fixture.get("assertions", {}).get("downstream_deletion_requested")
if assertion_value != deletion_claimed:
    fail("cancellation-transferred assertions disagree with the state definition")
for field in ("description",):
    if asserts_deletion(str(fixture.get(field, ""))) != deletion_claimed:
        fail(f"cancellation-transferred {field} disagrees with the state definition")
if asserts_deletion(str(fixture.get("assertions", {}).get("honest_claim", ""))) != deletion_claimed:
    fail("cancellation-transferred assertions.honest_claim disagrees with the state definition")

# The release notes.
release_notes = (root / "RELEASE.md").read_text(encoding="utf-8")
for line in release_notes.splitlines():
    if "cancellation-transferred" in line and asserts_deletion(line) != deletion_claimed:
        fail("RELEASE.md disagrees with the state definition about the deletion request")

# --- verification 5: fixtures are structurally valid --------------------------

known_codes = set(codes)
for name in FIXTURES:
    fixture = documents[name]
    if fixture.get("schema") != "supernutrition.mobile-api-fixture.v1":
        fail(f"{name} does not declare the fixture schema")
    if not fixture.get("fixture_id"):
        fail(f"{name} has no fixture_id")
    expected = fixture.get("expected")
    if expected not in {"accepted", "rejected"}:
        fail(f"{name} must declare expected as accepted or rejected")
    if expected == "rejected" and not fixture.get("reason"):
        fail(f"{name} rejects without a reason")
    error = fixture.get("response", {}).get("body", {}).get("error")
    if error is not None:
        code = error.get("gatewayCode")
        if code not in known_codes:
            fail(f"{name} references gatewayCode {code!r} that is not in the taxonomy")
        entry = next(item for item in errors if item["gatewayCode"] == code)
        if fixture["response"].get("status") != entry["httpStatus"]:
            fail(f"{name} status disagrees with the taxonomy entry for {code!r}")
        if error.get("recoverable") != entry["recoverable"]:
            fail(f"{name} recoverability disagrees with the taxonomy entry for {code!r}")

fixture_ids = [documents[name]["fixture_id"] for name in FIXTURES]
if len(fixture_ids) != len(set(fixture_ids)):
    fail("duplicate fixture_id in release")

legacy = documents["fixtures/empty-analysis-request.toml"]
if legacy.get("expected") != "rejected" or legacy.get("reason") != "missing-normalized-photo-payload":
    fail("empty-analysis-request fixture must reject missing normalized bytes")

# --- compatibility matrix ----------------------------------------------------

pairs = {
    (item.get("producer"), item.get("consumer"), item.get("direction"), item.get("expected"))
    for item in matrix.get("supported_pair", [])
}
required_pairs = {
    ("mobile-v1", "backend-v1", "forward", "accepted"),
    ("backend-v1", "mobile-v1", "backward", "accepted"),
}
if pairs != required_pairs:
    fail(f"compatibility matrix must contain exactly the v1 skew pairs: {sorted(pairs)}")

# --- verification 6: digest over ALL sorted release files --------------------
#
# Computed by scripts/lib/release_digest.py, the same module the emitter uses.
# It used to be an inlined loop over `*.toml` only, while scripts/emit-release-
# digest.sh hashed every file — so contracts/mobile-api/v1 had two defensible
# digests, and the workflow RELEASE.md documents (run the emitter, paste the
# value) produced a number this verifier rejected. An operator told their digest
# is stale, who follows the only documented way to refresh it and is told the
# same thing again, ends up editing by hand until something passes.
#
# All files is the correct set: RELEASE.md says "the sorted release files",
# schema.toml declares documentation = ["RELEASE.md"] as a member, and the
# catalog records these releases under release-sha256-v2, whose defining change
# was RELEASE.md becoming a hashed input.

declared = sorted(SCHEMAS + FIXTURES + DOCUMENTATION)
present = sorted(path.relative_to(root).as_posix() for path in root.rglob("*") if path.is_file())
if present != declared:
    fail(f"release contains undeclared or missing files: {sorted(set(present) ^ set(declared))}")

computed = release_digest.compute(root)
if matrix.get("digest") != computed:
    fail(f"compatibility digest is stale (computed {computed})")

# --- verification 7: no executable or source material ------------------------

allowed_documentation = {"RELEASE.md"}
for path in root.rglob("*"):
    if not path.is_file():
        continue
    if path.is_symlink():
        fail(f"release contains a symlink: {path.name}")
    if path.name not in allowed_documentation and path.suffix != ".toml":
        fail(f"release contains non-contract material: {path.name}")

print(
    "mobile-api contract: OK "
    f"({len(SCHEMAS)} schemas, {len(FIXTURES)} fixtures, {len(errors)} error codes, "
    "no nutrition, fail-closed markets, digest current)"
)
PY
