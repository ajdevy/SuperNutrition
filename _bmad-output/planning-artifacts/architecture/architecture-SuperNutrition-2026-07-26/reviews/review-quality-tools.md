# Architecture Reviewer Gate — Build and Quality Tooling

## Verdict

**Fail pending two high-severity corrections.** The core
Kotlin 2.4.10 / Gradle 9.5.0 / AGP 9.1.0 / Compose Multiplatform 1.11.1 tuple is
current and officially compatible. AD-25 through AD-28 also establish the right
centralization direction. The gate is not yet enforceable, however, because the
Detekt seed is from an incompatible tool generation and the AGP 9 KMP plugin
topology is not fixed. Cross-host quality, configuration-cache proof, lock-state
coverage, and Kover scope also need precise task contracts before the Bootstrap
proof can close.

The deterministic spine linter passes with zero findings. The failures below are
semantic/current-technology findings.

## Severity-ranked findings

| ID | Severity | Finding | Required disposition |
| --- | --- | --- | --- |
| QT-1 | **High** | Detekt 1.23.8 is not a valid seed for the pinned Kotlin/Gradle/AGP tuple. | Fix before scaffold implementation. |
| QT-2 | **High** | AD-26 does not fix the mandatory AGP 9 plugin split for Android apps versus Android-bearing KMP libraries. | Amend AD-25/AD-26 before independent module scaffolding. |
| QT-3 | **Medium** | `qualityCheck` and the Linux/macOS matrix do not define one complete, branch-protectable gate. | Add exact host-qualified task ownership and required CI checks. |
| QT-4 | **Medium** | “Proves configuration cache” is not an executable acceptance contract. | Require store-and-reuse runs with problems failing. |
| QT-5 | **Medium** | Dependency verification is well chosen, but dependency-lock completeness is not defined per project/configuration. | Define lock generation, committed files, and strict CI validation. |
| QT-6 | **Medium** | Kover 0.9.8 is current, but AD-27 can be read as covering Native and device tests that Kover cannot measure. | Split JVM/Android-host coverage from Native/device evidence and pin the merge variant. |
| QT-7 | **Low** | The formatting checker behind “Detekt plus formatting check” is unnamed and unversioned. | Pin the artifact, ruleset version, and check/format task pair with QT-1. |

## Detailed findings and precise fixes

### QT-1 — High: Detekt 1.23.8 is incompatible with the toolchain generation

The Stack calls `1.23.8` a conditional seed, while AD-27 makes Detekt mandatory.
Detekt's own compatibility table says 1.23.8 was built against Kotlin 2.0.21,
Gradle 8.12.1, and AGP 8.8.1. Detekt 2.0.0-alpha.5 is the current line built
against Kotlin 2.4.0 and Gradle 9.5.1; Detekt's migration guide describes 1.23.x
as the Kotlin 1.9/2.0 line and 2.0.x as the Kotlin 2.4 line. A bootstrap gate
should not begin from a known compiler/Gradle-generation mismatch and merely
hope the proof rescues it.

**Precise fix:**

1. Make one explicit tuple decision:
   - preferred for the pinned Kotlin 2.4 stack: seed
     `dev.detekt:detekt-gradle-plugin:2.0.0-alpha.5` and record that the quality
     tool is pre-release until a compatible stable Detekt 2 release exists; or
   - if stable Detekt is mandatory, lower Kotlin/Gradle/AGP to a tuple supported
     by Detekt 1.23.8.
2. Do not leave 1.23.8 as the seed for Kotlin 2.4.10.
3. In the Bootstrap proof, run the KMP source-set tasks plus type-resolved
   JVM/Android compilation tasks. Detekt documents that Native source sets are
   analyzed without type resolution, while JVM/Android compilation tasks can use
   it; the evidence report must not imply otherwise.
4. Pin the formatting extension from the same selected generation:
   `dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.5` for Detekt 2, or the
   matching 1.23.8 formatting artifact only if the whole tuple is lowered.

