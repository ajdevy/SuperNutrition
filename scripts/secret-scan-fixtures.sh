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

blocked=0
allowed=0

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

printf 'secret-scan fixtures: OK (%d blocked, %d allowed)\n' "$blocked" "$allowed"
