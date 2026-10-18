# Native semantic regression before publication

The complete owned-emulator command `:apps:androidApp:connectedAndroidTest --continue --no-configuration-cache --console=plain` executed all three variants on API35x86_64, isolated ADB5041. Fresh XML records **43tests/9failures/0errors/0skipped per variant** (poc, production, uiAcceptance). This is129executed/27failed, not a successful native result. Raw evidence is `mobile/build/home-no-scroll-2026-10-17/android-final/`.

All nine failures concern native UI resources: three BrandNavigationVisual checks cannot find home_title; six ComposeSemantics checks cannot find home_title or profile_title. Logcat confirms activities created/resumed/displayed with no fatal exception. Earlier independent shell UI hierarchies export home_title on the same APK; the discrepancy is unresolved and must not be dismissed as emulator noise.

Acceptance: establish why in-process UiAutomator cannot see those resource ids, preserve all semantic/selection/navigation/foreground assertions, observe the existing native regression pass after a real fix or a proven test-harness correction, then rerun the complete three-variant suite. Do not shrink/remove Home typography or alter immutable screenshot/APK evidence for a test-only diagnosis.

## SystemUI diagnostic and guarded recovery attempts

The unchanged one-method diagnostic run (`android-diagnostic-red`) executed 1 test / 1 failure. Fresh logs prove the Activity was resumed with `focus=false`, its full-size ComposeView was attached and shown, and the focused accessibility window belonged to Android. Existing resource-id flags were already enabled (`serviceFlags=65618`, `reportViewIds=true`), so changing them would not fix the established cause. The native screenshot `mobile/build/home-no-scroll-2026-10-17/native-after-failure.png` shows the SystemUI ANR modal.

After the interrupted session, the emulator had stopped. A fresh cold start on the same owned AVD reproduced the SystemUI ANR before app launch; `systemui-after-cold-start.png` was captured and visually inspected. A guarded tap of its screenshot-observed Wait button restored launcher focus. No app production source, assertion, timeout or accessibility flag was changed.

The next exact-method attempt (`android-diagnostic-after-wait`) failed **before instrumentation execution**: ddmlib could not obtain emulator properties, reported Unknown API Level and No compatible devices, and generated a synthetic TestRunner failure. This is 0 actual app tests executed, not a new application assertion failure or a pass. Earlier native red XML and logs remain archived. Recovery and a complete three-variant rerun are still required.

## Recovery confirmed on a clean guest

The old guest still reproduced SystemUI/Quickstep ANRs with no target app launched after isolating it outside the default ADB scan range. Its data was preserved. A clean task-created `SN_Final_Verification_35_20261018` AVD, API35 x86_64 at the same 720x1560/320dpi geometry, reached a healthy launcher. The emulator adjusted the requested 2GiB RAM to 2560MiB; it uses four vCPUs on this Intel/16GiB host. Default ADB and Maestro Studio were left untouched.

The **same** `initialLaunchOpensHomeSurface` test then passed: 1 test / 0 failures / 0 errors / 0 skipped, fresh XML from `android-diagnostic-clean`, 2026-10-18T09:39:28Z–09:41:10Z. It used a fresh Gradle JVM with explicit `ANDROID_ADB_SERVER_PORT=5041`, emulator console/ADB ports5590/5591, and no semantic timeout, assertion or production-code change. This closes the diagnosed focus blocker for that assertion; it does not by itself replace the complete three-variant rerun, which is in progress.

## Complete native rerun passed

`android-final-clean` executed the complete canonical `:apps:androidApp:connectedAndroidTest` entrypoint with `--continue --no-configuration-cache --console=plain`, strict dependency verification, one worker, a fresh Gradle JVM and the explicitly selected clean emulator. Started 2026-10-18T09:41:42Z; finished 09:48:13Z; Gradle exit 0. Fresh XML shows **43 tests / 0 failures / 0 errors / 0 skipped in each of poc, production and uiAcceptance**: **129 actual app tests passed**. All three variant tasks executed; the final aggregate task being UP-TO-DATE does not replace this per-variant XML evidence.

The earlier native failures, diagnostic red and prerequisite failures remain recorded. No production-source, accessibility flag, semantic timeout, navigation/selection assertion or test filter was changed to obtain the full pass. Raw logs/XML are in `mobile/build/home-no-scroll-2026-10-17/android-final-clean/`; a portable summary, exact command and three native XML files are checked in under `home-no-scroll-verification-2026-10-18/`. This resolves the diagnosed native focus blocker; it is emulator evidence and does not refresh expired physical-native policy attestations.
