#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
docker_bin="${DOCKER_BIN:-docker}"
image="${SUPERNUTRITION_CI_DOCKER_IMAGE:-supernutrition-ci-jvm:local}"
cache_root="${SUPERNUTRITION_DOCKER_GRADLE_CACHE:-${TMPDIR:-/tmp}/supernutrition-gradle-cache}"
source_manifest="$(mktemp "${TMPDIR:-/tmp}/supernutrition-source-files.XXXXXX")"

cleanup() {
  rm -f "$source_manifest"
}
trap cleanup EXIT

command -v "$docker_bin" >/dev/null 2>&1 || {
  printf 'Docker CLI is unavailable: %s\n' "$docker_bin" >&2
  exit 1
}
"$docker_bin" info >/dev/null
git -C "$repo_root" ls-files >"$source_manifest"
commit="$(git -C "$repo_root" rev-parse HEAD)"
mkdir -p "$cache_root"

"$docker_bin" build \
  --file "$repo_root/docker/ci-jvm.Dockerfile" \
  --tag "$image" \
  "$repo_root/docker"

"$docker_bin" run --rm --init \
  --user "$(id -u):$(id -g)" \
  --workdir /workspace \
  --volume "$repo_root:/workspace" \
  --volume "$cache_root:/tmp/gradle-home" \
  --volume "$source_manifest:/tmp/supernutrition-source-files.txt:ro" \
  --env GRADLE_USER_HOME=/tmp/gradle-home \
  --env HOME=/tmp/container-home \
  --env SUPERNUTRITION_SOURCE_FILES=/tmp/supernutrition-source-files.txt \
  --env SUPERNUTRITION_COMMIT="$commit" \
  "$image" \
  ./scripts/run-local-ci.sh jvm
