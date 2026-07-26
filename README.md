# SuperNutrition

## Agent tooling

This repository includes project-local BMAD Method skills and the graphify skill so agent workflows travel with the codebase rather than depending on one developer's home directory.

After a fresh checkout, install the graphify CLI used by the Codex hook:

```bash
./scripts/setup-agent-tools.sh
```

Prerequisites are Node.js 20.12+, Python 3.10+, and `uv`. BMAD is already vendored in `.agents/skills/` and `_bmad/`; start with `bmad-help` when you need help choosing a workflow. Graphify artifacts are generated locally in `graphify-out/` and are intentionally not committed.

To create or refresh the local knowledge graph from an agent, run `/graphify .` and later `/graphify . --update` after documentation or code changes.
