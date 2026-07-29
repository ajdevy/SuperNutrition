#!/usr/bin/env bash
# Shared read-only helpers for evidence, fingerprint, and acceptance scripts.

require_command() {
  local command_name="$1"
  command -v "$command_name" >/dev/null 2>&1 || {
    printf 'missing required tool: %s\n' "$command_name" >&2
    return 1
  }
}

hash_stream() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  else
    require_command shasum || return 1
    shasum --algorithm 256 | awk '{print $1}'
  fi
}

hash_file() {
  local path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$path" | awk '{print $1}'
  else
    require_command shasum || return 1
    shasum --algorithm 256 "$path" | awk '{print $1}'
  fi
}

digest_files() {
  local repo_root="$1"
  shift
  {
    local relative
    for relative in "$@"; do
      test -f "$repo_root/$relative" || {
        printf 'missing digest input: %s\n' "$relative" >&2
        return 1
      }
      printf '%s\0%s\0' "$relative" "$(hash_file "$repo_root/$relative")"
    done
  } | hash_stream
}

digest_tree() {
  local repo_root="$1"
  local root_relative="$2"
  test -d "$repo_root/$root_relative" || {
    printf 'missing digest directory: %s\n' "$root_relative" >&2
    return 1
  }
  {
    local absolute relative
    while IFS= read -r absolute; do
      relative="${absolute#"$repo_root/"}"
      printf '%s\0%s\0' "$relative" "$(hash_file "$absolute")"
    done < <(find "$repo_root/$root_relative" -type f -print | LC_ALL=C sort)
  } | hash_stream
}

toml_string() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  printf '"%s"' "$value"
}

lifecycle_value() {
  local root="$1"
  local relative_manifest="$2"
  python3 - "$root/$relative_manifest" <<'PY'
from pathlib import Path
import re
import sys

text = Path(sys.argv[1]).read_text(encoding="utf-8")
match = re.search(r'(?m)^lifecycle\s*=\s*"([^"]+)"\s*$', text)
if match is None:
    raise SystemExit(f"missing lifecycle in {sys.argv[1]}")
print(match.group(1))
PY
}

ensure_maestro_cli() {
  local repo_root="$1"
  local temp_root="$2"
  require_command python3 || return 1
  if ! command -v maestro >/dev/null 2>&1; then
    require_command curl || return 1
    require_command unzip || return 1
    local archive="$temp_root/maestro.zip"
    curl --fail --location --silent --show-error \
      https://github.com/mobile-dev-inc/Maestro/releases/download/cli-2.7.0/maestro.zip \
      --output "$archive"
    local expected_sha256
    expected_sha256="$(sed -n 's/^sha256=\([0-9a-f]\{64\}\)$/\1/p' "$repo_root/mobile/.maestro/maestro-cli-2.7.0.sha256")"
    test -n "$expected_sha256" || {
      printf 'missing Maestro checksum pin\n' >&2
      return 1
    }
    printf '%s  %s\n' "$expected_sha256" "$archive" | {
      if command -v sha256sum >/dev/null 2>&1; then
        sha256sum --check --status -
      else
        require_command shasum || exit 1
        shasum --algorithm 256 --check --status -
      fi
    }
    unzip -q "$archive" -d "$temp_root/maestro-dist"
    export PATH="$temp_root/maestro-dist/maestro/bin:$PATH"
  fi
  local maestro_bin
  maestro_bin="$(command -v maestro || true)"
  test -n "$maestro_bin" || {
    printf 'Maestro CLI is not available after installation\n' >&2
    return 1
  }
  "$maestro_bin" --version | grep -Fq '2.7.0' || {
    printf 'Maestro CLI 2.7.0 is required\n' >&2
    return 1
  }
  printf '%s\n' "$maestro_bin"
}
