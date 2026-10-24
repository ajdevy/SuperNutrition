# Durable catalog diary: acceptance findings recorded before fixes

1. Production Android and SharedKit construct in-memory diaries and profile names. Explicit saves must survive process restart; deletions/name clearing must survive another restart.
2. Profile name save callbacks return Unit and dismiss unconditionally; clearing only edits the draft. Durable failures must remain visible; an empty submitted name must delete the stored optional name.
3. Diary load failure is indistinguishable from empty history. Show a storage failure without overwriting corrupt bytes.
4. Imported SR Legacy nutrients are not activated. The upstream generator emits a computed archive hash without enforcing the pinned hash. Reproducible activation must validate source/package integrity and carry source/version/country provenance.
5. Existing grounded-nutrition/v1 has six non-null values; USDA has missing fiber/sodium (and sugar). Add separate partial nutrient semantics; do not change a frozen v1 or coerce missing to zero.
6. Authored meals discard grounded ledger values, and session supersession masks diary IDs. Persist explicit catalog bindings in the diary and derive their numeric presentation from the verified package; avoid a second durable ledger.
7. Existing source data has no category classification for most rewards. Source activation must not award color/fat/fermentation classifications from names.

Initial state: HEAD 7309e140b9cf12c6acf5733cfdcec00ce5912f1b, clean worktree before owner records. D-07/D-08 are recorded before implementation. Existing owner records pass strict verification; older explanatory PENDING quotations are historical, not pending fields.

## Source fixtures and packaging constraints

- Imported TSV SHA256: `1b416249fde1f9a6a0672e71c3ffb216b2a3883f3dbd833a173d3b5332b657eb`.
- Upstream archive pin: `b80817294b8850530aaedf2e515c02593b1824f763a0ff356e5c2081643e6fd0`.
- `food:usda-srl-01227` (Cheese, dry white, queso seco): per100g energy325, protein24.51, fat24.35, carbohydrate2.04, fiber unknown, sugar0.55, sodium1808mg. A50g serving must preserve unknown fiber and give162.5kcal, not discard the other values.
- `food:usda-srl-09138` (Groundcherries, raw): per100g energy53, protein1.9, fat0.7, carbohydrate11.2; fiber, sugar and sodium unknown.
- Published `grounded-nutrition/v1` stays intact. Partial values require separate types and truthful presentation.
- AD-7 specifies SQLDelight; a generated map alone is insufficient. AD-19 signed packaging must be described accurately: the immutable bundled MVP package can be authenticated by the enclosing signed application, with separately pinned data checksums. This does not implement signed remote package distribution, a downloaded update key service or independent OTA activation. Production release signing remains a release prerequisite; debug emulator builds use debug signing.
- The website repository currently states session-only meals/name in `src/data/legal.ts`. Factual retention copy must change through `flexsent-labs/supernutrition.app` alongside rollout of the new mobile behavior; no website files are to be uploaded manually.

## Adapter implementation review (before follow-up fixes)

