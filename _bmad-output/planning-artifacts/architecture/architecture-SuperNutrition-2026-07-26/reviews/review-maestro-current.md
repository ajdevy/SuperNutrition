# Current-Source Review — Compose Multiplatform and Maestro UI Testing

- **Reviewed:** `ARCHITECTURE-SPINE.md`, `solution-design.md`,
  `implementation-agent-guide.md`, and `giphysearch-practices-adoption.md`
- **Lens:** Compose Multiplatform 1.11.1 UI tests, Maestro CLI 2.7.0,
  cross-platform flow abstractions, and GitHub Actions execution
- **Review date:** 2026-07-26
- **Verdict:** **Conditional pass.** The two-layer strategy is current and
  suitable: common Compose tests provide fast shared screen contracts, while
  one Maestro flow inventory can drive packaged Android and iOS applications.
  Four implementation corrections are required before the Bootstrap story can
  claim that AD-34 and AD-35 are executable rather than directional.

## Severity-ranked findings

| ID | Severity | Finding | Required disposition |
| --- | --- | --- | --- |
| UI-1 | **Medium** | `runComposeUiTest` is ambiguous in Compose 1.11.1 because the old entry point is deprecated and the supported example imports the experimental v2 API. Host task ownership is also underspecified. | Bind the robot DSL to the exact v2 import, dependencies, and Gradle tasks before scaffold implementation. |
| UI-2 | **Medium** | Maestro's documented page-object model is a JavaScript selector-dictionary recipe, not a built-in typed page/robot API. | Define the checked-in loader, selector dictionaries, app-ID injection, and subflow boundary precisely. |
| UI-3 | **Medium** | GitHub Actions is a suitable host, but the named jobs omit the package installation, device selection, CLI invocation, and report contract required by local Maestro CLI. | Make both UI jobs executable acceptance contracts, including an exact CLI pin and JUnit output. |
| UI-4 | **Medium** | Maestro 2.7.0 captures a screenshot before every step and includes device/crash output, so an upload-time privacy statement alone is insufficient. | Constrain test data at generation time and explicitly allowlist retained artifacts. |
| UI-5 | **Low** | “Stable visible semantics/accessibility IDs” is sound, but identical Compose `testTag` behavior across Android and iOS is not guaranteed by the cited sources. | Prefer user-visible/accessibility semantics, isolate platform selectors, and prove both hierarchies during Bootstrap. |

## Required corrections

### UI-1 — Bind the robot DSL to Compose UI test v2 and real target tasks

Compose Multiplatform 1.11.1 officially documents the common dependency
`org.jetbrains.compose.ui:ui-test:1.11.1`, but the example imports
`androidx.compose.ui.test.v2.runComposeUiTest` and opts into
`ExperimentalTestApi`. The 1.11.1 release notes explicitly deprecate the older
`runComposeUiTest`, `runSkikoComposeUiTest`, and `runDesktopComposeUiTest`
entry points in favor of their v2 versions. A wrapper described only as being
“over `runComposeUiTest`” can therefore bind to the deprecated API while still
appearing to satisfy the architecture.

The supported execution targets also matter:

- JVM host execution is `:shared:jvmTest`;
- iOS simulator execution is `:shared:iosSimulatorArm64Test`;
- Android instrumentation execution is `:shared:connectedAndroidTest`;
- common Compose UI tests cannot currently run through an Android local-test
  configuration.

If Android instrumentation is selected, the official setup additionally uses
`withDeviceTestBuilder { sourceSetTreeName = "test" }`, an
`androidDeviceTest` manifest, and AndroidX
`ui-test-junit4-android:1.11.2` plus `ui-test-manifest:1.11.2`. Those AndroidX
versions intentionally differ from the JetBrains common artifact's `1.11.1`
because Compose Multiplatform 1.11.1 is based on Jetpack Compose UI 1.11.2.

**Precise fix:**

1. State that the project robot DSL wraps
   `androidx.compose.ui.test.v2.runComposeUiTest` and owns the experimental
   opt-in.
