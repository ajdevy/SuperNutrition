---
title: 'Native camera permission recovery and Home/Rewards layout corrections'
type: 'bugfix'
created: '2026-10-11'
status: 'in-review'
baseline_commit: '5ee5de077866feed70b418ee2760af24322b1d22'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent from the user's camera, Home, Rewards, device-check, and latest-main requests">

## Intent

**Problem:** Physical-device review and the user's feedback expose missing camera permission recovery, a thumbnail-sized Home mascot, elevated bottom navigation, and a clipped next-step card. The user also expects the previously implemented Rewards screen from latest main.

**Approach:** Preserve the existing Figma camera/review flow and rewards feature state while correcting native permission entry/recovery and measured Home/navigation layout. Pull latest main, locate the existing Rewards implementation, and verify the resulting screens on the connected physical Android phone.

## Boundaries & Constraints

**Always:** Keep photo/camera bridges behind ports, permission decisions in the native host, and shared presentation platform-neutral. Preserve centered care actions, foreground mascot feet, custom-meal plus, review selection, the simple review instruction, responsive scrolling, localization, stable automation IDs, and unrelated working-tree edits. Use one Gradle build at a time.

**Ask First:** New remote-consent/provider behavior, destructive device data operations, or a new Rewards design when no existing design/source can be located.

**Never:** Reset/stash away existing work, replace Rewards progress with invented values, silently capture a photo after granting preview permission, force recurring permission dialogs after refusal, push/merge, or claim screenshots/unit tests are physical instrumentation evidence.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Open camera | Permission granted | Live preview, shutter/gallery/close; no bottom bar | Camera-unavailable recovery stays visible |
| First permission request | Camera access not granted | Contextual system request on entering camera; grant starts preview only | Denial leaves close/gallery usable and an actionable access button |
| Permanent refusal | System no longer displays permission dialog | Visible button opens this app's Settings | No automatic request loop; returning rechecks permission |
| Settings return | User grants or keeps camera disabled | Granted access binds preview; denial preserves recovery UI | No forced restart or implicit shutter action |
| Home layout | Ordinary 361 dp phone, normal font | Large centered mascot, complete next-step card/action, bottom-anchored nav | Small screens/large text can scroll without losing actions |
| Rewards | Existing feature snapshot | Reachable screen from the implementation found in upstream/history; readable status icons | Report missing source honestly if investigation finds only the current list |

</frozen-after-approval>

## Code Map

- `mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt` — Android permission lifecycle, capture/tab entry, system-bar appearance.
- `mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisAndroidHostController.kt` — preview-only permission entry/retry, request guard, and narrow Settings-return recovery.
- `mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisLiveCameraPreview.kt` — native preview and denied-permission UI.
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreen.kt`, `HomeNextStep.kt`, `PhotoAnalysisMainLayout.kt`, `PhotoAnalysisMainBottomBar.kt` — responsive content and safe-area layout.
- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsScreen.kt` — restored original illustrated collection, using recovered prototype/assets and current domain progress.
- `mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PhotoAnalysisComposeSemanticsInstrumentationTest.java` — native permission/navigation regression coverage.
- `mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PhotoAnalysisCameraPermissionInstrumentationTest.java` — isolated Android controller permission regression cases without changing device grants.
- `mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisLayoutVisualTest.kt` — production-composable geometry checks and screenshots.

## Tasks & Acceptance

**Execution:**
- [x] Pull latest actual upstream main without discarding local work; record its commit and Rewards-source investigation.
- [x] Record missing-permission, denial, Settings-return and device-size layout regression evidence before fixing production.
- [x] Correct Android permission entry, actionable denied-permission UI, and lifecycle refresh without implicit capture.
- [x] Correct Home mascot/card measurements and bottom navigation position; preserve foreground paws and mirrored tabs.
- [x] Resolve the reported missing Rewards source and correct native status-bar contrast on its light background.
- [x] Connect 152 exact food identities to separate transparent artwork and verify broad category/plant-milk coverage without unrelated photo suggestions.
- [x] Run relevant module tests, JVM/Android quality and production build, shared Apple compilation, physical-device checks, and graph update; record exact results and remaining root-policy/full-host limitations.

