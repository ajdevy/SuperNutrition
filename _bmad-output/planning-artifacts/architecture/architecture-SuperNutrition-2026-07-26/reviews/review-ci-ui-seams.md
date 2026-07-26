# Adversarial CI and Cross-Platform UI Seam Review — NutriArch

## Verdict

**Not implementation-ready.** The revised PRD and architecture establish the
right layers—common Compose contracts, one Maestro inventory on both packaged
apps, targeted native suites, dual coverage thresholds, and an every-push/PR
aggregate—but several key terms remain self-declared. A team can still implement
one happy path per UJ, hide platform divergence in page objects, classify code or
native behavior out of the gates, or let `ci-required` disappear when a
dependency skips. Most importantly, `epics.md` is still an inventory template,
so none of these controls has an owned implementation story or acceptance
criteria.

## Blocker

### 1. The epics inventory contains no epics, stories, owners, or acceptance criteria

`epics.md` claims to be a complete breakdown, but `stepsCompleted` is empty and
the coverage map, epic list, epic, story, and acceptance-criteria sections are
unexpanded template tokens (`epics.md:2`, `epics.md:234-263`). NFR-17 through
NFR-21 and AR-36 through AR-42 are requirements inventory only. There is
therefore no story that owns the coverage denominator, journey scenario
registry, common Compose harness, Android/iOS Maestro runs, native-gap inventory,
workflow policy tests, or branch-protection evidence. “Story 1” in AD-16 and
“The bootstrap story” in the Giphy companion do not point to an actual story.

**Exact wording to add before any readiness approval:**

> Implementation readiness is BLOCKED until this file replaces every template
> token with implementable epics and stories. Each of NFR-17 through NFR-21 and
> AR-36 through AR-42 MUST map to one or more story acceptance criteria, an
> owning module/configuration path, an owner, exact verification commands, and
> produced evidence. The minimum decomposition MUST include separate stories for
> (1) fail-closed module and coverage manifests, (2) the common Compose robot
> contract harness, (3) the positive-scenario registry and shared Maestro flows,
> (4) native-gap evidence on Android and iOS, and (5) the every-push/PR GitHub
> Actions aggregate and its negative bypass tests. No implementation story may
> depend on an unnamed “bootstrap story.”

## Critical

### 2. “Every positive scenario” has no closed, reviewable denominator

UJ-1 through UJ-4 describe main paths, edge/recovery paths, and resolutions, while
SM-9 separately names local, remote, consent, consent decline, offline, Engine
unavailable, manual recovery, and cancellation. None of the reviewed documents
defines which of those are “positive scenarios.” `journeys.toml` can therefore
contain four convenient happy paths—one per UJ—and truthfully claim that every
*listed* journey ran. The agent guide makes the loophole worse by saying “Every
positive journey ID in `journeys.toml`,” reversing the intended relationship:
the requirements must generate the IDs, not the IDs define the requirements.

**Exact replacement for the scenario-mapping sentence in AD-34 and AR-41:**

> A checked-in, versioned positive-scenario catalog is derived from every UJ
> main path, alternative, edge/recovery path that reaches a valid result or
> privacy-preserving cancellation, and every SM-9 acceptance-matrix dimension.
> Each catalog entry has a stable scenario ID, originating UJ/FR/SM references,
> entry state, fixture tuple, ordered user-visible assertions, and terminal
> outcome. `config/quality/journeys.toml` MUST map every catalog entry to exactly
> one shared Maestro flow and both platform runs. CI MUST fail for an unmapped
> catalog scenario, an orphan journey ID, a duplicate mapping, or a UJ/SM-9
> change that does not update the catalog. The catalog, not the set of existing
> journey IDs, defines completeness.

### 3. “The same flow” can conceal two different programs behind page objects

AD-34 permits platform-specific selector implementations behind a page-object
loader but does not constrain those implementations to selectors. Android and
iOS page objects can contain different actions, assertions, waits, fixture
selection, conditions, or subflows while retaining one top-level YAML filename.
Both jobs then pass without proving behavioral parity.

**Exact wording to append to AD-34:**

> “Same flow” means the same ordered logical action IDs, assertion IDs, fixture
> ID, and terminal-state ID after expansion. Platform adapters MAY resolve only
> application identity, launch plumbing, and locators for the same semantic
> element; they MUST NOT contain control flow, taps or text input, assertions,
> waits, retries, fixture choices, scenario skips, or alternate terminal states.
> CI MUST expand each scenario for Android and iOS, compare the normalized
> logical-step fingerprint, and fail on any difference outside the approved
> locator/application-identity fields.

### 4. `ci-required` is not guaranteed to run when a dependency is skipped

AD-35 says the aggregate consumes four result manifests, but it does not specify
the job dependency semantics. In GitHub Actions, a downstream job normally skips
when a needed job fails or skips. That permits exactly the forbidden state:
`ci-required` never reports failure because it never runs. The text also does not
prevent forged/stale manifests, `continue-on-error`, a job-level `if`, an empty
matrix, or shell failure neutralization.

