# N-28 ignored invalidation — Edge Case Hunter

Reviewed on 2026-10-17 in `REDACTED_WORKTREE/a704/SuperNutrition`, branch `codex/figma-camera-flow`, HEAD `5ee5de077866feed70b418ee2760af24322b1d22`. The checkout contained unrelated tracked and untracked work before this review. This reviewer changed only this report.

Input: `mobile/build/review-ignored-invalidation-scoped.diff`, SHA-256 `5faf5e422d50f036c3245ae2b7b16ba90ed441aab103bfc1b77e5bad2a65f9a8`.

Specification: `_bmad-output/implementation-artifacts/spec-review-ignored-invalidation.md`. Applied the project-local `.agents/skills/bmad-review-edge-case-hunter/SKILL.md` and its deletion-check reference, after reading `AGENTS.md` and the relevant architecture-spine rules (AD-5, AD-6, AD-8, AD-18, AD-20 and AD-40).

## Scope and method

The review targets the scoped diff's three files: the common reducer and the two expanded regression tests. Referenced lifecycle, transition, state/snapshot, lease/session, route-coordinator and feature-dispatch functions were inspected only to resolve behavior directly reachable from the changed lines. Other worktree modifications were not reviewed. Graphify's existing graph was queried for navigation; current source was used to resolve the paths.

The Edge Case Hunter steps were completed in order:

1. **Receive content.** Loaded the nonempty text diff and identified its three replacement hunks and two expanded tests.
2. **Exhaustive path analysis.** Partitioned normal dispatch versus `dispatchBackgroundWork` early return; `stateChanged` true versus false; every event arm in background-cache maintenance; all seven public-state variants; and every public snapshot variant. Traced accepted and ignored `RestoreFromSnapshot`, `ReplacePhoto`, `Cancel`, `ScanAgain`, `Restored`, and repeated `Backgrounded`. Replacement partitions included identical, foreign-attempt, revoked and active distinct references. Restoration partitions included absent retained state, foreign attempts, revoked leases, missing sessions, live sessions, and opaque candidate-review snapshots. Background-work partitions included absent cache, matching/stale request IDs, repeated stage, nonempty/empty evidence, no-food/failure outcome and cancellation. Feature checks traced explicit dish/component/pantry selections and notes, their defensive set copies, restoration, and accepted foreground Retake. Route checks covered invalidating/non-invalidating events and current/stale/terminal tokens; consent remained bounded by the existing receipt validation. No introduced unhandled path was collected.
3. **Validate completeness.** Revisited implicit members of the sealed event/state/snapshot sets, unchanged-state reductions with effects, repeated events, nullable cache/session/reference boundaries, ordering of cache maintenance, route cancellation and effect application, and lease loss before restoration. The changed grouped cache-clear branch has no accepted unchanged-state invalidator with a remaining cleanup effect in the referenced transition code. Background cancellation takes the existing early-return work path. No additional finding was collected.
4. **Deletion check.** Read `references/deletion-check.md` after the path/completeness passes. Checked the replaced cache-maintenance call/signature, removed unconditional grouped clear, replaced route comparison, and replaced single-event test assertions. The shared state comparison preserves the route predicate; accepted grouped transitions retain cache clearing; ignored-event cache clearing is intentionally retired by N-28. The test expansions retain foreground restoration, fresh-process refusal, complete selection/note assertions, and foreground Retake cleanup. No deletion regression, orphaned reference or newly dead code was collected.
5. **Present findings.** The complete findings array is below.

## Findings

```json
[]
```

## Evidence limits

This is an independent static path review of the supplied scoped diff, not a clean bill of health for the full dirty worktree or every lifecycle implementation. It does not claim runtime verification, line/branch coverage, lint, host/native compilation, device execution, Bluetooth evidence or consented remote transfer. This reviewer ran zero builds and zero tests and performed no device/emulator actions, staging or commits. The spec's red/green counts are background input, not results independently reproduced by this review. There are zero reported findings awaiting runtime confirmation; runtime gates remain the root agent's responsibility.

Navigation and inspection used `pwd`, branch/HEAD/worktree/status inspection, `rg`, `nl`, `sed`, `cat`, a read-only graph-vocabulary extraction, `graphify query 'reducer lifecycle backgrounded restore snapshot photo analysis event cancel lease consent route' --budget 1800`, and the input SHA-256 check. Graphify reflection/result persistence/update were not run because this task permits only the report mutation and changes no code.
