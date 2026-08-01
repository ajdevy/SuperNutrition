---
name: SuperNutrition
description: Calm, evidence-aware nutrition estimation for Android and iOS.
status: final
created: 2026-07-26
updated: 2026-08-01
sources:
  - ../../prds/prd-SuperNutrition-2026-07-26/prd.md
colors:
  surface-base: '#F7F8F4'
  surface-raised: '#FFFFFF'
  surface-subtle: '#EEF1EC'
  ink-primary: '#1B1F1C'
  ink-secondary: '#58615B'
  ink-disabled: '#8D958F'
  accent: '#2F6B4F'
  on-accent: '#FFFFFF'
  accent-soft: '#DCEBDD'
  border-hairline: '#D9DED9'
  warning: '#8A5A00'
  error: '#B3261E'
  photo-scrim: '#99000000'
  on-photo: '#FFFFFF'
  surface-base-dark: '#111512'
  surface-raised-dark: '#191E1A'
  surface-subtle-dark: '#222823'
  ink-primary-dark: '#ECF1EC'
  ink-secondary-dark: '#B6C0B8'
  ink-disabled-dark: '#737C75'
  accent-dark: '#94D2AA'
  on-accent-dark: '#07391F'
  accent-soft-dark: '#214D35'
  border-hairline-dark: '#39423B'
  warning-dark: '#F2C37B'
  error-dark: '#FFB4AB'
typography:
  title:
    note: 'Platform-accessible Compose Material 3 role: headline medium'
  section:
    note: 'Platform-accessible Compose Material 3 role: title large'
  body:
    note: 'Platform-accessible Compose Material 3 role: body large'
  label:
    note: 'Platform-accessible Compose Material 3 role: label large'
  meta:
    note: 'Platform-accessible Compose Material 3 role: body small'
  nutrition-number:
    note: 'Tabular figures using the platform system font; never display typography'
rounded:
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  full: 9999px
spacing:
  '1': 4px
  '2': 8px
  '3': 12px
  '4': 16px
  '5': 24px
  '6': 32px
components:
  primary-action:
    background: '{colors.accent}'
    foreground: '{colors.on-accent}'
    radius: '{rounded.full}'
    min-height: '48px'
  primary-action-dark:
    background: '{colors.accent-dark}'
    foreground: '{colors.on-accent-dark}'
    radius: '{rounded.full}'
    min-height: '48px'
  secondary-action:
    background: '{colors.surface-raised}'
    foreground: '{colors.ink-primary}'
    border: '{colors.border-hairline}'
    radius: '{rounded.full}'
    min-height: '48px'
  secondary-action-dark:
    background: '{colors.surface-raised-dark}'
    foreground: '{colors.ink-primary-dark}'
    border: '{colors.border-hairline-dark}'
    radius: '{rounded.full}'
    min-height: '48px'
  consent-action:
    background: '{colors.surface-raised}'
    foreground: '{colors.ink-primary}'
    border: '{colors.accent}'
    radius: '{rounded.full}'
    min-height: '48px'
  consent-action-dark:
    background: '{colors.surface-raised-dark}'
    foreground: '{colors.ink-primary-dark}'
    border: '{colors.accent-dark}'
    radius: '{rounded.full}'
    min-height: '48px'
  candidate-card:
    background: '{colors.surface-raised}'
    foreground: '{colors.ink-primary}'
    border: '{colors.border-hairline}'
    radius: '{rounded.lg}'
  candidate-card-dark:
    background: '{colors.surface-raised-dark}'
    foreground: '{colors.ink-primary-dark}'
    border: '{colors.border-hairline-dark}'
    radius: '{rounded.lg}'
  nutrition-card:
    background: '{colors.surface-subtle}'
    foreground: '{colors.ink-primary}'
    radius: '{rounded.lg}'
  nutrition-card-dark:
    background: '{colors.surface-subtle-dark}'
    foreground: '{colors.ink-primary-dark}'
    radius: '{rounded.lg}'
  state-chip:
    background: '{colors.accent-soft}'
    foreground: '{colors.ink-primary}'
    radius: '{rounded.full}'
  state-chip-dark:
    background: '{colors.accent-soft-dark}'
    foreground: '{colors.ink-primary-dark}'
    radius: '{rounded.full}'
