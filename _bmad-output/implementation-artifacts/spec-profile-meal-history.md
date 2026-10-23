---
title: 'Open the designed Meal history from Profile'
type: feature
created: '2026-10-23'
status: done
baseline_commit: 527e08d08d5cfa35cde0529e3412812d3a5ca7f9
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="Direct human instruction to implement the linked screen">

## Intent

**Problem:** Profile currently presents meal history in a basic dialog. The human requested a separate screen matching Figma node 604:893.

**Approach:** Replace the dialog with a shared Compose history destination, reached from the existing Profile history card. Reuse session ledger data, fonts and exact Figma assets. Implement local search, dated sections, selectable meal cards and honest empty/no-results states. Preserve the previous Profile and Home navigation.

## Boundaries & Constraints

**Always:** Use confirmed-food ledger entries; group by local day and meal ID, so Profile count and history use one implementation. Dates come from the stored epoch day. Capture the reference day when history opens; do not treat a static design date as current. Time, photos and dish titles absent from the ledger must not be fabricated. Use the Figma placeholder for actual records without photos. Card selection shows a read-only summary of the actual entry. Names, search terms and food contents stay in memory; only non-sensitive destination/scroll state may enter saved state. Preserve 48dp hit targets, native safe areas, scrolling and readable 200% text.

**Ask First:** New persistence, remote history, image retention, telemetry or a changed confirmation/nutrition contract.

