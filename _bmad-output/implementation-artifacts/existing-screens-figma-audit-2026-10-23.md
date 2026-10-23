# Existing screens — current Figma audit, 2026-10-23

Baseline `7a141fac`, branch `codex/home-profile`, clean worktree. Figma session `36a014c1-6694-49f8-a338-7287e2c4b38f`; current MVP page `604:293`. Compared current source and historical rendered output to fresh high-fidelity context/screenshots before editing. Historical screenshots are a diagnostic baseline, not fresh passing evidence.

| Reachable screen | Current Figma | Observation before fixes |
|---|---|---|
| Home | 604:294 | Old rhythm badge instead of app logo; action chips in wide bubble; large static mascot and two-layer older garden; old water/movement metrics and small typography; obsolete next-step panel. Current design uses compact advice bubble, animated mascot, Fiber/Colors/Protein/Fats, 16dp gutter and new spacing. |
| Rewards | 604:860 | Old Earned/In progress grid, ten duplicate decorative cards, level/points header and gold medal art. Current design uses actual seven IDs in Daily/Weekly horizontal cards, new food art, summary counts and no level/points. |
| Shared bottom navigation | Home/Rewards frame instances | Old notched pill/protruding camera and olive accent. Current pill is 343x64 at x16; circular green 48 camera inside, 22 icons and 10pt labels. |
| Camera capture | 604:819 | Existing gallery/shutter/close closely match; right pencil is newly designed but Add-by-text is not an existing production route. Preserve dynamic camera, native permissions and minimum targets. |
| Camera review | 572:17720 | Old sans heading, olive selected controls, older instruction/placeholder/save copy; centered sparse labeled grid rather than current sticker palette. Current design has Fraunces heading, leaf-green controls and refreshed spacing. Candidate evidence remains dynamic. |
| Custom meal / typing | 604:1445 / 604:1485 | Existing custom choice and note field; no meal-name/save-library/portion persistence contract. Align existing controls and keyboard affordances; record missing new flow separately. |
| Profile/name | 604:921 / 604:1056 | Main Profile just implemented. Existing name editor is a Material dialog, whereas current edit-name frame edits in place with native keyboard. |
| Meal history | 604:893 | Just implemented. Preserve searchable actual confirmed-food projection, honest empty states and accessible muted text. |
| Loading/recovery/deep-link diagnostic | No matching current frame | Preserve existing functional surfaces; do not invent a design match. |

Android production reaches Home, Rewards, Profile, history, capture and review. iOS production currently launches shared capture/review only; its acceptance Home is a separate placeholder and is not production parity evidence. Standalone meal-search/research diagnostic composables are test-only. Meal/reward details and Add-by-text are designs without existing product routes at this baseline.

## Acceptance conditions from audit

- A1: Home and Rewards render current composition with exact asset slots, readable type, honest model values and working navigation.
- A2: Reward groups have four/three distinct IDs with computed progress; no decorative duplicates or invented earned state.
- A3: Updated shared navigation reserves content clearance and responds to larger text/RTL.
- A4: Review uses current visible copy/style while preserving real candidates, corrections, note, retake and save callbacks.
- A5: Existing name editing follows current in-place pattern with session-only behavior; Profile/history regressions pass.
- A6: Normal and large-text verification runs on available emulator; no claim of physical camera/inference or production iOS Home validation.

## Review findings

Independent post-change review and verification will be appended here before any resulting fixes.

## Regression evidence before implementation

- `ExistingScreensRefreshTest`: 2 tests, 2 failures, 0 errors/skips. Existing Home still contained Water; existing Rewards still contained decorative Fiber Friend. Saved XML: `existing-screens-verification-2026-10-23/red-ui-refresh.xml`.
- `ReviewProfileRefreshTest`: 2 tests, 2 failures, 0 errors/skips. Review heading was 18.22sp rather than current 20sp; Profile name editing still opened a dialog. Saved XML: `existing-screens-verification-2026-10-23/red-review-profile.xml`.
- First full UI attempt compiled production sources, then stopped at two stale direct test calls to HomeCarePrompt. No tests executed in that run; it is not passing evidence.

