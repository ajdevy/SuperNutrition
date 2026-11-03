# Android emulator screen matrix — 2026-10-17

Scope: continue the existing camera, gallery, permission, Home and Rewards work. The user requested separate emulators at different screen sizes and cleanup of obsolete task-created emulators. Physical-phone testing has been replaced by emulator testing for this continuation.

## Current source checkpoint

Worktree: `REDACTED_WORKTREE/a704/SuperNutrition`; branch `codex/figma-camera-flow`; HEAD `5ee5de077866feed70b418ee2760af24322b1d22`. Preserve the existing dirty working tree and generated food assets. No commit, push or merge is authorized.

Current N-30 source removes the reserved blank Rewards canvas: content renders beneath floating navigation and its final scroll padding keeps the last reward clear. Production SHA-256 is `90c2c7fadb809e84a08b5e97b8179e240f65952ee1931258d864a345906ad400`. Full shared UI replay passes **114/0/0/0** across 20 fresh suites; six scoped lint reports are clean and explicit iOS Arm64 compilation executed. All six Android app/test artifacts are hashed. Domain **115/0/0/0** and Feature **25/0/0/0** evidence was not rerun in N-30; their complete source hashes are verified unchanged. Independent Blind/Edge static reviews found no introduced defect. The N-30 native replay and four-size Rewards manual matrix are complete; current Large/tablet photo/IME replay also passed. N-29 keyboard/retention and earlier results below are historical checkpoints, not current whole-build evidence.

## Acceptance matrix

Use official AOSP API35 x86_64 on this Intel Mac. Run one emulator and one Gradle build at a time. Verify actual `wm size`, density, API, ABI and AVD identity; save native UI bounds and inspect screenshots immediately before further interaction.

| Profile | Intended pixels / density | Logical viewport | Checks |
| --- | --- | --- | --- |
| Baseline phone | 720×1560 / 320 dpi | 360×780 dp | Full three-flavor Android suite; Home, Rewards, gallery, selection, manual input with real IME, retake/close, permission denial/recovery and emulated-camera capture |
| Small phone | 640×1136 / 320 dpi | 320×568 dp | Home and Rewards at normal/200% text, scroll reachability and navigation clearance; gallery/photo aspect ratio; manual input and actions with real IME |
| Large phone | 824×1830 / 320 dpi | 412×915 dp | Home and Rewards at normal/200% text; camera/gallery review and manual-input layout |
| Tablet | 1200×1920 / 320 dpi | 600×960 dp | Home and Rewards at normal/200% text; proportional photo preview, review controls and real IME |

Record reproducible product findings here before editing production code. Build or guest prerequisite failures are separate from executed product-test failures. Do not skip assertions or change quality-policy thresholds to obtain a pass.

## Cleanup and scheduling

Delete the stopped task-created API36 AVD after retaining its configuration and evidence. Delete temporary size-profile AVDs after saving their evidence. Retain the useful baseline API35 AVD and the unrelated `Pixel_2_API_31` AVD.

The `super-nutrition` hourly heartbeat is ACTIVE on this chat. It resumes this agreed scope from the latest verified checkpoint, remains quiet when there is no actionable change, and stops work after completion.

## Results

The canonical current-source run started at `2026-10-17T00:09:24.924613Z` and completed with exit 0 in 10m50s. Fresh XML verifies `poc`, `production` and `uiAcceptance` each at **40 tests / 0 failures / 0 errors / 0 skipped**, nine class suites each. All six APK hashes match the final source manifest. The temporary native-run screen timeout was restored and verified. Archive: `mobile/apps/androidApp/build/reports/rewards-emulator-api35-2026-10-16/native/verified-current-counts.json`, `verified-xml/` and `gradle-current-verified.log`.

`android emulator remove SuperNutrition_API_36_x86_64` completed successfully. Both its `.ini` and `.avd` are absent; previous allocated size was 3.1 GiB. Configuration, ANR diagnostics and prior verified test evidence remain in the reports. The unrelated Pixel 2 and useful baseline API35 AVD were preserved.

