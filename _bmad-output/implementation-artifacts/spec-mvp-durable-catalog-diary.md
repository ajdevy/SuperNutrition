---
title: Durable local diary with explicitly sourced nutrition
type: feature
created: 2026-10-24
status: done
baseline_commit: 7309e140b9cf12c6acf5733cfdcec00ce5912f1b
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="Human approved both pending MVP decisions in this conversation">

## Intent

**Problem:** The completed MVP loses meal records and the profile name on restart, and deliberately shows unknown nutrition while its source decision is pending. The user has now explicitly approved durable local retention and the proposed USDA source and rules.

**Approach:** Retain user-authored meals and the optional profile name privately on each device until deletion. Offer an explicit USDA product and gram selection and derive nutrition from the pinned approved package. Persist that binding with the meal so restart, edit and deletion share one authority.

## Boundaries & Constraints

**Always:** Follow owner decisions D-07/D-08. Show exact product/preparation, USDA FoodData Central SR Legacy 2018-04, and US catalog coverage before confirmation. Require explicit grams. Unknown nutrients stay unknown individually and in totals. Keep unrestricted text/photo entries usable without numeric claims. A failed write must remain visible as a failure; malformed saved data must not be silently replaced. App-private documents must be excluded from cloud/device backup. Preserve existing Figma layouts, capybara placement, accessibility and camera flows.

**Ask First:** A different dataset, retention destination or remote processing requires another owner decision.

**Never:** Persist raw photos or transient drafts, infer food matches from typed names, split whole-meal mass across ingredients, enable FNDDS recipes, remote consent, telemetry or public accuracy claims. Do not alter frozen released contracts or fabricate physical-device evidence. No push or remote operations in this workflow.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Restart | Saved meal/name, app terminated | Records/name restored exactly | Visible load failure if unreadable |
| Delete | User deletes a meal or clears name | Removal survives restart | Failed deletion retains previous value |
| Catalog meal | Explicit product and positive grams | Deterministic per-portion and per-100g values with provenance | Invalid/missing weight blocks binding |
| Free text | No explicit catalog choice | Identity-only saved meal | No inferred nutrients |
| Missing source field | Selected food lacks fiber/sodium | That value and affected total stay unknown | Other sourced values remain available |
| Correction | Name/ingredient/weight changes | Revalidate binding; no stale values or rewards | Invalid correction keeps prior record |
| Corruption | Malformed/unsupported document or bad package | Fail closed; never partial restore or activate bad package | Preserve original bytes and explain failure |

</frozen-after-approval>

## Code Map

- `.evidence/OWNER-DECISIONS.md` — direct human approvals recorded before implementation.
- `mobile/feature/photo-analysis-presenter/` — saved-meal binding, bounded versioned codec and presentation models.
- `mobile/feature/photo-analysis/` — atomic diary writes and shared projections.
- `mobile/domain/nutrition/` — existing identity matcher, immutable v1 contracts and deterministic nutrition rules.
- `mobile/domain/nutrition/src/commonMain/resources/catalog/usda-sr-legacy.tsv` — pinned imported source, including missing fields.
- `mobile/data/nutrition/` — bounded bundled package, real SQLDelight activation, provenance and reproduction fixtures.
- `scripts/` — reproducible catalog generation and integrity checks.
- `mobile/feature/photo-analysis-ui/` — explicit selection, details, profile and storage failure copy.
- `mobile/apps/androidApp/`, `mobile/apps/sharedKit/` — platform storage composition and native evidence.

## Tasks & Acceptance

**Execution:**
- [x] Owner record and implementation findings — record approvals, current defects and testable conditions.
- [x] Storage ports/adapters and Android/sharedKit composition — durable atomic meals and profile name, no backups, surfaced failures.
- [x] Catalog package/generator and provenance fixtures — immutable source pin, reproducible integrity validation, documented activation.
- [x] Meal models/codec and nutrition projection — validated explicit bindings, version migration and nullable nutrient arithmetic without changing v1.
- [x] Meal form/details/profile/Home — optional exact product + grams confirmation, truthful values/source and durable delete/clear.
- [x] Focused unit/UI/native tests — prove red before fixes, restart/deletion, invalid values and missing nutrients.
- [x] Full relevant gates, arm64 compilation, emulator checks, graph refresh and local commit — publish exact evidence and limitations.

**Acceptance Criteria:**
- Given explicit saves, when the Android process is restarted, then name and meals remain; after deletion and another restart they remain deleted.
- Given a known USDA row and entered grams, when confirmed and reopened after restart, then its values match source scaling and display its source/version.
- Given an unselected text entry or an invalid/stale binding, when viewed, then no nutrition is fabricated.
- Given missing fiber or sodium, when portion/per-100g and totals are viewed, then missing fields are unknown while known fields remain numeric.
- Given a storage failure, when saving or loading, then the app explains failure and never reports destructive success.
- Given the Intel host and expired physical Android record, when verifying, then compile/emulator results and unavailable physical/iOS runtime evidence remain distinct.

