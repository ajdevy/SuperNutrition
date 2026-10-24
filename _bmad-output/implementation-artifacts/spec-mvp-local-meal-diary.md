---
title: Complete the local MVP meal diary and its designed destinations
type: feature
created: 2026-10-24
status: done
baseline_commit: 0f1fbb4e599e0a71accaef5c074466436b9e8e0b
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="direct user instruction to audit and implement missing MVP work">

## Intent

**Problem:** The Figma MVP screens mostly exist, but explicit meal confirmation never creates a history entry, text entry is unreachable, history has no details/editor, reward cards have no destination, and Home reports sample progress. iOS also uses an unrelated Home.

**Approach:** Complete the local meal diary from camera/text confirmation to history and corrections, using shared Compose destinations matching the actual Figma page. Reuse existing reviewed-food and catalog contracts, preserving honest unknown nutrition until the approved G-2 package exists. Deliver the audit and implement every independent local UI gap discovered in this pass.

## Boundaries & Constraints

**Always:** AD-1/4/7/8/15/19/23/25/33/40. Structured reviewed meals are identity-only unless an existing grounded result supplies nutrition. Keep them separate from `ConfirmedConsumedFood` so logging a name cannot earn nutrient rewards. All writes require explicit save, are idempotent per review, and retain no photo path/bytes. Use the existing Nunito/Fraunces, colors, artwork and stable semantics registry. Preserve old API defaults and tests where practical; platform hosts remain composition glue. One module/host ownership invocation at a time, one Gradle build per worktree.

**Ask First:** Enabling durable local user-data retention (question sent; dependent activation waits for the answer). Published contract, source approval, production remote, telemetry or privacy decisions remain the owner's. No new remote operations are part of implementation.