All three separate temporary size-profile AVDs were created against the already installed official API35 AOSP image. None was started concurrently with the baseline guest. Their configuration and intended dimensions are archived under `mobile/apps/androidApp/build/reports/emulator-screen-matrix-2026-10-17/`; runtime/manual checks remain pending.

## Camera first-inference diagnostic

The current-source POC permission flow verified nine phases: first request, denial/retry, rationale, second request/denial, permanently denied settings action, own-app settings, camera entry, granting the permission, and return to live preview without an implicit shutter. After the explicit emulated shutter, the 60-second harness deadline expired while native UI still reported `Finding visible foods`. No product completion is claimed for that attempt. Runtime permission flags, preference bytes and both package/UID camera AppOps were restored exactly in `finally`.

Investigate guest health, scoped logs and first model initialization before classifying the latency as a product defect or changing source. Preserve this failed attempt separately from any later replay. Permission navigation itself remains verified; emulated-camera result/retake/close and real-gallery review are pending.

Diagnostic replay completed without a source change: `camera-emulated-flow.json` verifies live preview, explicit shutter, **no-food recovery**, retake clearing photo candidates, and close returning Home. Capture-to-result time was **126.777 seconds** on this two-core Intel emulator. Guest diagnostics show no ANR and about 2.7 GiB available memory; model worker threads were active. This is recorded as an emulator performance limitation, not a passing phone latency result. The original 60-second attempt remains preserved in `permission-emulated-flow.json` and `permission-last-unexpected.xml`; the longer diagnostic is separate. No photo-food candidates were invented for the emulator's synthetic color pattern.

Baseline manual checks verified normal/200% Home, equal care-button frames, foreground mascot feet, full-word single-column metrics at 200%, next-step/Do it scroll clearance, Goals surviving font-triggered Activity recreation, and normal/200% Rewards with its final card entirely above navigation. Screenshots were inspected immediately. Gallery cancellation returned to live capture without taking a photo or entering review.

The real 3030×2670 banana fixture imported successfully, offering exactly one photo-food choice (`banana`). Selecting/unselecting it toggled the review selection and Add availability. Its photo retained its original aspect ratio. Import-to-review time was 220.804s in this emulator; a 300s diagnostic harness allowance was recorded, without claiming a phone performance pass.

Both plus controls worked with real Android LatinIME. The typed `apple` note remained focused and survived hiding/reopening the keyboard. `scripts/verify-photo-review-ime.py` passed against current native app bounds and the actual WindowManager IME inset: keyboard starts at y=938, input bottom y=747, both action buttons bottom y=876, Close visible. XML, screenshot and `real-ime-window.txt` are archived in the baseline report, with `review-real-ime-native-geometry.json`.

Retake while the keyboard was open cleared the note/photo-food review and returned live capture. Close returned to the originating selected Rewards tab. Normal font scale, native-run timeout, and POC permission/preferences/AppOps baselines were restored. No further source edit was needed for these checks.

## N-26 — focused note disappears when a small screen switches scroll policy

Reproduced on the separate 640×1136 / 320 dpi (320×568 dp) API35 emulator. The imported banana offers one relevant food and preserves its aspect ratio. After selecting the custom plus, scrolling the review body to reveal the manual-add plus and tapping it, LatinIME opens at y=547. The review switches from a pinned footer to scrolling the whole review. Native UI then contains only the header/custom tile and Close; the focused note and both footer actions are outside the visible viewport. `plus-ime-short-diagnostic.png`, `.xml` and `plus-ime-short-window.txt` preserve the red device observation. This is a product layout defect, separate from the earlier system-picker preview-icon mis-tap in the harness.

Acceptance: opening the note must keep the focused input visible after the IME-driven scroll-policy switch, without requiring the user to find it by scrolling. Keep both footer actions visible when the viewport can fit the footer; when it cannot, keep the focused input visible and all actions reachable by scrolling. Preserve typed text/focus, independent Close, the one-parent composition fix and normal-size geometry. Add a resize regression and observe it fail before editing production source. Replay the full UI module, scoped lint, explicit iOS Arm64 compile, APK assembly and current-source three-flavor Android suite after the fix; earlier APK/native results no longer qualify as the final source checkpoint.