## Source and platform constraints retained

- Home Fiber/Protein were already static host demonstration values. New Colors/Fats use explicit unavailable values, never relabeled Water/Movement quantities or invented nutrient grams. Progress fills require a supplied fraction.
- Review retains its existing Close control for exit/accessibility even though the current Figma review frame omits that control. Keep it clear of heading and maintain 48dp target.
- Profile's in-place name editor retains session-only state; no new persistence or saved meal-library contract is introduced.

## First complete module run — findings before fixes

Fresh run: core UI 4/4, presenter 19/19, feature 25/25; UI 213 tests with 17 failures, 0 errors/skips. The four original red regressions now pass. Both current design geometry renders execute. Remaining conditions:

- V1: Rewards' long text can remain outside the viewport after horizontal/vertical reveal, including 200% descriptions/titles and the very short landscape root. Fix scroll reachability and test the actual visible text; do not accept clipped glyphs or shrink fonts.
- V2: Review manual-add is now the fixed note row. Three tests still call `performScrollTo` on a control with no scrolling ancestor. Preserve the working focus action and adapt only obsolete scroll assumptions.
- V3: Sticker drag test no longer has enough content overflow after removing visible labels; strengthen the fixture to require an actual scroll while preserving no-selection-on-drag behavior.
- V4: Native-shape navigation replaces the old 347dp exported-image bounds with a 343dp pill; one common test still asserts old dimensions.
- V5: Old Rewards paper color and old Home backdrop exact-pixel assertions are stale; new asset/image sampling requires the current expected color and a bounded interpolation tolerance.
- V6: Two wrapped-metric fixtures no longer force a wrap at current text sizes/layout; force meaningful wrapping without changing production fonts.
- V7: Home overlay/standalone mascot sizing differs due to new compact breakpoint; verify actual meaningful visible art and card geometry rather than the superseded equality.
- V8: Home target suffix uses a mixed-font annotated string; text layout offsets must come from the actual resolved layout text rather than assuming a single merged result.
- V9: Fresh Rewards render shows normal-looking titles despite requested Nunito Bold. Verify weighted resource font resolution against the known Meal history solution before accepting visual parity.
- V10: Test triage confirms a real old-color seam: `CurrentRewardsScreen` paints #F6F7F5 but the shared shell still paints old `RewardsPaper` #F7F3EF in the safe area. Unify them on the current screen token rather than accepting two different expected colors.
- V11: The new glass layer reads `boundsInRoot`, whose bounding box is clipped by the scroll container. Its source translation must use the actual unclipped `positionInRoot` so the backdrop stays aligned while the card moves partially off screen.
- V12: The Android Profile host applies `safeDrawing.asPaddingValues()` without consuming insets, while the refreshed inline editor applies `imePadding()`. Consume safe drawing insets at the host boundary to avoid applying the keyboard height twice. Verify the name editor and Save remain visible with the native keyboard open. [Android inset consumption guidance](https://developer.android.com/develop/ui/compose/system/insets-ui).

## Presentation constraints retained after implementation

- Current Review uses leaf `#4A7D3B` behind small white text (4.90:1) in place of Figma `#518841` (4.24:1). White placeholder/photo readability retains the existing65% photo scrim instead of Figma45%; current tests cover a worst-case white photo.
- The native bottom-bar camera circle uses nearby green `#6DA158` behind the unchanged white Figma camera artwork: 3.0483:1 instead of Figma `#7AB462` at 2.4612:1. This bounded color exception meets [WCAG 2.2 SC 1.4.11's 3:1 non-text contrast threshold](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html). The regression measures the actual rendered glyph and circle pixels; icon artwork, 48dp target, and geometry remain unchanged.
- Review retains the existing functional Close action, with header clearance, in addition to native Back and Retake. The current design frame omits it.
- The Home glass panel now renders the actual garden crop with a15dp native background-only blur under55% white. The source rectangle is shared with the root garden and follows measured root coordinates on resize and scroll. Compose uses its unblurred fallback on Android versions below31; foreground text and metrics are unaffected.
- Known photo stickers now have icon-only visible presentation with full evidence descriptions for accessibility. Unknown foods retain visible text; actual candidate count/order remains authoritative. The note field is the manual-entry focus affordance, replacing the separate plus tile.

V9 implementation: Registered Normal/SemiBold/Bold resource entries in the existing Nunito family, without manual variation axes; fresh rendered verification is required.

## Second complete module run — remaining conditions before fixes

Core UI 4/4, presenter 19/19 and feature25/25 pass. Production APK assembles. UI213 tests now has6 failures (no errors/skips): Rewards short-window final progress, short-wide swipe visibility, and four 200% long-copy title/description reachability checks. Text layout reports no internal overflow; nested horizontal/vertical reveal still leaves the measured glyph frames clipped or outside the viewport. Preserve full text assertions while correcting scroll alignment. Native normal Home was inspected and fits without scroll; fresh Rewards has matching artwork/weighted Nunito but still shows white status icons on its pale background — verify and correct Android appearance after the UI issues.

V13: Native Rewards shows white status icons on #F6F7F5. The tab state is read only inside SideEffect and a nested SaveableStateProvider composition; changing the tab can recompose the nested content without rerunning the outer effect. Read the requested icon appearance during composition, then apply the captured Boolean. Add a native assertion for Rewards dark icons and return-to-Home light icons. The fresh normal Rewards screenshot is the observed red evidence.

## Rewards scroll verification — targeted follow-up

The six remaining failures were complete text layouts whose visible bounds were clipped after the generic test driver's nested `performScrollTo`. The reveal helper now uses the actual horizontal reveal, the target's unclipped layout coordinates, and the real vertical viewport's `ScrollBy` action with navigation clearance. It retains all glyph/line/frame completeness assertions; the landscape check also performs an actual touch swipe and verifies that scroll position advances. No production sizing or font reduction was needed for this fix.

Fresh targeted JVM execution: `ReviewBoundaryTest`5/5, `RewardsNavigationOverlayTest`2/2, `RewardsTextReadabilityTest`8/8; all0failures,0errors,0skips. Build49s; fresh XML timestamps2026-10-23T00:10:23Z–00:10:42Z. The full module must still be rerun after this targeted check.

## Formatting verification before corrections

`qualityFormat --continue` completed with55 UI findings: long lines, three single-declaration filename mismatches after layout extraction, and an unused `noteFocus` parameter. Other product formatting tasks passed or had unchanged inputs. Correct the code and declaration placement without suppressions; preserve behavior and existing module paths. This run is a failed formatter check, not passing lint evidence.

V14 — native camera ANR (observed before fixing): After granting one-time camera permission, API35 shows “Super Nutrition isn't responding”. The system ANR trace pins the main thread in `providerFuture.get()` at `PhotoAnalysisLiveCameraPreview.kt:195`, called synchronously by the newly attached lifecycle observer's ON_START. This is an existing host defect exposed by required native verification, not emulator noise. Keep the main thread responsive while CameraX initializes; let the completion listener bind only if the screen is still started and undisposed. Reproduce the pending-future condition in a deterministic check before fixing and re-run native camera/permission flow. Raw trace retained at `/tmp/supernutrition-design-audit/native-camera-dropbox.txt`.

The first provider regression build stopped at Java test compilation: AndroidX core is an implementation dependency of the host, not part of the app instrumentation compile API. No native tests executed. Replaced the new test's compat getter with the Android platform system-bar appearance getter (with the existing API26–29 flag equivalent), adding no dependency. JVM UI verification continued separately under `--continue`.

V14 red regression:4 native production tests executed;3 pass and the pending-future case fails with “attempted blocking read while provider initialization is pending”. Saved XML `red-camera-provider.xml`. Corrected the readiness boundary to return immediately while initialization is incomplete; the existing main-executor completion listener remains responsible for binding.

The combined JVM quality/Apple-target invocation stopped during configuration-cache serialization (4 setup actions, no source analysis): enabling Apple targets caused SharedKit JVM classpath resolution to encounter pre-existing missing coroutines lock entries. Separate the normal JVM/Android gate from the explicit Apple compile and disable the configuration cache for reliable diagnostics. Do not treat this as either a source pass or an Apple compile result; no lockfile is changed by this presentation work.

## Independent review corrections in progress

The two fresh reviewers completed source review; ten deduplicated patches and exact acceptance conditions are recorded in `existing-screens-verification-2026-10-23/review-triage.md` before fixes. Root replaces misleading food/save promises with note/confirmation copy, shares existing demonstration quantities between text/goals/fills, registers name controls, and wires full summary localization on both native boundaries. UI corrections cover RTL, standalone Back, visible card counts, optional session-name explanation and shared edit-state cancellation. All new reviewer claims requiring runtime evidence remain unverified until their focused and native checks execute.

R6 native red:5 production instrumentation tests ran; all4 provider readiness cases pass, while system Back closes Profile instead of cancelling the name draft. The assertion fails with “Back must cancel editing without leaving Profile”. Saved red-native-name-back.xml before fixing. Shared edit mode is ready; register its cancellation handler inside the Android Profile destination so it takes precedence over closing Profile. Core UI4/4, presenter19/19 and feature25/25 also pass in this invocation.

Final shared UI run passes230 tests across46 fresh suites (2026-10-23T00:48:58–00:50:25Z);0 failures/errors/skips. Explicit iOS-device-target cross-compilation of :feature:photo-analysis:compileKotlinIosArm64 passes on Intel in1m9s, including changed core UI, presenter and shared UI source. No Apple runtime execution is inferred. Full Android instrumentation is running after the Back fix.

Final gate after native150/150:4 task failures remain — stale bootstrap-android-host physical evidence; the newly controlled ProfileScreen has6 parameters (maximum5, reported on Android/JVM); and a SaverScope object literal must use its SAM lambda. Record these before correcting them. Preserve the existing five-argument ProfileScreen API; group identity data in an internal model for the controlled implementation rather than suppressing the parameter rule. Rerun full relevant UI/quality checks after this shape-only correction. Post-test ADB transport also stopped responding during installation; cancelled the two stalled android-cli processes and restarted only the task AVD. No successful native XML is replaced by installation diagnostics.

Final shape-only follow-up: public ProfileScreen retains its original five arguments and delegates through an internal ProfileScreenIdentity; ProfileFlow supplies the same editing state to the controlled implementation. No layout/state/IME logic changed. Full UI230/230 and explicit feature/UI iOS arm64 compilation pass. The final read-only JVM/Android quality gate has exactly one failed task, verifyMobilePolicy: stale bootstrap-android-host; all current code-analysis reports have zero findings.

Final device-verification limitation: the complete Android suite previously passed150/150 (Production/POC/UI Acceptance50 each) before the final parameter-grouping/SAM cleanup. A fresh post-cleanup run did not start: after emulator transport failure, cold starts stalled before sys.boot_completed and showed Launcher/System UI ANRs. The source readiness guard correctly exited before Gradle; it is not a product-test failure or passing evidence. Host load average reached390.56, so the task emulator was stopped rather than mutating user applications or accepting invalid execution. A final production assembly/unit invocation remains in progress; its result must be recorded separately. Retain earlier normal/200% screenshots as pre-cleanup evidence and fixture renders as JVM evidence only.

Final post-cleanup build: production-debug APK assembly and core UI4/4, presenter19/19, feature25/25 unit tests pass with strict dependency verification, --no-daemon and two Gradle workers. BUILD SUCCESSFUL in7m59s;173 actions (18 executed,155 up-to-date). No source changes followed this run. APK digest is recorded in verification/results.json; final installed hash and native rerun remain pending host readiness.
