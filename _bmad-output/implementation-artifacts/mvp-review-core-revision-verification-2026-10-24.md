# Core revision protocol verification

Scope: bounded AD-15 patch of `mobile/core/config` and its colocated tests only, implementing the shared revision-checking protocol for review R1/R2. The existing `PrivateDocumentStore` API remains unchanged. Android/iOS wrapper integration belongs to later, separately owned platform invocations. The approved spec's frozen block is unchanged.

Acceptance recorded before implementation: independent owners reject interleaved stale writes and deletion resurrection; explicit reload adopts the accepted document; a first save reads before writing; null and empty remain distinct; unsuccessful or throwing reads/writes preserve the prior expected revision; stale/error paths never invoke an unwanted writer. Callback failures, including cancellation, propagate. The caller must hold its existing shared platform document lock across each complete decorator operation.

Existing integration RED was executed by the parent before this invocation: API29 storage suite, 9 tests, 4 intended failures, 0 errors/skips, archived at `mobile/build/verification/mvp-audit/review-storage-red-api29/`. It is not this worker's runtime result and the old platform code will not be rerun here.

Planned verification: full strict `:core:config:jvmTest :core:config:koverXmlReportJvm :core:config:detekt`, followed in the same Gradle invocation by the parent's focused `:feature:photo-analysis:jvmTest --tests '*MealDiaryHomeModelTest'`. The feature test is intentionally expected to fail at 1,001 lookups versus 1; its fresh XML/log must be archived under `mobile/build/verification/mvp-audit/review-home-red/` before any subsequent run. Per-module counts and report freshness will be recorded after exact process closure. Native core compilation remains pending the parent's final SharedKit arm64 integration.

Status: bounded core implementation and JVM/lint verification complete. The combined invocation intentionally failed only on the parent's Home regression; this is not an overall green build. No ADB/device operations, Git operations, staging, commits, remote operations, policy changes, baselines or coverage exclusions were performed in this invocation.

## Implementation

Added public `mobile/core/config/src/commonMain/kotlin/com/supernutrition/core/config/RevisionCheckedPrivateDocumentStore.kt` with the exact `readDocument: () -> String?` and `writeDocument: (String) -> Boolean` callbacks. It implements the unchanged `PrivateDocumentStore` port. Each successful load records the exact observed content; each save reads actual content first, rejects a stale loaded owner before calling its writer, and advances the expected content only after a true write result. Null and empty are distinct. Callback exceptions and cancellation propagate without catches. KDoc requires the caller's existing shared platform lock around each complete operation; the helper does not claim its own cross-owner synchronization.

Added 11 meaningful common tests in `RevisionCheckedPrivateDocumentStoreTest.kt`: interleaved owners and explicit reload, deletion without resurrection, null/empty distinction, unobserved first-save read ordering, failed first read, failed reload, failed save read without writer invocation, rejected observed write and retry, rejected first write without adopting an observation, throwing writer preservation, and read/write cancellation propagation. Existing tests and production port source are unchanged. Static self-review checked the state-update ordering and absence of catches; this is not a new independent subagent review.

## Exact verification and closure

Executed from the repository root with Android Studio JDK and the requested Xcode developer directory:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
DEVELOPER_DIR='/Applications/Xcode-26.6.0.app/Contents/Developer' \
./mobile/gradlew -p mobile \
  :core:config:jvmTest \
  :core:config:koverXmlReportJvm \
  :core:config:detekt \
  :feature:photo-analysis:jvmTest --tests '*MealDiaryHomeModelTest' \
  --dependency-verification=strict --continue --no-daemon \
  --no-configuration-cache --max-workers=1 --console=plain
```

Tool process `32903` closed with exact exit **1**, **2m54s**, **59 actionable tasks: 29 executed, 30 up-to-date**. Its only failed task is `:feature:photo-analysis:jvmTest`, the deliberately requested RED. The exit was captured directly from Gradle before printing the log tail; no pipeline exit was substituted.

| Module / check | Fresh result |
| --- | --- |
| core/config JVM | 6 suites, 47 tests, 0 failures, 0 errors, 0 skips |
| New revision helper suite | 11 tests, 0 failures, 0 errors, 0 skips |
| Core Detekt | Four fresh XML reports, 0 findings each: commonMain, commonTest, mainJvm, testJvm |
| Helper Kover XML | 13/13 executable lines, 6/6 branch outcomes, 49/49 instructions; no missed helper lines/outcomes |
| Entire core/config Kover | 395 covered / 7 missed lines; 261 covered / 21 missed branch outcomes; existing unrelated misses remain visible |
| Feature focused Home RED | 1 suite, 3 tests, 1 intended assertion failure, 0 errors, 0 skips |

Core test XML timestamps span **2026-10-24T13:03:32.531Z–13:03:34.309Z**; file mtimes span **13:03:35.028505Z–13:03:35.037308Z**. Kover XML mtime is **13:03:36.112918Z**. Core Detekt XML mtimes span **13:03:46.298420Z–13:04:15.686783Z**. Every quoted file is newer than this run's log creation at **13:01:54.573165Z**. Core production/test compilers, JVM tests, Kover and all relevant Detekt tasks executed in this invocation.

The feature XML timestamp is **13:04:46.048Z**, mtime **13:04:48.649556Z**. Its sole failure is `home resolves only the requested day instead of the retained historical catalog meals`, at `MealDiaryHomeModelTest.kt:20`, with exact message `expected:<1> but was:<1001>`. This is an actual executed product assertion RED against unchanged production Home, not a compile/setup failure or an inferred stale report. This worker did not edit feature source/tests.

## Preserved evidence and remaining integration

Full combined log: `mobile/build/verification/mvp-audit/review-core-revision-home-red.log`.

Before any subsequent run, copied the fresh feature XML and full log into `mobile/build/verification/mvp-audit/review-home-red/`; its `summary.json` carries counts, timestamps and the exact assertion message. Copied all six core test XML files, the full core Kover XML and all four fresh core Detekt XML files into `mobile/build/verification/mvp-audit/review-core-revision/`, with a per-module/coverage `summary.json`. No stale reports contributed counts.

No core repair/rerun was necessary: this invocation produced no core test, compilation or lint findings. No denominator, floor or policy was changed. Native core compilation is **pending** the parent's final SharedKit arm64 integration. Android/iOS wrapper integration, Home repair/green verification, final graph refresh and final repository diff check remain with their separately assigned parent/platform invocations. The API29 emulator stayed paused; no device was accessed.

The Gradle process is closed and no build remains active from this worker. **Gradle mutex released to the parent** after this evidence archive and report were complete. This worker now holds its source slice; no further build or edit is planned without another assignment.