- The first iOS adapter calls `addressOf(0)` for an empty encoded document. The generic document port must round-trip an empty string without an index exception, even though current diary/name encodings have nonempty headers.
- Android `AtomicFile.finishWrite` can log a replacement failure without throwing. Prove a failed replacement cannot return save success, using an actual filesystem failure/read-back condition rather than only a fake in-memory store.
- Bound iOS reads before allocating untrusted document contents when the native file metadata is available; oversize documents must remain preserved and visible as load failures.
- The existing generated multilingual aliases contain FAO AGROVOC terms under CC-BY IGO 3.0, but no shipped attribution was found in the current UI. Reusing those aliases in the explicit USDA chooser must expose the required FAO/AGROVOC credit and licence link, alongside USDA CC0 provenance and Wikidata credit; a repository-only ATTRIBUTION.md is insufficient.
- Bind Android replacement success to absence of AtomicFile `.bak`/`.new` sidecars as well as exact base bytes, so a retained backup cannot later restore a logically deleted value. Normal round-trip fixture already asserts sidecar absence; add a failure-path assertion before extending the adapter.
- Initial v2 resolver directly calls the source lookup. An ordinary adapter read exception must produce `SOURCE_UNAVAILABLE` rather than crash details/save; cancellation must still propagate. Add an executable failure-source assertion before changing that boundary.
- Initial package activation closes its candidate directly in the cancellation catch. A close failure can replace the original cancellation; add a close-failing cancellation fixture and preserve cancellation while cleaning up best-effort.
- The initial bundled-source factory returns only the domain port and loses explicit driver disposal authority. An adapter-owned session must retain its driver, close idempotently, and reject numeric lookups after closure. Hosts must retain one session outside recomposition, create it off the UI thread, publish it on Main, and close a late result if the host was already disposed. Initialization cancellation must close the private candidate while preserving the original cancellation.
- The first real data-module Detekt invocation includes SQLDelight compiler output under `mobile/data/nutrition/build/generated/sqldelight/code/UsdaCatalogDatabase/` and reports the compiler's fixed indentation, wrapping and SQL identifier style. Keep authored `src/` files, including the reproducibly generated USDA payload, fully linted; constrain any tool-output lint rule to this exact SQLDelight directory. This is separate from coverage: emitted SQLDelight code stays in its full coverage denominator. The initial full-module XML exposes 12 unexecuted generated query listener/diagnostic lines; these are not VCS changed lines and do not justify tests that mirror generated `toString` boilerplate.
- XML inspection shows the adapter's private companion has a public `getAliases` accessor that production never calls (the outer class accesses the backing field directly). The alias map is an implementation detail; declare the property private rather than add a test for an unnecessary accessor or exclude it from coverage. The only real uncovered authored branch is the intentional search-index hit whose numeric SQL row has disappeared; a real database-loss fixture must prove it abstains.
- The first combined metadata/arm64 request compiles the actual data adapter and domain code for iosArm64, but existing domain:inference common metadata locks omit the newly selected multi-target metadata coordinates: kotlin-stdlib-common 2.4.10, atomicfu 0.23.1 and kotlinx-coroutines-core 1.10.2. Refresh the required metadata/native lock scopes at the existing pinned versions under strict verification, retain a pre-run snapshot, and restore unrelated lock churn. This is a dependency-resolution setup failure, separate from the successful arm64 compilation and from tests.

## Presenter binding integration lint, recorded before correction

The first full strict presenter run compiled production/tests and executed JVM
tests and Kover. Read-only Detekt then reported authored formatting findings:
`MealDiaryReader.kt:45` BlankLineBetweenWhenConditions;
`MealDiaryV2CodecTest.kt:135` five ArgumentListWrapping findings;
`PresenterCatalogFixtures.kt:16/30` seven ArgumentListWrapping findings and
`:26/27` three ClassSignature findings. Correct only these declaration/layout
issues and rerun the complete presenter tasks; no lint suppression, baseline or
rule changes. Typed checks are still finishing at the time of this record, so
this is not yet an exhaustive result for that run.

Fresh v1 XML has 69 tests, zero failures/errors/skips. All changed catalog/codec
lines are covered; one changed branch remains at `MealDraftValidator.kt:18`:
the safe call plus Elvis on a non-null ingredient list introduces a redundant
nullable outcome. Use the already-established explicit non-null condition and
retain both unchanged-structured-label and parsed-input fixtures. Full module
observations are 641/656 lines and 354/375 branches; preexisting unrelated
presenter gaps are not permission to add exclusions or lower thresholds.

## Central write validation acceptance before host integration

Feature v3 test compilation fails at
`PhotoAnalysisAnalysisCoordinatorTest.kt:117`: the new expected
`listOf(failure)` infers RuntimeException elements against a Throwable callback
list. Acceptance before correction: use `listOf<Throwable>(failure)` to retain
the exact exception-instance callback assertion. No v3 tests or coverage ran;
the previous 97-test/493-line/258-branch report remains v2 evidence only.
Wait for the active --continue run to close before editing and rerun the full
module afterward.

