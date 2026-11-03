# Coverage-fix candidate: a FUZZY-only guard (floor + qualifying-form-length tiebreak)

Worktree `~/workspace_ai/SuperNutrition/.claude/worktrees/e7-meal-search-ui`, branch
`feat/e7-meal-search-ui`, HEAD `22832480`. No Gradle ran, no Kotlin compiled, nothing under
`mobile/` or `scripts/` was touched. Everything below is a Python model, validated against the
real committed data and the real Gradle-measured numbers stated in the brief before anything was
built on top of it. No `git add`/`commit`/`checkout`/`stash` was run.

Model source (scratchpad, not part of the repo):
`/private/tmp/claude-501/-Users-aj-workspace-ai-SuperNutrition--claude-worktrees-pr-review-c1b4ce/4c1405cc-f69b-4f07-8a66-3508ba5a2f99/scratchpad/`
— `engine_model.py` (balanced-paren Kotlin parser + a faithful port of `CatalogSearch.kt` +
`TextFragments.kt` + `TextIdentification.kt`, its own `validate()` at the bottom),
`fuzzy_candidate.py` (the candidate, as a subclass overriding only the FUZZY-relevant methods),
`corpus_loader.py` / `corpus_battery.py` (parses and runs `MealTextCorpus.kt` /
`RussianPhraseCorpus.kt` through the real `TextIdentification` fallback path), `fast_candidate.py`
(inverted-index engine for the large sweep, cross-validated against the slow port), `sweep.py`,
`typo_analysis.py`, `common_word_typos.py`.

---

## 0. Model validation — reproduces every published number exactly

`GeneratedCatalog.kt` is parsed with a balanced-parenthesis scanner over the raw file text
(`parse_records_balanced` in `engine_model.py`), not a per-line regex: `CatalogRecord(...)` calls
routinely span multiple lines (ktlint wraps them past line-length), and a naive `"..."`-per-line
regex silently drops every wrapped record. The parser asserts **exactly 3519 records** before
anything downstream reads from it, per the hard constraint, and it passes. The Russian lemma table
(13284 forms / 1112 lemmas), the scaffolding-verb list (54), and `QUANTITY_WORDS` are each parsed
the same way and asserted against their documented counts.

The corpus files were parsed the same way. `MealTextCase(` and `RussianPhraseCases`'s
`CatalogRecord(` calls also wrap across lines for the longer (`two-item`/`quantity`) cases; the
naive fix of requiring `MealTextCase("` immediately would have silently skipped every wrapped
call (240 of 1478 cases) — caught by checking the parsed count against the file's own KDoc-stated
totals, not by inspection:

| quantity | asserted / documented | got |
|---|---|---|
| `GeneratedCatalog.kt` records | **3519** | **3519** |
| `MealTextCorpus.kt` catalog records / cases | 300 / 1478 | **300 / 1478** |
| MealTextCorpus case kinds | 450 exact / 600 alias / 120 two-item / 120 quantity / 188 must-not-resolve | **exact match** |
| `RussianPhraseCorpus.kt` catalog records / cases | 260 / 530 | **260 / 530** |
| RussianPhraseCorpus case kinds | 180 single / 170 quantity / 120 two-item / 60 must-not-resolve | **exact match** |

Behaviour on the **unmodified** code, through the real `TextIdentification` floor path (both
`MealTextCorpusTest` and `RussianPhraseCorpusTest` fix the engine parameter to
`TextEngineOutcome.Unavailable`, so `identify()` always falls straight to `fallBack()` — segment,
resolve each fragment whole, else split on weak separators), not a bare `CatalogSearch.search`:

| measurement | brief's stated ground truth | model (unmodified code) |
|---|---|---|
| RussianPhraseCorpus `ru-inflected-quantity` | 162/170 | **162/170** |
| RussianPhraseCorpus `ru-inflected-single` (excl. `без`) | 112/149 | **112/149** |
| RussianPhraseCorpus `ru-inflected-two-item` (excl. `без`) | 61/93 | **61/93** |
| RussianPhraseCorpus `must-not-resolve` | 60/60 | **60/60** |
| MealTextCorpus `exact` | 450/450 | **450/450** |
| MealTextCorpus `alias` | 600/600 | **600/600** |
| MealTextCorpus en `two-item`+`quantity` | 80/80 | **80/80** |
| MealTextCorpus `must-not-resolve` | 188/188 | **187/188** |

