# Durable MVP edge-case review — 2026-10-24

Reviewer role: independent Edge Case Hunter, using `.agents/skills/bmad-review-edge-case-hunter/SKILL.md`, including its secondary deletion check. No severity labels are assigned.

## Input and checkout

- Worktree: `REDACTED_WORKTREE/f2ff/SuperNutrition`.
- Branch: `codex/mvp-completion`; candidate HEAD: `137293faec3e2003df72fb571dfa2fe92179037f`.
- Baseline: `7309e140b9cf12c6acf5733cfdcec00ce5912f1b`.
- Supplied complete input: `mobile/build/verification/mvp-audit/review.diff`, 185 file sections, 33,335 lines, 2,683,114 bytes; SHA-256 `b5ce0e8a8fbe1f07510ae134b353134dc57f6661157a7a3e13ffe577be23f667`.
- Supplied inventory: `mobile/build/verification/mvp-audit/review-files.txt`.
- Initial tracked working change: `_bmad-output/implementation-artifacts/spec-mvp-durable-catalog-diary.md` (`status: in-review`). Preserved.

## Findings

```json
[
  {
    "location": "mobile/platform/photo-android/src/androidMain/kotlin/com/supernutrition/platform/photoandroid/AndroidPrivateDocumentStore.kt:20-24",
    "trigger_condition": "A valid base document coexists with a backup directory on Android 29.",
    "guard_snippet": "if (backup.isDirectory) throw IOException(\"Invalid private-document backup\")",
    "potential_consequence": "Reading malformed backup state deletes the last valid diary or profile document."
  },
  {
    "location": "mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealCatalogSourceInfo.kt:90-96",
    "trigger_condition": "The source or licence URL opener throws because no browser handler is available.",
    "guard_snippet": "try { onLink(link.url) } catch (_: IllegalArgumentException) { showLinkFailure() }",
    "potential_consequence": "Source/credit click can terminate the app and discard an unsaved meal draft."
  }
]
```

### Backup-directory read path

`load()` invokes `AtomicFile.openRead()` at line 22 without first applying the backup-directory guard that `save()` already applies at line 32. The installed SDK's primary implementation at `~/Library/Android/sdk/sources/android-29/android/util/AtomicFile.java:224-229` calls `mBaseName.delete()` whenever `.bak` exists, then renames that backup to the base path. If `.bak` is a directory, a previously valid regular base file is deleted before opening fails. `LocalProfile`/`MealDiary` can surface a load failure afterward, but they cannot preserve bytes already deleted by this read. Android 29 is inside the currently declared supported range (`mobile/gradle/libs.versions.toml:10`, `minSdk = "24"`). This is a static source trace; no Android execution is claimed.

Testable condition: on API 29, create a fixture regular base file containing valid prior bytes and a nonempty sibling `.bak` directory. Call `load()` through a fresh `AndroidPrivateDocumentStore`. The operation must report failure while leaving the base file's bytes and the backup-directory contents untouched. Run the same fixture for `MEAL_DIARY` and `PROFILE_NAME`. Existing `PrivateDocumentStoreInstrumentationTest.anObstructedLegacyBackupCannotReplaceThePreviousValue` exercises only `save()`, so it does not traverse the destructive read.

### Failed external-link path

`MealCatalogSourceInfo.kt:46-47` uses `LocalUriHandler.openUri()` unless a host override is supplied. All source/licence buttons reach the unguarded `onLink(link.url)` call at line 93. The changed code contains no failure state or exception handling for this callback. The same module's existing `ProfileScreen.kt:124-135` explicitly catches `IllegalArgumentException` for a missing native HTTPS handler; the catalog path does not reuse that handling. The existing `MealCatalogUriHandlerTest` records successful calls only.

Testable condition: provide a `LocalUriHandler` whose `openUri` throws `IllegalArgumentException`, open source information from a meal form, and click both the source and licence links. Repeat with a throwing `MealCatalogContext.onOpenLink` override. Each click must leave the app and selected meal draft usable, expose an actionable link-open failure, and keep close/retry available. A successful handler must still receive the exact URL once. These conditions were not executed during this review.

## Coverage and limits

The complete supplied diff was loaded and inventoried. The production path pass covered both platform storage adapters, profile retention, diary v1/v2 encode/decode and central write validation, explicit catalog binding and historical edits, nutrient arithmetic/display and daily aggregation, SQLDelight package staging/query/disposal, catalog session cancellation/retry/replacement, Android and Apple composition, chooser/details/profile failure presentation, generator/source pins, and the changed build/quality declarations. Focused tests and referenced current callees were read to distinguish already guarded paths from missing handling. The secondary deletion pass found no additional unhandled deleted behavior.

The generated payload was inspected structurally across all 7,793 rows rather than manually reading each numeric literal: all IDs are unique; every row has nine fields; every known nutrient is finite and nonnegative; maximum product-name length is 134; missing fiber/sugar/sodium counts are 562/1,786/84. The Kotlin string fragments reconstruct the exact package TSV, which in turn exactly matches every imported `F` row; its SHA-256 matches the manifest. This is read-only structural inspection, not an independent USDA nutritional-accuracy audit.

Changed TOML documents were parsed with `tomllib`; verification XML and provenance JSON were parsed, with no duplicate dependency-metadata component or provenance-artifact identities. Lockfile coordinate inventories were inspected. Historical verification/authority documents were read as supplied evidence, not replayed or independently ratified. Existing test assertions were reviewed selectively for the traced boundaries; no claim is made that every test assertion or every historical verification statement was independently audited.

No Gradle, Xcode, JVM unit, instrumentation, device, simulator, remote, staging, commit, or production/test/policy mutation was performed. No runtime test count is produced by this review. Both findings remain statically established and require the acceptance tests above before a fix can be called verified. This report is the reviewer's only authored file; ownership of all other changes remains with the other agents.
