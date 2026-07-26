# Adversarial Quality-Seam Review — NutriArch Architecture Spine

## Verdict

**Not yet a fail-closed reviewer gate.** AD-25 through AD-28 materially improve
the intended module graph and quality pipeline, but a developer can still satisfy
the prose while omitting native coverage, skipping module tasks, shipping test
support in an internal build, hiding failures behind exclusions/baselines, or
writing assertion-free tests that exercise every line. The gate needs
machine-readable module ownership and negative enforcement tests, not only
conventions.

## Critical

### 1. “Where tooling can measure it” and “eligible tests” are explicit escape hatches

AD-11 permits 100% coverage only “where the tooling can measure it,” AD-27
aggregates only “eligible” iOS tests, and the Stack says Kotlin/Native evidence is
separate. A developer can move branch-heavy policy into `iosMain`, declare the
tests ineligible, and still report 100% Kover coverage over JVM/Android code.
Nothing defines who determines eligibility or what evidence replaces line and
branch coverage.

**Enforceable fix:** Add a versioned module/source-set quality manifest that lists
every production source set and its mandatory compile, test, coverage, lint, and
device-evidence tasks. CI must fail when a production source set is missing,
marked ineligible without an approved waiver, produces no test result, or lacks
coverage/equivalent evidence. Keep deterministic policy in covered `commonMain`;
for unavoidable Kotlin/Native/Swift branches, require native coverage or an
explicit branch-to-test evidence map approved in the story.

### 2. The topology can be satisfied with empty shell modules while real code lives in apps

AD-25 names top-level groups and forbids production dependencies on `testing`,
but it does not enforce source ownership. A developer can create
`domain/inference`, `inference/local-ios`, and `inference/remote` as empty shells,
then put inference, state, or catalog logic into `apps/sharedKit` or
`feature/photo-analysis`. Gradle dependency checks remain clean because the
violation is code placement, not an edge. This defeats module isolation and lets
one team bypass the intended owners.

**Enforceable fix:** Commit a machine-readable module/ownership manifest with
exact project paths, allowed dependency edges, owned package prefixes, forbidden
imports/types, allowed source sets, and CODEOWNERS. Add Gradle graph tests plus
Kotlin/bytecode/package scans that fail when domain, inference, nutrition, or
state-machine classes appear outside their owning modules. Require each adapter
module to compile and test independently, and reject empty placeholder modules
once their capability story starts.

### 3. Coverage can be perfect while cancellation and privacy behavior is wrong

AD-11 and AD-27 measure execution, not assertions or fault behavior. A developer
can execute every reducer branch with tests that assert only “no exception,” mock
the transport so no real cancellation race occurs, and satisfy line/branch
coverage without proving AD-6, AD-8, AD-14, or AD-18. The rule against broad
privacy/cancellation suppressions does not create mandatory safety tests.

**Enforceable fix:** Define a non-waivable safety suite owned by
`testing/platform-support` and run it against production implementations. Include
state-transition table tests, property tests, mutation tests, delayed/late
emissions, cancellation at `PreTransfer`/`Transferring`/`Transferred`, duplicate
effects, process restoration, retry/idempotency, consent revocation, zero-byte
local paths, forbidden-log capture, lease cleanup, and deletion requests. The
gate fails on surviving safety mutations or any missing mandatory scenario,
independent of aggregate coverage.

## High

### 4. iOS evidence can degrade to a Kotlin simulator task

AD-16 says macOS iOS jobs exist and AD-23 calls for real-device accessibility
tests, but no required command proves the Xcode host links the exported KMP
framework, native bridges compile, Swift tests run, or the internal application
boots. A PR can run `iosSimulatorArm64Test`, call it the iOS job, and never invoke
`xcodebuild` or a physical-device lane.

