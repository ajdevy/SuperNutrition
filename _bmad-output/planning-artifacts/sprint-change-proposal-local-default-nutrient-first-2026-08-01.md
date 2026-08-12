---
title: Sprint Change Proposal — Local Default and Nutrient-first Profile
status: approved
date: 2026-08-01
mode: batch
scope_classification: moderate
approved_by: Anastasia, Product owner
approved_at: 2026-08-01
---

# Sprint Change Proposal — Local Default and Nutrient-first Profile

## Issue summary

The free/default MVP must not include a Remote consent surface. Remote analysis
is a later paid-only capability. The main photo-first review surface should keep
food selection and the catalog-grounded nutrient profile together, while serving
is an optional edit-and-return screen. Product prioritizes nutrients, vitamins,
minerals, and catalog-backed descriptive food properties over calorie display.

## Impact analysis

| Area | Change |
| --- | --- |
| PRD | Adds an amendment that makes free/default local/manual-only, omits calorie total from the core UI, and prohibits health-effect claims. |
| Epic E3 | Local routing keeps a free/default user local/manual; paid remote needs entitlement plus explicit action. |
| Epic E4 | Remains backlog, deferred, and paid-only; its consent/gateway requirements are preserved for later. |
| Epic E5 | E5.3 produces a catalog-grounded nutrient profile in the photo-first review surface; the serving editor returns there. |
| E5.1 visual gate | Replaces the old five-surface gate with one unified photo-analysis surface (Capture → retained photo → Candidate review/nutrient profile) and optional Serving editor. |
| UX/mocks | Remote consent leaves current mock coverage. Capture, Candidate selection, and nutrient profile merge into one stateful mock. |

## Approved approach

Use a direct adjustment; no inference contract, model, entitlement backend, or
remote implementation is authorized by this change.

- Free/default recovery offers Retake and Manual search, never Remote consent or
  an upgrade prompt.
- A future paid path is impossible to reach without verified entitlement,
  explicit paid-route action, G-4, and the existing consent/privacy controls.
- Nutrient values/property labels come only from a selected versioned catalog
  record and confirmed serving. Missing data is unknown/absent.
- The UI may describe catalog-backed nutritional composition, but cannot claim
  health effects, disease prevention/treatment, allergen safety, or personal
  outcomes.
- The photo-analysis surface has one stable layout: the live preview becomes a
  retained photo after shutter/import; selection controls remain with the
  nutrient profile; calculation populates the profile in place. Serving edit
  saves or closes back to that same screen.

## Implementation handoff

1. Keep E4 backlog; do not expose any free/default Remote consent UI or code
   path in the current visual slice.
2. Amend E5.3 to calculate only catalog-grounded supported nutrients,
   vitamins/minerals, and descriptive properties in the combined screen; omit
   calorie total from its core UI contract.
3. Rework the unified photo-analysis mock and accessibility semantics around
   the in-place Capture → retained photo → selection/profile states, then obtain
   fresh Product/Design approval of the revised current mock set.
4. When visual Compose work starts, prove no-free-remote-route, no-calorie-total,
   source/version/serving provenance, no-health-claim copy, optional-serving
   return, and standard visual accessibility/responsive evidence.

## Success criteria

- A free/default user cannot reach Remote consent from any local outcome.
- The unified photo-analysis surface retains the captured photo through review,
  shows only user-confirmed selections and a catalog-grounded nutrient profile,
  and has no calorie total or health claim.
- Serving edit returns to the same photo-first surface without loss of selected
  foods or provenance.
- Paid remote remains explicitly deferred until its separate entitlement and G-4
  release work is approved.
