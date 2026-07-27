# Cross-Platform UI Test Strategy Review

## Verdict

**FAIL.** The three-layer direction is sound—common Compose contract tests,
shared packaged-app Maestro journeys, and narrow native gap suites—but the
current decision is not implementable as written. One rule makes deterministic
packaged-app tests impossible, and the Compose test API/task, Kaspresso-equivalent
robot contract, selector exposure, and ownership split are not yet fixed tightly
enough for independent stories.

Deterministic spine lint passes with zero findings; the failures below are
semantic.

## What is coherent

- One authored Maestro journey executed on both products is the right
  cross-platform replacement for two platform-specific journey DSLs.
- Common Compose tests and packaged-app journeys serve different purposes; the
  documents do not collapse component contracts into slow end-to-end tests.
- Deterministic Engine, gateway, country, and catalog inputs are appropriate
  oracles; live inference/provider output is correctly excluded.
- Page objects, reusable subflows, platform-isolated selectors, stable
  semantics, and the ban on coordinates preserve the useful screen-object
  shape of the pinned GiphySearch/Kaspresso tests.
- Native instrumentation/XCTest is correctly retained for platform bridges,
  system UI, accessibility, lifecycle, and physical-device evidence.

## Critical

### C-1 — Deterministic packaged-app injection is forbidden by AD-28

AD-34 requires the Android and iOS packaged apps to run with injected test
Engines, gateway, country, and catalog fixtures. The PRD addendum, AR-41, and the
implementation guide repeat that requirement. AD-28 simultaneously says that
**every installable or deployable** Android variant, iOS framework/app, and
gateway artifact must contain no `testing` dependency, fake, test resource,
test manifest, or test-only DI binding.

A Maestro target is necessarily an installable app. It cannot replace the
production Engine and gateway deterministically without either a declared
test-only composition/configuration surface or an external deterministic
service. The blanket artifact rule therefore rejects the artifact AD-34 needs.

**Exact fix:**

1. Define manifest-listed, CI-only UI-test executables—for example an Android
   `uiTest` application variant and an iOS UI-test scheme/app configuration—with
   unique application/bundle IDs, test signing, no publishing task, and no
   production credentials.
2. Permit only those test executables to depend on
   `testing/compose-support` and fixture modules and to replace named ports at
   the app composition root. Keep production UI, reducer, navigation, and
   adapters real except for the ports explicitly replaced by the journey.
3. Amend AD-28's scan rule from “every installable or deployable artifact” to
   “every shippable product/internal-beta artifact,” then separately require
   CI-only test executables to be inventoried, non-publishable, and scanned for
   forbidden secrets/network destinations.
4. Add both test executables and their replacement-port allowlists to
   `module-boundaries.toml` and `module-matrix.toml`; make each Maestro lane
   install exactly its declared test executable.

If the blanket no-fake rule is intentionally retained, AD-34 must instead name
an external fixture gateway/configuration protocol and explain how both local
Engine and catalog behavior become deterministic without app-bundled test DI.

## High

### H-1 — The common Compose suite has no unambiguous runnable target

AD-35 says `quality-linux` includes common Compose UI contract tests, while
AD-27 describes the Linux slice as common/JVM/Android **host** tests. The
implementation guide lists `commonTest`, `androidUnitTest`, and `iosTest`, but
does not declare a JVM/desktop test target. Compose Multiplatform's current
documentation states that common Compose UI tests cannot run with Android local
test configurations; supported examples use `jvmTest`,
`connectedAndroidTest`, or `iosSimulatorArm64Test`.

Compose Multiplatform 1.11.1 also deprecates the old test API in favor of the v2
namespace, while every project document says only `runComposeUiTest`. An agent
can therefore choose a deprecated import or wire a Linux “host” task that never
executes the common UI suite.

**Exact fix:**

1. Bind the DSL to
   `androidx.compose.ui.test.v2.runComposeUiTest` with
   `@OptIn(ExperimentalTestApi::class)`.
2. Choose and name the execution topology. Recommended: give
   `feature/photo-analysis` and `testing/compose-support` an explicit
   test-purpose JVM target; run its `jvmTest` task in `quality-linux`, and run
   the same authored `commonTest` suite through `iosSimulatorArm64Test` in
   `quality-apple`.
3. If Android-runtime Compose behavior must also execute this suite, connect
   `androidDeviceTest` to the common test source-set tree and name a managed/
   connected-device task. Do not call it an Android host test.
4. Put the exact task names and nonzero test-result artifacts in
   `module-matrix.toml`; align AD-27, AD-35, solution design, the agent guide,
   PRD addendum, and AR-41.