## Spec Change Log

## Design Notes

This continues the already-approved MVP task. Both pending questions were answered affirmatively; no repeat specification approval is required. Keep the previous completed spec as historical evidence rather than rewriting its formerly pending scope. Use one persisted diary authority for nutrient bindings, so deleted/corrected facts cannot reappear after restart. Keep the old grounded-nutrition/v1 representation intact; partial nutrient semantics need a separate typed representation.

## Verification

- Focused JVM tests for changed modules, XML counts and changed-branch coverage.
- Reproducible generator/check tests and corruption/missing-value fixtures.
- `qualityCheckJvmAndroid qualityCheckApple --continue` with strict dependencies; report expired physical policy separately.
- Full `:apps:androidApp:connectedAndroidTest` for all variants on emulator-5554.
- Explicit arm64 SharedKit/iOS compilation on Intel; no simulator success claim.
- Reinstall final production APK, manually verify restart/edit/delete and source values; inspect screenshots immediately.
- `git diff --check` and `graphify update .` after final code changes.


## Suggested Review Order

**Entry and durable ownership**

- One composition root connects the catalog, diary and profile to private storage.
  [LocalUserData.kt:8](../../mobile/feature/photo-analysis/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/LocalUserData.kt#L8)

- A shared revision protocol rejects stale owners before replacement.
  [RevisionCheckedPrivateDocumentStore.kt:7](../../mobile/core/config/src/commonMain/kotlin/com/supernutrition/core/config/RevisionCheckedPrivateDocumentStore.kt#L7)

**Platform commit boundaries**

- Android serializes revisions and commits synced staged bytes with atomic rename.
  [AndroidPrivateDocumentStore.kt:19](../../mobile/platform/photo-android/src/androidMain/kotlin/com/supernutrition/platform/photoandroid/AndroidPrivateDocumentStore.kt#L19)

- Apple retains protected atomic documents outside backups under the same revision protocol.
  [IosPrivateDocumentStore.kt:31](../../mobile/platform/photo-ios/src/iosMain/kotlin/com/supernutrition/platform/photoios/IosPrivateDocumentStore.kt#L31)

**Persisted meaning and nutrition**

- The bounded versioned codec retains explicit source bindings and rejects malformed documents.
  [MealDiaryCodec.kt:18](../../mobile/feature/photo-analysis-presenter/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealDiaryCodec.kt#L18)

- Only the pinned validated package becomes an active SQL catalog.
  [BundledUsdaCatalog.kt:14](../../mobile/data/nutrition/src/commonMain/kotlin/com/supernutrition/data/nutrition/BundledUsdaCatalog.kt#L14)

- Product identity, source digest and positive grams determine sourced values.
  [CatalogMealNutritionResolver.kt:28](../../mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition/domain/nutrition/CatalogMealNutritionResolver.kt#L28)

- Unknown fields stay unknown through scaling and totals.
  [PartialNutrientArithmetic.kt:8](../../mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition/domain/nutrition/PartialNutrientArithmetic.kt#L8)

**User-facing projection and recovery**

- Home filters the requested day before shared merge and nutrition projection.
  [MealDiaryHomeModel.kt:6](../../mobile/feature/photo-analysis/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealDiaryHomeModel.kt#L6)

- Selected products require grams while unrestricted text entries retain optional portions.
  [MealForm.kt:25](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealForm.kt#L25)

- Unavailable source links expose a retryable message without discarding the draft.
  [CatalogSourceLink.kt:21](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/CatalogSourceLink.kt#L21)

**Fixtures and reproducibility**

- Real filesystem regressions exercise conflicts, backup obstructions and commit outcomes.
  [PrivateDocumentStoreInstrumentationTest.java:30](../../mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PrivateDocumentStoreInstrumentationTest.java#L30)

- The test-only runner isolates instrumentation without production testing switches.
  [IsolatedPrivateDataTestRunner.java:10](../../mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/IsolatedPrivateDataTestRunner.java#L10)

- Preserve installed apps and ordinary documents when connected-test teardown completes.
  [gradle.properties:32](../../mobile/gradle.properties#L32)

- Regeneration verifies source identity before producing the immutable numeric package.
  [generate-catalog-from-srlegacy.py:539](../../scripts/generate-catalog-from-srlegacy.py#L539)

- The verification record distinguishes executed evidence from expired physical and unavailable iOS runtime checks.
  [mvp-durable-catalog-verification-2026-10-24.md:970](mvp-durable-catalog-verification-2026-10-24.md#L970)
