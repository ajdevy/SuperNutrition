# Native Android review of the camera and layout changes

## Device and build

Physical HONOR DNY-NX9, Android 16 / API 36, arm64-v8a, 1264 × 2736 pixels at 560 dpi. A single authenticated Wi-Fi ADB transport is used: `adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp`. Initial ADB discovery found the service but authentication failed; after the user reconnected Wi-Fi debugging, installation and activity launch succeeded.

Production APK: `mobile/apps/androidApp/build/outputs/apk/production/debug/androidApp-production-debug.apk`, SHA-256 `6e68b53baba01d48c27a482503926f1d9561ea31ae9c8ddb0464b5562bf6905c`.

## Findings before fixes

- Home: the mascot feet render in front of the panel. Care-button icon/text groups are centered. Scrolling reveals the complete next-step card above the fixed navigation; its initial clipping is scroll overflow, not an unreachable action.
- A foreground interruption briefly showed another app. The SuperNutrition process remained alive and no recent SuperNutrition crash was present. Bringing its task forward and using a fresh resource-ID hierarchy confirmed the Rewards transition works. The unrelated screenshot was replaced with the actual Rewards capture.
- Rewards: cards have equal outer gutters and consistent vertical gaps. **Finding N-01:** status-bar icons are white against the light background and have inadequate contrast. Acceptance: use dark status-bar icons on Rewards; preserve the intended camera/Home appearance and restore it when leaving Rewards. Recorded before production edits.

## Pending checks

## User-reported findings recorded before the follow-up fixes

- **N-02 — Rewards destination:** the user reports a previously implemented Rewards design missing and expects it on latest `main`. Acceptance: fetch/pull the actual upstream, search existing implementations/history, restore the existing screen when found, and report the source accurately. `git fetch origin main` and `git pull --ff-only origin main` both succeeded; upstream `flexsent-labs/SuperNutrition` is already at `5ee5de0`. No local edits were stashed or discarded.
- **N-03 — Camera permission entry/recovery:** opening camera must request missing permission in context, provide an actionable grant/settings button after denial, and refresh permission/preview after returning from Settings. Granting preview permission must not take a photo. The current capture-entry handler only changes visibility; the preview fallback is a non-actionable text; permission state is refreshed only on creation and permission callback.
- **N-04 — Mascot size:** on the actual phone the mascot is reduced by the content-height formula to approximately 80 dp. Acceptance: retain a substantial, centered mascot at normal phone size and keep its feet in front of the panel with the design overlap.
- **N-05 — Bottom navigation position:** the native pill floats visibly above the bottom gesture area. Acceptance: anchor the bar at the bottom with one safe-area inset and no unused container gap; preserve symmetric tab centers and camera clearance.
- **N-06 — Next-step layout:** the initial Home viewport cuts off the next-step card and the Do it action has unbalanced spacing. Acceptance: show the complete card/action on the tested ordinary phone without shrinking the mascot to a thumbnail; retain scrolling and complete content at small sizes/larger font scales.

These findings expand the earlier manual review: merely scrolling to the clipped Home card is insufficient for the ordinary-phone acceptance condition.

Verify final reward scrolling, native camera/shutter/retained-photo review, updated instruction, custom-plus selection, deselection, text entry/keyboard, Retake, and return navigation. Then fix N-01 and rerun the relevant build and device checks. This is manual physical-device evidence, not an instrumentation-suite result.

Screenshots and hierarchy dumps are stored under `mobile/apps/androidApp/build/reports/manual-device-review-2026-10-11/`. Worktree: `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`; branch `codex/figma-camera-flow`; HEAD/base `5ee5de077866feed70b418ee2760af24322b1d22`. Existing changes remain local and uncommitted.

## Upstream Rewards investigation

Read-only investigation covered all 41 remote heads, 616 reachable commits, 1,161 Kotlin/Swift blobs, existing checkouts, and 73 small unreachable blobs. The only Rewards implementation is `8f753a1` (merged by `b2dc9f2`, `e97d2cc`, and `263c926`), whose plan `docs/superpowers/plans/2026-09-09-rewards.md` expressly specifies a temporary simple LazyColumn. No richer Rewards UI/assets were found. Alternative ajdevy/iggdrassilon repository URLs are inaccessible. The user was asked asynchronously for a branch, commit, or Figma source while permission/Home fixes continue. Do not describe the current list as restored visual-design implementation.

## Follow-up red evidence

Before production changes, `:feature:photo-analysis-ui:jvmTest --tests '*NativePhoneHomeLayoutTest'` completed in 51 s with **3 tests, 3 assertion failures, 0 errors, 0 skipped** (XML timestamp 2026-10-11T12:24:34.871Z). The intended failures show an 80 dp mascot, the next-step card cut at the content viewport boundary, and a 32 dp visible-navigation gap above the bottom safe edge. The baseline physical camera screenshot `permission-baseline-camera.png` independently confirms denied access renders only text, with no contextual OS request or access button.

The first native controller red run failed Java fixture compilation because ViewModel was absent from the Java test classpath; no test executed in that run. The fixture was corrected without adding dependencies before rerunning.

Corrected native controller regression then executed on the HONOR device: **1 test, 1 assertion failure** — granting preview access pressed the shutter (`captures expected 0, actual 1`, Java test line 37). `connectedProductionDebugAndroidTest` ended with this intended failure in 5 min 21 s. This establishes the permission callback defect before production changes; the earlier compile failure is not counted as executed native evidence.

