---
title: Final Adversarial Monorepo Seam Review
status: complete
reviewed: 2026-07-26
scope: closure of the three Critical and eight High findings in review-monorepo-seams.md, regression-checked through the complete first-feature handoff
---

# Final Adversarial Monorepo Seam Review — NutriArch

## Verdict

**PASS — no Critical or High findings remain in the eleven-item monorepo seam
checklist.**

The current PRD, addendum, architecture spine, solution design, epics, repository
guide, and implementation-agent guide now state compatible fail-closed rules for
all three product roots. The corrected rules are normative rather than examples:
they name the required inventories, ownership roles, released-version evidence,
six-job execution points, forbidden dependency classes, and failure conditions.

The post-AD-15 regression check does not change this verdict. AD-15 keeps its
four scaffold infrastructure exceptions closed and path-exact, assigns each to
a separate invocation and owner, and forbids each from owning product source.
The implementation guide then sequences disjoint committed handoffs and reserves
the final invocation to `mobile/product-lifecycle.toml` only. That candidate
`active` state must run the real read-only active paths of all four mobile jobs
and the same-SHA six-job aggregate before the protected branch can leave
`scaffold-pending`. The sequence therefore does not create a new cross-product,
root-tooling, ownership, inventory, or CI bypass.

The subsequent AD-34 profile change also preserves the verdict. The new
`mobile-ui-acceptance-policy` slice is restricted to `mobile/.maestro/**` and
cannot edit product source, quality catalogs, lifecycle state, root CI/policy,
contracts, web, or backend. The independently owned `mobile/config/**` profile
is monotonic and downgrade is forbidden. CI rejects product source while
`bootstrap` remains selected, rejects profile/task or catalog/flow mismatch, and
runs exactly the selected Android/iOS evidence in the four always-present mobile
jobs. Those jobs remain direct same-SHA inputs to `ci-required`, so the profile
cannot weaken any root boundary, released-contract, ownership,
executable-inventory, or common product-inventory control.

The final first-feature handoff expansion does not regress a seam. Module
registration reuses the path-exact product-local `mobile-build-policy` slice;
automation IDs stay in `mobile/core/ui` with colocated contract tests; and
sharedKit, Android, and iOS composition each have a separate host invocation.
The feature cannot register itself, edit IDs or policy, create Maestro flows, or
wire an app host, and no host invocation can edit another host or policy slice.
The combined candidate must compile, install, and test the inventoried graph on
both platforms and pass all four real mobile jobs plus `ci-required`. These
handoffs add no root, contract, cross-product, cache, deployment, or shared
dependency authority.

## Closure audit

| Prior finding | Result | Current controlling rule |
| --- | --- | --- |
| 1. Backend-controlled contract publication | **PASS** | AD-36 makes `contracts/mobile-api/` immutable non-executable data authored, approved, versioned, and published only by a contract principal independent of every product and repository-policy owner. Mobile and backend are equal consumers; generated bindings remain product-local and cannot write back. |
| 2. Same-commit compatibility permits released-version skew | **PASS** | AD-36 requires new backend behavior against every supported released mobile contract, new mobile behavior against current and staged backend contracts, and both against previous immutable fixtures. It expressly rejects HEAD-only proof and defines major-version coexistence, server-first activation, minimum-version, rollback, and removal controls. |
| 3. No unavoidable owner for seam CI | **PASS** | AD-27 makes the root boundary/policy and contract-catalog suite the unconditional first step of `quality-linux`; mobile and backend quality own their respective supported-release checks. AD-35 makes `ci-required` directly depend on exactly six successful jobs and unconditionally reconcile root, tool-inventory, contract-catalog, released-matrix, lifecycle, inventory, and evidence fingerprints for the same SHA. “Not applicable,” skipped, empty, stale, or wrong-SHA seam evidence fails. |
| 4. Root `tooling/` can become a fourth implementation root | **PASS** | AD-36 restricts root tooling through a protected allowlist to read-only policy, evidence-schema, contract-data compatibility, and CI orchestration. It forbids runtime libraries, product build plugins, generators, shared fixtures, domain mappings, deploy helpers, and product configuration, and forbids product build-graph dependency on root tooling. |
| 5. Cache namespace is not real isolation | **PASS** | AD-36 defines product-ID, platform, toolchain, lockfile, source, and schema inputs for cache keys; forbids cross-product restore prefixes and writes; separates immutable read-only contract caches; records restored keys and content digests; and rejects foreign, mutable, partial, or unverified content. AD-30 makes cache edges part of the machine-enforced root boundary. |
| 6. Product deploy files can still hide coupled deployment | **PASS** | AD-36 gives every product a separately signed/versioned artifact, provenance, workflow, environment, credentials, promotion decision, and rollback. A deployment may consume only its own artifact and a pinned compatible contract. Root workflows cannot build, publish, promote, or roll back products atomically, share mutable release tags, or require another product artifact. |
| 7. CODEOWNERS labels do not prove independent owners | **PASS** | AD-30 protects product, contract, and CI/policy boundaries under distinct principals. AD-36 requires distinct mobile, web, backend, contract, and repository-policy principals; one approval cannot satisfy multiple roles, relevant edits dismiss approval, and generated bindings/compatibility fixtures require contract-owner approval. AD-38 makes externally enforced ruleset evidence a production gate. |
| 8. Landing can become a mobile-backend runtime client | **PASS** | AD-36 explicitly forbids a `web/landing/` runtime dependency on the mobile backend, provider services, mobile configuration, and mobile API contract, with no related credentials and a rendered production experience that remains functional when the backend is absent. A future interactive client must be a separate product root. |
| 9. Root workspace or dependency hoisting can couple installs | **PASS** | NFR-19 and AD-26 limit central pins to each product boundary and policy-only root tooling. AD-26 forbids any root workspace, lockfile, virtual environment, dependency installation, resolution path, or hoisted tree spanning products; requires frozen product-local installs; and requires proof that parent or other-product dependency resolution is impossible. |
| 10. Root executable logic can escape inventory and coverage | **PASS** | AD-33 assigns every executable outside product roots to the protected policy/contract-tool inventory with executable tests and 100% changed-line and changed-branch coverage. AD-36 rejects any unlisted executable and reconciles the repository executable inventory; contracts themselves remain non-executable data and root tools cannot enter product build/runtime graphs. |
| 11. Web/backend can define away modules with “equivalent” inventories | **PASS** | AD-29 defines one protected root schema for all product inventories and reconciles every deployable module, package, worker/function, migration executable, generated runtime source, test, coverage report, artifact, owner, and quality task. AD-37 prevents empty placeholder compliance for every product: mobile `scaffold-pending` and web/backend `preselection` jobs prove that no production material exists and cannot claim build, test, coverage, app, artifact, or UI evidence; each atomic activation requires a nonempty conforming inventory and nonzero real quality evidence. |

## Remaining Critical or High findings

None.

This is an architecture-document closure result, not a claim that the controls
are already implemented or externally enforced. AD-38 correctly keeps production
implementation and release work blocked until the hosting ruleset,
`trusted-policy-audit`, distinct-owner enforcement, required aggregate, and
bypass evidence are provisioned and verified.
