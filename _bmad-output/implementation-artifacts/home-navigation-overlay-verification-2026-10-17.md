# Home floating-navigation follow-up — 2026-10-17

Home now renders through the safe viewport beneath the floating navigation, matching Rewards. The blank canvas strip is removed; scroll-end padding raises the whole helpful-step card and Do it above the controls. The former compact breakpoint and content spacing remain stable. The illustrated navigation pill and its targets are preserved.

Worktree: `/Users/anastasia/.codex/worktrees/a704/SuperNutrition`; branch `codex/figma-camera-flow`; HEAD `5ee5de077866feed70b418ee2760af24322b1d22`. Prior tracked and untracked changes remain. No files were staged, committed, pushed or merged.

## Verified source and build

- Focused baseline red: **4 / 2 / 0 / 0** (tests/failures/errors/skipped), before production edits. Home ended 83 dp above the safe bottom; the rhythm card disappeared at the reserved canvas.
- Focused green: **4 / 0 / 0 / 0**. Real pointer navigation, rendered card pixels and a swipe beside the pill pass; whole final card/action clearance passes at 320×568, 360×780, 412×915 and 600×960 dp with fonts 1.0 and 2.0.
- Full final UI JVM: **118 / 0 / 0 / 0**, 21 fresh XML suites, exit 0 in 2m46s. Six scoped UI lint reports have zero issues. Explicit UI iOS Arm64 compilation executed on this Intel host in the first complete task attempt; unchanged production inputs are legitimately UP-TO-DATE in the final replay. This does not claim Swift/Xcode, Apple lint or Apple device execution.
- The first full UI attempt had three legacy scroll-preparation failures, preserved separately. Their helpers stopped at the rectangular viewport edge rather than the actual scroll end after the overlay change. Only the preparations were corrected; all strict above-camera and readability assertions remain. Both independent reviews assessed this distinction and found no actionable introduced defect.
- **104 final source hashes** remain current after review. Final slice diff SHA-256: `f68d96a229ab43b925e2e608c81e4bbfb4d3be204406cf0a7033a6b846335774`.

Exact Gradle commands, all unfiltered output, exit records, fresh XML and cache lineage are archived in `mobile/feature/photo-analysis-ui/build/reports/home-navigation-overlay/`. Entry commands from the repository root were `python3 mobile/build/home-navigation-overlay-build.py red`, `focused-green` and `full-green`; the wrapper records its complete Gradle argument list in each phase's `run.json`. Full tasks include UI `jvmTest`, the six existing scoped detekt tasks, explicit `compileKotlinIosArm64` and `:apps:androidApp:assembleProductionDebug`, with `--continue`, strict dependency verification and no configuration cache. One Gradle invocation ran at a time.

## Emulator execution

Current production APK SHA-256: **`0969aa8fa690470aa9889a22fd41a805bedc12a1a1927a1bb204489c8a014abd`**, read back from the installed APK. Retained `SuperNutrition_API_35_x86_64`, API35 x86_64, serial `emulator-5554`, isolated ADB 5041; actual 720×1560 pixels at 320 dpi, font 1.0. This is emulator evidence.

Home safe viewport is `[0,136][720,1464]`. A real swipe beside the pill reaches the end: whole final card `[48,1132][672,1282]`, Do it `[521,1159][648,1255]`, first floating control y=1304, giving **22 px** card clearance. Rewards list still extends to y=1464 after an actual tab tap. Home was reopened and the emulator left running for the user's requested review. No additional AVD was created and no physical device was touched.

Visually inspected screenshots and fresh XML: UI report `emulator/home-top.png`, `home-end.png`, `rewards.png`, and corresponding XML. Installed digest/runtime profile, native end bounds and final Home foreground are in `installed.json`, `home-end-verification.json` and `final-state.json` in that folder.

## Review and handoff

[Blind review](home-navigation-overlay-blind-2026-10-17.md) and [edge review](home-navigation-overlay-edge-2026-10-17.md) found zero actionable introduced defects in the exact scoped patch. These are independent static reviews; automatic TalkBack/focus reveal was not executed. [The completed spec](spec-home-navigation-overlay.md) includes a clickable source review order and preserves all red/partial evidence.

`git diff --check` passes. `graphify update .` exits 0: 20,774 nodes, 27,818 edges, 1,992 communities. Graph evidence is code AST only: two SQL files lack the parser dependency, 253 JSON/asset files produce zero AST nodes, semantic document extraction did not run, and graph HTML exceeds the configured size limit.

The preceding complete 120-test Android instrumentation result belongs to APK `90c2c7…ad400`; that complete native suite was not repeated for this layout-only follow-up. Full root quality was not rerun; its preceding result was separately blocked by expired bootstrap Android-host evidence. No full root quality, physical phone or Swift/Xcode execution pass is claimed here.

After this agreed follow-up completed, automation `super-nutrition` was updated through the app tool to **PAUSED**, following its explicit instruction to stop when work is complete. Its prompt, hourly recurrence and attached chat were preserved. The emulator remains running for the user's manual review.
