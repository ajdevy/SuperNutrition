#!/usr/bin/env bash
# Fixtures for .claude/hooks/secret-scan.sh.
#
# The hook had no tests, and both of its defects were the kind only a fixture
# finds: a path allowlist that matched substrings, so `latest` and `attest`
# skipped scanning entirely, and a key pattern that predated every token format
# GitHub and OpenAI now issue.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
hook="${repo_root}/.claude/hooks/secret-scan.sh"

test -x "$hook" || {
    printf 'secret-scan hook is missing or not executable: %s\n' "$hook" >&2
    exit 1
}

payload() {
    python3 -c '
import json, sys
print(json.dumps({"tool_input": {"file_path": sys.argv[1], "content": sys.argv[2]}}))
' "$1" "$2"
}

# MultiEdit carries its text in .tool_input.edits[].new_string. The matcher
# names MultiEdit, but the extractor never read that array.
multiedit_payload() {
    python3 -c '
import json, sys
print(json.dumps({"tool_input": {"file_path": sys.argv[1], "edits": [
    {"old_string": "", "new_string": sys.argv[2]},
]}}))
' "$1" "$2"
}

blocked=0
allowed=0

expect_multiedit_blocked() {
    local label="$1" path="$2" content="$3"
    if multiedit_payload "$path" "$content" | "$hook" >/dev/null 2>&1; then
        printf 'fixture unexpectedly ALLOWED: %s (%s)\n' "$label" "$path" >&2
        exit 1
    fi
    blocked=$((blocked + 1))
}

expect_blocked() {
    local label="$1" path="$2" content="$3"
    if payload "$path" "$content" | "$hook" >/dev/null 2>&1; then
        printf 'fixture unexpectedly ALLOWED: %s (%s)\n' "$label" "$path" >&2
        exit 1
    fi
    blocked=$((blocked + 1))
}

expect_allowed() {
    local label="$1" path="$2" content="$3"
    if ! payload "$path" "$content" | "$hook" >/dev/null 2>&1; then
        printf 'fixture unexpectedly BLOCKED: %s (%s)\n' "$label" "$path" >&2
        exit 1
    fi
    allowed=$((allowed + 1))
}

# --- token shapes the hook did not know -------------------------------------
expect_blocked "an OpenAI project key" "src/config.ts" \
    'const key = "sk-proj-abcdefghij0123456789ABCDEFghijklmnop"'
expect_blocked "a classic OpenAI key" "src/config.ts" \
    'const key = "sk-abcdefghij0123456789ABCDEF"'
expect_blocked "a GitHub personal token" "src/config.ts" \
    'token = "ghp_abcdefghij0123456789ABCDEFghijklmn"'
expect_blocked "a fine-grained GitHub token" "src/config.ts" \
    'token = "github_pat_11ABCDEFG0abcdefghij_KLMNOPqrstuvwxyz0123456789"'
expect_blocked "a Slack bot token" "src/config.ts" \
    'token = "xoxb-123456789012-abcdefghijklmnop"'
expect_blocked "a private key block" "src/config.ts" \
    '-----BEGIN RSA PRIVATE KEY-----'
expect_blocked "an assigned api key" "src/config.ts" \
    'apiKey = "A1b2C3d4E5f6G7h8J9k0"'

# --- paths that merely CONTAIN an allowlisted word --------------------------
# The whole defect: `latest`, `attest`, `contest` and `manifest` all contain
# "test", so a credential written to any of them was never looked at.
for path in \
    "config/latest-keys.toml" \
    "src/attestation.ts" \
    "src/contest-entry.ts" \
    "build/manifest.json" \
    "src/protester.ts" \
    "infra/dockerfile.prod"
do
    expect_blocked "a credential in $path" "$path" \
        'apiKey = "sk-proj-abcdefghij0123456789ABCDEFghij"'
done

# --- paths that genuinely are test material ---------------------------------
# The allowlist must keep working, or every fixture file becomes unwritable.
for path in \
    "src/config.test.ts" \
    "src/config.spec.ts" \
    "tests/fixtures/keys.toml" \
    "docs/setup.md" \
    "src/__tests__/auth.ts" \
    "mocks/gateway.ts"
do
    expect_allowed "test material at $path" "$path" \
        'apiKey = "sk-proj-abcdefghij0123456789ABCDEFghij"'
done

# --- ordinary content must not be blocked -----------------------------------
expect_allowed "prose mentioning an api key" "src/notes.ts" \
    '// The api key is read from the environment, never committed.'
expect_allowed "an obvious placeholder" "src/config.ts" \
    'apiKey = "your-key-here-replace-me"'
expect_allowed "an env lookup" "src/config.ts" \
    'const key = process.env.OPENROUTER_API_KEY ?? ""'

# --- TL-H4: the placeholder filter judged the whole LINE ----------------------
# `grep -ivE "(test|dummy|…)"` ran over the matched line, so any line carrying
# one of those words anywhere suppressed a real credential on it. A trailing
# comment was enough. The filter now judges the VALUE that matched, so a
# genuine key is blocked whatever else the line says.
expect_blocked "a real key excused by a trailing comment" "src/config.ts" \
    'apiKey = "sk-proj-abcdefghij0123456789ABCDEFghij" // tested in staging'
expect_blocked "a real key excused by the word latest" "src/config.ts" \
    'apiKey = "sk-proj-abcdefghij0123456789ABCDEFghij" # latest rotation'
expect_blocked "a real key excused by an html-ish tag" "src/config.ts" \
    'apiKey = "sk-proj-abcdefghij0123456789ABCDEFghij" <!-- see runbook -->'
