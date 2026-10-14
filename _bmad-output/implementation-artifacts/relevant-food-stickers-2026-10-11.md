# Relevant food stickers — 2026-10-11

Status: red regressions observed; production implementation in progress.

## Findings

- RFS-1: `PhotoAnalysisFoodPalette.foodStickerTargets()` maps all 17 reference foods, even when the photo contains only one food. Unmatched stickers become manual pantry choices. This produces unrelated illustrations in every review.
- RFS-2: the shipped Android YOLO visible-food allowlist contains exactly `apple`, `banana`, `broccoli`, `carrot`, `orange`. Existing illustrations cover broccoli and carrot; banana, apple, and orange have no matching illustration.
- RFS-3: the fixed palette binds only the first component with a matching illustration. Duplicate detections are moved to text chips instead of preserving each illustrated component and its selection.
- RFS-4: presenter components expose their immutable component ID and food label. Unknown or ambiguous labels must retain that label; a generic or partial substring match would show a false food identity. The nutrition package has 7,793 records and is not an appropriate default suggestion palette.

## Acceptance conditions

- Given a review containing only banana, when the review renders, then its banana illustration is present and unrelated reference/manual foods are absent.
- Given component IDs selected by the existing meal-review selection, when the review renders or a sticker is toggled, then each corresponding sticker reflects only that component's selection and the confirmation retains those component IDs.
- Given two detections of the same food, when one is toggled, then the second remains separately selectable and keeps its own ID.
- Given an unsupported food label or a misleading compound such as banana bread, when the review renders, then an accessible text choice displays the actual label with a minimum 48 dp touch target and no false banana illustration.
- Given a desire to add another food, when the user taps the explicit add control, then manual input receives focus; no unrelated food is silently proposed or selected.
- Given narrow screens, enlarged text, many detections, or a drag beginning on a sticker, then choices and footer stay reachable without clipped labels, overlapping touch targets, or accidental selection.

## Scope and ownership

Shared `photo-analysis-ui` palette, sticker identity and presentation, meal-review evidence integration, and focused colocated tests. Root owns builds, device execution, host permission fixes, and presenter/domain changes. The asset worker owns newly generated PNGs and provenance.

Finite mandatory illustration additions: `food_sticker_apple.png`, `food_sticker_banana.png`, `food_sticker_orange.png`. Existing 17 illustrations remain reusable only when their exact supported food label is actually detected.

## Red regression evidence

Root ran `:feature:photo-analysis-ui:jvmTest --tests '*RelevantFoodStickersTest' --continue --no-configuration-cache --console=plain` before this slice's production changes. Fresh XML timestamp `2026-10-11T16:25:00.829Z`: 3 tests, 3 failures, 0 errors, 0 skipped; build duration 1m45s. Log: `/tmp/supernutrition-relevant-stickers-red.log`.

- Banana-only review unexpectedly contains `photo_analysis_pantry_salmon`.
- No illustration exists for the shipped local label `apple`.
- Unsupported-food text target measures 39 dp high instead of at least 48 dp.

## Additional native findings before correction

- RFS-5: the Food101 classifier offers low-confidence chocolate mousse, oysters, and mussels for the actual banana photo. The existing domain `ConfidenceBand.LOW` can be retained as typed presentation data. Consumer review should omit such proposals unless already explicitly selected; the domain draft and manual correction remain available. This is a UI suggestion policy, not an inference accuracy or grounding claim.
- RFS-6: the consumer Add to meal button requires a dish or a separate custom-meal choice even after explicit photo-food selection. The feature already accepts a dishless confirmation containing explicitly selected photo evidence. The button should honor that existing contract without an extra custom-meal tap.

Additional acceptance: an unselected LOW dish proposal is absent from consumer suggestions; an explicitly selected LOW proposal remains visible and reversible. An explicitly selected banana can be confirmed without a dish or custom-meal selection. Additional regression tests will run before those two behaviors change.

Root-owned follow-up run `/tmp/supernutrition-relevant-stickers-followup-red.log` completed before RFS-5/RFS-6 correction: fresh XML timestamp `2026-10-11T16:32:24.528Z`, 5 tests, 2 failures, 0 errors, 0 skipped, 48s. The initial three regressions passed. The two new failures show the unselected typed-LOW dish still present and Add to meal disabled after explicitly selecting banana without a dish/custom selection.

## Implemented behavior

- Food illustrations come only from actual visible components, with each component's opaque ID and explicit selection retained. Duplicate foods remain independently selectable.
- The responsive nonoverlapping palette replaces fixed coordinates and alpha hit testing; unsupported food labels use accessible text with at least 48 dp height.
- The finite illustration mapping uses exact aliases, never substring matching. Add remains an explicit 48 dp control that focuses manual input.
- Consumer dish choices omit engine-typed LOW suggestions unless explicitly selected; research/domain candidates remain intact. The custom plus centers when no suggested dish remains.
- Explicit photo-food or manually entered content enables the existing dishless confirmation path without an extra custom-meal tap. Detection never silently selects food identities.

Validation pending: root-owned focused/full UI tests, quality gates, and actual-device photo review.

## Additional review relevance findings before fixes

- RFS-5: on the physical banana-photo review, three low-confidence unanchored Food101 dish suggestions (chocolate mousse, oysters, mussels) appear alongside banana evidence. The domain provides a typed existing confidence band, but presentation exports only a localized label. Add that typed metadata without changing inference; omit LOW dish proposals in the default consumer row unless a user already selected one. Keep research/full-domain candidates and explicit selected choices intact; do not claim the score is calibrated.
- RFS-6: the Add to meal action requires a dish or custom choice even when the user explicitly selected a visible banana. The feature confirmation already accepts dish-free explicit photo evidence. Align the consumer action with that existing contract so an unrelated dish/custom selection is unnecessary. Observe regression failures before changing both UI behaviours.

- RFS-7: existing four selected reference PNGs already contain their checkmark, while the adaptive renderer adds another badge. Keep the original selected artwork and draw the extra badge only for assets whose selected image is the same as their plain image. This is a visual correction recorded before the patch.

2026-10-13 correction to the historical RFS-7 assumption: direct inspection of
all four original selected PNGs showed an outline, not a baked checkmark. The
current renderer therefore adds one visible checkmark for every selected food,
including these originals. Its regression passed with the final shared UI tests.

The current library has 152 exact identities and 574 unique aliases, with 156
runtime PNGs including the four original selected variants. The catalog contains
3,519 canonical foods and 582 dishes derived from 7,793 raw SR Legacy records;
the old note above conflated the raw and canonical record counts.