Feature v2 closed exit0 in 3m20s with 97 passing tests and clean read-only
Detekt aliases, including Android and iosMain source checks. Coverage floor
still fails from the direct XML observations above. Bounded unchanged-code
acceptance cases selected before follow-up: coordinator line51 must ignore a
request/attempt mismatch before calling either engine; cancellation at96 must
propagate, while IllegalArgument/IllegalState paths at102/103 must produce the
declared capture recovery and failure callback; recovery mapping at157/166
must cover PHOTO v2 abstention/failure enum values with their truthful domain
reasons. Add source-driven behavioral tests without unrelated production
changes, reflection/getter probes or threshold changes. Router/context/ledger
misses remain visible if these cases suffice for the unchanged-code floor.

The public feature ports now expose `ApprovedNutritionCatalog` and
`MealCatalogState`; existing public saved-meal APIs already expose presenter
types. Declaring those two existing project edges as implementation hides
their contract types from Java/SharedKit clients and relies on incidental
adapter transitivity. Acceptance: promote exactly domain:nutrition and
feature:photo-analysis-presenter to api in the feature build script. Preserve
all versions/coordinates and adapter boundaries; any required consumer lock
refresh or typed architecture-manifest reconciliation belongs to its separate
policy invocation, not this feature source slice.

Fresh feature v2 JVM XML has 97 tests, zero failures/errors/skips. Current
Kover observes 493/521 lines and 258/318 outcomes (81.13%, below the 85%
module floor). New diary/local-data/Home authored paths are covered. The new
`MealCatalogSession.kt:75` finally publication guard has cb4/mb2 because Kotlin
duplicates finally bytecode for normal/caught/cancelled exits, although both
semantic outcomes execute. Acceptance before repair: move the common finish
logic to one private implementation called from finally, retaining cleanup,
Unavailable publication and opening-job reset on every exit; rerun exact
semantic cancellation/replacement cases with no exclusions or suppression.
Other observed misses are on unchanged coordinator/launcher/router/feature/
ledger/context paths, not a reason to claim the module floor passes. Inspect
their exact XML/source sites and add meaningful reachable acceptance cases;
do not invent reflection/constructor-getter tests merely for counters.

Feature full v1 (46572) closed exit1 in 3m30s: 93 tests, one previously
recorded exception-identity harness failure, zero errors/skips. Kover did not
regenerate. Before formatting fixes, unique lint has 26 findings:

- Android Activity: seven (MaxLineLength at423; LongMethod for 62-line
  `renderContent` at429; three ArgumentListWrapping at423; ImportOrdering at3;
  MaximumLineLength at423).
- `MealDiary.kt`: fourteen (MaxLineLength and MaximumLineLength at55/67;
  eight ArgumentListWrapping at55/67; two BinaryExpressionWrapping at55/67).
- `FeatureCatalogFixtures.kt`: five ArgumentListWrapping at24.

Acceptance: wrap existing calls, sort imports and extract the capture-state
construction into one named helper so `renderContent` meets the existing
60-line limit without suppression or behavior changes. Typed reports mirror
these findings (mainAndroid21, mainJvm14, testJvm5); iosMain/jvmMain have zero.
Preserve actual compiler success separately from the failed test/lint run.

The first full feature run executes 93 tests with one failure, zero errors/
skips: a direct factory-cancellation test requires exception object identity
across `withContext`, but coroutine stacktrace recovery can copy that exception.
Acceptance for this harness correction: require a CancellationException with
the original message; conversion to Unavailable without cancellation must
still fail. Do not weaken cancellation propagation or cite the stale Kover
report from a failed test dependency. Exact lint findings await run closure.

