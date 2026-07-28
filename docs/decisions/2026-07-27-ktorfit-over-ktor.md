# Decision: Ktorfit over Ktor for typed remote APIs

Status: accepted as the initial mobile requirement

## Context

Remote inference needs one typed Kotlin Multiplatform API surface while Android
and iOS retain platform-specific Ktor client engines.

## Decision

SuperNutrition remote mobile APIs use Ktorfit 2.7.4 for typed, generated
Kotlin Multiplatform endpoint declarations over Ktor 3.5.1. Ktor remains the
transport, platform-engine, authentication, timeout, cancellation, and pipeline
layer. The initial Ktorfit pin is compatibility-gated by the Kotlin/KSP mobile
tuple and may only change through a dedicated reviewed compatibility change.

## Boundaries

- Ktorfit-generated APIs live only in `mobile/inference/remote` (or its owned
  remote data-source boundary).
- Generated APIs know the first-party mobile gateway contract, never provider
  schemas, provider credentials, or UI state.
- Android uses the OkHttp Ktor engine; iOS uses the Darwin Ktor engine.
- `mobile/domain` remains pure common Kotlin and has no Ktor or Ktorfit
  dependency.

## Verification

- Ktor `MockEngine` and Ktorfit generated-client tests cover upload,
  cancellation, timeout, redaction, compatibility failures, malformed
  responses, rate limiting, and at-most-once transfer effects.
- The scaffold and remote-adapter stories must compile common, Android, and iOS
  targets with the pinned Kotlin/KSP/Ktor/Ktorfit tuple before the pin is treated
  as proven.

## Consequences

Typed endpoint declarations reduce handwritten transport boilerplate while
keeping provider isolation and Ktor engine control explicit. KSP-generated code
adds a build-time compatibility surface that must remain in the version catalog,
dependency locks, and scaffold evidence.

## References

- https://foso.github.io/Ktorfit/
- https://github.com/Foso/Ktorfit
- https://ktor.io/docs/client-engines.html
