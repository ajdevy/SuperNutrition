# CI and Cross-Platform UI Seam Resolution Review — NutriArch

## Verdict

**FAIL — no remaining critical findings, seven remaining high findings.**

The correction substantially resolves the original review. The scenario
denominator is now requirements-derived and closed; Android/iOS logical-flow
fingerprints are constrained; `ci-required` has direct `needs` plus
`always()`/exact-success semantics; fixture apps have a non-distributable
boundary and release-composition smoke; common Compose contracts have named
JVM/Android/iOS tasks; coverage has per-source denominators and cumulative
changed-line/branch rules; and the workflow policy now bans the principal skip,
neutralization, quarantine, and assertion-retry mechanisms.

The Compose v2 API and the named `jvmTest`, `connectedAndroidTest`, and
`iosSimulatorArm64Test` executions are consistent with the current official
Compose Multiplatform 1.11.1 testing guide. The GitHub `needs` plus job-level
`always()` structure is also the documented mechanism for ensuring a downstream
job evaluates failed or skipped dependencies.

The undecomposed `epics.md` template is **not counted as a defect in this
requirements-confirmation artifact**. It remains the expected next-step
implementation-readiness blocker when BMAD advances beyond Step 1.

## Remaining high findings

### 1. The native-gap inventory is exhaustive only by assertion

AD-34 says `native-gaps.toml` is exhaustive and CI fails when a
“platform-sensitive requirement” has neither shared nor native evidence, but no
machine-readable inventory defines the complete set of platform-sensitive
requirements. A team can omit VoiceOver focus restoration, TalkBack announcement
behavior, permission recovery, rotation, or another AD-23/UX contract and the
validator has no missing ID to detect.

**Required correction:** Create a closed
`config/quality/platform-contracts.toml` derived from NFR-9, NFR-13, AD-23, and
the bound UX requirements. Every contract ID must map to common, Maestro, or
native-gap evidence for each applicable platform; CI rejects unmapped
requirements and any source-requirement change without a catalog update.

### 2. Physical/native evidence can be stale or detached from the changed binary

`native-gaps.toml` records cadence, artifact, and expiry, while AD-35 aggregates
only four same-SHA host/simulator jobs. The documents do not say whether a
story/release physical-device result must use the same application digest and
commit, how long cached evidence remains valid, or which source changes
invalidate it. A current UI job can therefore link an old device result.

**Required correction:** Each native-gap result must attest commit SHA,
application/framework digest, fixture/config digest, device/OS identity, task
version, and execution time. The gap entry must define maximum age and
source/module invalidation paths. Same-commit evidence is mandatory when a bound
path changes; cached evidence is allowed only when all digests and invalidation
inputs are unchanged.

### 3. Non-visual oracles are not explicitly route-by-platform or binary-bound

AD-34 requires the same fixture and commit, but one common/mock oracle result
could be linked to both packaged-app runs. That does not prove zero egress,
consent ordering, or cancellation behavior from both the Android and iOS
acceptance executables. “Network deny” also proves blocked egress unless an
observer records attempted and completed transfers.

**Required correction:** Every applicable oracle produces separate Android and
iOS results bound to the installed application digest, process/network
namespace, fixture digest, scenario ID, and commit. The oracle records attempted
connections, request count, byte count, photo digest, consent/effect ID, and
terminal transfer state; local, offline, and declined-consent scenarios require
zero attempts as well as zero transmitted image bytes.

### 4. The robot DSL retry rule contradicts the workflow retry rule

AD-28 permits retry for an “idempotent lookup/assertion,” while AD-35 says product
assertion failures are never retried into success and AR-42 says policy tests
reject assertion retries. An implementation can follow AD-28, turn a transient
assertion failure green, and still claim the retry was reported.

**Required correction:** Replace AD-28's retry clause with:

> Bounded polling is allowed only while waiting for a declared asynchronous
> state before its assertion deadline. Once a product assertion is evaluated
> and fails, it is final for that attempt and is never re-executed into success.
> Lookup failures may be retried only inside the same bounded wait, never after
> the assertion result is emitted.

### 5. Push changed-coverage has no base for new or rewritten branches

AD-33 uses the push event `before` SHA and fails on a missing base. A new branch
push supplies a null/all-zero predecessor; a force push may reference a commit
that is no longer fetchable. The first push to a branch therefore cannot satisfy
the promised complete every-push matrix, and an implementation is likely to add
an ad hoc permissive fallback later.

**Required correction:** Define fail-closed bases for every push shape:
ordinary updates use the event predecessor; a new branch uses the merge base
with the repository default branch (or the empty tree for an explicitly approved
root branch); force-push predecessors are fetched by object ID before checkout
and otherwise use the reviewed default-branch merge base. Branch deletion events
must be explicitly excluded from build coverage or handled by a separate
policy-only workflow.

### 6. Merge-queue protection has no matching workflow trigger

AD-35 says the repository ruleset requires `ci-required` for merge-queue commits,
but the canonical workflow triggers only on `push` and `pull_request`. A GitHub
merge queue requires a `merge_group` trigger for its synthesized commit. As
written, the required status never appears for the commit the ruleset says it
protects.

**Required correction:** Add `merge_group` to the canonical workflow trigger,
run the identical four jobs and aggregate for `github.sha`, and include
`merge_group` in manifest event validation. If merge queue is deliberately not
enabled, remove the merge-queue claim until a dedicated story enables both the
trigger and ruleset atomically.

### 7. The required workflow still validates policy changes using PR-controlled code

The canonical workflow, its policy test, manifest generator, and aggregate
validator are all checked-in files changed by the pull request they judge.
CODEOWNERS and “independent approval” provide review policy, but the architecture
does not require that GitHub enforce that approval or that an immutable/default-
branch control verify the candidate workflow. A PR can weaken all four jobs and
their validator together while preserving the `ci-required` check name.

**Required correction:** The repository ruleset must enforce CODEOWNERS approval
for the canonical workflow, build logic, policy validator, manifest schema, and
required-check configuration. In addition, run a trusted required-workflow
auditor sourced from the protected default branch or an immutable full-SHA
reusable workflow; it compares the candidate workflow/policy files with the
approved schema and must pass before the candidate-generated `ci-required`
status is accepted.

## Resolution status against the original review

| Original seam | Status |
| --- | --- |
| Undecomposed epics | Expected Step 1 state; next-step readiness blocker |
| Closed positive-scenario denominator | Resolved |
| Android/iOS logical-flow equivalence | Resolved |
| `ci-required` direct `needs` and `always()` | Resolved; trusted-control issue remains |
| Fixture/test artifact boundary | Resolved |
| Native-gap inventory | Partially resolved; closed denominator and freshness remain |
| Non-visible privacy/offline claims | Partially resolved; route-by-platform provenance remains |
| Per-source/module coverage denominator | Resolved |
| Changed line/branch coverage | Partially resolved; new/force-push base remains |
| Common Compose execution matrix | Resolved |
| Workflow bypass policy | Substantially resolved; merge-queue and self-validation remain |
| Retry policy | Not resolved because AD-28 and AD-35 conflict |

## Authoritative implementation checks

- [Compose Multiplatform UI testing](https://kotlinlang.org/docs/multiplatform/compose-test.html)
- [GitHub Actions job dependencies and `always()`](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-jobs)
