#!/usr/bin/env bash
# PreToolUse hook: block Write/Edit/MultiEdit that embeds credentials.
# Receives tool JSON on stdin. Exit 2 = block. Never reads files, scans payload only.
set -u
export PATH="/usr/bin:/usr/local/bin:$PATH"
input="$(cat)"
path="$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null || true)"
# Anchored to path SEGMENTS and extensions, not substrings.
#
# The previous pattern matched anywhere in the path, so any file whose name
# merely contained one of these words skipped scanning entirely: `latest`,
# `attest`, `contest`, `manifest` and `protest` all contain "test", and
# `docs` matches inside `dockerfile`. A credential written to
# `config/latest-keys.toml` was never looked at.
if printf '%s' "$path" | grep -qE '(^|/)(tests?|spec|specs|fixtures?|mocks?|examples?|docs|__tests__)(/|$)|\.(test|spec)\.[A-Za-z0-9]+$|(^|/)[A-Za-z0-9_.-]*\.(test|spec)\.'; then
  exit 0
fi
# Scan the CONTENT, not the whole payload.
#
# The scan ran over the raw tool JSON, which embeds file_path — so the
# placeholder filter below saw the path too, and any file whose name contained
# an excluded word ("latest" contains "test") suppressed a real credential match
# in the content. Anchoring the path allowlist alone did not fix that; the two
# defects hid each other.
content="$(printf '%s' "$input" | jq -r '[.tool_input.content // empty, .tool_input.new_string // empty, .tool_input.old_string // empty] | join("\n")' 2>/dev/null || true)"
if printf '%s' "$content" | grep -iE "(api[_-]?key|client[_-]?secret|secret[_a-z]*|password|passwd|private[_-]?key|auth[_-]?token|access[_-]?token|bearer[_-]?token)[^\"[:space:]]{0,40}[[:space:]]*[:=][[:space:]]*\\\\?[\"']?[A-Za-z0-9_./+=:-]{16,}|[^a-zA-Z0-9_]sk-(proj-)?[A-Za-z0-9_-]{16,}|[^a-zA-Z0-9_]ghp_[A-Za-z0-9]{20,}|[^a-zA-Z0-9_]gh[oprsu]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|[^a-zA-Z0-9_]xox[baprs]-[A-Za-z0-9-]{10,}|-----BEGIN [A-Z ]{3,} KEY-----" | grep -ivE "(test|dummy|example|sample|fake|placeholder|change[_-]?me|your[_-]?key|xxxx|<[^>]+>)"; then
  echo "ERROR: secret-scan blocked this edit: looks like a credential is being written to ${path:-<unknown>}" >&2
  exit 2
fi
exit 0