**Acceptance Criteria:**
- Given the tested HONOR phone, when Home opens, then its mascot remains substantial and the complete next-step card and Do it are visible above the bottom menu.
- Given the camera permission scenarios in the matrix, when permission changes, then the live preview/recovery UI follows the actual OS grant state without taking a photo or looping prompts.
- Given Home or Rewards, when changing tabs or returning from camera, then navigation, system bars, and safe-area spacing follow the visible destination.
- Given retained-photo review, when selecting/deselecting foods or the custom plus and retaking/closing, then the previously requested flow remains functional.

## Spec Change Log

- 2026-10-11: Implemented the Android preview permission slice after the physical-device regression failed with `expected 0 captures, actual 1`. First capture entry requests only undetermined access; refusal leaves the native access action plus gallery/close; grants and Settings returns refresh the preview without triggering capture. The live request guard survives configuration changes. Destination callbacks update system-bar contrast. Added thirteen isolated native controller regression cases; build, full native execution, and final acceptance verification remain pending with the root task.

## Verification

**Commands:**
- `./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest :feature:photo-analysis:jvmTest --continue --no-configuration-cache --console=plain` — fresh relevant module XML reports, zero failures/errors/skips.
- `./mobile/gradlew -p mobile qualityCheckJvmAndroid :apps:androidApp:assembleProductionDebug --continue --no-configuration-cache --console=plain` — current clean JVM/Android checks and installable APK.
- `./mobile/gradlew -p mobile :feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true --continue --no-configuration-cache --console=plain` — shared UI Apple source compilation; not Apple execution/lint evidence.
- `./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.deviceSerial=adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp --no-configuration-cache --console=plain` — exact productionDebug/uiAcceptanceDebug report counts on the physical phone.
- `git diff --check` and `graphify update .` — clean whitespace and refreshed graph inventory.

Inspect the installed production app with fresh native hierarchy/screenshots: Home, Rewards, camera permission recovery/live preview, capture/review, plus, Retake, and return navigation. Keep device execution and blocked prerequisites separate from build results.

## Source investigation update

Latest origin/main is 5ee5de0. A final non-Kotlin source search recovered local unreachable tree `015c5dd90c23df0c97b819cf1dab39f48441ccd0`, containing HTML prototype blob `aa06746fc0dcfca36e69e95f65777d57ab4a9d5f`, seven original medal PNGs, and a foliage hero background. This establishes an existing visual source, not a main-branch Kotlin implementation. Restore its equal rounded-card grid and original artwork with the current seven domain rewards; discard prototype fixtures for Level/XP/unrelated reward rules. Exact source bytes and checksums are preserved in the recovered manifest.

A focused physical native test observed the original permission callback taking a photo on grant (expected zero captures, actual one). Three pre-fix shared UI tests observed the 80 dp mascot, clipped next-step card and 32 dp visible navigation gap. Production permission edits followed these red results.

## User scope extension: relevant food illustrations

On 2026-10-11 the user reported a banana photo showing unrelated food art and no corresponding selected banana. The consumer review must render only actual photo components, bind each illustration and checkmark to the existing component selection, keep duplicate IDs independent, and offer explicit manual input for other foods. Generate missing transparent stickers in the existing photorealistic white-border style. The shipped detector labels are apple, banana, broccoli, carrot and orange; support all five. Preserve a truthful accessible text fallback for unillustrated foods and exact identity matching instead of substrings. Add a reusable common-food illustration library without presenting it as photo evidence. Hide unselected LOW-confidence dish suggestions from the default consumer choices using typed engine bands, preserving explicitly selected candidates and the full research/domain review. Component-only explicit confirmation must use the existing feature contract without requiring a separate custom/dish choice.

On 2026-10-12 the user explicitly expanded the illustration scope to popular
fruits/vegetables, grains, meat/fish, vegan foods and dairy. Generate 105
additional distinct food images, bringing the finite library from 47 to 152
identities. Include plant milks, individual nuts, olives and brown rice.
Record exact prompts, original output paths, transparency and hashes. Match
whole names in English/Russian; illustrate a detected label without rewriting
its component ID, selection or confirmation provenance. Native detector
coverage and illustration coverage remain separate inventories.

## Final implementation verification updates (2026-10-13)

