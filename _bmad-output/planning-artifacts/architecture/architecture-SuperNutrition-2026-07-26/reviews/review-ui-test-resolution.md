# Cross-Platform UI Test Resolution Review

## Verdict

**FAIL — one High contradiction remains; no Critical findings.**

The updated documents substantively resolve H-1 through H-4 and the operational
part of C-1. The new non-distributable UI-acceptance exception, however,
conflicts with AD-25's still-absolute production-module dependency rule. Until
that exception is stated at the topology level, two implementation agents can
legitimately make opposite decisions about whether the app module may reference
`testing/compose-support`.

Deterministic spine lint passes with zero findings.

## Resolution matrix

| Prior finding | Status | Evidence |
| --- | --- | --- |
| C-1: deterministic packaged apps forbidden | **Partially resolved** | AD-28 now defines unique-ID, non-distributable Android `uiAcceptance` and iOS `SuperNutritionUIAcceptance` executables, an allowlist of replaceable ports, no publish/export path or credentials, distributable-artifact scans, and a real-composition smoke. AD-29/30 inventory the executables and replacement ports; AD-35 installs the exact executables. One topology contradiction remains below. |
| H-1: no runnable common Compose target | **Resolved** | The API is fixed to `androidx.compose.ui.test.v2.runComposeUiTest`; the same `commonTest` IDs execute through `jvmTest`, `connectedAndroidTest`, and `iosSimulatorArm64Test`; AD-27/29/35 name their lanes and reconcile nonzero IDs/results. The guide now declares `jvmTest` and `androidDeviceTest` source roots. |
| H-2: robot DSL lacks Kaspresso-equivalent behavior | **Resolved** | AD-28 now requires typed screen scopes, named steps, no raw selectors in test bodies, idling/clock synchronization, bounded waits, narrowly scoped reported retries, fixture lifecycle hooks, redacted failure artifacts, and negative DSL tests. |
| H-3: selectors not exposed or owned | **Resolved** | AD-34 gives `core/ui` one locale-neutral `UiAutomationId` registry, separates localized accessibility semantics, enables Android `testTagsAsResourceId`, preserves iOS accessibility identifiers, and requires cross-platform discovery/uniqueness checks. |
| H-4: robot/journey ownership conflicts | **Resolved** | `testing/compose-support` owns only the base DSL and infrastructure; concrete robots remain in `feature/photo-analysis/src/commonTest`; CODEOWNERS assigns the scenario catalog/shared flows/page schema to a cross-platform acceptance owner and platform locator dictionaries/runners to their platform owners. |

## Critical

None.

## High

### R-1 — AD-25 still forbids the AD-28 executable dependency it now requires

AD-25 says, without qualification, that “production modules never depend on
`testing`.” The Android `uiAcceptance` executable is defined as an application
variant of the production app and must use `testing/compose-support` and fixture
modules to replace allowlisted ports. Even if only a variant-specific
configuration carries that edge, the Gradle project is still the production app
module.

AD-28 and AD-30 explicitly allow this edge, while AD-25 explicitly forbids it.
The implementation guide already uses the narrower and coherent wording
“production source sets never depend on `testing`,” so the guide and spine now
disagree at the module boundary.

**Exact recommended fix:**

1. Replace AD-25's final clause with:

   > Distributable production source sets, variants, frameworks, apps, and
   > services never depend on `testing`; only the two AD-28 manifest-listed,
   > non-distributable UI-acceptance executables may use declared test-only
   > edges and replacement ports.

2. In `module-boundaries.toml`, model the exception at the configuration/scheme
   level, not as a general `apps -> testing` edge.
3. Add negative boundary fixtures proving:
   - `implementation`, release, store, internal-beta, framework-export, and
     gateway graphs fail on any `testing` dependency;
   - only Android `uiAcceptance` and iOS `SuperNutritionUIAcceptance` accept the
     exact allowlisted test edges;
   - renaming or adding another acceptance configuration fails until the
     manifest is independently approved.
4. Add brief structural-seed comments under `apps/androidApp` and `apps/iosApp`
   naming their one non-distributable acceptance configuration so the seed
   cannot be read as three production-only hosts.

## Pass condition

After AD-25 is narrowed to the same distributable/source-set boundary already
used by AD-28 and the implementation guide, C-1 and H-1 through H-4 are
resolved with no remaining Critical or High contradiction in the reviewed
documents.
