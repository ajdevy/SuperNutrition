# Final 152-food slice: fresh Blind Hunter static review

Reviewed on 2026-10-13 with the project-local
`.agents/skills/bmad-review-adversarial-general/SKILL.md`.

Worktree: `REDACTED_WORKTREE/a704/SuperNutrition`.
Branch: `codex/figma-camera-flow`; HEAD and diff baseline:
`5ee5de077866feed70b418ee2760af24322b1d22`. The checkout contains substantial
tracked and untracked WIP. This reviewer changed only this report.

Input: `mobile/build/final-152-review.diff`, including tracked and untracked
changes. The snapshot contains 243 path entries, including 63 Kotlin, two
Swift, one Java, one C/Objective-C header and 146 JSON entries. Current source
files were authoritative, including the signature whitespace cleanup after
the snapshot.

Scope inspected: the consumer camera/review composables, exact food lookup and
all three runtime sticker declarations, retained selection and confirmation
payloads, feature validation, Android permission/lifecycle/CameraX integration,
shared Apple callbacks and native artwork integration, responsive Home/Rewards
helpers and navigation, related test source, the 152-food JSON inventory, and
the generated-resource lint exclusion. Read the architecture spine and the
two current camera/Home/Rewards specs; used `graphify query` for navigation.
The findings below are source analysis and color/geometry calculations. No
Gradle, device, simulator, test execution, image generation, Git mutation or
pixel-level asset audit was performed.

The small-text contrast threshold is supported by the checked-in UX design
at `DESIGN.md:167-169` for on-photo text and by
[W3C contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)
for ordinary text and placeholders. Ratios below use source RGB values and
the standard sRGB relative-luminance calculation; they are not screenshots or
native measurement results.

## Confirmed static defects and mismatches in the changed implementation

- **B-01 — Light-photo review and progress text has insufficient contrast.**
  Trigger: review a bright photo, for example a white plate, where white pixels
  lie behind the food labels or review instruction. The new consumer overlay
  at [PhotoAnalysisMealReview.kt:70](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReview.kt:70)
  is only 45% black. Over white that produces approximately `#8C8C8C`, while
  `reviewTextStyle` uses `#F9F9F9`, only **3.19:1**; the `#D2D4D2` subtitle at
  line 118 is only **2.26:1**. Illustrated component labels at
  [PhotoAnalysisFoodPalette.kt:120](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisFoodPalette.kt:120)
  have no opaque background. The same source problem affects progress/recovery
  text at [PhotoAnalysisCameraScreen.kt:156](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisCameraScreen.kt:156).
  The Android host supplies `photoTextContrast`, but the consumer screen never
  reads it. Consumers with reduced contrast sensitivity can lose the
  instruction, recognized food names and recovery explanation on common bright
  food photos. Acceptance: all these text roles retain at least 4.5:1 over
  bright and dark retained-photo fixtures, using a stronger scrim or a readable
  local surface.

- **B-02 — The manual-input placeholder becomes nearly invisible on a bright photo.**
  Trigger: the same bright retained photo, with an empty Anything else input.
  [PhotoAnalysisMealReview.kt:277](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMealReview.kt:277)
  adds only 6% black to the already translucent scrim, and line 300 renders the
  placeholder in `#95928F`. Its background is then approximately `#848484`,
  giving about **1.2:1** contrast. This needs a separate input treatment: merely
  correcting the white review text does not guarantee this gray placeholder
  becomes readable. Consumers can miss the manual alternative that the plus
  focuses. Acceptance: both the empty placeholder and entered text remain
  readable across bright/dark photo backgrounds.

- **B-03 — Small Rewards progress/earned text misses the text-contrast threshold.**
  Trigger: any current Rewards card, especially an earned one.
  [RewardsCard.kt:84](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsCard.kt:84)
  and line 94 use `RewardsGreen` (`#3D8A62`) for 12sp progress and 10sp earned
  text on the white card. That pair is **4.19:1**, below 4.5:1 for this small
  text. The 25sp header summary is a different text size and is not the finding.
  Consumers who depend on adequate contrast have less legible per-card progress
  and earned state. Acceptance: the small text uses a sufficiently dark text
  color while decorative reward artwork can retain its current palette.

- **B-04 — Home's primary next-step action retains a low-contrast label.**
  Trigger: ordinary Home at default text size. This is an **inherited palette
  problem moved into the new helper**, not a newly introduced color regression.
  [HomeNextStep.kt:121](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeNextStep.kt:121)
  renders the 11sp action label in `HomeAmber` (`#D98324`) on `#FEECD3`,
  **2.51:1**. The 10sp next-step heading uses the same color on `#FDF3DF`,
  **2.64:1**. Consumers can see the button shape but struggle to read its
  action. Acceptance: preserve the artwork/layout while providing adequate
  contrast for these two text roles; triage any exact-palette constraint
  explicitly rather than calling the current palette accessible.

- **B-05 — The Home metric denominator and unit remain too faint.**
  Trigger: the current metric strings containing ` / `, such as the daily
  target and unit. This is also **inherited from the baseline palette**.
  [HomeRhythmCard.kt:201](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/HomeRhythmCard.kt:201)
  sets the suffix to 9sp `HomeMuted` (`#A08D80`) on the `#FDFCFB` card,
  **3.10:1**. The target/unit is meaningful information rather than decoration;
  the responsive refactor makes it wrap correctly without correcting its
  contrast. Acceptance: the entire value/target/unit stays readable, including
  the styled suffix, at normal and larger text sizes.