**Never:** Invent nutrient values, nutrient-driven rewards, clock metadata, ingredient identities, catalog bindings, physical-device evidence or successful iOS execution. Do not mutate immutable contracts, expired device records, pending release gates or unrelated WIP. No raw-photo retention, accounts, sync or paid remote enablement.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
| --- | --- | --- | --- |
| Reviewed photo | Valid reviewed selection | Save one named record with explicit ingredients, actual local date/time and unknown nutrition | Reject empty selection; keep review on storage failure |
| Text meal | Authored name, optional positive portion, comma-separated ingredients | Validate and save explicit user content without claiming catalog grounding | Invalid/negative/nonfinite portion and empty name show inline errors |
| History details | Existing reviewed or grounded meal | Dedicated detail, ingredient list and honest unavailable numbers; serving/per100g switch | Zero/unknown mass cannot produce fake normalized numbers |
| Correction | Edit existing record | Replace that ID; cancel preserves original; delete removes it | Stale IDs fail without inserting duplicates |
| Rewards | Any of seven cards | Detail with current progress, rules and supported evidence | Empty ledger shows zero/unavailable, never mock meals |
| Home | Empty/populated diary | Actual date/count and unknown nutrient progress | No invented goal completion or streak |
| Restart | Retention decision | Activate approved storage only, recover structured records and optional name | Malformed/unsupported storage must not become fabricated valid records |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis-presenter/src/commonMain/` — shared reviewed-meal/draft models and validation.
- `mobile/feature/photo-analysis/src/commonMain/` — observable diary, explicit save/edit/delete and existing review coordinator.
- `mobile/feature/photo-analysis-ui/src/commonMain/` — manual form, details/editor, history/profile count, camera hint, reward details, truthful Home model.
- `mobile/feature/photo-analysis/src/androidMain/` and `src/iosMain/` — production callbacks, shared Home bridge and native storage adapters as authorized.
- `mobile/apps/sharedKit/src/iosMain/` — session bridge for shared diary and Home.
- `mobile/apps/iosApp/PhotoAnalysisHostSupport.swift` — arm64 shared Home composition.
- `mobile/core/ui/.../UiAutomationId.kt` — shared stable identifiers.

## Tasks & Acceptance

**Execution:**
- [x] Presenter module: add typed identity-only records/drafts and exhaustive validation/codec tests.
- [x] Feature module: observable diary and exact-once review confirmation; wire both platform callbacks and safe storage failures.
- [x] UI module: reachable text form and camera hint; dedicated meal details with truthful unavailable nutrition and edit/delete; shared Profile/history data.
- [x] UI module: seven reward-detail destinations using actual progress and supported evidence; truthful Home date/count/metrics.
- [x] SharedKit and iOS host in separate invocations: share the diary, render designed Home on arm64, retain documented Intel limitation.
- [x] Core UI invocation: add identifiers used by new controls.
- [x] Verify focused red/green tests, complete relevant modules, JVM/Android quality, arm64 compilation and packaged emulator journeys/screenshots; run graphify update and diff checks.

**Acceptance Criteria:**
- Given camera review, when Add to meal succeeds twice for the same review, then history contains one record and Back/new capture does not delete it.
- Given capture, when the pencil is activated, then Add by text is usable with IME and 200% text and cancel writes nothing.
- Given a saved meal, when its row opens, then the dedicated Figma-derived details and correction route work without fake numbers or retained photos.
- Given an empty grounded ledger, when identity-only meals are saved, then nutrient rewards and nutrient facts remain unavailable, recording-day progress uses real diary dates, and Home's meal count updates.
- Given supported iOS arm64, when Home is shown, then the same shared garden/capybara and profile entry are composed.

## Spec Change Log

- Source audit: recording habit depends on explicit diary dates, not nutrient grounding. Pass typed meal/date evidence separately to Rewards; unresolved meals cannot silently satisfy protein-in-every-meal. Keep the prohibition on invented nutrient/category facts.

## Verification

Focused presenter/feature/UI JVM tests first, then full affected module tests and
`qualityCheckJvmAndroid --continue` with strict dependency verification. Build
production APK and exercise text-save/history/details/edit/delete plus camera
review and rewards on the explicit emulator. Compile affected `iosArm64` targets
with `-Psupernutrition.enableIosDeviceTarget=true`; Intel execution is not claimed.
Record exact XML counts, commit, installed APK digest and screenshots in the
verification report. Expired physical-device evidence remains an explicit known
gate failure, never silently renewed.


### Acceptance evidence before review

Presenter41,feature54,UI254 anddomain272 tests pass. The real packaged Android
text-save/edit/delete journey passes. Manual keyboard entry and save at200%font
with IMEvisible passed; a real imported photo reached review, explicit custom
confirmation saved a third record and Home updated. Camera exact-once and reset
boundaries are covered by focused feature tests. All seven reward destinations
are covered in the full UI suite. iOS arm64 compilation/framework/native-test
compilation and Swift arm64 type checks pass; simulator execution remains the
accepted Intel limitation. Both complete quality entrypoints were invoked with
--continue and only the unchanged expired physical evidence fails policy.

Extended native-suite execution, Swift bundle build and review remain tracked in
the verification report. These do not turn the known source/retention/release
conditions into approved production claims.

## Final available evidence

JVM XML:domain280,presenter41,feature66,UI266 tests;each0 failures/errors/skips.
Clean complete Android instrumentation:PoC51,production51,UI acceptance51;each
0 failures/errors/skips. Production APK and manual keyboard/history/edit/note/
reward flows verified on Android15/API35 x86_64. Arm64 Kotlin/framework/native-test
compilation and actual Xcode production/acceptance/XCTest builds pass. Both root
quality entrypoints fail only expired bootstrap-android-host policy;aggregate
coverage verification is blocked by that dependency. No policy/evidence bypass.
See mvp-verification-2026-10-24.md for commands,reports,digest,screenshots and the
remaining retention/source/inference/physical/Intel limits. Story key is unset;
sprint-status synchronization is intentionally inapplicable.

## Suggested Review Order

**Запись и исправления**

- Явные записи публикуются только после успешного сохранения.
  [MealDiary.kt:9](../../mobile/feature/photo-analysis/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealDiary.kt#L9)

- Исправления синхронно обновляют общий дневник и награды.
  [MealDiaryActions.kt:7](../../mobile/feature/photo-analysis/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealDiaryActions.kt#L7)

**Интерфейс MVP**

- История, детали и редактор используют общую запись и обратимую навигацию.
  [ProfileFlow.kt:16](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/ProfileFlow.kt#L16)

- Текстовый черновик переживает изменения камеры в памяти сеанса.
  [CaptureTextEntry.kt:42](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/CaptureTextEntry.kt#L42)

- Ошибка подтверждения сохраняет выбор и разрешает явную повторную попытку.
  [MealReviewSaveState.kt:10](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealReviewSaveState.kt#L10)

**Достоверность наград**

- Одна проекция исключает устаревшие факты из интерфейса и расчёта.
  [RewardsFeature.kt:80](../../mobile/feature/photo-analysis/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsFeature.kt#L80)

- Счётчик и детали разделяют одинаковую непрерывную серию дней.
  [RecordingStreakDays.kt:6](../../mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition/domain/nutrition/RecordingStreakDays.kt#L6)

**Платформы**

- Android подключает живой дневник к профилю и исправлениям.
  [PhotoAnalysisComposeActivity.kt:607](../../mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt#L607)

- Arm64 использует общий Home; Intel показывает ограничение среды.
  [SuperNutritionApp.swift:55](../../mobile/apps/iosApp/SuperNutritionApp.swift#L55)

**Проверки и вспомогательные контракты**

- Нативный сценарий проверяет сохранение, историю, исправление и удаление.
  [MealDiaryJourneyInstrumentationTest.java:28](../../mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/MealDiaryJourneyInstrumentationTest.java#L28)

- Ограниченный кодек отклоняет повреждённые документы целиком; постоянное хранение не включено.
  [MealDiaryCodec.kt:18](../../mobile/feature/photo-analysis-presenter/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealDiaryCodec.kt#L18)

- Kotlin defaults сохраняют общий helper нативных фикстур без жёсткой арности.
  [PhotoAnalysisCameraPermissionInstrumentationTest.java:299](../../mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PhotoAnalysisCameraPermissionInstrumentationTest.java#L299)
