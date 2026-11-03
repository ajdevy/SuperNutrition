# Roadmap

Updated 2026-11-02. The linked GitHub issues are the source of truth; this page
groups the current work by theme.

## Mobile app

- Finish the user-facing Profile experience and close remaining Android/iOS
  acceptance gaps.
- Continue evaluating photo identification with sourced, reproducible data;
  keep research results separate from production accuracy claims.

## API and gateway

- Correct transfer-state handling, request validation order, and shared state
  before expanding gateway deployment ([#48](https://github.com/flexsent-labs/SuperNutrition/issues/48),
  [#49](https://github.com/flexsent-labs/SuperNutrition/issues/49),
  [#50](https://github.com/flexsent-labs/SuperNutrition/issues/50)).
- Establish reliable hosted CI for the backend and repair the repository's
  branch-independent CI fingerprint check ([#51](https://github.com/flexsent-labs/SuperNutrition/issues/51),
  [#52](https://github.com/flexsent-labs/SuperNutrition/issues/52)).
- Resolve remaining owner decisions for market binding and consent-receipt
  persistence before enabling production remote inference
  ([#45](https://github.com/flexsent-labs/SuperNutrition/issues/45),
  [#46](https://github.com/flexsent-labs/SuperNutrition/issues/46)).

## Research and release readiness

- Revisit remote inference scope and its parked review findings ([#43](https://github.com/flexsent-labs/SuperNutrition/issues/43)).
- Refresh expired release edits and verify the resulting release evidence
  ([#47](https://github.com/flexsent-labs/SuperNutrition/issues/47)).