expect_blocked "a real key excused by a nearby example" "src/config.ts" \
    'client_secret = "abcdefghij0123456789ABCDEFghij" ; example follows below'

# The point of the filter still has to hold: a placeholder VALUE stays allowed.
expect_allowed "a placeholder value on a line with no excuse" "src/config.ts" \
    'apiKey = "test-key-placeholder-value-here"'
expect_allowed "an angle-bracket template value" "src/config.ts" \
    'apiKey = "<YOUR_OPENROUTER_KEY_HERE>"'

# --- TL-H4: MultiEdit was named by the matcher but never extracted -----------
expect_multiedit_blocked "a credential inside a MultiEdit edit" "src/config.ts" \
    'const key = "sk-proj-abcdefghij0123456789ABCDEFghijklmnop"'

# --- TL-H4: an unusable jq must fail closed ----------------------------------
# `|| true` turned every jq failure into empty content, which matches nothing
# and exits 0. A hook that cannot read its input must not report "clean".
#
# Two things this fixture got wrong before, and both made it pass without ever
# reaching the branch:
#
#   1. the hook itself does `export PATH="/usr/bin:/usr/local/bin:$PATH"`, and
#      both macOS and ubuntu ship /usr/bin/jq — so prepending a shim to PATH
#      from outside was overridden by the hook's own first line. The override
#      now happens INSIDE a copy of the hook.
#   2. it fed the copy a real credential, so the ordinary matcher blocked it and
#      the fixture recorded a rejection that had nothing to do with jq.
#      The payload is now benign: with a working jq it must be ALLOWED, and the
#      only thing that can block it is jq being unusable.
jq_probe="$(mktemp -d "${TMPDIR:-/tmp}/secret-scan-nojq.XXXXXX")"
trap 'rm -rf "$jq_probe"' EXIT
printf '#!/usr/bin/env bash\nexit 127\n' >"$jq_probe/jq"
chmod +x "$jq_probe/jq"

# A copy whose PATH puts the broken jq first, ahead of the hook's own prepend.
sed "s|^export PATH=.*|export PATH=\"$jq_probe:/usr/bin:/usr/local/bin:\$PATH\"|" \
    "$hook" >"$jq_probe/secret-scan.sh"
chmod +x "$jq_probe/secret-scan.sh"

benign='const greeting = "hello"'

# Control: the same copy with a working jq must ALLOW this payload. If it does
# not, the rejection below says nothing about jq.
if ! payload "src/config.ts" "$benign" | "$hook" >/dev/null 2>&1; then
    printf 'positive control failed: the hook blocked a payload with no credential in it\n' >&2
    exit 1
fi

if payload "src/config.ts" "$benign" | "$jq_probe/secret-scan.sh" >/dev/null 2>&1; then
    printf 'fixture unexpectedly ALLOWED: the hook passed while jq was unusable\n' >&2
    exit 1
fi
blocked=$((blocked + 1))

# --- and the OTHER jq branch: not present at all -----------------------------
# The shim above exits 127 when RUN, so `command -v jq` still finds it and the
# hook reaches the parse-failure branch. The `jq is not available` branch —
# reached when nothing named jq is on PATH — had no fixture. Two different
# refusals, one of them untested.
#
# The copy's own `export PATH=` line is replaced with an empty directory, so it
# cannot re-add /usr/bin and find the system jq.
jq_absent="$(mktemp -d "${TMPDIR:-/tmp}/secret-scan-jq-absent.XXXXXX")"
mkdir -p "$jq_absent/bin"
# Everything the hook needs BEFORE the jq check, and nothing named jq. A bare
# empty PATH would have removed `cat` too, so the hook would have failed to read
# its input at all — reaching the right branch for the wrong reason, and
# printing a broken pipe on the way.
for tool in cat python3; do
    resolved="$(command -v "$tool")"
    ln -s "$resolved" "$jq_absent/bin/$tool"
done
sed "s|^export PATH=.*|export PATH=\"$jq_absent/bin\"|" "$hook" >"$jq_absent/secret-scan.sh"
chmod +x "$jq_absent/secret-scan.sh"

# Control: with jq restored on that same PATH the copy must ALLOW the benign
# payload, so the refusal below is about jq and not about the trimmed PATH.
ln -s "$(command -v jq)" "$jq_absent/bin/jq"
if ! payload "src/config.ts" "$benign" | "$jq_absent/secret-scan.sh" >/dev/null 2>&1; then
    printf 'positive control failed: the trimmed PATH broke the hook independently of jq\n' >&2
    exit 1
fi
rm -f "$jq_absent/bin/jq"

set +e
absent_output="$(payload "src/config.ts" "$benign" | "$jq_absent/secret-scan.sh" 2>&1)"
absent_status=$?
set -e
rm -rf "$jq_absent"

# The exit CODE, not merely non-zero: 2 is what a PreToolUse hook must return to
# block, and any other non-zero would let the edit through.
if test "$absent_status" -ne 2; then
    printf 'fixture: an absent jq did not block with exit 2 (got %s)\n' "$absent_status" >&2
    exit 1
fi
case "$absent_output" in
    *"jq is not available"*) : ;;
    *)
        printf 'fixture: the hook refused, but not for jq being absent\n' >&2
        printf '  actual: %s\n' "$absent_output" >&2
        exit 1
        ;;
esac
blocked=$((blocked + 1))

printf 'secret-scan fixtures: OK (%d blocked, %d allowed)\n' "$blocked" "$allowed"
