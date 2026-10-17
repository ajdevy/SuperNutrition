# Final 152-food Edge Case Hunter review

Date: 2026-10-13. Worktree: `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`.
Branch: `codex/figma-camera-flow`; HEAD and review baseline:
`5ee5de077866feed70b418ee2760af24322b1d22`. Existing tracked and untracked WIP
was preserved. This review did not edit production source or run builds/devices.

Input: `mobile/build/final-152-review.diff` (tracked and untracked changes).
Current source was authoritative for line references, including the signature
formatting cleanup after the snapshot. Applied
`.agents/skills/bmad-review-edge-case-hunter/SKILL.md`: received the diff, traced
branches and boundaries, revisited the edge classes, and ran its secondary
deletion check. Relevant camera/layout specs and the architecture spine were
read. The existing graph was queried for navigation without rebuilding it.

## Findings

```json
[
  {
    "location": "mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/MealReviewChoices.kt:21-24",
    "trigger_condition": "Standalone renderer reanalysis transitions through ANALYZING, then returns different component IDs.",
    "guard_snippet": "if (state == PhotoAnalysisPresenterState.ANALYZING) fallback = fallback.copy(dishId = null, componentIds = emptySet())",
    "potential_consequence": "Old food IDs enable Add but produce empty evidence after a reanalysis."
  }
]
```

Concrete static path: supply `PhotoAnalysisCaptureScreen` with a surface whose
`mealReviewSelection` is null; select component `A` in a candidate review. Emit
`ANALYZING`, then a new candidate review containing only component `B`. The
fallback retains `A` because it resets only for `CAPTURE`/`PREPARING`. Component
`B` renders unselected. `PhotoAnalysisMealReview.kt:147-159` nevertheless enables
Add from the nonempty stale set, while `:174-175` filters the confirmation's
photo evidence down to an empty list. With no dish/manual input, the resulting
callback contains no selected current food. Native hosts avoid this path by
supplying feature-owned selection: accepted grid reanalysis clears the old
photo choices in `PhotoAnalysisFeature.kt:67-69`. The finding is confined to
the reusable renderer's explicitly supported fallback.

## Scope and limitations

The trace covered review action measurement/weighted widths and rounding;
dish, component and custom choices; confirmation validation, mutable input
copies, accepted/ignored reset events, background retention and reanalysis;
Android permission entry/refusal/rationale/Settings refresh and camera binding;
the changed iOS camera/gallery callbacks and resource wiring; Home/Rewards
width, height, scrolling, insets and navigation branches; exact sticker alias
ownership and resource mapping; and meaningful replaced behavior. The deletion
pass produced no additional finding.

A read-only source/resource enumeration found 152 distinct sticker IDs and
574 distinct normalized aliases, no alias ownership collisions, no missing or
misnamed artwork, and no distinct identities sharing identical PNG bytes.
All mapped PNG headers declare RGBA. Soy, oat, almond and coconut milk resolve
to their own IDs; generic plant milk, unknown/composite labels and `no nuts`
do not resolve to a food image. This does not prove the semantic contents or
alpha pixels of the artwork.

The finding is established by static branch tracing, not a newly executed
regression test. Existing tests were inspected as source only; their current
results are owned by the root task. No native permission dialog, camera,
keyboard, accessibility font, image render, Apple compiler or device run was
performed by this reviewer. No prior review was counted as a fresh review.