Read-only integration review of the new session finds that `retry()` returns
whenever a Ready handle already exists. UI search can report an ordinary read
failure while the owning state is still Ready, so its explicit Retry would
then do nothing. Acceptance before repair: execute a regression opening a
Ready source, simulate its failed search and request Retry; a second factory
open must run off Main and publish a valid replacement. Keep the previous
owned handle until replacement verification succeeds (AD-7 atomic activation),
close it exactly once on successful replacement or final disposal, preserve it
on a rejected/throwing candidate while exposing Unavailable, and close every
failed/late candidate. Add successful replacement, failed candidate/getter,
retry-after-failure and disposal-during-replacement cases. Do not simply clear
the previous handle before verification or lose it on a null candidate.
An unsuccessful replacement cancelled while a previous handle is retained
must also leave Loading, expose Unavailable, propagate cancellation and allow
another explicit retry when the owner scope remains active. Cleanup must track
publication success rather than assuming a non-null retained old handle means
the current attempt succeeded.

The new session handoff initially transfers its candidate to the retained
handle and clears the cleanup variable before reading `ownedHandle.catalog`.
An ordinary getter exception can retain an unavailable handle, prevent retry
and defer cleanup. Acceptance before repair: execute a throwing-getter test;
obtain the Ready catalog before transferring candidate ownership, close the
failed candidate exactly once, expose Unavailable, and allow a later good
handle to activate. Keep cancellation propagation/prompt-cancellation cleanup.

The binding-copy regression has now executed against minimally wired approved
validation while the old omission remained: one test, one assertion failure,
zero errors/skips (UTC 2026-10-24T09:07:06.029Z; exit1 in 1m49s). Saving a
valid selected meal succeeded, but its expected USDA/01227 binding was null.
This is an executed product regression, not a compile failure.

`MealDiary` has internal `ValidatedMealDraft` add/update paths used by photo
review as well as its public raw-draft paths. Persisting the new binding field
must not let these structured paths bypass the current approved source check.
Keep one presenter validation implementation for both raw and structured drafts;
do not reconstruct CSV in the feature or duplicate catalog/identity checks there.
Acceptance: a new unverified structured binding is rejected, a verified bound
draft retains its binding, ordinary unavailable/stale historical bindings remain
editable under the approved rules, and comma-bearing structured photo labels
retain their original list boundaries. Add a narrowly owned presenter overload
if needed before completing the feature-module integration.

The host availability owner must remain cheap until explicitly started after
rendering. Create/verify/index the catalog on the background dispatcher, retain
the candidate outside the `withContext` return value for prompt-cancellation
cleanup, publish/query/dispose on the owning main context, and make startup,
explicit retry and disposal idempotent. Android ownership survives activity
recreation; SharedKit production ownership spans its Home/Profile/capture views.
Default test/acceptance constructors remain isolated. Cancellation and a late
candidate must not leak a driver or publish Ready after disposal.

## Canonical aggregation and normalization acceptance

AD-19 assigns numeric aggregation to the nutrition domain. Keep UI responsible
for date filtering and choosing the projected portion/per-100g basis, with one
domain sum/normalization implementation shared by Home and history.
The old UI `MealHistoryMeal.nutrition(true)` computes `100 / grams` before
multiplication. For a valid tiny positive serving this factor can overflow,
turning a known zero into NaN and therefore unknown. Execute a regression
against that existing API before replacing its arithmetic. Normalize as
`value / grams * 100` under validated positive finite grams, and preserve
known zero. Empty input, an unknown contributing meal/field and finite overflow
must leave affected totals unknown independently, without altering frozen v1
contract types or threshold policy.

## Structured validator test lint before correction