---

# SuperNutrition — Design Spine

## Brand & Style

SuperNutrition should feel like a quiet measuring companion, not a scorekeeper. The
visual posture is calm, natural, and precise without looking clinical. It gives
the photo and the user's correction work visual priority, then presents
nutrition as an estimate with provenance rather than a verdict.

The visible product name is always `SuperNutrition`. The store-listing title
`SuperNutrition AI nutritionist` may appear in store acquisition material, but
the `AI nutritionist` descriptor must not replace or extend the installed app
name, launcher label, navigation title, or in-product wordmark.

**UX-A1 — Working palette assumption:** the muted natural-green accent is the
working visual direction until the Product/Design owner validates it through
brand review and accessibility/usability checks before the first visual UI story
is marked ready for development. The system should remain credible if that
accent changes; layout, typography, and semantic state must not depend on brand
color.

**Low-friction interaction:** surfaces should feel immediate and quiet. Prefer a
visible, editable default and one clear action over a choice-first sheet or a
multi-step form. Defaults must be deterministic, reversible, and labeled with
their provenance and come from the protected default policy; privacy and consent
decisions remain explicit, medical/allergen conclusions are prohibited, and
materially uncertain identity remains unresolved until honestly corrected.

## Colors

- **Soft stone (`{colors.surface-base}`)** is the default canvas. It is warmer
  than clinical white and quiet behind photography.
- **Raised white (`{colors.surface-raised}`)** holds Candidates, forms, consent,
  and corrections.
- **Leaf (`{colors.accent}` / `{colors.accent-dark}`)** identifies the primary
  action and active selection. It is not a health score, correctness grade, or
  decoration.
- **Ink (`{colors.ink-primary}`)** carries all essential content.
- **Warning and error** are reserved for actionable states. Confidence and
  Inference Route always include text/icon semantics and never rely on color.
- Dark mode uses the explicit `*-dark` component variants. Theme composition
  maps each component role to either the light or dark variant; implementation
  must not resolve an unsuffixed light component inside a dark theme.
- On-photo controls use `{colors.photo-scrim}` with `{colors.on-photo}`. If real
  image testing cannot meet 4.5:1 for text or 3:1 for essential icon boundaries,
  the control moves to an opaque surface rather than weakening contrast.

Avoid gradients, calorie heat maps, red/green “good versus bad” nutrition coding,
and saturated success celebrations.

## Typography

Use Compose Material 3 semantic roles backed by platform system fonts. Dynamic
type and Android font scaling are requirements, not exceptions.

Nutrition numbers use tabular figures where available, but remain body-scale.
The app must not turn calories into a giant score. Food names and unresolved
assumptions outrank nutrient values in the hierarchy. Labels use sentence case;
all-caps and condensed labels are prohibited.

Text containers grow for localized copy and dynamic type. Primary actions may
wrap to two lines and grow vertically; essential labels and nutrition values are
never ellipsized. Number and unit formatting comes from locale-aware formatters,
not display strings assembled in UI code.

## Layout & Spacing

Use the 4/8/12/16/24/32 scale. Phone content keeps at least
`{spacing.4}` horizontal padding and `{spacing.5}` between major decision groups.
Capture is edge-to-edge; review, consent, and result content use a readable
single column.

For Compose implementation, numeric spacing, radius, border, and target-size
values map to density-independent `Dp`; the `px` syntax in this DESIGN.md is a
portable design-token notation, not physical screen pixels. Typography remains
semantic so platform font scaling owns final rendered size.

Candidate lists and nutrient details expand vertically rather than compressing
type. Primary actions remain reachable without obscuring system insets, the
keyboard, or camera controls.

