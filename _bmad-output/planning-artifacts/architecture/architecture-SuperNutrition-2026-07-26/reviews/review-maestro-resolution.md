# Resolution Review — Compose Multiplatform and Maestro UI Testing

- **Re-reviewed:** `ARCHITECTURE-SPINE.md`, `solution-design.md`,
  `implementation-agent-guide.md`, and `giphysearch-practices-adoption.md`
- **Baseline:** `reviews/review-maestro-current.md`
- **Review date:** 2026-07-26
- **Verdict:** **PASS**
- **Remaining Critical findings:** **0**
- **Remaining High findings:** **0**

The corrected architecture resolves every architecture-level disposition from
the source review. It now fixes the exact Compose UI test API and artifacts,
names all three execution targets, represents Maestro's page-object recipe
accurately, makes the local-CLI GitHub Actions lanes implementable, and treats
Maestro 2.7.0 output as sensitive by construction.

This is a documentation/architecture pass. The repository is still an
unimplemented scaffold, so the Bootstrap proof remains responsible for
demonstrating that the selected Gradle tasks, device setup, Maestro commands,
and privacy controls execute successfully.

## Resolution matrix

| Prior finding | Resolution | Evidence in corrected architecture | Result |
| --- | --- | --- | --- |
| UI-1 — ambiguous `runComposeUiTest` API and target ownership | The robot DSL is bound to `androidx.compose.ui.test.v2.runComposeUiTest` with `ExperimentalTestApi`; the common and Android dependencies are separately pinned; tests are authored in `commonTest` and executed through named JVM, Android-device, and iOS-simulator tasks. | AD-26 through AD-29, AD-34, Stack, Bootstrap template | **Resolved** |
| UI-2 — page object could be mistaken for a built-in typed Maestro API | Page objects are explicitly JavaScript `output` selector dictionaries loaded by `runScript`; platform-conditional `runFlow` may load only app identity and locators; action/assertion logic remains in the shared YAML flow/subflows. | AD-34, solution-design test strategy, positive-journey template | **Resolved** |
| UI-3 — GitHub Actions lanes omitted executable local-CLI duties | Each UI job must provision its pinned device, build and install the acceptance app, inject `APP_ID`, checksum-install and version-check CLI 2.7.0, target an explicit device, run the shared workspace, and emit JUnit plus reviewed debug output. Actions are full-SHA pinned and the Android/iOS host responsibilities are separated. | AD-35, solution-design quality contract, Bootstrap template | **Resolved** |
| UI-4 — 2.7.0 per-step screenshots and device/crash output create privacy exposure | Acceptance executables contain no production credentials, permit only fixture endpoints, and use deterministic fixtures. AD-35 forbids real user data and requires allowlisted, redacted artifact paths/types with short explicit retention. | AD-28, AD-34, AD-35 | **Resolved** |
| UI-5 — cross-platform identifier behavior needed proof rather than assumption | The architecture separates locale-neutral automation IDs from accessibility labels, explicitly configures Android resource-ID exposure, relies on Compose's documented iOS `testTag` to `accessibilityIdentifier` mapping, and requires selector-contract tests for missing, duplicate, orphaned, or platform-only IDs. | AD-34 and selector-contract tests | **Resolved** |

## Verification details

### Compose Multiplatform UI tests

The corrected contract is exact enough for independent scaffold implementers:

- common dependency:
  `org.jetbrains.compose.ui:ui-test:1.11.1`;
- Android device-test dependencies:
  `androidx.compose.ui:ui-test-junit4-android:1.11.2` and
  `androidx.compose.ui:ui-test-manifest:1.11.2`;
- wrapper entry point:
  `androidx.compose.ui.test.v2.runComposeUiTest`;
- opt-in:
  `@OptIn(ExperimentalTestApi::class)`;
- Linux execution:
  `jvmTest`;
- Android emulator execution:
  `connectedAndroidTest`;
- Apple execution:
  `iosSimulatorArm64Test`.

AD-26 also requires Android-bearing KMP modules to opt into the required
host/device test builders, and the implementation guide fixes
`src/androidDeviceTest` as a standard source root. This removes the previous
risk of accidentally treating Android local tests as supported common Compose
UI execution. AD-34 correctly limits the shared claim to semantics, state, and
bounded layout and explicitly rejects a common pixel-golden claim.

