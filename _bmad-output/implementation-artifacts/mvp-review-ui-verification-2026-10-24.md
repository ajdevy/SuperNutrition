# MVP UI review repair verification — 2026-10-24

Scope: bounded quick-dev step04 PATCH invocation for R3 and R6 only, against
`137293faec3e2003df72fb571dfa2fe92179037f` on `codex/mvp-completion`, worktree
`/Users/anastasia/.codex/worktrees/f2ff/SuperNutrition`. Existing parent-owned
findings and approved frozen spec are unchanged. Source/test ownership is
`mobile/feature/photo-analysis-ui/`; no policy, host, dependency or lock changes.

## Environment and commands

Intel macOS (`uname -m`: `x86_64`). All Gradle runs use:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export DEVELOPER_DIR='/Applications/Xcode-26.6.0.app/Contents/Developer'
```

Regression RED, before production edits (session 14026):

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest \
  --tests '*MealCatalogLinkFailureTest' --tests '*MealCatalogPortionCopyTest' \
  --dependency-verification strict --continue --no-daemon \
  --no-configuration-cache --max-workers=1 --console=plain
```

Log: `mobile/build/verification/mvp-audit/review-ui-red/red.log`.

## Results

RED CLOSED session 14026, exit 1 in 6m 17s: **4 tests / 4 failures / 0 errors / 0 skips**,
2 fresh suites, XML mtimes 2026-10-24T12:30:23Z. Both URL tests reproduce the
uncaught `IllegalArgumentException`; the bound error actually contains
"or leave it empty", and the required helper is absent. XML copied before
production changes to `mobile/build/verification/mvp-audit/review-ui-red/xml/`;
exact counts/messages/timestamps in adjacent `summary.json`.

Parent subsequently assigned final arm64 compilation to the overall integration
run after core storage changes. This UI-only invocation therefore leaves that
compile explicitly pending parent evidence; no iOS execution is claimed.

## Repair

R3 uses one shared `CatalogSourceLink` implementation for source, licence and
all attribution links. It catches only `IllegalArgumentException`, keeps an
inline accessible live-region message local to the failing URL, and clears it
only after successful opening (or sheet disposal). Platform and explicit host
handlers use this same boundary. Selected meal/query/note stay in the form.

R6 selects the positive-grams helper and `PORTION_INVALID` message from the
current draft's explicit binding. Removal returns to original optional manual
copy; free-text validation is unchanged. Copy fields are appended with defaults
so hosts can localize them without API rewiring.

Final full-module results are recorded below. No partial/cached output is
treated as executed-test or iOS-runtime evidence.

Full verification candidate v1 (session 51633):

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest \
  :feature:photo-analysis-ui:detekt :feature:photo-analysis-ui:koverXmlReportJvm \
  --dependency-verification strict --continue --no-daemon \
  --no-configuration-cache --max-workers=1 --console=plain
```

Log: `mobile/build/verification/mvp-audit/review-ui-green/full-v1.log`.
The raw JVM Kover report is supplemental: this UI denominator has a
native-equivalent classification in `coverage.toml`. Neither generated Compose
outcomes nor existing module gaps are removed, waived or claimed as 100%.

## Owned files

Production:

- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/CatalogSourceLink.kt`
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealCatalogSourceInfo.kt`
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealCatalogCopy.kt`
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealForm.kt`
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealFormCopy.kt`

Tests:

- `mobile/feature/photo-analysis-ui/src/commonTest/kotlin/com/supernutrition/feature/photoanalysis/MealCatalogLinkFailureTest.kt`
- `mobile/feature/photo-analysis-ui/src/commonTest/kotlin/com/supernutrition/feature/photoanalysis/MealCatalogLicenceFailureTest.kt`
- `mobile/feature/photo-analysis-ui/src/commonTest/kotlin/com/supernutrition/feature/photoanalysis/MealCatalogPortionCopyTest.kt`

Public host handoff: only defaulted appended copy properties
`MealCatalogCopy.linkOpenFailed`,
`MealFormCopy.selectedProductPortionDescription`, and
`MealFormCopy.selectedProductPortionInvalid`. No host call-site changes needed.
Parent owns the final combined `graphify update .` and Apple compile evidence.

## Candidate v1 concrete lint findings — recorded before repair

CommonMain detekt emitted four `FunctionSignature` errors at
`MealFormCopy.kt:35–37`: no whitespace after opening parenthesis, a single
space before the second parameter, no whitespace before closing parenthesis,
and newline before the expression body. The two-parameter extension signature
fits on one line; its multiline expression belongs on the following line.
Acceptance: change this signature/expression formatting only, preserve both
selected/manual messages and validator behavior, then rerun full UI tests,
module detekt and supplemental Kover. At this before-repair checkpoint, candidate v1 was still running under
`--continue`; no repair was applied while it owned the build slot.

Fresh candidate v1 JVM XML already confirms 78 suites / 305 tests / 0 failures
/ 0 errors / 0 skips, oldest XML 2026-10-24T12:39:10Z. This does not convert the
whole still-running candidate into a pass.

