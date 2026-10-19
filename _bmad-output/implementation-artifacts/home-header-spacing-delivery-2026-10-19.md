# Home compact header and spacing — delivery evidence

Branch: `codex/figma-camera-flow`. Worktree: `/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`. Source baseline: `262811616f9a2415c5135c23b74597033d788120` plus this slice, with source hashes recorded and matched after all application checks. The preceding focused gallery/generator WIP is retained as an immutable dependency of the new comparison; the primary main checkout was not edited.

## Result

Visible Super Nutrition and weekday/date removed. The rhythm badge and profile remain, including Home accessibility identity and RTL/live update handling. Header/care, care/mascot and rhythm/helpful-step have a shared 16dp gap; page padding is 8dp, outside phone gutters 24dp, card insets 12dp and metric gaps 8dp. Fixed gaps are budgeted before decoration. The care tail paints in its existing gap. No typography, minimum action size, meal/goals callbacks or completed food assets were reduced or hidden.

## Fresh verification

- Focused red: 9 UI tests, 8 intended failures; focused green: 9/0/0/0 tests/failures/errors/skipped.
- Full final JVM UI module: **177/0/0/0**, 31 fresh suites. Core UI: **4/0/0/0**, one fresh suite. Existing ordinary-phone no-scroll and 160dp mascot expectations pass unchanged.
- Production Android assembly, scoped core/UI detekt and explicit `:feature:photo-analysis-ui:compileKotlinIosArm64 -Psupernutrition.enableIosDeviceTarget=true` pass on this Intel host. Shared core was compiled as a dependency. This is Apple source compilation, not Apple runtime execution.
- Core UI Kover XML: 142/151 lines (94.04%), 25/28 branch outcomes (89.29%); all four executable changed lines and their branch outcomes covered.
- Canonical `:apps:androidApp:connectedAndroidTest`: **43/0/0/0 for each of poc, production and uiAcceptance**, 129 total. One API35 x86_64 emulator, serial `emulator-5590`, owned AVD `SN_Final_Verification_35_20261018`, isolated ADB5041, strict dependency verification. No physical-phone interaction.
- Tooling regressions: 7 tests, 6 intended failures before fixes; 7/0/0 afterward. Complete `python3 -m unittest discover -s scripts/tests`: **32 passing**. Mocked capture calls are offline tool tests, never device evidence.

Exact Gradle commands, source hashes and fresh per-module XML are saved under the new gallery's [verification directory](../design-review/home-spacing-2026-10-19/verification/). Build run logs remain under `mobile/build/home-header-spacing-2026-10-19/`. One Gradle invocation ran at a time. No stale root quality gate or expired physical/native policy record was renewed or claimed green.

Production APK SHA-256: `81d6e595d545c8379d85f828cf2b1256c92a115b61a4d5b498a307aaca705012`, 478,036,374 bytes. The installed APK was independently hashed before captures.

## Designer review

[New HTML gallery](../design-review/home-spacing-2026-10-19/index.html) · [Designer document](../design-review/home-spacing-2026-10-19/DESIGNER-NOTES.md) · [Validation](../design-review/home-spacing-2026-10-19/gallery-validation.json).

Nine popular portrait sizes, 18 new raw PNGs at 100% and 200% text. All nine normal-text variants fit. Three enlarged tablet variants fit; six enlarged phone variants keep readable scrolling and are explicitly marked designer-required. All 52 visible/measurable independent section boundaries are 16dp; two lower boundaries are entirely offscreen at enlarged text and are not falsely reported as measured. The 18 main screenshots, one before image and one restored-state screenshot were each visually inspected and journaled with matching PNG hashes. Previous full 456-file and focused 86-file galleries remain byte-identical.

All emulator settings were restored and verified against the saved baseline; the updated production Home remains open. The restored viewport retains the guest's tablet-style system taskbar after overrides, visible in the uncropped control PNG. Size labels describe native logical compositions, not specific hardware or iOS execution. Tablet proportions and enlarged-phone limits are documented honestly.

HTML links, finite profile inventory, one expected APK and 14 filter scenarios using the actual inline JavaScript passed offline. Browser visual HTML execution is unavailable because its security policy rejected local file navigation; no alternative route was used. Native PNG inspection is complete.

Two independent review agents found three tooling issues and no introduced product defect. All three are fixed, with a read-only follow-up confirmation. See [review findings](home-header-spacing-review-2026-10-19.md). Source changes and acceptance trail are in [the completed spec](spec-home-header-spacing.md), including a Suggested Review Order.

`graphify update .` completed with exit 0: 21,230 nodes, 28,749 edges, 2,026 communities. It reported 453 files without extracted nodes, two SQL files without the optional parser, and skipped the oversized HTML visualization. The code graph and report were updated; no semantic document extraction is claimed. Working-tree and staged `git diff --check` both pass. Only the intended Home slice, its evidence/tooling and the preceding gallery dependency are included in the local save.
