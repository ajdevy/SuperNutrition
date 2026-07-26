# Source Reconciliation — NutriArch PRD

## Verdict

The final PRD preserves the camera-first, low-friction concept while safely
superseding historical Expo/cloud-only behavior, model-generated nutrition
fallback, silent upload, and exact-photo claims. It is suitable for UX and
architecture planning; public launch remains gated.

## Reconciled Changes

- The hybrid flow must improve corrected Candidate accuracy or time to a
  confirmed result over both single-route baselines (SM-10).
- A route×platform×consent/recovery acceptance matrix proves local and remote
  behavior on Android and iOS (SM-9).
- The Tier 1 segment has a reversible English-speaking reference cohort and a
  separate legal launch gate (G-1).
- Evaluation ground truth now includes weighed portions, recipes/brands,
  raw/cooked state, oil/sauce, device/cuisine/lighting context, and
  leakage-resistant splits (SM-6).
- Calm, minimal, nonjudgmental UX intent is explicit in NFR-16.
- Legacy 2/4/10-second targets are labeled spike hypotheses rather than
  unsupported release claims.

## Superseded Historical Decisions

- React Native/Expo as the production client stack.
- A 72-food TypeScript table as the production nutrition catalog.
- Model-generated calories/macros on database misses.
- Cloud upload before meaningful disclosure and consent.
- Correction and serving confirmation deferred beyond v1.