Layouts must tolerate at least 30% English text expansion without overlap.
Leading/trailing alignment mirrors under bidirectional layout even before an RTL
language is scheduled; photos, numeric values, and capture orientation do not
mirror semantically.

## Elevation & Depth

Hierarchy comes from tonal surfaces and spacing. Use elevation only for the
camera control bar, bottom sheets, and transient system surfaces. Cards use
`{colors.border-hairline}` or tonal separation, not floating shadows.

## Shapes

Cards use `{rounded.lg}`. Inputs and inline surfaces use `{rounded.md}`. Primary
and secondary actions use `{rounded.full}` to stay distinct from information
cards. Camera shutter remains a circle because it follows the platform camera
metaphor; no other control copies it.

## Components

- **Camera shutter** — Large centered circular action with text alternative
  “Take meal photo.” Disabled only while capture is not ready.
- **Primary action** — `{components.primary-action}`. One per decision surface:
  Analyze remotely, Confirm foods and serving, or Scan again.
- **Secondary action** — `{components.secondary-action}` for Retake, Continue
  without upload, Edit, and Cancel.
- **Candidate card** — `{components.candidate-card}`. Food name, Confidence
  wording, source route, serving assumption, alternatives, remove/edit action.
  A v2 visible component also shows its visible-region/ambiguity state and an
  editable `Estimated range` in grams or `Not enough visual evidence`; neither
  is presented as measured weight or numeric nutrition.
- **Nutrition card** — `{components.nutrition-card}`. Confirmed food and serving
  precede calories/macros; source/version and estimate language remain visible.
- **State chip** — `{components.state-chip}`. Text plus optional icon for Local,
  Remote, Manual, Needs review, or Offline. Never color-only.
- **Analyzing treatment** — Frozen photo with a low-opacity neutral scrim,
  truthful stage label, one bounded progress refresh per real stage, cancel
  action, and no countdown or anxious animation. The permitted long local path
  uses `Preparing photo`, `Finding visible foods`, `Estimating portions`, and
  `Checking results`; it never simulates progress beyond completed work.
- **Consent sheet** — Purpose, data sent, retention, provider-category wording,
  alternatives, and two `{components.consent-action}` actions with equal size,
  weight, keyboard/focus access, and no preselection. “Continue without upload”
  is listed first in reading/focus order; “Send photo for remote analysis”
  follows. Neither uses the filled primary-action treatment.
- **Country and catalog sheet** — Current catalog country, separate
  policy-resolved market jurisdiction and processing-region values, suggested
  system region, confirmed `CatalogCountry` persisted for the next attempt, and
  “Not listed” recovery. `MarketJurisdiction` and `ProcessingRegion` are never
  persisted as user-selectable values. Catalog selection never silently changes
  where a consented photo may be processed. It is contextual configuration, not
  an account/settings hierarchy.
- **Serving editor** — Keep the standard serving/common unit inline as a labeled
  `Default` when the confirmed catalog and protected default policy support it.
  Edit and Undo remain available without forcing a separate picker;
  recipe/oil/sauce questions appear only when a canonical item leaves them
  unresolved.

## Do's and Don'ts

| Do | Don't |
| --- | --- |
| Put food identity and assumptions before nutrient numbers | Lead with a giant calorie score |
| Say “Estimate,” “Needs review,” and “We couldn't tell” | Say “Perfect,” “Bad meal,” or “AI knows” |
| Keep Local/Remote/Manual provenance visible | Hide cloud use behind generic loading |
| Use restrained motion and tonal hierarchy | Use confetti, streaks, rings, gradients, or pulsing urgency |
| Preserve native camera and picker expectations | Recreate permission or media pickers inside custom UI |
| Pair state color with text and semantics | Use green/red as correct/incorrect or healthy/unhealthy |
| Prefill deterministic frequent values and show their source | Force a picker, scroll, or repeated typing for a safe default |
| Keep defaults editable and reversible | Hide assumptions or auto-submit a sensitive/uncertain decision |