**Enforceable fix:** Make named macOS statuses mandatory: KMP iOS target tests,
XCFramework/sharedKit export and link, `xcodebuild build-for-testing`, Swift/native
bridge unit tests, simulator UI smoke tests, and scheduled or release-blocking
physical-device suites for camera, local inference, lifecycle, accessibility,
memory, and performance. Publish result bundles and device/OS identifiers.
Branch protection must require the relevant status rather than accepting one
generic `qualityCheck`.

### 5. `qualityCheck` can silently omit, disable, or ignore failing tasks

AD-27 lists aggregated tools but does not require a fail-closed task inventory. A
module can set a test task `enabled = false`, use `onlyIf`, configure
`ignoreFailures`, filter out all tests, omit itself from aggregation, or produce
no coverage report. The root task still succeeds and the text is technically
satisfied for the remaining “eligible” tasks.

**Enforceable fix:** Generate the required task matrix from the module manifest,
not task-name discovery. CI must verify that every expected task exists, executes,
has `ignoreFailures=false`, has no unapproved filters, reports a nonzero test
count when production code exists, and produces its declared XML/coverage/lint
artifact. Fail on skipped/`NO-SOURCE` tasks unless the manifest has a reviewed,
expiring exception.

### 6. Baselines and suppressions can legalize defects in the same story

The Suppressions convention permits a baseline, exclusion, or suppression when a
story supplies owner, reason, and removal condition. A developer can therefore
add a large Detekt/lint baseline or `@Suppress` annotations alongside new code,
name the current story as justification, and make the gate green indefinitely.
No expiration, numerical budget, independent approval, or zero-growth rule is
required.

**Enforceable fix:** Store suppressions in a registry with exact rule and
file/symbol, owner, approval, issue, creation date, expiration date, and maximum
count. CI rejects expired entries, count growth, wildcard/package scope, and new
entries in consent, transfer, deletion, logging, authentication, reducer,
nutrition, dependency, and contract code. Require a separate reviewer approval
and a dedicated debt story; do not let the implementation author self-approve a
baseline.

### 7. Coverage exclusions and “generated” labels are ungoverned

AD-27 requires Kover line/branch verification, but the convention plugin can
exclude packages, annotations, classes, generated sources, or whole modules.
Moving code into an excluded namespace or marking handwritten code generated
keeps reported coverage at 100%. Nothing compares the coverage source inventory
to the production source inventory.

**Enforceable fix:** Use an exact, reviewed exclusion allowlist with owners and
expiry; forbid package wildcards and exclusion annotations outside generated
output directories. CI must diff the compiler's production class/source
inventory against coverage inputs and fail when an executable production class
is absent. Run both total and changed-code coverage, and publish the excluded
class list for review.

### 8. Dependency integrity can be reset mechanically instead of reviewed

AD-26 enables Gradle locking and verification metadata, but a developer can
regenerate both after adding a compromised artifact and claim the files were
“reviewed as code.” The rule does not forbid dynamic/SNAPSHOT versions, lenient
verification, unverified plugin/included-build artifacts, direct file/URL
dependencies, or unmanaged Xcode/Swift/service dependencies.

**Enforceable fix:** Require strict dependency verification, locked resolved
graphs, no dynamic/range/SNAPSHOT/file/URL dependencies, and separate
dependency-change PR scope. CI must verify plugin, buildscript, included-build,
runtime, native/Xcode, gateway, model, catalog, and container inputs; produce an
SBOM and provenance record; and reject verification-metadata changes without
named upstream source, license/security review, checksum/signature evidence, and
independent approval.

### 9. Production-to-testing isolation does not cover all shippable variants or binaries

AD-25 says production modules never depend on `testing`, while AD-28 limits test
support to test source sets. A developer can still add `debugImplementation` or
an internal-beta flavor dependency on `testing`, export a test fake through
`sharedKit`, package test resources in the iOS framework, or leak a test service
locator transitively. If the internal beta uses that variant, test code ships
despite a clean release graph.

**Enforceable fix:** Scan every shippable Android variant, XCFramework/iOS app,
and gateway artifact—not only `release`—for `testing` coordinates, packages,
resources, fake class names, test manifests, and test-only DI bindings. Resolve
and inspect all runtime/exported classpaths transitively. The rule must state
that no installable or deployable variant may contain test support; only test
executables may depend on it.