The first complete structured-overload presenter run exited 1 after executing
79 passing tests (zero failures/errors/skips). Production commonMain has no
findings; all 22 authored findings are in
`StructuredMealDraftValidationTest.kt`: MaxLineLength 2 (:149, :156),
ArgumentListWrapping 14 (:56 twice, :149 six, :156 six), FunctionSignature 1
(:156), MaximumLineLength 2 (:149, :156), and Wrapping 3 (:164 twice, :166).
Wrap the existing calls/helper declarations below 120 characters without
altering assertions or behavior, then rerun the full module. Do not change
lint rules or add suppressions. The new overload already covers all 9 lines
and 2 branch outcomes; full XML observes 650/665 lines and 355/375 branches.

## UI assertion findings from the first complete catalog run

Policy clarification after typed manifest/code inspection: both feature UI and
feature host already use `coverage_kind = "native-equivalent"` in the existing
coverage manifest. `QualityPlugin.jvmCoverageModulePaths/Targets` includes only
kind `jvm`; these two direct Kover reports are therefore supplemental diagnostic
measurements, not newly failed numeric denominator-gate executions. Their
required physical Android equivalence records remain expired, as previously
accepted/reported by the human; optional Apple records remain pending. No
classification, threshold, evidence record or source denominator was changed.
Retain all raw shortfall observations and compiler maps without claiming 100%
raw branches or fresh physical equivalence. The disconnected legacy-renderer
cleanup is a separately bounded improvement, not necessary merely to bypass a
numeric gate that does not apply to this existing denominator classification.

The v5 raw module result is 5,967/6,686 lines and 1,955/2,770 branches
(70.5776%, below the 85% module floor); changed outcomes remain 222/252,
below 100%. There is no preserved pre-phase baseline XML, so these figures
must not be represented as a measured baseline regression. Of the misses,
30 are on changed lines and 785 on unchanged lines; resources add no branches.

Read-only repository call-site inspection identifies a legacy internal render
cluster disconnected from the current Rewards/Home entry points:
`RewardsScreenComponents.kt`'s old four-argument header/hero/cards,
`RewardsReadableCanvas.kt`, `RewardsReadableCard.kt`, `RewardsImageLayout.kt`,
`RewardsTextFit.kt`, and `HomeNextStep.kt`. Their entry functions have no
production callers outside the old cluster; the public `RewardsScreen` routes
to `CurrentRewardsScreen`, and current Home uses `HomeHeader`,
`HomeCarePrompt`, `HomeAnimatedMascot`, and `HomeRhythmCard`.
Acceptance for a later narrow UI cleanup: prove every removed declaration has
no reachable production/test consumer, preserve live overloaded functions and
shared helpers, delete only dead implementation instead of excluding it from
coverage, then rerun full UI tests/lint/coverage and inspect actual screenshots.
Zero coverage alone does not authorize deleting a reachable component. Do not
change thresholds, denominators, public APIs or native evidence to hide gaps.

The v4 lint run reports `NoBlankLineBeforeRbrace` in
`MealCatalogRevalidationTest.kt:106` and `TinyServingNutritionTest.kt:65`.
Acceptance: remove only the blank before each final class brace; preserve all
296 passing-test assertions, then finish the full relevant rerun.
`ProfileSelectedMeal.kt:40` has the authored delete result `ifeq31` (false and
true exercised) plus generated memoization checks at bytecode offsets
1047/1056/1067/1082/1090/1101/1116/1144/1155. Retain exact javap/line-table
evidence and actual raw counts rather than claiming that all outcomes pass.

Fresh v4 XML has 296 UI tests / 74 suites and no failures/errors/skips.
Changed executable lines now cover 340/340, while raw outcomes remain 221/252
(31 missed). Source/bytecode inspection identifies a second unused internal
`CaptureTextEntry.controlsVisible=true` default: its sole production caller
always supplies the argument and bytecode offset 123 is the default-mask
branch. Acceptance: require that internal argument explicitly, retain public
contexts and all control-visibility behavior, then rerun. Remaining retry/link
handler replacement and selected-meal callback outcomes still require exact
source/XML/bytecode mapping; do not assume them covered from line coverage.