2. State that tests are **authored once in `commonTest` and executed in the
   named host targets**. “Tested once” must not mean that one host execution
   proves all platform rendering.
3. Bind `quality-linux` to `jvmTest` for the fast common contract suite and
   `quality-apple` to `iosSimulatorArm64Test`. If the architecture wants the
   common suite executed against Android UI rather than only JVM plus packaged
   Maestro coverage, add `connectedAndroidTest` to a device-bearing lane; do
   not place it under Android host/local tests.
4. Treat semantics finders, actions, and assertions as contract evidence.
   Unless explicit screenshot assertions are added, do not describe these
   tests as pixel/visual-regression proof or accessibility-service proof.

Sources:
[Compose Multiplatform UI testing](https://kotlinlang.org/docs/multiplatform/compose-test.html),
[Compose Multiplatform 1.11.1 UI test v2 changes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html).

### UI-2 — Specify what the Maestro page-object and subflow abstractions are

The architecture's desired abstraction is supported, but Maestro does not
provide a typed page-object or robot class framework. Its official POM recipe:

1. declares selector values in JavaScript files through the `output` object;
2. loads them with `runScript`, commonly from a loader Flow;
3. invokes that loader with `runFlow`;
4. references values such as `${output.capture.confirmButton}` from YAML; and
5. conditionally loads Android and iOS implementations with
   `when: platform`.

Reusable action sequences are separate YAML subflows invoked with `runFlow`.
Arguments can be passed through `env`. Maestro also recommends injecting
`APP_ID` because Android package names and iOS bundle identifiers commonly
differ.

**Precise fix:** define `.maestro/selectors/` as JavaScript `output`
dictionaries plus one checked-in loader Flow. Define `.maestro/subflows/` as
atomic YAML action sequences. Keep stable functional selector names and the
positive journey Flow files platform-neutral; load platform-specific selector
values conditionally and inject `APP_ID` per job. Do not imply that JavaScript
selector files contain a first-class Maestro page/robot API.

Sources:
[Maestro POM recipe](https://docs.maestro.dev/examples/recipes/implementing-the-page-object-model-pom),
[nested flows](https://docs.maestro.dev/maestro-flows/flow-control-and-logic/nested-flows),
[conditions](https://docs.maestro.dev/maestro-flows/flow-control-and-logic/conditions),
[parameters and constants](https://docs.maestro.dev/maestro-flows/flow-control-and-logic/parameters-and-constants).

### UI-3 — Make the local Maestro GitHub Actions lanes executable

Maestro CLI 2.7.0 is the current signed release as of this review, released on
2026-07-20. It requires Java 17 or newer, so the architecture's JDK 21 pin is
sufficient. GitHub-hosted Linux runners support Android-emulator hardware
acceleration. GitHub's `macos-26` image includes Xcode 26.4.1 and an iOS 26.4
simulator runtime. These facts make the proposed jobs suitable.

Suitability does not provide provisioning automatically. Maestro's Android
documentation assumes the app is already installed on a running emulator or
device; iOS local execution requires a booted Xcode simulator and a
simulator-built `.app`. Maestro supports Android emulators and physical devices,
but local iOS support is simulator-only. The architecture correctly keeps
physical iOS and native accessibility/hardware claims outside Maestro.

The official GitHub Action documented by Maestro is for Maestro Cloud. AD-35 can
use the open-source local CLI instead, but it must not be implemented as though
the Cloud Action provisions the existing local jobs.

**Precise fix:** require the Bootstrap workflow to prove:

- `ui-journeys-android`: boot one pinned emulator, wait for readiness, install
  the APK, select its device ID, and inject the Android `APP_ID`;
- `ui-journeys-ios`: run on a pinned macOS image, select the exact Xcode path,
  boot one pinned simulator/runtime, build and install a simulator `.app`,
  select its UDID, and inject the iOS `APP_ID`;
- both: install exactly CLI 2.7.0 (the official installer supports
  `MAESTRO_VERSION`), assert `maestro --version`, and run a command equivalent
  to `maestro --device <id> test <workspace> --format junit --output <file>`
  with an explicit test/debug output directory;
- upload the JUnit report separately from Maestro's test/debug artifact
  directory, because the report is not placed there automatically;
- pin every referenced GitHub Action by full commit SHA. Pin a concrete runner
  image label and toolchain selection rather than relying on `-latest`.

Sources:
[Maestro CLI 2.7.0 release](https://github.com/mobile-dev-inc/Maestro/releases/tag/cli-2.7.0),
[Maestro CLI installation and Java requirement](https://docs.maestro.dev/maestro-cli/how-to-install-maestro-cli),
[specific-version installation](https://docs.maestro.dev/maestro-cli/how-to-install-maestro-cli/update-the-maestro-cli),
[device targeting](https://docs.maestro.dev/cli/start-device),
[Android execution](https://docs.maestro.dev/getting-started/build-and-install-your-app/android),
[iOS execution](https://docs.maestro.dev/getting-started/build-and-install-your-app/ios),
[reports and artifacts](https://docs.maestro.dev/troubleshooting/debug-output),
[GitHub-hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners),
[macOS 26 runner image](https://github.com/actions/runner-images/blob/main/images/macos/macos-26-Readme.md),
[pinning Actions by full SHA](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/find-and-customize-actions).

### UI-4 — Treat Maestro output as sensitive by construction

Maestro 2.7.0's changelog says that its artifact bundle captures a screenshot
before every step and can include a failing view hierarchy, device logs, and
crash/ANR reports. For a nutrition/photo application, these can expose a
captured image, user-visible labels, paths, or provider-derived content before
an upload-time filter has a chance to help.

**Precise fix:** AD-14/AD-35 implementation must use only deterministic,
non-sensitive fixtures and synthetic photos in every Maestro run; forbid real
credentials, user images, filenames, free text, and live provider responses;
allowlist the exact report/artifact files that may be uploaded; omit any
artifact category that cannot be reliably sanitized; and set a short explicit
GitHub artifact retention period. The CI command and its failure handler must
not print secret environment values.

Sources:
[Maestro 2.7.0 changelog](https://github.com/mobile-dev-inc/Maestro/blob/main/CHANGELOG.md#270),
[GitHub artifact retention](https://docs.github.com/en/actions/tutorials/store-and-share-data).

## Confirmed-current and sound decisions

- Compose Multiplatform UI testing is experimental, uses the same finders,
  actions, assertions, and matchers as Jetpack Compose testing, and is
  appropriately isolated behind a project-owned robot DSL.
- One common test source set can run on JVM, iOS simulator, and Android
  instrumentation when each target is configured as documented.
- Maestro CLI 2.7.0 is a valid current pin and contains fixes relevant to both
  Android and iOS flow reliability.
- One platform-neutral Flow inventory is feasible. `runFlow`, JavaScript
  `output` selector dictionaries, platform conditions, and environment
  parameters support the proposed subflow/selector design.
- Maestro uses the accessibility/UI layer. On Android, its official Compose
  guidance prefers text and semantics for resilience and permits IDs/test tags
  for disambiguation. That supports AD-34's ban on coordinate selectors.
- Running the same user-visible assertions on packaged Android and iOS apps is
  a useful acceptance contract, but it is not by itself proof of VoiceOver,
  TalkBack, pixel equivalence, camera hardware, or physical-device behavior.
  The architecture correctly retains native suites and story-gated real-device
  evidence for those gaps.
- GitHub Actions can host the proposed local CLI lanes. Linux is suitable for
  the Android emulator, while the iOS lane must run on macOS with an Xcode
  simulator.

Additional primary sources:
[Maestro supported platforms](https://docs.maestro.dev/get-started/supported-platform),
[Maestro Jetpack Compose guidance](https://docs.maestro.dev/get-started/supported-platform/android/jetpack),
[Maestro Android Native selectors](https://docs.maestro.dev/get-started/supported-platform/android/android-native),
[Maestro CLI overview](https://docs.maestro.dev/maestro-cli).
