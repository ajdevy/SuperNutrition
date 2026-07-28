# Temporary Intel iOS acceptance lane

Status: accepted-temporary
Decision date: 2026-07-27
Review/expiry date: 2026-08-27

## Decision

Until an Apple Silicon runner and the exact planned Xcode tuple are available,
the Intel Mac's `iosX64` simulator lane is accepted as the local Apple UI and
bootstrap acceptance lane for development. The user explicitly authorized
continuing without `iosSimulatorArm64`.

## Boundaries

- This is executable simulator evidence, not a physical-device or camera,
  performance, energy, or hardware claim.
- The lane is bound to the named simulator UDID, test result, source/config
  digests, and the current Xcode/OS tuple.
- Xcode invocations must pass `ARCHS=x86_64 ONLY_ACTIVE_ARCH=YES` so the
  simulator host links the matching Intel `SharedKit.framework`; an implicit
  arm64 Xcode build is not evidence for this lane.
- The exception expires on the review date or earlier if the x64 test lane,
  Xcode, or app host changes incompatibly.
- Release and hardware evidence still require the canonical Apple target or a
  separate reviewed physical-device/native-gap evidence decision.
- No `iosSimulatorArm64` source is deleted; it remains the canonical future
  target when an Apple Silicon environment is available.
- This decision also permits the E2 development/profile transition to proceed
  while the lifecycle record remains `scaffold-pending`; `quality_claim` stays
  false and no release or active-state claim is emitted until the remaining
  digest, coverage, trusted-CI, and exact-toolchain gates are closed.