Source reachability inspection refines the standalone-name-dialog finding:
the internal NAME enum branch has no production assignment/entry point after
the inline `ProfileNameEditor` replaced it. Remaining dialog assignments are
PRIVACY, TERMS and CRASH_REPORTS; NAME occurs only in its enum and unreachable
renderer branch. Four missed changed lines belong to that dead save path.
Acceptance: remove that unused internal NAME variant/dialog and its now-unused
`ProfileDialogActions` helper, retaining public Profile APIs and all reachable
legal/crash/inline-name behavior. Do not add tests solely to exercise dead UI.
The remaining name-editor persistence/IME cases still require real tests.

Fresh v3 Kover audits 334/340 changed executable lines and 214/260 raw branch
outcomes against baseline 7309 plus untracked UI sources (full observations in
`mobile/build/verification/durable-ui/ui-coverage-audit-v3.txt`). Before further
changes, these authored gaps require meaningful cases:

- Render the stale/invalid catalog-selection save error.
- Exercise a non-finite legacy serving-mass sum without fabricating per-100g
  numbers.
- Exercise durable standalone Profile dialog name-save state, failure and
  success (`ProfileDialogs.kt:46,53,64,66`).
- Exercise controlled name-editor IME invalid/unchanged guards and its invalid
  message, plus nullable `ProfileNamePersistence` fallback.
- Remove the unused default from the internal `CaptureTextEntry` parameter
  whose sole caller always supplies a catalog context; retain public defaults.
- Replace SourceLink's redundant safe-call/Elvis nullable path with explicit
  equivalent branching shared by the override and platform-link tests.

Remaining raw outcomes on Compose declaration/recomposition/callback lines
must be identified as compiler-generated only with source/XML evidence; keep
them in the denominator and report actual raw counts. This observation does
not claim the changed-code target or overall gate is already green.

The second run additionally reports an over-120-character backtick test
signature in `MealCatalogAvailabilityTest.kt:20` (MaxLineLength,
MaximumLineLength and FunctionSignature). Acceptance: shorten that test name
only; retain all availability/query-preservation assertions. The first run's
five style conditions are resolved in the current common-main report.

The second run's fresh UI XML has 291 tests and one remaining selector-test
failure, zero errors/skips. The unmerged preparation text is
`Preparation not confirmed: fried. Check the exact product before saving.`
but the test uses `assertTextContains("fried")` with its default
`substring=false`, requesting a whole text item equal to `fried`.
Acceptance: use `substring=true` to check that required preparation token in
the visible warning. Keep the source/preparation requirement and the rest of
the selection/save-failure assertions. The Fat scoping and the two new
availability/platform-link tests pass. No overall green is claimed while
lint finishes or this assertion remains failing.

The new legacy nutrition projection repeats the existing serving-gram sum in
`portionGrams()`. Acceptance: use one existing-semantics presentation helper
from both paths; keep deterministic nutrient arithmetic in the shared domain
helpers and do not introduce new mass allocation or rounding rules.

Read-only source-set lint also reports mechanical formatting in
`MealHistoryModels.kt` (import order), `MealNutritionProjection.kt:30,41`
(wrap nested aggregation), `ProfileSettings.kt:57` (split the existing long
storage-load conditional), `MealCatalogSearchTest.kt:25-27` (wrap nested
assertions), and `MealDiaryModelsTest.kt:22` (blank line before annotation).
Acceptance: change formatting only, preserving calculation, error and test
semantics, then rerun the full module. This is a partial list while typed
Android/JVM lint tasks finish; it is not an exhaustive clean result.

- The fresh `photo-analysis-ui` JVM XML has 289 tests and two assertion failures.
  `MealCatalogDetailsTest` finds the correct `12.18 g` twice: once in the Fat
  macro and once in the Total fat breakdown. Acceptance: scope the assertion to
  the Fat label's sibling value and retain the exact numeric check.
