#!/usr/bin/env bash
# Read-only check that every recorded owner decision says who decided it, when,
# and where.
#
# WHY THIS EXISTS
# Two decisions about privacy posture — what user data may live in Redis, and
# how a retention clause is read — were implemented on 2026-08-20 with no entry
# in .evidence/OWNER-DECISIONS.md at all. The authority was real and given in
# conversation; the repository had no way to show that. Read from the outside it
# looked like an implementer had settled a privacy question and approved their
# own answer (PR #44 review, EV-1).
#
# WHAT THIS CAN AND CANNOT DO
# It cannot make a decision get recorded on time — nothing cheap can, and that
# remains a rule for the people and agents doing the work. What it does is stop
# an INCOMPLETE record landing: a decision with no date, no decider, or no
# channel is not provenance, it is a paragraph. Deliberately narrow, so it stays
# worth running.
#
# PENDING is permitted and is not an escape hatch: it is how a drafted entry
# says out loud that it is unratified. `--strict` refuses it, which is the check
# to run before treating a decision as authority.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
record="${SUPERNUTRITION_OWNER_DECISIONS:-$repo_root/.evidence/OWNER-DECISIONS.md}"
strict=0
if [ "${1:-}" = "--strict" ]; then
    strict=1
fi

test -f "$record" || {
    printf 'missing owner decision record: %s\n' "${record#"$repo_root/"}" >&2
    exit 1
}

OWNER_DECISION_RECORD="$record" OWNER_DECISION_STRICT="$strict" python3 - <<'PY'
import os
import re
import sys
from datetime import date

record = os.environ["OWNER_DECISION_RECORD"]
strict = os.environ["OWNER_DECISION_STRICT"] == "1"
text = open(record, encoding="utf-8").read()

REQUIRED = ("Decided on", "Decided by", "Channel")
HEADING = re.compile(r"^## (D-\d+)\b(.*)$", re.M)

failures = []
seen = {}
pending = []

headings = list(HEADING.finditer(text))
if not headings:
    sys.exit(f"{record} records no decisions; the check would pass by having nothing to check")

for index, heading in enumerate(headings):
    identifier = heading.group(1)
    end = headings[index + 1].start() if index + 1 < len(headings) else len(text)
    body = text[heading.end():end]

    if identifier in seen:
        failures.append(f"{identifier} is used twice; a decision id must name one decision")
    seen[identifier] = True

    for field in REQUIRED:
        found = re.search(rf"^{field}:[ \t]*(.+?)[ \t]*$", body, re.M)
        if not found:
            failures.append(f"{identifier} has no `{field}:` line")
            continue
        value = found.group(1)
        if value == "PENDING":
            pending.append(f"{identifier} {field}")
            continue
        if field == "Decided on":
            try:
                decided = date.fromisoformat(value)
            except ValueError:
                failures.append(f"{identifier} Decided on is not an ISO date: {value!r}")
                continue
            # A decision dated in the future is a typo or a backdated record, and
            # both are worth catching while someone still remembers which.
            if decided > date.today():
                failures.append(f"{identifier} is dated in the future: {value}")

if strict and pending:
    failures.extend(f"{entry} is still PENDING and --strict was requested" for entry in pending)

if failures:
    for failure in failures:
        print(failure, file=sys.stderr)
    sys.exit(1)

note = f", {len(pending)} field(s) awaiting ratification" if pending else ""
print(f"owner-decisions: OK ({len(seen)} decisions, each dated, attributed and sourced{note})")
PY