Primary confirmation:
[Compose Multiplatform UI testing](https://kotlinlang.org/docs/multiplatform/compose-test.html),
[Compose Multiplatform 1.11.1 test v2 changes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html).

### Maestro flow, selector, and platform boundaries

AD-34 now matches Maestro's documented abstraction model:

- JavaScript files define selector dictionaries through `output`;
- `runScript` loads those dictionaries;
- platform-conditioned `runFlow` selects Android or iOS locator/app-identity
  data;
- shared YAML flows and reusable subflows retain all actions, input,
  assertions, waits, fixtures, and outcomes.

The added normalized Android/iOS flow fingerprint is stronger than the original
correction: it makes “same flow” machine-checkable while allowing only locator
and app-identity differences. Platform files are expressly forbidden from
containing alternate behavior.

Primary confirmation:
[Maestro POM recipe](https://docs.maestro.dev/examples/recipes/implementing-the-page-object-model-pom),
[nested flows](https://docs.maestro.dev/maestro-flows/flow-control-and-logic/nested-flows),
[conditions](https://docs.maestro.dev/maestro-flows/flow-control-and-logic/conditions).

### GitHub Actions suitability and executable responsibilities

AD-35 no longer assumes that Maestro or GitHub provisions the environment. It
assigns build, device boot/provisioning, installation, app identity, CLI
integrity/version, device selection, test execution, and report generation to
the UI jobs. It keeps the Android emulator on an accelerated Linux lane and the
iOS simulator on the pinned macOS/Xcode lane. The Android Compose device suite
runs before Android Maestro acceptance; the iOS common suite remains in the
Apple quality job.

The architecture also correctly distinguishes the open-source local CLI plan
from Maestro Cloud and retains native/physical-device evidence for claims that
simulators and accessibility-layer automation cannot establish.

Primary confirmation:
[Maestro CLI 2.7.0](https://github.com/mobile-dev-inc/Maestro/releases/tag/cli-2.7.0),
[device targeting](https://docs.maestro.dev/cli/start-device),
[Android execution](https://docs.maestro.dev/getting-started/build-and-install-your-app/android),
[iOS execution](https://docs.maestro.dev/getting-started/build-and-install-your-app/ios),
[reports](https://docs.maestro.dev/troubleshooting/debug-output),
[GitHub-hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners),
[macOS 26 image](https://github.com/actions/runner-images/blob/main/images/macos/macos-26-Readme.md).

### Artifact privacy

The corrected rules apply before upload rather than relying on post-failure
cleanup:

- the acceptance apps carry no production credentials;
- only allowlisted fixture endpoints and deterministic fixture data are
  permitted;
- real user data is forbidden;
- artifact paths and types are allowlisted;
- retained output is redacted;
- retention is short and explicit.

This is an appropriate architecture response to Maestro 2.7.0 capturing a
screenshot before each step and collecting failure hierarchy, device, and
crash/ANR evidence.

Primary confirmation:
[Maestro 2.7.0 changelog](https://github.com/mobile-dev-inc/Maestro/blob/main/CHANGELOG.md#270),
[GitHub artifact retention](https://docs.github.com/en/actions/tutorials/store-and-share-data).

## Bootstrap proof obligations

These are implementation evidence, not remaining architecture findings:

1. The generated Gradle build must demonstrate the official Android device-test
   builder/source-set/runner/manifest configuration and nonzero executions for
   all three Compose tasks.
2. The workflow evidence report must record the exact Maestro invocation,
   including explicit device selection, JUnit output path, and separate
   debug/test-output directory.
3. Negative privacy tests must prove that screenshots, view hierarchies, device
   logs, crash/ANR output, report XML, and shell logs cannot publish
   non-allowlisted fixture or secret data.
4. Both packaged acceptance apps must expose the automation-ID registry in the
   real Maestro hierarchy, and the same normalized journey fingerprints must
   pass on Android and iOS.

Failure of any Bootstrap obligation reopens the relevant finding; none is a
Critical or High defect in the corrected architecture text.
