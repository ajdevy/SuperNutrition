# Rewards enlarged-text readability regression — 2026-10-18

Final scoped result: 167 shared UI tests pass with zero failures, including
R01–R04's red-to-green fixes. R05 has an actual feature iOS Arm64 compile pass;
UIKit runtime is unavailable. The human retained ten medals and prototype
levels; R07–R09 remain intentional mock presentation and R06's See all action
remains unresolved/nonfunctional. The whole quality command still fails on
separate stale native-gap evidence.

## Finding before production changes

The imported Rewards canvas renders `RewardsVisualCardCopy.title` in fixed-width,
single-line text with `TextOverflow.Clip`. The existing long-copy regression
already supplies ten nonempty `visualCards` with the long title at font scale
1.5, but its assertions measure the node/card bounds only. A clipped line can
retain those bounds and pass. Final progress-card subtitles are present in the
accessible row description; only the first four earned-card subtitles are
visually rendered. An accessible description is not an oracle for visible text.

At the initial checkpoint this was a static finding: the controlled red run
had not yet executed and no responsive-text change had been made. The witnessed
red, diagnostic refutation, corrections and final verification follow below.

## Acceptance to witness red, then fix

- Given ten visual cards titled `A little care for your daily rhythm`, when a
  320×568 phone uses font scale 1.5 and the canvas reaches its actual end, then
  the measured final title displays every character, has no visual overflow or
  ellipsis, and all measured lines fit within its text bounds. Keep the existing
  title/progress/state-in-card and whole-final-card-before-camera assertions.
- Given the renderer's actual default visual copy, when a 320×568 phone uses
  font scale 2, then every one of the ten rendered titles and four displayed
  earned subtitles meets the same full-text measurement criteria, stays inside
  its card, and the final card/state clears floating navigation at the true end.
- Given that same actual default copy, when a 375×812 phone uses font scale 2,
  then the same full-text and final-card criteria pass independently of the
  compact-phone test.
- Given the ordinary 375-width layout at font scale 1, when responsive behavior
  is implemented, then the existing Figma header/grid geometry and paired-card
  alignment regressions retain their exact assertions.

## Authored regressions and measurement

- `PhotoAnalysisLayoutVisualTest`: existing long-copy case strengthened by
  reading the actual tagged title's `TextLayoutResult` after its original
  clearance assertions.
- `RewardsTextReadabilityTest`: four tests cover compact/ordinary phones and
  titles/displayed subtitles independently, so a title failure cannot prevent a
  subtitle red result. They exercise the actual fallback renderer copy with an
  empty `visualCards` model; title tags and subtitle `Text` nodes supply measured
  text, never row `contentDescription`.
- `RewardsTextTestSupport`: checks exact rendered text, complete visible
  character coverage, no ellipsized lines, actual glyph/line spans and natural
  frame dimensions with 1px raster tolerance. Raw paragraph overflow and
  leading are diagnostic metadata. The diagnostic section below records why
  those flags were replaced as clipping oracles.

Parent must archive the fresh red report and append its observed failures here
before production changes for this finding. Builds, devices,
Git, staging and the combined verification remain parent-owned.

## Witnessed red before responsive changes

Phase `focused-rewards-readable-red` ran from 2026-10-17T23:07:01.562624Z
through 2026-10-17T23:07:52.738741Z and exited 1. Its exact invocation and
pre-change source hashes are archived in
`mobile/build/home-no-scroll-2026-10-17/focused-rewards-readable-red/run.json`:

```sh
./mobile/gradlew -p mobile :feature:photo-analysis-ui:jvmTest \
  --tests '*RewardsTextReadabilityTest*' \
  --tests '*PhotoAnalysisLayoutVisualTest*' \
  --init-script /Users/REDACTED/.codex/worktrees/a704/SuperNutrition/mobile/build/agent-ui-runtime.gradle \
  -Psupernutrition.enableIosDeviceTarget=true \
  -Pkotlin.native.home=/Users/REDACTED/.codex/worktrees/a704/SuperNutrition/mobile/build/agent-konan/kotlin-native-prebuilt-macos-x86_64-2.4.10 \
  -Pkotlin.compiler.execution.strategy=in-process \
  '-Dorg.gradle.jvmargs=-Xms256m -Xmx2g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC -Dfile.encoding=UTF-8' \
  --max-workers=1 --dependency-verification strict --no-watch-fs --continue \
  --no-configuration-cache --console=plain
```

The freshly archived XML establishes **19 tests / 5 failures / 0 errors / 0
skipped**, split by module suite:

- `xml/TEST-com.supernutrition.feature.photoanalysis.PhotoAnalysisLayoutVisualTest.xml`:
  15 tests / 1 failure. `long reward copy remains readable with larger text`
  fails `Rewards text is clipped: A little care for your daily rhythm`.
