# Home profile findings

1. Home currently opens the Android deep-link diagnostic placeholder (profileId=me). Acceptance: Home opens the Figma screen, while the existing external ID contract stays intact.
2. The outer profile branch disposes the remembered Home/tab shell. Acceptance: the new Home route retains shell state and survives Activity recreation; Back returns to Home.
3. No profile identity, legal URLs, or crash-report transport exists. Acceptance: do not hardcode Anastasia, 24 meals, policy prose or an apparently working telemetry consent. Use session name editing and ledger data, with explicit unavailable actions.
4. iOS temporary Home has no avatar. Acceptance: add an entry and host the same Compose Profile on canonical Apple targets.

Integration audit: primary checkout's staged Rewards is byte-identical to committed 7d5971c0, already in origin/main. Historical graph-unmerged refs include squashed replacements and owner-parked PR39; they must remain parked. Only outstanding current work: Home 5e4dd347 and mascot research PR40 (38cd9bf4). PR40 hosted CI failed README snapshot freshness after Gradle coverage passed; this is not infrastructure success.

## Independent review triage

- Accepted: Android rememberSaveable retained the profile name and draft in system saved state. Acceptance: identity stays in in-memory host state; only the route is saved. Fixed with retained ViewModel name and unsaved dialog draft.
- Accepted: an absent HTTPS browser throws IllegalArgumentException from the native URL handler. Acceptance: legal-row activation explains document unavailability without crashing; add a callback-failure regression before fixing.
- Accepted: Intel diagnostics notice still claimed documents were unavailable after document links were wired. Acceptance: the notice refers only to unavailable crash reports and no transmission.
- Accepted, low severity: first-codepoint avatar truncates decomposed accents and flags. Acceptance: initial preserves combining accents and paired regional indicators; keep existing surrogate-pair emoji support.
- Visual inspection: name edit pencil was pinned to the far edge instead of following the name. Acceptance: short name keeps the adjacent edit glyph while long and enlarged names wrap.

Both independent reviewers inspected the entire source snapshot; these findings are not a claim of exhaustive correctness.

## Final gate resolution findings (2026-10-23)

- Stable verification completed all three Android variants (44 tests each) and arm64 Apple compilation, but two JVM tasks failed before execution because their strict dependency locks omit existing transitive runtime dependencies. `apps:sharedKit:jvmTestRuntimeClasspath` needs Compose components-resources and components-resources-desktop 1.11.1; `feature:meal-search-presenter:jvmTestRuntimeClasspath` needs kotlinx-coroutines core/core-jvm/BOM 1.10.2. Acceptance: update only the resolved lock entries with Gradle, retain strict verification, then execute both complete JVM suites and the relevant quality/coverage tasks. This is a dependency resolution failure, not a test assertion or a Java version defect.