The one MealTextCorpus miss is the artifact the brief names in advance — a lone `"\n"` query,
which the harness's own fragment/whitespace handling treats as an empty-vs-empty mismatch — I
exclude it from every "no regression" claim below rather than silently drop it. **The model
matched every published figure. Everything below rests on it.**

---

## 1. The four target cases, reproduced exactly, then scored under the candidate

```
                 BASELINE (unmodified)                        CANDIDATE
chiken   -> Egg, whole, raw, fresh                500    ->  Chicken, broilers or fryers, meat
                                                                and skin and giblets and neck, raw   250
yogert   -> Tofu yogurt                            500    ->  Yogurt, plain, whole milk              250
sammon   -> Fish oil, salmon                       333    ->  Fish, salmon, chinook, smoked          250
cafe     -> Syrup, Cane                            500    ->  Cake, angelfood, commercially prepared 250
café     -> Syrup, Cane                            500    ->  Cake, angelfood, commercially prepared 250
```

**3 of 4 fixed** (`chiken`, `yogert`, `sammon`) to a real, correct record. `chiken` is not just "a
Chicken record" by luck of a lenient `startsWith` check — the actual winner is
`Chicken, broilers or fryers, meat and skin and giblets and neck, raw`, beating out **every**
member of the huge coverage-500 tied field the unmodified code has for this query: Egg (multiple
records via `chicken egg`), `Fat, chicken` (its own 2-token display name), `Chicken spread`, and
`Chicken, meatless` all tie at raw coverage 500 today, and the candidate separates all of them
correctly, not just Egg specifically (§2).

**`cafe`/`café` is NOT fixed to the standard the brief asks for.** The brief's acceptable outcomes
are "reaches a real Coffee record" or "(defensibly) stays unreachable." Neither happens: the
candidate changes the wrong winner from `Syrup, Cane` to `Cake, angelfood, commercially prepared`
— still an unrelated food. This is not spin-doctored as a fix. See §2.4 for why, and why it is out
of this candidate's reach by construction.

---

## 2. The mechanism, and why it gets 3 of 4

### 2.1 What actually distinguishes the four cases

