# Story 2.1: Implement camera and picker bridges

Status: done

## Story

As a user, I want to capture or import one meal photo with clear permission
recovery, so I can start analysis without a diary or account flow.

## Acceptance Criteria

- Native Android CameraX/Photo Picker and iOS AVFoundation/PhotosUI remain
  bridge-only; common code exchanges an opaque leased `PhotoRef`.
- Denial, restriction, unavailable hardware, and capture failure each expose
  settings, import, or manual-search recovery.
- The ready capture surface has one shutter, one import action, and a bounded
  first-use catalog confirmation budget with visible provenance.
- Physical Android evidence remains the device lane; real iOS/Apple/Xcode
  evidence is good-to-have and does not block local activation. Intel x64
  compatibility output remains explicitly non-canonical.

## Dependencies

Requires E1.1 scaffold activation, E1.5 interaction contract, and the separate
`mobile/core/config` and `mobile/domain/photo-analysis` ownership slices.

## Tasks / Subtasks

- [x] Add common capture surface and recovery contracts.
- [x] Add Android permission, CameraX capture host, and Photo Picker bridge.
- [x] Add iOS bridge contract and simulator acceptance surface IDs.
- [x] Add the temporary Intel `iosX64` acceptance decision with expiry.
- [x] Replace the temporary native acceptance surface with the Compose-owned
  production presenter when the active-state host composition is enabled.
- [x] Complete physical Android camera, picker, and permission-recovery
  evidence for the available device lane.
- [x] Re-run the physical Android camera/capture evidence against the bounded
  in-memory CameraX bridge introduced after the previous device attestation.
- [x] Keep physical iOS camera/picker evidence and the Intel `iosX64` bridge
  lane available as optional native-gap evidence; neither Apple/Xcode path is
  required for local activation.

## Dev Agent Record

### 2026-07-29 physical Android evidence

The clean-source physical acceptance run
`:apps:androidApp:connectedUiAcceptanceDebugAndroidTest` passed 14/14 with no
failures, errors, or skips on `HONOR DNY-NX9 / Android 16`. The resulting
attestation binds the camera/picker evidence to
`38aaf2a072553bcd404005e41c8765a446a0f952`; iOS remains optional and pending.

### 2026-07-28 verification note

The connected iPhone 14 Pro was paired, in Developer Mode, and had a connected
CoreDevice tunnel. A fresh arm64 fixture build succeeded, but the signed
temporary runner was rejected before execution because no valid provisioning
profile was available for the local Apple Development team (`0xe8008015`). A
fresh Xcode physical acceptance attempt was likewise stopped before tests by
`No Account for Team` and missing profiles for the acceptance app/test bundle
IDs. No new physical evidence binding was emitted; the checked-in records
remain pending.

### Debug Log References

- 2026-07-27: JVM tests pass for core config, photo-analysis domain, Android/iOS
  bridge contracts, and feature state.
- 2026-07-27: Android KMP bridge compiles and the acceptance host packages with
  permission, camera-intent, and picker-intent wiring. Device run is delegated
  to the physical serial lane.
- 2026-07-27: iOS x64 simulator is an explicitly temporary executable lane;
  physical camera/hardware claims remain excluded.
- 2026-07-27: Physical Android DNY-NX9/API 36 installed and launched the
  acceptance APK; UI dump found `capture_shutter` and `capture_import`, camera
  and system picker handlers resolved, and instrumentation remained `OK (1
  test)`. A real import tap exposed an OEM Honor freeform/launcher failure;
  the bridge now uses the more stable selected-URI `ACTION_OPEN_DOCUMENT`
  fallback instead of direct `ACTION_PICK_IMAGES`.
- 2026-07-27: iOS x64 simulator acceptance passed with one XCTest and the
  checked-in `capture_shutter`/`capture_import` accessibility IDs.
- 2026-07-27: The iOS bridge dependency graph now compiles through the
  isolated `photoIos` host slice on `iosX64`; `iosSimulatorArm64` is not
  configured on this Intel acceptance lane.
- 2026-07-27: After the Android device reconnected, both APKs installed on the
  physical Honor DNY-NX9/API 36 and the selected ADB endpoint ran
  `BootstrapHostInstrumentationTest` directly with `OK (1 test)`. The camera
  handler resolves to `com.hihonor.camera/.ThirdCamera` and the picker handler
  resolves to DocumentsUI `PickActivity`. Gradle's connected task still sees
  two mDNS endpoints for the same handset and fails before execution with
  `INSTALL_FAILED_DUPLICATE_PACKAGE`; manual button taps remain pending while
  the handset is keyguard-locked.