Candidate v1 typed test lint additionally reports `UseRequire` at
`MealCatalogLinkFailureTest.kt:37:33`: the intentionally unavailable URI handler
uses a conditional explicit `IllegalArgumentException`. Acceptance before
repair: use `require(available)` with the identical failure message and preserve
the failure/retry state, exact URL counts and retained-draft assertions.

Typed `detektMainAndroid` and `detektMainJvm` each emit **6 compiler errors
during analysis**, with an explicit reporting-accuracy warning. This is the
preexisting flattened-KMP analysis diagnostic, separate from the actual JVM
and Android compilations, both of which passed. It is preserved without
suppression or reinterpretation as a product compiler failure.

Candidate v1 CLOSED session 51633, exit 1 in 12m 43s: 305 tests / 78 suites pass;
only the recorded formatting and test guard lint conditions fail. Actual JVM
and Android main compilation passed. Reports copied under
`mobile/build/verification/mvp-audit/review-ui-green/v1/` before repairs.

After those two mechanical repairs, candidate v2 was started (session 80158,
`full-v2.log`) immediately before parent requested the Gradle slot for native
storage RED. It was stopped during startup by terminating only its owned
wrapper PID 31674. Session exit 143; its specific single-use daemon PID 31726
then exited. No tests or final lint result from this interrupted attempt are
claimed. UI source is stable while parent temporarily owns the build slot.


Parent returned the Gradle slot after its independently owned API29 regression
run. Full UI candidate v2 resumed as session 95851, same complete command above,
log `mobile/build/verification/mvp-audit/review-ui-green/full-v2-retry.log`.
The interrupted `full-v2.log` remains intact. Parent changes only Android app
runner/Application/tests outside this module's dependencies during this run.

## Final executed result

Full candidate v2 retry **CLOSED session 95851, exit 0, 8m 13s**. The complete
command is the same full-module command above. All **78 suites / 305 tests /
0 failures / 0 errors / 0 skipped** executed; the seven new R3/R6 regression
cases pass. Test XML mtimes range from **2026-10-24T12:56:04.259Z** to
**12:56:04.559Z**, all newer than this attempt's start.

Final immutable copies for this invocation:
`mobile/build/verification/mvp-audit/review-ui-green/final/` contains all 78 test
XMLs, checkstyle XMLs, `reportJvm.xml`, and machine-readable `summary.json`.
The source reports may subsequently be regenerated by parent integration runs;
these archived copies retain this invocation's evidence.

Fresh detekt reports have **0 issues**:

| Report | UTC mtime |
|---|---|
| commonMainSourceSet.xml | 12:56:37 |
| commonTestSourceSet.xml | 12:56:44 |
| mainAndroid.xml | 12:57:50 |
| mainJvm.xml | 12:58:23 |
| testJvm.xml | 12:59:01 |

Unchanged androidMain/jvmMain/jvmTest source-set checks were UP-TO-DATE. Old
`format.xml` at 06:11 contains 2 historical findings and is **not** a result of
this run; no formatting task was executed or claimed. The two typed-main
analysis accuracy warnings remain exactly 6 each, separately from actual
successful compiler/test tasks. No warnings were suppressed.

Supplemental Kover is fresh at **12:59:05Z**. Module raw coverage is
**5988 / 6707 executable lines (89.28%)**, **1970 / 2786 branch outcomes
(70.71%)**. Neither a JVM branch floor nor changed raw 100% is claimed from this
report; the UI native-equivalent denominator and final evidence handling remain
under parent integration.

For the five changed production files against HEAD 137293f, raw changed-line
coverage is **42 / 48**, raw changed branch outcomes **31 / 46**. Exact source
line/outcome counters are preserved in `final/summary.json`:

| File | Changed lines hit/total | Raw branches hit/total |
|---|---:|---:|
| CatalogSourceLink.kt | 19/19 | 14/20 |
| MealCatalogSourceInfo.kt | 8/8 | 7/10 |
| MealCatalogCopy.kt | 1/1 | 0/0 |
| MealForm.kt | 5/5 | 6/6 |
| MealFormCopy.kt | 9/15 | 4/10 |

The six unhit lines in MealFormCopy.kt:38,40–44 are existing error-display
cases (`NAME_TOO_LONG`, ingredient length/count/empty errors, `NOTE_TOO_LONG`)
reindented by the enforced expression-body signature formatting. Their zero
hits remain visible; they were not excluded or relabeled as compiler branches.
Other raw missed outcomes are at CatalogSourceLink.kt:21,26,36 and
MealCatalogSourceInfo.kt:84; no 100% claim or unproven blanket synthetic-branch
classification is made. Both newly introduced bound/manual portion outcomes
and failure/success/retry behavior are executed by the regression tests.

`git diff --check` passed after the final source changes. No staging, commit,
push, policy, lock or baseline changes. Gradle mutex released to parent after
archiving final evidence; no remaining child build process. Apple arm64
compilation, overall native fixtures, final graph refresh and final integration
quality gates remain parent-owned pending work.
