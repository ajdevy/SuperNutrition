# Review — E7.5 segmentation split

Adversarial review of the uncommitted E7.5 slice: `split`, the description-carrying
`SegmentationReview`, and `wordsFor`. Five lenses (criteria conformance, split
correctness, API change, test quality, shared vocabulary), then a
refute-by-default skeptic per deduplicated finding.

**15 raw findings → 9 after dedup → 3 survived, 6 refuted.**

Two of the survivors are the same defect at different lines; the dedup key is
`file:line`, so a single issue reported against `split` and against `trimmedSpan`
survived twice. Counted honestly that is **two distinct defects**.

## Survived

### F-1 — split knew one separator and the segmenter knows three (low, but it is the repo's named failure mode)

`SegmentationReview.kt:145`, `SegmentationReview.kt:173`

`TextFragments.separatorAt` treats `,` as a separator on its own, recognises a
conjunction between whitespace runs, and treats a newline as separating. `split`
shared only `isConjunction` and kept its own whitespace trimming. So the two
agreed about "and" and disagreed about ",".

Two consequences, both verified by simulating the algorithm:

- `"chicken, rice"` cut after `chicken` leaves a half whose words are `"chicken,"`
  — provenance carrying a separator, which this segmenter would never emit.
- `"chicken,rice"` contains no whitespace at all, so the word-boundary rule
  rejected every offset and the item was unsplittable — a dead end for a
  description the segmenter itself would have split into two foods.

This is exactly what AGENTS.md warns about, one level down: sharing one list and
re-implementing the rest is not sharing. **Fix:** `TextFragments` now owns
`trimSeparators` and `separatesWords`; `SegmentationReview` has no separator
knowledge of its own, and its four private whitespace helpers are deleted rather
than kept in step.

### F-2 — merge's hull can claim words another item still shows (medium)

`SegmentationReview.kt:96`

`merge` builds the replacement span as `min(start)..max(end)` and guards only
membership. Merging two items that straddle a third produces a span covering the
third item's words — while that third item is still in the split, showing the
same words.

Constructible from the test file's own fixtures: description `"two boiled eggs
and a slice of rye toast with olive oil"`, `egg(0,15)`, `bread(20,40)`,
`oil(46,55)`. Merging egg and oil yields span `(0,55)`, so `wordsFor` returns the
entire description for that item while bread still claims `"a slice of rye
toast"`. Two items cannot both have come from those words.

The verifier's correction is worth keeping: this is **pre-existing** `merge`
behaviour, not something the change introduced. A presenter reading the public
`item.sourceSpan` would have produced the same substring. What the change does is
make it the class's own contract via `wordsFor` — which is why fixing it belongs
here.

`DetectedItem` carries one span, so a non-contiguous merge cannot be represented
honestly at all. **Fix:** `merge` refuses when the hull would straddle an item
still in the split, and allows it once that item is gone — the rule is about
words another item still claims, not about distance.

## Refuted, and worth recording

- **`add`'s new span requirement blocks the CAP-7 add-back path.** Refuted: the
  KDoc states the limitation explicitly and no producer of a spanless item
  exists. Recorded as the thing to change when add-by-search arrives, rather than
  inventing a span that claims provenance the item does not have.
- **`split` drops `unmatchedPreparation`.** Refuted: both halves are new foods the
  user just chose, so preparation carried from the collapsed item would be
  asserting something about a record nobody selected.
- **The in-place-position test cannot distinguish prepending.** Refuted as
  written — it splits `items[0]` — but the point had already been covered, since
  the halves' spans are asserted in order against the untouched third item.
- **The confirmation-dropping assertion is vacuous.** Refuted: `confirmedItems`
  filters by membership, so an empty result after a split that kept the
  confirmation would still fail once the original is gone from `items`.
- **`isConjunction` is the wrong sharing boundary; it should be a
  `TextFragments`-owned span trim.** Refuted at its own line, but this is F-1 said
  a third time and it was right. Acted on.