Sources: [Detekt compatibility table](https://detekt.dev/docs/introduction/compatibility/),
[Detekt 2 migration guide](https://detekt.dev/docs/introduction/migration/),
[Detekt KMP type-resolution behavior](https://detekt.dev/docs/gettingstarted/type-resolution/),
[Detekt 2 ktlint wrapper](https://detekt.dev/docs/rules/ktlint/).

### QT-2 — High: AGP 9 requires plugin identities that AD-26 leaves open

The structural seed correctly separates `apps/androidApp` from shared modules,
but “KMP library,” “Compose feature,” and “Android app” convention plugins are
not sufficient identities. With AGP 9 built-in Kotlin:

- an Android application module uses `com.android.application` and must not
  apply `org.jetbrains.kotlin.android`;
- a KMP library with an Android target must apply
  `org.jetbrains.kotlin.multiplatform` together with
  `com.android.kotlin.multiplatform.library`;
- combining KMP with `com.android.application` or `com.android.library` in the
  same module is no longer allowed under built-in Kotlin.

Independent convention-plugin authors can currently choose incompatible plugin
sets while still claiming to implement AD-25/AD-26.

**Precise fix:**

1. Amend AD-26 to name the plugin sets:
   - `apps/androidApp`: `com.android.application`, AGP built-in Kotlin, Compose
     compiler plugin where Compose is compiled; never `org.jetbrains.kotlin.android`;
   - Android-bearing KMP libraries: `org.jetbrains.kotlin.multiplatform` +
     `com.android.kotlin.multiplatform.library`, using the AGP 9 `kotlin {
     android { ... } }` DSL;
   - pure KMP domain modules: `org.jetbrains.kotlin.multiplatform` only and no
     Android target unless the module actually needs one.
2. Make the Android application entry point remain a separate module; forbid
   `com.android.application` in shared KMP modules.
3. Where Android KMP tests are expected, have the convention plugin explicitly
   enable `androidHostTest` and `androidDeviceTest`; the new plugin requires
   opting into host/device test builders.
4. Add a forbidden-plugin check to AD-27 for `org.jetbrains.kotlin.android` and
   legacy `com.android.library` inside KMP modules.

Sources: [Android built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin),
[official Android-KMP library plugin guide](https://developer.android.com/kotlin/multiplatform/plugin),
[Kotlin AGP 9 KMP migration](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html).

### QT-3 — Medium: the cross-host quality gate is incomplete

AD-27 says one `qualityCheck` aggregates eligible iOS tests, but AD-16 proves
Linux `qualityCheck` and separate “macOS iOS jobs.” Apple final binaries and iOS
tests require macOS, and runnable simulator tests require a compatible native
macOS host. Therefore a Linux-only `qualityCheck` cannot be the complete gate,
while unspecified macOS jobs may omit lint, fixtures, locking, or
configuration-cache enforcement.

**Precise fix:** define and branch-protect this minimum matrix:

| Required check | Host | Exact responsibility |
| --- | --- | --- |
| `quality-linux` | Linux x64 | Read-only formatting/Detekt, forbidden dependencies, contract/schema fixtures, common/JVM tests, Android host tests, Android lint, selected Kover verification, strict dependency verification/locks, configuration-cache store and reuse. |
| `quality-apple` | macOS Apple silicon | Compile/link the shared framework, run `iosSimulatorArm64Test` and Apple-specific adapter tests, run the iOS/Xcode host build, validate Apple lock/verification resolution, and prove configuration-cache store/reuse for the Gradle Apple task set. |
| `quality-android-device` | physical Android evidence lane when required by the story | Android device tests and camera/platform evidence; do not count them as Kover coverage. |
| `quality-ios-device` | physical iOS evidence lane when required by the story | iOS device/bridge evidence; do not substitute simulator output for AD-11 evidence. |

Keep `qualityCheck` as the host-neutral local entrypoint if desired, but define
host-qualified Gradle tasks such as `qualityCheckJvmAndroid` and
`qualityCheckApple`. The CI workflow, not one cross-host Gradle task, is the
aggregate. All applicable named jobs must be required checks.

Source: [Kotlin/Native target and host support](https://kotlinlang.org/docs/native-target-support.html).

### QT-4 — Medium: configuration-cache proof needs store and reuse semantics

Gradle does not enable the configuration cache by default. “Proves configuration
cache” could be satisfied by one storing run, warning mode, or a narrow task that
does not exercise the quality graph. None proves that the graph can be restored.

**Precise fix:**

1. Set `org.gradle.configuration-cache=true` and
   `org.gradle.configuration-cache.problems=fail` in the root
   `gradle.properties`.
2. For both `quality-linux` and `quality-apple`, run the exact same clean
   task-request twice in the same workspace/user home: the first must store a
   configuration entry; the second must reuse it.
3. Fail if either run discards the entry, reports a problem, or silently opts a
   required task out with `notCompatibleWithConfigurationCache`.
4. Include the included `build-logic` convention-plugin build in this proof.
   Root runtime configuration-cache properties apply to the whole composite
   invocation; custom convention tasks still need compatible inputs/services.

Sources: [Gradle configuration-cache enablement and problem modes](https://docs.gradle.org/current/userguide/configuration_cache_enabling.html),
[configuration-cache build-logic requirements](https://docs.gradle.org/current/userguide/configuration_cache_requirements.html),
[Gradle composite-build property behavior](https://docs.gradle.org/current/userguide/composite_builds.html).

### QT-5 — Medium: lock enforcement is not complete until every project resolves committed lock state

The root `gradle/verification-metadata.xml` placement is correct: Gradle uses the
current build's verification metadata across subprojects and included builds.
Dependency locking is different. It is enabled per configuration and stored per
project; `lockAllConfigurations()` does not lock buildscript classpaths, and a
root `dependencies --write-locks` invocation does not resolve every subproject.
The structural seed shows verification metadata but no lock-state pattern.

**Precise fix:**

1. Apply `dependencyLocking { lockAllConfigurations() }` through the appropriate
   root/convention policy to every Gradle project that resolves external
   dependencies, including projects in the included `build-logic` build.
2. Commit the resulting per-project `gradle.lockfile` files; document their
   expected paths in the structural seed or implementation guide.
3. Provide a dedicated, local-only lock refresh workflow that resolves every
   selected resolvable configuration on both Linux and macOS and runs only with
   `--write-locks`. Mark any resolution helper that inspects configurations at
   execution time as intentionally not configuration-cache compatible; it must
   not be part of `qualityCheck`.
4. CI must never write locks or verification metadata. Run with
   `--dependency-verification=strict`, resolve the same configuration sets, and
   fail on missing/stale lock or verification entries.
5. Review generated verification checksums/signatures before commit; generation
   records whatever artifacts are present and is not itself a trust decision.

Sources: [Gradle dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html),
[Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html).

### QT-6 — Medium: Kover scope must be explicit

Kover 0.9.8 is the current documented plugin and supports line and branch
verification for JVM-executed tests, including Android local unit tests and
common code compiled into a JVM target. It does not collect coverage from
Kotlin/Native, iOS tests, JS, or Android on-device instrumentation tests.
The Stack note acknowledges that Native evidence is separate, but AD-27's single
sentence can still be implemented as an impossible all-platform coverage claim.
Multi-module aggregation is also not automatic: the merging project must declare
`kover(project(...))` dependencies.

**Precise fix:**

1. State that Kover verification covers only JVM executions and Android host
   tests; it makes no Native or device-test coverage claim.
2. Choose one root or dedicated merging project and declare explicit `kover`
   dependencies on every covered production module.
3. Pin the exact report variant used by CI and configure both `LINE` and `BRANCH`
   bounds there. Avoid ambiguous unqualified task names that may run unrelated
   variants.
4. Run iOS/Native and Android device tests in the host/device lanes from QT-3 and
   publish their test evidence separately.
5. Treat any Kover exclusion as an AD-27 suppression requiring owner, reason,
   narrow scope, and removal condition.

Source: [Kover 0.9.8 Gradle plugin documentation](https://kotlin.github.io/kotlinx-kover/gradle-plugin/).

### QT-7 — Low: the formatting implementation is unspecified

AD-27 distinguishes formatting check from Detekt and gives `qualityFormat`
exclusive mutation authority, but the Stack does not pin the formatter artifact
or rule version. That permits the check task and autocorrect task to use different
engines or rules.

**Precise fix:** make the formatting checker one versioned ruleset from QT-1,
load one reviewed configuration under `config/detekt`, and derive both
`qualityCheck` (autocorrect disabled) and `qualityFormat` (autocorrect enabled)
from that exact ruleset. Add a CI assertion that `qualityCheck` leaves the
worktree clean.

## Confirmed-current portions

- Kotlin 2.4.10 officially fully supports Gradle 7.6.3–9.5.0 and AGP
  8.5.2–9.1.0, so the pinned Kotlin/Gradle/AGP tuple is coherent.
- AGP 9.1 requires at least Gradle 9.3.1; Gradle 9.5.0 satisfies that minimum.
- Compose Multiplatform 1.11.1 is current, supports Android API 21+ and iOS 14+,
  and is compatible with the latest Kotlin; the spine's API 23 and iOS 15 floors
  are within those bounds.
- Compose modules must also apply
  `org.jetbrains.kotlin.plugin.compose` at the Kotlin version; this should be
  part of the Compose convention plugin under AD-26.
- Dependency verification at the root correctly covers subprojects and included
  builds; the remaining gap is lock-state completeness and CI resolution scope.

Sources:
[Kotlin Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html),
[Kotlin Multiplatform compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html),
[AGP/Gradle compatibility](https://developer.android.com/build/releases/about-agp),
[Compose Multiplatform compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html),
[Compose compiler plugin](https://kotlinlang.org/docs/multiplatform/compose-compiler.html).
