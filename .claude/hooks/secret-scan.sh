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
#
# TL-H4 — `edits[].new_string` is included. MultiEdit is in the hook's own
# matcher, but the extractor read only content/new_string/old_string, so every
# credential written through MultiEdit was invisible to a hook that claimed to
# cover it.
#
# TL-H4 — jq failing is a REFUSAL, not a clean report. This ended in `|| true`,
# so an unresolvable jq produced empty content, matched nothing, and exited 0. A
# scanner that cannot read its input must never answer "no secret found".
if ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: secret-scan cannot run: jq is not available, so the edit cannot be checked" >&2
  exit 2
fi
content="$(printf '%s' "$input" | jq -r '
  [ .tool_input.content // empty,
    .tool_input.new_string // empty,
    .tool_input.old_string // empty
  ] + [ (.tool_input.edits // [])[] | (.new_string // empty), (.old_string // empty) ]
  | join("\n")
')" || {
  echo "ERROR: secret-scan cannot run: the tool payload could not be parsed, so the edit cannot be checked" >&2
  exit 2
}

# TL-H4 — the placeholder filter judges the matched VALUE, not the whole line.
#
# It was `grep -ivE "(test|dummy|…|<[^>]+>)"` applied to the matched line, so a
# real credential passed whenever its line happened to contain one of those
# words anywhere: a trailing `// tested in staging` was enough, and so was
# `latest`, `greatest`, `attest`, or any `<tag>`. Replacing "tested" with "used"
# blocked the identical key, which is not a security property, it is a
# coincidence.
#
# Now the value is extracted and judged on its own. A placeholder VALUE is still
# allowed — that is what the filter is for — but nothing else on the line can
# excuse a genuine one.
SECRET_CONTENT="$content" python3 - <<'PY_SCAN'
import os
import re
import sys

content = os.environ.get("SECRET_CONTENT", "")

# Two shapes: an assignment whose right-hand side is the value, and a token that
# is self-identifying wherever it appears.
ASSIGNMENT = re.compile(
    r"(?:api[_-]?key|client[_-]?secret|secret[a-z_]*|password|passwd|private[_-]?key"
    r"|auth[_-]?token|access[_-]?token|bearer[_-]?token)"
    r"[^\"\s]{0,40}\s*[:=]\s*\\?[\"']?([A-Za-z0-9_./+=:-]{16,})",
    re.IGNORECASE,
)
TOKENS = re.compile(
    r"(?<![A-Za-z0-9_])(sk-(?:proj-)?[A-Za-z0-9_-]{16,}"
    r"|ghp_[A-Za-z0-9]{20,}|gh[oprsu]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}"
    r"|xox[baprs]-[A-Za-z0-9-]{10,})"
)
PEM = re.compile(r"-----BEGIN [A-Z ]{3,} KEY-----")

# Judged against the VALUE alone. `<...>` is included because a templated value
# IS the placeholder; on a whole line it merely meant "this line has a tag".
PLACEHOLDER = re.compile(
    r"test|dummy|example|sample|fake|placeholder|change[_-]?me|your[_-]?key|xxxx|^<[^>]+>$",
    re.IGNORECASE,
)


def is_placeholder(value: str) -> bool:
    return bool(PLACEHOLDER.search(value))


if PEM.search(content):
    sys.exit(1)

for match in ASSIGNMENT.finditer(content):
    if not is_placeholder(match.group(1)):
        sys.exit(1)

for match in TOKENS.finditer(content):
    if not is_placeholder(match.group(1)):
        sys.exit(1)

sys.exit(0)
PY_SCAN
if test $? -ne 0; then
  echo "ERROR: secret-scan blocked this edit: looks like a credential is being written to ${path:-<unknown>}" >&2
  exit 2
fi
exit 0
