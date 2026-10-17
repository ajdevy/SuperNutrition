---
title: 'Preserve native APIs while correcting existing Apple source lint'
type: 'maintenance'
created: '2026-10-13'
status: 'in-review'
baseline_commit: '5ee5de077866feed70b418ee2760af24322b1d22'
context:
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md'
---

## Findings recorded before correction

The current full check with the Intel host's explicit Apple target registration
found existing, unchanged baseline source problems. These are not newly
introduced runtime defects. Log: `/tmp/supernutrition-final-quality-connected-oct13.log`.

- `feature/photo-analysis/src/iosMain/.../RewardsViewController.kt`: four
  `FunctionSignature` formatting findings at lines 11–13.
- `apps/sharedKit/src/iosMain/.../SharedKitPhotoAnalysisSession.kt`:
  `applyNativePocAnalysis` is 92 lines / cyclomatic complexity 16 (limits 60/14),
  and its region-bounds require call has missing trailing-comma/indentation findings.

## Intended correction

Preserve every public Swift-facing name, argument order/type, validation order,
error message, inference identity, provenance, state event and native effect.
Extract small private validation/building helpers inside the session class and
correct declaration/call formatting. No new suppressions or policy exclusions.

## Ownership and acceptance

- [x] Format only `mobile/feature/photo-analysis/src/iosMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsViewController.kt`.
- [x] Correct only the above method/helper slice in `mobile/apps/sharedKit/src/iosMain/kotlin/com/supernutrition/sharedkit/SharedKitPhotoAnalysisSession.kt`.
- [x] Root re-runs the relevant Apple source lint and attempts native compilation,
  recording the real Xcode prerequisite limitation if it prevents the compiler.

No Gradle or device execution by the worker: the root owns the currently running
physical-device build and the one-build-at-a-time constraint. Preserve all other
working-tree changes. A clean lint result is not Apple compilation or execution.

## Implementation and worker verification — 2026-10-13

- Reflowed the two-argument `RewardsViewController` signature onto one line and
  placed its multiline expression body on the next line. The existing annotation,
  public names, argument order and types, and controller body are unchanged.
- Extracted the dish and component construction blocks into two private session
  helpers, `nativePocDishes` and `nativePocComponents`. The public method retains
  its readiness/photo guards, array-size checks, request guard, empty-region
  recovery, stage changes and final fusion/dispatch in their original order.
- Added the missing trailing comma and required continuation indentation to the
  component bounds check. No suppression, dependency, policy or other file was
  changed by this worker.
- Static verification inlined both helper bodies back into the public method
  and compared the entire reconstructed session's Kotlin tokens to a saved
  pre-edit source snapshot. All tokens matched after ignoring whitespace and
  optional call-site trailing commas. This covers unchanged error strings,
  inference IDs/provenance and event/validation order. Rewards likewise matched
  token-for-token after formatting normalization.
- Static body counts are 37 nonempty lines for `applyNativePocAnalysis`, 25 for
  the dish helper and 37 for the component helper. These are source counts,
  not Detekt's line or complexity measurements. Both files remain within 120
  columns; the scoped `git diff --check` passed.
- No Gradle, Apple compiler, physical-device action or Git mutation was run by
  the worker. The root's pending lint and compiler verification remain required;
  no clean Apple build or runtime result is claimed here.
