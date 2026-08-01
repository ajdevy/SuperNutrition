# Project Context Digest

> Generated 2026-08-01 from the last local Claude Code sessions (E4.3 gateway work).
> This file is the handoff memory for new sessions (Telegram bot / server CLI).
> If the file is missing, ask Claude: "continue the E4.3 gateway state from git and PRs".

## How we work (continuity protocol — read first, obey strictly)

This project is worked on by Claude Code as an **uninterrupted continuation of the local sessions that built Stages 1–5**. New sessions must behave as if they were the same working agent. Rules:

1. **Language: English.** Reply in English, commit messages in English, docs in English.
2. **Read before acting, in this order:** `backend/AGENTS.md` → this digest (`backend/docs/context.md`) → the stage spec file named in the task. If the task names a stage (e.g. "Stage 6 USDA"), the spec lives at `backend/NN-<stage-name>.md` — open it fully before writing code.
3. **Do not improvise the task.** The stage specs (backend/01…08) are the authoritative briefs. If the spec is missing or the task is ambiguous, ASK instead of guessing or stopping early.
4. **"Done" means verified.** Never report "Task completed" after only reading or exploring. A task is done when: code written, typecheck/lint/format pass, tests green, coverage ≥85% lines/branches (per AGENTS.md), contract checks pass. Then report: what changed, test results, files touched.
5. **No partial silent work.** If you cannot finish in one run, leave a clear status file (e.g. `backend/docs/wip-<stage>.md`) with what is done and what remains — never just stop.
6. **Update this digest at the END of every task** (not only when interrupted). Rewrite the "Next stage", "Current focus", and git-state blocks to reflect what was done, gate results, and the exact next step — so any new session from any entry point (bot, code-server, kitty) picks up the latest state. A task is not fully done until this file says so.
7. **Git discipline:** work on the current branch (`feat/e43-gateway-impl` for E4.3 stack) or a new branch per stage; commit per coherent unit with `feat(backend): ...` style; DO NOT commit the dirty benchmark files or unrelated docs; push rarely and deliberately (paid GitHub Actions quota — see AGENTS.md).
8. **Deep context:** full old session transcripts are at `~/.claude/projects/-Users-jj-Work-SuperNutrition/` (imported from the local machine). If this digest lacks a detail, search those transcripts for how the local agent decided it.

## Next step (current objective) — NOT Stage 7

- Stages 1–6 are done (architecture, skeleton, contracts, E4.3 gateway, meals product API, USDA catalog).
- **NEXT: the photo-flow curl test — end to end, by hand, against the running API:** photo → inference → candidate confirm → USDA facts → meal save → history. This is the next task, decided 2026-08-01.
- **Do NOT start Stage 7** (`backend/07-goals-progress.md`) until the photo-flow curl test is done. Stage 8 (`backend/08-achievements-coach-journey.md`) comes after Stage 7.
- The curl run is also where open decision 5 gets settled: it is the first traffic against the live USDA API, so it is the moment to check whether `/foods/search` actually parses (see "Stage 6 risks" below).

## Current focus: Stage 6 USDA catalog — DONE (2026-08-01)

Implementation landed in `09d2d05`, the test surface in `ec48c3d`, and the catalog-auth fix in the commit this digest ships with. All committed, **not pushed**.

- `USDAProvider` (real HTTP over an injectable fetch, retry on 5xx/timeout only, circuit breaker, 7-day in-memory lookup cache, `#apiKey` isolation), `LookupFoodHandler`/`SearchFoodHandler`, `GET /api/v1/catalog/search`, wiring in `main.ts` with dataset version `SR Legacy 2021-10`.
- `LookupFoodHandler` is wired but intentionally NOT routed — `GET /api/v1/catalog/items/:foodId` stays 501 until a later stage. Do not "fix" that.
- `NutritionCalculator` stays unimplemented on purpose (docs/08 §8.4 keeps the server-side calculation path disabled until the golden-fixtures contract release).
- `GET /api/v1/catalog/search` authenticates like every other Surface B route (fixed 2026-08-01 — see "Stage 6 risks" 2).
- Tests: **830 passing (was 753 passing + 2 red), 49 files.** 95.70% lines / 93.44% branches overall; `usda.provider.ts` 100% lines / 95.06% branches. `npm run typecheck`, `npm run lint`, `npm run format:check`, coverage thresholds, and `verify-{mobile-api,inference,interaction-policy}-contract`, `verify-root-boundaries`, `verify-product-identity` all pass.
- Two non-test changes came with the test commit: `extractNutrients` now reads `NUTRIENT_MAP` instead of duplicating the nutrient ids inline (eslint failed on the dead map; behaviour identical), and prettier was run over the three Stage 6 files that had never been formatted (`domain/index.ts`, `usda.provider.ts`, `routes/product/index.ts`).

