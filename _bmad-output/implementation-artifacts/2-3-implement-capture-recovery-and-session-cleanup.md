# Story 2.3: Implement capture recovery and session cleanup

Status: review

## Story

As a user, I want retake, cancel, offline, and manual recovery so failed photo
analysis never leaves a dead end or stale result.

## Acceptance Criteria

- Poor image, no-food, offline, cancellation, and replacement actions map to
  explicit reducer states and bounded recovery choices.
- Late callbacks cannot reopen a result after cancellation or replacement.
- Scan again clears the prior attempt's photo bytes and returns to Capture.
- Every effect carries attempt/request identity and cleanup is idempotent.

## Dependencies

Requires E2.1/E2.2, the photo-analysis reducer, and shared Android/iOS physical
device lifecycle/accessibility evidence.

## Tasks / Subtasks

- [x] Add reducer states for capture, preparing, ready, analyzing, review, and
  typed recovery.
- [x] Reject stale analysis callbacks after replacement or cancellation.
- [x] Revoke the attempt lease and clear session content idempotently on Scan
  again/cancel/replacement.
- [x] Add platform lifecycle/background restoration tests.
- [x] Run physical Android recovery evidence for permission denial, settings
  recovery, and background/foreground restoration.
- [x] Complete iOS physical lifecycle/recovery evidence; the checked-in
  native-gap records remain pending until trusted same-SHA activation.

## Dev Agent Record

- 2026-07-27: JVM tests pass for stale callback rejection, replacement, and
  Scan again cleanup. Cancellation and replacement do not retain PhotoRef
  session content.
- 2026-07-27: Physical Android instrumentation and iOS x64 XCTest bootstrap
  lanes remain green; native recovery/camera execution is still pending after
  the Android wireless-debugging transport went offline.
- 2026-07-27: The reconnected physical Android endpoint passed the direct
  instrumentation smoke (`OK (1 test)`); native recovery/camera execution
  remains pending until the handset is unlocked for the tap-flow.
- 2026-07-27: After unlock, the physical picker selection and clean-task camera
  open/back flows completed without a crash; the acceptance host remained on
  the capture surface after both returns. Reducer-level recovery and
  background-restoration tests are still separate pending work.
- 2026-07-27: Added process-safe background snapshots containing only opaque
  photo identity/request data, restore rules for live leases/session content,
  expiry recovery, and analysis-request invalidation. Six JVM domain/feature
  tests pass, including restore from background and rejection of the old late
  callback. Physical recovery/native-gap execution remains pending while the
  handset is in Dozing/NotificationShade state.
- 2026-07-27: On physical Honor DNY-NX9/API 36, denying CAMERA produced the
  typed recovery state `Camera permission is required`; `Open settings` opened
  the app settings card, and Home/return restored the same recovery surface.
  This closes the Android recovery/background evidence lane. iOS physical
  lifecycle/recovery and native-gap finalization remain pending.
- 2026-07-27: Added process-safe `Bundle` snapshot encode/decode in the
  production Compose Android activity. Only opaque attempt/request/recovery
  data is persisted; bytes, URIs, native handles, and leases are not. Feature
  and domain tests cover restore, expiry, and stale-callback rejection.
- 2026-07-27: Physical Android instrumentation passed `OK (7 tests)` with the
  snapshot codec and packaged host probe; iOS x64 simulator acceptance passed
  `2 tests, 0 failures`. The lifecycle implementation is locally covered, but
  canonical ARM64 and physical iOS native-gap evidence remain outside this
  Intel host lane.
- 2026-07-27: Closed the capture-failure edge in the reducer: preparation
  failures from `Capture` now enter typed recovery, including explicit
  `CAMERA_UNAVAILABLE` and `NO_FOOD` reasons; presenter and Android host tests
  cover the recoverable status path. Physical Android instrumentation remains
  green at `OK (7 tests)`.
- 2026-07-27: Added explicit cancellation recovery in the Android activity and
  presenter contract (`Analysis cancelled` with Scan again/Import photo), plus
  simulator camera-unavailable recovery coverage. JVM, Android physical, and
  iOS x64 acceptance checks remain green; physical iOS recovery is pending.
- 2026-07-28: Re-ran the packaged physical Android snapshot/recovery suite after
  the presenter split; `7 tests` passed. The shared presenter session now drives
  both the x64 compatibility host and canonical Compose host state observers;
  iOS physical lifecycle/native-gap evidence remains pending.
- 2026-07-28: Added the iOS x64 acceptance background/foreground recovery test;
  the clean Xcode run passed `4 tests, 0 failures`. This proves the executable
  simulator compatibility lifecycle path while keeping physical iOS evidence
  explicitly pending.