- `MealCatalogSelectionTest` searches the merged tree for
  `meal_search_row_0_detail`, which belongs to the unmerged descendants of a
  clickable row. Acceptance: query that explicit ID in the unmerged tree,
  retaining the preparation/source requirement before selection.
- Production and test compilation succeeded. The genuine missing-chooser and
  tiny-serving regressions now pass. These are test harness corrections, not
  permission to weaken product assertions. The run is still finishing the
  core registry, coverage and read-only lint tasks, so no overall pass is
  claimed at this checkpoint.



### Android app composition and dependency reproducibility checkpoint

Before the Android app fix, `BootstrapApplication` implements only the runtime
provider. Without the independent catalog provider, the feature constructs an
unconfigured session and production catalog state stays Unavailable. Acceptance:
first execute a native failing Application-provider assertion, then configure a
cheap app-root factory with a fresh owned real SQLDelight handle. Construction
must tolerate an unattached Application, opening must run off Main, and Main
queries must prove the 01227/50g golden values, missing fiber, four credits and
post-close rejection. Add a regular-file `.bak` recovery compatibility fixture;
retain the existing directory-sidecar write-failure protection.

A previous reconnaissance note incorrectly attributed the Linux-specific lock
selection to data/nutrition. Direct source inspection shows that override exists
only in feature/photo-analysis/build.gradle.kts. Data/nutrition correctly uses its
normal gradle.lockfile on all hosts. No extra data Linux lock or Linux VM is
required by that source. The new app drivers still require verified pins and
resolved locks, including real published Apple-Silicon artifact metadata. Do not
claim another host was built from metadata-only resolution.

Android host activation is explicit: BootstrapApplication's normal catalog
factory opens the approved bundle only for the production flavor. PoC and
uiAcceptance receive a null-returning factory, preserving their existing
isolated hosts. The direct real-driver native fixture must execute on all three
variants using an explicitly constructed factory; no flavor-skipped golden test
and no production user-document mutation. Verify default activation separately.


### SharedKit production catalog and Swift root lifetime (before implementation)

SharedKitProductionSessions currently constructs durable LocalUserData with its
default unconfigured catalog session. Its production host therefore cannot open
the approved bundle. Acceptance: the sharedKit app root selects a real Native
SQLDelight in-memory driver and iosSha256, shares one MealCatalogSession across
Home/Profile/Capture, creates/indexes it off Main after rendering, and serializes
queries/disposal on Main. Keep legacy constructors isolated and public bridge
signatures intact. The inMemoryDriver(schema) helper creates the schema itself;
do not create it twice. Add compiled native fixtures for real golden/source and
isolated-legacy behavior; Intel cannot execute those arm64 tests.

The Swift App currently keeps the photo session as a value property and creates
rewards/profile in the host initializer. It has no explicit catalog start or root
disposal. Acceptance: one retained ObservableObject/StateObject owns all three
sessions, starts catalog after the root host exists, and closes on owner disposal
on Main. Modal presentation/Home disappearance must never close this shared
resource. Preserve the explicit Intel unsupported-renderer screen and shared
Compose screen rendering rather than adding a parallel Swift UI.


### Quality inventory reconciliation before final gates

The existing native-equivalent UI denominator points at photo-android-camera-
picker/photo-ios-camera-picker, but their invalidated_by lists omit the UI module.
Acceptance: include that actual denominator source path in those evidence
invalidation inventories, retaining required physical capability, expiry, old
execution records and optional Apple policy. Do not substitute emulator results
for the expired physical records. Check related lifecycle/accessibility source
inventories for their actual UI dependencies rather than broadening to all code.

coverage.toml currently records photo-analysis-presenter observed line/branch
percentages as 100.00/100.00, while current fresh module XML is 650/665 lines and
355/375 branches. Acceptance: reconcile observed diagnostics with actual latest
XML without changing coverage kinds, thresholds, denominator selection or tests.
Remove the unused feature-host -> data-nutrition architecture allowance, since
only app roots select the actual adapter. Keep the valid app-root allowance.