The first two regressions reproduced hidden input before the fix (5/2/0/0). The initial focused correction passed 5/0/0/0. Independent review then exposed partial input-frame visibility in an oversized-footer viewport: a new normal-font 100 dp regression reproduced a 45 px frame clipped to 32 px (6/1/0/0). Relocation now targets the whole note frame when the footer cannot fit; the fitting-footer text assertion also rejects partial visibility. Focus/text remain on the same editable child, and user scrolling does not retrigger relocation. The focused final replay is **6/0/0/0**. All red attempts, the invalid initial ScrollTo setup, an incomplete rename compile failure and the first lint failure are retained separately under `mobile/feature/photo-analysis-ui/build/reports/review-focused-resize/`.

## N-26 verification checkpoint

The current full source replay completed with exit 0: **109/0/0/0 UI tests**, 19 current XML suites, six clean scoped lint reports (unchanged commonTest is UP-TO-DATE), explicit executed UI `compileKotlinIosArm64`, and six assembled APKs. Current production SHA-256: `972e11d2e616dfa095737252f7ada96ae48efd0ae1da5157e6ae88745b071bf9`. Exact commands, source/artifact digests and timestamps: `mobile/apps/androidApp/build/reports/emulator-screen-matrix-2026-10-17/build-verification.json`. No root quality or Swift-host execution pass is claimed.

The first small-guest native launcher was rejected by its foreground guard before Gradle/test execution because the booted AOSP SystemUI displayed an ANR. No app tests ran and no runtime-setting backup was mutated. Diagnostics/screenshot/XML and the failed launcher log are preserved in `small/boot-systemui-anr/`. The owned worktree's completed 4 GiB Gradle daemon was stopped to release memory. Only the observed SystemUI Wait action was selected on the owned emulator; no app/system failure was hidden as a test pass. The replay uses a 2 GiB Gradle heap and preserves all required tests/strict verification. Guest recovery must be verified before restarting native execution.

`graphify update .` completed: 20,656 nodes / 27,654 edges / 1,964 communities. This is code AST coverage; semantic document extraction was not run. The tool reported two SQL files omitted because `tree_sitter_sql` is unavailable and some JSON/asset files with zero AST nodes. No graph HTML was generated for the graph above 5,000 nodes. These graph-tool limitations do not qualify as app runtime verification.

After recovery, the current N-26 canonical native suite completed with exit 0 in 4m15s on the actual small 320×568 dp guest: POC, Production and UI Acceptance each **40/0/0/0**, nine class suites each. All six artifact digests match the current build manifest, all recorded UI source digests remain unchanged, and the installed production APK matches the current production SHA. Runtime timeout cleanup is verified. Archive: `small/native/verified-current-counts.json`, `verified-xml/`, `gradle-current-verified.log` and `launcher-current-verified.log`. No app/native failure was introduced or suppressed during this successful replay.

## N-27 investigation — review results lost on font-scale Activity recreation

On the current verified APK in the small guest, a real banana review with selected banana and typed `apple` passed the real IME geometry check; keyboard hide/reopen retained its text and focus. Changing system font scale to 2.0 then recreated the Activity. The app retained the original photo but entered recovery with `The photo is available, but analysis results could not be restored.` The review choices and note were no longer present. Native XML and inspected screenshot: `small/review-font200-after-recreate.xml`, `small/ime-font200-current.png`. Investigate snapshot validation/retention before classifying the cause or editing production. This native observation is distinct from the synthetic resize tests and does not establish a serializer defect by itself. Acceptance is to preserve the valid current review/explicit selections/note across configuration recreation without trusting invalid/stale restored evidence or changing consent/routing.

## N-28 — ignored background invalidation also loses retained review

