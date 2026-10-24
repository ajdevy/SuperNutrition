# SuperNutrition

<!-- coverage-report:start -->
## Quality snapshot

[![Coverage dashboard](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml/badge.svg?branch=main)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml) [![JVM unit coverage](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml/badge.svg?branch=main)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml) [![Android UI coverage](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/android-ui-coverage.yml/badge.svg?branch=main)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/android-ui-coverage.yml) [![iOS UI coverage](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ios-ui-coverage.yml/badge.svg?branch=main)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ios-ui-coverage.yml)

[![JVM line](https://img.shields.io/badge/JVM%20line-99.50%25-brightgreen?style=flat-square)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml) [![JVM branch](https://img.shields.io/badge/JVM%20branch-n%2Fa-lightgrey?style=flat-square)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml) [![Source files](https://img.shields.io/badge/Source%20files-1%2C214-5865F2?style=flat-square)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml) [![Code lines](https://img.shields.io/badge/Code%20lines-350%2C790-5865F2?style=flat-square)](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml)

| Metric | Value |
|---|---:|
| JVM host line coverage | 99.50% |
| JVM host branch coverage | n/a |
| Tracked source files | 1,214 |
| Code lines | 350,790 |

### Coverage by denominator

| Module | Kind | Line | Branch |
|---|---|---:|---:|
| `data-nutrition` | jvm | 96.83% | 100.00% |
| `domain-inference` | jvm | 99.84% | 85.22% |
| `core-config` | jvm | 98.26% | 92.55% |
| `domain-photo-analysis` | jvm | 98.03% | 86.00% |
| `inference-local-android` | jvm | 94.12% | n/a |
| `inference-local-ios` | jvm | 88.24% | n/a |
| `inference-local-shared` | jvm | 96.76% | 90.15% |
| `inference-local-android-poc` | native-equivalent | n/a | n/a |
| `core-ui` | jvm | 96.38% | 89.29% |
| `platform-photo-android` | native-equivalent | 0.00% | 0.00% |
| `platform-photo-ios` | native-equivalent | n/a | n/a |
| `feature-photo-analysis-ui` | native-equivalent | 0.00% | 0.11% |
| `feature-photo-analysis-presenter` | jvm | 97.74% | 94.67% |
| `feature-photo-analysis` | native-equivalent | 0.00% | 0.12% |
| `domain-nutrition` | jvm | 99.99% | 100.00% |

### Report links

| Report | Workflow and artifacts |
|---|---|
| Coverage dashboard | [Open workflow runs and artifacts](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml) |
| JVM unit coverage | [Open workflow runs and artifacts](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ci-required.yml) |
| Android UI coverage | [Open workflow runs and artifacts](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/android-ui-coverage.yml) |
| iOS UI coverage | [Open workflow runs and artifacts](https://github.com/flexsent-labs/SuperNutrition/actions/workflows/ios-ui-coverage.yml) |

Run the exact local JVM/report gate with [`scripts/run-local-ci.sh jvm`](scripts/run-local-ci.sh). Use `docker` for the same Linux JVM/report gate in Docker, or `android`, `ios`, or `all` when the native toolchains are available.

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