**Exact replacement for the aggregate clause in AD-35 and AR-42:**

> One required `ci-required` job in the same workflow declares direct
> `needs: [quality-linux, ui-journeys-android, quality-apple,
> ui-journeys-ios]`, uses `if: ${{ always() }}`, and succeeds only when the
> expected job set is exactly those four and every `needs.<job>.result` is
> `success`. It also validates each evidence manifest's schema, event,
> repository, full commit SHA, run ID, job ID, and artifact digest. Missing,
> unparsable, duplicate, stale, user-supplied, or wrong-SHA evidence fails.
> Required jobs and steps MUST NOT use `continue-on-error`, result-neutralizing
> shell constructs, an `if` that can skip required work, an empty/excluding
> matrix, or optional dependencies.

## High

### 5. Deterministic fixture injection conflicts with the no-test-code artifact rule

AD-28 says every installable Android/iOS artifact must contain no fakes,
test-only DI bindings, or `testing` code. AD-34 simultaneously requires packaged
apps with injected deterministic Engine, gateway, country, and catalog fixtures.
No document defines the injection boundary. An implementer must either violate
AD-28 or test a build/composition graph unlike the product while still calling
it the packaged app.

**Exact wording to add to AD-28/AD-34:**

> UI acceptance uses a named, non-distributable `uiAcceptance` application
> variant built from the production feature, domain, data, navigation, and app
> composition code. Its only permitted substitutions are manifest-listed port
> implementations at the existing Engine/gateway/catalog/configuration
> boundaries; no product state, route, policy, or screen may be replaced.
> Fixture bindings are unavailable to release/store variants, and binary scans
> MUST prove that every distributable artifact excludes them. CI MUST also boot
> the release composition graph with external services disabled, so a green
> fixture variant cannot mask broken production wiring.

### 6. Native-only gaps are story-selected rather than exhaustively inventoried

AD-34 says native suites are added “only” when common Compose/Maestro cannot
prove behavior; AD-35 demotes physical/native evidence to an “additional
story/release status.” A story author can simply name no gap. Camera, picker,
permissions, VoiceOver/TalkBack, lifecycle, focus restoration, and real-device
behavior then have no mandatory task even though AD-23 and NFR-9 require them.

**Exact wording to add as a new architecture rule:**

> `config/quality/native-gaps.toml` is the exhaustive, versioned inventory of
> behavior not established by common Compose tests and shared Maestro flows.
> Every entry names the bound UJ/FR/UX/AD requirement, Android and/or iOS gap,
> reason, exact instrumentation/XCTest/XCUIAutomation or physical-device task,
> device/OS matrix, owner, cadence, evidence artifact, and expiry/review date.
> Each positive scenario MUST reference all applicable gap IDs. CI fails when a
> required gap has no task/result, when the task is missing, filtered, skipped,
> neutral, cancelled, timed out, unavailable, or artifact-less, or when a
> platform-sensitive requirement has neither shared evidence nor a native-gap
> entry. Authors may not self-classify a requirement as having no native gap.

### 7. The journey mapping does not bind non-visible privacy and offline claims

Maestro assertions can prove visible state, but not zero image-byte transmission,
no pre-consent upload, no transfer after decline, or truly offline operation.
UJ-1, UJ-2, UJ-4, SM-5, and AD-31 require those facts. Deterministic fixture
responses could make the correct screen appear even while the app leaks bytes or
quietly uses a network.

**Exact wording to append to AD-34:**

> Every journey entry also names its required non-visual oracle evidence. At a
> minimum, local and offline scenarios run under an enforced network deny and
> assert zero image-byte egress; remote scenarios assert zero transfer before
> valid consent and after decline, then exactly the permitted transfer after
> acceptance; cancellation scenarios assert the required transfer-state
> behavior. The shared UI flow is green only when its user-visible assertions
> and all linked conformance/native/network-oracle results pass for that same
> fixture and commit.

### 8. “Covered” and “where measurable” still let code opt out of both thresholds

NFR-17, AGENTS.md, AD-33, and all companions repeat “covered production module”
and “where measurable.” AD-29 improves the source-set inventory, but does not
say that every production source is covered or equivalent-evidence; a manifest
author can label a module uncovered or nondeterministic and supply a weak
branch-to-test map. The merged denominator and cross-module duplicate-class
rules are also absent.

**Exact replacement for AD-33:**

> Every compiled production source/class belongs to exactly one module coverage
> denominator. JVM/Android-host production code MUST meet at least 85% line and
> branch coverage per module and across the deduplicated merged graph. All
> production Kotlin is deterministic by default. A source may use
> equivalent-evidence mode only when `module-matrix.toml` identifies an
> unavoidable Native/Swift/framework or hardware boundary, the exact branches,
> executable tests, owner, independent approval, and expiring review date.
> There is no optional `covered` flag. Missing reports, zero denominators,
> duplicate classes, unmapped sources, or unapproved classification fail the
> gate.

### 9. The 100% changed-code gate has no executable definition

