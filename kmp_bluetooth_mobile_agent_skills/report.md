# Research report: Minimal agent skill and workflow stack for Kotlin Multiplatform mobile development and efficient Codex/Claude use

## Scope

All notable options, prioritizing current 2024-2026 maintenance, ecosystem fit, public feedback, and minimal context footprint

This report separates **official maintenance/adoption signals** from **direct public feedback**. Stars, installs, and forks are not treated as satisfaction ratings.

## Contents

1. [Bluetooth, device QA, signing, release, and publishing specialization](#bluetooth-device-qa-signing-release-and-publishing-specialization)
2. [KMP mobile implementation and execution skills](#kmp-mobile-implementation-and-execution-skills)
3. [Minimal cross-agent memory, coding, and context-management stack](#minimal-cross-agent-memory-coding-and-context-management-stack)

## Decision matrix

| Area | Minimal recommendation | Direct public feedback / operational caveat |
| --- | --- | --- |
| Bluetooth, device QA, signing, release, and publishing specia… | decision: Adopt a three-skill baseline: official `android-cli`, repo-local `ble-kmp-hil`, and repo-local `mobile-release`. Use Kotlin migration skills only when their named migration is active. Do not install a global BLE, signing, or publ… | method: Only direct public issue/discussion feedback is treated as feedback. Stars, forks, and download counts are listed separately as adoption signals and are not interpreted as sentiment.; feedback table: candidate: Android Skills and A… |
| KMP mobile implementation and execution skills | Priority 1: add a project-local Android execution entry point using the official Android CLI skill and keep Gradle/ADB commands in the repository guide. Priority 2: add only the official Android testing and performance skills when the task… | / Candidate / Relevant public feedback / Decision impact / / --- / --- / --- / / Android CLI skill / A user reported stale `android screen` examples against Android CLI 1.0 in issue #74; another reported that its imperative `curl / bash` g… |
| Minimal cross-agent memory, coding, and context-management st… | Adopt now: root AGENTS.md as the only canonical instructions file; root CLAUDE.md containing '@AGENTS.md'; docs/decisions/ for short ADRs; and a small executable verification surface. Keep default startup context lean and do not add a new… | Claude Code issue #27298: a user asked for layered memory because always-loaded MEMORY.md and an ever-growing CLAUDE.md waste context. This directly supports a small startup index with lazy topic files rather than a large persistent prompt… |

## Recommended minimal stack

1. Keep one concise, versioned **AGENTS.md** as the canonical repository guide and a tiny **CLAUDE.md** wrapper that imports it.
2. Add only **android-cli** externally for Android execution; do not install all Android or Kotlin skills.
3. Keep exactly two explicit repo-local skills: **ble-kmp-hil** and **mobile-release**. They load only for Bluetooth/device validation or signing/release work.
4. Treat Kotlin migrations, Xcode execution MCP, Kable, fastlane, Graphify, and a GitHub connector as on-demand tools—not baseline skills.
5. Use scripts and CI as the quality contract. A real-device BLE result and a non-production store delivery are evidence; a successful simulator build is not.

## Bluetooth, device QA, signing, release, and publishing specialization

_Evidence source: `bluetooth_device_qa_signing_release_and_publishing_specialization.json`_

### Classification

_What is being evaluated and whether it is official, community-maintained, or a proposed custom skill._

- **Research Item:** Bluetooth, device QA, signing, release, and publishing specialization
- **Evaluation Date:** 2026-07-26
- **Finding:** The reviewed official Android and Kotlin skill catalogs contain useful, narrowly scoped agent skills, but neither contains an end-to-end BLE/KMP, Apple release, Android signing, or store-publishing skill. The smallest dependable solution is one official Android tool skill plus two small repo-local skills that activate only for BLE/HIL and release work. BLE libraries and release CLIs are dependencies or tools, not additional always-loaded agent skills.
- **Recommended Minimal Skill Registry:**
  - name: android-cli; kind: Official Android skill; scope: Android environment, project analysis, emulator or device deployment, screenshots, UI inspection, and Android documentation lookup.
  - name: ble-kmp-hil; kind: Proposed repo-local custom skill; scope: BLE architecture, permission and background rules, platform boundaries, reconnection, logs, and hardware-in-the-loop acceptance evidence.
  - name: mobile-release; kind: Proposed repo-local custom skill; scope: Signed Android AAB and iOS archive creation, TestFlight and Play internal-track delivery, credential guardrails, release evidence, and human production approval.
- **Explicit Non Recommendation:** Do not add a global generic BLE skill, an unreviewed community mobile skill pack, a separate signing skill, a separate store-publishing skill, or all Android/Kotlin skills. They increase registry noise without removing the requirement for a product-specific hardware and release checklist.

### Candidates

_Relevant skill, CLI, repository, or workflow candidates, including an exact name or source when available._

- name: Android Skills: android-cli; classification: Official Google agent skill and CLI; fit: Strong base skill for Android create, build-adjacent setup, emulator and connected-device deployment, UI capture, project description, and current Android knowledge. It is a tool skill, not a BLE implementation or Play publishing workflow.
- name: Kotlin Agent Skills; classification: Official JetBrains/Kotlin agent-skill collection; fit: The current published workflows are Java-to-Kotlin, AGP 9 migration, and CocoaPods-to-SwiftPM migration. The SwiftPM migration is useful when a KMP project needs native iOS dependency integration, but none of the skills covers BLE, CoreBluetooth, device QA, signing, TestFlight, or Play publishing.
- name: Kable; classification: Community-maintained Kotlin Multiplatform BLE library, not an agent skill; fit: Kable offers coroutine-based scanning, connection state, GATT I/O, logging, Android-specific connection settings, Apple background-state restoration configuration, and Android/iOS targets. It can reduce duplicate API code, but it does not declare the host app's permissions, cannot validate firmware behavior, and must not replace the app's own state machine or HIL test suite.
- name: Native Android Bluetooth APIs plus CoreBluetooth behind a KMP boundary; classification: Proposed implementation pattern; fit: This is the most predictable option for a specialized production peripheral. `commonMain` owns protocol models, reducer/state machine, retries, telemetry contracts, and a small `BleClient` interface; `androidMain` owns Android Bluetooth and permissions; `iosMain` owns CoreBluetooth, authorization, restoration, and background modes. Kotlin recommends interfaces and platform implementations for larger platform-specific logic rather than exposing platform types in common code.
- name: fastlane: match, pilot, supply; classification: Mature community mobile release automation tool, not an agent skill; fit: One tool can synchronize iOS signing material, upload TestFlight builds, upload Android APK/AAB artifacts, manage metadata, and target staged Play tracks. It is valuable once both store release paths are stable, but it does not make App Review, store policy, or real-device BLE verification automatic.
- name: Gradle Play Publisher; classification: Community Android-only Gradle publishing plugin; fit: It can build, upload, promote AAB/APK artifacts, and synchronize Play metadata from Gradle, which fits a Kotlin project well.
- name: ble-kmp-hil and mobile-release; classification: Two proposed custom repo-local skills; fit: They retain the product-specific facts that external tools lack: service and characteristic UUIDs, firmware compatibility, background policy, expected state transitions, device matrix, release identifiers, approved tracks, and human approval boundaries.

### Coverage

_Lifecycle stages covered: create, build, run, debug, test, sign, release, publish, maintain._

- **Create:** Use a KMP project with `commonMain`, `androidMain`, and `iosMain`. Define a common `BleClient` interface, explicit connection and permission state models, protocol codecs, and a fake implementation. Keep Android Bluetooth and CoreBluetooth imports out of common code.
- **Build:** Build all configured KMP targets on every change. Android uses the Gradle wrapper and a pinned JDK/Android SDK; iOS archive and device builds require macOS with Xcode. The Android CLI can describe an Android project, manage SDK/emulator setup, and deploy an already-built APK, but it does not replace Gradle or Xcode.
- **Run And Debug:** Use Android CLI/ADB for Android deploy, logs, screenshots, and UI inspection; use Xcode and Xcode command-line tools for iOS. Enable sanitized BLE event logs with connection attempt IDs, OS/device/firmware versions, GATT operation timing, permission state, and terminal failure reasons. Never include secret payloads or personal device identifiers in exported logs.
- **Test:** Run common-unit tests, Android unit/instrumentation tests, and iOS tests for all target builds. Then run a hardware-in-the-loop matrix using the actual peripheral and at least one supported Android phone and iPhone. The matrix must cover first pairing, permission deny/grant/revoke, scan filters, connect, discovery, MTU or protocol setup, read/write/notify, peripheral reboot, radio off/on, out-of-range, app background/foreground, process termination, reconnect/backoff, duplicate callbacks, and firmware-version compatibility.
- **Sign:** Android: build a release AAB using a separate upload key, then use Play App Signing for the distribution key. iOS: archive with Xcode automatic/cloud-managed signing or a deliberately managed credential store such as fastlane match. Signing material is injected only at the release boundary from a secret manager or ephemeral CI credentials.
- **Release:** Default automation destination is non-production: Android Play internal testing and iOS TestFlight. Verify the signed artifact, package/bundle ID, version/build number, symbol mapping, generated APK/device install, HIL result, and store processing status before a staged release.
- **Publish:** Android production is a staged rollout only after a human approval gate; Google Play Developer API edits can create drafts and track releases. iOS production remains a human-reviewed App Store Connect/App Review decision even when archive upload is automated. Record the exact build IDs, track, reviewer, and rollback plan.
- **Maintain:** Review `ble-kmp-hil` after each target-SDK, major Android, iOS, Xcode, KMP, firmware, or BLE-library upgrade. Re-run the complete HIL matrix after radio/background behavior changes and pin dependency/tool versions in source control.

### Platform Support

_Android, iOS, KMP/common code, macOS/Xcode, CI, physical-device support, and important constraints._

- **Android:** For Android 12+ scanning, connecting, and advertising require the runtime `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, and `BLUETOOTH_ADVERTISE` permissions as applicable. Legacy permissions must be capped at API 30. `neverForLocation` is valid only when scan results are not used to derive location and can filter some beacons. Android Companion Device Manager can simplify association; background use requires an intentional Companion Device or `connectedDevice` foreground-service design rather than periodic scans. Android Emulator now lists Classic and BLE profiles for API 31+, but it is not a replacement for the actual peripheral/device matrix.
- **Ios:** CoreBluetooth requires an `NSBluetoothAlwaysUsageDescription` purpose string and explicit handling of authorization and manager state. Background BLE requires an appropriate `UIBackgroundModes` value such as `bluetooth-central`; it is constrained by the operating system and must be paired with state preservation/restoration using a stable central-manager restoration identifier when long-lived behavior is required. Apple documents cases where a user force-quit or certain radio actions prevent restoration, so the app must expose honest recovery UI and never promise an unconditional background connection.
- **Kmp Common Code:** Kotlin `expect`/`actual` declarations or a common interface plus platform factories are the correct boundary for native BLE APIs. Prefer a common interface with fakes and platform implementations for a larger BLE service; use `expect`/`actual` only for the small platform construction boundary. This preserves testability and avoids leaking Android or Apple types into shared business logic.
- **Macos And Ci:** iOS compilation, archive, signing, and TestFlight preparation require macOS/Xcode and an Apple Developer/App Store Connect team. CI therefore needs a protected macOS runner. Android build/test can run on macOS, Linux, or Windows, but release secrets and a connected physical device should be isolated from untrusted pull-request jobs.
- **Physical Device Support:** HIL is mandatory. Maintain a checked-in device matrix containing phone model, OS, app build, peripheral firmware, transport role, and result. Use an emulator/simulator for ordinary UI and deterministic logic only; validate radio, peripheral firmware, background wakeup, and OS-specific reconnection on physical devices.

### Bluetooth Relevance

_Direct Bluetooth/BLE capability, or the exact missing parts and custom-skill requirements._

- **Direct Skill Coverage:** No reviewed official skill directly owns Android BLE plus CoreBluetooth plus KMP boundaries and release-quality HIL. Android `android-cli` helps deploy/debug Android but is not a Bluetooth specialist. Kotlin official skills help migrations only.
- **Recommended Ble Kmp Hil Skill Contents:**
  - Require a written GATT/protocol contract: device roles, UUIDs, discovery order, read/write semantics, notification enablement, packet framing, encryption/authentication expectations, expected timeouts, and firmware compatibility.
  - Enforce a common state model such as Unsupported, Unauthorized, PoweredOff, ReadyToScan, Scanning, Connecting, Discovering, Ready, Reconnecting, and Failed; platform callbacks may not directly mutate UI state.
  - Require Android permission and Bluetooth-adapter checks before scan/connect; require iOS authorization/CBManager state checks and a purposeful user-facing recovery path.
  - Use narrow service-UUID scan filters whenever the peripheral supports them. Persist only a safe logical device identity; do not assume Android MAC addresses and Apple peripheral UUIDs are interchangeable or stable cross-platform identities.
  - Serialize GATT work, cancel work correctly on disconnect, distinguish transient retryable failures from user action, and use bounded exponential backoff with visibility into retry state.
  - Treat background design as a separate product requirement. Android needs Companion Device or foreground-service policy; iOS needs declared background mode and state restoration. Neither platform guarantees indefinite background execution.
  - Require a real-device acceptance report with raw-safe BLE log excerpts, app SHA, phone/OS, peripheral firmware, scenario result, and known limitation before declaring BLE work complete.
- **Kable Assessment:** Kable is the highest-signal KMP BLE dependency found in this pass: its current docs show Android/Apple scanning filters, GATT lifecycle, logging, Android `autoConnect` configuration, Apple background state-restoration configuration, and explicit host-app permission ownership. It is a viable acceleration layer, not a proof that a device flow works. Its current 0.x versioning means exact pinning and adapter isolation are prudent.
- **Native Boundary Assessment:** For a medical, safety-sensitive, high-throughput, proprietary, or long-running peripheral protocol, use direct Android Bluetooth and CoreBluetooth implementations behind the common interface unless a Kable spike passes the full HIL matrix. This keeps platform edge cases visible instead of hiding them behind a library abstraction.

### Toolchain And Prerequisites

_Required CLIs, SDKs, hardware, accounts, permissions, signing assets, CI runners, and non-interactive limits._

- **Baseline Development:**
  - Pinned Kotlin, Gradle wrapper, Android Gradle Plugin, JDK, Android SDK/build-tools, Android CLI/ADB, and a physical Android device with developer debugging enabled.
  - macOS with a supported Xcode version, a physical iPhone, Apple Developer Program membership, an App Store Connect app record, bundle identifiers, and permissions/capabilities configured for the target.
  - The actual BLE peripheral or a controlled firmware test fixture, a documented firmware version, power/reboot control if available, and a reproducible way to capture safe radio/application logs.
  - A CI macOS runner for iOS builds and a protected release environment. Never make a physical-device or signing environment available to arbitrary forked pull requests.
- **Android Release:**
  - A unique package name, monotonically increasing version code, upload keystore and alias/password stored outside the repository, and Play App Signing configured. Google Play requires new apps to use Play App Signing for AAB distribution.
  - A Play Console app created manually, a least-privilege service account or workload identity, Android Publisher API access, an internal testing track, and a bundletool/device-install smoke test before promotion.
- **Ios Release:**
  - A distribution-capable Apple team role, App Store Connect API key with narrowly scoped access, an Apple Distribution certificate/provisioning solution, and an Xcode archive/export workflow. Automatic/cloud-managed signing is simpler for a small team; fastlane match is useful only when controlled shared credential storage is truly needed.
  - TestFlight test information, internal testers, external-test review expectations, privacy/export-compliance metadata, and an App Review submission owner.
- **Fastlane If Adopted:**
  - Pin fastlane through Bundler rather than a machine-global gem.
  - Use API keys rather than personal Apple ID login where possible; fastlane documents App Store Connect API-key authentication as preferred for TestFlight tooling.
  - For Play CI, prefer Workload Identity Federation to a downloadable JSON key; if a key is unavoidable, keep it in a secret manager and grant no production-release permission to the automation identity.

### Installation Or Adoption

_Installation/use path and available adoption metrics such as installs, stars, forks, or release activity._

- **Minimal Sequence:**
  - 1. Install only Android `android-cli` in the mobile repository and verify it can identify the Android project and deploy a debug APK to a connected test device.
  - 2. Create repo-local `ble-kmp-hil` with a short entrypoint and progressive references for the GATT contract, Android/iOS permission matrix, background policy, state machine, and HIL report template.
  - 3. Create repo-local `mobile-release` with a short entrypoint and scripts for artifact inspection, Android internal-track delivery, iOS archive/TestFlight upload, release evidence, and mandatory production approval prompts.
  - 4. Choose direct platform BLE adapters first, or run a time-boxed Kable spike and keep it only if all target hardware scenarios pass.
  - 5. Complete one manual Play internal and TestFlight delivery before introducing fastlane. Add fastlane only if recurring releases justify the dependency.
- **Adoption Signals As Of 2026 07 26:**
  - **Android Skills:** Official GitHub repository shows approximately 6.4k stars and 363 forks; the catalog supports selective installation and warns users to double-check AI output. This is an adoption signal, not a quality guarantee or user-satisfaction score.
  - **Kotlin Agent Skills:** Official Kotlin documentation published the skills workflow in June 2026; the GitHub organization listing shows approximately 718 stars and a small, focused current catalog. It is too new and too narrow to treat as BLE/release coverage.
  - **Kable:** GitHub shows approximately 1.2k stars and 113 forks; release 0.44.3 was published on 2026-07-22 and includes Android maintenance work. This supports active maintenance, not automatic suitability for a given peripheral.
  - **Fastlane:** GitHub shows approximately 41.9k stars and 6.0k forks, with release 2.235.0 published on 2026-05-26. It is the strongest single cross-store automation candidate found, but must be pinned and smoke-tested against current store rules.
  - **Gradle Play Publisher:** GitHub shows approximately 4.3k stars and release 4.0.0 in January 2026, but the project explicitly states maintenance mode and says issues are ignored.
- **Context Footprint Control:** Make the two custom skill entrypoints concise and keyword-gated. Put platform tables, release commands, and device-matrix examples under `references/`; load them only after the task is classified as BLE/HIL or release. Store volatile secrets and current device inventory outside SKILL.md; link to a protected runbook or CI environment instead.

### Evidence Of Maintenance

_Evidence of official stewardship, recent updates, repository activity, or explicit uncertainty._

- **Android Skills And Cli:** Google maintains the Android skills repository and official Android CLI documentation. The skills repository had a v1.0.0 release in May 2026, and the CLI docs describe current device, SDK, and skill-install operations.
- **Kotlin Agent Skills:** JetBrains/Kotlin documents compatibility with Codex, Claude Code, Gemini, Copilot, and Junie. Its June 2026 documentation identifies the maintained migration workflows, but it does not claim BLE or release specialization.
- **Kable:** Kable is active: GitHub shows current releases through 0.44.3 on 2026-07-22, recent Android-related maintenance, a maintained sample, and dedicated documentation. Its release series remains 0.x, so upgrade compatibility must be assessed deliberately.
- **Fastlane:** fastlane has a large active repository, many releases, current 2026 release activity, and maintained action documentation for code signing, TestFlight, and Play uploads. Store/API changes can still break release lanes, so a scheduled non-production smoke lane is necessary.
- **Gradle Play Publisher:** The plugin is explicitly in maintenance mode despite a recent release. That status outweighs its historical popularity for a greenfield minimal stack.
- **Custom Skills:** The project owns the two custom skills. Version them with the app, require review for policy changes, and update them after Android/iOS/KMP/Xcode/firmware changes rather than accumulating more skills.

### Public User Feedback

_Direct user reviews, issue/discussion feedback, or a clear statement that meaningful feedback was unavailable; never infer sentiment from installs alone._

- **Method:** Only direct public issue/discussion feedback is treated as feedback. Stars, forks, and download counts are listed separately as adoption signals and are not interpreted as sentiment.
- **Feedback Table:**
  - candidate: Android Skills and Android CLI; feedback found: No substantive independent user review was identified in the reviewed sources. The official repository invites issues and feature requests; the official CLI documentation lists Windows emulator/download limitations.; operational takeaway: Use it for Android tooling, but do not infer BLE or release reliability from repository popularity.
  - candidate: Kotlin Agent Skills; feedback found: No meaningful public end-user feedback specific to BLE or release work was found. The published scope itself is limited to migration workflows.; operational takeaway: Treat it as optional migration help, not a validated mobile-device workflow.
  - candidate: Kable; feedback found: A user reported that disabling Bluetooth from iOS Control Center did not emit the expected disconnected state while Android behaved correctly. The maintainer reproduced the platform distinction, linked a fix, and closed the issue in a later milestone.; feedback source: https://github.com/JuulLabs/kable/issues/959
  - candidate: fastlane; feedback found: Users reported that automated internal-track publication failed for draft Play apps after Play rule changes; a later commenter described a manual closed-testing initialization workaround.; feedback source: https://github.com/fastlane/fastlane/issues/21429
  - candidate: Gradle Play Publisher; feedback found: The project README states maintenance mode and that issues are ignored. An older user report also documented variant application-ID publishing trouble and a maintainer workaround.; feedback source: https://github.com/Triple-T/gradle-play-publisher and https://github.com/Triple-T/gradle-play-publisher/issues/979
- **Overall Feedback Conclusion:** The strongest real-world signal is not a uniformly positive library/tool review; it is recurring evidence that Bluetooth and store behavior are platform- and policy-dependent. The workflow must produce real-device and non-production-store evidence rather than relying on a skill description or local compile success.

### Security And Release Safety

_Secret-handling and approval guardrails, especially for keystores, Apple credentials, Play/App Store releases, and real device access._

- **Secrets:** Never commit, paste into agent prompts, display in logs, or give broad filesystem access to Android keystores, key passwords, Apple `.p8` keys, provisioning-profile archives, private certificates, fastlane match passphrases, Play service-account keys, or production API tokens. Use a secret manager, masked CI variables, short-lived workload identity where available, and an ephemeral keychain/keystore workspace.
- **Android:** Use Play App Signing with a separate upload key. Keep the upload private key distinct from the Play-held app-signing key, verify the expected certificate fingerprint and package/version before upload, and give automation access only to the intended app and non-production tracks by default. Upload a signed AAB, generate/install device-specific APKs for a smoke test, then move through internal testing before staged production approval.
- **Ios:** Use an App Store Connect API key with the narrowest viable role, a protected Apple team, and a known signing strategy. Xcode automatic/cloud-managed signing is preferable for small teams; if fastlane match is adopted, use a private encrypted credential store and never run destructive certificate/profile reset commands without an explicit human approval. Archive/upload to TestFlight can be automated; App Store submission and release require a named human owner.
- **Agent Guardrails:** The `mobile-release` skill must default to plan or dry-run mode, label side effects, require an explicit confirmation token for TestFlight or Play upload, and require a second explicit production/staged-rollout approval after displaying immutable release facts. It should block production when HIL evidence, version uniqueness, release notes, rollback path, or credential provenance is absent.
- **Device And Privacy:** Use dedicated test accounts and consented devices. Treat BLE identifiers, advertisement data, health/sensor values, and diagnostic logs as potentially sensitive. Redact payloads unless a narrowly scoped debugging session requires them, and retain logs for a defined period.

### Limitations And Gaps

_Unsupported lifecycle work, stale guidance risk, platform gaps, or validation limitations._

- **Skill Gap:** No skill can prove a BLE peripheral works on a particular phone, OS build, firmware, radio state, or background condition. A reusable skill can enforce the test protocol but cannot replace hardware-in-the-loop execution.
- **Platform Gap:** Android and iOS deliberately differ in permission, device identity, scan filtering, background scheduling, process death, radio state, and restoration behavior. A common Kotlin API cannot erase those differences without weakening observability or correctness.
- **Simulator Gap:** Android Emulator reports Classic/BLE profiles for API 31+, but actual peripheral/GATT, OEM behavior, power, and background validation remain physical-device work. iOS CoreBluetooth simulator support has historical limitations and current community questions; use a physical iPhone for release acceptance.
- **Release Gap:** Store app creation, legal agreements, banking/tax setup, policy declarations, App Review, and external TestFlight review include human or portal-side steps that cannot be safely treated as agent-autonomous. Store rules drift, so a previously working lane may require an update.
- **Dependency Gap:** Kable is not an official platform API and is still pre-1.0; fastlane is community maintained; Gradle Play Publisher is in maintenance mode. Pin versions, keep adapters, and retain a documented direct-API fallback.
- **Operational Gap:** The exact peripheral protocol, target OS range, regulatory classification, required background semantics, store accounts, CI provider, and existing signing posture were not supplied. The proposed skill references must be tailored once those facts are known.

### Recommendation

_Adopt, adapt, or avoid recommendation with priority and rationale._

- **Decision:** Adopt a three-skill baseline: official `android-cli`, repo-local `ble-kmp-hil`, and repo-local `mobile-release`. Use Kotlin migration skills only when their named migration is active. Do not install a global BLE, signing, or publishing skill pack.
- **Implementation Choice:** Begin with a common BLE interface plus native Android/CoreBluetooth implementations. Run a short Kable spike only if reducing duplicated BLE code is valuable; keep Kable only if it passes the real-device matrix and remains hidden behind the adapter.
- **Release Choice:** Complete manual Android internal-track and iOS TestFlight delivery first. Then add one pinned fastlane setup if recurring cross-store release automation is worth it. Do not add Gradle Play Publisher to a new stack because of its stated maintenance status.
- **Required Workflow:**
  - Classify the task. Load `ble-kmp-hil` only for Bluetooth/HIL work and `mobile-release` only for signing/release/publishing work.
  - For BLE changes: update the protocol/state contract, run common and platform builds/tests, then execute and attach the HIL matrix report before completion.
  - For release: build signed artifacts in protected CI, inspect/install the Android AAB-derived APKs and validate the iOS archive, upload only to Android internal testing/TestFlight after explicit authorization, collect processing evidence, and stop before production without a second human approval.
  - For production: require a fixed release manifest, HIL report for the exact candidate build, release notes, staged rollout percentage, monitoring window, rollback action, named owner, and explicit approval.
- **Success Criteria:** The team can reproduce Android and iOS BLE acceptance on physical hardware, trace a failed connection to a structured state/log event, produce signed AAB/IPA artifacts without exposing secrets, deliver them to Play internal testing and TestFlight, and prove each store-side result with build identifiers and logs.

### Sources

_Primary sources and directly relevant feedback links._

- title: Android Skills repository; url: https://github.com/android/skills; relevance: Official catalog, selective installation behavior, repository activity, and absence of a BLE/signing workflow in the listed skill tree.
- title: Android CLI documentation; url: https://developer.android.com/tools/agents/android-cli; relevance: Official agent-first Android setup, device/emulator, deployment, screenshots, project inspection, and skill management.
- title: Kotlin AI skills documentation; url: https://kotlinlang.org/docs/kotlin-ai-skills.html; relevance: Official current Kotlin skill scope and supported Codex/Claude-compatible agents.
- title: Kotlin expected and actual declarations; url: https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html; relevance: Official KMP boundary and interface guidance for native platform APIs.
- title: Kable repository and documentation; url: https://github.com/JuulLabs/kable; relevance: KMP BLE API, scan filtering, state lifecycle, logging, Apple background configuration, and host-permission responsibility.
- title: Kable release 0.44.3; url: https://github.com/JuulLabs/kable/releases; relevance: Recent maintenance and release activity.
- title: Kable iOS Bluetooth-off user issue; url: https://github.com/JuulLabs/kable/issues/959; relevance: Direct user feedback and maintainer resolution showing platform-specific radio behavior.
- title: Android Bluetooth permissions; url: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions; relevance: Runtime permission, legacy compatibility, location, and BLE feature requirements.
- title: Android BLE background communication; url: https://developer.android.com/develop/connectivity/bluetooth/ble/background; relevance: Official background scan, connection, foreground-service, and Companion Device guidance.
- title: Android companion device pairing; url: https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing; relevance: Association, background/presence behavior, and current API constraints.
- title: Android Emulator networking capabilities; url: https://developer.android.com/studio/run/emulator-networking; relevance: Current emulator Classic/BLE profile capability statement.
- title: Apple Core Bluetooth background processing; url: https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html; relevance: Background modes and state-preservation/restoration basics.
- title: Apple Bluetooth state-restoration relaunch rules; url: https://developer.apple.com/documentation/technotes/tn3115-bluetooth-state-restoration-app-relaunch-rules; relevance: Current restoration limits after user actions, radio changes, and force quit.
- title: Apple Bluetooth usage description; url: https://developer.apple.com/documentation/BundleResources/Information-Property-List/NSBluetoothAlwaysUsageDescription; relevance: Required iOS Bluetooth purpose string.
- title: Apple app distribution documentation; url: https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases; relevance: Apple Developer Program, archive, TestFlight, App Store distribution, signing, and review workflow.
- title: Apple TestFlight overview; url: https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview; relevance: Tester limits, review, feedback, and build lifespan.
- title: Android app signing; url: https://developer.android.com/studio/publish/app-signing; relevance: Upload key, Play App Signing, key separation, and release signing requirements.
- title: Google Play Developer API; url: https://developers.google.com/android-publisher; relevance: Transactional edits, upload, tracks, staged rollout, and automation boundary.
- title: fastlane repository; url: https://github.com/fastlane/fastlane; relevance: Cross-platform release automation maintenance, capabilities, and credential locality.
- title: fastlane match, pilot, and supply documentation; url: https://docs.fastlane.tools/actions/match/ ; https://docs.fastlane.tools/actions/pilot/ ; https://docs.fastlane.tools/actions/supply/; relevance: Signing synchronization, TestFlight API-key flow, Play tracks, and workload identity guidance.
- title: Gradle Play Publisher repository; url: https://github.com/Triple-T/gradle-play-publisher; relevance: Maintenance-mode declaration and scope for the avoid recommendation.

### Primary links

- https://github.com/android/skills
- https://developer.android.com/tools/agents/android-cli
- https://kotlinlang.org/docs/kotlin-ai-skills.html
- https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html
- https://github.com/JuulLabs/kable
- https://github.com/JuulLabs/kable/releases
- https://github.com/JuulLabs/kable/issues/959
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- https://developer.android.com/develop/connectivity/bluetooth/ble/background
- https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing
- https://developer.android.com/studio/run/emulator-networking
- https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html
- https://developer.apple.com/documentation/technotes/tn3115-bluetooth-state-restoration-app-relaunch-rules
- https://developer.apple.com/documentation/BundleResources/Information-Property-List/NSBluetoothAlwaysUsageDescription
- https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases
- https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview
- https://developer.android.com/studio/publish/app-signing
- https://developers.google.com/android-publisher
- https://github.com/fastlane/fastlane
- https://docs.fastlane.tools/actions/match/ ; https://docs.fastlane.tools/actions/pilot/ ; https://docs.fastlane.tools/actions/supply/
- https://github.com/Triple-T/gradle-play-publisher


## KMP mobile implementation and execution skills

_Evidence source: `kmp_mobile_implementation_and_execution_skills.json`_

### Classification

_What is being evaluated and whether it is official, community-maintained, or a proposed custom skill._

A minimal, execution-oriented stack: official Google Android skills and Android CLI for Android work; official JetBrains Kotlin skills for KMP migrations and Kotlin/Native build performance; one conditional iOS execution integration; and selectively adapted community KMP guidance. These are complementary, not substitutes for Gradle/Xcode builds or real-device BLE validation.

### Candidates

_Relevant skill, CLI, repository, or workflow candidates, including an exact name or source when available._

- candidate: Google Android skills plus Android CLI; kind: Official Google skill pack and CLI; adopt: Primary Android foundation. Install android-cli, then add testing-setup, perfetto-trace-analysis, r8-analyzer, and play-policy-insights only when their trigger applies.
- candidate: JetBrains Kotlin AI skills; kind: Official JetBrains Kotlin/KMP skills; adopt: Install on demand: kotlin-tooling-native-build-performance for slow iOS/Kotlin-Native builds; kotlin-tooling-agp9-migration and kotlin-tooling-cocoapods-spm-migration only during their named migrations.
- candidate: Apple Xcode 27 built-in skills; kind: Official Apple/Xcode skills; adopt: Use only inside an Xcode 27-capable macOS environment, especially device-interaction, test-modernizer, and audit-xcode-security-settings. Do not duplicate them globally for every agent.
- candidate: XcodeBuildMCP with its optional xcodebuildmcp skill; kind: Maintained community/open-source MCP and skill; adopt: Conditional iOS execution path for Codex or Claude outside Xcode. Enable simulator workflows first; enable device, debugger, and UI automation workflows only when the team has approved signing and physical-device access.
- candidate: mmiani/kotlin-kmp-claude-agent-skills; kind: Community KMP catalog; adopt: Adapt or selectively install only kotlin-platform-kmp-bridges, kotlin-testing-kmp, and kotlin-project-bugfix after review. Do not install its full pipeline, hooks, settings, or GitHub workflows as a baseline.
- candidate: Broad community Compose kits such as felipechaux/kmp-compose-multiplatform-skill and haidrrrry/compose-kotlin-agent-skills; kind: Community reference only; adopt: Avoid as a default installation. Mine individual rules only if they match the repository's existing architecture.

### Coverage

_Lifecycle stages covered: create, build, run, debug, test, sign, release, publish, maintain._

| Candidate | Create/build/run | Debug/test/performance | Sign/release/publish |
| --- | --- | --- | --- |
| Android CLI + official skills | Create projects; manage SDK/AVD; deploy APKs; Gradle remains the build authority | Layout/screenshot/ADB-oriented inspection; testing setup; Perfetto; R8 | Policy and billing-adjacent guidance only; no complete Play publishing workflow |
| JetBrains Kotlin skills | KMP Gradle and iOS integration migrations; Kotlin/Native build tuning | Measured native build diagnosis | No signing, archive, store, or device-QA workflow |
| Apple Xcode 27 skills | Xcode-local Swift/UI/device workflows | Device interaction, testing modernization, security settings | No evidence of end-to-end store-publishing automation |
| XcodeBuildMCP | Discover/build/run/test on simulator and signed physical device | Logs, LLDB, screenshots, hierarchy, UI automation | Requires Xcode signing; no replacement for App Store approval/publishing controls |
| Selected community KMP skills | Architecture, source sets, bug fixing, KMP tests | Guidance only; must invoke native tools and devices | No release authority |

### Platform Support

_Android, iOS, KMP/common code, macOS/Xcode, CI, physical-device support, and important constraints._

Android: the Android CLI and official skills are strongest for SDK, emulator, APK deployment, UI inspection, Android tests, Perfetto, and R8. KMP/common code: JetBrains skills cover AGP 9 KMP structure, SwiftPM migration, and Kotlin/Native compilation performance. iOS: full Xcode on macOS is still required; use Apple Xcode 27 skills in Xcode or XcodeBuildMCP for external-agent simulator/device execution. Physical Android and iOS devices remain necessary for Bluetooth behavior, permission prompts, background transitions, pairing, and connection recovery.

### Bluetooth Relevance

_Direct Bluetooth/BLE capability, or the exact missing parts and custom-skill requirements._

No inspected official Android, JetBrains Kotlin, Apple Xcode, or selected community skill is a dependable BLE/KMP implementation skill. Android CLI can deploy and inspect an app on a connected Android device, and XcodeBuildMCP can run/log a signed iOS build, but neither models GATT/CoreBluetooth state, permissions, background behavior, or hardware protocol truth. Keep one small repository-local BLE skill/workflow with KMP interfaces in common code, Android and CoreBluetooth implementations at platform edges, serialized operations, redacted logs, and a required hardware-in-the-loop test matrix. Emulator and simulator success must never count as BLE proof.

### Toolchain And Prerequisites

_Required CLIs, SDKs, hardware, accounts, permissions, signing assets, CI runners, and non-interactive limits._

Android requires a JDK compatible with the project, Gradle wrapper, Android SDK/platform tools, ADB, an emulator or physical Android device, and BLE hardware for final validation. JetBrains CocoaPods-to-SwiftPM migration requires Kotlin 2.4.0-Beta2 or later, Xcode 16.4 or later, and careful Gradle plus Xcode verification. iOS execution requires macOS and full Xcode; XcodeBuildMCP additionally requires macOS 14.5 or later, Xcode 16 or later, and Node.js 18 or later when installed through npm. Signed device work additionally needs an Apple team, bundle ID/profiles/certificates, a trusted device, Developer Mode, and user-controlled physical-device actions. Android CLI does not replace Gradle; XcodeBuildMCP does not replace Xcode signing configuration.

### Installation Or Adoption

_Installation/use path and available adoption metrics such as installs, stars, forks, or release activity._

Use project-local, task-triggered installation rather than a global catalog. Android: `android skills add --skill=android-cli --project=.` and add one named specialist only when needed; avoid `--all`. Kotlin: use the Kotlin marketplace/plugin or `npx skills add Kotlin/kotlin-agent-skills`, then retain only the three named KMP skills needed by the project. iOS: choose exactly one execution route per environment: Apple built-ins within Xcode 27, or XcodeBuildMCP for external agents. As observed on 2026-07-26, android/skills had 6,358 GitHub stars, Kotlin/kotlin-agent-skills 967, XcodeBuildMCP 6,148, mmiani 56, and felipechaux 66; these are adoption signals, not quality or user-satisfaction evidence.

### Evidence Of Maintenance

_Evidence of official stewardship, recent updates, repository activity, or explicit uncertainty._

Google's Android skills repository is official, actively updated, and its Android documentation was updated in July 2026. JetBrains documents Kotlin AI skills, supports Codex and Claude Code, and the Kotlin agent-skill repository had activity in July 2026. XcodeBuildMCP is active, MIT licensed, has CI, and showed July 2026 repository activity. The community KMP catalogs were created in March 2026 and showed July metadata updates, but their comparatively small communities and limited independent feedback justify a trial-only posture.

### Public User Feedback

_Direct user reviews, issue/discussion feedback, or a clear statement that meaningful feedback was unavailable; never infer sentiment from installs alone._

| Candidate | Relevant public feedback | Decision impact |
| --- | --- | --- |
| Android CLI skill | A user reported stale `android screen` examples against Android CLI 1.0 in issue #74; another reported that its imperative `curl | bash` guidance can cause an agent to attempt a system-wide install without explicit consent in issue #61. | Verify command syntax with `android ... --help`; treat CLI installation as a user-approved prerequisite. |
| JetBrains Kotlin skills | No meaningful user review of the KMP migration/native-performance skills was found in the inspected official repository. Issue #17 requests a Coroutines skill, which indicates a catalog gap rather than product validation. | Use the official skills for their narrow documented triggers, not as a complete KMP engineering playbook. |
| XcodeBuildMCP | A detailed user report in issue #492 describes intermittent planning hangs and stale multiple MCP-server processes; the reporter recommends a single instance, timeouts, and a direct `xcodebuild | tee` fallback. | Enable one server per workspace/session, set time limits, and retain direct Xcode commands as a recovery path. |
| Community KMP/Compose kits | A Reddit user said the felipechaux KMP Compose skill felt solid, while the mmiani author describes existing KMP skills as often generic, thin, or overly opinionated. This is anecdotal and not an independent benchmark. | Do not adopt a broad community bundle on this evidence alone; selectively adapt small, reviewable rules. |

### Security And Release Safety

_Secret-handling and approval guardrails, especially for keystores, Apple credentials, Play/App Store releases, and real device access._

Never place Android keystores, key passwords, Apple private keys, provisioning profiles, App Store Connect keys, Play service-account JSON, or device identifiers in skills, prompts, logs, or repository history. Separate build/verification authority from release authority: agents may assemble and validate unsigned/debug artifacts, but archive/export/upload/store-track changes require explicit approval and least-privilege CI secrets. For XcodeBuildMCP and Appium-style device tools, start with simulator-only permissions; enable device workflows only after signing assets are already configured and never give the agent raw credential contents. Add a release checklist that verifies package/application ID, version/build number, signing identity fingerprint, privacy manifest/data-safety changes, store metadata, and a real-device BLE smoke result before upload.

### Limitations And Gaps

_Unsupported lifecycle work, stale guidance risk, platform gaps, or validation limitations._

The official Android pack is new and intentionally targeted; it does not provide a complete Gradle, Play publishing, BLE, or KMP release skill. Android CLI documentation/examples have shown syntax drift, and it must not be allowed to auto-install system software without approval. Kotlin's CocoaPods-to-SwiftPM workflow is a migration skill using experimental-era prerequisites, not a general iOS integration substitute. Apple Xcode skills are tied to a current Xcode environment. XcodeBuildMCP is powerful but adds an MCP server, telemetry considerations, failure modes, and potential hangs; it should remain optional. None of these packages validates real BLE radio behavior, vendor protocol framing, reconnection, or background behavior without hardware.

### Recommendation

_Adopt, adapt, or avoid recommendation with priority and rationale._

Priority 1: add a project-local Android execution entry point using the official Android CLI skill and keep Gradle/ADB commands in the repository guide. Priority 2: add only the official Android testing and performance skills when the task specifically triggers them. Priority 3: install JetBrains Kotlin Native Build Performance; keep AGP 9 and CocoaPods-to-SwiftPM skills dormant until a migration is actually scheduled. Priority 4: pick one iOS execution mechanism: Xcode 27's built-ins for Xcode-centric work, otherwise XcodeBuildMCP with simulator-only defaults and a direct xcodebuild fallback. Priority 5: adapt a tiny KMP platform-bridge and test workflow from mmiani into repository-local instructions, then add the dedicated BLE/release workflow separately. Do not install any large all-in-one Compose/KMP catalog, Appium real-device stack, or broad global plugin unless a concrete task requires it.

### Sources

_Primary sources and directly relevant feedback links._

- Google Android skills overview: https://developer.android.com/tools/agents/android-skills
- Google Android skills repository and installation options: https://github.com/android/skills
- Android CLI stale-command feedback: https://github.com/android/skills/issues/74
- Android CLI system-install consent feedback: https://github.com/android/skills/issues/61
- JetBrains Kotlin AI skills documentation: https://kotlinlang.org/docs/kotlin-ai-skills.html
- JetBrains Kotlin agent skills repository: https://github.com/Kotlin/kotlin-agent-skills
- JetBrains Kotlin/Native build-performance skill: https://github.com/Kotlin/kotlin-agent-skills/blob/main/skills/kotlin-tooling-native-build-performance/SKILL.md
- Apple Xcode 27 release notes: https://developer.apple.com/documentation/xcode-release-notes/xcode-27-release-notes
- XcodeBuildMCP repository and requirements: https://github.com/getsentry/XcodeBuildMCP
- XcodeBuildMCP hang feedback: https://github.com/getsentry/XcodeBuildMCP/issues/492
- Community KMP catalog: https://github.com/mmiani/kotlin-kmp-claude-agent-skills
- Community KMP feedback thread: https://www.reddit.com/r/androiddev/comments/1ueh3is/most_claude_skills_for_kotlin_multiplatform_were/
- Community Compose skill and feedback thread: https://github.com/felipechaux/kmp-compose-multiplatform-skill and https://www.reddit.com/r/androiddev/comments/1ueh3is/most_claude_skills_for_kotlin_multiplatform_were/

### Primary links

- https://developer.android.com/tools/agents/android-skills
- https://github.com/android/skills
- https://github.com/android/skills/issues/74
- https://github.com/android/skills/issues/61
- https://kotlinlang.org/docs/kotlin-ai-skills.html
- https://github.com/Kotlin/kotlin-agent-skills
- https://github.com/Kotlin/kotlin-agent-skills/blob/main/skills/kotlin-tooling-native-build-performance/SKILL.md
- https://developer.apple.com/documentation/xcode-release-notes/xcode-27-release-notes
- https://github.com/getsentry/XcodeBuildMCP
- https://github.com/getsentry/XcodeBuildMCP/issues/492
- https://github.com/mmiani/kotlin-kmp-claude-agent-skills
- https://www.reddit.com/r/androiddev/comments/1ueh3is/most_claude_skills_for_kotlin_multiplatform_were/
- https://github.com/felipechaux/kmp-compose-multiplatform-skill


## Minimal cross-agent memory, coding, and context-management stack

_Evidence source: `minimal_cross_agent_memory_coding_and_context_management_stack.json`_

### Classification

_What is being evaluated and whether it is official, community-maintained, or a proposed custom skill._

A deliberately minimal, cross-agent operating stack for Codex, Claude Code, and agents that understand AGENTS.md or Agent Skills. The baseline is four durable, versioned assets: one concise canonical AGENTS.md, a thin CLAUDE.md compatibility wrapper, short decision records kept outside startup context, and executable verification scripts plus CI. Add one explicit, repo-local hardware-in-the-loop/release skill only after the workflow repeats. Treat Graphify and a GitHub connector as optional, evidence-driven add-ons rather than defaults. Do not install a broad skill pack, a generic vector-memory server, or a suite of SaaS connectors just in case.

### Candidates

_Relevant skill, CLI, repository, or workflow candidates, including an exact name or source when available._

- 1. Canonical shared instructions: keep one root AGENTS.md as the checked-in source of truth for repository map, exact build/test commands, a few non-negotiable safety rules, and a definition of done. Keep it roughly 80-150 focused lines; it must never become a generated repository summary. Codex loads AGENTS.md automatically. Claude Code should use a 3-10 line root CLAUDE.md containing '@AGENTS.md' plus only Claude-specific notes, so the instructions are not duplicated.
- 2. Progressive project knowledge: store durable project facts in docs/decisions/ (short ADRs) and subsystem guides such as docs/ble-protocol.md, not in the always-loaded instruction file. AGENTS.md routes the agent to read a decision or protocol guide only when the task touches that area. For Claude Code, use path-scoped .claude/rules/ only for narrowly scoped source trees; use nested AGENTS.md at a matching directory boundary for Codex. This creates shared, reviewable memory without startup bloat.
- 3. Local personal memory, not canonical project memory: enable Claude Code auto memory and Codex local Memories only for personal preferences, recurring debugging clues, and transient continuity. Review and prune them periodically. Both are useful recall layers but are local/generated state, not a cross-machine source of truth; a committed ADR or an issue/PR remains authoritative for a team decision.
- 4. One repo-local Agent Skill, created only when justified: add a small mobile-hil-release skill under .agents/skills/ after the team has repeated the BLE hardware-validation or release procedure at least twice. Put the short checklist at the top and place long device matrices, signing guidance, and troubleshooting in referenced files. Make deploy, signing, publishing, and irreversible device actions user-invocable only; the agent must not trigger them implicitly.
- 5. Executable quality contract instead of prose: expose a small number of stable commands such as ./scripts/verify, ./scripts/test-android, ./scripts/test-ios, and ./scripts/hil-ble, then make CI run their safe subsets. Have agents run the narrowest applicable check, inspect the diff, and use an on-demand review pass for security, lifecycle, and test gaps. This replaces many always-loaded instructions with deterministic tooling.
- 6. Optional Graphify codebase map: adopt only for a multi-module, unfamiliar, or repeatedly explored repository. Begin with local code-only extraction, query/path/explain during read-heavy architecture work, and update the graph after meaningful code changes. It is cross-agent compatible and can reduce repeated repository exploration, but graph construction, update cost, generated files, query logs, and retrieval quality mean it is not a default for a small or rapidly changing repository.
- 7. Optional GitHub connector or existing GitHub plugin: enable it only if agents regularly need live PR, issue, Actions, or review context. Keep it disabled for ordinary local coding and especially for lightweight subagents. Do not add Slack, Notion, Drive, email, calendar, or broad database MCP servers until a concrete recurring workflow needs live access to that system.
- 8. Explicit non-adoption: do not install a large generic skills bundle, a multi-agent orchestration framework, or more than one release/review skill at baseline. Keep specialized planning, adversarial review, documentation, and research workflows as opt-in actions. Parallel agents should be used only for independent read-only exploration, test triage, or review; they cost more tokens than a single focused agent.

### Coverage

_Lifecycle stages covered: create, build, run, debug, test, sign, release, publish, maintain._

- Create: use a short task prompt with goal, relevant paths, constraints, and a definition of done. Use an ephemeral plan for ambiguous or multi-step work; do not turn every plan into permanent instructions.
- Build, run, debug, and test: AGENTS.md points to executable scripts and CI jobs. The shared instructions name only stable commands and environment constraints; detailed troubleshooting stays in linked docs or the on-demand skill.
- Review: require diff review plus the smallest relevant automated check before handoff. Invoke a focused code-review workflow only for a non-trivial change, sensitive BLE state-machine code, permissions, signing changes, or a release candidate.
- Sign, release, and publish: keep credentials and release state in the CI secret manager and protected release workflow. The only skill that can guide these stages is explicitly invoked by a human; it must stop for confirmation before signing, uploading an AAB/IPA, promoting a TestFlight build, or publishing a store release.
- Maintain: write a concise ADR when a decision will affect future tasks, update a linked protocol guide after verified hardware findings, periodically prune local memory, and refresh the optional graph after structural changes.

### Platform Support

_Android, iOS, KMP/common code, macOS/Xcode, CI, physical-device support, and important constraints._

The stack is platform-neutral and works with Android, iOS, Kotlin Multiplatform/common code, macOS/Xcode, CI, and physical devices because it routes each task to repo-owned commands rather than pretending that a generic agent skill replaces the platform toolchain. Codex natively discovers AGENTS.md and repo-local .agents/skills. Claude Code reads CLAUDE.md, officially supports importing AGENTS.md, path-scoped rules, auto memory, and Agent Skills. GitHub Copilot and Android Studio Gemini also support AGENTS.md, so the canonical file remains useful beyond Codex and Claude. The stack does not itself install Android SDKs, Xcode, simulators, signing tools, Play Console tooling, App Store Connect tooling, or hardware drivers; those belong in the KMP/mobile execution and release workflow.

### Bluetooth Relevance

_Direct Bluetooth/BLE capability, or the exact missing parts and custom-skill requirements._

There is no trustworthy generic agent memory or plugin that supplies Bluetooth correctness. The minimal stack supports Bluetooth work by making the BLE contract, permissions, device matrix, reconnect/background expectations, and hardware-in-the-loop commands easy to retrieve without loading them every session. The optional mobile-hil-release skill should cover expect/actual boundaries, Android and CoreBluetooth permissions, scan/connect/notification lifecycle, real-device log capture, firmware/device variants, and a stop rule for unsafe commands. It must never be treated as proof of BLE behavior: physical-device validation remains required.

### Toolchain And Prerequisites

_Required CLIs, SDKs, hardware, accounts, permissions, signing assets, CI runners, and non-interactive limits._

- Baseline: Git, a repository task runner or checked-in scripts, Android and iOS build/test toolchains, CI, and access to a real BLE test device. The exact commands belong in AGENTS.md and scripts, not in a globally installed skills pack.
- Codex: use repository AGENTS.md, repo-local .agents/skills, narrow sandbox/approval defaults, and local Memories only if the user enables them. Codex reports that skill metadata is available for discovery while full skill instructions load when selected.
- Claude Code: use CLAUDE.md importing AGENTS.md; inspect /memory and /context when startup context is unexpectedly large; use /compact with a focused summary or /clear between unrelated tasks. Auto memory is local to the machine and repository. Disable unneeded MCP servers before starting subagents because their tool definitions are inherited.
- Optional Graphify: Python 3.10 or later plus uv or pipx; the package name is graphifyy while the command is graphify. Prefer graphify extract --code-only for private code or a first trial. Review its generated workspace files and local query-log policy before adoption.
- Optional GitHub connector: authenticated least-privilege GitHub access. Use read/review capabilities by default; protect write, merge, release, and secret-management actions behind normal GitHub review and CI controls.
- Release safety prerequisite: Android keystores, Apple certificates/profiles, API keys, provisioning material, and store credentials must never be placed in AGENTS.md, CLAUDE.md, auto-memory files, Graphify input, skills, terminal transcripts, or repository history.

### Installation Or Adoption

_Installation/use path and available adoption metrics such as installs, stars, forks, or release activity._

Adopt in stages. Stage 0: install nothing beyond existing agent clients; add root AGENTS.md, thin CLAUDE.md importing it, docs/decisions/, and verification scripts. Stage 1: enable and audit each agent's local-memory feature, but keep project decisions in Git. Stage 2: after two repetitions of the same BLE/release procedure, create one repo-local explicit skill rather than importing a marketplace bundle. Stage 3: trial Graphify only if architecture exploration repeatedly dominates sessions; as of 2026-07-26 its public GitHub API reported 96,142 stars, 9,310 forks, Apache-2.0 licensing, and a push on the same date, but those popularity counts are not a quality guarantee. Stage 4: enable only the GitHub connector if live PR/Actions/issue work occurs often enough to justify its tool definitions. Claude Code documents standalone .claude configuration as the right first step for one project and recommends packaging as a plugin only when the workflow must be shared or distributed.

### Evidence Of Maintenance

_Evidence of official stewardship, recent updates, repository activity, or explicit uncertainty._

Codex current documentation recommends a short AGENTS.md, skills with progressive disclosure, and a progression from AGENTS.md plus enforcement tooling to skills, MCP, then subagents. Claude Code current documentation explicitly supports a CLAUDE.md import of AGENTS.md, documents automatic memory, path-scoped rules, skill invocation controls, context inspection, hooks, and plugin scopes. The Graphify repository is publicly active: GitHub API data on 2026-07-26 showed a same-day push and update. The independent evidence is mixed: two 2026 studies report different outcomes for AGENTS.md, which supports treating concise human-maintained instructions as a measured project control rather than cargo cult.

### Public User Feedback

_Direct user reviews, issue/discussion feedback, or a clear statement that meaningful feedback was unavailable; never infer sentiment from installs alone._

- Claude Code issue #27298: a user asked for layered memory because always-loaded MEMORY.md and an ever-growing CLAUDE.md waste context. This directly supports a small startup index with lazy topic files rather than a large persistent prompt.
- Claude Code issue #24147: a user reported that full instructions and MCP tool definitions were repeatedly included as cached context and associated the behavior with unexpectedly high usage. This is a user report, not an audited cost measurement, but it reinforces disabling unused MCP servers.
- Codex issue #3198: a user requested AGENTS.md reload on every turn; an OpenAI collaborator replied that this would be inefficient for longer files and recommended a new session after changes. This supports short, stable instructions and avoiding frequent edits mid-session.
- Codex issue #30788: a user requested a dedicated instruction-discovery debug command because current inspection is a large mixed prompt dump. This shows that too many layers and overrides create observability and maintenance cost even before model-token cost.
- Graphify feedback is mixed and anecdotal. Some Reddit users report major reductions in repeated codebase exploration, while another reports a single update costing 5-10 million tokens on a relatively small project. Treat all such claims as workload-dependent; the local PromoMesh benchmark is encouraging but should not be extrapolated blindly to a KMP repository.
- Research evidence is also mixed. A 2026 study across agents found context files increased inference cost by more than 20 percent and recommended minimal human-written requirements; another study of 10 repositories and 124 PRs associated AGENTS.md with lower median runtime and lower output-token consumption. The practical synthesis is not 'no instructions' but 'few, specific, human-maintained instructions plus executable checks.'

### Security And Release Safety

_Secret-handling and approval guardrails, especially for keystores, Apple credentials, Play/App Store releases, and real device access._

Treat instructions as guidance, never as enforcement. Keep sandbox and approval scopes tight; use CI, branch protection, secret stores, signed artifact provenance, and deterministic hooks for rules that must always happen. A release skill must have manual invocation only, must require an explicit target environment and human confirmation before signing/uploading/publishing, and must verify the produced artifact and store state afterward. Give MCP connectors the minimum scope, do not feed credentials into tool prompts or durable memories, and turn off unneeded connectors before spawning subagents. Graph indexing should start code-only for sensitive repositories; do not send proprietary documentation or device logs to a hosted extractor without an explicit data-residency decision. The agent workflow must preserve a separate, real-device BLE test gate before any production promotion.

### Limitations And Gaps

_Unsupported lifecycle work, stale guidance risk, platform gaps, or validation limitations._

- Local Codex Memories and Claude Code auto memory are not a shared cross-agent, cross-machine knowledge base. They are useful convenience layers but cannot replace committed decisions, code, CI evidence, or issue/PR history.
- AGENTS.md and CLAUDE.md are advisory context, not guaranteed enforcement. Vague, conflicting, or stale text can reduce adherence; use a hook, linter, test, CI check, permission boundary, or protected environment for an invariant.
- A CLAUDE.md import improves maintainability but does not reduce the imported AGENTS.md startup tokens. Imports are organization, not lazy loading. Long skill bodies also stay in context after invocation, so one large omnibus skill is the opposite of the proposed design.
- Graphify has an up-front extraction/update cost and generated artifacts. Its project claims and local benchmark are not a controlled prediction for this KMP repository. It also does not replace direct source reading or physical-device debugging.
- MCP gives live capability but its tool definitions consume startup context in Claude Code and are inherited by subagents. Adding several connectors creates privacy, approval, and context risk even if they are seldom called.
- The stack does not replace Android/iOS build tooling, a release manager, Apple or Google account authority, production credentials, or a second person reviewing a store publication.

### Recommendation

_Adopt, adapt, or avoid recommendation with priority and rationale._

- Adopt now: root AGENTS.md as the only canonical instructions file; root CLAUDE.md containing '@AGENTS.md'; docs/decisions/ for short ADRs; and a small executable verification surface. Keep default startup context lean and do not add a new marketplace plugin.
- Configure now: Codex and Claude local memories may be enabled, but explicitly classify them as personal/generated recall. Review them for secrets and prune them monthly. Make a project rule that decisions and validated BLE facts go into Git-managed ADR/protocol documents instead.
- Create later: exactly one repo-local mobile-hil-release skill after the procedure repeats. Use explicit human invocation for side-effectful stages and keep all long references outside the SKILL.md body. Do not install multiple generic coding/review/release skills.
- Use on demand: an agent-led review plus tests for meaningful changes; parallel subagents only for independent read-heavy work. Use Graphify when a module map materially shortens repeated exploration, beginning with code-only mode and a measured trial. If it costs more than it saves after several tasks, remove it.
- Enable only when justified: the existing GitHub integration for live PR/CI/issue workflows. Leave Slack, Notion, Drive, Gmail, calendar, broad database, and external-memory MCP connectors uninstalled until an explicit use case and data-access review exist.
- Do not adopt: a universal 'memory server' or broad agent framework solely to compensate for poor repository instructions. First measure repeated loss of a specific kind of information; if it persists despite ADRs, focused skill references, and local auto-memory, evaluate one narrowly scoped retrieval layer with privacy controls.

### Sources

_Primary sources and directly relevant feedback links._

- OpenAI Codex manual, AGENTS.md discovery and best-practice guidance: https://learn.chatgpt.com/docs/agent-configuration/agents-md and https://learn.chatgpt.com/guides/best-practices
- OpenAI Codex manual, customization, skills, MCP, and memories: https://learn.chatgpt.com/docs/customization/overview, https://learn.chatgpt.com/docs/build-skills, and https://learn.chatgpt.com/docs/customization/memories
- Claude Code, project memory, AGENTS.md compatibility, auto memory, imports, and path-scoped rules: https://code.claude.com/docs/en/memory
- Claude Code, skill activation and context behavior: https://code.claude.com/docs/en/skills and https://code.claude.com/docs/en/context-window
- Claude Code, context-pressure mitigation and MCP/subagent guidance: https://code.claude.com/docs/en/errors
- Claude Code, standalone configuration versus plugins and plugin trust warning: https://code.claude.com/docs/en/plugins and https://code.claude.com/docs/en/discover-plugins
- AGENTS.md open-format repository and adoption signal: https://github.com/agentsmd/agents.md
- Graphify repository, privacy, install, cross-agent integration, and caveats: https://github.com/Graphify-Labs/graphify and live repository metadata: https://api.github.com/repos/Graphify-Labs/graphify
- User feedback on Claude Code layered memory and context cost: https://github.com/anthropics/claude-code/issues/27298 and https://github.com/anthropics/claude-code/issues/24147
- User feedback on Codex instruction discovery and reload cost: https://github.com/openai/codex/issues/3198 and https://github.com/openai/codex/issues/30788
- Anecdotal Graphify feedback, including update-cost concern: https://www.reddit.com/r/ClaudeCode/comments/1uq9vcj/are_you_using_graphify/
- Research evidence with conflicting results: https://arxiv.org/abs/2602.11988 and https://arxiv.org/abs/2601.20404

### Primary links

- https://learn.chatgpt.com/docs/agent-configuration/agents-md
- https://learn.chatgpt.com/guides/best-practices
- https://learn.chatgpt.com/docs/customization/overview,
- https://learn.chatgpt.com/docs/build-skills,
- https://learn.chatgpt.com/docs/customization/memories
- https://code.claude.com/docs/en/memory
- https://code.claude.com/docs/en/skills
- https://code.claude.com/docs/en/context-window
- https://code.claude.com/docs/en/errors
- https://code.claude.com/docs/en/plugins
- https://code.claude.com/docs/en/discover-plugins
- https://github.com/agentsmd/agents.md
- https://github.com/Graphify-Labs/graphify
- https://api.github.com/repos/Graphify-Labs/graphify
- https://github.com/anthropics/claude-code/issues/27298
- https://github.com/anthropics/claude-code/issues/24147
- https://github.com/openai/codex/issues/3198
- https://github.com/openai/codex/issues/30788
- https://www.reddit.com/r/ClaudeCode/comments/1uq9vcj/are_you_using_graphify/
- https://arxiv.org/abs/2602.11988
- https://arxiv.org/abs/2601.20404

## Adoption rule

Add a skill, plugin, connector, or automation only after it has removed repeated work at least twice and its scope cannot be expressed more cheaply as a script, ADR, or linked reference. Remove stale migration-only skills after the migration.
