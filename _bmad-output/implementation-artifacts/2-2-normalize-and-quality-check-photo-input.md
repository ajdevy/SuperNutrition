# Story 2.2: Normalize and quality-check Photo Input

Status: done

## Story

As an inference engine, I want one orientation-normalized, size-bounded,
metadata-scrubbed Photo Input so Android and iOS receive equivalent safe bytes.

## Acceptance Criteria

- Orientation, dimensions, color space, size, and metadata policy are
  deterministic and fixture-tested.
- EXIF, GPS, and non-essential metadata are removed before any consent digest
  or remote payload.
- Unavoidable temporary persistence is encrypted, attempt-scoped, and deleted
  on success, failure, cancellation, expiry, and replacement.

## Dependencies

Requires the opaque `PhotoRef`/lease contract from E2.1. Android native
fixture evidence remains the device lane; physical iOS/Apple fixture evidence
is good-to-have and does not block local activation.

## Tasks / Subtasks

- [x] Add common orientation/dimension/metadata-free Photo Input contract.
- [x] Add deterministic digest and size/dimension bounds.
- [x] Add Android decode, rotate, bound, and JPEG re-encode seam.
- [x] Add iOS CoreGraphics/PhotosUI re-encode seam.
- [x] Run binary-bound Android/iOS metadata fixtures on the physical Android
  lane and the approved Intel `iosX64` simulator lane.
- [x] Re-run the physical Android binary fixture after the CameraX bridge
  hardening invalidated the previous `platform/photo-android` attestation.
- [x] Keep the physical iOS fixture and arm64 Kotlin/Native runner available as
  optional evidence; the simulator lane remains compatibility-only.

## Dev Agent Record

### 2026-07-29 physical Android evidence

The clean-source physical acceptance run
`:apps:androidApp:connectedUiAcceptanceDebugAndroidTest` passed 14/14 with no
failures, errors, or skips on `HONOR DNY-NX9 / Android 16`. Its binary
normalization fixture attestation is bound to
`38aaf2a072553bcd404005e41c8765a446a0f952`; iOS remains optional and pending.

### 2026-07-28 verification note

The connected iPhone 14 Pro was paired, in Developer Mode, and had a connected
CoreDevice tunnel. The fresh `iosArm64TestBinaries` build succeeded, but the
temporary signed runner could not be installed because no valid provisioning
profile was available for the local Apple Development team (`0xe8008015`). The
physical binary fixture therefore did not execute and no new evidence binding
was emitted; the checked-in iOS record remains pending.

- 2026-07-27: JVM normalization tests pass for rotation, dimensions, sRGB,
  empty metadata output, and SHA-256 digest shape.
- 2026-07-27: Android native normalizer added; it re-encodes pixels after the
  orientation supplied by the native metadata reader, so EXIF/GPS are not
  carried into the normalized payload.
- 2026-07-27: `platform:photo-android:compileAndroidMain` and all E2 JVM
  suites pass after adding the Android bitmap rotate/scale/re-encode seam.
- 2026-07-27: iOS `iosX64` now compiles the UIKit/CoreGraphics decode, draw,
  JPEG re-encode, and Foundation-byte-copy seam through the isolated
  `photoIos` host slice; no `iosSimulatorArm64` task is part of this lane.
- 2026-07-27: The physical Android picker selected a real recent image and
  returned a content URI to the acceptance host without a crash; binary-bound
  normalized-byte fixture coverage remains pending for the platform seam.
- 2026-07-27: Added the canonical base64-encoded 6x4 JPEG fixture with EXIF
  orientation=6, GPS, and software metadata, plus Android test packaging. The
  physical DNY-NX9 test decoded the fixture, verified 4x6 upright output,
  sRGB, empty metadata, JPEG bytes, and absence of EXIF/GPS/software markers.
  iOS native compilation remains available in the Intel compatibility lane;
  physical iOS fixture execution is still pending.
- 2026-07-27: The iOS binary fixture now passes `:platform:photo-ios:iosX64Test`
  after production re-encoding and APP1-APP15 stripping. It verifies 4x6,
  `UP`, `sRGB`, empty metadata, JPEG SOI, and no JPEG application metadata.
  `qualityCheckApple` also passes; physical iOS execution remains the only
  fixture evidence gap.
- 2026-07-27: Android physical instrumentation rerun after wiring the default
  EXIF reader passed `OK (7 tests)`; the orientation=6 fixture is normalized to
  4x6 upright JPEG bytes with empty metadata and no EXIF/GPS/software markers.