All105additional generated images are integrated unchanged, with152exact
identities and574unique normalized aliases. Original selected outlines now
receive an explicit check badge. Review action labels are measured at their
actual widths using the same resolved text style and padding as rendering;
normal actions remain48dp and long translations grow both equally. The
14focused selection/geometry/boundary tests passed. Full gates and native
verification are in progress; status in-review is not a completion claim.

October 14 device update: latest complete production/POC/uiAcceptance XML each
shows 40 tests with zero failures/errors/skips. The production suite passed
after an unchanged replay of an OS-cancelled Close touch; no product/test
assertion was weakened. Real banana selection is now verified with native
checkbox state and the visible badge. Remaining manual and host prerequisites
are tracked in [the October 14 device report](native-device-verification-2026-10-14.md).
Live `git ls-remote origin refs/heads/main` still resolves to the baseline
`5ee5de077866feed70b418ee2760af24322b1d22`; whitespace checks pass. Keep this spec
in-review while its final evidence scope remains incomplete.

October 15 gallery checkpoint: the additional gallery-photo fitting bug
is corrected and verified on the installed production build. The complete
current-source physical run passed in one command: POC, production and
uiAcceptance each 40/0/0/0, 120 total. The full banana photo is now visibly
contained with correct proportions; its relevant food choice and checkbox
work. Six current native attestations were emitted as local-only/dirty, and
all six APK hashes then matched the tested manifest. Relevant JVM modules total
141/0/0/0. The screen timeout is restored and there is no pending permission
mutation. See [the current verification report](gallery-photo-fit-verification-2026-10-15.md).
Later manual work verified Home, all seven Rewards cards, gallery Retake/Close,
real sensor capture with no-food recovery, and all nine real OS permission
request/denial/permanent-denial/Settings-return phases. POC grants, flags,
preferences and Camera AppOps were restored exactly. Those checks are complete.

The real keyboard check found N-17: Android panned Close and the review header
off screen. Its native geometry regression failed before correction. The
Android host now requests resize, all three APKs rebuilt, and the new production
APK is installed with its on-device hash verified. After the user stopped parallel phone testing, the corrected real IME
geometry and dismissal passed on the installed production APK. Both screenshots
were immediately inspected; native Close/input/Retake/Add bounds are valid
above the keyboard and the full photo/header return after dismissal. The complete N-17 physical replay passed **120/0/0/0** on the HONOR:
POC, production and uiAcceptance each 40/0/0/0, with fresh root/class XML
and all six APK/test-APK hashes verified. Its run finished 21:37:18 UTC
(October 16 locally), exit 0 in 13m 38s. Timeout and exact POC permission/
preferences/AppOps baselines were read back; production is left on Home.
Six new attestations are local-only/dirty, not activation evidence. The
scoped Android implementation/device checks are complete. This parent spec
remains in-review for user review and the documented root-policy/full-Xcode
limits, with no claim that either gate is green. See
[the final N-17 checkpoint](keyboard-review-pan-2026-10-15.md). No phone
question, active run or device cleanup remains pending.

## Suggested Review Order

**Capture and permission lifecycle**

- One tab owner remembers the return destination and closes capture without finishing the host.
  [PhotoAnalysisComposeActivity.kt:546](../../mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt#L546)

- Permission grant starts preview; refusal exposes explicit retry or Settings without an automatic shutter.
  [PhotoAnalysisAndroidHostController.kt:86](../../mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisAndroidHostController.kt#L86)


**Photo review**

- Shared capture geometry preserves reachable controls and a separate retained-photo review state.
  [PhotoAnalysisCameraScreen.kt:52](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisCameraScreen.kt#L52)

- Only actual photo evidence becomes selectable food choices; manual entry remains explicit.
  [PhotoAnalysisMealReview.kt:62](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReview.kt#L62)

- Whole-name matching chooses the illustration without replacing component identity or selection.
  [FoodSticker.kt:355](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/FoodSticker.kt#L355)


**Main screens**

- Measured mascot, card and navigation geometry adapts to phone size and text scale.
  [HomeScreen.kt:47](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeScreen.kt#L47)

- The recovered visual collection renders actual domain progress in equal responsive columns.
  [RewardsScreen.kt:27](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsScreen.kt#L27)


**Native evidence**

- The unchanged device test asserts Close returns to real Home semantics.
  [PhotoAnalysisComposeSemanticsInstrumentationTest.java:108](../../mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PhotoAnalysisComposeSemanticsInstrumentationTest.java#L108)
