#!/usr/bin/env bash
# Stop hook: deterministic "done means verified" gate.
#
# Runs backend unit tests when the backend working tree is dirty, and blocks the
# turn from ending while they are red.
#
# TWO THINGS THIS GOT WRONG, both of which made the gate a no-op.
#
# 1. It exited 1 on failure. A Stop hook that exits 1 is a non-blocking error:
#    the message is shown and the turn ends anyway. Exit 2 is what feeds the
#    reason back and blocks. So red tests were reported and then ignored.
#
# 2. It read `stop_hook_active` from the environment. That field arrives on
#    stdin as JSON, so the variable was always unset, the guard never fired, and
#    the hook was free to recurse — the one case it was written to prevent.
set -u
export PATH="/usr/bin:/usr/local/bin:$PATH"

# Stdin is the hook payload. Read it before anything else; nothing downstream
# consumes stdin, and an empty read must not hang the turn.
payload=""
if [ ! -t 0 ]; then
  payload="$(timeout 5 cat 2>/dev/null || cat 2>/dev/null || true)"
fi

stop_hook_active="$(
  printf '%s' "$payload" | python3 -c '
import json, sys
try:
    print(str(json.load(sys.stdin).get("stop_hook_active", False)).lower())
except Exception:
    # A payload we cannot parse is not a licence to skip the gate.
    print("false")
' 2>/dev/null || printf 'false'
)"

if [ "$stop_hook_active" = "true" ]; then
  exit 0
fi

ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
if ! git -C "$ROOT" status --porcelain -- backend | grep -q .; then
  exit 0
fi

LOG="$ROOT/.claude/hooks/gate.log"
cd "$ROOT/backend/mobile-api" || exit 0
if npm test -- --reporter=dot >"$LOG" 2>&1; then
  echo "test-gate: PASS — backend tests green ($(grep -oE "[0-9]+ passed" "$LOG" | head -1))" >&2
  exit 0
else
  echo "test-gate: FAIL — backend tests are red; do not report done until green." >&2
  tail -15 "$LOG" >&2
  # Exit 2, not 1: only 2 blocks the stop and returns this reason.
  exit 2
fi