No document defines the comparison base for pushes versus PRs, rename handling,
which executable lines/branches count as changed, how branch coverage is
attributed to a changed condition, or what happens when debug metadata cannot
map a branch. A tool can report 100% changed *lines* while changed branches are
untested, or compare against the immediately previous commit to avoid cumulative
PR changes.

**Exact wording to add after AD-33:**

> Changed-code coverage uses the PR merge base for pull requests and the event's
> before SHA for branch pushes, and evaluates the cumulative executable
> production lines and branch outcomes added or modified by that diff. It tracks
> renames, excludes only exact generated-output paths approved by AD-32, and
> requires both changed-line coverage = 100% and changed-branch coverage = 100%.
> A missing/ambiguous source map, shallow-history comparison, absent base SHA,
> unreported changed branch, or tool result that supplies only line coverage
> fails rather than being treated as unmeasurable.

### 10. Common Compose test execution is not a closed target matrix

AD-34 requires common test code, but AD-35 says Apple runs “supported
non-Android” Compose tests. “Supported” is another eligibility term. The same
robot source could execute only on a JVM/desktop host while Android/iOS merely
compile or omit it, yet documentation would still say the shared contract was
tested once.

**Exact wording to add to AD-29/AD-34:**

> `module-matrix.toml` names the exact source set and task that compiles and
> executes every common Compose robot contract, plus the exact Android and Apple
> tasks that compile that same test source against their product UI. The manifest
> records whether a platform executes the common runner or supplies a named
> packaged-app/native replacement for a renderer-specific gap. “Supported,”
> task discovery, compilation alone, and a JVM-only pass are not evidence of an
> Android or iOS rendering contract. CI reconciles robot test IDs and result
> counts with the manifest and fails on missing or zero-test output.

### 11. Workflow policy forbids only some skip mechanisms

The documents ban path filters, commit-message skips, `pull_request_target`, and
floating action tags, but not job/step `if`, `continue-on-error`, matrix
exclusions, reusable-workflow input defaults, cancelled concurrency runs,
test-level ignore/quarantine, or an unprotected replacement workflow with the
same check name. NFR-21 also omits `cancelled`, although GitHub reports it as a
distinct non-success conclusion.

**Exact wording to add to AD-35:**

> A policy test parses all workflow and reusable-workflow YAML and fails on any
> construct that can omit or neutralize required work, including job/step
> conditions outside the approved `ci-required: always()` expression,
> `continue-on-error`, empty or excluding matrices, optional workflow inputs,
> ignored/quarantined required tests, and success fallbacks. Cancelled,
> action-required, stale, startup-failed, and runner-unavailable conclusions are
> failures. The repository ruleset requires `ci-required` from the canonical
> workflow for every protected branch and merge queue commit; workflow, ruleset,
> and required-check-name changes require independent quality-owner approval.

## Medium

### 12. Retries can turn a flaky or platform-specific failure green

Neither AD-34 nor AD-35 defines retry policy. Maestro, device runners, shell
wrappers, or GitHub jobs can rerun until success. This can mask a first-attempt
platform divergence while preserving a final `success` result and nominally
satisfying every listed assertion.

**Exact wording to append to AD-35:**

> Product assertion failures are never retried into success. A bounded
> infrastructure retry is allowed only for a classified runner/device-startup
> failure before the application test begins; every attempt and classification
> is published, and an assertion failure in any attempt fails `ci-required`.
> Quarantined, ignored, muted, or retry-only passing required tests count as
> missing evidence and cannot satisfy a journey, native gap, coverage gate, or
> aggregate.

## Required story acceptance inventory

Before implementation readiness can pass, the concrete stories should prove at
least these negative cases in addition to the happy path:

1. Removing a UJ/SM-9 scenario mapping fails.
2. Giving Android and iOS different logical flow steps or assertions fails.
3. Omitting either platform journey result fails.
4. Skipping or cancelling one required job still runs and fails `ci-required`.
5. Supplying a stale, wrong-SHA, duplicate, or malformed result manifest fails.
6. Excluding a production module/source or emitting an empty coverage report
   fails.
7. Leaving one changed deterministic branch uncovered fails.
8. Omitting a common Compose contract result or returning zero tests fails.
9. Omitting a required native-gap result fails.
10. Shipping fixture/test-support bindings in a distributable artifact fails.
11. Allowing network egress in local/offline/declined-consent journeys fails.
12. Retrying a product assertion failure to green fails.

## Sources reviewed

- `AGENTS.md`
- `prds/prd-SuperNutrition-2026-07-26/prd.md`
- `prds/prd-SuperNutrition-2026-07-26/addendum.md`
- `architecture-SuperNutrition-2026-07-26/architecture-spine.md`
- `architecture-SuperNutrition-2026-07-26/solution-design.md`
- `architecture-SuperNutrition-2026-07-26/implementation-agent-guide.md`
- `architecture-SuperNutrition-2026-07-26/giphysearch-practices-adoption.md`
- `epics.md`
