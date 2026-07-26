---
name: ble-kmp-hil
description: Implement or verify Kotlin Multiplatform Bluetooth LE work safely. Use for BLE, Bluetooth, GATT, scan, pair, connect, reconnect, peripheral, CoreBluetooth, Android Bluetooth, notification, background, permission, or hardware-in-the-loop tasks.
---

# KMP BLE and hardware-in-the-loop

Use this workflow to produce evidence from the real peripheral; an emulator or simulator cannot prove BLE correctness.

## Workflow

1. Read the existing BLE/protocol contract. If it is absent, record device role, service and characteristic UUIDs, framing, timeouts, firmware range, and intended background behavior before changing code.
2. Keep shared policy in `commonMain`: protocol models, a small `BleClient` interface, explicit connection state, retries, and a fake. Keep Android Bluetooth and CoreBluetooth types in `androidMain` and `iosMain`.
3. Check platform state before scan or connect. Model permission/authorization, powered-off, scanning, connecting, discovery, ready, reconnecting, and terminal failure explicitly; never map callbacks straight to UI state.
4. Serialize GATT operations, cancel work on disconnect, use bounded backoff, and redact payloads, identifiers, and sensor/health data in logs.
5. Run the narrowest applicable common and platform checks. For a behavior-affecting change, read [the HIL acceptance reference](references/hil-acceptance.md) and execute its real-device scenarios.
6. Record evidence: app commit/build, phone and OS, peripheral firmware, scenario, redacted log excerpt, result, and known limitation.

## Completion rule

Do not claim a BLE change is complete without physical-device evidence for the affected flow. If the peripheral, a supported phone, or permission to run the test is unavailable, report the exact unverified scenarios instead.

## Safety

Never commit or paste pairing secrets, production device identifiers, raw BLE payloads, health data, or private logs into source, prompts, or test fixtures. Treat background operation as a separately validated product requirement on Android and iOS.