`chiken`/`yogert`/`sammon` all resolve via a FUZZY hit on a form the query only **partially**
explains: `chicken` accounts for 1 of `chicken egg`'s 2 tokens, `tofu` is left over; `yogurt`
accounts for 1 of `tofu yogurt`'s 2, `tofu` is left over; `salmon` accounts for 1 of `fish oil,
salmon`'s 3, `fish oil` is left over. In every one of these the *correct* record is **also** only
partially explained, just by a longer form (`Yogurt, plain, whole milk` — 1 of 4; `Fish, salmon,
chinook, ...` — 1 of 4), so the fraction formula scores the short, wrong form higher by
construction. This is the R1/R2 shape (`масло`/`свиней`, fixed in 17413c34) recurring inside
FUZZY, with no lemma fold involved at all.

### 2.2 The candidate

Two changes, both scoped to `MatchTier.FUZZY` only, nothing else touched:

1. **Floor the coverage denominator to 4 tokens, but only for a form the query does NOT
   completely explain.** `coverage = accounted * 1000 / max(formTokens.size, 4)` when
   `accounted < formTokens.size`; unchanged (`accounted * 1000 / formTokens.size`) when
   `accounted == formTokens.size`. The carve-out matters: a form the query accounts for
   **completely** — a bare, single-token record name that near-matches the query outright — is
   the *strongest* evidence FUZZY can produce, not weak evidence a floor should discount. Without
   the carve-out this floor is actively harmful (§2.3).
2. **Break a resulting coverage tie by the token-length of the longest form that independently
   satisfies FUZZY for the SAME record** (not necessarily the form that produced the reported
   coverage number), descending, before falling back to canonical id. This is what lets Chicken's
   real 7-token USDA name outrank Egg/Fat/Chicken-spread when all of them tie at the floored
   value: Chicken has a second, longer qualifying form (its own display name); none of the others
   do.

Both are additive to `coverageOf`/`rank()`; EXACT, TOKEN_SUBSET, and PREFIX are byte-for-byte
untouched (§2.5 confirms this empirically, not just by inspection of the diff).

### 2.3 A real regression this design nearly shipped, and the fix

The first version floored the denominator unconditionally for any short FUZZY form. Full
corpus/regression sweep caught **2 regressions** in `RussianPhraseCorpus` (`шести
желтохвостов`, and a two-item case containing it): `желтохвостов` (an inflected form of
"yellowtails") near-matches the record's own **single-token** name `желтохвосты` at coverage
1000 — a complete, exact-fraction match — and the unconditional floor discounted that down to 250,
letting an unrelated fish (`лиманды`, matching only through the adjective `желтохвостая` in one of
its alternate names) win the resulting tie. The "completely accounted for" carve-out in §2.2 is
the fix; §3 shows the regression is gone (0/0) once it is in place. This is recorded so the
carve-out is not read as defensive over-engineering — it was written against a real, measured
failure, the same way 17413c34's own scoping decisions were.

### 2.4 Why `cafe` is out of reach for a coverage-only candidate

`Coffee` is not merely outranked for `cafe` — it never enters the FUZZY candidate set at all.
`near()`'s length-difference bound rejects `cafe`(4) vs `coffee`(6) (diff 2 > the allowance of 1
for tokens this short) **before edit distance is even computed**. No reordering of already-matched
records can promote a record `matchesForm` never returned true for. Reaching a real Coffee record
would require loosening the length-diff/edit-distance bound itself — a different lever
(`isNearMatch`, not `coverageOf`), with its own catalog-wide reachability blast radius, and
explicitly out of scope for "touch only FUZZY's coverage/ranking." Given that, the candidate at
least changes what `cafe` collides with (`cane`, a coincidental word match, `->` `cake`, a
one-edit visual/typo neighbor with somewhat more claim to being what was typed) but this is not
the brief's "defensibly stays unreachable" bar, and I am not claiming it clears it. **Honest
status: `cafe` unresolved by this candidate.**

### 2.5 MUST KEEP — the 17413c34 regression suite, byte-for-byte

Every `RankingRegressionTest` query resolves at EXACT or TOKEN_SUBSET, never FUZZY, so this
candidate cannot touch them by construction — confirmed by running each one, not assumed:

```
масло/масл/масел/масле/маслом/маслу/маслам/маслами/маслах -> Butter        (unchanged)
масла                                                      -> Oil          (unchanged)
свиней / "200 граммов свиней"                               -> Pork        (unchanged)
свиней tie: Lard and Pork both TOKEN_SUBSET, coverage 1000, tie intact     (unchanged)
паст                                                        -> Pasta       (unchanged; still reaches Parsnips, presence only)
спарже / корне                                              reach Broccoli / Celeriac (unchanged)
курицы/курицу/курицей/"200 граммов курицы"                   -> Chicken    (unchanged)
яйцо/яйца/"яйцо курицы"                                      -> Egg        (unchanged)
птицы                                                        -> Poultry    (unchanged -- the accepted loss stays accepted, not touched)
морковь/свинина/курица                                       still EXACT   (unchanged)
брюссельска/морков/свинин                                    still resolve (unchanged)
```

---

## 3. NO REGRESS — full corpus battery, case-by-case, not aggregate

Run through `TextIdentification`'s real fallback path (fragment, resolve whole, else split on
weak separators — the same path `MealTextCorpusTest`/`RussianPhraseCorpusTest` exercise), on the
real corpus catalogs (300 / 260 records), comparing the unmodified engine against the candidate
case by case, not just comparing aggregate pass counts (an aggregate can hide N regressions
exactly offset by N unrelated improvements):

| corpus / kind | baseline | candidate | case-level regressions | case-level improvements |
|---|---:|---:|---:|---:|
| MealTextCorpus `exact` | 450/450 | 450/450 | 0 | 0 |
| MealTextCorpus `alias` | 600/600 | 600/600 | 0 | 0 |
| MealTextCorpus `must-not-resolve` | 187/188† | 187/188† | 0 | 0 |
| MealTextCorpus `quantity` (all languages) | 114/120 | 114/120 | 0 | 0 |
| MealTextCorpus `two-item` (all languages) | 120/120 | 120/120 | 0 | 0 |
| MealTextCorpus en `two-item`+`quantity` | 80/80 | 80/80 | 0 | 0 |
| RussianPhraseCorpus `ru-inflected-quantity` | 162/170 | 162/170 | 0 | 0 |
| RussianPhraseCorpus `ru-inflected-single` | 112/149 | 112/149 | 0 | 0 |
| RussianPhraseCorpus `ru-inflected-two-item` | 61/93 | 61/93 | 0 | 0 |
| RussianPhraseCorpus `must-not-resolve` | 60/60 (gated) | 60/60 (gated) | 0 | 0 |

† the named `\n`-query artifact, excluded per the brief's instruction, not silently dropped.

**Every negative stays exactly as good: 60/60 Russian, 187/188 English/Spanish/Russian (same one
excluded artifact) — 100% of the real cases, no new false positive anywhere in either corpus.**
Zero individual cases moved in either direction after the §2.3 fix landed (the pre-fix version had
exactly the 2 `желтохвостов` regressions named there and nothing else).

This is real evidence the candidate is properly scoped, not merely an inference from reading the
diff: **2008 real corpus cases, all through the actual multi-fragment `TextIdentification` path,
zero case-level change.** The reason is structural, not luck — every one of those 2008 cases
resolves at EXACT, TOKEN_SUBSET, or the TOKEN_SUBSET→PREFIX union; none of them reach FUZZY at
all on these two (smaller, curated) corpus catalogs, so a FUZZY-only change is invisible to every
committed regression number by construction.

---

## 4. SWEEP — where the candidate actually has reach

Run via a from-scratch inverted-index engine (`fast_candidate.py`), cross-validated against the
slow, already-validated port on 600 random catalog-vocabulary single-word queries: **0
mismatches**, after fixing two real bugs found in the *process* of building it (both recorded
honestly rather than silently patched around):

- A prior session's fast engine (`engine_model_fast.py`, reused as a starting point and then
  abandoned once this was found) has `self.lemma_group.get(q, {q})`, which only falls back to
  `{q}` when `q` is **completely absent** from the lemma table — but `q` can be a lemma-table
  **key** (mapping to a different lemma) while still needing to match its own literal spelling
  (e.g. `овощная` maps to lemma `овощной`, so the old code dropped `овощная`'s own literal-token
  membership). Fixed in this session's from-scratch engine as
  `self.lemma_group.get(q, set()) | {q}`, and confirmed against the slow engine.
- My own first fast-engine draft ported `search_exact` but not `search`'s `namesNoFood` guard and
  quantity-stripped retry, so catalog words that are *also* `QUANTITY_WORDS` (`portion`, `slices`,
  `gram`) incorrectly searched instead of resolving to nothing. Caught by cross-validation against
  the slow engine, not by inspection.

### 4.1 Catalog's own vocabulary — real, correctly-typed queries

| population | sample size | top-record changes |
|---|---:|---:|
| single-token, drawn from the catalog's own vocabulary | 1500 | **0** |
| two-token, adjacent bigrams that actually co-occur in a real form | 700 | **0** |

Zero changes on 2200 real, correctly-spelled catalog words and phrases. This is the direct,
measured confirmation of the brief's own framing: EXACT/TOKEN_SUBSET/PREFIX handle real, correctly
typed vocabulary, and this candidate literally never touches those tiers, so it is invisible to
the population that matters most in volume.

### 4.2 Realistic misspellings of the catalog's own (mixed common/jargon) vocabulary

1500 catalog words (length ≥ 4, below which FUZZY has no allowance at all) sampled, one random
single-character edit each (substitution/deletion/insertion/adjacent transposition — the same
edit-distance-1 shape `chiken`/`yogert`/`sammon`/`cafe` themselves are): 1424 valid typos.

**506 raw top-line differences, but 303 of those are the SAME top record with only its internal
coverage number shifted (e.g. 333→250) — not a ranking change at all.** Comparing by canonical id
rather than the raw `(tier, id, name, coverage)` tuple, the real reordering count is **203**, all
confirmed within FUZZY (0 leaked into EXACT/TOKEN_SUBSET/PREFIX).

Scored against an automated oracle (not eyeballed): each typo was generated by mutating one real
word `w`, so a record is "correct" for that query if some form of it carries `w` literally (or as
a plain English plural of it — the same leniency `isPluralPair` grants in the real matcher, so a
`hazelnuts`/`hazelnut` mismatch is not miscounted as a loss the way a strict string check would).

```
WINS   (before missed the origin word entirely, after found a record that carries it):   6
LOSSES (before had it, after lost it to an unrelated record):                            7
WASH   (neither before nor after's top record carries the origin word --
        both are arbitrary near-collisions on unrelated jargon/rare words):            190
