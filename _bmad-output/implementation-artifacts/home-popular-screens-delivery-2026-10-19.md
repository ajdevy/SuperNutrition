# Popular-screen Home handoff — complete

The requested new HTML and accompanying Markdown document are complete in `_bmad-output/design-review/home-popular-screens-2026-10-18/`. This is a separate focused handoff; the original full review remains byte-identical across all 456 files.

## Results

- Nine exact portrait logical sizes, each captured at 100% and 200% system text: 18 original PNGs, all visually inspected immediately after capture and matched to their SHA-256 inspection entries.
- Twelve initial frames fit without scrolling. The six selected phone sizes at 200% need a different composition: lower metrics or the helpful step fall below the first frame or meet the floating navigation. Text was not reduced to hide these outcomes.
- All three selected tablet sizes fit at both text scales. Their large empty areas are documented for design review; a fit result is not a visual-design approval.
- Same production APK throughout: `7f7296bd7282a86dc63fe4feaad0d4a19706c8b4ca231c39607ff0e3abc1f26c`.
- All 74 local link references, 18 PNG dimensions/hashes and 14 generated-script filter combinations passed independent checks. Python compilation and `git diff --check` passed.

The selected sizes come from worldwide StatCounter web-resolution observations for September 2026. They prioritize logical compositions, not native installed-device market shares. Actual captures are from Android API 35 x86_64 viewport overrides, not iOS or model-specific execution.

## Environment and commands

Worktree: `/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`.
Branch: `codex/figma-camera-flow`; unchanged product HEAD: `262811616f9a2415c5135c23b74597033d788120`.
This documentation slice is uncommitted. It adds the new gallery/generator and an optional output-directory argument to the shared capture helper; it changes no app sources. Unrelated primary-checkout Rewards staging remains untouched.

Intel Mac (`x86_64`), task-owned AVD `SN_Final_Verification_35_20261018`, serial `emulator-5590`, isolated ADB port 5041. No physical phone was used. One emulator ran at a time. The existing review AVD was reused and retained for user review; no new AVD was created.

Capture command, repeated only for the 18 selected size/text pairs:

```sh
python3 scripts/review-home-viewport.py capture --phase after \
  --output _bmad-output/design-review/home-popular-screens-2026-10-18 \
  --serial emulator-5590 --avd SN_Final_Verification_35_20261018 \
  --geometry <selected-geometry> --font <1.0-or-2.0>
```

Every PNG was viewed before the next device action. The initial cold-boot System UI ANR stopped capture before any PNG; its incomplete directory was preserved under ignored build diagnostics, the observed system dialog was dismissed, and the exact capture was retried successfully. See `popular-screen-emulator-recovery-2026-10-19.json`.

```sh
python3 scripts/review-home-viewport.py restore \
  --output _bmad-output/design-review/home-popular-screens-2026-10-18 \
  --serial emulator-5590 --avd SN_Final_Verification_35_20261018
python3 scripts/generate-popular-home-design-review.py
python3 -m py_compile scripts/review-home-viewport.py scripts/generate-popular-home-design-review.py
graphify update .
git diff --check
```

Restoration verified size 720×1560 px, density 320 dpi, text scale 1.0, rotation and timeout against the saved baseline. Production Home was left open. No Gradle build or app-test rerun was needed for this documentation-only slice.

Graphify updated successfully. It reported zero-node metadata files, missing optional SQL extraction support and an oversized graph visualization; those warnings are not app or gallery validation results.

## Validation limits and continuation checkpoint

The browser security policy rejected the local `file:` URL. No alternate browser surface or protocol workaround was used. Therefore browser visual rendering is **not verified**. Static links/data were checked directly, and the actual generated inline filter script passed 14 scenarios in an isolated Node VM without a browser or network. Native app screenshots were all inspected visually.

Reports inside the new gallery: `gallery-data.json`, `static-validation.json`, `filter-validation.json`, `visual-inspection.jsonl`, and `restored-SN_Final_Verification_35_20261018.json`.

Hourly automation `super-nutrition` is active with an hourly interval. This agreed handoff is finished. A future continuation should recognize this checkpoint, remain quiet when nothing changes and avoid restarting completed app work or unrelated tasks.
