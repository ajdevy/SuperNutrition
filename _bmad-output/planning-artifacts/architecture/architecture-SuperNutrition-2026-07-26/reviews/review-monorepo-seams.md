# Adversarial Monorepo Seam Review — NutriArch

## Verdict

**FAIL — three critical and eight high findings.**

The documents consistently name `mobile/`, `web/landing/`, and
`backend/mobile-api/`, run six product jobs on every branch push, pull request,
and merge group, and prohibit direct source/workspace edges. Those controls are
not yet sufficient to prevent a nominally compliant team from coupling products
through contract ownership, root tooling, caches, dependency installation,
runtime calls, or deployment. The contract and boundary gates also lack one
unconditionally owned execution point, so the six jobs can all be green while
the cross-product seam is unverified.

The `epics.md` placeholders are expected at BMAD create-epics Step 1 and are not
counted as a defect in this review.

## Critical

### 1. The backend is allowed to own the supposedly independent contract publication

AD-36 says the mobile backend “publishes `contracts/mobile-api/`,” while the
solution design assigns `contracts/mobile-api` to a cross-product contract owner.
The backend can therefore change the schema and fixtures to match its own
implementation, publish them, and force mobile to follow. That is producer-owned
coupling, not a neutral contract boundary.

**Required correction:**

> `contracts/mobile-api/` is authored, versioned, approved, and published only
> by the independent cross-product contract owner. Neither mobile nor backend
> owns or generates the authoritative contract. Mobile and backend are equal
> consumers of the same immutable contract release and compile/test independently
> against its digest. Product builds may generate product-local bindings from
> that release but may not write back to `contracts/` or publish a replacement.

### 2. Same-commit compatibility tests permit lockstep breaking changes

AD-36 requires compatibility tests and all six jobs for a contract change, but
does not say which released mobile/backend versions must remain compatible. A
PR can change the schema, mobile, and backend together; every job passes against
HEAD while deployed old mobile clients fail against the new backend or a newly
released mobile app fails before backend rollout.

**Required correction:**

> Every contract change runs producer and consumer tests against the complete
> supported released-contract matrix: new backend with every supported released
> mobile contract, new mobile with current and staged backend contracts, and both
> products against the previous immutable fixtures. Additive changes prove
> backward/forward behavior; incompatible changes require a new major contract,
> a coexistence window, server deployment before client activation, minimum-
> supported-version policy, rollback, and removal story. Updating both products
> in one commit cannot replace version-skew evidence.

### 3. No required job unconditionally owns root boundary and contract compatibility

AD-36 creates root boundary and compatibility tests, but AD-35 names only four
mobile jobs, web quality, and backend quality. None is explicitly required to
run the root monorepo-boundary suite and full released-contract matrix.
`ci-required` validates contract fingerprints only “where applicable,” allowing
all six manifests to mark the seam not applicable and pass.

**Required correction:**

> Assign root boundary and contract verification to an exact, unavoidable step
> in one of the six direct dependencies of `ci-required` (or fold it into all
> affected product entrypoints with identical result reconciliation). The
> aggregate always requires a root-boundary result and contract-catalog
> fingerprint, not “where applicable.” A contracts, root dependency, workspace,
> cache, tooling, deployment, CODEOWNERS, or product-inventory change requires
> the compatibility/boundary result and fails when it is absent, skipped, empty,
> stale, or wrong-SHA.

## High

### 4. Root `tooling/` is an unrestricted fourth implementation root

The structural seed permits `tooling/monorepo-boundaries`, root shared pins, and
CI tooling. AD-36 rejects product-to-product dependencies but does not reject
product dependencies on root tooling. Teams can move code generators, build
plugins, shared test utilities, API adapters, or business transformations into
`tooling/` and make all products depend on them while claiming that no product
root imports another.

**Required correction:**

> Root `tooling/` is allowlisted to read-only repository-policy validation,
> evidence-schema validation, and CI orchestration. It exposes no runtime
> library, product build plugin, source generator, test fixture, domain mapping,
> deploy helper, or product-specific configuration. Product compile, test,
> package, and deploy graphs may not depend on root tooling. Boundary checks
> reject executable files outside the three product roots unless their exact
> path and policy-only purpose appear in a protected root-tool inventory.

### 5. A cache “namespace” does not establish cache isolation

AD-36 says each product owns a build-cache namespace, but does not constrain
cache keys, restore prefixes, write credentials, output paths, or artifact
validation. Products can use different labels over one fallback cache, restore
another product's outputs, or write into a shared cache key. This couples
builds and can substitute stale or poisoned evidence.

**Required correction:**

> Each product cache key includes product ID, runner/OS/architecture, toolchain
> digest, lockfile digest, product source digest, and cache-schema version.
> Restore keys cannot cross product IDs; product jobs have write access only to
> their namespace; contract artifacts use a separate immutable read-only cache.
> Shared output directories and broad fallback restore prefixes are forbidden.
> CI records restored key and content digest and rejects foreign, mutable,
> partial, or unverified cache content.