N-27 Blind Hunter traced a preexisting unconditional invalidation for background ReplacePhoto/ScanAgain, outside its introduced diff. The deferred finding is recorded in `deferred-work.md`; this user-authorized broader-interaction follow-up is specified in `spec-review-ignored-invalidation.md`. Expanded tests reproduced ignored replacement losing the live draft (domain 115/1/0/0; focused feature class 9/1/0/0), before correction. The later Retake iteration did not execute in that failing loop; its same invalidation branch was static evidence until green replay. Red/source snapshots are preserved in `mobile/domain/photo-analysis/build/reports/review-ignored-invalidation/`.

Cache invalidation and route invalidation now share the actual reduction's state-change result. Ignored background events leave the live review/note/choices intact; accepted foreground invalidation still clears them. Capture-only opaque process restoration stays guarded by the single shared predicate added for N-27. No new transition, serialized payload, routing or consent behavior is introduced. Focused green executes snapshot/replacement/Retake iterations and passes domain 115/0/0/0 and feature class 9/0/0/0. Independent Blind and Edge reports identify zero introduced defects; their execution remains static and is not native evidence.

## Final source build checkpoint after N-26/N-27/N-28

Production SHA-256: `4610022502bacce3fc4812c915e2448dbc2a6221d136a545d2baa4f222208627`. Full replay completed with exit 0: domain **115/0/0/0** (11 suites, legitimately UP-TO-DATE from the current full-domain focused run), feature **25/0/0/0** (6 fresh suites), UI **109/0/0/0** (19 fresh suites). Fourteen scoped lint reports are clean. Feature and shared UI iOS Arm64 compile tasks executed explicitly on this Intel host. Domain coverage is 98.025751% lines / 85.995086% branches, meeting the 85% floor; all nine changed executable lines and their branch outcomes have zero misses in fresh Kover XML. Six APK and source hashes, exact commands and cache/freshness details are recorded in `build-verification.json` under the matrix reports and the archived `review-ignored-invalidation/verified-final-green/`.

Earlier N-26 native/IME checks are historical until replayed against this new digest. Final current-source native, real font-scale review retention and remaining screen-profile checks are pending. No full root quality, physical-device or Swift/Xcode host execution pass is claimed. `git diff --check` is clean. The final code graph update is 20,681 nodes / 27,677 edges / 1,989 communities; previously reported AST/SQL/semantic-document limitations remain.

## Final source native replay after N-26/N-27/N-28

The canonical full Android task completed with exit 0 in 4m18s on the isolated API35 x86_64 small guest (320×568 dp). Fresh XML confirms POC, Production and UI Acceptance each at **40/0/0/0**, nine class suites each. The verifier confirms all six APK SHA-256 values still match the final source manifest. All 102 recorded Kotlin source hashes remain unchanged after execution. Native-run timeout restoration is verified. Archive: `small/native-final-retention/verified-current-counts.json`, `verified-xml/`, `gradle-current-verified.log`, `launcher-current-verified.log` and `source-still-current.json`. Real review retention at 200% font and the remaining large/tablet manual checks are still pending.

## N-29 — Close overlaps the focused note at 200% font

Real final-source small-emulator execution preserves `apple` and the review through font-scale Activity recreation (N-27). However, the compact whole-form relocation positions the note frame around y=177 beneath Close at y=142–238. The inspected screenshot `small/final-ime-font200.png` visibly shows Close covering the pencil/frame; field text remains readable. Record this product defect before correction. Acceptance and scoped code map: `spec-review-close-clearance.md`. The required correction reserves Close clearance in the compact scroll viewport and compares relocation against that reduced height, while keeping normal full-screen geometry and short-viewport reachability. Prior 120-test native evidence belongs to the pre-N-29 digest.

## N-29 final source build and review checkpoint

After the native overlap red, the focused suite passed 7/0/0/0. Independent review exposed two further introduced boundaries and both were reproduced before correction: tiny-height/touch-area **8/2/0/0**, fractional-density **9/1/0/0**. Clearance is now bounded to preserve a minimum touch viewport and computed in integer parent pixels. Both final independent static reviews report no open introduced production defect; optional suggestions are not treated as defects or completed device work. The superseded line-wrapping build was stopped with exit 130 and remains archived as incomplete.

