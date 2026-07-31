# Coverage and source-line dashboard

## Decision

Publish a static quality dashboard from GitHub Actions to GitHub Pages on each
push to `main`. The dashboard contains:

- the weighted JVM-host line and branch coverage already enforced by the
  mobile coverage inventory;
- one Kover HTML report link per committed coverage denominator; and
- a tracked-source line counter with an explicit scope and definition.

The dashboard is reporting evidence, not a replacement for the local quality
policy or native/device evidence. Native-equivalent Kover observations remain
separate from the JVM-host headline and are not presented as product-wide
native coverage.

## Research and trade-offs

GitHub's custom Pages workflow uses a Pages artifact followed by the deploy
action. The deployment job must grant `pages: write` and `id-token: write`, use
the `github-pages` environment, and depend on the artifact-build job:
<https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages>.

Kover 0.9.8 already provides HTML and XML report tasks for Kotlin Multiplatform
JVM and Android-host variants, so no second coverage engine is introduced:
<https://kotlin.github.io/kotlinx-kover/gradle-plugin/>.

`cloc`, `scc`, and `tokei` are viable general-purpose counters. They report
language-aware code/comment/blank totals, but adding an unpinned binary or
third-party action would add supply-chain and version drift to this small
reporting path. The repository therefore uses a standard-library Python
counter over tracked source files. Its `code_lines` value means non-blank
lines that are not classified as full-line comments; the page also publishes
the raw definition and language breakdown. If a language-aware counter becomes
important later, `cloc` is the most conservative replacement candidate:
<https://github.com/AlDanial/cloc>.

## Scope and operation

The Pages workflow is separate from `ci-required.yml`, preserving the current
reduced CI adapter and its policy fingerprint. It runs only on `main` (or an
explicit manual run), uses full-SHA-pinned actions, and fails if an inventory
report or its HTML companion is missing. `web/landing` remains preselection and
does not become the report site.

GitHub repository settings must have Pages enabled with **GitHub Actions** as
the publishing source. The workflow creates/uses the `github-pages` deployment
environment; the resulting URL is exposed in the workflow run summary.