- `xml/TEST-com.supernutrition.feature.photoanalysis.RewardsTextReadabilityTest.xml`:
  4 tests / 4 failures. Compact and ordinary font-2 title cases each fail
  `Rewards text is clipped: Fiber Friend`; compact and ordinary font-2 subtitle
  cases each fail `Rewards text is clipped: Fiber goal · 3 days`.

All failures are actual measured `TextLayoutResult.hasVisualOverflow` findings,
not fixture, selector, compilation or device prerequisite failures. These
failures were witnessed and recorded before changing the production renderer.

## Responsive correction prepared for verification

Enlarged system text now selects a measured, single-column canvas. Titles and
displayed subtitles keep the original font sizes and the current system font
scale; they wrap without a line cap, and cards grow to contain them. Header,
hero and section copy also flow naturally. Trailing scroll padding uses the
shared navigation overlap plus a visible gap, so the final card and its state
can clear the floating controls at the true end.

The font-scale-1 Figma canvas retains its existing positions and sizes. Both
paths share the exported artwork placement, medal crop factory, palette,
progress/state formatting and accessible row description. No assertion, font
scale, typography size, quality policy or business rule was lowered to pass.
At this checkpoint fresh green execution was still pending.

## Full-candidate failures and diagnostics before further changes

Phase `full-final-candidate` (2026-10-17T23:16:49.547122Z through
2026-10-17T23:25:19.785199Z, exit 1) has 163 shared UI tests / 7 failures / 0
errors / 0 skipped. Its exact combined command and source hashes are archived
in `mobile/build/home-no-scroll-2026-10-17/full-final-candidate/run.json`.

Owned Rewards results in that phase are five failures:

- `xml/TEST-com.supernutrition.feature.photoanalysis.RewardsTextReadabilityTest.xml`:
  4 tests / 4 failures, the compact/ordinary font-2 title and subtitle cases still
  report `Rewards text is clipped` for `Fiber Friend` and `Fiber goal · 3 days`.
- `xml/TEST-com.supernutrition.feature.photoanalysis.PhotoAnalysisLayoutVisualTest.xml`:
  15 tests / 2 failures. Its one owned Rewards failure reports `Rewards line 0
  is vertically clipped: A little care for your daily rhythm`; the other
  failure belongs to the Home ownership slice. The remaining Home failure is in
  its separate suite, also outside this slice.

`hasVisualOverflow` is not by itself sufficient to diagnose native glyph
clipping: paragraph alignment canvases can differ from the visible text frame.
Before further production edits, failure diagnostics now include text layout
size, actual semantics frame, constraints, density/font scale, font/line height,
every measured line's character coverage and ellipsis state, line spans, and
the bounding union of its non-whitespace glyphs. Original full-character,
vertical, horizontal and card/navigation assertions remain enforced while the
parent runs this diagnostic version.

Independent review additionally found normal-font defects: the 80dp header
clips `Rewards` to `Reward`, the 94dp one-line card frame clips long visual
titles, the Back target is 38dp rather than at least 48dp, and the See all link
has contrast approximately 3.407 rather than 4.5. New measured font-1 header and
card cases, Back width/height/click checks, and a contrast check against the
actual backdrop are ready for parent red execution. These new cases have not
yet been executed here. No production fix for them has been made yet.

The owned lint findings (single-class image-helper filename and crop-factory
signature formatting) are retained for the next bounded production correction.
See all behavior and native/iOS Back wiring remained parent-owned. The
seven-domain-criteria/ten-medal decision was pending at this checkpoint; its
subsequent human resolution is recorded below.

## Diagnostic red distinguishes metadata from rendered clipping

Phase `focused-rewards-normal-and-diagnostic-red` ran at
2026-10-17T23:32:57.598559Z through 2026-10-17T23:35:12.415427Z, exit 1.
Its `run.json` contains the exact focused command, and its two fresh XML suites
establish 23 tests / 9 failures / 0 errors / 0 skipped (15/1 PhotoLayout, 8/8
RewardsTextReadability).

The five enlarged-text failures are verifier defects, not evidence that the
new natural renderer clips text. `Fiber Friend` covers 12/12 characters inside
134×30; its actual glyph bounds are x=0..133.5 and y=0..30, although its
paragraph canvas permits width 225 and reports overflow. `Fiber goal · 3 days`
covers 19/19 characters inside 169×26 with glyph bounds x=0..168.7 and y=0..26.
The long title covers 35/35 characters in a 193×38 two-line frame; its first
line's glyph top is -0.2px, while `getLineTop()` reports -1px of leading. These
differences cannot justify shrinking typography or enlarging cards further.

