# Story 1.3: Establish product identity metadata

Status: review

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
