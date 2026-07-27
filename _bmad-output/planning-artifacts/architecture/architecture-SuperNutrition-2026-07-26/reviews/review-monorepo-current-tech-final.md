# Final Current-Technology Review — Monorepo and CI

**Review date:** 2026-07-26
**Verdict:** **PASS**
**Remaining Critical findings:** 0
**Remaining High findings:** 0

## Scope

This review re-checks the current filesystem versions of:

- `ARCHITECTURE-SPINE.md`
- `solution-design.md`
- `implementation-agent-guide.md`
- `giphysearch-practices-adoption.md`

It specifically retests the two previous High findings, the six-job GitHub
Actions design, independent product quality boundaries, the AD-15 scaffold
invocation sequence and candidate activation, AD-30/NFR-19 consistency, and
the monotonic AD-34 UI-evidence profiles, ownership slices, and mobile
Compose/Maestro paths.

## Previous High findings resolved

### Fail-closed preselection before web/backend toolchain choice

AD-37 now gives every product a protected, versioned lifecycle record. Mobile
begins `scaffold-pending`; `web/landing/` and `backend/mobile-api/` begin
`preselection`. Their always-run jobs prove that no production source, product
dependency installation or lock, runtime, deployment configuration, deployable
build, or undeclared executable exists. They emit same-SHA lifecycle evidence
and are forbidden from claiming build, test, coverage, artifact, app, or UI
evidence.

Each transition to `active` is atomic. Mobile's scaffold story must add its real
build substrate, frozen dependencies and locks, nonempty inventory, host/device
quality entrypoints, nonzero tests and coverage, fake-only apps, and
UI-acceptance executables. Each web/backend activation must add its separately
reviewed pinned runtime/build/lint/test/coverage tuple, product-local
dependencies and locks, nonempty inventory, real read-only quality entrypoint,
nonzero tests and coverage, artifact, and deploy/rollback structure. All six
jobs must pass each transition, and production material fails while any product
remains non-active.

AD-27, AD-29, AD-35, AD-37, the delivery order, and the implementation-agent
guide now agree on this lifecycle. Toolchain selection remains genuinely
deferred; no web or backend framework is implied.

### Current private GitHub Free enforcement limitation

Live checks still report:

```text
repository: flexsent-labs/SuperNutrition
visibility: private
default branch: main
organization plan: free
```

AD-38 now states that private-repository rulesets, an organization-enforced
required workflow, and a private merge queue are not available or implemented
in that state. Checked-in workflows are treated only as bootstrap evidence.
Production implementation and release work are blocked until an infrastructure
story proves the required hosting capabilities or a new reviewed decision
defines a concretely enforceable fallback.

The required exit evidence is sufficient and falsifiable: active ruleset,
distinct-owner protection, immutable `trusted-policy-audit` source, required
`ci-required`, a blocked bypass attempt, and a successful `merge_group` run
when private merge queue remains enabled. This is consistent with GitHub's
current documented availability: private rulesets require a paid plan, while a
private organization merge queue requires Enterprise Cloud.

## Implementability confirmation

- The fixed six jobs remain implementable. GitHub Actions permits
  `needs` to contain an array of job IDs and documents
  `if: ${{ always() }}` for an aggregate that must execute after unsuccessful
  dependencies. The `needs` context exposes each direct dependency's result.
- `ci-required` directly names exactly the four mobile jobs plus
  `quality-web-landing` and `quality-backend`, requires exact success, and
  rejects absent, stale, skipped, empty, or wrong-SHA evidence. A non-active
  lifecycle changes the expected evidence schema, not whether any named job
  runs. In particular, all four mobile jobs report only `scaffold-pending`
  lifecycle evidence before activation; they neither provision devices nor
  claim app, Compose, or Maestro execution.
- AD-15 and the implementation guide split the mobile scaffold into disjoint,
  sequentially committed build-policy, quality-policy,
  bootstrap-Maestro-policy, probe, Compose-support, sharedKit, Android-host,
  iOS-host, and
  lifecycle-activation invocations. The last invocation changes only
  `mobile/product-lifecycle.toml`. That candidate commit is read as `active` by
  CI, so all four mobile jobs execute their real build, test, device, and UI
  paths on the candidate SHA. A failed job prevents the all-six aggregate and
  merge, leaving the protected branch `scaffold-pending`; successful candidate
  evidence cannot be confused with evidence from an earlier lifecycle.
- AD-34's active UI evidence is monotonic. `bootstrap` runs only a
  non-production scaffold robot and packaged launch/DI smoke under
  `mobile/.maestro/bootstrap/`; it makes no UJ or release claim. The first
  photo-analysis UI story irreversibly proposes `photo-analysis` and its full
  UJ/SM-9 catalog. Separate AD-15 invocations own the feature, build-policy
  registration, core automation IDs, quality profile/catalogs,
  `mobile/.maestro/photo-analysis/`, sharedKit, Android host, and iOS host.
  The combined candidate must compile, install, and execute the profile-selected
  Compose and Maestro evidence on both platforms; it is mergeable only when the
  four real mobile jobs and `ci-required` pass. On failure, the protected branch
  remains `bootstrap`.
- AD-30, AD-36, and NFR-19 consistently keep dependency installation, locks,
  caches, build graphs, artifacts, deployment, credentials, and rollback
  product-local. Root pins and executable tooling are restricted to tested
  repository-policy, contract-data, evidence, and CI orchestration functions
  and cannot become a shared product framework or dependency graph.
- Mobile paths remain valid after relocation. Common Compose contracts live at
  `mobile/feature/photo-analysis/src/commonTest`; shared test support lives at
  `mobile/testing/compose-support`; catalog and platform evidence live under
  `mobile/config/quality/`; and Maestro content lives under
  `mobile/.maestro/`. The intended executions remain `jvmTest`,
  `connectedAndroidTest`, and `iosSimulatorArm64Test`. Bare relative entries in
  the structural seed are nested beneath the displayed `mobile/` root rather
  than stale repository-root paths.
- The architecture-spine deterministic linter completed with zero findings.

## Current primary sources

- [GitHub Actions job dependencies and `always()`](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-jobs)
- [GitHub Actions `needs` context](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#needs-context)
- [GitHub ruleset availability](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets)
- [GitHub required-workflow rules](https://docs.github.com/en/enterprise-cloud@latest/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets#require-workflows-to-pass-before-merging)
- [GitHub private-repository merge-queue availability](https://docs.github.com/en/enterprise-cloud@latest/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/managing-a-merge-queue)