The corrected verifier will retain full character coverage and no ellipsis,
check actual non-whitespace glyph bounds and line width, and require natural
layout size to fit the actual semantics frame, with 1px raster tolerance.
`hasVisualOverflow` and line-leading remain diagnostics rather than an ink
oracle. All card/state/navigation assertions remain enforced.

Four normal-font findings are genuine and were witnessed before their fixes:

- Header `Rewards`: length 7 but first line ends at 6 in an 80×24 frame.
- Normal long visual title: length 35 but first line's visible end is 17
  (untrimmed end 18) in a 94×15 frame.
- Back touch target: measured width 38dp, below 48dp.
- See all: actual text/backdrop contrast 3.4069164, below 4.5.

Prepared correction: a naturally sized centered header, an expanded 48dp Back
hit region preserving the 38dp visual circle and its center, text-fit-driven
selection of measured cards for custom long copy even at font scale 1, and a
darker olive link token (#5D7120; calculated contrast approximately 4.943 on
#F7F3EF). No domain criteria, reward mapping, native host callback or See all
behavior is changed by this slice. Fresh green verification followed below.

## Focused green and shared typography handoff

Phase `focused-rewards-complete-green` ran at 2026-10-17T23:42:08.517425Z
through 2026-10-17T23:43:15.883409Z and exited 0. Its archived `run.json` and
fresh XML establish **23 tests / 0 failures / 0 errors / 0 skipped**:

- `PhotoAnalysisLayoutVisualTest`: 15/0/0/0, including the exact regular Figma
  alignment, full final-card clearance and enlarged long-copy assertions.
- `RewardsTextReadabilityTest`: 8/0/0/0, including normal header/card character
  completeness, Back target/click, real backdrop contrast, and compact/ordinary
  font-2 title/subtitle glyph and natural-frame checks.

This green validates the genuine normal-font fixes and the measured refutation
of the five enlarged verifier failures. Paragraph canvas overflow and negative
leading were not treated as native ink clipping; full characters, glyph spans,
natural frame dimensions and navigation clearance remain enforced.

After that run, the bounded `RewardsCardTypography` refactor replaced repeated
title/subtitle font recipes in the fixed renderer, measured renderer and fit
predicate with the same composable `TextStyle` helpers. Font family, weights,
sizes, line heights and colors retain their rendered values; progress text
also reuses the subtitle typography with its existing color/alignment. This
prevents measurement/rendering drift without changing layout decisions or
business representation. The final combined run after this refactor is
recorded below; the preceding focused green remains distinct pre-refactor
evidence.

## Final scoped verification and retained prototype representation

Phase `full-final-scoped` ran at 2026-10-17T23:57:30.559541Z through
2026-10-18T00:01:03.374332Z. Its 30 fresh shared UI XML suites establish
**167 tests / 0 failures / 0 errors / 0 skipped**, including 15/0/0/0
PhotoAnalysisLayoutVisualTest and 8/0/0/0 RewardsTextReadabilityTest after shared
typography and test-helper brace fixes. Exact invocation, source hashes, raw
log and XML are archived under
`mobile/build/home-no-scroll-2026-10-17/full-final-scoped/`.

R01–R04 are fixed and covered by witnessed red-to-green tests: complete normal
header/card text, 48dp Back hit target with working Compose callback, and actual
link/backdrop contrast at least 4.5. The final glyph oracle preserves full
characters, no ellipsis, natural frame dimensions, and card/state/navigation
clearance while rejecting the five enlarged paragraph-metadata false positives.

R05's actual `:feature:photo-analysis:compileKotlinIosArm64` task executed and
passed in this final run after adding the missing `navigationController`
import. It is source-compilation evidence for the iOS Back adapter. UIKit
runtime validation remains unavailable; full sharedKit/Xcode/physical-iOS
success is not established by that module compile.

The combined command exited 1 solely at `:verifyMobilePolicy` with
`Native-gap evidence is stale: bootstrap-android-host`. The final UI pass does
not make that whole quality gate green or retire the separate evidence expiry.

Human representation decision on 2026-10-18, direct conversation:
«Сохранить 10 медалей; новые уровни пока оставить как макет».
R07–R09 intentionally retain the ten-medal design prototype, static level/
points copy, index grouping, visual goal text and higher-tier duplicates. The
seven canonical domain criteria are unchanged; no new level or reward criteria
are promised. Those prototype labels are not live earned-state evidence.

R06 remains an unresolved prototype action: See all is a nonfunctional label;
an actual earned collection and reversible dismissal have not been
implemented. This is a verified layout/readability result with a documented
interaction limitation, not a clean whole-interaction pass.

This final update changes documentation only. Production source and tests stay
frozen for the APK used by the parent's current 94-image native matrix; native
matrix results remain separately recorded by its owner.
