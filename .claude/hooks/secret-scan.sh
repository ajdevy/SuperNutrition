#!/usr/bin/env bash
# PreToolUse hook: block Write/Edit/MultiEdit that embeds credentials.
# Receives tool JSON on stdin. Exit 2 = block. Never reads files, scans payload only.
set -u
export PATH="/usr/bin:/usr/local/bin:$PATH"
input="$(cat)"
path="$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null || true)"
if printf '%s' "$path" | grep -qE '(test|spec|fixture|mock|example|docs|hooks|__tests__|\.test\.|\.spec\.)'; then
  exit 0
fi
if printf '%s' "$input" | grep -iE "(api[_-]?key|client[_-]?secret|secret[_a-z]*|password|passwd|private[_-]?key|auth[_-]?token|access[_-]?token|bearer[_-]?token)[^\"[:space:]]{0,40}[[:space:]]*[:=][[:space:]]*\\\\?[\"']?[A-Za-z0-9_./+=:-]{16,}|[^a-zA-Z0-9_]sk-[A-Za-z0-9]{16,}[^a-zA-Z0-9_]|-----BEGIN [A-Z ]{3,} KEY-----" | grep -ivE "(test|dummy|example|sample|fake|placeholder|change[_-]?me|your[_-]?key|xxxx|<[^>]+>)"; then
  echo "ERROR: secret-scan blocked this edit: looks like a credential is being written to ${path:-<unknown>}" >&2
  exit 2
fi
exit 0
