## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## BMAD

This repository vendors the BMAD Method core and BMM skills under `.agents/skills/` and `_bmad/`.

- Use `bmad-help` when the next BMAD workflow is unclear.
- Use the BMAD planning/architecture skills before implementation work that needs a durable plan.
- Keep team configuration in `_bmad/config.toml` or `_bmad/custom/`; do not commit `_bmad/config.user.toml`.
- Store shared BMAD planning and implementation artifacts under `_bmad-output/` when they are created.

## Agent tooling setup

The skills are committed to this repository. On a fresh checkout, run `./scripts/setup-agent-tools.sh` once to install the local graphify CLI used by the project hook. Node.js 20.12+, Python 3.10+, and `uv` are required by the current BMAD/graphify tooling.

## Claude Code agents

The repository also vendors the pinned `AlexGladkov/claude-code-agents` marketplace under `vendor/claude-code-agents/`. `.claude/settings.json` enables its `ai-sdlc`, `ai-university`, and `project-coordinator` plugins at project scope so collaborators can use the same agents after trusting the checkout in Claude Code. Do not edit the vendored agent prompts without updating `vendor/claude-code-agents/UPSTREAM_COMMIT` and documenting the reason.

## SuperNutrition monorepo and KMP engineering baseline

- Treat `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md` as the source of truth for module dependencies, platform seams, and quality gates.
- Keep product identity exact: installed/launcher/in-app name `SuperNutrition`;
  Apple App Store and Google Play listing title
  `SuperNutrition AI nutritionist`. Store-only descriptors must not leak into
  the installed display name.
- The only product implementation roots are `mobile/`, `web/landing/`, and `backend/mobile-api/`. Each independently owns its dependencies and installation, tests, quality entrypoint, caches, artifact, deployment, credentials, rollback, and distinct CODEOWNERS principal. Cross-product source, workspace/package-manager, generated-code, runtime, cache, build-task, and deployment dependencies are forbidden; integrate only through immutable, versioned, language-neutral releases independently owned under `contracts/`. `web/landing/` has no runtime dependency on `backend/mobile-api/`.
- Inside `mobile/`, use the GiphySearch-derived groups `apps`, `core`, `domain`, `data`, `inference`, `platform`, `feature`, `testing`, and `evaluation`; keep mobile domain pure KMP, Android/iOS local inference separate, and native photo bridges out of app hosts.
- Mobile begins in fail-closed `scaffold-pending`; its reviewed scaffold story atomically adds `mobile/gradle/libs.versions.toml`, `mobile/build-logic`, a nonempty inventory, real host/device/app evidence, and changes the lifecycle to `active`. Web and backend begin in `preselection`; their reviewed activation stories atomically add a central tool/dependency manifest, frozen lock state, nonempty product inventory, and read-only `quality-check`.
- `qualityCheckJvmAndroid` and `qualityCheckApple` must be read-only; `qualityFormat` is the only autocorrect entrypoint and never runs in CI. Do not add broad lint baselines, coverage exclusions, wildcard Compose stability entries, or shippable dependencies on `testing`.
- Treat the architecture/module, source-set, positive-scenario, journey, platform-contract, and native-gap manifests as enforceable inventories once the scaffold creates them; never make required work disappear through task filters, skips, empty modules, stale evidence, optional coverage, or “eligibility.”
- Every compiled production source has exactly one coverage denominator. Every mobile, web-landing, and backend module and each product's deduplicated aggregate maintain at least 85% unit-test line and branch coverage; changed production lines and branch outcomes maintain 100%; Native/hardware equivalents are executable, approved, and expiring.
- Mobile scaffold activation uses only a non-product `bootstrap` robot and packaged launch/DI Maestro smoke. The first photo-analysis product source must atomically and irreversibly switch the UI evidence profile through separate feature, `mobile/config/**`, and `mobile/.maestro/**` invocations. That profile tests shared Compose semantics/state/layout from `commonTest` in `jvmTest`, `connectedAndroidTest`, and `iosSimulatorArm64Test`; the closed UJ-1 through UJ-4/SM-9 catalog maps each entry to one fingerprinted Maestro 2.7.0 flow on Android/iOS plus binary-bound privacy/network and fresh platform/native-gap evidence.
- GitHub Actions runs the four mobile jobs plus `quality-web-landing` and `quality-backend` on every branch push and pull request without path filters; merge-group runs become required only after the hosting-capability gate proves support. `ci-required` always runs, directly needs exactly all six, and unconditionally validates exact success plus same-SHA root-boundary, lifecycle/inventory, released-contract matrix, product, and flow fingerprints. Implementation work may proceed now, but **every gate is advisory**: verified 2026-07-28, the org is on GitHub Free and the repo is private, so rulesets and branch protection both return `403` and nothing blocks a merge. Never claim externally enforced required checks, a blocked bypass, distinct-owner enforcement, or an immutable policy source while that holds; release and AD-37 `active` claims still require the provisioned GitLab governance fallback. Native instrumentation/XCTest and real-device evidence remain mandatory for cataloged camera, permission, OS UI, accessibility, lifecycle, and hardware gaps.
- **Low-friction interaction is a product-wide rule for every agent and story.** For user-interaction-affecting work, optimize the shortest safe happy path: minimize taps, scrolls, typing, and choice points; use only fields allowlisted by `contracts/interaction-policy/v1` and the protected mobile policy. Context defaults come from the immutable configuration snapshot; an approved catalog source may provide one atomic serving unit/amount only after canonical-item confirmation and never mutates the snapshot. No user-correction preference is persisted in the MVP. Show every default inline with its provenance, keep it editable and reversible, and preserve corrections only within the approved session scope. `MarketPolicyResolver` owns market-jurisdiction and processing-region resolution; those values must be shown before remote consent and users do not pick provider routing. Never silently default remote upload/consent or materially uncertain food identity. Medical/allergen conclusions are prohibited. User-interaction-affecting stories record required and bounded auxiliary interaction budgets plus proposal/decision IDs, digests, default/override paths, and forbidden-default tests; non-UI stories attest that they do not change them.

## Shared agent memory and mobile work

- This file is the canonical repository guide. `CLAUDE.md` imports it; do not duplicate its content between agent-specific files.
- Store verified long-lived decisions in `docs/decisions/` and link detailed BLE protocol facts from there rather than growing this startup file.
- Use the project-local `android-cli` skill for Android environment and device work, `$ble-kmp-hil` for BLE or physical-device work, and `$mobile-release` only when release work is explicitly requested.
- Do not treat emulator or simulator results as Bluetooth proof. Never put signing credentials, store keys, pairing secrets, or raw sensitive BLE logs in repository guidance or skills.
