# SuperNutrition Agent Guide

This file is the canonical agent guide. `CLAUDE.md` imports it; keep agent-specific copies out of sync by editing this file only.

## Start every task

- Confirm the real `cwd`, branch, worktree, and `git status --short --branch` before acting. Worktree identity is part of the task: never edit a dirty user checkout to reach another branch.
- Preserve unrelated changes. Never use `git reset --hard`, broad checkout, recursive deletion, or blanket staging. Stage only files in scope.
- Read the relevant story/spec and `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`. Use `graphify query` for codebase navigation when `graphify-out/graph.json` exists.
- Use the project-local skill for the task. On a fresh checkout, run `./scripts/setup-agent-tools.sh` only when the required tooling is actually missing.
- A fresh worktree has no `mobile/local.properties` — it is gitignored, so it does not come across with the branch. Without it `qualityCheckJvmAndroid` fails at SDK resolution *before* inspecting anything: `4 actionable tasks` and no detekt report. That is a missing-setup result, not a quality result. Copy `sdk.dir` from the main checkout into the worktree; never read the failure as a red gate.
- When the next BMAD workflow is unclear, use `bmad-help`. Keep shared BMAD artifacts under `_bmad-output/`; keep team config in `_bmad/config.toml` or `_bmad/custom/`, never `_bmad/config.user.toml`.

## Product and architecture invariants

- Product roots are independent: `mobile/`, `web/landing/`, and `backend/mobile-api/`. Cross-product source, runtime, cache, build-task, generated-code, or deployment dependencies are forbidden; integrate through immutable versioned contracts.
- Mobile module groups are `apps`, `core`, `domain`, `data`, `inference`, `platform`, `feature`, `testing`, and `evaluation`. Keep domain pure KMP, hosts thin, and native photo/camera bridges behind ports.
- Product identity is exact: installed name `Super Nutrition`; store listing `SuperNutrition AI nutritionist`. Do not leak the store descriptor into the installed display name.
- The checked-in mobile lifecycle is currently `active` with `source_digest = "local-only"` and Apple evidence explicitly good-to-have. Re-read `mobile/product-lifecycle.toml` before changing lifecycle policy; do not silently revert to scaffold-pending or make Apple execution a local blocker.
- Quality entrypoints are read-only: `qualityCheckJvmAndroid` and `qualityCheckApple`. `qualityFormat` is the only autocorrect entrypoint and never runs in CI. Do not add broad baselines, coverage exclusions, wildcard Compose stability entries, or production dependencies on `testing`.
- Know what each entrypoint covers before quoting it as evidence. `qualityCheckJvmAndroid` inspects JVM and Android source sets only; `qualityFormat` inspects every source set including `iosMain`/`iosTest`. A green JVM/Android gate is therefore not Apple lint evidence — say which gate produced the result. Run the gates with `--continue`: they stop at the first failing module otherwise, so later modules are never inspected and an earlier run's clean report survives to be misread as current.
- Prefer a real fix, then a documented rule config, then a targeted `@Suppress` on the declaration with the reason beside it. A suppression is defensible only when obeying the rule would break a published contract — a Swift-facing name or API shape, a deliberately-throwing compatibility variant. It is never a way to retire a finding you did not want to fix, and it is never a baseline file.
- When two code paths must agree, make them share the implementation rather than keeping them in step. Duplicated segmentation, and later a duplicated quantity vocabulary, each drifted within days and produced user-visible wrong answers. Deleting the duplicate is the fix; synchronising it is not.
- Every production source has one coverage denominator; module and aggregate line/branch coverage targets are at least 85%, and changed lines/branch outcomes are 100%. Required Android/native equivalents are executable and expiring. Apple/Xcode evidence remains optional unless a policy explicitly says otherwise.
- `domain:nutrition` (the typed-text meal search/matching code) carries a stricter target than the 85% project floor: line AND branch coverage are both effectively 100%. This module is the catalog matcher, segmentation, and ranking logic the E7.2 review and its slang/edge-case sweep repeatedly found silent wrong-food defects in (курицы→eggs, масло→oil, chicken→eggs, no nuts→nuts) — an uncovered branch here is a plausible wrong answer waiting to be found, not a cosmetic gap. As of 2026-08-03 this is a **CI-enforced gate**, in addition to the aggregate 85% floor, not in place of it: `QualityPlugin.strictCoverageModules` (`mobile/build-logic/.../QualityPlugin.kt`) maps `:domain:nutrition` to a 99.99% line / 100% branch minimum and calls the new `JvmCoverageVerifier.verifyModule()` from inside both `qualityCoverageJvm` and `mobileUnitCoverage`, right after the existing aggregate check. `coverage.toml`'s own per-denominator `line_minimum_percent`/`branch_minimum_percent` fields cannot carry this: `MobilePolicyValidator` requires every denominator to declare the uniform 85, so a per-module floor stricter than that has to live beside it, not inside it.
  Branch is a genuine 100%, achieved by closing every real gap the Kover XML report showed (not the HTML report — see below). Line stops at 99.99, not 100, because of exactly one confirmed exception: `CatalogServingDefaultSource.standardServingIfBoundToSnapshot` (a `fun interface` default method) never receives Kover line attribution no matter what calls it. This was verified, not assumed: `CatalogServingDefaultSourceTest` already exercises both the digest-match and digest-mismatch branches, and forcing each branch's condition to a hardcoded constant makes that existing test fail for the expected reason — so the gap is a real, cross-checked Kover instrumentation artifact on this Kotlin construct, not a missing test. 99.99% is the tightest line floor this one confirmed artifact allows without leaving room for a real regression to hide under it.
  The Kover **HTML** report's own line numbers do not reliably match real source line numbers for this module (confirmed by cross-checking against grepped source text — the mismatch was never fully root-caused, only worked around). The Kover **XML** report (`build/reports/kover/reportJvm.xml`) does not have this problem: its per-`<sourcefile>` `<line nr="..." mi="..." ci="..." mb="..." cb="...">` elements use real source line numbers directly. Read the XML, not the HTML, when locating an uncovered line in this module.