The final relevant UI full replay passes **112/0/0/0**, 19 fresh suites, six clean UI lint reports, explicit executed UI iOS Arm64 compilation and all six APK assemblies. Domain 115/0/0/0, feature 25/0/0/0, eight additional scoped lint reports and domain coverage remain unchanged and source-hash verified from N-28; those tasks were not reexecuted in N-29. Production SHA-256: `c2f996b48a755b359381197f3c48aead68706572cf195404478870439db972b0`. Current manifest: matrix `build-verification.json`; archived full logs/XML/manifests: UI `review-close-clearance/verified-final-green/`. Final native and profile replay against this digest are pending.

The first N-29 native launcher was started before full guest readiness. SystemUI showed a startup ANR; the foreground guard stopped this exact run with exit 130 before any `Starting 40 tests` marker. No app tests ran. Its logs, run/cleanup metadata, native XML/window dump and inspected screenshot are archived in `small/n29-boot-prerequisite/`. The owned Gradle daemon was stopped; only the observed SystemUI Wait action was selected. Fresh preflight then verified boot completed, API35/x86_64, 640×1136/320 dpi, font 1.0 and a healthy Launcher. The scratch launcher now explicitly rejects an unbooted guest and every crash/ANR title before mutation. Only after this verified recovery was the canonical replay restarted.

## N-29 final native replay

After verified guest recovery, the canonical task completed with exit 0 in 4m13s. POC, Production and UI Acceptance each pass **40/0/0/0**, nine fresh class suites each. All six APK digests match the final `c2f996…` source manifest, all 102 recorded source hashes remain unchanged, and native-run timeout restoration is verified. Archive: `small/native-final-close/verified-current-counts.json`, `verified-xml/`, verified Gradle/launcher logs and `source-still-current.json`. Manual font/IME and remaining profile checks still need final replay.

## Final small-profile manual results

Actual 320×568 dp/API35/x86_64, current `c2f996…` APK. Gallery displays the owned banana without stretch and one relevant selectable banana. Custom plus/manual plus open real LatinIME. Normal-font native geometry verifier passes input/Close/Retake/Add above IME. At 200% font, Activity recreation and keyboard hide/reopen retain selected banana, custom choice and `apple`; the inspected complete drawn note frame remains below Close. The expanded Android editable accessibility touch envelope is slightly larger than the drawn frame; no claim of an unexecuted strict full-footer verifier at 200% is made. With the oversized footer, three ordinary scroll gestures reveal both full 182 px actions above the IME; Close remains independent. Retake clears the review and Close returns to Home. A harness assumption initially tried importing from an existing review because Retake was offscreen; it stopped without a tap, was corrected to reveal the existing control, and replay passed.

Home and Rewards top/bottom screenshots were inspected at 100% and 200% text. Native bounds verify complete Next Step cards and final reward card inside the scroll viewport: Home navigation clearance 22/38 px; Rewards 38/38 px. Card margins remain symmetric and the Rewards page continues its background behind the bar. Evidence: small `n29-*.png/.xml/.check.json`. Font and screen timeout baselines restored. The stopped temporary Small AVD was deleted through the official CLI after retaining evidence/configuration. The baseline and unrelated Pixel_2 guests are preserved. Large/tablet checks remain pending.

## N-30 Rewards floating navigation — user clarification

The user clarified that matching the light background does not solve the obstruction behind the bar. Observed cause: the shared shell reserves the entire 83 dp navigation canvas, so Rewards cannot render there. A focused red run fails both assertions for the reduced safe viewport and a reward replaced by the blank canvas. The scoped spec is `spec-rewards-navigation-overlay.md`; three-file production baseline and red XML/log are preserved in `mobile/feature/photo-analysis-ui/build/reports/rewards-navigation-overlay/`. The N-29 matrix/native evidence above remains historical for this newly requested layout fix. Implementation and fresh verification are pending.

