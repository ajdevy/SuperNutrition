# Backend workflow conventions

## Git push policy

GitHub Actions CI on this repo bills minutes (private repo, paid billing enabled).

- Commit freely and often — commits never trigger CI.
- Do NOT push unless the user agrees: only push a finished branch for review/PR, or when explicitly asked.
- One push per feature branch (extra pushes only if review requests changes).
- Run `npm test` and the full local quality entrypoint before any push; local gates are authoritative, CI is only an optional adapter.

## Current state (handoff memory)

- Read `backend/docs/context.md` at the start of every session before doing anything else — it is the authoritative handoff digest (E4.3 gateway state, PR stack, dirty files, open decisions).
- If `backend/docs/context.md` is missing or stale, reconstruct it from `git log`, PRs, and the working tree before starting work.

## Continuity protocol (Telegram bot / new sessions)

New sessions are an **uninterrupted continuation** of the local sessions that built Stages 1–5, not fresh strangers. Full protocol lives in `backend/docs/context.md` §"How we work"; the essentials:

- **Work in English** (replies, commits, docs).
- **Read in order before acting:** `backend/AGENTS.md` → `backend/docs/context.md` → the stage spec file (`backend/NN-<stage>.md`). Never improvise a stage from the stage name alone.
- **"Done" means verified:** code + typecheck/lint/format + green tests + coverage ≥85% + contract checks. Reporting "Task completed" after reading/exploring only is a violation.
- If the spec is missing or the task is ambiguous, ask the user — do not guess and do not stop silently.
- If a run is interrupted, leave `backend/docs/wip-<stage>.md` describing what is done and what remains.
- **Update `backend/docs/context.md` at the END of every task** (not just interruptions): replace the "Next stage" / "Current focus" / git-state blocks with what was done, gate results, and the exact next step, so the next session in ANY entry point (Telegram bot, code-server CLI, kitty CLI) starts with the latest state. A task is not fully done until the digest reflects it.
- Old session transcripts for deep context: `~/.claude/projects/-Users-jj-Work-SuperNutrition/`.

## Mandatory self-verification (non-negotiable)

After EVERY task, before reporting done, do a verification pass. Never report "done" on intent — verify on evidence.

1. **Re-run everything that validates the change**: typecheck, lint, format, tests, coverage, contract checks, repo gates. If the project has a validate script or quality gate, run it. If a step fails, fix it or say it fails — do not hide it.
2. **Review your own diff before finishing**: `git diff --stat` (unstaged) and `git diff --cached --stat` (staged) — look at the stats, not full diffs. Full `git diff` output is token-expensive; read full diffs only when stats show something suspicious. Look for: accidental deletions, files swept in that weren't part of the task, benchmark/dirty files caught in the commit, TODOs left, secrets or absolute local paths committed.
3. **Re-read the original request and compare**: was everything asked for actually delivered? List what was done, what was skipped, and any deviations from the brief (with reasons). Say "I did not do X" out loud rather than leaving it implicit.
4. **Check for misses**: edge cases (empty input, errors, concurrency, timeouts), files that should have been updated alongside (docs, CLAUDE.md, configs, migrations), tests that should have been added for new behavior, cleanup of temp files.
5. **Verify the actual state, not memory**: after any git operation, confirm the branch, the commit exists, the push/PR was created (e.g., `git log --oneline -1`, `gh pr view`). After file edits, re-read the critical lines.
6. **Check the working tree is clean of collateral damage**: `git status --porcelain` — anything dirty that you did not touch must not be blamed on you, and must not be swept into your commit. Report it.
7. **Honesty**: if you found something wrong in your own work, report it explicitly (what was wrong, what you fixed, what you could not fix). Do not present unverified results as verified. Do not claim green for checks you did not run.
8. **Report format** at the end of each task: What was done → How it was verified → What was found in self-review → Anything left open.

## Repository billing: GitHub Actions quota (critical)

CI on this repo consumes paid GitHub Actions minutes, and the org quota has already been exhausted once (runners stopped being assigned repo-wide on 2026-07-28; macos-26 jobs bill at 10× multiplier). Every push or PR triggers the full `ci-required` workflow (7 jobs) — twice per PR (push + pull_request triggers, no filters).

