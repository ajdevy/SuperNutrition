# GiphySearch Reference-Fidelity and KMP Good-Spine Review

## Verdict

**Request changes.** The adoption record is materially faithful to pinned
GiphySearch commit `16bec3a193a2793db29e1f6f10f9a72e2b25656d`, and the proposed
KMP structure is directionally sound. However, AD-25 through AD-28 and the
structural seed still leave three ownership boundaries contradictory and the
allowed dependency graph underspecified. Those gaps can re-couple the local
Android, local iOS, and remote teams even while every named module exists.

Deterministic spine lint passed with zero findings:

```text
{"ok":true,"spine":"ARCHITECTURE-SPINE.md","total_findings":0,"by_severity":{},"findings":[]}
```

## Reference-fidelity result

The pinned source supports the adoption record's factual claims:

- `settings.gradle.kts` defines `app`, `core`, `domain`, `data`, `feature`, and
  `testing` groups and enforces `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.
- `gradle/libs.versions.toml` is the central dependency/plugin version catalog.
- The root build applies one Detekt configuration to subprojects, configures a
  single Compose stability file, and enables Detekt formatting.
- `gradle.properties` enables parallel execution, build cache, VFS watching, and
  configuration cache.
- Production tests remain in their owning feature/data/domain modules, while
  `testing:gifs` and `testing:compose-support` provide reusable fakes, data,
  Compose host, and Koin test support.
- The checked-in stability file contains an exact fully qualified type, not a
  wildcard.
- The pinned tree has neither a checked-in CI workflow nor a coverage gate.
- The reference root build sets `autoCorrect = true` on Detekt tasks; the
  adoption record explicitly and correctly refuses to carry that behavior into
  verification.
- The reference `domain:gifs` module depends on Paging and Koin, so the proposed
  pure-common `domain` rule is accurately described as a KMP strengthening, not
  as behavior inherited from the reference.

The reference is therefore being used honestly as a practices seed rather than
presented as evidence for KMP, iOS, coverage, dependency verification, or
team-isolation controls that it does not contain.

## Critical findings

None.

## High findings

### 1. Routing policy has two owners and can become an inference-contract change

The adoption record says `domain/photo-analysis` owns the reducer, policies, and
ports. The structural seed says `domain/inference` owns “contract v1,
capability, candidates, routing policy,” and the solution design labels the same
module “Shared contract and routing.” At the same time, AD-3 makes
`domain/inference` the shared seam consumed by all three engine teams, while
`domain/photo-analysis` owns attempt coordination.

This is not a harmless label mismatch. Product routing consumes consent,
capability, attempt, country, and fallback policy and changes independently of
the evidence schema implemented by engines. Putting routing in the engine
contract module expands the shared churn surface and can force Android-local,
iOS-local, and remote teams to coordinate on product-policy changes. Splitting
pieces between both modules also invites a dependency cycle.

**Concrete fix:** Make `domain/inference` own only the engine contract,
capability/evidence vocabulary, serialization, and compatibility artifacts.
Make `domain/photo-analysis` own `RoutePolicy`, orchestration, consent/fallback
decisions, and attempt coordination, with a one-way dependency on
`domain/inference`. Update AD-25, the structural comments, the solution-design
ownership table, and the adoption record to state that edge identically.

### 2. Composition roots are also assigned native adapter implementation

AD-25 says `apps` own composition and explicitly prevents app modules from
absorbing adapter responsibilities. The structural seed nevertheless calls
`apps/androidApp` and `apps/iosApp` “native bridges,” while
`apps/sharedKit` is another iOS composition boundary. The adoption record also
says the Xcode host owns native frameworks.

This permits bridge logic to accumulate in the app targets and leaves two
plausible owners for iOS graph/bootstrap initialization. The result is hard-to-
reuse bridge code, weak independent tests, and a risk of duplicate or
order-dependent Koin startup. It is also a KMP adaptation not justified by the
Android-only reference's single app root.

**Concrete fix:** Add explicit Android and iOS platform-adapter modules (for
example `platform/photo-android` and `platform/photo-ios`) that implement the
ports and are independently testable. Keep `apps/androidApp` and
`apps/iosApp` as thin hosts. State that `apps/sharedKit` exports exactly one
Kotlin composition/bootstrap API and that the Swift host invokes it once; name
the sole owner of Koin startup. Add the platform group and its allowed edges to
AD-25 and the structural seed.

### 3. Canonical and engine-specific fixture ownership is contradictory

AD-3 says local and remote modules have separate tests, fixtures, and owners.
AD-17 says `domain/inference` owns and publishes immutable canonical JSON and
in-memory contract fixtures. AD-28 instead says `testing` modules own inference
contract fixtures, and the solution design assigns `testing/*` to test
architecture.

The three statements cannot all be applied literally. If adapter teams edit a
shared testing module to make their implementation pass, they can change the
oracle and block one another. If all fixtures are separate, compatibility can
drift. This weakens the final PRD's requirement that local Android, local iOS,
and remote streams remain independently implementable and testable.

**Concrete fix:** Keep immutable, versioned contract fixtures and the
compatibility matrix owned and published by `domain/inference` under contract-
owner approval. Let `testing/inference` own only the reusable contract-suite
runner, fakes, and builders that consume those artifacts. Keep engine-specific
photos, model outputs, provider responses, and fault fixtures inside each
adapter's test source sets under its own owner. State that shared-fixture
changes run against current and supported released adapters and cannot be made
inside an engine story without contract-owner approval.

### 4. AD-25 cannot yet generate AD-27's forbidden-dependency check

AD-25 names groups and a few prose constraints, but it does not define a
complete allowed-edge matrix. It leaves ambiguous, among other cases,
feature-to-feature edges, `core` dependencies, platform-adapter placement,
evaluation access to implementations, gateway reuse of contract types, and
whether app hosts can contain non-composition code. “Approved core ports” in
AD-3 is also not enumerated. AD-27 promises a forbidden-dependency check, but
there is no single machine-readable graph against which that check can operate.

Creating the named directory tree would therefore satisfy the structural seed
without enforcing its architecture. Empty shell modules plus real
implementation in an app or broad `core` module would pass an edge-only check.

**Concrete fix:** Add a versioned module manifest containing every Gradle,
Xcode, and service module, owner, source sets, owned package prefixes, allowed
direct dependencies, allowed test-only dependencies, and forbidden platform
imports. Generate settings inclusion, dependency-graph tests, CODEOWNERS checks,
and AD-27 task membership from that manifest. At minimum, encode the one-way
graph `apps -> feature/platform/inference/data`, `feature ->
domain/core`, `platform|inference|data -> domain/core`, with explicit
exceptions for evaluation and service contract consumption. Reject undeclared
modules and dependencies.

## Medium findings

### 5. One host-relative `qualityCheck` name can conceal the missing half of the PR gate

The adoption matrix correctly separates Linux and macOS responsibilities, and
the solution design says macOS adds iOS and Xcode checks. AD-27, however, binds
one `qualityCheck` to every pull request and qualifies platform tasks as
“eligible.” A Linux invocation can succeed without proving that the required
macOS job ran, while a single generic status gives reviewers no fail-closed
evidence.

**Concrete fix:** Define named lane contracts such as `qualityCheckLinux` and
`qualityCheckMacos`, plus a required aggregate PR status that succeeds only
after both result manifests are present for the same commit. Define
“eligible” from the module/host manifest, fail on missing, skipped, disabled, or
`NO-SOURCE` required tasks, and reserve host-relative `qualityCheck` for local
pre-push use.

### 6. AD-26's central dependency policy stops at Gradle despite binding Xcode and services

AD-25 binds Gradle and Xcode modules and includes a deployable gateway, but
AD-26 centralizes only Gradle versions, repositories, locks, and verification.
The GiphySearch reference is Android/Gradle-only, so it supplies no precedent
for Swift Package Manager, CocoaPods/Xcode inputs, service/container
dependencies, or model/catalog artifacts.

**Concrete fix:** Narrow AD-26 explicitly to the Gradle graph and add equivalent
owned manifests and lock/verification rules for Xcode/native dependencies,
gateway/container dependencies, and model/catalog packages. The aggregate
dependency-integrity gate should enumerate all of those ecosystems rather than
imply that `libs.versions.toml` governs them.

## PRD and independence assessment

The overall structure remains compatible with the final PRD: local-first
operation, consent-gated remote transfer, deterministic nutrition, separate
Android/iOS local implementations, independent remote work, route-by-platform
evidence, and 100% line/branch coverage for measurable deterministic code are
all represented. The separate `inference/local-android`,
`inference/local-ios`, and `inference/remote` modules are the correct backbone.

The gate should remain **request changes** until Findings 1–4 are resolved.
They are structural ambiguities at the exact seams used for parallel ownership;
leaving them to implementation would weaken the local/remote independence that
the PRD and AD-3 explicitly require. Findings 5–6 may be resolved in the same
architecture update or converted into explicit bootstrap-story acceptance
criteria, provided the spine names the fail-closed outcome.
