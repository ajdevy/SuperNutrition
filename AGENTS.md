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
