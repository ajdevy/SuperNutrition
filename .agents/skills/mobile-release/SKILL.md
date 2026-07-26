---
name: mobile-release
description: Prepare and verify safe Android and iOS releases. Use only when signing, keystores, AAB, IPA, archive, TestFlight, Play Console, App Store, publishing, rollout, or store-release work is explicitly requested.
---

# Mobile release safety

Use this workflow for deliberate, auditable release operations. Default to planning and verification; never infer permission to sign, upload, promote, or publish.

## Workflow

1. Identify the target: package/bundle ID, version and build number, source commit, destination, release owner, and rollback path. Stop if any fact is unknown.
2. Run applicable builds and tests. Require a real-device BLE acceptance result for the exact candidate when BLE is part of the app.
3. Keep keystores, passwords, Apple certificates, provisioning profiles, `.p8` keys, and Play credentials in a secret manager or protected CI environment. Never print, commit, or place them in prompts or skill files.
4. Inspect the generated artifact and signing identity. For Android, use a distinct upload key and Play App Signing. For iOS, verify the bundle ID, signing team, archive/export method, and App Store Connect target.
5. Before any upload, show immutable release facts and obtain explicit confirmation for the named non-production target: Play Internal Testing or TestFlight.
6. Verify processing and record build identifiers, artifact hash, target track, and outcome. Read [the release evidence reference](references/release-evidence.md) for the required record.
7. Before production, obtain a second explicit approval after displaying staged-rollout percentage, monitoring window, rollback action, and named owner.

## Hard stops

Do not sign, upload, promote, publish, reset certificates/profiles, or alter a store track without explicit user authorization for that exact action. Do not claim release completion until the destination service reports the expected build or track state.
