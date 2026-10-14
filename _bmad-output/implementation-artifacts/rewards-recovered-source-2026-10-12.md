# Rewards recovered visual material and test handoff

## Scope and provenance

Current branch is `codex/figma-camera-flow`; baseline and upstream main are
`5ee5de077866feed70b418ee2760af24322b1d22`. The upstream screen is the temporary
Material list. The recovered HTML and eight original PNGs come from unreachable
tree `015c5dd90c23df0c97b819cf1dab39f48441ccd0`, with no associated main commit.
The stable source record is [mobile/fixtures/rewards/README.md](../../mobile/fixtures/rewards/README.md)
and [source-manifest.json](../../mobile/fixtures/rewards/source-manifest.json).
The manifest and original HTML fixture were saved without machine-specific paths.
All eight copied production PNGs matched their original SHA256 values.

Shared Rewards now uses those illustrations, a light foliage header, naturally
measured paired cards, a balanced final odd row, and a single-column layout for
large text or narrow screens. The presenter derives the header count from actual
earned statuses and the existing localized template. The seven current domain
rules, per-card progress/state and automation IDs remain the source of truth.
No prototype XP, levels, demo rewards, or Sprout branding were added to production.

## Verification evidence and corrections

Before Rewards production edits, root ran `RewardsLayoutTest`; it failed 1/1
because the first two cards occupied different vertical positions in the old
list. After the port, the later full UI run reported 59 tests and five failures.
Its current XML reports dated 2026-10-11 UTC showed two Home geometry findings
and three Rewards assertions that still assumed individual full-width list items.

The Rewards test corrections retain behavioral checks:

- The title aligns with the left grid edge, paired cards have equal width and
  height, and their outer gutters mirror each other.
- The short-screen test scrolls to the final grid row, verifies all of the odd
  final card clears navigation, and checks it remains centered.
- The fixed 375 dp shared Compose case visits all seven cards through their
  four rows and verifies each actual progress and earned-state label.
- The ordinary-phone grid regression also displays the localized earned
  summary above the first row. Presenter assertions cover zero, some and all
  earned statuses with a non-English template.

These new summary assertions were added after implementing the new header and
are validation of that behavior, not a claimed failing-before-fix regression.
The root task owns rerunning tests, quality, native deployment, graph update and
final evidence. This worker ran no Gradle or physical-device commands and makes
no new green-build claim.

## Platform and layout handoff

The shared Compose screen supplies Android and supported shared iOS hosts.
The existing Intel-only Swift Rewards compatibility list was not changed; it
does not have this illustrated parity. Root owns the remaining Home viewport
iterations and related tests. The last intermediate phone screenshot still
showed an incomplete next-step card; increased mascot size and bottom anchoring
alone did not satisfy the full first-frame acceptance criterion.