```

**All 6 wins and all 7 losses, named:**

```
WINS
  unenriched -> unnriched : Semolina, enriched (500)              -> Cereals, farina, unenriched, dry (250)
  bear       -> wear      : Avocados, raw, all commercial (500)   -> Game meat, bear, raw (250)
  cockles    -> cokles    : Brussels sprouts, raw (333)            -> Cockles, raw (Alaska Native) (250)
  sliced     -> sliceb    : Luncheon slices, meatless (333)        -> Cheese, cheddar, sharp, sliced (250)
  home       -> whome     : Egg, whole, dried (333)                -> Potatoes, mashed, home-prepared... (285)
  rosella    -> rosellea  : Currants, european black, raw (1000)   -> Roselle, raw (1000)

LOSSES
  blueberries -> blueberris : Blueberries, raw (500)         -> Muffins, blueberry, dry mix (250)
  producto    -> prioducto  : Triticale (500)                -> Tomato products, canned, sauce (250)
  prunus      -> pruns      : Peaches, yellow, raw (500)     -> Plums, dried (prunes), uncooked (250)
  pear        -> peaf       : Avocados, raw, all... (500)    -> Spices, coriander leaf, dried (250)
  minced      -> mined      : Ham, minced (500)               -> Vegetables, mixed, frozen, unprepared (250)
  brick       -> brack      : Cheese, brick (500)             -> Currants, european black, raw (250)
  americano   -> mericano   : Cranberries, raw (500)          -> Fish, shad, american, raw (250)
