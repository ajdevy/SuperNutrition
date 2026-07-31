---
title: 'Resolve PR #24 fifth-pass review findings'
type: 'bugfix'
created: '2026-07-30'
status: 'done'
baseline_commit: 'b270049e86330aa808a0b0011aec73a08d729fcf'
review_loop_iteration: 5
context:
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md'
  - '{project-root}/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-gh-24-fourth-pass-review-fixes.md'
---

<frozen-after-approval reason="human-owned intent — fifth-pass review scope">

## Intent

Repair the remaining correctness findings from the fifth-pass review of PR #24 at their ownership boundaries, preserving stable module paths and optional Apple execution policy.

## Boundaries & Constraints

**Always:** Keep Apple/Xcode runtime execution optional; require arm64 compile/link evidence when available; keep photo bytes bounded and out of snapshots/logs; preserve pure KMP domain ownership; keep localization fail-closed; preserve static SharedKit framework integration.

**Never:** Do not replace checked crypto with a truthy wrapper; do not use unbounded iOS image decoding; do not ship test fixtures in production source; do not retain deprecated lifecycle workarounds; do not hide enum, policy, or presenter failures behind defaults or regex-only interpretation.

</frozen-after-approval>

## Tasks & Acceptance

- [x] Fix Swift/KMP action evolution, Info.plist copy ownership, native delegate cleanup, and iOS crypto/image bounds.
- [x] Fix Android result exception containment, permission-request state timing, lifecycle retention, and CameraX listener cleanup.
- [x] Move the presenter fixture to test ownership without weakening module boundaries, and simplify presenter copy ownership/API.
- [x] Replace PolicyToml regex extraction with a typed parser that safely handles strings, arrays, sections, and malformed input.
- [x] Add focused regression coverage for the typed parser and preserve the existing presenter, normalization, and bridge contract suites.

## Acceptance Criteria

- Given unknown/future native actions or malformed policy/snapshot data, the host rejects them deterministically without silent fallback or process-wide crashes where recovery is possible.
- Given iOS/Android image, permission, camera, or picker failure, the operation completes once with bounded memory and typed recovery.
- Given presenter and test copy changes, production source contains no test-only fixture and callers retain localized, responsibility-owned copy.
- Given canonical arm64 compilation, Xcode builds the production and acceptance targets with signing disabled; Apple execution remains optional.

## Verification

- Strict focused JVM/Android compile and test gates pass.
- `verifyMobilePolicy`, governance scripts, `git diff --check`, and `graphify update .` pass.
- SharedKit arm64 link and production/acceptance Xcode arm64 compile pass with signing disabled.
