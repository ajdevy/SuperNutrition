# Durable local diary and USDA verification

Status: implementation and emulator verification complete; independent local review pending. Required expired physical evidence still blocks the complete quality gate. This file supersedes the
previous MVP report only for the D-07/D-08 continuation; it is not a release or
hosted-CI success claim.

## Candidate and authority

- Worktree: `/Users/REDACTED/.codex/worktrees/f2ff/SuperNutrition`.
- Branch: `codex/mvp-completion`; baseline `7309e140b9cf12c6acf5733cfdcec00ce5912f1b`.
- D-07 explicitly approves private local meals/name across launches until user
  deletion; D-08 explicitly approves USDA SR Legacy 2018-04, US coverage, exact
  product/grams confirmation and unknown missing values. Both are recorded in
  `.evidence/OWNER-DECISIONS.md` before implementation. Strict verification passed
  for all eight decision entries.
- Product roots and unrelated worktrees are preserved. No push, merge, release
  upload or remote activation is part of this local continuation.

## Verification boundaries

- Android evidence uses `emulator-5554`, Android 15/API 35, x86_64,
  720×1560/320dpi (360×780dp). This is not physical-device evidence.
- Host is Intel x86_64 macOS 15.8.1 with Xcode 26.6/build 17F113 selected per
  command. Arm64 compilation can run; the primary Compose iOS renderer cannot
  execute in an Intel simulator. No physical iPhone is connected.
- Required `bootstrap-android-host` physical evidence is expired. Do not renew,
  fabricate or bypass it. The root quality gates cannot be called green while
  that policy fails. Their coverage verifier dependency also does not execute;
  direct Kover XML observations must be described separately.
- Website retention changes are prepared in
  `mvp-retention-website-handoff-2026-10-24.md`, not published. The website must
  change through `flexsent-labs/supernutrition.app` and its reproducible GitHub
  build when this mobile behavior is publicly released.

## Regression evidence collected so far

- Retention: the existing profile Compose test was executed before the fix and
  failed specifically on Clear→Save (8 tests, 1 intended failure). A later
  focused run passed core/config 36, presenter 43, feature 74 and UI 270 tests,
  all with zero failures/errors/skips. Presenter policy changed subsequently,
  so these are intermediate observations, not final-candidate evidence.
- Source pin: a real negative importer fixture first failed because a wrong
  archive was accepted, then all four importer tests passed after the pin fix.
  Six bundled-generator tests passed. The original TSV and new numeric payload
  reproduced byte-for-byte from the pinned upstream archive.
- Resolver: a real unavailable source threw `IllegalStateException` before the
  boundary fix (6 tests, 1 intended failure). Ordinary source failures now map
  to unknown while cancellation still propagates; final rerun remains required.
- SQL activation: actual SQLDelight schema generation and JDBC tests ran under
  strict dependency verification. The cleanup-cancellation fixture executed
  before its fix (12 tests, 1 intended failure): a close failure masked the
  original cancellation. Final rerun remains required.

Detailed retention evidence is in `mvp-retention-verification-2026-10-24.md`.
All final module XML counts, native results, coverage counters, artifact hashes,
screenshots and command outcomes will be added after stable integration.

The completed backbone JVM rerun (`catalog-focused-jvm-final.log`, exit 0) has fresh
module XML: domain/nutrition **295** tests and data/nutrition **20** tests, both
with zero failures/errors/skips. Domain Kover XML observes **12,149/12,150** lines
(99.99177%) and **548/548** branches (100%); the one missed line is the previously
verified default-method instrumentation artifact. Data observes **362/374** lines
(96.79144%) and **76/76** branches (100%). Every authored data line/outcome is
covered; twelve emitted SQLDelight listener/diagnostic lines remain in its
denominator. No coverage exclusion or threshold reduction was introduced.
These are module XML observations, not an executed root coverage-verifier result.
See `mvp-catalog-backbone-verification-2026-10-24.md` for exact commands and logs.

## Generated lint policy follow-up