### 10. Separate module ownership is declarative, not merge-enforced

AD-3 requires separate owners for Android local, iOS local, and remote engines,
but there is no required CODEOWNERS mapping, independent approval, or rule
preventing one story/PR from changing both engines and the shared contract.
A developer can alter contract fixtures to match both implementations and
self-certify compatibility.

**Enforceable fix:** Bind every module to an owner in the ownership manifest and
CODEOWNERS; require independent approval for `domain/*`, build logic, quality
configuration, dependency metadata, and shared fixtures. CI must flag PRs that
cross local/remote or Android/iOS ownership boundaries without an explicit
integration story and approvals from each owner. Shared fixture changes require
contract-owner approval and must run against the prior released adapter versions.

### 11. CI policy is not itself a required, non-bypassable control

The spine defines tasks but not branch protection, merge queue requirements,
path-filter behavior, `[skip ci]`, fork/secret conditions, or what happens when a
required runner is unavailable. A PR can avoid macOS or expensive device jobs by
changing only “unrelated” paths or merge while a check is skipped/neutral.

**Enforceable fix:** Record the required status matrix and branch-protection
policy as architecture evidence. Required checks must trigger from dependency and
module impact analysis that fails closed, never path-filter away changes to
shared/build/config code, and treat skipped, neutral, missing, timed-out, or
runner-unavailable statuses as non-mergeable. Protect quality workflow and build
logic changes with dedicated owners.

## Medium

### 12. “Read-only” and “only autocorrect entrypoint” are not verified properties

A custom convention task can mutate source, update baselines/locks, or invoke a
second formatter while still being reached through `qualityCheck`. Conversely,
another task such as a direct Detekt autocorrect or formatter-apply task can
remain callable despite the claim that `qualityFormat` is the only entrypoint.

**Enforceable fix:** Run `qualityCheck` in CI with a clean-tree hash/diff guard and
fail on any tracked-file mutation. Add Gradle TestKit policy tests that inspect
the task graph and configurations, reject autocorrect/apply dependencies from
verification tasks, and disable or hide every mutating formatter/linter task
except `qualityFormat`.

### 13. The enforcement build logic has no negative conformance suite

AD-16 proves the happy-path scaffold, but convention plugins can regress later.
There is no requirement to test malicious sample projects: project repositories,
dynamic dependencies, testing leakage, disabled tests, ignored failures, missing
coverage, bad module edges, broad exclusions, or hidden autocorrect.

**Enforceable fix:** Add Gradle TestKit negative fixtures for every policy
invariant. Each fixture intentionally violates one rule and must make
`qualityCheck` fail with the expected diagnostic. Run these tests whenever build
logic, settings, version catalog, quality configuration, dependency metadata, or
module manifest changes.

### 14. Golden/contract fixtures can be changed to bless an incompatible implementation

AD-17 and AD-28 centralize fixtures, but the same PR can update fixtures and all
adapters together. Passing “unchanged” fixtures then proves only internal
agreement at that commit, not backward compatibility with shipped clients,
gateway versions, or previous catalog/model packages.

**Enforceable fix:** Version and freeze released fixture sets as immutable CI
artifacts. Contract changes must test the new implementation against current and
supported historical fixtures, and test prior released adapters against the new
gateway/schema where compatibility is claimed. Require contract-owner approval
and an explicit compatibility-matrix update.

## Gate Recommendation

Do not declare the architecture quality gate closed until the scaffold proves:

- a machine-readable module/source-set/owner/required-task manifest;
- negative TestKit policy fixtures;
- fail-closed Linux and macOS required-status matrices;
- coverage-source inventory and native evidence rules;
- non-waivable cancellation/privacy mutation and fault tests;
- all-variant production-binary isolation from `testing`; and
- strict, independently reviewed dependency and fixture provenance.
