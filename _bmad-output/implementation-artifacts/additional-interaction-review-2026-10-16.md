# Additional interaction and display review — 2026-10-16

User request: check further app interactions/display variants and fix demonstrated defects.

Baseline: `codex/figma-camera-flow`, HEAD `5ee5de077866feed70b418ee2760af24322b1d22`, existing local camera/Home/Rewards work preserved. The preceding physical run passed 40 tests in each of POC, production and uiAcceptance (120 total). Those results predate this review and do not validate subsequent changes.

## Review matrix

| Area | Additional boundary | Acceptance |
| --- | --- | --- |
| Recovery/loading | Short viewport, long status, font 200% | Close never covers status or recovery controls; content scrolls |
| Home care actions | Larger text, unequal label lengths | Symmetric action heights, readable labels, adequate touch targets |
| Goals dialog | Save/restore while open | Dialog stays open; dismissal survives the next restore |
| Photo review | Three long dish names, large text | No clipped labels; every choice and footer remains reachable |
| Main screens | Font 200%, narrow/landscape viewports | Last content reachable above navigation; tabs remain usable |
| Native host | Back, picker cancellation, tab return, background | Correct return destination, no unintended capture or selection |

Android's installed Activity declares portrait orientation. Shared landscape rendering is a layout compatibility check, not evidence that phone rotation changes the installed Activity's orientation.

## Findings and verification

Focused baseline run: `AdditionalInteractionLayoutsTest`, six tests, five failures and one pass; fresh XML archived at `mobile/feature/photo-analysis-ui/build/reports/additional-interactions/red/`. Log: `mobile/build/additional-interactions-red.log`. Each failure was observed before production edits.

| ID | Reproduced defect | Acceptance/failing evidence |
| --- | --- | --- |
| N-18 | Recovery content scrolls underneath Close | At 320×240 / font 200%, Close `(13,31)-(61,79)` overlaps status `(32,32)-(288,164)`; reserve a separate scroll viewport below Close |
| N-19 | Home care buttons have unequal heights | Large labels measure 66 vs 122 dp; both must share height. An explicit 48 dp semantic/touch frame is an additional acceptance requirement; the old 32 dp painted/semantic size alone does not establish the extent of Compose's automatic pointer-target expansion |
| N-20 | Goals dialog disappears after state restoration | Save the real Compose registry with goals open, remove/recreate composition: goals are absent; open/dismissed state must restore correctly |
| N-21 | Selected long dish labels clip at font 200% | All three proposals in the ordinary review row; text layout reports visual overflow after explicit selection; labels must measure fully and choices/footer remain reachable |

The 812×375 / font 200% Home/Rewards final-content/navigation test already passes. No claim of exhaustive device/OS coverage.

First full-module replay: 101 UI tests, one failure in the existing ordinary-phone first-frame check. Raising the painted Home buttons from 32 to 48 dp pushed the next-step card downward. Correct the hit frame inside the existing bubble padding and preserve the original visible button height. Source lint also identified formatting in this review's new code; repair rather than suppress it.

N-22 (visual inspection): at font 200% on a 320 dp viewport, Home reserves two 48 dp side slots around the brand. The narrower text slot splits `Nutrition` into `Nutritio` and `n`. Acceptance: retain the complete brand words, centered, with a separate reachable profile action at large type sizes. Add a text-layout regression and witness failure before changing the header.

N-22 regression witnessed red: one test, one failure, `Brand words split: [Super, Nutritio, n]`. XML archived under `additional-interactions/brand-red/`; log `mobile/build/additional-interactions-brand-red.log`. At font scales above 150%, the header now gives the brand a full row and puts the profile action in a separate row.

## Final implementation

- N-18: the recovery scroll viewport starts below the independently anchored Close button, including at font 200% on the 320×240 shared viewport.
- N-19: care actions share their measured height. Their explicit hit frames use existing bubble spacing; the painted chip retains its original normal-size appearance. The original ordinary-phone first-frame and icon/text-centering regressions pass again.
- N-20: Home retains the goals dialog's open/dismissed state through Compose saved-state restoration.
- N-21: large type and narrow review layouts show dish choices vertically. Ordinary-size proposals retain the horizontal layout and exact IDs/selections.
- N-22: large Home type gives the brand its full line and keeps profile reachable in a separate row. Complete brand words render correctly in the inspected image.

