# Story 1.3: Establish product identity metadata

Status: done

## Story

As a release owner, I want one machine-readable identity manifest so visible
names cannot drift between Android, iOS, acceptance, web, and store metadata.

## Acceptance Criteria

1. Installed, launcher, in-app, and accessibility identity is exactly
   `SuperNutrition`.
2. Store listing title is exactly `SuperNutrition AI nutritionist` and never
   appears as the installed or in-app name.
3. Technical package and bundle identifiers remain separate values.

## Tasks

- [x] Add `mobile/config/product-identity.toml`.
- [x] Bind the read-only mobile policy validator to exact identity values.
- [x] Record separate Android and iOS acceptance identifiers.
- [x] Add a contract-owned, preselection-safe web/store metadata release and
  read-only validator; final runtime generation remains tied to web/backend
  root selection.

## Evidence

Evidence includes the mobile policy validator and the contract-owned
preselection metadata gate. Runtime web/backend generation and final store
delivery remain separate activation work while those roots are unselected.

The read-only identity check also reconciles the real Android application ID
and acceptance suffix, Android visible label, iOS display name and acceptance
bundle configurations, plus the web/backend preselection lifecycle records.

The production identity is now executable as well: Android exposes a
`production` flavor with `app.supernutrition`, while the acceptance
flavor remains isolated at `app.supernutrition.uiAcceptance`. The iOS
host project exposes both `SuperNutrition` (`com.supernutrition.ios`) and
`SuperNutritionUIAcceptance` (`com.supernutrition.ios.uiacceptance`) targets.
The product-identity contract is a versioned release with compatibility digest
and immutable fixtures; CI fingerprints include the complete release.
The acceptance and production iOS targets have distinct product references,
build phases, and committed shared schemes. The local strict gate compiled
`:apps:androidApp:assembleProductionDebug`; the canonical Linux CI job repeats
that production-target compilation when Actions is enabled.

## Independent Code Review

Claude CLI Opus/high review was rerun on 2026-07-29.

- [x] [Review][Patch] Product identity validation is now part of the local
  `verifyMobilePolicy` path and is required by the local quality tasks; it no
  longer depends on hosted GitHub workflow execution.
- [x] [Review][Patch] The mobile validator now checks the exact visible identity
  fields instead of accepting unanchored substring matches.
- [x] [Review][Patch] The product-identity negative fixture copies both iOS
  schemes and has a baseline positive control before applying drift mutations.
- [ ] [Review][Defer] Additional direct scans for every Android and iOS
  generated/display-name property remain a follow-up hardening item; the
  current validator still reconciles the authoritative manifest and existing
  platform metadata checks.
- [ ] [Review][Defer] Hosted-workflow identity-step tamper resistance is not a
  local product gate because hosted GitHub enforcement is intentionally
  optional.