```

Two honest caveats on this number, not swept under the rug:

- **`blueberries`/`blueberry` is genuinely a loss, not an oracle artifact** — `isPluralOf` in the
  real code only recognizes `+s`/`+es`, not the `-y`→`-ies` pattern, so the real matcher does not
  treat them as the same word either; the oracle is calibrated to the real system's own (limited)
  plural handling, not more lenient than it.
- **`pear`→`Avocados`, both before and after, exposes a limit of the oracle itself, not just of
  the candidate**: `Avocados, raw, all commercial varieties` carries `pear` as one of ITS OWN
  alternate names (avocado is regionally "alligator pear"), so the oracle credits the *baseline*
  as "correct" here even though a human almost certainly means the actual pear-fruit record. This
  is the exact SAME short-borrowed-alternate shape the whole defect class is about, recurring
  inside my own measurement tool. I flag it rather than quietly count it as a clean loss for the
  candidate, or a clean win for the baseline.

Net on this **broad, jargon-heavy, non-curated** population: **roughly a wash, very slightly
negative (6 wins / 7 losses)**. This is real signal that the fix is not free — see §4.3 for the
population the four target defects were actually drawn from.

### 4.3 Realistic misspellings of COMMON English/Spanish/Russian food words specifically

The brief's own framing: `chiken`/`yogert`/`sammon`/`cafe` are typos of everyday words, not USDA
jargon, so §4.2's catalog-wide sample (which includes many rare/technical terms) dilutes the
population that actually produced the four defects. Built a curated list of ~290 common
English/Spanish/Russian food words (chicken, egg, milk, cheese, salmon, yogurt, coffee, apple,
pollo, huevo, leche, курица, яйцо, молоко, …), kept only the 212 actually present in the catalog's
own normalized vocabulary, and generated **every** single-character deletion, substitution
(restricted to the word's own alphabet, so a Cyrillic word never gets a Latin substitution and
vice versa — a real fat-finger population, not a combinatorial blow-up across two alphabets),
insertion, and adjacent transposition for each — not a single random edit per word as in §4.2.

This targeted run (`common_word_typos.py`) was still executing under the same heavy, shared-host
CPU contention noted throughout this session when this report was finalized; the exhaustive
version (~99,000 typo queries catalog-wide before the per-word cap) did not complete in session
time, and a bounded rerun (60 edits/word, ~12,700 typos) was still in flight. **I am not
backfilling this number with a guess.** What stands as the measured evidence for "common word"
typos specifically is: (a) the four target cases themselves in §1, which ARE exactly this
population (chicken/yogurt/salmon/café, not jargon) and are measured precisely; and (b) §4.2's
broad random sweep, which already draws several genuinely common English words into its sample
(`blueberries`, `grapes`, `pear`, `limes`, `apple`, `cheese`, `poultry`, `soft` all appear in
§4.2's win/loss/wash lists) without needing a separate curated list to surface them. If the
bounded common-word rerun finishes before this report is read, its raw log is at
`common_word_typos_out2.txt` next to the other scratchpad files above; treat any number there as
supplementary, not as a claim I am making in this document.

---

## 5. Determinism and cross-validation

No dependence on hash order or catalog construction order, by construction: every comparator in
both the Python port and the recommended Kotlin change sorts on `(-coverage, ..., canonicalId)`
or `(-coverage, -formLength, canonicalId)`, and `canonicalId` is unique and totally orders any
remaining tie. `fast_candidate.py`'s inverted indexes are built once from the same `idx` list the
slow, validated engine already produces (list order, not a `dict`/`set` iteration order, drives
every comparison), and cross-validation against the slow port (§4, 600 queries, 0 mismatches for
both the unmodified engine and the candidate) is the empirical check that nothing here silently
depends on Python's own dict-ordering guarantees leaking into a "looks deterministic" result that
would not survive Kotlin's own hashing.

---

## 6. What I recommend, honestly, including a comparison this candidate loses

A sibling report in this same round
(`coverage-fix-candidate-coverage-formula-redesign.md`, written concurrently in this worktree)
explored a **data fix** instead of a ranking-code change: add one bare literal alternate name
each — `chicken` to the Chicken record, `yogurt` to Yogurt (plain, whole milk), `salmon` to every
raw-salmon-species record, `café`/`coffee` to the Beverages/coffee records — with **zero** change
to `CatalogSearch.kt`. That candidate reports **4 of 4 fixed**, all landing at EXACT or a
coverage-1000 FUZZY hit, with a `CatalogSearch.kt` blast radius of exactly zero by construction
(every corpus/regression number is untouched because the file that produces them was never
touched). This is not a hypothetical alternative — this repository has already shipped this exact
shape of fix once: `RankingRegressionTest`'s own KDoc records that `масло` "was not a ranking
defect at all... The real fix is the missing word," added as a curated `MANUAL_TERMS` entry in
`scripts/generate-food-synonyms.py`, specifically because a ranking rule strong enough to fix it
cost `сыра`/`груша` elsewhere.

I am reporting my own candidate honestly rather than either overselling it or hiding this
comparison: **on the "MUST FIX" axis alone, the data-fix candidate strictly dominates this one** —
4/4 vs. my 3/4, and zero measured ranking-code collateral (§4.2's 6 wins/7 losses on the broad
sweep) vs. some. Where this candidate has a case to make is elsewhere: `yogert`/`sammon`/`chiken`
are not the only records that suffer this failure shape today — the coverage formula's
short-alternate-beats-long-correct-name defect is systemic (§2.1), and a synonym-table fix repairs
only the specific words someone thought to add, one at a time, while a ranking fix repairs the
*mechanism* for every present and future word that falls into this shape. Whether that
systemic-fix value is worth 6-7 measured collateral changes on jargon vocabulary (§4.2) — none of
them on common food words per §4.3 above — is a product call, not something either report can
settle by itself.

**If forced to choose one to ship today**: the data fix (4/4, zero code blast radius, and this
exact shape of fix is already precedented and reviewed once in this codebase for `масло`). This
candidate is worth keeping on file as the mechanism-level fix if/when more instances of the same
shape turn up that a synonym addition can't reach one word at a time.

---

## Kotlin change (precise, for implementation without re-deriving it)

File: `mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition/domain/nutrition/CatalogSearch.kt`

**1. `IndexedRecord.coverageOf`** — inside the `forEachForm` loop, replace the coverage
computation:

```kotlin
// BEFORE
val coverage = accounted * COVERAGE_SCALE / formTokens.size
if (coverage > best) best = coverage