Review method: focused code/path inspection and executable desktop Compose tests. No independent review agents were run. Deterministic review models exercise presentation/interaction; they do not establish additional vision-model accuracy or expand recognition beyond its existing taxonomy.

## Final verification

Host: Intel `x86_64` macOS. Worktree remains `/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`, branch `codex/figma-camera-flow`, HEAD `5ee5de077866feed70b418ee2760af24322b1d22`. Changes are local, unstaged and uncommitted; unrelated work/assets were preserved.

| Module / check | Tests / failures / errors / skipped | Execution |
| --- | --- | --- |
| `:feature:photo-analysis-ui:jvmTest` | **102 / 0 / 0 / 0** | Fresh full run, including nine added tests |
| `:feature:photo-analysis:jvmTest` | **25 / 0 / 0 / 0** | Fresh full run |
| `:feature:photo-analysis-presenter:jvmTest` | 19 / 0 / 0 / 0 | `UP-TO-DATE`, unchanged inputs; earlier XML retained |
| `:core:ui:jvmTest` | 4 / 0 / 0 / 0 | `UP-TO-DATE`, unchanged inputs; earlier XML retained |
| Five UI source-set/typed lint reports | Zero issues | Fresh commonMain, jvmTest, mainJvm, testJvm, mainAndroid reports |
| `:feature:photo-analysis-ui:compileKotlinIosArm64` | Completed | Explicit device target on Intel, Kotlin/Native only |
| `:apps:androidApp:assembleProductionDebug` | Completed | New production APK ready |
| `qualityCheckJvmAndroid --continue` | **Failed** | Sole failed task is `verifyMobilePolicy`: `Native-gap evidence is stale: bootstrap-android-host`; no baseline/TTL/policy changes |
| `git diff --check` | Passed | Local dirty worktree |
| `graphify update .` | Completed | 20,574 nodes / 27,534 edges / 1,977 communities; 253 empty AST inputs and two missing SQL-parser inputs reported honestly; HTML skipped above size limit |

The combined Gradle run exited 1 in 4m 10s because of the policy failure. Test results above were read from module XML, checked for freshness and archived; they are not inferred from a build summary. Exact environment, full command, input hashes, APK digest, report paths, fresh/cached module distinctions and host identity are in:

`mobile/feature/photo-analysis-ui/build/reports/additional-interactions/verified-final/verification.json`

Execution logs:

- `mobile/build/additional-interactions-red.log` — original six-test run, five failures.
- `mobile/build/additional-interactions-fix1.log` — three defects corrected, long dish labels still fail.
- `mobile/build/additional-interactions-fix2.log` — original six tests pass.
- `mobile/build/additional-interactions-full.log` — first full run exposes the ordinary-phone regression and formatting findings.
- `mobile/build/additional-interactions-brand-red.log` — brand word-splitting regression fails before header correction.
- `mobile/build/additional-interactions-final.log` — final full test/build/lint execution; policy blocker remains.
- `mobile/build/additional-interactions-graphify.log` — graph update and parser limitations.

New production APK:

`mobile/apps/androidApp/build/outputs/apk/production/debug/androidApp-production-debug.apk`

SHA-256: `b4dfa414823d75053df60b4e23db5185b0b904c07bf820888091759c18605286`.

Final screenshots were visually inspected: `mobile/feature/photo-analysis-ui/build/reports/layout-design/home.png` (normal Home with complete card), and `.../additional-interactions/home-care-large-text.png` / `review-large-text.png` (stress fixtures at font 200%).

## Physical follow-up checkpoint

User authorized device checks and renewed availability for 20–25 minutes. Physical HONOR DNY-NX9, hardware serial `AU3N025429006787`, Android 16/API 36, arm64-v8a. A separate task-owned ADB server on port 5041 restored the Wi-Fi connection without restarting the user's global ADB server. Exactly one canonical endpoint remains on that server: `adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp`.