Candidate v1 read-only Apple lint reports SharedKitProfileSession.kt:3
ImportOrdering: LocalProfile must precede ProfileLegalLinks. Acceptance: reorder
those imports only after the active --continue build closes; preserve all session
composition/lifetime semantics, then rerun the complete relevant module/gates.
This finding is recorded before repair; further candidate tasks are still active.


Candidate v1 fresh feature iosMain lint also reports
MainViewController.kt:37 AppleMainContent at 62 nonblank lines (maximum60).
Acceptance: extract its existing remembered ticking Apple clock into a named
private composable helper, preserving its local-day refresh, cancellation and
all main navigation/catalog/reward behavior. Do not compress statements, suppress
the rule or alter the published five-argument controller. Wait for the current
build to close, then rerun full relevant lint and actual arm64 compilation.


Candidate v1 platform/photo-ios fresh iosMain XML reports four ClassSignature
formatting findings at IosPrivateDocumentStore.kt:31-33: opening/first-parameter
spacing, next-parameter spacing, closing-parenthesis spacing, and supertype
newline. The fresh iosTest XML is clean. Acceptance: apply the exact class
signature layout only, preserving storage protection/atomicity/recovery and all
runtime code. Record before repair; change in a separate platform/photo-ios
invocation after current build closure, then rerun relevant lint and actual
main/test arm64 compilation.


Candidate v1 additionally reaches the real Native test compiler and rejects
SharedKitUsdaCatalogTest.kt:31: @Test function must return Unit. Its expression
body infers the final assertIs result as the coroutine/test return type.
Acceptance: explicitly bound runBlocking<Unit> (or equivalent Unit block),
preserving every golden/lifetime assertion. This is a genuine compile failure,
not an executed Native test failure. Record before sharedKit repair.

verifyMobilePolicy stops on stale manifest_digest after the deliberate policy
inventory edits. Acceptance: use the exact typed validator's bound-path digest
algorithm after final XML observation reconciliation, preserving all native
execution records/capabilities/expiry and source lifecycle policy, then rerun
root gates to expose the actual current physical-evidence state. Do not bypass
or label that gate green from supplemental XML calculations.


### Actual Xcode app link failure before repair

Direct production target build64596 CLOSEDexit65 after successful Swift source
compilation and SharedKit framework phase. The only failing command is the
arm64 application Ld: unresolved _sqlite3_* symbols from SQLiter's sqlite3
cinterop (bind/open/prepare/step/column/close). Acceptance: add inherited-preserving
-lsqlite3 system library linkage to production and acceptance app Debug/Release
configurations in the checked-in Xcode project, not as an ephemeral CLI override.
Preserve all other flags/frameworks/SDK/deployment/signing settings. Then rerun
actual production target and XCTest target builds sequentially with available
SDK. Workspace schemes remain separately blocked by missing local iOS26.5
platform component; no destination/runtime success is inferred from direct link.


### Full Android run: late permission dialog synchronization (before repair)

Unfiltered connectedAndroidTest candidate v1 executed the PoC journey and failed `MealDiaryJourneyInstrumentationTest.textMealCanBeSavedCorrectedAndDeletedThroughProfile`: missing capture_text. NativeUiSemanticDiagnostics proves the foreground/active root is com.android.permissioncontroller at13:45:50, rather than an absent Compose control. The test waits only3000ms for permission_deny_button, then waits15000ms exclusively for capture_text; a later permission prompt remains modal and cannot be handled. Evidence is saved in mobile/build/verification/mvp-audit/native-failure-diag.log and connected-durable-full-v1.log. This is an actual native assertion RED. Acceptance: synchronize on either the real text-entry control or the real system permission-deny control within one bounded deadline, deny the actual prompt when present, then assert capture_text. Do not pregrant/revoke permission, bypass the flow, inflate all timeouts or weaken assertions. Remaining flavors/current full run must finish before any repair/build.
