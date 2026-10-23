---
title: 'Open the designed Profile from Home'
type: feature
created: '2026-10-22'
status: in-progress
baseline_commit: 8062c3f66efd8f749ac7041b6ac97c29b8a0d9dc
review_loop_iteration: 1
context:
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="User requested implementation and integration in this chat">

## Intent

**Problem:** Home's Android avatar opens a diagnostic deep-link placeholder; the iOS Home has no profile entry. Users need the Profile screen supplied in Figma node 604:921.

**Approach:** Implement the shared Compose profile using the existing fonts and the exact Figma icons. Connect the Home avatar on both production hosts. Keep the existing external profile deep-link contract separate. First integrate the outstanding Home commit and mascot research; leave superseded and explicitly parked historical branches alone.

## Boundaries & Constraints

**Always:** Preserve Home and Rewards navigation, safe-area handling, accessibility roles, 48dp interactive targets, and readable overflow at 200% text. Use actual distinct meal IDs from the in-memory ledger for counts. Name editing is optional and session-scoped: an unknown name is shown as 'Your name', never inferred from the design's example. Back returns to Home; profile has no bottom navigation. Existing external profile IDs remain compatible.

**Ask First:** New telemetry destinations, persisted identity, or invented legal policy content.

**Never:** Upload diagnostics, enable consent silently, use fake saved meals, change photo-analysis policy, modify the dirty primary checkout, or resurrect closed/superseded branches. Legal links are used only if supplied; unavailable documents and diagnostics have an explicit explanatory action.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Home avatar | Home visible | Profile opens, tab bar hidden | No photo action |
| Return | Profile open | Header Back and Android Back return to prior Home | Route survives recreation |
| Name | Unknown or edited name | Initial/placeholder; valid trimmed edit survives returning during session | Empty input does not save |
| History | Empty or populated ledger | Distinct meal count and real ledger entries | Honest empty state |
| Legal/diagnostics | No approved URLs/provider | Explain unavailability; diagnostics remain off | No external transfer |
| Accessibility | Narrow viewport, 200% text | Content readable by scrolling, controls reachable | No clipped fixed-height rows |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/` — Profile screen, copy, state and existing Fraunces/Nunito helpers.
- `mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt` — production Home shell and separate legacy deep-link route.
- `mobile/feature/photo-analysis/src/iosMain/` — shared Compose UIViewController factory.
- `mobile/apps/sharedKit/src/` — thin Apple factory and automation exports.
- `mobile/apps/iosApp/PhotoAnalysisHostSupport.swift` — Home avatar and native route only.
- `mobile/core/ui/src/commonMain/kotlin/com/supernutrition/core/ui/UiAutomationId.kt` — stable semantic IDs.

## Tasks & Acceptance

**Execution:**
- [x] Audit worktrees, staged Rewards equivalence and PR history; integrate 5e4dd347 and mascot research locally.
- [x] Add shared Profile screen and local Figma assets; cover actions, actual/empty data, name edits and large text with Compose tests.
- [x] Wire Android Home route with saved state and header/system Back; preserve deep-link behavior; add native navigation regression.
- [x] Wire Apple Home avatar to the same Compose screen with thin factories and automation IDs.
- [x] Run focused tests, full relevant UI suite, read-only quality checks, Android instrumentation, Apple compilation, diff validation and graphify update. The formal aggregate gates remain blocked by expired physical Android evidence; the human explicitly accepted emulator-only verification. Website Git connection/publication remains in progress.

**Acceptance Criteria:**
- Given Home, when the avatar is activated, then the Figma Profile appears with its name row, history, legal rows and off diagnostics control.
- Given Profile, when Back is activated or the Android activity is recreated, then navigation remains predictable and Home state is preserved.
- Given unknown identity and any ledger contents, when Profile opens, then no example identity or meal count is presented as real user data.
- Given no approved diagnostic provider, when diagnostics is activated, then it remains off and explains availability without any transfer.
- Given a 360dp viewport and 200% font scale, when Profile is displayed, then all actions remain reachable without text overlap.

## Spec Change Log

- 2026-10-22: Human expanded scope to generate and publish app Privacy Policy and Terms on supernutrition.app. Existing site repository is independent, at flexsent-labs/supernutrition.app; preserve its waitlist /privacy/ notice, add app /privacy-policy/ and extend /terms/ in EN/RU/ES. User supplied Flexsent labs SIA, Latvia; use already-published hello@supernutrition.app. Profile delegates document actions to native URL handlers after publication. No change to telemetry, routing or retention policy is authorized by factual disclosure work.

- 2026-10-23: Human chose emulator-only Android verification and requested that physical-device limitations be stated. Required physical records remain unchanged and their policy failure is reported. Website publication must use committed GitHub source and a reproducible build from flexsent-labs/supernutrition.app; existing Worker is being connected to Workers Builds, with no local production upload.

## Design Notes

Figma reference uses a 375x812 screen, #F6F7F5 background, #253F26 text, 80dp initial avatar, 16dp outer gutters, green history card and white 20dp-rounded settings card. Status bars and home indicator are native chrome, not assets to duplicate. Product data replaces Anastasia/24-meals illustration values. The unavailable diagnostic switch uses the exact off-state artwork and an explanatory action; it does not imply consent or transmission.

## Verification

- Focused and complete `:feature:photo-analysis-ui:jvmTest`, `:core:ui:jvmTest`, and relevant host tests; verify XML counts.
- `qualityCheckJvmAndroid --continue`, `qualityCheckApple --continue`; report each gate's actual scope and failures.
- Android connected instrumentation on one explicit emulator serial; check productionDebug and uiAcceptanceDebug XML reports.
- `:feature:photo-analysis:compileKotlinIosArm64 :apps:sharedKit:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true` on Intel host.
- `git diff --check`, `graphify update .`; no generated build output staged.

## Suggested Review Order

**Navigation**

- Home keeps route state and returns without losing the underlying tab content.
  [PhotoAnalysisComposeActivity.kt:633](../../mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt#L633)

- Both canonical Apple hosts use the shared Compose profile and one session.
  [PhotoAnalysisHostSupport.swift:340](../../mobile/apps/iosApp/PhotoAnalysisHostSupport.swift#L340)

**Profile and data**

- The screen renders supplied session data with the Figma geometry and native actions.
  [ProfileScreen.kt:49](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/ProfileScreen.kt#L49)

- Optional names remain in memory; blank names are never inferred from examples.
  [SharedKitProfileSession.kt:8](../../mobile/apps/sharedKit/src/iosMain/kotlin/com/supernutrition/sharedkit/SharedKitProfileSession.kt#L8)

- History uses actual meals; unavailable diagnostics explain their state without sending data.
  [ProfileSettings.kt:44](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/ProfileSettings.kt#L44)

**External documents**

- One URL contract feeds Android and Apple handlers.
  [ProfileLegalLinks.kt:4](../../mobile/feature/photo-analysis/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/ProfileLegalLinks.kt#L4)

- The human publication decision is recorded before factual legal-document wiring.
  [OWNER-DECISIONS.md:3](../../.evidence/OWNER-DECISIONS.md#L3)

**Verification**

- Native regression covers opening, editing, recreation, reopening and system Back.
  [PhotoAnalysisComposeSemanticsInstrumentationTest.java:54](../../mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PhotoAnalysisComposeSemanticsInstrumentationTest.java#L54)

- Shared actions cover empty data, name validation, browser errors and Unicode initials.
  [ProfileScreenComposeTest.kt:25](../../mobile/feature/photo-analysis-ui/src/commonTest/kotlin/com/supernutrition/feature/photoanalysis/ProfileScreenComposeTest.kt#L25)

- Geometry and enlarged text are rendered and checked at the target viewport sizes.
  [ProfileLayoutTest.kt:28](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/ProfileLayoutTest.kt#L28)


## Current delivery state

Profile implementation and review fixes are verified with 188 shared UI tests and all three Android instrumentation variants (44 tests each), plus arm64 Kotlin compilation. The complete gate rerun has only the explicitly reported expired-physical-evidence prerequisite. See [verification record](profile-verification-2026-10-22/README.md) for exact commands, report observations and platform limits. The legal website source and deterministic build recipe are on GitHub main at 533b956102c9c80b10cc4d7554d896d1bad044aa with passing CI. Live publication is pending GitHub browser sign-in and connection of the existing Cloudflare Worker.
