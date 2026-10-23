# Final-source native verification follow-up

Source commit: `3d54a716730858a313ac54ba943675251d10852a`. No production or test source was changed during this follow-up.

## Observed failure and acceptance condition

The existing API35 task AVD eventually reached `sys.boot_completed=1` at 08:32:36 UTC, after Android system-server restart symptoms and a System UI ANR on Launcher. The ANR was dismissed using Wait before the final-source native suite started. The initial cold-start command returned success before the independent boot/animation guard had confirmed readiness.

The new full-suite attempt then completed 34 POC test cases, with five Activity launch timeouts at 45 seconds, as of 08:47 UTC. These are partial console/test-runner counts, not a completed suite or final XML counts. The three BrandNavigation tests and two Compose navigation tests timed out before their assertions. Concurrent saved logcat shows SurfaceFlinger `Faking VSYNC due to driver stall`, large system-component frame delays, and Activity creation taking 33 seconds. An independent read-only system `screencap` timed out after 12 seconds and listing app diagnostic files timed out after 8 seconds. These observations do not establish a production-code root cause; the final-source native check remains unresolved.

Acceptance: obtain a responsive Android system interface and system screenshot, then run the unchanged complete native suite with fresh variant XML reports. Do not increase timeouts, skip failing tests, replace old XML with partial output, or report the earlier pre-cleanup 150-test pass as fresh final-source proof.

One bounded environment recovery is planned: stop only this invocation and task AVD, then boot the same AVD headless with the emulator-provided software renderer and no saved snapshot. Preserve AVD user data, code, policy, native test inventory, and all unrelated host processes.

## First attempt cancellation

The task was interrupted (wrapper exit130); the isolated Gradle daemon subsequently exited. The runner log contains36 completed POC status records:29 passes and7 failures/errors. This is explicitly partial runner output, not final XML. Production and UI Acceptance were not reached. See `native-final-attempt1-partial.json` and `native-final-attempt1-interrupted.log`. Existing earlier full-run XML is retained under its original filenames and remains pre-signature-cleanup evidence.

The task AVD was stopped via its emulator console. No ADB server restart, user application termination, source change, timeout relaxation, or AVD data wipe was used. A single headless restart uses `-no-window -no-audio -no-snapshot-load -no-snapshot-save -gpu swiftshader`; a separate300-second readiness guard must pass before any new test command.

## Bounded renderer recovery result

The headless SwiftShader restart reached boot completion at08:51:30 UTC (emulator log:102608ms). The first screenshot was black while the screen was asleep. After Wake/Menu, the Launcher displayed `Quickstep isn't responding`, before the application was launched. See `environment-headless-quickstep-anr.png` and `environment-headless-readiness.jsonl`. No second Gradle/instrumentation invocation was started. A boot property alone is insufficient readiness evidence. The remaining final native/visual/hash checks are unresolved. No production-code cause was established or hidden by changing tests.