- 2026-07-27: With the handset unlocked, the physical `Choose photo` action
  opened DocumentsUI through `ACTION_OPEN_DOCUMENT`; a real recent image was
  selected and returned to the acceptance host with both capture actions still
  present and no crash. On a clean acceptance task, `Take meal photo` opened
  Honor Camera (`com.hihonor.camera/.ThirdCamera`) with the `PHOTO` surface and
  `Touch to capture` control; Back returned to the acceptance host. A stale
  OEM task once surfaced Telegram instead, so evidence is recorded against the
  clean `force-stop`/launch setup.
- 2026-07-27: The launcher now resolves to the production
  `PhotoAnalysisComposeActivity`; the native acceptance `BootstrapActivity` was
  removed. Compose owns the capture surface and the thin host owns lifecycle,
  permission, camera, picker, and settings intents. Android APK and
  instrumentation APK both package successfully and the physical device ran
  the bootstrap plus normalization fixture tests successfully.
- 2026-07-27: Final production Compose flow was exercised on physical Honor
  DNY-NX9/API 36: `Choose photo` opened DocumentsUI and returned, `Take meal
  photo` opened `com.hihonor.camera/.ThirdCamera` and returned, CAMERA denial
  rendered `Camera permission is required` with `Open settings`, and the
  settings action opened the app's system settings card. The remaining gap is
  physical iOS camera/picker evidence, not the Android bridge.
- 2026-07-27: Replaced duplicated photo-surface locator strings with the
  protected `core/ui` registry and exported the same IDs through `SharedKit`
  for the iOS acceptance host. Android physical instrumentation remained
  `OK (7 tests)` and the iOS x64 acceptance flow passed `3 tests, 0 failures`.
- 2026-07-27: Moved the shared production presenter and Compose capture surface
  into pure-KMP `mobile/feature/photo-analysis-ui`; the Android app assembles
  from the new graph and the physical Honor DNY-NX9/API 36 instrumentation
  rerun passed all `7 tests`.
- 2026-07-28: Moved the reducer/presenter contract into
  `mobile/feature/photo-analysis-presenter` so the Intel x64 simulator shares
  the production state machine. Canonical Apple source sets now expose
  `PhotoAnalysisViewController`; the x64 host remains an explicit renderer
  compatibility lane. Android instrumentation rerun passed `7 tests`, and the
  iOS x64 acceptance rerun passed `3 tests, 0 failures`.
- 2026-07-28: Bound the scaffold launch/DI marker to the acceptance Compose
  root without replacing the capture IDs. The installed physical Honor UI
  tree exposes `bootstrap_shell_launch_di`, `photo_analysis_status`,
  `capture_shutter`, and `capture_import`; the clean iOS x64 suite remains
  green at `4 tests, 0 failures`.
- 2026-07-28: Hardened the domain bridge boundary: `PhotoRef` now accepts only
  bounded opaque tokens, redacts its string form, and the reducer rejects
  unleased capture/replacement refs before mutating the active session.

- 2026-07-28: Re-ran the connected physical Honor DNY-NX9/API 36 acceptance
  flow after Gradle tuning; all 9 instrumentation tests passed. The clean
  Xcode Intel simulator compatibility suite passed 4/4, while physical iOS
  camera/picker evidence remains intentionally open.
- 2026-07-28: Re-ran the same physical Android bridge suite under strict
  dependency verification after committing the Gradle lock state; 9/9 passed.
- 2026-07-28: Added the production Compose semantic instrumentation assertion
  for `photo_analysis_title`, `photo_analysis_status`, `capture_shutter`, and
  `capture_import`; the strict connected physical Android lane passed 10/10.
  Physical iOS camera/picker evidence remains open.
- 2026-07-28: Maestro 2.7.0 now parses the shared photo-analysis flow after
  adding the required YAML front-matter separator. On the physical Honor
  wireless-debugging transport, Maestro reaches app stop/launch but hangs in
  the OEM driver step; Android instrumentation remains the authoritative 10/10
  device evidence and no Maestro pass is claimed.
- 2026-07-28: Removed the duplicate mDNS alias for the same physical Honor
  handset and reran the strict connected suite through one direct IP transport;
  all 10 tests passed in 14 seconds. The duplicate endpoint had caused pending
  package-session collisions, not a bridge or product failure. Maestro's
  physical driver remains unclaimed because Honor PowerGenie freezes it when
  backgrounded.
