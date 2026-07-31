# Coverage and source-line reporting in README

## Decision

Publish a generated quality snapshot in the repository README. The snapshot
contains:

- the weighted JVM-host line and branch coverage already enforced by the
  mobile coverage inventory;
- one Kover coverage row per committed coverage denominator; and
- a tracked-source line counter with an explicit scope and definition.

GitHub Actions rebuilds the report data and fails when the marked README
section is stale. This keeps the committed README values reviewable in pull
requests without requiring GitHub Pages, a separate hosting plan, or a
third-party coverage service.

The dashboard is reporting evidence, not a replacement for the local quality
policy or native/device evidence. Native-equivalent Kover observations remain
separate from the JVM-host headline and are not presented as product-wide
native coverage.

## Research and trade-offs

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

The README workflow is separate from `ci-required.yml`, preserving the current
reduced CI adapter and its policy fingerprint. It runs on pull requests, pushes
to `main`, or an explicit manual run, uses a full-SHA-pinned checkout, and
fails if an inventory report, its HTML companion, or the marked README
snapshot is missing or stale. `web/landing` remains preselection and does not
become the report site.