- 2026-07-28: Extended the shared presenter tests to cover every typed retry
  recovery (`CAMERA_UNAVAILABLE`, `CAPTURE_FAILED`, `POOR_IMAGE`, `NO_FOOD`,
  `OFFLINE`, and `CANCELLED`); JVM and Intel `iosX64Test` both pass.
- 2026-07-28: Re-ran physical Android recovery/snapshot instrumentation at
  7/7 and the Xcode Intel simulator lifecycle suite at 4/4. The simulator
  background/foreground result is compatibility evidence only; physical iOS
  lifecycle/native-gap evidence remains pending.
- 2026-07-28: Hardened restoration against malformed saved request IDs so a
  corrupted Bundle is rejected without crashing `onCreate`; reducer cleanup
  now reports all revoked leases and same-registration observer disposal is
  independently idempotent. Presenter and physical Android regression tests
  pass.
- 2026-07-28: The final connected Android recovery/snapshot suite passed 9/9;
  the Xcode Intel simulator lifecycle suite passed 4/4. Physical iOS
  lifecycle and native-gap evidence remains explicitly pending.
- 2026-07-28: The connected recovery/snapshot suite passed 9/9 again under
  strict dependency verification and the lock-bound Gradle graph.
- 2026-07-28: The strict connected Android recovery/snapshot and Compose
  semantic suite passed 10/10; the Intel x64 Gradle compatibility lane passed
  presenter/normalization/framework checks and the Xcode simulator lifecycle
  suite remains 4/4. Physical iOS lifecycle/native-gap evidence remains
  explicitly pending.
- 2026-07-28: After removing the duplicate mDNS transport for the same Honor
  device, the strict direct-IP connected recovery/snapshot suite passed 10/10
  again. The device-level Maestro driver issue is isolated to Honor's
  background-freeze policy and does not affect the instrumentation recovery
  evidence.
- 2026-07-28: The checked-in Maestro recovery flow passed 1/1 on the booted
  Intel iOS 26.0 simulator compatibility app. Physical iOS lifecycle/native-gap
  evidence remains explicitly pending; the physical Honor Android Maestro
  driver still hangs during wireless stop/launch, while instrumentation remains
  green.
- 2026-07-28: Closed the preparation-failure cleanup gap: failure now revokes
  the attempt lease, wipes session bytes, and returns recovery without a stale
  PhotoRef; an expired Ready session cannot start a new request. Added coverage
  for every live background snapshot variant, idempotent background/restore,
  cancellation late callbacks, and cleanup byte wiping. Domain JVM tests,
  strict physical Android instrumentation, and the Intel iOS simulator lifecycle
  suite remain green at 10/10 and 4/4 respectively; physical iOS evidence is
  still pending.
- 2026-07-28: Re-ran the full JVM/common matrix (69 tasks successful) and the
  single-device physical Android acceptance lane (10/10). The iOS x64 fixture
  compatibility lane also passed 3/3; lifecycle/native-gap evidence for a
  physical iPhone remains intentionally pending.
- 2026-07-28: Re-ran the Xcode iOS x64 compatibility lifecycle scheme; the
  xcresult summary reports 4 passed, 0 failed, and 0 skipped. Physical iOS
  lifecycle/native-gap evidence remains intentionally pending.
- 2026-07-28: Re-ran the strict direct-IP physical Android recovery/snapshot
  and Compose semantic suite after Gradle Metaspace tuning; 10/10 passed. The
  Intel Xcode lifecycle compatibility result remains 4/4 passed, while
  physical iOS lifecycle and native-gap evidence remains open.
- 2026-07-28: The Android host now observes the shared feature state from one
  lifecycle-bound Compose composition instead of replacing the composition on
  every reducer event. This closes the local presenter/lifecycle integration
  gap; physical iOS lifecycle evidence remains a separate native gap.
- 2026-07-28: Lifecycle and accessibility evidence now use separate per-platform
  native-gap records. The physical iOS lifecycle/accessibility requirements are
  explicit and cannot be satisfied by the Intel x64 compatibility run.
- 2026-07-28: Re-ran the Intel x64 Xcode acceptance scheme with explicit
  `ARCHS=x86_64`; the simulator lifecycle/bridge result reports 4 passed, 0
  failed, and 0 skipped. Physical iOS lifecycle/accessibility evidence remains
  pending and is not inferred from this run.
- 2026-07-28: After adding simulator picker coverage, the complete Intel
  x86_64 Xcode compatibility suite passed 5/5, including background/foreground
  recovery and camera-unavailable recovery. Physical iOS lifecycle evidence
  remains pending by policy.
- 2026-07-28: On the physical iPhone 14 Pro (iOS 26.5.2), the Xcode acceptance
  suite passed 5/5: native camera source/recovery, picker cancellation,
  bootstrap idempotency, capture-surface actions, and background/foreground
  restoration. The iOS 26 system camera dismiss control is now handled by the
  acceptance test without weakening the native-source assertion.
