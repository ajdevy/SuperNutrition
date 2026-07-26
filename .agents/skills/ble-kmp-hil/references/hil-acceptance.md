# BLE hardware-in-the-loop acceptance

Use this reference only when a BLE behavior change needs real-device validation.

## Preconditions

- Record app commit or artifact hash, phone model/OS, peripheral firmware, and test account.
- Use the actual peripheral and at least one supported Android phone and iPhone.
- Enable redacted application logs; do not export secrets, payloads, or personal identifiers.

## Minimum matrix

Validate the affected subset plus the full connection path when protocol or lifecycle code changes:

1. First pairing or known-device recovery.
2. Permission deny, grant, and revoke.
3. Bluetooth powered off/on and app recovery messaging.
4. Scan filtering, connect, service discovery, read/write, and notification enablement.
5. Peripheral reboot, out-of-range, duplicate callback, and disconnect handling.
6. App background/foreground and process restart where the product claims support.
7. Reconnect/backoff behavior and firmware-version compatibility.

## Evidence format

```text
Build/commit:
Phone and OS:
Peripheral firmware:
Scenario:
Expected / observed:
Redacted log reference:
Result: pass | fail | blocked
Known limitation:
```