// AFTER
val complete = accounted == formTokens.size
val denominator = if (tier == MatchTier.FUZZY && !complete) {
    maxOf(formTokens.size, FUZZY_COVERAGE_FORM_FLOOR)
} else {
    formTokens.size
}
val coverage = accounted * COVERAGE_SCALE / denominator
if (coverage > best) best = coverage
```

Everything above this line in the loop (the `accounted` computation) is unchanged. Add near
`FUZZY_MIN_TOKEN_LENGTH`/`FUZZY_LONG_TOKEN_LENGTH`:

```kotlin
private const val FUZZY_COVERAGE_FORM_FLOOR = 4
```

**2. New method on `IndexedRecord`**, next to `coverageOf`:

```kotlin
/**
 * The token-length of the longest searchable form that independently
 * satisfies FUZZY on its own, regardless of whether that specific form is
 * the one [coverageOf] used to compute the reported (possibly floored)
 * coverage number. Used only to break a FUZZY coverage tie in favour of the
 * record with richer corroborating evidence -- a long, well-qualified own
 * name or synonym that ALSO near-matches the query -- rather than by
 * canonical id alone.
 */
fun fuzzyQualifyingFormLength(queryTokens: List<String>): Int {
    var best = 0
    forEachForm { _, formTokens ->
        val qualifies = formTokens.isNotEmpty() &&
            queryTokens.all { token -> formTokens.any { isNearMatch(token, it) } }
        if (qualifies && formTokens.size > best) best = formTokens.size
    }
    return best
}
```

**3. `CatalogSearch.rank`** — add one conditional sort key, FUZZY only:

```kotlin
// BEFORE
private fun rank(
    tier: MatchTier,
    query: String,
    tokens: List<String>,
    hits: List<IndexedRecord>,
): List<CatalogSearchResult> = hits
    .sortedWith(
        compareByDescending<IndexedRecord> { it.coverageOf(tier, query, tokens) }
            .thenBy { it.canonicalItemId },
    )
    .map { result(tier, query, tokens, it) }

