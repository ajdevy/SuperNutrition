# Rewards tab backdrop — 2026-10-16

User request: remove the light background directly behind the bottom tab bar on Rewards; use an emulator suitable for this Intel Mac to complete UI/device checks.

## N-23 finding and acceptance

`PhotoAnalysisMainLayout` uses the theme background for the full viewport on Rewards, while `RewardsScreen` paints only its content area with `RewardsPaper`. The reserved navigation area therefore forms a visibly separate pale pink strip behind the floating pill.

Acceptance: Rewards must have one continuous screen background behind the pill and throughout the safe-area padding. Keep navigation positions, touch actions, the pill illustration and scroll clearance unchanged. Home retains its existing illustrated background.

Before the production edit, extend the existing Rewards layout/visual check to compare pixels outside the pill above and below the navigation boundary; witness that assertion fail. Then share `RewardsPaper` with the full-screen main backdrop for the Rewards tab.

## Emulator setup

Host verified `x86_64`, macOS 15.7; 16 GiB RAM and about 129 GiB free. Android Emulator 36.6.11 reports Hypervisor.Framework acceleration available. Existing API 31 AVD uses arm64 and is unsuitable for accelerated execution on this host. Install the official Android 36 Google APIs x86_64 image, create a separate task-owned AVD, and preserve existing AVDs/physical-device settings. Emulator tests are compatibility/UI evidence, distinct from the preceding physical-device results.

## Verification

Pending: red/green existing visual assertion, full affected UI module, Android APK build, Kotlin/Native compilation, emulator instrumentation and manual navigation/gallery/keyboard/permission checks. No fresh physical-device result is claimed.

Red witnessed: the existing single Rewards visual test failed on the added navigation-background continuity assertion before the production edit. The main Rewards backdrop now uses the shared `RewardsPaper` color over the entire viewport, including navigation/safe-area space. The floating pill and tab geometry were preserved.

The single-build replay completed successfully in 5m 35s: fresh UI XML shows **102 tests, zero failures/errors/skips** across 18 suites; all six Android APK/test-APK artifacts assembled; `:feature:photo-analysis-ui:compileKotlinIosArm64` completed explicitly on Intel. Updated Rewards screenshot visually inspected and the strip is absent. Production APK SHA-256: `47083feca69ed74290fd7407ac8537775b9a5337e4bfdf912a9ffc7163105be0`. Module XML archived under `mobile/feature/photo-analysis-ui/build/reports/rewards-backdrop/green/`; emulator artifact manifest is `mobile/apps/androidApp/build/reports/rewards-emulator-2026-10-16/build-verification.json`.

Independent read-only bmad Blind Hunter review completed: no introduced user-visible defect. Optional safe-area test expansion was not treated as a product failure. Review report: `rewards-tab-backdrop-review-2026-10-16.md`.

Setup detail: the Android CLI's preset `medium_phone` attempted to fetch a second Google Play image despite the installed Google APIs image. That owned creation attempt was stopped before an AVD was created. The old SDK `tools/bin/avdmanager` is incompatible with the installed Java 17 (`javax.xml.bind.annotation.XmlSchema` missing); installing current official command-line tools avoids modifying Java or the old AVD. The intended guest remains Android 36 / x86_64 / 2 cores / 2048 MiB RAM.

`graphify update .` completed: 20,589 nodes, 27,547 edges, 1,962 communities; known 253 empty AST inputs and two missing SQL-parser inputs were reported, with oversized HTML omitted.

AVD `SuperNutrition_API_36_x86_64` created. Initial 1080×2340 software rendering saturated graphics-composer CPU; the setup was adjusted before app testing to recommended automatic rendering, verified Intel Iris host GPU, 720×1560 at 320 dpi and four vCPUs (host has four physical cores), retaining 2048 MiB guest RAM. Second boot completed in 130862 ms. The initial System UI startup displayed an ANR dialog. The native wrapper correctly refused to start Gradle/device tests: no native metadata or test result was produced. This is an emulator setup failure, distinct from an app failure. Stabilize the owned guest before running app checks.

The initial System UI dialog was given one explicit Wait in this task-owned guest. Launcher remained foreground afterward; boot-completed/API 36/x86_64/AVD identity and baseline settings were verified at 2026-10-16T06:55:02.711889+00:00. Canonical all-flavor Android instrumentation is now running on the emulator with the same strict foreground guards and separate output, not on the phone. Baseline guest font=1.0, timeout=2147483647, accelerometer rotation=1, user rotation=0.

## Final source checkpoint and lighter Intel guest

The focused-input resize regression was observed failing before its fix. The footer now remains under one stable composition parent when switching scroll policy. Final full replay completed with exit 0 in 11m35s: fresh **106/0/0/0** UI XML, six clean scoped lint reports (unchanged commonTest cached), all six current APKs and current explicit iOS Arm64 UI compile. Production digest is `6afa576e042f3f3f0c0284572635d7e3446d99807118046468e4425bdaa13be3`. Archived evidence: `photo-analysis-ui/build/reports/review-compact-footer/verified-stable-green/`.

The recovered API36 guest still showed both Launcher and Process system ANR before the current-source native run, including after one explicitly guarded Wait. No current-source Android tests were started in that failed environment. The prior N-23 API36 120-pass XML remains preserved independently. Stop only that owned guest and create a separate fresh API35 AOSP x86_64 AVD with 4GiB, two cores and 720x1560/320dpi; preserve existing AVDs. This lighter image omits unnecessary Google service processes. API35 is a supported Android runtime and remains emulator evidence, distinct from the physical Android16 checkpoint. New report root: `mobile/apps/androidApp/build/reports/rewards-emulator-api35-2026-10-16/`.