The exact SQLDelight output-folder rule in `QualityPlugin.kt` was changed only
for `:data:nutrition` and only for
`build/generated/sqldelight/code/UsdaCatalogDatabase/commonMain`. Authored `src`
files (including the immutable package generator's Kotlin output) remain checked.
No coverage source was excluded. The original typed/source-set lint failures and
their exact counts precede the fix in the backbone report.

Actual full alias rerun: `:data:nutrition:detekt --dependency-verification=strict
--no-daemon --no-configuration-cache --max-workers=1 --continue --console=plain`.
Exit 0 in 6m35s; 23 tasks (16 executed, 7 up-to-date). Both untyped authored
source checks and typed MainJvm/TestJvm checks completed. The alias itself is
NO-SOURCE with real dependencies; its dependent checks are the evidence.
Log: `mobile/build/verification/mvp-audit/catalog-generated-lint-policy-green.log`.

## Android sidecar regression in progress

The new real-filesystem fixture uses a regular old base file and a nonempty
legacy `.bak` directory. AOSP's log-only rename failure can let the subsequent
new base bytes compare equal while the failed backup remains. Acceptance requires
a reported failure, preservation of the old base bytes and obstructing backup,
and no `.new` residue. The existing base-directory replacement-failure fixture is
retained. [AOSP implementation](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/util/AtomicFile.java)
supports the failure hypothesis; the native assertion, not source inspection,
will decide it. Do not add a post-start `.bak` rejection that breaks the older
backup-writing implementation on supported Android versions.

The first native RED attempt started at 2026-10-24T06:46:42Z on the single
emulator-5554 endpoint. Only the four isolated private-document tests are selected;
this is not a full instrumentation-suite result. All six owned target/test
packages were stopped and no active instrumentation remained before starting.

The executed RED completed: 4 tests, 1 failure, 0 errors/skips. The new
`anObstructedLegacyBackupCannotReplaceThePreviousValue` failed at its `assertFalse`
on line 114 because the adapter returned true. The original three tests passed.
The command exited 1 in 10m55s after compiling/packaging the changed Android code;
this is an actual native assertion failure, not a compile/setup result. Preserve
the RED log and copied XML `private-store-sidecar-red.xml` before the green run.

After the narrow Android adapter fix, the exact production connected task ran
again with private-document and existing image-normalizer class filters: **7
tests, 0 failures/errors/skips**, exit 0 in 3m05s. This includes all four storage
cases and all three native normalization cases. The new corrupt-backup fixture
now confirms both failure and preservation of the old bytes. The preexisting
uncommitted-replacement and Unicode/empty/oversize fixtures pass too. Photo and
catalog verification now share the platform `androidSha256` helper; no crypto
algorithm was added or copied.
Log/XML: `private-store-sidecar-green.log` / `private-store-sidecar-green.xml`.
Full unfiltered three-variant execution remains required after integration.

## Apple storage compilation finding before its fix

The first explicit platform storage compile (source-set proof enabled) failed at
`IosPrivateDocumentStore.kt:66`: unresolved `writeToURL`. Foundation category
methods require their Kotlin extension import on this toolchain. This is an
actual `iosMain` compiler failure; no native tests compiled or ran in that
attempt. The source proof independently confirms that `iosMain` is in the arm64
main compilation and `IosPrivateDocumentStoreTest.kt` is in its iosTest graph.
Log: `ios-storage-compile.log`, exit 1 in 3m34s. Fix the native interop reference
and rerun both main and test compilation before claiming Apple build evidence.

The corrected platform build (`ios-storage-compile-v2.log`) completed with exit 0
in 1m58s: both `:platform:photo-ios:compileKotlinIosArm64` and
`:platform:photo-ios:compileTestKotlinIosArm64` executed successfully. The guard
printed main source sets `[iosArm64Main, iosMain, appleMain, nativeMain,
commonMain]` and the analogous test hierarchy, explicitly listing
`IosPrivateDocumentStoreTest.kt` and the two existing test files. Existing
CommonCrypto SHA-256 is now public for composition-root reuse, with its cinterop
opt-in retained. No iOS test execution or app-level Swift/link result is claimed
by this platform compilation.

## Additional integration finding recorded before its fix

`RewardDetailsScreenCopy.noFoodsDescription` currently says nutrition and food
groups are unavailable for saved meals. Once explicit USDA bindings expose
nutrition, that wording becomes false even though group-based rewards correctly
remain uncredited. USDA supplies none of the reward contract's protein/group,
fermentation or herb/spice classifications. Keep the existing v1 reward contract
and avoid inferring these facts from names or positive protein. Update the empty
reward explanation to distinguish unavailable classification from available
nutrition and retain the independent diary-recording reward.

The existing identity matcher also uses `GeneratedRussianForms.kt` and
`GeneratedRussianScaffolding.kt`, whose headers identify OpenCorpora dictionary
0.92/rev417150 via pymorphy3 2.0.6 and pymorphy3-dicts-ru 2.4.417150.4580142.
The initial structured catalog acknowledgements omit that source. Add its
separate credit, changed-subset notice and source/licence links alongside the
other search credits; do not label these forms CC0 or conflate package MIT
metadata with the dictionary's published CC-BY-SA terms. The
[OpenCorpora FAQ](https://opencorpora.org/?page=faq) and
[downloads page](https://opencorpora.org/?page=downloads) publish CC-BY-SA 3.0.
The generated headers already leave the exact redistribution/share-alike
assessment to counsel before release. Preserve that release prerequisite;
adding attribution does not settle it or approve public distribution.

## Host interop reconnaissance

Read-only `javap` of the actual Maven Central Android SQLDelight 2.3.2 AAR
confirms a public three-argument constructor
`AndroidSqliteDriver(SqlSchema<QueryResult.Value<Unit>>, Context, String)`.
The Java composition root can therefore construct the in-memory catalog with
the existing schema, application context and a null database name without
guessing Kotlin default-argument masks or adding a Kotlin host source set.
The downloaded AAR/classes are ignored verification artifacts; this is API
inspection, not an executed Android database/host integration test.

The Native driver's official Maven Central 2.3.2 source artifact was separately
checked against its published SHA-256
`7c6db9f5dc6289bf765fef23b2169ac84aac3a5608de60ef97244d4e67d7b19c`.
It confirms the public `inMemoryDriver(schema)` helper creates an in-memory
driver and calls `schema.create` through its create callback; app roots must not
create that schema a second time. Published Native metadata includes iosArm64,
iosSimulatorArm64 and iosX64 variants. Its direct metadata dependencies include
SQLiter driver 1.3.3 and Stately concurrency 2.1.0 (which also declares Stately
strict 2.1.0). These are read-only dependency/API observations, not resolved host
locks, strict-verification pins or target compilation. Cached source/metadata
are under `mobile/build/verification/mvp-audit/driver-metadata/`; no build policy
was changed by this reconnaissance.

## Durable binding format regression

The presenter-only RED executed the old `MealDiaryCodec.decode` API against a
complete valid SNMD2 single-product document. It failed specifically because
the decoder returned `UNSUPPORTED_VERSION` instead of `Success`: 1 test,
1 failure, 0 errors/skips, exit 1 in 2m23s. The immutable RED log/XML are under
`mobile/feature/photo-analysis-presenter/build/verification/catalog-presenter-red.*`.
The new writer uses SNMD2 while the reader retains bounded, all-or-nothing
SNMD1 support. Full module tests and coverage are still running; this paragraph
records the executed negative fixture rather than claiming a final pass.

The first complete presenter run executed 69 tests with zero failures/errors/
skips, then exited 1 on four read-only lint tasks (1 main and 15 test findings,
each reported by both source-set and typed checks). Its log ends with
`BUILD FAILED in 4m23s`. The implementation worker then became unavailable due
to the service usage limit. The parent preserved those results, applied the
documented formatting/nullable-branch corrections in the presenter module and
started the full strict rerun. This is implementation handoff evidence, not an
independent completed review.

The complete strict presenter v2 rerun exited 0 in 3m40s: JVM tests, Kover XML,
both authored source-set lint checks and both typed checks executed. Fresh XML
contains 69 tests in 12 classes, zero failures/errors/skips (oldest XML
2026-10-24T07:30:25Z). Coverage observes 641/656 lines and 353/373 branches;
the corrected changed validator line has 0 missed / 2 covered outcomes. The
new catalog/codec paths are covered; preexisting unrelated gaps remain visible.
Log: `mobile/feature/photo-analysis-presenter/build/verification/catalog-presenter-v2.log`.

A subsequent narrow `core/ui` change adds fixed, locale-neutral chooser/source
and energy automation IDs to the existing unique registry. Existing positional
meal-search row IDs will be reused for chooser results. No business logic or
new mirror tests were added; the existing registry test and UI/native flows
will verify this low-impact contract in the upcoming relevant runs.

## Search-data attribution regression

The data-only OpenCorpora RED compiled and executed one provenance test. It
failed exactly on the missing dictionary credit (1 test, 1 failure, zero errors/
skips; exit 1). After adding the separate structured credit and preserving the
README's unresolved counsel-before-release prerequisite, fresh full data XML
has 21 tests, zero failures/errors/skips. Kover observes 367/379 lines and 76/76
branches; all authored lines/outcomes are covered and the same twelve emitted
SQLDelight lines remain visible. All 28 protected numeric/package/lock/verification
hashes are unchanged; the generator's 7,793-row check matches. Read-only Detekt
is still running, so these observations do not yet claim an overall green run.

The completed full data rerun exited 0 in 3m54s, including the full read-only
Detekt alias. The 21-test and 367/379-line, 76/76-branch observations above are
the final module results for this slice. Exact commands and copied RED/green/
coverage/lint XML are retained in
`mobile/data/nutrition/build/verification/opencorpora/summary.json` and
`summary.md`. Only provenance, its common test and README changed; the verified
numeric package and dependency pins stayed unchanged. All run sessions closed.

## UI regressions before arithmetic/chooser integration

- The existing `MealForm` compiled with the new error enum copy and then failed
  its absent-chooser regression: 1 test, 1 failure, zero errors/skips. The runtime
  could not find `meal_catalog_open`; XML timestamp 2026-10-24T07:46:46Z,
  `chooser-red.log`, exit 1 in 5m56s.
- The separate existing-v1 `TinyServingNutritionTest` compiled and executed with
  a valid all-zero nutrient record/portion and `grams=1e-308`. It failed because
  `nutrition(true)` returned null instead of known zeros: 1 test, 1 failure,
  zero errors/skips; XML timestamp 2026-10-24T07:51:47Z, exit 1 in 3m22s.
  No production arithmetic changed before this RED. The new shared domain sum/
  per-100g helpers will own that math; the UI will consume them after the
  narrowly owned domain/presenter invocations complete.

UI logs are under `mobile/build/verification/durable-ui/`. The UI worker is
holding writes/builds during these dependencies; no Gradle builds overlap.

The canonical arithmetic domain slice completed (exit 0, 6m51s): 306 tests,
zero failures/errors/skips; full Detekt alias has zero findings. XML observes
12,177/12,178 lines and 574/574 branches, including every new arithmetic line
(28/28) and outcome (26/26). The sole missed line is the established default-
method instrumentation artifact. The 99 protected existing source/boundary/lock
hashes stayed unchanged. Frozen v1 and `PartialNutrientProfile.scaledToGrams`
were preserved. Evidence: `mobile/domain/nutrition/build/verification/partial-arithmetic/summary.json`.

UI date filtering must retain null contributing portions when calling
`aggregatePartialNutrients`, rather than filtering unknown meals away.
`per100GramsFromPortion` supplies canonical normalization. Storage load failure
must also force daily totals unknown even if another projection has some known
foods, because the complete day's diary cannot be established.

The structured validator seam is complete: the first full run passed 79 tests
and failed only the documented test formatting. After the narrow layout fix,
the full strict rerun exited 0 in 1m57s. Fresh XML has 79 tests, zero failures/
errors/skips; Kover observes 650/665 lines and 355/375 branches. The new overload
covers every new line (9/9) and outcome (2/2), retaining preexisting unrelated
gaps. Both source-set and both typed lint checks are clean; unchanged production
checks legitimately reused the successful first-run inputs, while test checks
regenerated. The existing 30 module source hashes stayed unchanged except the
owned validator/new test. Evidence:
`mobile/feature/photo-analysis-presenter/build/verification/structured-draft/summary.json`.
The public three-explicit-argument overload accepts `ValidatedMealDraft`, optional
existing record and optional current approved catalog. It reuses field/catalog/
historical normalization while passing the original ingredient list untouched.
UI work resumed only after all these build sessions closed.

## Remaining sequential integration at this checkpoint

Coverage-policy clarification: typed TOML inspection confirms feature UI and
feature host are existing `native-equivalent` denominators, and the actual
QualityPlugin numeric selector filters strictly to kind `jvm`. Their direct
Kover shortfalls below are supplemental diagnostics, not an additional numeric
gate failure. Keep the raw observations and never claim 100% raw branches.
The required physical Android equivalent records are still expired; human
approval to verify only on emulator is already recorded. No classification,
threshold, denominator or evidence entry was changed to obtain this result.
The legacy dead-renderer cleanup may be separate follow-up rather than broaden
this MVP solely to satisfy a non-applicable numeric gate. Current meaningful
feature cancellation/error/recovery tests remain in scope.

The Ready-source retry regression also executed before repair: 66204, exit1
in 1m36s, one test/one failure/zero errors/skips (UTC 09:17:22.162), expected
two factory attempts but observed one. Afterward the session was changed to
stage a candidate from Ready, retain the previous handle through failed/null/
throwing/getter/cancelled attempts, close it after a verified replacement, and
leave Loading on any unsuccessful cancellation. Tests cover failed search,
replacement timing, three failure kinds, retry after cancellation and late
replacement disposal. The full feature v2 run is active; no pass is claimed.

Feature integration executed two genuine regressions before their fixes:
binding RED 57193 (one test/one failure/zero errors/skips, 1m49s,
UTC 09:07:06.029) saved a valid selection but lost its binding; ownership
RED 15654 (one test/one failure/zero errors/skips, 1m33s, UTC 09:09:19.420)
retained a throwing-getter candidate and closed it zero times instead of once.
The feature worker then centralized raw/structured write validation and binding
copying, obtained the candidate catalog before ownership transfer, wired the
retained Android source after composition and existing iOS bridges, and added
deterministic cancellation/retry/lifetime/migration/Home tests. Its complete
module verification is next; these observations do not yet claim it passes.

UI v5 is now CLOSED, exit 0 / 8m21s: 298 tests / 75 suites, zero failures,
errors or skips; core UI's four tests / one suite remain legitimately
UP-TO-DATE with unchanged inputs. Actual JVM and Android compilers passed.
Fresh commonMain/commonTest/mainAndroid/mainJvm/testJvm lint XML all have zero
issues (UTC 09:00:18–09:03:17); the same six baseline flattened-KMP typed
analysis warnings remain on each main task. Old `format.xml` and `iosMain`
reports are explicitly not evidence from this run. `git diff --check` passed.

Fresh v5 Kover (UTC 08:59:47) observes 339/339 changed lines and 222/252 raw
changed outcomes, and 5,967/6,686 module lines / 1,955/2,770 module branches.
Both the 100% raw changed-branch target and 85% raw module-branch floor remain
UNMET. Thirty missed outcomes map to changed lines, 785 to unchanged lines;
there is no preserved pre-phase baseline XML to claim a measured regression.
No exclusions, suppressions, masks or policy waivers were introduced. Exact
source/XML/javap evidence, full log, public host APIs and file inventory are
under `mobile/build/verification/durable-ui/` (`*-v5.txt`, `ui-full-v5.log`).
The separate feature-module integration invocation started only after this
UI build closed. A later bounded UI cleanup must inspect/remove only proven
unreachable legacy rendering code and rerun actual coverage; zero coverage
alone never authorizes removal or classification as generated.

UI v4 subsequently executed 296 tests / 74 suites with zero failures/errors/
skips and 340/340 changed executable lines; raw changed outcomes are 221/252.
The full run exited 1 in 8m22s only for two documented test blank-line lint
findings. Both actual JVM and Android compilation passed. Unreachable internal
NAME-dialog code was deleted, retaining the inline name editor/public APIs.
The two style fixes, the final unused internal default removal and meaningful
latest retry/link-handler tests are now in the final UI v5 run. No root Gradle
build overlaps it. The strict raw changed-branch target remains explicitly
unmet until an actual report proves otherwise.

The UI v3 full strict run subsequently exited 0 in 3m40s. Fresh UI XML has
291 tests, zero failures/errors/skips; core UI has four passing registry tests.
Kover now exists and audits 334/340 changed executable lines and 214/260 raw
branch outcomes against 7309 plus the untracked UI sources. The module run is
green, but the changed-code requirement is not yet met. Meaningful missing
catalog-error, legacy-overflow, profile persistence/IME and fallback cases are
recorded before follow-up changes. No Compose outcome is excluded or marked
covered by assertion; any remaining compiler-only claim requires source/XML
and bytecode evidence and preserves the actual raw denominator.

1. The presenter validator, canonical arithmetic and UI implementation/tests
   are complete at the module-run level. UI coverage deficits above remain
   explicit required follow-up; they are not a full quality pass. The genuine
   chooser/tiny-serving regressions and all 298 UI tests pass.
2. In `feature/photo-analysis`, add the cheap catalog factory/owned-handle/session
   port, explicit startup/retry/disposal and central validation on every raw and
   structured diary write. Copy the binding on add/update, preserve V1 bytes on
   load, and change the old unsupported-version fixture from SNMD2 to SNMD3.
   A session owned by the diary/local-data composition can expose its state to
   existing Home/Profile/capture bridges without growing their five-argument
   APIs. Keep `LocalUserData`'s stores lambda last for source compatibility.
3. Android source ownership must survive activity recreation and close on
   ViewModel disposal. Create/index off Main after rendering; capture the
   unpublished candidate inside the background block so prompt cancellation
   cannot lose it. Production storage stays private; other constructors stay
   isolated. Add concrete SQLDelight factories only in app roots.
4. Add the Android Java app factory/driver dependency plus actual native catalog
   tests and normal regular `.bak` recovery compatibility. Add the Native app
   factory/driver dependency in SharedKit, retaining one session across its
   surfaces. Swift production ownership starts it after host creation and closes
   it on owner disposal, not on a modal Home disappearance.
5. Resolve only required host driver locks/verification pins at the frozen
   versions, preserving snapshots of existing changes. Include available
   Apple-Silicon artifact metadata/pins without faking an Intel target build.
   Fix an SQLite linker setting only from actual Xcode link evidence.
6. In a separate quality-policy slice, remove the new feature-host→data-nutrition
   permission if it remains unused; preserve app→data edges. Refresh actual
   inventories/observations/digests with typed tooling, without changing expired
   physical records or thresholds.
7. Run final local gates/arm64 framework and test compilation, actual Xcode
   production and XCTest links sequentially, full three-variant Android native
   suite, final APK install/hash/process-restart/edit/delete/clear checks,
   360×780/200% checks and inspected screenshots. Then graph update, workflow
   review/fixes/reverification and scoped local commit. Remote operations remain
   outside this continuation.


Feature final v4 is CLOSED, session 22719 exit 0 in 2m31s. Fresh XML has 101
tests, zero failures/errors/skips; eight read-only Detekt reports have zero
issues. Actual JVM/Android compilers passed. Supplemental native-equivalent
Kover is 499/522 lines and 265/314 branches. New common files are fully measured:
MealCatalogSession 39 lines/30 branches, MealDiary 83/40, LocalUserData 10/0,
MealDiaryHomeModel 6/2. The remaining 23 lines/49 outcomes are in unchanged source.
V3's preceding test compile failed before tests, then the exact recorded generic
list type was corrected; no v3 test pass is inferred. All feature builds are now
closed and feature source is HOLD. Native/arm64 integration remains pending.

Dependency reconnaissance correction: only the feature module selects a separate
Linux lock. The data module uses its normal committed lock on all hosts, so the
absence of data/nutrition/gradle-linux.lockfile is not a defect. No Linux VM was
started. Apple-Silicon new-driver pins/lock scopes remain a reproducibility task,
without a claim of Apple-Silicon execution.


Android app composition runtime RED is confirmed before the factory fix:
session 53412 exited 1 in 3m03s after successful production/test compilation
and emulator installation/execution. Fresh production XML UTC 09:36:06 has
1 test, 1 failure, 0 errors, 0 skipped; BootstrapHostInstrumentationTest.java:18
fails “The production application must provide the bundled catalog factory”.
The raw log and copied RED XML are under
mobile/apps/androidApp/build/verification/catalog-integration/.
The app-only factory/provider and explicit real-driver fixtures are now being
implemented; this RED does not imply a final app result.


Android app source handoff is stable. The subsequent strict Java compile attempt
98444 is CLOSED exit 1 in 1m16s before app/test Java compilation (four build-logic
tasks UP-TO-DATE). Configuration-cache serialization reports the underlying
unrefreshed production runtime lock: SQLDelight android-driver/runtime/runtime-jvm
2.3.2 and AndroidX sqlite/sqlite-android/sqlite-framework/sqlite-framework-android
2.6.2 are missing from that lock. This is dependency-policy preparation, not a
product-test failure or green compile. No lenient verification was used. New
source and native fixtures await lock/pin refresh and actual execution.

A separate sharedKit app-root invocation now selects the Native driver and
explicit root session lifecycle. No builds overlap the closed Android invocation.


Read-only driver artifact reconnaissance verified 30 candidate artifacts from
11 new coordinates, before modifying pins: seven Maven Central module files
matched their published SHA-256; four Google Maven module files matched separate
fresh HTTPS downloads; nineteen binaries/metadata jars matched SHA-256 values in
those verified module files. Includes real iOS arm64 and Simulator Arm64 artifacts.
Detailed URLs, hashes and methods are in the ignored
mobile/build/verification/mvp-audit/driver-pins/recon.json. No policy pins/locks
were changed by this download and no foreign-host compilation is claimed.


SharedKit app-root source is stable with public Main-only startCatalog() and
closeCatalog(), one retained MainScope/session, background opening, real Native
in-memory SQLDelight factory, production-only durable composition and compiled-
fixture source for golden/lifetime/default-isolation behavior. The first strict
actual arm64 main/test request 7660 is CLOSED exit 1 in 1m26s (60 tasks: 45
executed/15 UP-TO-DATE). Seventeen prerequisite tasks reject the same four new
unregistered NativeDriver/SQLiter .module files. Neither main/test Native compiler
ran, and there is no Native test execution/RED claim. The verified candidate
pins already exist in the parent's read-only reconnaissance; a separate policy
slice will register them and refresh locks, then rerun actual compilation.


Swift root lifetime wiring is complete in SuperNutritionApp.swift only: one
StateObject retains photo/rewards/profile, mounted host calls idempotent start,
and owner disposal serializes close on Main. Syntax parsing passed host, arm64
and Intel simulator branches in Swift5 mode; cross-target parse warned about
CLT macOS sysroot. This is not framework typechecking/link or runtime evidence.

Dependency-policy preparation CLOSED59474 exit0 in1m33s, no FAILED/unresolved
nodes in the complete dependency report. Added30 verified pins/11coordinates,
preserving2623existing pin tuples. Snapshot23lockfiles: only Android app,
SharedKit and data/nutrition locks changed, with no unrelated state restoration
needed. Actual resolution host is Intel macOS with explicit iosArm64 target;
SimulatorArm64 artifacts were independently verified, not built/resolved as a
fake host. Driver-pins/pin-addition-summary.json and lock-refresh-summary.json
retain counts; strict artifact verification remains enabled.

Separate typed TOML quality reconciliation removed unused feature-host→data
permission, added actual UI paths to six camera/lifecycle/accessibility native
invalidation inventories, and refreshed three current XML observations. Coverage
kinds, floors, changed-code targets, expiry/capabilities and old physical records
are unchanged. Final candidate v1 session62356 is active with strict verification,
--continue, Android APKs, explicit core-config tests/coverage, both root gates and
actual SharedKit arm64 framework/test + platform-iOS test compilation. No final
candidate pass is claimed before its exact closure.


Full candidate v1 CLOSED62356 exit1 in18m23s,614tasks (291executed,54cache,
269UP-TO-DATE). Android production/uiAcceptance app/test APK tasks passed, actual
main arm64 code (including data Native driver/feature/UI/SharedKit) compiled and
SharedKit debug framework linked. Platform-iOS arm64 test compilation passed.
SharedKit Native tests failed compilation only: @Test expression body inferred
an assertIs object instead of Unit; no Native test execution occurred.

The five failures are: verifyMobilePolicy stale manifest digest; SharedKit
import-order lint; feature AppleMainContent length62/60; platform-iOS four class-
signature style findings; SharedKit Native test return type. All are recorded
before repair. Physical expiry has not yet been reached because manifest digest
validation stops earlier, so the full gate is not claimed green or reduced to
only device expiry. Fresh module XML counts/coverage are saved separately under
mobile/build/verification/mvp-audit/candidate-jvm-{results,coverage}.json.


SharedKit repair invocation CLOSED2716 exit0 in2m35s,118tasks (25executed,
93UP-TO-DATE). The two recorded fixes only reorder ProfileSession imports and
bound runBlocking<Unit> on the Native golden test. Full SharedKit Detekt passed;
actual compileKotlinIosArm64 and compileTestKotlinIosArm64 both EXECUTED/passed,
so the three Native fixtures now compile. No framework link/runtime execution
was claimed by this repair. A separate feature-only invocation now extracts the
existing remembered clock to resolve its length finding without API/UI changes.


Fresh diff-to-Kover XML audit against baseline7309 (including untracked authored
production source, not generated build output) finds no missed changed JVM
outcomes in these modules:

| Module | Changed executable lines | Changed branch outcomes |
| --- | ---: | ---: |
| domain/nutrition | 142/142 | 96/96 |
| data/nutrition | 247/247 | 76/76 |
| photo-analysis-presenter | 114/114 | 110/110 |
| photo-analysis host common/JVM | 113/113 | 64/64 |
| core/config | 13/13 | 8/8 |
| core/ui | 24/24 | 0/0 |

Exact changed-line audit and raw module counters are retained in
mobile/build/verification/mvp-audit/changed-jvm-coverage-audit.txt. This audit
cannot cover iosMain/Android-native code absent from JVM XML. UI raw Composer
outcomes and expired physical native-equivalent records remain explicitly as
previously reported; no generated/native outcome is relabelled covered here.


Feature clock repair CLOSED55804 exit0 in2m53s: fresh101JVMtests/18suites,
zero failures/errors/skips; fresh iosMain lint0; actual arm64 main compilation
EXECUTED/passed. Only the existing clock effect was extracted into a private
composable helper; published controller/API behavior is unchanged.

Platform-iOS style repair CLOSED21906 exit0 in2m21s,32tasks (8executed,
24UP-TO-DATE). Exact two-line class signature layout cleared all four findings;
full module lint passed, actual main and test arm64 compiles EXECUTED/passed.
No storage I/O logic or assertions changed and no Native runtime is claimed.

Typed policy reconciliation refreshed all ten actual JVM XML observations and
rebound13manifests to SHA256642c6201c2c2e10750471ab935cc708eafc3e681b46a3157c2c31e9c1af23d3d.
All11native execution records are exactly preserved, along with floors/kinds,
capabilities, dates, expiry and lifecycle. The first helper validation used a
wrong count-key and failed closed before writes; the corrected typed key agrees
with15productionunits/15denominators. Candidate v2 now repeats the entire v1
request with strict verification and --continue. All code corrections are made;
its final gate state remains pending exact closure.


Candidate v2 CLOSED58404 exit1 in5m02s,614tasks (51executed,563UP-TO-DATE).
All requested Android APK, JVM/test, lint and actual arm64 compilation/framework
link tasks passed. SharedKit main/test compilers and debug framework link executed
against the repaired source; unchanged dependencies legitimately remained cached.
The sole failure is verifyMobilePolicy: “Native-gap evidence is stale:
bootstrap-android-host”. No code/lint/digest failure remains in this full request.
The complete quality entrypoints remain BLOCKED, never green, by required old
physical evidence. Other older physical records remain unchanged; this validator
reports its first stale record, not an independent recency pass for every record.
Interop -Xexport-library warnings and typed KMP-analysis warnings remain recorded
in the full log; actual relevant source compilers passed. Root-boundaries and
release immutability scripts passed locally (immutability uses cached local
origin/main0f1fbb4e599e; no fetch/remote operation was performed).


Xcode workspace scheme build CLOSED56161 exit70 before compilation: no destination
for SuperNutrition. Explicit generic iOS retry CLOSED67865 exit70 confirms local
“iOS26.5 is not installed” platform-component blocker despite available SDK headers
used successfully by Kotlin/Native. No simulator/device result is claimed. The
known direct project-target SDK build is now attempted solely for actual Swift
compiler/link evidence with existing locked CocoaPods inputs; workspace-based
scheme/reproduction/runtime remains separately blocked by local platform setup.
No system platform download, global Xcode change or provisioning modification was
performed. Both blocked attempts and the direct-target log remain in mvp-audit.


Direct Xcode production target64596 CLOSEDexit65: Swift source compilation
succeeded, then actual arm64 app link failed solely on unresolved SQLite3 symbols
from Native SQLiter. This confirms a real project linker setting defect beyond
Kotlin framework compilation. It is recorded before a separate iosApp-only
project configuration repair. The workspace/generic attempts remain external
setup failures before any compilation. No iOS runtime execution was attempted.


SQLite linkage repair: direct production target46010 CLOSEDexit0, BUILD SUCCEEDED; direct XCTest target37693 CLOSEDexit0, BUILD SUCCEEDED. Actual Swift/arm64 production app, acceptance app and XCTest bundle compiled and linked with checked-in inherited -lsqlite3 in exactly four app Debug/Release configurations. Typed plist comparison preserved every other object/setting; plutil lint and diff check passed. Production otool confirms /usr/lib/libsqlite3.dylib. This is build evidence only: zero tests executed; workspace scheme still blocked by missing local iOS26.5 platform component. Logs/summary: mobile/apps/iosApp/build/verification/sqlite-link-repair/.


## Stable JVM XML inventory (2026-10-24)
All listed XML has zero failures, errors and skips. Unchanged tasks reused valid inputs; this table does not relabel cached tests as newly executed. Platform/photo-ios JVM XML is the unchanged October23 suite, distinct from newly executed arm64 main/test compilation.
| Module | Suites | Tests | XML timestamp range UTC |
| --- | ---: | ---: | --- |
| core/config | 5 | 36 | 2026-10-24T09:54:47.379Z – 2026-10-24T09:54:48.541Z |
| core/ui | 1 | 4 | 2026-10-24T08:22:48.433Z – 2026-10-24T08:22:48.433Z |
| data/nutrition | 3 | 21 | 2026-10-24T09:54:54.303Z – 2026-10-24T09:55:06.990Z |
| domain/inference | 16 | 126 | 2026-10-24T09:55:36.348Z – 2026-10-24T09:55:39.787Z |
| domain/nutrition | 40 | 306 | 2026-10-24T07:58:31.950Z – 2026-10-24T07:58:46.600Z |
| domain/photo-analysis | 11 | 115 | 2026-10-24T09:55:52.828Z – 2026-10-24T09:55:55.995Z |
| feature/photo-analysis | 18 | 101 | 2026-10-24T10:17:29.799Z – 2026-10-24T10:17:41.232Z |
| feature/photo-analysis-presenter | 13 | 79 | 2026-10-24T08:10:02.694Z – 2026-10-24T08:10:04.761Z |
| feature/photo-analysis-ui | 75 | 298 | 2026-10-24T08:57:42.807Z – 2026-10-24T08:59:41.484Z |
| inference/local-shared | 2 | 25 | 2026-10-24T09:56:53.327Z – 2026-10-24T09:56:55.332Z |
| inference/local-android | 2 | 25 | 2026-10-24T09:56:15.491Z – 2026-10-24T09:56:17.833Z |
| inference/local-ios | 2 | 21 | 2026-10-24T09:56:40.550Z – 2026-10-24T09:56:42.848Z |
| inference/local-android-poc | 3 | 6 | 2026-10-24T09:56:26.582Z – 2026-10-24T09:56:26.669Z |
| platform/photo-ios | 1 | 2 | 2026-10-23T17:40:49.172Z – 2026-10-23T17:40:49.172Z |
| apps/sharedKit | 1 | 3 | 2026-10-24T10:13:18.772Z – 2026-10-24T10:13:18.772Z |


Full unfiltered Android v1 CLOSED24899 exit1 in22m28s;324tasks (45executed,10cache,269UP-TO-DATE). Fresh XML: PoC60tests/1failure/0errors/0skips; production60/1/0/0; uiAcceptance60/0/0/0. Both failures are exactly MealDiaryJourneyInstrumentationTest missing capture_text under the late system camera permission prompt. All remaining tests, including real SQLDelight Android-driver golden queries and five real-filesystem storage tests in each flavor, passed. RED XML and journey diagnostics are preserved in mobile/build/verification/mvp-audit/native-red-v1/. No product/device success claim is substituted for these two failed assertions; a bounded journey synchronization repair and rerun follow.


Single-file native journey repair uses the existing15s deadline to handle a real permission-deny control, accept app controls only with target foreground, click capture once per permission transition, and require the actual enabled visible meal-name field. Only stale accessibility objects are retried; save/edit/unknown/delete assertions remain unchanged. No pregrant/revoke, production source, global UiAutomator flag or broad timeout change. Pinned UiAutomator2.3.0 APIs verified locally. Focused production native rerun session94281 is now active; no pass is claimed yet.

Focused production journey94281 CLOSEDexit0 in4m22s;192tasks (18executed,174UP-TO-DATE). Fresh native XML:1test,0failures/errors/skips. The previously failing actual path now saves, edits, verifies unknown nutrition and deletes through profile. XML preserved under permission-focused-green before the full unfiltered rerun overwrites production reports.


Full unfiltered Android v2 CLOSED5102 exit0 in21m42s;325tasks (25executed,300UP-TO-DATE). Fresh XML confirms PoC60/0/0/0, production60/0/0/0, uiAcceptance60/0/0/0:180tests total, no failures/errors/skips, with no class/method filter. This is emulator-only Android15/API35/x86_64 evidence. It does not renew the physical native-gap records. Exact output: connected-durable-full-v2.log; structured per-flavor XML paths/timestamps: native-results.json. The one-file late-permission synchronization repair passes the entire suite after its actual RED and focused green.


## Manual final production APK verification

Installed productionDebug APK with android CLI; local and installed base.apk SHA256 both `cf5c572359a783696b0cbbddbbf92a51db2194a54fc6b0940520a548a383a88b`. Debuggable app.supernutrition, real emulator-5554/API35/x86_64. Installed identity/hash/PID are in installed-apk.json. Initial state: no name and zero meals.

Through the UI only, saved name MVP QA, explicitly selected `food:usda-srl-01227` / Cheese, dry white, queso seco from the ready 7,793-food USDA2018-04 US catalog, entered 50 g and saved. First force-stop killed PID 14017; fresh process 14611 restored exact name, one meal and its binding. Source initialization initially showed unknown numeric values; after source Ready, history/details showed 162.5 kcal, protein 12.26 g, carbs 1.02 g, fat 12.18 g, sugar 0.28 g and sodium 904 mg; fiber remained unknown. Per 100 g showed 325 kcal, protein 24.51 g / carbs 2.04 g / fat 24.35 g/fiber unknown. Edited portion to 100 g and observed the selected portion 100 g / 325 kcal, then restored 50 g / 162.5 kcal without losing the binding.

Captured and immediately visually inspected real PNGs under /Users/REDACTED/.codex/visualizations/2026/10/22/01a16557-276b-7002-b45e-4a79f11f1c89/mvp-durable-20261024/: home.png (capybara seated on the room floor), profile.png, meal-history.png and meal-detail-50g.png. These screenshots contain explicitly authored synthetic QA data, subsequently removed. No generated mockup is presented as device evidence.

At 360×780 dp / font 2.0, the existing Home fallback is one column and scrolls. A real initial and scrolled screenshot confirmed all four metrics reachable and clear of the bottom navigation; this is not a no-scroll acceptance claim. Screenshots home-200-percent.png and home-200-percent-scrolled.png preserve both states. A temporary unsaved text draft at 200% with IME open scrolled its Add meal button fully above the keyboard (meal-form-200-percent-ime.png); the draft was discarded, leaving one saved record. Font scale restored and read back as 1.0.

Cleared the name through Clear→Save and deleted only the own QA meal through its confirmation. Second force-stop killed PID 14611; fresh process 15839 restored the unnamed profile / 0 meals and empty history, with no row left. Final UI was returned to Home. Structured manual-check.json records both PID changes, checks, and cleanup_pending=false; exact fresh layouts are retained in mvp-audit. No blanket app data clear was used for this manual cleanup.

Both cold am start -W waits returned Status:timeout after 14,728 ms / 15,010 ms. Subsequent fresh UI snapshots and the complete interactions above verified the running app; no startup-time/SLO claim is made from those timed-out waits. Initial catalog availability also required waiting for real Ready state; values were not assumed while loading. This remains emulator functional evidence, not physical performance or iOS runtime evidence.


Graph refresh CLOSED72400 exit0: graphify update . re-extracted934 uncached files and rebuilt23,030nodes/32,502edges/2,097communities. graph.json and GRAPH_REPORT.md were updated locally and remain ignored. This is code AST evidence only:525 mostly metadata/document-like inputs produced no nodes, two SQL files were not indexed because optional tree_sitter_sql is absent, and semantic document extraction was not performed. graph.html was explicitly skipped by the23,030-node/5,000-node visualization size guard. No graph output or build artifact is staged. git diff --check passed.


## Reproduction commands

From the repository root, the full candidate v2 request was:

```sh
DEVELOPER_DIR=/Applications/Xcode-26.6.0.app/Contents/Developer \
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./mobile/gradlew -p mobile \
  :apps:androidApp:assembleProductionDebug \
  :apps:androidApp:assembleProductionDebugAndroidTest \
  :apps:androidApp:assembleUiAcceptanceDebug \
  :apps:androidApp:assembleUiAcceptanceDebugAndroidTest \
  :core:config:jvmTest :core:config:koverXmlReportJvm \
  qualityCheckJvmAndroid qualityCheckApple \
  :apps:sharedKit:linkDebugFrameworkIosArm64 \
  :apps:sharedKit:compileTestKotlinIosArm64 \
  :platform:photo-ios:compileTestKotlinIosArm64 \
  :data:nutrition:compileKotlinIosArm64 \
  -Psupernutrition.enableIosDeviceTarget=true \
  -Psupernutrition.enableSharedKitFramework=true \
  -Psupernutrition.enableTestingAppleTargets=true \
  --dependency-verification=strict --no-daemon --no-configuration-cache \
  --max-workers=1 --continue --console=plain
```

This full request has the required physical-policy failure described above; it is not green. The subsequent only test-source correction was compiled and executed by the final unfiltered native request:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.deviceSerial=emulator-5554 \
  --dependency-verification=strict --no-configuration-cache --console=plain \
  --no-daemon --max-workers=1 --continue
```

The focused green instead selected connectedProductionDebugAndroidTest with the additional property android.testInstrumentationRunnerArguments.class=com.supernutrition.android.MealDiaryJourneyInstrumentationTest. The full final request above has no such property.

Actual Xcode compiler/link fallback, invoked twice sequentially with target SuperNutrition and then SuperNutritionUIAcceptanceTests:

```sh
DEVELOPER_DIR=/Applications/Xcode-26.6.0.app/Contents/Developer \
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
xcodebuild -project mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj \
  -target SuperNutrition -configuration Debug -sdk iphoneos \
  ARCHS=arm64 CODE_SIGNING_ALLOWED=NO \
  SYMROOT=/Users/REDACTED/.codex/worktrees/f2ff/SuperNutrition/mobile/apps/iosApp/build/MvpProducts \
  -jobs 1 -hideShellScriptEnvironment build
```

For the second invocation replace only -target with SuperNutritionUIAcceptanceTests; never build both targets concurrently. The pre-existing locked Pods inputs were available locally. This direct-project build does not substitute for a fresh workspace scheme build: the local missing iOS26.5 platform-component limitation remains. No Xcode tests executed.
