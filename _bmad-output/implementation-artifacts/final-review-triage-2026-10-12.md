# Camera, Home and Rewards review triage

Baseline: `5ee5de077866feed70b418ee2760af24322b1d22`; branch
`codex/figma-camera-flow`. The two independent review artifacts were read before
the following fixes. Their source findings are not device-test results.

## Accepted patch findings and acceptance conditions

1. **Stale evidence selection, high:** an accepted grid reanalysis must clear
   dish/component selections and the old confirmation before new evidence can
   be selected. Explicit manual ingredients, pantry choices and the note stay
   editable. Ignored events and background restoration preserve current choices.
2. **Missing review exit, high:** the no-selection review must expose the existing
   host Close callback without taking a photo, selecting foods or invoking Retake.
   The existing host cancellation policy remains authoritative.
3. **Unknown food selection cue, medium:** an unfamiliar detected food keeps its
   actual label and a visible checkmark when selected, in addition to accessible
   selection semantics. No illustration is guessed from a substring.
4. **Zero-height Rewards list, high:** with the real earned summary in a 320 by
   240 dp shell, all seven rewards and the final progress remain reachable by
   scrolling. The header participates in that scrolling.

Add focused regressions and observe their failures before changing production.
Run the complete relevant tests after fixing these cases.

## Other reviewer observations

- Confidence/details visibility, a synchronous native confirmation handoff and
  its photo identity are contract/presentation questions, not reproduced
  regressions. This consumer UI retains explicit food selection and the existing
  synchronous session-local draft; no asynchronous or persisted-meal consumer is
  introduced. Do not claim these questions were proven safe by static review.
- The library-size observation requires measurement as its coverage expands;
  assets alone are not detector coverage. Preserve generated originals and
  render only current photo components.
- Native permission controls in a short window and translated large footer
  labels require targeted verification. Existing normal-phone checks do not
  prove these boundaries.
- The centered final Rewards card is an intentional documented layout choice.

## JVM and Android quality findings

The failed full gate reported Android when-condition spacing, a single model
with a mismatched file name, and CaptureTab length. The scoped worker recorded
these before editing in `android-camera-lint-cleanup-2026-10-12.md`.
Remaining shared UI findings concern HomeRhythmCard length, modifier line
wrapping, test argument wrapping and expression-body signatures. Extract the
metric grid and format only reported lines; retain the measured geometry and
tests. Presenter test argument wrapping is also in scope. No suppression or
baseline is authorized for these findings.

## Follow-up verification

Before the four fixes, the new boundary class reported 3/3 failures (missing
Close, missing unknown-food checkmark, Rewards viewport `Rect(0,0,0,0)`), and
feature review reported 9 tests/1 failure for the stale accepted confirmation.
After the fixes, all three UI boundaries and all 25 feature tests passed.
The expanded catalog independently reported its intended 9 tests/8 failures
before generated resources/mappings were connected.

The footer verification gap became a reproduced patch finding: at 320 dp,
font scale 1.5 and valid Russian labels, the fixed 44.3 dp button clips
“Сделать новое фото”. The new four-case boundary class reported one failure
before changing the footer. Acceptance: natural label measurement, equal
button heights and at least 48 dp touch height, without height/width overflow.

The footer fix uses FlowRow's measured row height for both buttons, rather
than restricting text to 44.3 dp or relying on intrinsic text estimates. The
four-case boundary class subsequently passed with no failures/errors/skips.
Reference for the row sizing API: https://developer.android.com/develop/ui/compose/layouts/flow.

The inspected native Rewards image also shows differently wrapped descriptions
moving progress/state labels to different baselines within an equal-height pair.
Record this before correction: reserve flexible space above the progress/state
block so those labels line up across a pair without clipping longer descriptions.
Verify a pair with short/long descriptions before changing production.

On 2026-10-13 the new pair-alignment regression ran 2 tests with 1 failure,
then passed both after adding flexible space above progress/state. Final full
UI/quality verification remains pending expanded image integration.

## Expanded icon scope

Expand the existing 47 reusable food identities with 97 distinct common-food
icons: 21 fruit/berry, 22 vegetable, 9 grain, 9 meat, 13 fish/seafood,
14 plant-food and 9 dairy identities. Use separate transparent images in the
existing white-outline photographic style. Connect exact English/Russian
identities, including explicit plant-milk identities; never match plant milk
to dairy milk or mixed dishes to their constituent art. Check resource paths,
duplicate aliases, hashes, transparency, and actual component-only rendering.

Generation provenance is kept in separate `expanded-produce` and
`expanded-protein` fixture directories so interruptions cannot lose outputs.

Add eight separately illustrated staples to close common vegan/grain gaps:
brown rice, almonds, peanuts, walnuts, cashews, pistachios, hazelnuts and
olives. Preserve generic nuts/rice as distinct identities. Final finite
inventory is 152 food identities (47 existing plus 105 additions).

## Native shutter accessible name regression (2026-10-13)

Physical HONOR DNY-NX9, Android16/API36, sole mDNS ADB transport:
`adb-PHYSICAL_DEVICE_SERIAL-97BOdK._adb-tls-connect._tcp`. Full suite production
flavor completed36tests with1failure.
`centerCameraActionKeepsCaptureSurfaceAvailable` found resource ID
`capture_shutter` but its text/contentDescription was null instead of
`Take photo`. This is a real native accessibility result; JVM click tests
do not establish an accessible native name. Acceptance: shutter/import/close
artwork controls expose their localized names and button role through the
actual Android accessibility tree with a live native camera preview.
Production source fix and complete rerun are pending.

The run initially stalled in background controller fixtures while another
app was foreground; bringing the target activity to the foreground let
the complete production suite finish. This screenshot belongs to the other
app and is not product evidence. Remaining flavors must execute separately;
no current full-suite green result exists.

## Visual footer and original selection regression (2026-10-13)

The full integrated JVM UI run exposed an83dp normal-text footer with
FlowRow.fillMaxRowHeight: labels are measured at their minimum intrinsic
width before weights receive real widths. Normal English actions must retain
a48dp touch height and the31.1dp bottom anchor; translated larger text must
grow equally and remain unclipped.

The inspected review image also shows no checkbox for the original salmon,
broccoli, avocado or leafy-greens selections. Direct inspection establishes
that their selected PNGs contain only a green outline, not a baked checkmark.
The prior conditional omitted the overlay for those four. Acceptance: every
selected food, including original artwork, has a visible check badge bound
to its existing component ID; unselected food has none. New regression
assertions were added before the corrective source edits.

## Native controller fixture setup

Source inspection confirms each permission fixture's default Feature constructor
creates fresh catalog/dish search indexes through productionManualIngredientParser.
These15permission tests do not mutate the parser; each independently owns
its reducer/lease/session/controller state. Reuse one actual production parser
per test class, passing it through the existing constructor with mask0. This
keeps all15cases and actual production parser construction without a new
production API, dependency, skip or device permission change. The previous
production run took690.821s overall; that includes UI/background effects and
is not a measured exclusive parser benchmark.

POC installation is blocked by an older0.0.0-scaffold build from2026-09-16
with a different signing identity. The exact installed APK was saved to
/private/tmp/supernutrition-old-poc-2026-10-13.apk. A user question about
removing only this old test variant remains pending; do not uninstall before
explicit permission because its data would be lost.