### 6. Product-owned deploy files still allow one coupled deployment pipeline

Owning separate `release/deploy configuration` does not forbid a root workflow
from building or deploying all products together, sharing one version/tag,
requiring another product's artifact, or using the same credentials and
environment gate. A backend deploy can therefore be blocked by web/mobile or
silently promote their artifacts as side effects.

**Required correction:**

> Each product produces a separately signed artifact, version, provenance
> statement, deployment workflow, environment, credential set, rollback, and
> promotion decision. A product deployment may consume only its artifact plus a
> pinned compatible contract release; it cannot build, publish, promote,
> rollback, or require an artifact from another product. Root workflows may
> dispatch product deploy workflows but cannot perform multi-product atomic
> deployment or share mutable release tags and credentials.

### 7. CODEOWNERS labels do not guarantee independent ownership

AD-36 requires a CODEOWNERS team per root and all affected owners on integration
changes, but the same user or GitHub team can own mobile, backend, web,
contracts, and CI. One approval then technically satisfies every affected-owner
requirement and defeats the intended organizational boundary.

**Required correction:**

> The ownership manifest names distinct mobile, web, backend, contract, and
> repository-policy principals. Contract and policy owners are independent of
> product implementation owners. The repository ruleset requires one approval
> from each distinct affected principal, prevents one approval from satisfying
> multiple roles, dismisses approvals after relevant changes, and requires
> contract-owner approval for generated bindings and compatibility fixtures.

### 8. The landing site may still become a runtime client of the mobile backend

AD-36 forbids only private provider or backend-administration APIs. The landing
site may call public `backend/mobile-api` endpoints and remain technically
contract-compliant, creating an availability, release, credential, and schema
dependency between two products that are supposed to deploy independently.

**Required correction:**

> `web/landing/` has no runtime dependency on `backend/mobile-api/`, provider
> services, mobile configuration, or mobile API contracts. Its production build
> and rendered public experience remain functional when the mobile backend is
> absent. Any future interactive web client is a separately named product root,
> contract consumer, threat model, CI lane, owner, and deployment—not an
> expansion hidden inside the landing product.

### 9. Root package-manager workspaces and dependency hoisting remain possible

Each product has a central dependency manifest and lock state, and direct
cross-product workspace links are forbidden, but the text does not ban a root
package-manager workspace that installs/hoists web and backend dependencies
together. Both products can then depend on a shared root lockfile or hoisted
package tree and fail to build independently from their directories.

**Required correction:**

> No root package-manager workspace, lockfile, virtual environment, dependency
> installation, module-resolution path, or hoisted package tree may include more
> than one product. Each product performs a frozen install from its own manifest
> and lock into a product-local dependency/cache directory. Product quality jobs
> start from an environment without another product's installed dependencies and
> prove no parent-directory resolution.

### 10. Executable contract/root logic can escape every 85% denominator

AD-33 covers compiled production source inside the three product boundaries.
Executable schema validators, compatibility logic, code generators, CI
adapters, deployment scripts, and boundary tooling under `contracts/`,
`tooling/`, or `.github/` are outside every product denominator. A team can move
meaningful transformations there and reduce product coverage while still
claiming contracts contain no runtime implementation.

**Required correction:**

> Every executable repository file belongs to either one product's
> module/coverage inventory or a protected root policy/contract-tool inventory
> with its own test and changed-line/branch requirements. Contracts remain data;
> executable validation/generation lives in the root tool inventory and cannot
> be imported by product runtime/build graphs. CI reconciles the repository
> executable-source inventory with all denominators and fails on unmapped code.

### 11. Web/backend “equivalent inventories” let each team define away modules

Mobile has an exact `module-matrix.toml`; web and backend need only maintain
“equivalent” inventories after their technology stories. A product can declare
itself one giant module, omit packages/workers/migrations/serverless handlers, or
classify generated runtime code as an artifact. Per-module 85% then collapses
into the product aggregate and no longer prevents weak subareas.

**Required correction:**

> Root policy defines one schema for all product inventories. Each inventory is
> reconciled with the product's compiled/bundled source graph and lists every
> deployable module, package, worker/function, migration executable, generated
> runtime source, tests, coverage report, artifact, owner, and quality task.
> Undeclared or merged-away deployable units, empty modules, zero denominators,
> and source-to-artifact reclassification fail. Toolchain selection may populate
> product-specific task names but cannot weaken the common schema.

## Required resolution order

1. Make `contracts/` independently owned and define immutable released-version
   compatibility/skew policy.
2. Bind boundary and contract verification unconditionally into the six-job
   aggregate.
3. Close root tooling, executable-source, dependency-install, and cache
   inventories.
4. Make ownership and deployment independence enforceable rather than
   directory-level conventions.
5. Standardize web/backend module and coverage inventories before their
   toolchain stories add production source.