N-30 focused corrected red/green and full relevant build are complete. UI **114/0/0/0**, 20 fresh suites; six clean lint tasks and explicit shared UI Apple compilation, six hashed APKs. Actual Large guest preflight verified API35/x86_64, 824×1830/320 dpi and a healthy Launcher before starting the canonical native task. No guest was started during the preceding full Gradle build. Native and manual checks are running.

## N-30 verified canonical Android replay

Canonical connectedAndroidTest completed with exit 0 in 4m16s on the Large API35/x86_64 guest. Each flavor (POC, Production, UI Acceptance) has **40/0/0/0**, nine fresh class suites and 40 exact test cases. All six APK digests match the current build manifest and all 103 related source hashes remain unchanged. Native timeout baseline restoration is verified. Evidence: `large/native-rewards-overlay/verified-current-counts.json`, `verified-xml/`, `source-still-current.json` and the verified Gradle/launcher logs. The owned idle Gradle daemon was stopped before manual checks.

Large current-build Rewards at 100% and 200% was inspected: the list extends to y=1734 beneath the controls instead of stopping at y=1568. Passing cards are visible around the floating pill. At the final scroll position the complete last card retains 38 px clearance above the camera control at both font scales. Read-only native bounds assertions and screenshots are archived as `large/n30-rewards-*`. Home at normal font was inspected on the current build; all content, capybara feet, centered care buttons and Do it are visible. Gallery/IME and the remaining size profiles are in progress.

## N-30 completed Large manual checks and cleanup

The current installed Production digest was read back from the guest and matches `90c2c7…`. Current gallery fixture review preserves the banana aspect ratio and exposes exactly one banana food choice. The custom/manual plus, typed `apple`, font-scale Activity recreation, IME hide/reopen, Retake and Close were exercised. Normal and reopened 200% font strict native IME geometry verifiers pass with real keyboard top y=1143; selected banana, custom choice and note survive recreation/reopening. Font-scale recreation closes the keyboard; retapping the retained field reopens it. The first read-only verifier invocation used an incorrect argument name, was rejected before checking data, and was rerun correctly against the saved normal-font hierarchy. No failing product assertion is concealed. The gallery harness took 138.645 seconds on this guest; this is emulator first-inference/harness timing, not physical-phone performance evidence.

Home at 200% was inspected at top and bottom; the full Next Step card/Do it retain 38 px clearance. Native settings were restored, the guest stopped and temporary Large AVD removed using the official CLI; both its directory and ini are verified absent while all evidence remains. Small was recreated only for current N-30 compact Rewards verification, with a fresh settings backup/report namespace to avoid mixing its historical N-29 evidence.

## N-30 compact Rewards replay

The recreated 320×568 dp guest reached a verified healthy Launcher and current Production install digest. Fresh `small/n30/` settings/evidence avoids reusing N-29 device baselines. Rewards at 100% and 200% was inspected at top and end: list viewport [48,136,592,1040] extends beneath the controls; final card retains 38 px clearance at both scales, with all last-card text visible. Font/timeout baselines were restored before stopping/removing the temporary Small AVD. Historical small N-29 gallery/IME checks above concern unchanged review/host sources and remain explicitly historical rather than being relabeled as N-30 executions.

## N-30 tablet main-screen results

Actual API35/x86_64/1200×1920/320 dpi (600×960 dp), current APK digest read back by the installer. The first boot briefly showed Android FallbackHome; the read-only preflight stopped before any app action, then verified a healthy Launcher. Home at 100% and 200% and Rewards top/end at both scales were inspected. Native Rewards viewport [218,48,982,1808] extends beneath controls; complete last-card clearance is 38 px at both scales. Home 200% full Next Step/Do it also clears navigation by 38 px. The tablet platform taskbar is outside the application safe viewport and is preserved. Evidence: `tablet/n30-*.png/.xml/.check.json`, runtime/install/boot-focus records. First actual camera permission request was observed and granted through the exact While Using control; the owned gallery fixture/IME checks are in progress.

## N-30 tablet photo/IME completion