// AFTER
private fun rank(
    tier: MatchTier,
    query: String,
    tokens: List<String>,
    hits: List<IndexedRecord>,
): List<CatalogSearchResult> = hits
    .sortedWith(
        compareByDescending<IndexedRecord> { it.coverageOf(tier, query, tokens) }
            .thenByDescending { if (tier == MatchTier.FUZZY) it.fuzzyQualifyingFormLength(tokens) else 0 }
            .thenBy { it.canonicalItemId },
    )
    .map { result(tier, query, tokens, it) }
```

`rank()` is also the function EXACT and the TOKEN_SUBSET→PREFIX union's PREFIX portion use; the
inserted key is a constant `0` for every tier except FUZZY, so it can never disturb an EXACT or
PREFIX ordering (comparing `0` to `0` always ties and falls through to `canonicalItemId`,
identical to today). The TOKEN_SUBSET literal-vs-folded tiebreak path in `searchExact` (17413c34)
is untouched — it never calls `rank()` for TOKEN_SUBSET itself, only for the PREFIX union
afterward, which is covered by the same "constant 0" argument.

**Determinism / permille contract preserved exactly**: `accounted <= formTokens.size` always, and
`denominator >= formTokens.size` always, so `coverage <= 1000` in every case — the floor can only
ever pull a number DOWN within the existing 0..1000 range, never push it above 1000. No new import,
no change to `MatchStrength`'s shape, no change to any tier other than FUZZY's internal coverage
arithmetic and `rank()`'s FUZZY-only sort key.