- 2026-07-28: Repeated the single-device connected acceptance lane after the
  Gradle/build-logic profile update; the direct Honor DNY-NX9/API 36 XML result
  reports 10 tests, 0 failures, and 0 errors. The duplicate mDNS endpoint was
  disconnected before execution; physical iOS evidence remains open.
- 2026-07-28: The Intel x64 Xcode acceptance compatibility scheme also passed
  4/4 on the booted iOS 26.0.1 simulator; this does not close the physical iOS
  camera/picker gap.
- 2026-07-28: Re-ran the strict direct-IP physical Android lane after the
  bounded `1536m` Gradle Metaspace tuning; the production Compose bridge,
  permission recovery, picker/camera contracts, and semantic assertions passed
  10/10. Physical iOS camera and picker evidence remains open.
- 2026-07-28: Refactored the Android production host to keep one
  lifecycle-bound `ComposeView` composition with an observer-backed state
  owner; native permission, camera, picker, and settings effects remain in the
  thin host. The Compose compiler now consumes the exact stability manifest for
  the photo-analysis modules; no wildcard or mutable presenter-model claim was
  added.
- 2026-07-28: Split native-gap ownership into camera/picker, lifecycle,
  accessibility, and normalization records per platform. The Intel x64 iOS
  lane is explicitly compatibility-only; all physical iOS records remain
  pending until a real iPhone attestation is available.
- 2026-07-28: Re-ran the iOS x64 acceptance scheme with the framework built
  under the complete Intel compatibility flag pair and `ARCHS=x86_64`; the
  XCTest result reports 4 passed, 0 failed, and 0 skipped on iOS 26.0.1.
  Physical iOS camera/picker evidence remains pending by policy.
- 2026-07-28: Replaced the Android production `ACTION_IMAGE_CAPTURE` thumbnail
  route with a bridge-only CameraX 1.6.1 activity using lifecycle-bound preview
  and `ImageCapture`. The bridge now captures into a bounded in-memory output
  stream, so no new JPEG is persisted to disk; the defensive stale-file sweep
  remains for interrupted runs from older builds, and CameraX failure is mapped
  to the existing recovery state. Strict direct-IP physical Honor DNY-NX9/API
  36 instrumentation passed 13/13 before this in-memory hardening; the manual
  CameraX smoke had returned to Compose with `Photo ready`. Physical iOS
  camera/picker evidence remains pending.
- 2026-07-28: The prior physical Android camera evidence is intentionally not reused after
  this native bridge change; the current bounded in-memory path needs a fresh
  connected-device run before the native-gap record can be refreshed.
- 2026-07-28: Re-ran the current bounded CameraX path on the physical Honor
  DNY-NX9/API 36 device: permission dialog, preview, capture, return to the
  production Compose host, and `Photo ready` all passed. The app-private cache
  contained no new `supernutrition-camera-*` capture file.
- 2026-07-28: Re-ran the production `OPEN_DOCUMENT` picker on the same physical
  device, selected a real recent JPEG from DocumentsUI, and returned to the
  Compose host with `Photo ready`.
- 2026-07-28: Moved iOS `UIImagePickerController`/`PhotosUI` ownership out of
  the Xcode host and into `mobile/platform/photo-ios`; the host now routes
  opaque `NSData` callbacks through `IosPhotoBridge`. The Intel x64 bridge
  compile, SharedKit framework link, and Xcode simulator suite passed 4/4.
  Physical iOS camera/picker evidence and native-gap finalization remain
  pending by policy.
- 2026-07-28: Extended the Intel iOS compatibility XCTest to open and cancel
  the real `PHPickerViewController`; the current x86_64 simulator suite passed
  5/5. This remains compatibility-only and does not close the physical-iOS gap.

### File List

- `mobile/core/config/**`
- `mobile/domain/photo-analysis/**`
- `mobile/platform/photo-android/**`
- `mobile/platform/photo-ios/**`
- `mobile/feature/photo-analysis/**`
- `mobile/apps/androidApp/**`
- `mobile/apps/iosApp/SuperNutritionUIAcceptanceApp.swift`
- `mobile/.maestro/photo-analysis/**`
- `mobile/config/quality/**`
- `docs/decisions/2026-07-27-intel-ios-x64-acceptance-lane.md`
