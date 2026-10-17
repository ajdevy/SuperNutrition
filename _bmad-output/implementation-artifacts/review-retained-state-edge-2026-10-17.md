# Retained review recreation — Edge Case Hunter

Reviewed at 2026-10-17T05:09:58Z on the x86_64 host. Worktree:
`/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`; branch:
`codex/figma-camera-flow`; HEAD: `5ee5de077866feed70b418ee2760af24322b1d22`.
The checkout contains unrelated modified and untracked work. This reviewer wrote only this report.

Input: `mobile/build/review-state-recreation-scoped.diff`.
SHA-256: `9e8ebdd6d6407e50871798fa4f2b5dd43587322417dc509c4c6d6b32247b7343`.
Reviewed reducer SHA-256: `51bf97825fb23f10e9344cf84294fff6013f3326a97b486d99af938010e43cfa`.
Spec: `_bmad-output/implementation-artifacts/spec-review-state-recreation.md`.

Applied the installed `bmad-review-edge-case-hunter` skill in order: receive diff,
enumerate paths, revisit edge classes, run the referenced deletion check, present
the findings array. Read `AGENTS.md` and the architecture spine; AD-5, AD-6,
AD-8, AD-18 and AD-23 govern the referenced lifecycle boundaries. Graphify was
used for navigation; current source was used to trace behavior.

## Findings

```json
[]
```

No introduced unhandled path was identified in the scoped diff. This is a
static review result, not runtime proof or a clean bill of health for the mixed
checkout.

## Completeness

The second pass revisited all seven `PhotoAnalysisState` variants, all six
process-snapshot variants, nullable retained-cache branches, repeated snapshot
events, and both relative orders of snapshot/foreground restoration.

| Required boundary | Static trace |
| --- | --- |
| Accepted Capture restoration | Both `reduce` and cache maintenance use the same private predicate, `this == Capture` (`PhotoAnalysisReducer.kt:73-75,93-94,107`). Capture restoration retains its existing lifecycle validation and clears the cache. |
| Snapshot ignored while backgrounded | `RestoreFromSnapshot` is excluded from background-work dispatch, is ignored by the Backgrounded transition, and no longer clears the retained cache (`PhotoAnalysisReducer.kt:41-45,73-77,120-129`; `PhotoAnalysisReducerTransitions.kt:13`). |
| Snapshot ignored while foregrounded | Every non-Capture state reaches its existing no-op event branch; the foreground live state is retained. `Restored` clears the cache after restoring it, so a later ignored snapshot has no previous cache to resurrect. |
| Live evidence arriving while backgrounded | Request-bound evidence is reduced against the retained live state and refreshes the opaque background snapshot. An ignored process snapshot before or after evidence no longer discards that state (`PhotoAnalysisReducer.kt:41-51`; `PhotoAnalysisAnalysisTransitions.kt:27-47`). Empty evidence, wrong request IDs, expired leases and cancellation retain their existing branches. |
| Lease and session boundaries | `Restored` still invokes `restoreInMemory`; review/Ready/Analyzing restoration requires the owning attempt, an active lease and readable session content (`PhotoAnalysisReducerLifecycle.kt:27-46,56-63`). Opaque process review restoration still returns `REVIEW_RESTORE_UNAVAILABLE` (`:18,75-76`). The diff does not bypass these checks. |
| Route and consent handling | The changed event creates no effect, route token or receipt. Existing route invalidation events remain unchanged (`PhotoAnalysisReducer.kt:109-117`). Snapshot types contain no consent/provider data. Route acceptance and receipt binding remain in `RouteAttemptCoordinator` and `LocalFirstRouter`, unchanged by this diff. |
| Feature choices | Feature reset policy remains event-and-state-change dependent. `RestoreFromSnapshot` and `Restored` are absent from the reset event set; retained selection updates still copy evidence sets (`PhotoAnalysisFeature.kt:59-89,149-157`). The changed test asserts live review, explicit component choices and manual query preservation. |
| Android ordering | The referenced Activity restores the snapshot in `onCreate`, dispatches `Restored` in `onStart`, and retains the feature/stores in its ViewModel (`PhotoAnalysisComposeActivity.kt:232,321-325,437-443,481-499`). This ordering reaches the corrected ignored-snapshot branch. |

Unchanged lifetime behavior was not promoted into introduced findings:
`currentWorkState()` exposes the cached state before foreground lease validation;
the existing Recovery restoration validates only attempt ownership; other
cache-invalidating events retain their existing behavior. No runtime assertion
about these existing behaviors was made.

## Deletion check

The removed unconditional `RestoreFromSnapshot` cache invalidation is
re-established for accepted Capture restoration and intentionally retired for
ignored restoration. The previous pure Capture condition is preserved by the
shared predicate. The new predicate has both intended callers; no removed test
assertion, orphaned reference or newly dead path was identified. Deletion
findings: zero.

## Verification limits

This reviewer ran only repository identity/status commands, source/diff reads,
searches, graph queries, hashes and report validation. No Gradle task, test,
APK/native build, emulator/device action, staging or commit was performed; the
root agent owns the running build. Reviewed test assertions are source evidence
only. No red/green test counts or Android recreation/IME results were
independently reproduced here.

Graph traversal output was budget-limited and used only as navigation. No
graph regeneration or graph-feedback write was performed under this report-only
ownership. The completeness claim is confined to paths reachable from the
three-file scoped diff and directly referenced reducer/lifecycle/feature/host
code; it excludes the rest of the dirty checkout and native callback scheduling
under real execution.