**Never:** Seed production meals from the design, invent timestamps/nutrients, rewrite camera confirmation, alter the existing Apple UTC/Android-local ledger-day convention, or change the public website deployment workflow. Its GitHub authentication remains pending independently.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Open and return | Profile history card | Dedicated Meal history; header/system Back returns to Profile, then Home | Hide bottom navigation |
| Recreate | History route active | Destination survives Activity recreation | Search/food data is not system-saved |
| Populated ledger | Multiple entries/meal and dates | One card per (day, meal), newest day first, stable within-day order | Missing photo/time uses placeholder/availability copy |
| Search | Whitespace, mixed case, names, no match | Filter actual meals without mutating data | Clear empty result, distinguish empty ledger |
| Card tap | Real card | Read-only actual meal summary | No fake detail route or data write |
| Accessibility | 360x780 at 200% text | Wrapping, scroll and reachable actions | No clipped fixed-height text |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/` — Profile action/card count, shared flow, history renderer/model/copy/date boundary.
- `mobile/core/ui/src/commonMain/kotlin/com/supernutrition/core/ui/UiAutomationId.kt` — stable history semantics.
- `mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt` — thin destination and system Back integration.
- `mobile/feature/photo-analysis/src/iosMain/kotlin/com/supernutrition/feature/photoanalysis/ProfileViewController.kt` — same shared flow on canonical Apple hosts.
- `mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PhotoAnalysisComposeSemanticsInstrumentationTest.java` — native open/back/recreation regression.
- `mobile/feature/photo-analysis-ui/figma-assets-20261023-meal-history/` — unchanged downloaded vectors and reference photos.

## Tasks & Acceptance

**Execution:**
- [x] UI module — add tested aggregation/search, dedicated screen and shared flow; remove obsolete history dialog.
- [x] UI resources — render original static SVG assets; keep example photos as test fixtures and native chrome native.
- [x] Host factories — connect shared route on Android and canonical Apple; preserve Profile/Home state and Back.
- [x] Tests — watch native regression fail first; cover actual/empty/searched data, date grouping, selection and 200% layout; render design fixture separately.
- [x] Verification — full UI/unit tests, complete Android instrumentation on emulator, arm64 Kotlin compilation, read-only quality gates with --continue, graph refresh and diff checks.

**Acceptance Criteria:**
- Given Profile, when Meal history is activated, then the dedicated Figma screen appears with search, correct data/empty state and no bottom bar.
- Given valid confirmed ledger entries, when browsing or searching, then card count, names and dates agree with actual entries and unavailable metadata is explicit.
- Given history, when header/system Back or recreation occurs, then the route and previous Profile remain coherent without saving sensitive text in system state.
- Given the design fixture at 375x812 and large text at 360x780, when rendered, then assets occupy the correct slots and all actions remain usable.

## Design Notes

Figma screen is 375x812, background #F6F7F5. Header uses Fraunces 20sp/600 with 38dp circle inside a 48dp target. Search is 343x48 at x16/y104 with 20dp search icon; list starts y172. Figma muted #A08D80 is darkened to #7D6B5F for AA text contrast; section labels remain 13sp; white 20dp-rounded cards use 90x69 thumbnails with 16dp corners, 14dp gap, 13sp clock/time, 16sp bold title, 14sp subtitle and 18dp chevron. Placeholder art is exactly 52x32. Normal card min-height is 77dp and grows with enlarged text. The existing Back SVG is byte-identical and reused. Photos/time/title overrides from Figma are fixture data, not real history.

Production currently does not bridge photo-review confirmation into the grounded ledger. The existing ledger is empty by default. Adding that bridge would change food-grounding/storage behavior and is outside this screen request. Existing Apple day calculation uses UTC; Android uses local epoch days. Relative labels use that same host convention; stored days are unchanged.

## Verification

Use one Gradle run at a time with Android Studio JDK21. Targeted red instrumentation runs before production changes; then run complete UI tests, read-only quality gates and complete connectedAndroidTest. Explicitly compile modified iosMain with -Psupernutrition.enableIosDeviceTarget=true on this Intel host. Reuse the human's emulator-only decision: expired physical evidence remains unchanged and the formal policy failure is reported. Full commands/results go in `meal-history-verification-2026-10-23/README.md`; no green aggregate claim from blocked policy, old reports or Apple syntax-only checks.


## Implementation notes

The reference clock follows the existing host ledger convention: Android local day, Apple UTC day. Shared `ProfileFlow` owns the history destination and saves only its visibility/scroll position. Android registers a nested system Back handler; the Apple controller uses the same flow. Date labels include the year when it differs from the freshly captured reference year. History reuses the existing Fraunces/Nunito font helpers; loaded-resource and idle-frame waits precede visual capture. Non-normal Nunito weights are declared through the standard resource Font API; its default variation settings are retained. No separate platform font configuration remains.

## Suggested Review Order

**Shared destination**

- One flow opens history and preserves Profile state across both hosts.
  [ProfileFlow.kt:16](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/ProfileFlow.kt#L16)

**Native navigation**

- Android system Back returns through History, Profile and Home.
  [PhotoAnalysisComposeActivity.kt:617](../../mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt#L617)

- Canonical Apple host uses the same shared destination.
  [ProfileViewController.kt:19](../../mobile/feature/photo-analysis/src/iosMain/kotlin/com/supernutrition/feature/photoanalysis/ProfileViewController.kt#L19)

**Confirmed data**

- One adapter supplies the count and groups real entries by day and meal.
  [MealHistoryModels.kt:25](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealHistoryModels.kt#L25)

- Unicode whitespace and all-word matching filter actual names locally.
  [MealHistoryModels.kt:34](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealHistoryModels.kt#L34)

**Dates and presentation**

- Stored days stay timezone-free; relative dates follow the host ledger convention.
  [MealHistoryDates.kt:33](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealHistoryDates.kt#L33)

- The dedicated screen owns search, dated sections and empty states.
  [MealHistoryScreen.kt:49](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealHistoryScreen.kt#L49)

- Cards wrap at enlarged text and expose missing metadata honestly.
  [MealHistoryCard.kt:36](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealHistoryCard.kt#L36)

**Verification**

- Fixture-only photos verify geometry, readable contrast and 200% scrolling.
  [MealHistoryLayoutTest.kt:39](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/MealHistoryLayoutTest.kt#L39)

- Native regression exercises open, recreation and both Back paths.
  [PhotoAnalysisComposeSemanticsInstrumentationTest.java:93](../../mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PhotoAnalysisComposeSemanticsInstrumentationTest.java#L93)

## Final verification limits

Implementation and requested emulator validation are complete. Full read-only gates fail on expired physical bootstrap-android-host evidence; typed Detekt also has the documented expect/actual analysis limitation. This is not a merge-ready/aggregate-coverage claim. Apple arm64 compilation passed; Apple execution was not performed. Detailed commands/counts, red regressions, reviewer closure and APK digest are in the sibling verification report. No sprint story_key is attached, so sprint synchronization is not applicable. Changes remain local until explicitly requested for push.
