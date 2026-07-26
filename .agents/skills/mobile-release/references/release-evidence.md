# Mobile release evidence

Use this reference after a signed artifact is prepared or delivered.

## Required record

```text
Source commit:
Package / bundle ID:
Version / build number:
Artifact filename and SHA-256:
Signing identity or certificate fingerprint:
BLE HIL evidence reference (or not applicable):
Target: Play Internal Testing | TestFlight | production
Upload confirmation and returned build/release ID:
Rollback action and named owner:
```

## Delivery checks

- Android: inspect the signed AAB, confirm the upload-key certificate, and use Play App Signing.
- iOS: inspect the archive/export, bundle ID, signing team, and TestFlight processing state.
- Production: confirm release notes, staged percentage, monitoring window, and rollback owner before promotion.