- Treat architecture, source-set, scenario, journey, platform-contract, native-gap, and coverage manifests as enforceable inventories. Do not hide required work with task filters, skips, empty modules, stale evidence, or “eligibility.”
- Low-friction interaction is product-wide: defaults are visible, sourced, editable, reversible, and session-scoped; never silently default remote consent, uncertain identity, medical/allergen conclusions, or provider routing.
- Do not edit vendored agent prompts without updating `vendor/claude-code-agents/UPSTREAM_COMMIT` and documenting why.

## Review → fix → verify loop

1. Convert every concrete review finding into a testable acceptance condition. Fix the highest-impact issue first and keep stable module paths unless boundary evidence proves a rename necessary.
2. Write the findings to `_bmad-output/implementation-artifacts/` before fixing any of them. A review whose output lives only in the conversation is lost to compaction, and the work then has to be re-derived from scratch.
3. Validate in layers: compile/configuration, focused unit tests, full relevant module tests, then device/native tests. A compile failure means no device test ran; a launch failure means execution was partial; report those states separately.
4. Re-run the full relevant task after each fix. Do not call a task green from partial output, cached compilation, or a report from a different commit.
5. Watch a new test fail before you make it pass, and make sure it fails for the stated reason. A test written after the fix proves the code compiles, not that the defect existed.
6. If adversarial/edge review agents are unavailable, record that honestly; never present generated prompts or a static scan as completed subagent review. When a review verifies only its top findings, report how many went unverified — a capped review is not a clean bill of health.
7. Where reviewers disagree about a finding, treat it as a design question and resolve it from the code's actual behaviour, not from the vote. Two of this loop's real defects were split-verdict findings; one was refuted by majority and still real.

### Reading build output

Gradle reports are the most common source of confident wrong conclusions here. Every one of these has produced a false green in this repo:

- Run one Gradle build at a time per worktree, your own included. Two overlapping builds fail with `e: Daemon compilation failed` and no `e: file://…` line, which reads like a code defect and sends you hunting through a diff that is fine. A compiler error names a file; this one names nothing. Wait for the previous build before starting the next.
- Wait on the specific thing you started, never on a machine-wide condition. A loop waiting for "no Gradle process anywhere" matches **Android Studio's** idle daemon, which the developer keeps open all day, so the wait never ends: the build never starts and the task sits in `sleep` looking exactly like a slow build. Wait for that run's own sentinel in its own output, and stop any waiter whose result you have already read. If you must check for a running build, match only processes under this worktree.
- Capture `${PIPESTATUS[0]}`, never the exit of a pipeline ending in `tail`/`grep`. A failed build behind `| tail` reports success, including in task notifications.
- Never filter build output so narrowly that a failure yields no matched lines. `exit=1` with nothing printed tells you nothing; re-run unfiltered before diagnosing.
- A summary line printed near a failure may predate it. After a compile failure no tests ran, so any test count on screen belongs to an earlier run.
- Check report freshness, not just contents. `qualityFormat` does not regenerate the check reports, so `*SourceSet.xml` can be hours old while looking current; compare file mtimes against the run you just did.
- Read test counts per module, never aggregated. A stale module contributes its old passing numbers indistinguishably from a fresh one, and a module you forgot to name in the task list looks identical to one that passed.
- `UP-TO-DATE` is a legitimate pass only when that module's inputs genuinely did not change. When in doubt use `--rerun-tasks`.
- Verify counts from the XML under `build/test-results/`, not from `BUILD SUCCESSFUL`.