Sources: [Compose Multiplatform UI testing](https://kotlinlang.org/docs/multiplatform/compose-test.html)
and [Compose 1.11.1 test API changes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html).

### H-2 — “Project robot DSL” does not yet preserve Kaspresso's useful abstraction

The pinned GiphySearch tests obtain more than fluent names from Kaspresso:
feature-local screen objects encapsulate selectors and nodes; tests use named
`step` scopes; Kaspresso supplies bounded flaky-operation handling,
interceptors, readable logs, hierarchy/screenshot artifacts, and setup/cleanup
hooks. AD-34 requires only an unspecified robot DSL. A minimal wrapper around
`onNodeWithTag` would technically comply while losing the reference's
readability, synchronization, and failure diagnosis.

**Exact fix:** Add a versioned robot contract owned by
`testing/compose-support` that requires:

- typed screen scopes with action/assertion methods and no raw selector use in
  test bodies;
- stable named step IDs/descriptions in emitted reports;
- Compose-clock/idling synchronization plus bounded `waitUntil`/`awaitIdle`,
  with no sleeps;
- bounded retry only around idempotent node lookup/assertion—not whole journey
  retries—with every retry reported;
- before/after fixture reset and cleanup hooks;
- on-failure step trace, semantics tree, platform/locale/configuration, and a
  privacy-filtered screenshot;
- the same robot API on every selected Compose test target.

Add negative tests for timeout, retry exhaustion, artifact redaction, and a
selector escaping its screen scope. This preserves the Kaspresso benefits
without importing its Android-only runtime. Source:
[Kaspresso capabilities](https://github.com/KasperskyLab/Kaspresso#capabilities-of-kaspresso).

### H-3 — Stable packaged-app selectors are named but not exposed or owned

AD-34 combines “visible semantics” and “accessibility IDs,” but those are
different contracts. Visible labels are localized user content; automation IDs
must be locale-neutral and stable. On iOS, Compose `testTag` maps to
`accessibilityIdentifier`. For Android external automation, `testTag` must be
exposed to the accessibility/UIAutomator tree; legacy resource-ID matching
requires `testTagsAsResourceId = true`. None of the documents names the
production owner, namespace, exposure configuration, or collision/removal rule.

**Exact fix:**

1. Define a production `UiAutomationId` registry under `core/ui` (or
   feature-owned constants governed by one namespace) with stable,
   locale-neutral IDs. Keep localized accessibility labels/roles/state
   descriptions separate.
2. Apply those IDs as Compose `testTag`s and enable
   `testTagsAsResourceId = true` once at the Android product semantics root.
   Preserve iOS mapping to `accessibilityIdentifier`.
3. Add a packaged-app selector-contract smoke test on both platforms that
   proves every ID referenced by the Maestro page objects is discoverable and
   unique. CI must fail on orphaned, duplicate, or platform-only IDs.
4. Restrict platform selector overrides to genuine native/system surfaces;
   shared Compose nodes should use the same semantic ID on both platforms.

Sources: [Compose iOS accessibility mapping](https://kotlinlang.org/docs/multiplatform/compose-ios-accessibility.html)
and [Android Compose test-tag interoperability](https://developer.android.com/develop/ui/compose/testing/interoperability).

### H-4 — Screen robots and journey assets have conflicting or missing owners

AD-28 and the structural seed put “screen robots” in
`testing/compose-support`, while solution design says tests stay beside the
owned production behavior. The pinned GiphySearch reference follows the latter:
shared test hosting/Koin support is centralized, but concrete Compose screen
objects live in each feature's test source set. The implementation guide's
“one shared Compose screen-contract test slice” does not name a module or path.
`module-boundaries.toml` governs modules, but `.maestro/`,
`journeys.toml`, and the platform selector maps also need explicit owners.

Centralizing every concrete robot makes routine feature changes contend on one
test-support module; leaving `.maestro` ownership unnamed lets either platform
silently change the shared journey or the other platform's selector map.

**Exact fix:**

- `testing/compose-support` owns only the base robot DSL, host,
  synchronization/reporting hooks, and test Koin utilities.
- `feature/photo-analysis/src/commonTest` owns its concrete screen robots and
  screen-contract tests beside the UI behavior.
- A named cross-platform UI-acceptance owner owns `journeys.toml`, shared
  `.maestro/flows`, subflows, and page-object schema.
- Android and iOS UI owners own only their selector implementations and runner
  configuration; changing a shared flow/page schema requires the acceptance
  owner plus both platform checks.
- Encode these non-module paths in CODEOWNERS and the ownership manifest, and
  replace “screen robots” with “base robot DSL” in AD-28 and the structural
  seed.

## Medium

### M-1 — “Rendering tested once” overclaims what the common suite proves

All four documents say common tests cover “rendering,” but no rendering oracle,
layout tolerance, screenshot format, or golden ownership is defined. Compose
platforms do not guarantee pixel-identical text and rendering. The phrase can
mean either semantic visibility/layout assertions or visual regression, leading
two stories to implement incompatible evidence.

**Exact fix:** Define common “rendering” as semantic presence, visibility,
enabled/selected state, bounded layout relationships, and state-driven content.
Rename it to “semantic and layout contract” in all inventories. If visual
regression is required, add separately owned per-platform golden tests with
reviewed device/font/theme/locale matrices and tolerances; do not claim one
common golden proves Android and iOS pixels.

### M-2 — The epic inventory contains the requirement but no implementable ownership stories

NFR-20 and AR-41/AR-42 are present, but `epics.md` still ends in the
`{{epics_list}}` and `{{story_title_N_M}}` template. The architecture guide
assumes a scaffold story and a positive-journey story that do not yet exist in
the inventory. With the repository's one-PR-per-story rule, no story currently
owns the v2 DSL, test executables/fixture seam, selector contract, shared
journeys, or two platform runners.

**Exact fix:** Before implementation, create separate stories/PR boundaries for:

1. Compose v2 robot base, runnable target matrix, and failure artifacts.
2. CI-only Android/iOS test executables and deterministic composition seam.
3. Stable UI automation IDs plus Android/iOS exposure contract.
4. Shared Maestro page schema, journey manifest, and UJ-1..UJ-4 flows.
5. Android and iOS runner/selector implementations, followed by one integration
   story that proves the same flow inventory on both.

Feature stories should own their feature-local common robots/contracts and
update a shared journey only when user-visible journey behavior actually
changes.

## Pass condition

The strategy passes once C-1 and H-1 through H-4 are resolved consistently in
the spine, solution design, implementation guide, PRD addendum, and epics
inventory; the exact v2 tasks execute in the declared lanes; and the scaffold
proves one deterministic journey against both declared CI-only packaged apps.