The owned banana fixture keeps its aspect ratio and exposes one relevant banana choice. Manual plus and `apple` were exercised with actual LatinIME; normal and reopened 200% strict IME checks pass (keyboard top y=1130). The complete field clears Close; Retake/Add are wholly above the keyboard. Font-scale Activity recreation retains selected banana, custom choice and note. Retake clears the review and Close returns Home. Camera permission entry/request/grant used observed exact system controls. Settings were restored before stopping/removing the temporary tablet guest; final configuration and evidence remain. No new introduced app finding was observed in these completed manual cases.

## N-30 retained baseline and complete viewport replay

Baseline API35/x86_64/720×1560/320 dpi (360×780 dp) is updated to the current Production APK. A startup SystemUI ANR was inspected and archived before installation; only the observed SystemUI Wait control was selected. Healthy Launcher and complete preflight were verified afterward. This was a guest prerequisite failure, not a product test failure. Current Rewards top/end at 100% and 200% were inspected; viewport reaches y=1464 beneath controls and the complete last card retains 38 px clearance at both scales. Settings were restored and the baseline guest stopped. Its AVD and current app are retained for future use; the unrelated Pixel_2 AVD is preserved.

Current Rewards navigation was manually verified at **320×568, 360×780, 412×915 and 600×960 dp**, each at 100% and 200% text. The full current native suite ran once on the Large guest, not four times. Gallery and real-IME current replay ran on Large/tablet; the unchanged review's compact real-IME execution remains explicitly documented at N-29 above. Existing landscape/tiny/fractional-density cases are in the full current UI JVM suite.

## Commands and limitations

- Full relevant validation: `python3 mobile/build/rewards-navigation-overlay-build.py full-green`; its archived JSON contains every exact Gradle argument. UI JVM: **114/0/0/0**, 20 fresh suites. Six scoped UI lint tasks are clean; unchanged CommonTest lint is legitimately UP-TO-DATE.
- Android: `./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest --no-configuration-cache --console=plain`, with strict dependency verification, `--continue`, isolated server 5041, explicit device serial and recorded build/installation flags. Current POC/Production/UI Acceptance each **40/0/0/0**, nine fresh class suites. See `large/native-rewards-overlay/verified-current-counts.json` for exact commands/artifacts/XML.
- Apple: current shared UI `:feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true` executed on Intel. No physical Apple, simulator or Swift/Xcode host execution is claimed.
- Domain **115/0/0/0** and Feature **25/0/0/0** are retained prior-module results with unchanged source hashes, not new N-30 executions. Their eight scoped lint reports are likewise retained; the six current UI reports are separate.
- The full root quality gate remains blocked by the previously recorded expired `bootstrap-android-host` evidence. This patch does not refresh physical-host evidence or weaken policy. Scoped validation above is the evidence claimed.
- Emulator ML/harness timing is not real-phone performance evidence. The built-in recognizer remains limited to its verified five-class allowlist; 152 food sticker identities do not imply 152 recognized classes.
- Branch `codex/figma-camera-flow`, HEAD `5ee5de077866feed70b418ee2760af24322b1d22`, worktree `REDACTED_WORKTREE/a704/SuperNutrition`, existing tracked/untracked WIP preserved. No stage, commit, push or merge.

## Completion and repository state

Agreed emulator continuation and N-30 Rewards fix are complete. Final code graph update succeeded after all guests stopped: 20,746 nodes / 27,760 edges / 1,986 communities; code AST only, with the same missing SQL parser and empty JSON/asset-node limitations. `git diff --check` is clean. Working tree has 301 dirty entries (44 tracked modifications, 257 untracked); none is staged. Existing work and assets remain intact. No commit, push or merge. Final artifact/source, module test counts, four-size/font verification and AVD/settings cleanup are recorded in `mobile/apps/androidApp/build/reports/emulator-screen-matrix-2026-10-17/completion-verification.json`. Retained AVD inventory is exactly `SuperNutrition_API_35_x86_64` (updated app) and unrelated `Pixel_2_API_31`, both stopped. The hourly heartbeat may remain quiet now that this agreed work is complete; it must not start unrelated implementation.