- 2026-07-27: Re-ran the binary-bound iOS fixture through `:platform:photo-ios:iosX64Test`; it passed after adding `core/ui` to the isolated photo-iOS host slice. Physical iOS fixture evidence remains pending.
- 2026-07-28: Re-ran `:platform:photo-ios:iosX64Test` after the presenter/host split; the binary normalization fixture passed again. Physical iOS fixture evidence remains intentionally pending because this Intel lane is a simulator compatibility lane.
- 2026-07-28: Re-ran the binary-bound iOS x64 normalization fixture and the
  physical Android connected suite after the Gradle memory changes; both
  passed, including metadata stripping and EXIF-orientation assertions. No
  physical iOS fixture claim was made.
- 2026-07-28: Added domain ownership of normalized bytes (copy-on-normalize)
  and an Android device regression for extreme bounds, preventing mutable
  input aliasing and zero-dimension bitmap creation. The red/green fixture
  run passed on the physical DNY-NX9/API 36 lane.
- 2026-07-28: The final connected Android suite passed 9/9 with the binary
  EXIF/GPS fixture and extreme-bound regression; the iOS x64 binary fixture
  also passed. These remain compatibility evidence only until physical iOS
  execution is available.
- 2026-07-28: The Android and Intel x64 normalization lanes remained green
  after strict lock/verification enforcement; no ARM64 or physical-iOS claim
  was added.
- 2026-07-28: The strict connected physical Android suite passed 10/10 with
  the binary EXIF/GPS fixture and extreme-bound regression; the Intel x64
  normalization lane passed again. Physical iOS fixture execution remains
  intentionally pending.
- 2026-07-28: Bound the independently packaged Android and iOS EXIF/GPS
  fixtures to the same canonical source SHA-256
  `4371a3c8d33d1503cc8904b5a9e78687194daa5525c6d8322e4e7be3d619120c`.
  The strict Android device fixture suite passed 10/10 and the Intel iOS x64
  fixture suite passed with the new digest assertion; this does not claim
  physical iOS execution.
- 2026-07-28: Re-ran the binary-bound `iosX64Test` fixture with strict
  dependency verification; its XML result reports 3 tests, 0 failures, and 0
  errors. This remains Intel compatibility evidence, not physical iOS evidence.
- 2026-07-28: The strict physical Android normalization/metadata suite passed
  10/10 after the final Gradle memory profile; the x64 iOS binary fixture and
  Xcode compatibility suite remained green. No physical iOS fixture claim was
  added.
- 2026-07-28: Normalization evidence is now bound to dedicated Android/iOS
  normalization native-gap IDs instead of sharing one broad bridge record;
  iOS evidence explicitly requires a physical device while retaining the x64
  fixture as compatibility-only.
- 2026-07-28: The physical iPhone 14 Pro (iOS 26.5.2, arm64) ran the exact
  `iosArm64TestBinaries` Kotlin/Native binary in a temporary signed app. The
  bridge contract passed 2/2 and the binary normalization fixture passed 3/3,
  including the canonical source digest, 4x6 upright sRGB output, and removal
  of JPEG application metadata. The runner now emits the XML consumed by the
  physical evidence emitter; no simulator result was substituted.
- 2026-07-28: Rebuilt the shared iOS x64 framework with the complete Intel
  compatibility flag pair and reran the Xcode acceptance scheme with
  `ARCHS=x86_64`; the compatibility result reports 4 passed, 0 failed, and 0
  skipped. This does not close the physical-iOS normalization fixture gate.
- 2026-07-28: Hardened the common normalization boundary so it rejects any
  non-UP orientation, non-sRGB color space, or non-empty metadata set instead
  of relabeling unsanitized bytes as safe. Android and iOS platform adapters
  already satisfy the stricter contract; the JVM, iOS x64 fixture, and physical
  Android 10/10 acceptance lanes remain green.
- 2026-07-28: Removed new Android CameraX disk persistence from the capture
  path: `ImageCapture` now writes to a bounded in-memory stream and returns
  opaque bytes through the bridge. The legacy app-private file sweep remains a
  defensive cleanup for interrupted pre-hardening runs; no new capture file is
  created by production CameraX. Physical iOS fixture evidence remains
  pending.
- 2026-07-28: Re-ran the strict connected Android acceptance suite on the
  physical Honor DNY-NX9/API 36 device after the bridge hardening; all 14
  instrumentation tests passed, including the binary normalization and
  one-shot capture-result checks.
- 2026-07-28: The physical `OPEN_DOCUMENT` picker path also accepted a real
  recent JPEG and returned normalized bytes to the production Compose host,
  which reported `Photo ready`.
