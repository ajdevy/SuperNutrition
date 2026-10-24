# D-07 local retention verification

Baseline: `7309e140b9cf12c6acf5733cfdcec00ce5912f1b`, branch `codex/mvp-completion`.
Owner approval is recorded in D-07 before implementation. Findings 1–3 in
`mvp-durable-catalog-findings-2026-10-24.md` define the acceptance conditions.

## Red before implementation

Added `ProfileScreenComposeTest.clearing an existing optional name submits its deletion`
using only the pre-existing API: start with Alex, clear the field, press Save,
expect the host to receive an empty string. The test ran and failed because Save
was disabled and the submitted value remained null. The selected class executed
8 tests with 1 failure, 0 errors, 0 skipped; Gradle exited 1 after 6m17s.

Command: `:feature:photo-analysis-ui:jvmTest --tests '*ProfileScreenComposeTest*'`
with JDK 21 from Android Studio and `--no-daemon --max-workers=1 --console=plain`.
Preserved local evidence:
`mobile/build/verification/mvp-audit/retention-red.log` and
`retention-red-ProfileScreenComposeTest.xml`.

## Implementation and pending evidence

- Generic closed-name bounded UTF-8 document port lives in core config;
  platform adapters contain native filesystem APIs. No new module or external
  dependency library is introduced.
- Android uses app-private noBackupFilesDir, AtomicFile, explicit fd.sync,
  serialized access and exact committed-byte readback. The latter is necessary
  because AtomicFile can log a failed rename without throwing.
- Apple uses protected atomic files under an excluded Application Support
  directory. The parent exclusion covers temporary replacements; file size is
  checked before allocating NSData, and malformed UTF-8 remains an error.
- Only production composition activates durable documents. Acceptance/PoC
  sessions stay isolated in memory. Profile clearing is a stored empty optional
  name, and failed writes preserve the editor and its draft.
- Corrupt/unreadable documents remain untouched. Profile and history explain
  the load failure; Home and Profile counts show unavailable rather than zero.
- Native adapter tests are authored but have not yet run. Android has three
  isolated fixture tests; Apple has backup/protection/Unicode/empty roundtrip.
  Native execution and Apple arm64/test compilation remain parent integration
  work after the focused JVM validation.

## Focused JVM run v1

`retention-focused-v1.log` completed naturally with exit 0, `BUILD SUCCESSFUL`
in 8m04s, 62 tasks (31 executed, 31 up-to-date). No Gradle build remains running.
The command requested all four module suites without a test filter:

```text
./mobile/gradlew -p mobile :core:config:jvmTest :feature:photo-analysis-presenter:jvmTest :feature:photo-analysis:jvmTest :feature:photo-analysis-ui:jvmTest --no-daemon --no-configuration-cache --max-workers=1 --continue --console=plain
```

Fresh XML observations, each with 0 failures, 0 errors and 0 skipped:

| Module | Suites | Tests |
| --- | ---: | ---: |
| core/config | 5 | 36 |
| photo-analysis-presenter | 8 | 43 |
| photo-analysis | 12 | 74 |
| photo-analysis-ui | 60 | 270 |

The Profile UI class ran 10 tests, including the formerly red clear/save case,
failed persistent save preserving the draft, and load-failure/count-unavailable
presentation. The new profile-state and shared-storage tests ran successfully.
Results are under each module's `build/test-results/jvmTest/`.

**Input limitation:** the final `ProfileNamePolicy.canSubmit` follow-up and its
third policy test were added after the presenter compilation/test task in this
run. That test is absent from this run's XML (the policy class ran its first two
tests). Therefore v1 is provisional for the final presenter/UI source and is
not a final complete verification claim. Per the parent integration plan, no
redundant broad rerun was started before the next USDA changes. The final stable
integration suite/gates must compile and run the follow-up too.

`git diff --check` passed after source edits. A manifest of the 45 mobile files
owned by this slice was saved locally at
`mobile/build/verification/mvp-audit/retention-owned-files.txt`.
No commit, push, full quality gate, Android instrumentation, Apple compilation,
or native execution was performed by this worker. Those remain integration work.
