# Current-Technology Review — Monorepo and CI

**Review date:** 2026-07-26
**Verdict:** **FAIL**
**Critical findings:** 0
**High findings:** 2

## Scope

This review checks the current monorepo architecture for:

- coherent deferral of the `web/landing/` and `backend/mobile-api/` toolchains
  without choosing a framework;
- implementability of the six product-job GitHub Actions aggregate and
  independent product quality entrypoints; and
- correctness of the Compose and Maestro paths after the mobile product moved
  under `mobile/`.

Reviewed:

- `ARCHITECTURE-SPINE.md`
- `solution-design.md`
- `implementation-agent-guide.md`
- `giphysearch-practices-adoption.md`

## High findings

### H-1 — The delivery order requires passing web/backend quality gates before the deferred toolchains exist

**Evidence**

- AD-27 defines `web/landing/quality-check` and
  `backend/mobile-api/quality-check` as real entrypoints that compile/build,
  lint, run unit tests, enforce coverage, verify dependency integrity, and emit
  evidence.
- AD-29 fails missing, zero-test, `NO-SOURCE`, or artifact-less tasks.
- AD-33 fails missing coverage reports and zero coverage denominators.
- AD-35 always runs both product jobs and permits no affected-only omission.
- Delivery step 1 requires the product quality entrypoints and green six-job
  aggregate; delivery step 3 selects and proves the web and backend toolchain
  tuples.

An implementation cannot satisfy the step-1 build, lint, test, coverage,
dependency, and artifact contract without first selecting enough language,
runtime, build, test, lint, and coverage tooling to perform those operations.
The independent entrypoint abstraction is framework-neutral and coherent after
a tuple is selected, but the present bootstrap sequence is not.

**Required correction**

Keep framework selection deferred, but define a machine-readable,
versioned pre-selection lifecycle state for each unselected product. In that
state its required job must still execute and must:

1. prove that no production source, product dependency manifest/lock, runtime,
   or deployable build has appeared;
2. validate the monorepo boundary, ownership, contract, and workflow-policy
   rules that are applicable before product bootstrap; and
3. emit same-SHA schema-versioned evidence describing the pre-selection state.

The transition out of that state must be atomic: the product story adds the
reviewed pinned tuple, real dependency and lock state, production quality
entrypoint, nonzero tests/build artifacts, and coverage evidence, and all six
jobs pass. Production source must fail CI if it appears while the product is
still declared unselected. This preserves always-run product jobs and does not
invent a web or backend framework or introduce an affected-only skip.

### H-2 — The mandatory trusted-policy and merge-queue enforcement is unavailable on the repository's current GitHub plan

**Evidence**

Live checks on 2026-07-26 report:

```text
origin: https://github.com/flexsent-labs/SuperNutrition.git
repository visibility: private
default branch: main
organization plan: free
```

AD-35 requires a repository or organization ruleset, an externally enforced
`trusted-policy-audit`, and merge-queue commits protected by both that audit and
`ci-required`.

GitHub documents that:

- rulesets for private organization repositories require GitHub Team or
  Enterprise Cloud;
- required-workflow rules are configured at organization or enterprise level;
  and
- merge queues for private organization repositories require GitHub Enterprise
  Cloud.

Therefore, the six jobs can be authored, but the architecture's claimed
tamper-resistant required-workflow and private-repository merge-queue controls
cannot be enabled for the current private repository on GitHub Free.

**Required correction**

Make the hosting capability an explicit infrastructure bootstrap gate. Before
AD-35 is considered implemented, upgrade and verify:

- at least the plan required for private-repository rulesets and the
  organization-level required workflow; and
- GitHub Enterprise Cloud if the private-repository merge queue remains
  mandatory.

The exit evidence must include the active ruleset, the protected
`trusted-policy-audit` source, required `ci-required` status, and a successful
`merge_group` run. If that upgrade is not intended, AD-35 needs an explicit
implementable fallback enforcement design and must not claim merge-queue or
organization-required-workflow protection.

## Verified without Critical or High findings

- GitHub Actions supports a job depending directly on six jobs through
  `needs`; `if: ${{ always() }}` is the documented way to run an aggregate
  after failed or skipped dependencies, and each dependency conclusion is
  available through `needs.<job>.result`. The proposed aggregate is
  implementable.
- Independent product-owned `quality-check` entrypoints are a sound
  framework-neutral boundary. They do not require a shared workspace or a
  cross-product build; only the bootstrap state in H-1 is missing.
- Mobile relocation is internally consistent. Shared Compose contracts are
  under `mobile/feature/photo-analysis/src/commonTest`, support code is under
  `mobile/testing/compose-support`, and the same authored contracts run through
  `jvmTest`, `connectedAndroidTest`, and `iosSimulatorArm64Test`. Maestro flows
  and their catalogs are consistently rooted at `mobile/.maestro/` and
  `mobile/config/quality/`.

## Current primary sources

- [GitHub Actions job dependencies and `always()`](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-jobs)
- [GitHub Actions `needs` context](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts)
- [GitHub `merge_group` event](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#merge_group)
- [GitHub ruleset availability](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets)
- [GitHub required-workflow rules](https://docs.github.com/en/enterprise-cloud@latest/repositories/configuring-branches-and-merges/in-your-repository/managing-rulesets/available-rules-for-rulesets#require-workflows-to-pass-before-merging)
- [GitHub merge-queue availability](https://docs.github.com/en/enterprise-cloud@latest/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/managing-a-merge-queue)
