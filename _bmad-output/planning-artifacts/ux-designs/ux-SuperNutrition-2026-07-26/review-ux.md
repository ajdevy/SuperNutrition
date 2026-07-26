# UX Review — NutriArch

## Verdict

The two spines are suitable for architecture and story planning. Visual UI
implementation remains gated by UX-A1 palette validation and the five
layout-sensitive mocks named in `EXPERIENCE.md`.

## Resolved Findings

- Added explicit dark component variants and a theme-alias rule.
- Removed consent coercion by giving upload and no-upload actions equal visual
  treatment and putting the no-upload option first in reading/focus order.
- Added on-photo scrim/foreground tokens and a contrast fallback.
- Added text expansion, wrapping, locale formatting, bidi, and Compose `Dp`
  mapping rules.
- Made the palette assumption traceable as UX-A1 with owner and resolution gate.
- Added contextual country/catalog selection without inventing a settings
  hierarchy.
- Added pre-transfer, in-transfer, and post-transfer cancellation behavior.
- Added component/state closure for privacy details, source details, and internal
  diagnostics.
- Added camera-specific screen-reader quality guidance.
- Added a state transition contract to prevent implementation agents from
  creating navigation destinations for internal analysis states.
- Explicitly limits native iOS UI to camera/picker bridges; Compose owns the
  feature screens.

## Remaining Handoffs

- Create mocks for Capture, Remote consent, Candidate review, Serving
  confirmation, and Grounded result before the first visual UI story is
  ready-for-development.
- Architecture must freeze the native camera bridge and sealed state/event/effect
  types.
- PRD Gate G-1 replaces the provisional country/catalog cohort.
