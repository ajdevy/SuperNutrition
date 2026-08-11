# Resolving `pr/slice-ab-mobile` into `pr/slice-ab-backend`

Verified by performing the merge in a scratch worktree and running every check.
Ten files conflict. None of them is arbitrary — each has a side that is correct
for a reason.

## Take the MOBILE side (`--theirs`)

| file | why |
|---|---|
| `contracts/release-catalog/v1/digests.toml` | Superset. Same mobile-api correction, same superseded row and same market-policy entry as the backend slice, **plus** the two v2 releases this branch publishes. Taking the backend side silently drops `inference/v2` and `inference-evidence/v2`, and `verify-release-catalog.sh` then fails on main. |
| `README.md` | Carries the coverage snapshot for the merged tree. Re-run the two metrics commands after merging and confirm the file does not change. |
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
