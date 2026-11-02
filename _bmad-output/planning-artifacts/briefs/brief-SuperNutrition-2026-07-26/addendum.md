---
title: SuperNutrition Product Brief Addendum
status: final
created: 2026-07-26
updated: 2026-07-26
---

# Product Brief Addendum

`SuperNutrition` is the product, installed app, launcher, in-app brand,
repository, and BMAD project name. The exact Apple App Store and Google Play
listing title is `SuperNutrition AI nutritionist`; `AI nutritionist` is a
store-listing descriptor, not part of the installed or in-app name.

## Nutrition Authority

- Models return food candidates, portion evidence or assumptions, confidence
  scores, and abstention reasons.
- Numeric nutrition values come from controlled nutrition data only after the
  user confirms the food candidate and serving assumption.

## Platform and UI

- The app uses Kotlin Multiplatform to target Android and iOS.
- Compose Multiplatform is the default shared UI implementation, with native
  interop where camera, system picker, accessibility, or inference APIs require
  it.
- The first beta is English-first for Tier 1 consumer markets. Domain models must
  not hard-code locale, serving units, decimal formats, nutrition source, privacy
  copy, or provider region.
- The default UX is low-friction: the protected interaction-default policy may
  prefill only allowlisted deterministic values such as locale, measurement
  system, confirmed catalog, and standard serving. Show defaults with provenance
  and keep them editable/reversible within their approved scope. Policy-resolved
  jurisdiction and processing region are shown before remote consent; the user
  authorizes the resulting transfer rather than selecting provider routing.
  Remote consent/upload remains explicit; medical/allergen conclusions are
  prohibited, and materially uncertain food identity remains an honest
  question/abstention. Meal-specific preparation corrections remain
  session-scoped in the MVP and are not persisted as future defaults.

## Inference Isolation and Orchestration

- The local and remote inference implementations must live in separate Gradle
  modules and separate BMAD epics. They may depend on the shared inference
  contract, image contract, result contract, and test fixtures; neither may
  depend on the other.
- A separate integration owner may implement capability resolution and pipeline
  orchestration only after both engines pass the shared contract suite.
- The local track may contain different Android and iOS implementations and
  multiple device tiers. "Local" is a policy boundary, not a promise that one
  runtime or model works everywhere.
- The remote track includes the KMP client adapter and a first-party gateway. The
  gateway owns credentials, provider adapters, upload controls, rate limits, and
  raw-image retention enforcement.

## Implementation-Agent Constraint

Implementation should use the vendored
`kotlin-multiplatform-developer` agent for complete KMP feature slices and the
other vendored Kotlin agents for initialization, testing, diagnostics,
refactoring, security, and delivery when the task falls within their documented
scopes.

The project architecture and story acceptance criteria are authoritative when a
generic agent prompt conflicts with this project. Every invocation must include
the selected DI framework, platforms, UI approach, local/remote data-source
requirements, owning Gradle module, allowed dependencies, architecture decision
IDs, and required contract tests. The detailed invocation contract will live in
the implementation-agent guide; this addendum records only the product-level
constraint.

## Evidence Provenance — Non-Normative

- `~/Downloads/NutriArch/` — historical NutriArch concept, prototype, and planning
  material. React Native/Expo decisions are historical input, not the selected
  implementation stack.
- `~/Downloads/report.md` — food-photo recognition market, model,
  privacy, cost, and evaluation research current as of 2026-07-26.
