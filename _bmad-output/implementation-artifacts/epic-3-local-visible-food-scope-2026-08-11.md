# Epic 3 local visible-food scope — 2026-08-11

## Accepted implementation scope

- The local Android and iOS paths identify visible foods only.
- Local presentation shows no ingredient suggestions and no grams.
- Component/mass production activation is not claimed locally; a future remote
  route can introduce ingredient or mass inference under its own contract.

## Implemented changes

- Pin the selected YOLO-World asset and immutable five-label taxonomy in the
  Android visible-food manifest with `STAGED` activation and no placeholder
  approvals or rollback evidence.
- Share Android YOLO-World preprocessing, decode, label filtering, normalised
  boxes, and NMS between the shipped PoC runtime and benchmark runner.
- Remove local dish-to-ingredient priors and suppress local mass/ingredient UI
  output while retaining the remote presentation path.
- Make local loading and evidence labels describe visible-food identification.

## Explicitly out of scope

- Ingredient inventory, portion/gram estimation, component corpus calibration,
  and component/mass production sign-off. These are for a separately governed
  remote model if and when it is introduced.

## Verification record

- `:feature:photo-analysis-presenter:jvmTest` — 10 tests.
- `:inference:local-android-poc:testDebugUnitTest` — 6 tests.
- `:feature:photo-analysis-ui:jvmTest` — 10 tests.
- `tools/g3-benchmark/android-runner :app:testDebugUnitTest :app:assembleDebug`
  — 4 tests and debug APK assembled.
- `:apps:sharedKit:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true`
  — Kotlin/Native compile passed on the local Intel host.
- `qualityCheckJvmAndroid --continue` — passed with authoritative JVM-host
  aggregate coverage of 98.15% line / 87.22% branch across nine production
  modules.
- Physical HONOR DNY-NX9 / Android 16 / API 36:
  `connectedAndroidTest` passed 16/16 each for `pocDebug`, `productionDebug`,
  and `uiAcceptanceDebug`, with zero failures, errors, or skips. The clean
  evidence emitter bound the UI and PoC APK/test-APK digests to commit
  `171eeae7771b850d5b93a3706742e46a5a758b41`.
