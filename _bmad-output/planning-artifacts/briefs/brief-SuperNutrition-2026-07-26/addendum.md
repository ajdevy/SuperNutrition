---
title: NutriArch Product Brief Addendum
status: final
created: 2026-07-26
updated: 2026-07-26
---

# Product Brief Addendum

NutriArch is the product working name. SuperNutrition is the repository and BMAD
project name, which is why generated artifact folders use `SuperNutrition`.

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

- `/Users/aj/Downloads/NutriArch/` — prior concept, prototype, and planning
  material. React Native/Expo decisions are historical input, not the selected
  implementation stack.
- `/Users/aj/Downloads/report.md` — food-photo recognition market, model,
  privacy, cost, and evaluation research current as of 2026-07-26.
