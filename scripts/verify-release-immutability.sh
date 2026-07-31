#!/usr/bin/env bash
# Cross-revision immutability gate for published contract releases.
#
# Every other contract validator reads only the working tree, comparing a
# computed digest against a digest recorded in that same tree. That detects a
# release edited without updating its digest, and nothing else. It cannot detect
# a release edited together with every digest describing it, because the tree is
# then internally consistent — which is precisely what commit 83547213 did to
# four releases on 2026-07-31, green.
#
# The missing property is not "the digest matches the bytes" but "the bytes have
# not changed since they were published". That is only answerable against a base
# revision, so this gate reads git history rather than the tree. The base is
# derived from outside the change under review and is never read from a file in
# it, since a file in the tree is exactly what an in-tree change controls.
#
# Additions are always permitted: publishing a new release and appending its
# catalog entry is the normal workflow and must not be blocked.
set -euo pipefail

target_root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
catalog_path="contracts/release-catalog/v1/digests.toml"

cd "$target_root"

if ! git rev-parse --git-dir >/dev/null 2>&1; then
    printf 'release immutability requires a git repository: %s\n' "$target_root" >&2
    exit 1
fi

# SUPERNUTRITION_IMMUTABILITY_BASE exists for fixtures and for CI, which knows
# its own merge base. It is an explicit argument, never a value the reviewed
# tree supplies.
base="${SUPERNUTRITION_IMMUTABILITY_BASE:-}"
if test -z "$base"; then
    base=$(git merge-base origin/main HEAD 2>/dev/null || true)
fi
if test -z "$base"; then
    printf 'cannot determine an immutability base revision; pass SUPERNUTRITION_IMMUTABILITY_BASE\n' >&2
    exit 1
fi
if ! git rev-parse --verify --quiet "${base}^{commit}" >/dev/null; then
    printf 'immutability base is not a commit: %s\n' "$base" >&2
    exit 1
fi

status=0

# 1. Published release bytes are frozen. Any modification or deletion of a file
#    that existed under a versioned release directory at the base revision is a
#    violation regardless of how consistent the resulting tree looks.
while IFS=$'\t' read -r state path _rest; do
    test -n "${state:-}" || continue
    # The catalog sits under contracts/ and matches the release-directory shape,
    # but it is the record ABOUT releases, not a release. Appending to it is the
    # normal act of publishing, so it is governed by the append-only rule below
    # rather than by byte-freezing.
    test "$path" != "$catalog_path" || continue
    case "$path" in
        contracts/*/v[0-9]*/*) ;;
        *) continue ;;
    esac
    case "$state" in
        A) continue ;;
        M|D|R*|C*|T)
            printf 'published release file %s was %s since %s; releases are immutable, publish a new version instead\n' \
                "$path" \
                "$(case "$state" in M) echo modified ;; D) echo deleted ;; T) echo retyped ;; R*) echo renamed ;; *) echo copied ;; esac)" \
                "${base:0:12}" >&2
            status=1
            ;;
    esac
done < <(git diff --name-status --no-renames "$base" HEAD -- 'contracts/' || true)

# 2. The catalog is append-only. Existing entries record what a release was when
#    it was published; rewriting one destroys the only evidence that a release
#    changed. Additions are how new releases are recorded.
if git cat-file -e "${base}:${catalog_path}" 2>/dev/null; then
    removed=$(git diff --numstat "$base" HEAD -- "$catalog_path" | awk '{print $2}')
    if test -n "${removed:-}" && test "$removed" -ne 0; then
        printf 'release catalog %s had %s line(s) removed or rewritten since %s; the catalog is append-only\n' \
            "$catalog_path" "$removed" "${base:0:12}" >&2
        status=1
    fi
fi

if test "$status" -ne 0; then
    exit 1
fi

printf 'release immutability: OK (no published release or catalog entry changed since %s)\n' "${base:0:12}"
