# SuperNutrition Agent Guide

This file is the canonical agent guide. `CLAUDE.md` imports it; keep agent-specific copies out of sync by editing this file only.

## Start every task

- Confirm the real `cwd`, branch, worktree, and `git status --short --branch` before acting. Worktree identity is part of the task: never edit a dirty user checkout to reach another branch.
- Preserve unrelated changes. Never use `git reset --hard`, broad checkout, recursive deletion, or blanket staging. Stage only files in scope.
- Read the relevant story/spec and `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`. Use `graphify query` for codebase navigation when `graphify-out/graph.json` exists.
- Use the project-local skill for the task. On a fresh checkout, run `./scripts/setup-agent-tools.sh` only when the required tooling is actually missing.
- When the next BMAD workflow is unclear, use `bmad-help`. Keep shared BMAD artifacts under `_bmad-output/`; keep team config in `_bmad/config.toml` or `_bmad/custom/`, never `_bmad/config.user.toml`.

## Product and architecture invariants

- Product roots are independent: `mobile/`, `web/landing/`, and `backend/mobile-api/`. Cross-product source, runtime, cache, build-task, generated-code, or deployment dependencies are forbidden; integrate through immutable versioned contracts.
- Mobile module groups are `apps`, `core`, `domain`, `data`, `inference`, `platform`, `feature`, `testing`, and `evaluation`. Keep domain pure KMP, hosts thin, and native photo/camera bridges behind ports.
- Product identity is exact: installed name `Super Nutrition`; store listing `SuperNutrition AI nutritionist`. Do not leak the store descriptor into the installed display name.
- The checked-in mobile lifecycle is currently `active` with `source_digest = "local-only"` and Apple evidence explicitly good-to-have. Re-read `mobile/product-lifecycle.toml` before changing lifecycle policy; do not silently revert to scaffold-pending or make Apple execution a local blocker.
- Quality entrypoints are read-only: `qualityCheckJvmAndroid` and `qualityCheckApple`. `qualityFormat` is the only autocorrect entrypoint and never runs in CI. Do not add broad baselines, coverage exclusions, wildcard Compose stability entries, or production dependencies on `testing`.
- Every production source has one coverage denominator; module and aggregate line/branch coverage targets are at least 85%, and changed lines/branch outcomes are 100%. Required Android/native equivalents are executable and expiring. Apple/Xcode evidence remains optional unless a policy explicitly says otherwise.
- Treat architecture, source-set, scenario, journey, platform-contract, native-gap, and coverage manifests as enforceable inventories. Do not hide required work with task filters, skips, empty modules, stale evidence, or “eligibility.”
- Low-friction interaction is product-wide: defaults are visible, sourced, editable, reversible, and session-scoped; never silently default remote consent, uncertain identity, medical/allergen conclusions, or provider routing.
- Do not edit vendored agent prompts without updating `vendor/claude-code-agents/UPSTREAM_COMMIT` and documenting why.

## Review → fix → verify loop

1. Convert every concrete review finding into a testable acceptance condition. Fix the highest-impact issue first and keep stable module paths unless boundary evidence proves a rename necessary.
2. Validate in layers: compile/configuration, focused unit tests, full relevant module tests, then device/native tests. A compile failure means no device test ran; a launch failure means execution was partial; report those states separately.
3. Re-run the full relevant task after each fix. Do not call a task green from partial output, cached compilation, or a report from a different commit.
4. If adversarial/edge review agents are unavailable, record that honestly; never present generated prompts or a static scan as completed subagent review.

## Android device evidence

- Use the project `android-cli` skill and `adb devices -l`. An emulator is not physical-device evidence; an emulator/simulator is never Bluetooth proof.
- For the complete Android instrumentation suite, run from the repository root:

  `./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest --no-configuration-cache --console=plain`

- Disconnect duplicate ADB endpoints before evidence runs. With multiple devices, pass `-Pandroid.testInstrumentationRunnerArguments.deviceSerial=<serial>` and use strict dependency verification when the evidence command requires it. Confirm both `productionDebug` and `uiAcceptanceDebug` ran. Verify the XML reports under `mobile/apps/androidApp/build/outputs/androidTest-results/connected/` show exact `tests`, `failures`, `errors`, and `skipped` counts.
- Treat ABI/API, lifecycle reflection, permission, camera, UI semantics, and accessibility failures as product failures to fix—not as device noise. When Compose sets `testTagsAsResourceId`, UI Automator must query resource IDs (`By.res`), not descriptions (`By.desc`).

## Apple/iOS device evidence

- The configured Xcode Apple Account is `REDACTED_EMAIL`,
  exposing the `Contributor` team with Team ID `REDACTED_TEAM_ID`. The
  `DEVELOPMENT_TEAM` setting is checked into the iOS project for the production,
  acceptance-host, and acceptance-test configurations.
- Certificates, private keys, and generated provisioning profiles are local
  machine inputs and must never be committed. Run `pod install` in
  `mobile/apps/iosApp` and use
  `SuperNutritionUIAcceptance.xcworkspace`; the Podfile keeps static CocoaPods
  libraries unsigned while Xcode signs the app/test products.
- Physical iOS evidence requires a paired iPhone in Developer Mode, listed by
  `xcrun devicectl list devices`, and unlocked before install or launch. A
  locked-device launch failure is an execution prerequisite failure, not a
  product-test result. Follow `mobile/apps/iosApp/README.md` and
  `mobile/REAL_DEVICE_TESTING.md` for the exact arm64 build, acceptance, and
  fixture commands.

## Policy, CI, and merge evidence

- Parse structured TOML/manifests with the typed parser; keep cached documents immutable, reject duplicate declarations and empty inventories, and fail closed. Do not reintroduce regex parsing or string round-tripping for policy decisions.
- Local policy and quality checks are authoritative. GitHub Actions is an optional adapter: a zero-step, immediate failure is runner/billing evidence, not Gradle/test evidence. Never call hosted CI green when it is queued, failed, or billing-blocked.
- Before push/merge, refresh the PR head, base, review state, and checks. Push or merge only when explicitly requested. If an admin merge overrides infrastructure failure, state that clearly and verify `origin/main` plus post-merge runs afterward.

## Completion handoff

- Run `git diff --check`; run `graphify update .` after code changes. Generated `build/` output is never staged; expected dirty graph files are not a reason to skip graphify.
- Report exact branch, commit, worktree state, commands, device/host, test counts, reports, and limitations. Link real files with absolute paths when useful.
- Commit only the intended slice. Leave unrelated WIP untouched. Do not claim completion until the evidence matches the full requested scope.
