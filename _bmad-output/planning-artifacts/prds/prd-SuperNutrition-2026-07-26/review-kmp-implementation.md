# KMP Implementation Review — SuperNutrition PRD

## Verdict

The final PRD is ready for architecture, contract, fake-adapter, UX-state, and
test-harness work. Real local and remote Engine releases remain intentionally
blocked by architecture decisions and G-2 through G-5.

## Required Architecture Closure

- Freeze exact Gradle modules, dependency edges, package roots, allowed paths,
  composition roots, and platform source-set ownership.
- Define Engine request/result/error/capability types, state transitions,
  Confidence semantics, contract versioning, fixtures, and fakes.
- Separate Android and iOS Local Engine stories within the local epic.
- Define shared versus native camera/UI surfaces; Compose UI does not move
  platform image objects into `commonMain`.
- Specify remote transfer start, cancellation, idempotency, timeout, and redacted
  logging behavior.

## Project Overrides for `kotlin-multiplatform-developer`

- The shared Engine API is an intentional interface boundary; the agent's
  concrete-repository preference does not apply.
- Real Gradle source roots include `src/`.
- One invocation owns one module and exact platform scope.
- Cancellation is rethrown and runtime/transport errors map at adapter
  boundaries.
- Photo/inference bodies are never logged.
- Adapter tests and the project's 100% new-code coverage rule are mandatory.
- Pinned version-catalog dependencies win over automatic “latest version”
  upgrades.
