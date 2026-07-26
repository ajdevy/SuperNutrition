# Current-Technology Reality Review — NutriArch Architecture Spine

- **Reviewed:** `ARCHITECTURE-SPINE.md`
- **Lens:** Current versions, official compatibility, platform floors, adapter fit, and repository reality
- **Review date:** 2026-07-26
- **Verdict:** **Conditional pass — the proposed stack is current and internally coherent, but it is not yet repository-proven, and the iOS 14 floor does not align cleanly with the selected Xcode real-device evidence workflow.**

## Findings

### High — The stack is proposed documentation, not current repository reality

The repository contains no Gradle wrapper, version catalog, Gradle build scripts,
or Xcode project. Therefore none of the versions in `## Stack`, none of the
Android/iOS adapters, and none of the named module boundaries have been resolved
or compiled locally. This matters because the spine describes the tuple as a
commitment while AD-11 requires real Android/iOS evidence.

**Required fix:** Treat the stack table as a verified proposed tuple until a
bootstrap story creates and pins the wrapper, version catalog, Android build,
and Xcode host. Make the bootstrap exit gate a clean Android build, iOS
framework/link build, shared tests, and one minimal Compose screen on physical
Android and iOS devices.

### High — The advertised iOS 14 floor conflicts with the selected real-device toolchain workflow

The Deferred section correctly reports Compose Multiplatform's library floor as
iOS 14. However, Apple states that Xcode 26.4 supports on-device debugging on
iOS 15 and later. AD-11 simultaneously requires real-device evidence for camera,
performance, memory, and local inference. An iOS 14 product minimum would
therefore need a separate installation/test strategy and cannot be assumed from
Compose compatibility alone.

**Required fix:** Set the provisional product/development floor to iOS 15 or
higher, or explicitly define how release builds and physical acceptance will be
executed on iOS 14 without relying on Xcode 26.4 on-device debugging. Keep iOS 14
as a framework capability statement, not a supported-product claim, until that
evidence exists.

Sources: [Compose supported floors](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html),
[Xcode 26.4 release notes](https://developer.apple.com/documentation/Xcode-Release-Notes/xcode-26_4-release-notes).

### Medium — The bootstrappable toolchain envelope is incomplete

AGP 9.1 requires JDK 17 and names its Android SDK/Build Tools defaults; Xcode
26.4 requires macOS Tahoe 26.2 or later. The stack commits AGP and Xcode but
omits JDK, compile SDK/target SDK, Build Tools, Android Studio, and macOS host
requirements. Two implementation owners could follow the spine and still use
incompatible development or CI hosts.

**Required fix:** Add these to the stack or to a mandatory environment seed:
JDK 17, selected compile/target SDK, Build Tools, Android Studio channel, macOS
host floor, and Xcode selection mechanism. Pin them in CI/bootstrap reality,
not only prose.

Sources: [AGP 9.1 compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes),
[Xcode 26.4 requirements](https://developer.apple.com/documentation/Xcode-Release-Notes/xcode-26_4-release-notes).

### Medium — The stack is a compatibility-ceiling tuple and must not be upgraded independently

Kotlin's current KMP compatibility table verifies Kotlin 2.4.10 with Gradle only
through 9.5.0, AGP through 9.1.0, and Xcode 26.4. Gradle 9.6 and AGP 9.1.1 exist,
but are outside the exact tuple listed in that table. The spine's Gradle 9.5.0
and AGP 9.1.0 pins are therefore defensible compatibility choices rather than
stale versions. AD-15 already restricts version churn, but the reason is not
visible in the stack.

**Required fix:** Label Kotlin/Gradle/AGP/Xcode as one verified compatibility
tuple and require a full Android+iOS build matrix for any member upgrade. Do not
automatically patch AGP or Gradle based only on a newer release being available.

Sources: [KMP compatibility table](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html),
[Gradle 9.5 release notes](https://docs.gradle.org/9.5.0/release-notes.html),
[AGP 9.1 release notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

### Medium — Decompose compatibility is plausible but not yet proven against the selected Compose/Kotlin tuple

Decompose 3.5.0 is the current stable release and supports Android, iOS, and
Compose Multiplatform. Its official release declares that it was built with
Kotlin 2.1.0 and Compose Multiplatform 1.9.3. This satisfies Compose's stated
minimum dependency baseline, but does not prove the exact Kotlin 2.4.10 /
Compose 1.11.1 integration used here. With no build in the repository, the
navigation/lifecycle choice remains unverified.

**Required fix:** Include Decompose lifecycle, state preservation, back
handling, and iOS linking in the bootstrap compatibility test. Retain 3.5.0
rather than adopting the available 3.6 alpha unless a separate decision accepts
pre-release risk.

Sources: [Decompose 3.5.0 release](https://github.com/arkivanov/Decompose/releases/tag/3.5.0),
[Compose dependency compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html).

## Verified stack and fit

| Commitment | Reality check | Judgment |
| --- | --- | --- |
| Kotlin/KMP 2.4.10 | Current stable release; official KMP table covers the selected Gradle, AGP, and Xcode tuple. | Verified current |
| Compose Multiplatform 1.11.1 | Current official documentation; compatible with current Kotlin. Android API 21 and iOS 14 are framework floors. | Verified current; floor caveat above |
| Gradle 9.5.0 | Released and within Kotlin 2.4.10's supported range. A newer Gradle exists, but exceeds the official KMP ceiling. | Verified compatible pin |
| AGP 9.1.0 | Within Kotlin 2.4.10's supported range; requires JDK 17. A newer patch exists but is not the exact version in the KMP table. | Verified compatible pin |
| Xcode 26.4 | Official Kotlin-supported baseline; requires macOS 26.2+. | Verified; host/test caveat above |
| Ktor 3.5.1 | Current official release with Kotlin 2.4 compatibility fixes; OkHttp on Android and Darwin/NSURLSession on iOS are supported client engines. | Verified current and fit |
| SQLDelight 2.3.2 | Current stable release supports Android and Kotlin/Native/iOS. The published Android driver AAR declares minSdk 23. | Verified current; Deferred floor statement is correct |
| Koin 4.2.2 | Published current Maven release; 4.2.x supports Kotlin 2.3+ and KMP/Compose. Official narrative release pages lag at 4.2.1. | Artifact verified; compile proof still required |
| Decompose 3.5.0 | Current stable; supports Android, iOS, and Compose. | Verified stable; tuple proof required |

Primary sources:

- [Kotlin releases](https://kotlinlang.org/docs/releases.html)
- [KMP compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [Compose compatibility and platform floors](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Ktor releases](https://ktor.io/docs/releases.html)
- [Ktor client engines](https://ktor.io/docs/client-engines.html)
- [SQLDelight official repository](https://github.com/sqldelight/sqldelight)
- [SQLDelight Android driver 2.3.2 artifact](https://repo1.maven.org/maven2/app/cash/sqldelight/android-driver/2.3.2/android-driver-2.3.2.aar)
- [Koin setup and compatibility](https://insert-koin.io/docs/setup/)
- [Koin Maven metadata](https://repo1.maven.org/maven2/io/insert-koin/koin-core/maven-metadata.xml)
- [Decompose releases](https://github.com/arkivanov/Decompose/releases)