## Android device evidence

- Use the project `android-cli` skill and `adb devices -l`. An emulator is not physical-device evidence; an emulator/simulator is never Bluetooth proof.
- For the complete Android instrumentation suite, run from the repository root:

  `./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest --no-configuration-cache --console=plain`

- Disconnect duplicate ADB endpoints before evidence runs. With multiple devices, pass `-Pandroid.testInstrumentationRunnerArguments.deviceSerial=<serial>` and use strict dependency verification when the evidence command requires it. Confirm both `productionDebug` and `uiAcceptanceDebug` ran. Verify the XML reports under `mobile/apps/androidApp/build/outputs/androidTest-results/connected/` show exact `tests`, `failures`, `errors`, and `skipped` counts.
- Treat ABI/API, lifecycle reflection, permission, camera, UI semantics, and accessibility failures as product failures to fix—not as device noise. When Compose sets `testTagsAsResourceId`, UI Automator must query resource IDs (`By.res`), not descriptions (`By.desc`).

## Apple targets

- "Apple evidence is optional" means device and simulator execution. It does not mean Apple code may go unbuilt: `iosMain` compiles on this toolchain and a change there should be compiled before it is called done.
- `KmpAppleTargetsPlugin` registers no iOS target on an Intel host unless `-Psupernutrition.enableIosDeviceTarget=true` is passed, and `iosSimulatorArm64` is unavailable there entirely. A default build on such a host compiles **no** iOS code, so a green build says nothing whatever about `iosMain`. Check `uname -m` before trusting a build as Apple coverage.
- Compile an `iosMain` change with `./mobile/gradlew -p mobile :<module>:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true`. Kotlin/Native cross-compiles it from Intel; the first run downloads the toolchain and is slow.
- Detekt reads `iosMain` sources whether or not a target is registered, so Apple-side lint findings appear on hosts that cannot build the code they describe. Fix them; do not assume an unbuildable target is an unreviewed one.
- Moving a declaration out of a class drops the class-level `@OptIn`. A cinterop call that compiled inside an annotated class fails at file level, and nothing in the diff shows it — only the compiler does.
## Apple/iOS device evidence

- The configured Xcode Apple Account is `ancientslaviccalendar@gmail.com`,
  exposing the `Arturs Jaskevics` team with Team ID `Z4JKB6L22Z`. The
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

## Owner decisions

- A decision that binds a published contract, a privacy posture, or a
  correctness guarantee is the owner's, not the implementer's. Record it in
  `.evidence/OWNER-DECISIONS.md` **before** implementing it, as a `## D-NN`
  section carrying `Decided on`, `Decided by` and `Channel`.
- `scripts/verify-owner-decisions.sh` refuses an entry missing any of the three,
  or one whose date does not parse or has not happened yet. It cannot force an
  entry to be written on time — that is this rule, not the script. `--strict`
  additionally refuses `PENDING` and is the check to run before treating a
  decision as settled authority.
- Two decisions were implemented on 2026-08-20 with no entry at all. The
  authority was real and given in conversation, and the repository had no way to
  show it; read from the outside, that is an implementer settling a privacy
  question and approving their own answer (PR #44 review, EV-1). A chat
  transcript is not the durable record.

## Completion handoff

- Run `git diff --check`; run `graphify update .` after code changes. Generated `build/` output is never staged; expected dirty graph files are not a reason to skip graphify.
- Report exact branch, commit, worktree state, commands, device/host, test counts, reports, and limitations. Link real files with absolute paths when useful.
- Commit only the intended slice. Leave unrelated WIP untouched. Do not claim completion until the evidence matches the full requested scope.
