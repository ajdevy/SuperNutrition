# SuperNutrition

<!-- coverage-report:start -->
## Quality snapshot

| Metric | Value |
|---|---:|
| JVM host line coverage | 92.94% |
| JVM host branch coverage | n/a |
| Tracked source files | 165 |
| Code lines | 23,911 |

### Coverage by denominator

| Module | Kind | Line | Branch |
|---|---|---:|---:|
| `domain-inference` | jvm | 100.00% | 79.58% |
| `core-config` | jvm | 97.66% | 92.99% |
| `domain-photo-analysis` | jvm | 82.62% | 72.64% |
| `core-ui` | jvm | 75.76% | 100.00% |
| `platform-photo-android` | native-equivalent | 0.00% | 0.00% |
| `platform-photo-ios` | native-equivalent | n/a | n/a |
| `feature-photo-analysis-ui` | native-equivalent | 84.13% | 40.00% |
| `feature-photo-analysis-presenter` | jvm | 99.05% | 85.11% |
| `feature-photo-analysis` | native-equivalent | 0.00% | 0.76% |
| `domain-nutrition` | jvm | 100.00% | n/a |

Generated from the committed coverage inventory by [`scripts/build_coverage_site.py`](scripts/build_coverage_site.py). `n/a` means that the report has an empty denominator; the quality gate remains fail-closed for that case.
<!-- coverage-report:end -->

## Agent tooling

This repository includes project-local BMAD Method skills and the graphify skill so agent workflows travel with the codebase rather than depending on one developer's home directory.

After a fresh checkout, install the graphify CLI used by the Codex hook:

```bash
./scripts/setup-agent-tools.sh
```

Prerequisites are Node.js 20.12+, Python 3.11+, and `uv`. BMAD is already vendored in `.agents/skills/` and `_bmad/`; start with `bmad-help` when you need help choosing a workflow. Graphify artifacts are generated locally in `graphify-out/` and are intentionally not committed.

To create or refresh the local knowledge graph from an agent, run `/graphify .` and later `/graphify . --update` after documentation or code changes.

## Claude Code agents

This repository vendors a pinned copy of [AlexGladkov/claude-code-agents](https://github.com/AlexGladkov/claude-code-agents) under `vendor/claude-code-agents/`. The project-scoped Claude Code settings enable these plugins for every collaborator:

- `ai-sdlc` — Kotlin, Spring Boot, Android, and Compose Multiplatform development agents.
- `ai-university` — teaching agents for medicine, AI/ML, and robotics.
- `project-coordinator` — project coordination and workflow agents.

After cloning, trust the repository in Claude Code when prompted. The plugins are then available through their normal namespaced skills and agents. The imported upstream revision is recorded in `vendor/claude-code-agents/UPSTREAM_COMMIT`.

To validate the local marketplace manually:

```bash
claude plugin validate ./vendor/claude-code-agents
```
