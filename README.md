# SuperNutrition

<!-- coverage-report:start -->
## Quality snapshot

[![Coverage dashboard](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/coverage-readme.yml/badge.svg?branch=main)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/coverage-readme.yml) [![JVM unit coverage](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml/badge.svg?branch=main)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml) [![Android UI coverage](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/android-ui-coverage.yml/badge.svg?branch=main)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/android-ui-coverage.yml) [![iOS UI coverage](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ios-ui-coverage.yml/badge.svg?branch=main)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ios-ui-coverage.yml)

[![JVM line](https://img.shields.io/badge/JVM%20line-97.45%25-brightgreen?style=flat-square)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/coverage-readme.yml) [![JVM branch](https://img.shields.io/badge/JVM%20branch-87.88%25-brightgreen?style=flat-square)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/coverage-readme.yml) [![Source files](https://img.shields.io/badge/Source%20files-169-5865F2?style=flat-square)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/coverage-readme.yml) [![Code lines](https://img.shields.io/badge/Code%20lines-24%2C407-5865F2?style=flat-square)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/coverage-readme.yml)

| Metric | Value |
|---|---:|
| JVM host line coverage | 97.45% |
| JVM host branch coverage | 87.88% |
| Tracked source files | 169 |
| Code lines | 24,407 |

### Coverage by denominator

| Module | Kind | Line | Branch |
|---|---|---:|---:|
| `domain-inference` | jvm | 100.00% | 79.58% |
| `core-config` | jvm | 97.66% | 92.99% |
| `domain-photo-analysis` | jvm | 97.38% | 88.68% |
| `core-ui` | jvm | 75.76% | 100.00% |
| `platform-photo-android` | native-equivalent | 0.00% | 0.00% |
| `platform-photo-ios` | native-equivalent | n/a | n/a |
| `feature-photo-analysis-ui` | native-equivalent | 84.13% | 40.00% |
| `feature-photo-analysis-presenter` | jvm | 99.05% | 85.11% |
| `feature-photo-analysis` | native-equivalent | 0.00% | 0.76% |
| `domain-nutrition` | jvm | 92.31% | 100.00% |

### Report links

| Report | Workflow and artifacts |
|---|---|
| Coverage dashboard | [Open workflow runs and artifacts](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/coverage-readme.yml) |
| JVM unit coverage | [Open workflow runs and artifacts](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml) |
| Android UI coverage | [Open workflow runs and artifacts](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/android-ui-coverage.yml) |
| iOS UI coverage | [Open workflow runs and artifacts](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ios-ui-coverage.yml) |

Generated from the committed coverage inventory by [`scripts/build_coverage_site.py`](scripts/build_coverage_site.py). `n/a` means that an optional report is pending or has an empty denominator; the quality gate remains fail-closed for an empty denominator.
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
