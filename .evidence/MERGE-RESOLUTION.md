# Resolving `pr/slice-ab-mobile` into `pr/slice-ab-backend`

Verified by performing the merge in a scratch worktree and running every check.
Ten files conflict. None of them is arbitrary — each has a side that is correct
for a reason.

## Take the MOBILE side (`--theirs`)

| file | why |
|---|---|
| `contracts/release-catalog/v1/digests.toml` | Superset. Same mobile-api correction, same superseded row and same market-policy entry as the backend slice, **plus** the two v2 releases this branch publishes. Taking the backend side silently drops `inference/v2` and `inference-evidence/v2`, and `verify-release-catalog.sh` then fails on main. |
| `README.md` | Carries the coverage snapshot for the merged tree. Re-run the two metrics commands after merging — the file WILL change; see below. |
| `scripts/verify-inference-contract.sh` | The backend slice removed the v2 wiring because it does not publish v2. After the merge v2 exists, so the wiring must come back — that was H1's design. This side also carries the P1-11 expectations. |
| `scripts/inference-contract-negative-fixtures.sh` | Same reason: the v2 mutation groups belong with the release, and this side has the P1-1 fix (each release naming its own root). |

## Take the BACKEND side (`--ours`)

| file | why |
|---|---|
| `contracts/root-policy/v1/boundaries.toml` | Registers `verify-release-catalog.sh`. |
| `scripts/verify-root-boundaries.sh` | M17: bare-path rule and the rg/grep exclusion alignment. |
| `scripts/emit-release-digest.sh` | M13: one shared digest implementation. |
| `scripts/verify-release-immutability.sh` | M12 + H3: acknowledgements, worktree comparison, addition rule, structural catalog rule. |
| `scripts/release-immutability-negative-fixtures.sh` | The seven fixtures that hold the above honest. |

## Merge BY HAND

`scripts/emit-ci-fingerprint.sh` — keep the backend slice's per-release
derivation (M15) **and** add the two releases the mobile slice publishes:

```
contracts/inference/v2 \
contracts/inference-evidence/v2 \
```

Taking either side whole is wrong: the backend side omits the v2 releases, and
the mobile side re-introduces the hand-listed file set M15 replaced.

## After resolving

All of these pass on the merged tree, and were run to confirm it:

- 14 contract scripts, including `verify-release-catalog.sh` and
  `verify-release-immutability.sh`
- the script unit tests
- all six v2/market-policy `ci-required.yml` steps are present again, which is
  what H1 promised the merge would restore

## The README snapshot has to be regenerated after merging

The tracked-source-file count is a property of the merged tree, so no single
value is correct on both branches at once. Measured with
`scripts/build_coverage_site.py`'s own counter:

| tree | files | code lines |
|---|---:|---:|
| `pr/slice-ab-mobile` alone | 706 | 277,859 |
| both branches merged | 709 | 279,473 |

This branch's README records 706, which is what its own tree measures. The
three files that move it to 709 are on the backend slice
(`scripts/benchmark-negative-fixtures.sh`, `scripts/secret-scan-fixtures.sh`,
`backend/mobile-api/apps/api/src/logging/telemetry-allowlist-config.test.ts`).

`ci-required.yml` only ever runs `--check` against a freshly built `_site` on a
merged tree, so 709 is the value that has to be in main. After merging, and
after the Gradle coverage run that produces the Kover XML the site builder
needs:

```
python3 scripts/build_coverage_site.py --output-dir _site
python3 scripts/update_readme_metrics.py --readme README.md --site-dir _site
```

Commit the result. The coverage percentages are not expected to move — the
mobile modules they measure are the same on both sides — but they come from
that run rather than from this note.

## Verified on the merged tree

The merge above was performed and resolved in a scratch worktree, and on the
result:

- 11 of 12 `verify-*.sh` scripts pass. `verify-ci-fingerprints.sh` fails for
  want of CI-run artifacts, identically to how it fails on either branch alone;
  it is not a code defect.
- all 13 negative-fixture suites pass, including the three added with the
  Low-severity backend fixes.
- `verify-release-catalog.sh` reports `OK (7 current entries recomputed from the
  tree)`, which is the check that would otherwise have failed on main for the
  missing `inference/v2` and `inference-evidence/v2` entries.