## Git / PR state

- Branch `feat/e43-gateway-impl`. **Six local commits are ahead of origin and unpushed** (oldest first): `bc87798` (Stage 5–8 specs + backend AGENTS.md), `4ac78d4` (meals domain and product API), `09d2d05` (Stage 5/6 implementation, "backup … (auto)"), `ec48c3d` (Stage 6 tests), `e00a2ef` (this digest, Stage 6 completion), `adfbc5b` (catalog auth + these decisions). Push only when the user asks — paid Actions quota. Confirmed 2026-08-01: do not push.
- `origin/feat/e43-gateway-impl` still points at `24d5137`; the local remote ref was last fetched 2026-07-30, so re-fetch before trusting the count.
- Last pushed commit: `24d5137 feat(backend): implement the E4.3 first-party inference gateway` (51 files, +6124/-88).
- **PR #27 OPEN**: `feat/e43-gateway-impl` → `feat/e43-gateway` (stacked on #26).
- PR stack: #27 → #26 (contract-only `feat/e43-gateway`) → #23 (stage-2 skeleton) → #22 (stage-1 architecture) → main.
- `docs/12-api-testing-guide.md` was approved and is included in PR #27.

## What Stage 4 delivered (all verified green)

- Fastify server; four gateway routes; JWT auth + fenced test-mode auth; consent verification.
- Two-pass orchestrator; real OpenRouter adapter; Redis rate limiter; telemetry recorder; kill switch; ordered pipeline.
- **537 tests (up from 278), 94.1% lines / 90.2% branches.** Typecheck, lint, format, coverage, contract consumption, and all 12 repo validators pass.

## Bugs the tests found and fixed

1. Test-mode fence ran before the market check → wrong error for unresolved markets. Fence is now the last gate before transfer.
2. Consent copy-version check ran before posture check → denied markets reported wrong error. Posture first.
3. JWT expiry matched by substring `/exp|expired/i` → matched "unexpected". Now matches jose's `ERR_JWT_EXPIRED`.

## Known divergences from the brief

- **No `@fastify/cors`** by design (docs/09 §9.9; native client only). Test asserts no `Access-Control-Allow-Origin` header.
- **Production ships an empty market policy**: every real market resolves `unresolved` and fails closed until G-1/G-4 supply a reviewed posture. Code-complete, but no real market permits a transfer — that's intended (AD-41 MP-1).
- `analyze` port gained a second param (`InferenceAttemptOptions`, for `AbortSignal` per AD-6 cancellation); `PayloadRef` gained a `PayloadSource` resolver (orchestrator never touches image bytes).

## Stage 6 risks

1. **OPEN — `search` may parse nothing from the real FDC API.** `extractNutrients` reads `foodNutrients[].nutrient.id` / `.amount`, which is the `/food/{fdcId}` detail shape. The public `/foods/search` response uses the abridged shape (`nutrientId` / `value`), so against the live API every candidate could be skipped as malformed and search would always return an empty list. **Decision 2026-08-01: do NOT change the code now.** Validate it against the live USDA API during the photo-flow curl test; only then decide whether `extractNutrients` should accept both shapes. Tracked as open decision 5.
2. **CLOSED — `GET /api/v1/catalog/search` now authenticates.** It was the one Surface B route that did not call `deps.authenticate`, so anonymous callers could spend the USDA quota. Fixed 2026-08-01: the route authenticates first, before the query is parsed, so an anonymous caller gets 401 rather than a validation error that would tell them which parameters were wrong. Covered by three tests in `product-routes.test.ts` (anonymous search refused with no upstream call; anonymous + invalid answers 401 not 400; the route joined the shared "refuses without a credential" table).

## Working tree state (verified 2026-08-01 after the Stage 6 test commit)

- Only two dirty paths, both pre-existing and NOT ours: modified `.claude/settings.json` and untracked `.claude/hooks/gate.log`. Do not sweep them into a backend commit.
- Earlier entries here are now stale and resolved: the 16 modified `backend/benchmark/results/**` files are no longer dirty, and `backend/AGENTS.md`, `backend/docs/`, and the Stage 5–8 spec files are committed (`bc87798`).
- DeepSeek benchmark files were REMOVED from the worktree (10 untracked files, not recoverable via git — intentional, on request).

## Other in-flight work from older sessions (not finished)