All six APKs were assembled from the current inputs in one successful preparation build. The canonical `connectedAndroidTest` was then attempted twice with strict dependency verification, a 900-second installation timeout and foreground guards. Both attempts stopped **before any fresh native test result**:

| Attempt | Start / stop (UTC) | Result |
| --- | --- | --- |
| 1 | 05:43:02 / 05:47:40 | Exit 130; foreground switched to `com.flexsentlabs.onering` during installation; zero fresh XML |
| 2 | 06:00:12 / 06:06:43 | Exit 130; same foreground switch during POC installation; zero fresh XML |

The second attempt began with production Super Nutrition foreground. At 2026-10-16T06:15:08.374472+00:00 the foreground was `com.flexsentlabs.onering.test/.KeepAliveActivity`; the Android activity instrumentation inventory was empty. This establishes the interfering foreground, not the process or chat that caused it. A concise clarification was sent to the user; no OneRing app, process, data or unrelated chat was changed.

Both attempts stopped only Super Nutrition target/test packages and restored the original screen timeout, verified at 600000 ms. Font scale remains 1.0. Each attempt's logs and metadata are preserved under `mobile/apps/androidApp/build/reports/additional-interactions-2026-10-16/native/attempt1-foreground-guard/` and `attempt2-foreground-guard/`. No owned Gradle execution remains active. The task-owned ADB server on port 5041 was stopped after cleanup; the global server on port 5037 was left untouched. `git diff --check` passed again. APK digests are recorded in `.../additional-interactions-2026-10-16/artifact-digests.json`.

The earlier 120/120 native tests belong to the preceding N-17 build and do **not** validate this APK. Once the foreground conflict is resolved, resume the current-source native run and manual large-font, goals restoration, gallery cancellation/review/keyboard, tab return and permission checks. Preserve earlier device evidence; restore font, timeout, POC permissions and AppOps after any temporary changes. The prior hourly automation remains deleted; no new schedule was created.

## Existing limitations

The root JVM/Android quality gate previously failed on expired committed `bootstrap-android-host` evidence. Full Xcode/iOS host execution was unavailable. Keep those limitations separate from focused test results; do not relax policy to obtain a green gate.

## N-24 — native large-text Home metric word split

During the user-authorized emulator follow-up on the N-23 APK, normal Home and goals preservation were checked. At font scale 2.0, after scrolling Home to the bottom, the `Movement` metric title visibly splits into `Movemen` and `t` in its two-column tile. Evidence: `mobile/apps/androidApp/build/reports/rewards-emulator-2026-10-16/home-font-200-bottom.png` (visually inspected), with corresponding XML. The full next-step card and Do it are reachable after scrolling; their navigation clearance is intact.

Acceptance: every standard metric title retains its whole word at font 200% on a 360 dp viewport, values remain readable and final Home content stays reachable above the tabs. Retain the normal two-column design and symmetric spacing at ordinary text size. Add a text-layout regression and observe it fail before changing the production grid. Further emulator interaction checks continue on the pre-N-24 APK before the next single-build replay.

N-24 red witnessed twice before production edits: initial all-title test failed on a clipped Fiber text layout; the reordered reproduction failed on `Movement -> [Movemen, t]`, matching the native screenshot. XML archived under `mobile/feature/photo-analysis-ui/build/reports/home-metric-word/red-first-fiber/` and `red-movement/`. The metric grid now shares one item/row implementation and uses one column for large fonts or a narrow content area; ordinary-size two-column appearance is preserved.

## N-25 — review footer hidden by IME on a short usable viewport

The Intel API 36 emulator exposes a shorter usable area than the preceding physical phone. With the owned banana gallery fixture, selecting the custom plus and opening the manual input shows the real Gboard IME beginning at y=962. After typing `apple`, the input bounds end at y=992 and Retake/Add to meal remain below the keyboard. The same read-only native geometry checker used for N-17 fails: `photo_analysis_manual_ingredient_input extends behind the keyboard: (124, 896, 656, 992)`. Screenshots were immediately visually inspected and preserved under `rewards-emulator-2026-10-16/ime-short-viewport-red/`.