Rules:
- **Push rarely and deliberately.** Batch commits: complete the whole task, run all gates locally, commit once, push once. Do not push intermediate progress "for safety" or to trigger CI.
- Do not open PRs until the branch is complete and locally verified (typecheck, lint, format, tests, coverage, contracts).
- Do not rerun CI without a reason; a red check caused by the quota outage must not be re-fired repeatedly.
- Prefer local verification over remote CI when the quota is low or during outages.
- If CI runs are failing with `runner_name: ""` and zero steps executed, that is the quota outage again — stop pushing, report it, do not retry in a loop.

## Token economy (non-negotiable)

Subscription quota is shared across all sessions (bot + CLI + code-server). Thinking output is ~97% of token spend. Rules:

- **Effort level is the #1 lever.** Default `medium` (set in `~/.claude/settings.json` for CLI sessions and `CLAUDE_EFFORT` for the bot). Use `/effort low` / `/effort medium` for routine or mechanical work; `/effort high`/`max` only for genuinely hard problems. Never run at max by default.
- **Never dump full `git diff` output into context.** Use `git diff --stat` / `git diff --cached --stat`. Full diffs only when stats show something suspicious.
- **Do not re-read files you already hold.** Prefer `sed -n` slices and `grep` to `cat`-ing whole files. `Read` with explicit ranges.
- **Batch shell output.** Use `| tail -N` / `| head -N` / `grep` filters on every command that could print a lot (npm test, vitest, tsc, docker).
- **Do not rerun the full test suite for a one-line fix.** Run the single affected test file first (`npx vitest run <file>`), the full gate only at the end.
- **Model selection:** sonnet for most work; opus only when explicitly asked. Haiku for trivial edits.
- **Session hygiene:** prefer short focused sessions over one giant session. `/new` between unrelated tasks. A 200k-context session re-sends the whole history on every turn — long sessions are quadratic in cost.

## Claude CLI on this VPS (environment facts)

- This repository is worked on by Claude Code running on the VPS `tg-cl-dev.com` (user `igg`, workdir `/opt/claude/SuperNutrition`). Local desktop usage is discontinued — do not assume a local GUI.
- **Auth**: Claude Pro/Max subscription via `CLAUDE_CODE_OAUTH_TOKEN` (canonical copy in `/etc/claude-code/env`, root:igg 640 — it is a systemd EnvironmentFile without `export`, so login shells must source it with `set -a`/`set +a`, done in `~/.profile`). Valid 1 year. Never set `ANTHROPIC_API_KEY` — it would hijack auth and start pay-per-token billing. Never print, commit, or log the token.
- **Interactive sessions must run inside tmux** so they survive SSH drops: `tmux new -s dev` → work → detach (`Ctrl+B D`) → reattach (`tmux attach -t dev`). Check `tmux ls` before creating duplicate sessions.
- **Session history** lives in `~/.claude/projects/` (7 sessions for this repo were imported from the old local machine). Use `claude --resume` to continue old conversations.
- The Telegram bot (`@iggdrassilon_bot`, service `claude-telegram-bot`) drives this same CLI in SDK mode. Its config is `/opt/claude-code-telegram/.env`; after changing it, `sudo systemctl restart claude-telegram-bot`. Its logs: `sudo journalctl -u claude-telegram-bot -f`.
- Every session starts by reading `backend/docs/context.md` (handoff digest) — see "Current state" section above.
- Useful in-session commands: `/usage` (subscription limit windows), `/status` (account/model), `/diff` (review own edits), `/compact` (save context), `/repo` (switch project, bot).
- Voice messages are transcribed locally with whisper.cpp (`/home/igg/whisper.cpp`); no external transcription API is used or needed.
- **Dev infrastructure** (Docker compose, dir `/opt/supernutrition-infra`, managed with `sudo docker compose`): Postgres 16 on `127.0.0.1:5432` (db/user `supernutrition`, password in `/opt/supernutrition-infra/.env`, root-only) and Redis 7 on `127.0.0.1:6379` (no password, loopback-only). Both start on boot (`restart: unless-stopped`), data in `pgdata`/`redisdata` volumes. User guide: `~/CLAUDE-CLI-GUIDE.md` on the VPS.