- **B-06 — Capture close and gallery targets collide in a short app window.**
  Trigger: a 320 x 240dp CAPTURE viewport with 24dp top and bottom platform
  insets, a valid supported small-window input for the responsive surface.
  [PhotoAnalysisCameraScreen.kt:98](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisCameraScreen.kt:98)
  places the close artwork at top `24 + 34.2`; its expanded 48dp target ends at
  y **102.9dp**. Lines 106-118 position gallery with fixed bottom offsets; its
  top is `240 - 24 - 68.3 - 51.3 - 16.65 = 79.75dp`. Their horizontal targets
  overlap as well. Close is composed above the stage, so about 23dp of the
  gallery's top intersects the close target. Consumers trying the upper gallery
  area can close capture instead of importing a photo. This is a static
  constraint calculation, not a rendered/device reproduction. Acceptance:
  compare actual native/Compose bounds in a short viewport and adapt the
  capture controls until close, gallery and shutter have disjoint reachable
  targets.

- **B-07 — Activity recreation during a pending shutter operation can leave the shutter blocked.**
  Trigger: press the shutter and recreate the Android Activity before its inline
  capture callback returns. This is a **pre-existing integration defect**, not
  caused by the new preview-permission guard. The pending operation ID survives
  in the retained ViewModel and can also be restored from the Bundle at
  [PhotoAnalysisComposeActivity.kt:249](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt:249).
  But line 468 clears the Activity-owned inline camera controller, whose
  [clear method at line 53](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisInlineCameraController.kt:53)
  drops the pending request and callback. The new controller has no matching
  queued operation, while
  [PhotoAnalysisAndroidHostController.kt:224](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisAndroidHostController.kt:224)
  rejects every new capture while that retained ID is non-null. A late result
  on the old cleared controller has no callback to release it. Consumers can
  return to a working-looking camera whose shutter does nothing until the
  session/process is replaced. Acceptance: an interrupted operation is either
  resumed by its owning controller or explicitly failed/cleared during
  recreation, without an implicit new capture. Native reproduction remains
  required; the permission-request rotation cases do not cover this separate
  photo-operation phase.

## Conditional findings and improvement questions — not established product defects

- **B-08 — Sighted users have no on-demand uncertainty detail in the consumer review.**
  Trigger to verify: supply a visible component with LOW confidence,
  IDENTITY_AMBIGUOUS or a partial region/mass warning. The description assembled
  at [PhotoAnalysisReviewCopy.kt:6](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisReviewCopy.kt:6)
  retains that information for accessibility, but the consumer tile at
  [PhotoAnalysisFoodPalette.kt:96](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisFoodPalette.kt:96)
  only toggles selection and renders the food name. There is no detail action
  in this inspected path. The UX's minimal-icon pattern asks for on-demand
  detail, while the latest Figma/user scope may intentionally exclude it.
  Decide whether the selected minimal consumer design supersedes that older
  detail requirement; do not infer that a current wrongly selected food was
  demonstrated by this review.

- **B-09 — Native screen-reader transition focus and announcement need direct evidence.**
  Trigger to verify: enable TalkBack or VoiceOver, activate the shutter, then
  wait for preparation, analysis and review. At
  [PhotoAnalysisCameraScreen.kt:68](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisCameraScreen.kt:68)
  the stage has a changing stateDescription; the visible status/title changes
  elsewhere, but no explicit accessibility announcement or transition-focus
  request appears in the inspected consumer path. The sole explicit focus
  request is the manual-input plus. Framework defaults may provide useful
  behavior, so absence of a custom implementation is not proof of a silent or
  trapped screen reader. Acceptance evidence should show one stage/readiness
  announcement and a sensible focus destination after the old shutter node
  disappears, as required by EXPERIENCE.md:253-267.

- **B-10 — Inventory parity is presently correct, but its regression checks duplicate the alias data.**
  The production declarations, `catalog-152.json`, and large literal maps in
  [ExpandedFoodStickerCatalogTest.kt:112](REDACTED_WORKTREE/a704/SuperNutrition/mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/ExpandedFoodStickerCatalogTest.kt:112)
  hold parallel versions of the food vocabulary. This review parsed the
  current Kotlin declarations and the JSON with `json.loads`: **152 records,
  574 aliases, no duplicate normalized alias, an English and Russian alias for
  each identity, and no record/alias difference between production and JSON**.
  There is no current parity defect. A future production-only alias addition
  can nevertheless pass the inspected tests because they check the listed
  expected aliases and overall identity set, not equality with the durable JSON
  alias inventory. Consider one authoritative inventory or an exact inventory
  parity check so provenance and runtime mapping cannot drift silently.

## Review limits and rejected candidates

The exact lookup uses complete normalized aliases, so this inspection found no
substring-based wrong-food illustration or unexpected pantry insertion. The
palette iterates only current photo evidence, keys duplicate names by distinct
component IDs, and confirmation keeps those IDs separate from manual labels.
The custom card renders one centered plus. Permission grant/Settings refresh
does not invoke the shutter in the inspected Android controller. Those are
source observations, not executed acceptance results.

Do not record the food tiles' use of `selected` rather than `toggleableState`
as a proven Android accessibility defect: current
[AndroidX accessibility delegate source](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeViewAccessibilityDelegateCompat.android.kt)
maps non-tab Selected state to checkability and checked state. This is an
upstream reference check, not a native run of the pinned app dependency.
Similarly, the final full-width odd Rewards row is centered deliberately in the
current source and evidence; it is not reported as an accidental unequal-card
regression.

The 156 PNG pixel contents, alpha channels, checksums, every per-image prompt,
Figma visual fidelity, packaged native bundles, actual dynamic-type bounds,
runtime races and native accessibility-service behavior were not independently
verified here. No test counts, green gate, severity ranking or completion claim
are supplied by this static review. Root owns build/device execution and triage.
