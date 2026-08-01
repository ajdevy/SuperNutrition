#!/usr/bin/env bash
# Stop hook: deterministic "done means verified" gate (Spotify honk pattern).
# Runs backend unit tests when the backend working tree is dirty.
# Exit 1 = block turn end until tests are green.
set -u
export PATH="/usr/bin:/usr/local/bin:$PATH"
if [ "${stop_hook_active:-false}" = "true" ]; then
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
  exit 1
fi