Acceptance: at a 360 dp width and approximately 414 dp usable review height, keep the focused manual input and both footer actions fully inside the available area; allow the photo-food/header portion to scroll while Close remains independent. Preserve ordinary-size review positioning and retained-photo proportions. Add a short-viewport layout reproduction and witness it fail before changing production. This is a newly verified layout defect; the preceding physical large-screen N-17 verification remains valid for its own viewport.

The short-viewport red first failed because the manual input was outside the visible area. Scrolling that field into view then reproduced clipped input bounds `(62,396,328,414)`, independently of the native IME failure. The weighted body/fixed footer fix passes the 414 dp and 200% fixtures together with all ten gallery-fit checks. The existing plus-centering check now scrolls large-text choices into view before measuring them. Independent review identified a further boundary: extremely small usable heights cannot fit the complete fixed footer plus a visible body. Add a reachable full-content scroll fallback based on the same measured action height used to render the buttons, and verify it at 170 dp / 200 dp available heights. Do not use guessed duplicate label measurements.

## N-24/N-25 full local replay

`mobile/build/review-compact-footer-final-green-build.log` completed with exit 0 in 9m17s. Fresh full UI XML is **105/0/0/0** across 19 suites; the six scoped source/typed lint reports have no findings (commonTest unchanged/cached; the other five regenerated in this final run). All six app/test APKs assembled, and the explicit iOS Arm64 UI target is current on this Intel toolchain. Current production SHA-256: `82a4e1747358626f944c95705c1c0579787d40563e9ed33724a5753741b4534a`. Exact manifest and archived XML are under `rewards-emulator-2026-10-16/build-verification.json` and `photo-analysis-ui/build/reports/review-compact-footer/verified-green/`.

The N-25 independent review's two findings were addressed: footer tests directly verify visibility/actions rather than requiring a scroll ancestor, and the extreme-height fallback is selected using the same measured action height as the renderer, with shared note/padding/gap constants. Two short-height fixtures (170dp ordinary and 200dp/font200%) reproduced the missing-body defect before the fallback and now pass. The 414dp fixtures compare unclipped and visible field bounds as well as action height. Ordinary review footer placement and all ten retained-photo fit cases pass. No suppression or lint threshold was added.

## Emulator interruption and recovery

The 2GiB guest stalled during permission-settings return: production had an input-dispatch ANR, UI dumps and subsequently ordinary ADB shell commands stopped responding. Preserved traces show approximately 463MiB of production process swap and a failed tombstoned stack dump; the earlier system-server trace is a PowerManagerService pre-watchdog block. The available app trace only establishes its main thread waiting in Binder; it cannot attribute an application-code deadlock. Two interrupted permission attempts restored exact POC grant/flags/preferences and package/UID AppOps. A four-day-old abandoned UI test executor from this same worktree was also stopped after verifying its PID, parent, cwd and old daemon shutdown log. No other checkout or global ADB server was changed.

Task ADB port 5041 was restarted, responsiveness recovered, and scoped ANR files were copied from the owned emulator with a recorded temporary adbd root/unroot (uid 2000 restored; backup flag false). Native source-code verification remains distinct from this environment interruption. Restart the same owned guest with 4GiB, two vCPUs, SwiftShader, emulated camera and no boot animation, retaining the 720x1560/API36/x86_64 profile and all existing AVD data. Repeat current-source native and manual checks before treating this recovery as verified.

N-25 follow-up acceptance from independent review: switching between pinned-footer and extreme-height layouts must retain the focused manual input and its current text. The first adaptive version relocates the footer between different composition parents; direct-start viewport tests do not establish focus retention across that transition. Add a dynamic shrink/grow regression, witness focus loss, then keep the footer under one stable composition parent while switching the outer/body scroll modifiers.

The dynamic 780→414→170→414→780dp regression failed on `Focused=false` while the note still read `apple`; XML preserved under `review-compact-footer/focus-red/`. The corrected layout keeps one outer Column and the footer in one composition position, switching only the viewport/body scroll modifiers. Focus, caret-host state and the text are no longer recreated between branches. All three compact-viewport tests now pass (**3/0/0/0**, 4m24s); this is focused evidence, followed by a fresh full module/build replay.