### iOS photo picker crash — fixed, one bug remains
- Fixed: `mobile/platform/photo-ios/src/iosMain/kotlin/com/supernutrition/platform/photoios/IosPhotoBridge.kt` — `dismissViewControllerAnimated` (UIKit) was called off-main from `NSItemProvider` completion queue → SIGABRT on "Choose photo". Fix: `dispatch_async(dispatch_get_main_queue())`. Verified in simulator.
- **NOT fixed (open question):** `PHPickerViewController.configuration` is `readonly, copy` — assignments to `filter`/`selectionLimit` mutate a discarded copy. `selectionLimit=1` is default anyway, but the images-only filter never applies → user can select a video → nil → app reports "cancelled". Fix requires building the config before construction and passing it to the initializer. Was left for user decision.

### CI outage (GitHub Actions) — infrastructure, not code
- Since ~11:28 Jul 28 runners stopped being assigned repo-wide (`runner_name: ""`, 0 steps, fails in 2–13s), including `main`. Leading hypothesis: paid Actions minutes exhausted (workflow has `macos-26` jobs at 10× multiplier). Verify: GitHub → Settings → Billing → Actions (org `flexsent-labs`).
- PR #16 (`fix/android-studio-sync-dependency-verification`) — verification-metadata change, locally verified (sync, assembleDebug, qualityCheckJvmAndroid pass), but CI couldn't validate it. Re-run after outage: `gh run rerun --repo flexsent-labs/SuperNutrition 30378541137`.
- 14 failed checks on PRs = 7 jobs × 2 triggers (push + pull_request, no filters) — intended per AGENTS.md, not a config bug.

### Stage 1 architecture docs
- `backend/mobile-api/docs/` — docs 01–08 written in one session (system-architecture-overview, domain-architecture, folder-structure, ddd-module-design, database-design, rest-api-specification, openrouter-integration, usda-integration), interrupted by sleep + auth 403s. Docs 09–13 (incl. `09-market-policy.md`, `12-api-testing-guide.md`) exist — verify full set 01–13 is complete on server.

### Food API deep research (as of Jul 30)
- `food-photo-recognition-research/` — completed research (`results/` — 10 reports, incl. `Recognition_service_and_API_cost_landscape.json`, `Recommended_hybrid_architecture_for_SuperNutrition.json`). Present on server.
- `food-weight-api-research/` — was started (outline.yaml, fields.yaml) but **the folder no longer exists anywhere** (not on Mac, not on server) — outputs lost or removed. If this research is still wanted, it must be re-run.

## Open decisions (ask the user)

1. ~~What to do with the 16 modified benchmark result files~~ — moot as of 2026-08-01: they are no longer dirty in the worktree.
2. Whether to merge PR #27 (or the whole stack) — or keep the gateway contract-only until G-1/G-4 posture reviews.
3. Fix the PHPicker images-only filter bug in IosPhotoBridge.kt (offered, not done).
4. Re-run the `food-weight-api-research/` deep research (folder lost) if the Passio/LogMeal/CalorieMama/LLM-API comparison is still needed — food-photo-recognition-research is intact as a base.
5. **OPEN:** confirm the `/foods/search` nutrient shape against the live FDC API **during the photo-flow curl test**, then decide whether `extractNutrients` should accept both shapes. Deliberately not fixed in advance (see "Stage 6 risks" above).
6. ~~Whether `GET /api/v1/catalog/search` must authenticate~~ — **decided and done 2026-08-01:** it must, and it now does.

## Token economy (set 2026-08-01, shared quota across bot + CLI + code-server)

Thinking output is ~97% of token spend. Policy lives in `backend/AGENTS.md` (§ Token economy); in short:

- **Effort = medium by default** everywhere: `~/.claude/settings.json` (`effortLevel: "medium"`, applies to all CLI sessions) and bot `.env` (`CLAUDE_EFFORT=medium`, applied per request via SDK). Bot command: `/effort low|medium|high|max` (also sets model policy). Use low/medium for routine work, high/max only for hard problems.
- **Models:** bot default `sonnet`; CLI default `opus` (user preference). `/model` switches at runtime for the bot.
- **Never dump full `git diff`** into context — use `git diff --stat`. Batch command output (`tail -N`/`grep`). Prefer targeted reads (`sed -n`/grep) over `cat`-ing whole files. Run single test files first, full gate at the end.
- **Short sessions:** `/new` between unrelated tasks; one giant session re-sends the whole history each turn (quadratic cost).
- Known limitation: bot's `CLAUDE_MAX_TURNS=10` was verified NOT to cap turns (7 user requests → 222 tool calls); mitigate with session hygiene, not the setting.

## Environment notes

- Server workdir: `/opt/claude/SuperNutrition` (bot's APPROVED_DIRECTORY). Git history intact.
- Auth: Claude subscription via `CLAUDE_CODE_OAUTH_TOKEN` (valid 1 year).
- Commands inside sessions: `/repo` to switch projects, `/new` to reset context, `/status` for usage, `/context` to pick AGENTS/context files, `/effort` to set thinking effort.