A final scoped search of unreachable non-Kotlin source located an existing designed Rewards HTML prototype and seven original badge illustrations. This supersedes the initial conclusion that only the Material list was available; recovery/source provenance is being checked before restoring the presentation.

Rewards grid red: the pre-fix `RewardsLayoutTest` ran **1 test, 1 failure, 0 errors/skipped** in 1 min 8 s; the current list puts the first two rewards at different vertical positions. This records the intended restored-grid layout condition before Rewards production edits.

**N-07 — Permission request context:** a null preview can also occur while restoring a review or while Capture is backgrounded. The preview-entry effect and coordinator must require a foreground Capture state, and Retake into Capture must re-evaluate access. Two native regression cases were added before the corresponding guard fix; the focused class run is pending.

Permission-context native red completed in 6 min 11 s: **15 tests, 2 failures, 0 errors/skipped**. The original 13 permission coordinator cases passed; the two added foreground-context cases failed with expected zero requests, actual one. The foreground Capture guard and keyed live-preview effect were then applied. Full green/native UI verification remains pending.

**N-08 — Native access-action accessibility:** the physical first-entry OS prompt and post-denial Allow camera button render correctly in the intermediate APK, but two fresh Android UI hierarchy dumps omit the button and its message. The full-screen semantic stage overlays the live-camera permission fallback, so Android accessibility treats the fallback as occluded. Acceptance: keep the access action, message, shutter, gallery and close discoverable together through native resource-ID semantics. Move the stage semantic container around both backdrop and foreground, preserving the existing stage ID/state. Recorded before that structural fix.

## Continued verification — 2026-10-12

The completed prior full JVM run has fresh XML timestamps 2026-10-11T16:48:48 through 16:49:35: core UI 4/0 failures, presenter 18/0, feature 23/0; consumer UI 59 tests with 5 failures, 0 errors/skips. All five relevant-food regressions passed. The remaining ordinary-phone Home failure shows next-step bounds (24,645)-(337,677) clipped by a viewport ending at677. The short enlarged-text mascot check measured clipped semantics (148px instead of the actual160dp); scroll the mascot fully into view before comparing its design overlap. Three Rewards test assumptions still describe a seven-item single-column list and must follow the restored two-column collection.

Home production follow-up will use compact spacing/metric padding on ordinary short content viewports while retaining width-based mascot sizing (at least160dp), fixed48dp Doit action, natural text measurement, and scrolling for enlarged text. This finding is recorded before that follow-up patch.

The changed execution sandbox made the default Gradle wrapper lock, Kotlin daemon marker, and Skiko data lock unwritable in the user profile. These are execution setup failures, not new product assertions. Gradle now uses a worktree-local writable home with the existing dependency cache read-only, Kotlin compiles in-process, file watching is disabled, and the temporary init script only moves Skiko runtime extraction/locks into ignored build output. The 58 shared-library initialization failures from that setup attempt must be replaced by a full fresh UI run; earlier green reports are not substituted.

## Review-boundary and host verification on 2026-10-12

The temporary Skiko init also sets `java.awt.headless=true`, allowing genuine
offscreen Compose rendering in this restricted host. A complete fresh UI run
passed 60 tests before adding the final-review regressions. After their fixes,
the full relevant run passed presenter 19 and feature 25 tests; consumer UI
ran 72 with exactly eight expected failures for the not-yet-integrated broad
icon catalog. Its three boundary tests passed. The subsequent translated-footer
red ran four boundaries with one clipping failure; FlowRow's equal measured
button heights then passed all four. This is JVM evidence, not Android evidence.

The 152-identity catalog red subsequently ran 10 tests with nine expected
failures before generated assets were integrated. All repeated static checks
for the native Android coordinator/preview/activity are clean; the remaining
shared test line wrapping is being corrected before the complete final gate.

Shared Apple compilation ran with explicit `enableIosDeviceTarget=true` on
this Intel host. Pinning the existing Kotlin/Native distribution to one home
removed a duplicated-platform-KLIB setup error. `core:ui`, the presenter and
consumer UI then compiled successfully, including their real dependencies.
The separate full native bridge/host compilation could not run: cinterop
reports `MissingXcodeException` because only CommandLineTools is installed.
No Swift/native-device execution or full Apple lint success is claimed.

A rebuilt production APK was installed through Android CLI using its local
writable cache (`ANDROID_USER_HOME=mobile/build/agent-android-home`). The
installed update time was 2026-10-12 10:42:03 on the physical phone. The
`home-oct12-review-fixes.png` capture shows a substantial mascot, foreground
feet, centered care actions, complete next-step card/Do it, and bottom-anchored
navigation. This APK precedes the expanded icon integration and final footer.

The user concurrently used other apps. A later hierarchy failed idle detection
and a purported Rewards screenshot instead captured another app. These are
not Rewards acceptance evidence. A short uninterrupted device window was
requested; actual final Rewards, permission recovery, capture/review and native
suite verification remain pending. Final evidence must use a fresh hierarchy,
verify Super Nutrition is foreground immediately before each tap, and capture
and inspect each result promptly. Do not infer native success from the Home
image or from stale coordinates.
