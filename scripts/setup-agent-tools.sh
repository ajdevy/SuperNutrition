#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if ! command -v uv >/dev/null 2>&1; then
  echo "uv is required. Install it from https://docs.astral.sh/uv/ and rerun this script." >&2
  exit 1
fi

graphify_version="$(tr -d '[:space:]' < "$repo_root/.codex/skills/graphify/.graphify_version")"
echo "Installing graphifyy ${graphify_version} with uv..."
uv tool install --force "graphifyy==${graphify_version}"

if ! command -v graphify >/dev/null 2>&1; then
  echo "graphify was installed, but its bin directory is not on PATH yet." >&2
  echo "Restart your shell or add the uv tool bin directory to PATH, then rerun this script." >&2
  exit 1
fi

echo "Configured project-local BMAD and graphify skills."
echo "graphify version: $(graphify --version)"
