# Coverage fix decision: chiken / yogert / sammon / cafe

**Author:** independent judge subagent, not one of the four candidate sessions.
**Scope:** decide what the main session should do about the four named FUZZY-tier
coverage defects in `CatalogSearch.kt`'s `IndexedRecord.coverageOf`, after building
and validating my own Python model of the engine and independently re-measuring
the two live candidates rather than taking any of the four prior reports on faith.

**Bottom line up front:** ship a **curated per-record data fix** (a new small,
reviewed, dated override table that adds four literal bare words to specific
records — the exact shape already precedented by масло's fix), not a ranking-code
change. All four ranking-only candidates explored this round converge on the same
conclusion their own authors reached: no reweighting of the coverage formula fixes
more than 3 of 4 without cost, and even the best of them (a FUZZY-tier-only floor +
tiebreak) leaves a **more important, currently-shipping defect untouched**: the
plain, correctly-spelled words `chicken`, `yogurt`, and `salmon` — no typo at all —
**already return the wrong food today**, because they fail at `TOKEN_SUBSET`, a
tier no FUZZY-scoped ranking candidate can reach. That finding is not in any of the
four candidate reports as a decision-relevant comparison, and it is the strongest
reason to fix this as data, not ranking.

---

## 1. Method: build my own model, validate before trusting it

I did not reuse any prior session's `engine_model.py`/parser (several exist in this
worktree's shared scratchpad from the four candidate sessions; I did not read or
adapt them). I wrote a fresh Python parser and engine port, saved under
`/private/tmp/claude-501/.../scratchpad/cfd_*.py` (`cfd_kparse.py`, `cfd_build_data.py`,
`cfd_engine.py`, `cfd_validate.py`, `cfd_defects.py`, `cfd_datafix.py`, `cfd_sweep.py`).

**Parser correctness, asserted before reading anything from the data:**
- `GeneratedCatalog.kt`: a balanced-parenthesis, string-literal-aware parser
  (not a line regex, not BSD-grep `\b`) found **exactly 3519** `CatalogRecord(...)`
  calls. Verified the naive "does this line's parens balance" check independently
  flags 460 of them as spanning multiple lines (e.g. long qualified USDA names with
  an inline `listOf(...)`) — confirming the task's warning about a naive parser
  silently parsing a fraction of the file, and confirming my parser is not naive.
- `MealTextCorpus.kt`: 300 `corpusCatalog` records, 1478 `corpusCases`.
- `RussianPhraseCorpus.kt`: 260 `russianPhraseCatalog` records, 530 `russianPhraseCases`.
- `GeneratedRussianForms.kt`: 13284 form→lemma entries over 1112 distinct lemmas.
- `GeneratedRussianScaffolding.kt`: 54 scaffolding surface forms.

All five counts match the task's stated ground truth exactly (`cfd_build_data.py` asserts
every one; a failed assertion aborts before anything downstream reads from it).

**Engine port**: a line-by-line translation of `CatalogSearch.kt` (`normalizeForSearch`,
`tokenizeNormalized`, `isNearMatch`/`editDistance`/`isPluralPair`, `sharesLemma`,
`coverageOf`, the four-tier `searchExact` loop including the TOKEN_SUBSET
literal-vs-folded tiebreak and the PREFIX-union branch), plus `TextFragments.kt`
(`of`, `splitOnWeakSeparators`, the separator/conjunction machinery) and
`TextIdentification.kt`'s floor path (engine fixed to `Unavailable`, exactly as
every real Kotlin test in this repo runs it). Every function is a direct port with
no shortcuts; tokenization uses per-character `str.isalnum()` (Unicode-aware, not
a regex `\b`), so the Cyrillic pitfall the task warned about does not apply here.

### Validation against real ground truth (before building anything on it)

Ran the unmodified port against the exact suites named in the task:

| Suite | My result | Task's stated ground truth | Match |
|---|---|---|---|
| `RankingRegressionTest`-equivalent (12 assertions, real 3519-record catalog) | **ALL PASS** | all pass (shipped state) | ✅ |
| MealTextCorpus exact | 450/450 | 450/450 | ✅ |
| MealTextCorpus alias | 600/600 | 600/600 | ✅ |
| MealTextCorpus en two-item+quantity | 80/80 | 80/80 | ✅ |
| MealTextCorpus negatives | **188/188** | 188/188 (one is a parsing artifact) | ✅ (see below) |
| RussianPhraseCorpus quantity | 162/170 | 162/170 | ✅ |
| RussianPhraseCorpus single | 112/149 | 112/149 | ✅ |
| RussianPhraseCorpus two-item | 61/93 | 61/93 | ✅ |
| RussianPhraseCorpus negatives | 60/60 | 60/60 | ✅ |

Every number matches exactly. One artifact worth naming precisely: my first parser
draft scored MealTextCorpus negatives 187/188 (missing the single query that is a
literal `"\n"` in the Kotlin source). My literal-string parser was decoding the
Kotlin escape `\n` as the two characters `n`, not a real newline, which then went
through the full search pipeline instead of hitting `TextIdentification`'s
`isBlank()` short-circuit — producing a spurious PREFIX-tier hit (a bare `"n"`
prefixes many record tokens: "nuts", "no-salt", etc.) instead of the correct empty
result. I fixed the escape-sequence handling in my own string-literal parser
(`\n`, `\t`, `\r`, `\"`, `\\` now map to real characters), re-ran, and the number
became **188/188** — i.e., the real engine's behavior on that case is correct
(`isBlank()` catches a true newline before any tier runs); the "known parsing
artifact" is specifically a **parser** artifact in a naive Python simulation, not
a matcher defect, exactly as the task's brief flagged. I did not need to exclude
anything — fixing the parser reproduced the true number.

This is as strong a validation as a Python port can get without a Gradle run: every
number the task supplied, across two independent corpora and the direct regression
suite, reproduces exactly.

---

## 2. The four target defects, reproduced on the real 3519-record catalog

```
chiken  -> Egg, whole, raw, fresh                    FUZZY  500   (Chicken, broilers or fryers,
                                                                    meat and skin and giblets and
                                                                    neck, raw ALSO scores 500 --
                                                                    genuine tie, broken by
                                                                    canonical id: usda-srl-01123
                                                                    sorts before usda-srl-05001)
yogert  -> Tofu yogurt                               FUZZY  500   (Yogurt, plain, whole milk: 250)
sammon  -> Fish oil, salmon                          FUZZY  333   (Fish, salmon, chinook, raw: 250)
cafe    -> Syrup, Cane                                FUZZY  500   (no Coffee record reachable --
café    -> Syrup, Cane                                FUZZY  500    cafe/café(4) vs coffee(6) fails
                                                                    the FUZZY length-diff bound)
```

All four numbers match the task's stated ground truth exactly, including the
tie mechanism for `chiken` (both Egg and the real Chicken record score FUZZY 500;
Chicken has no bare single-token English form because its shortest form containing
"chicken" as a token is the 12-token qualified USDA name itself).

---

## 3. A finding none of the four candidate reports made explicit: this is not 4 isolated typos

I checked what the plain, **correctly spelled** bare words resolve to today, with
no typo involved at all:

```
"chicken"  (BASELINE, no typo) -> TOKEN_SUBSET 500  Egg, whole, raw, fresh
"yogurt"   (BASELINE, no typo) -> TOKEN_SUBSET 500  Tofu yogurt
"salmon"   (BASELINE, no typo) -> TOKEN_SUBSET 333  Fish oil, salmon
```

**Today, typing the single word "chicken" into search — spelled perfectly — returns
Egg as the #1 result on the shipped code.** Same for "yogurt" (returns Tofu yogurt)
and "salmon" (returns the fish-oil byproduct). This is the identical failure
mechanism as the four named FUZZY-tier defects (a qualified multi-token display
name scores a low coverage fraction; a short, unrelated alternate name scores a
high one) — it just arrives via `TOKEN_SUBSET` instead of `FUZZY`, because the
literal word "chicken" is already a token of Chicken's own 12-token display name
(so `TOKEN_SUBSET` matches it directly, no fold needed), while Egg's alternate
"chicken egg" is a 2-token form giving coverage 500 against Chicken's 1/12≈83.

This matters directly for judging the candidates: **any fix scoped to the FUZZY
tier only — which is how every "ranking" candidate this round deliberately scoped
itself, precisely to avoid the larger blast radius that sank the LEMMA-tier and
form-multiplicity candidates earlier this session — cannot touch this case at all.**
I confirmed this by direct measurement (§5). A fix that only reaches misspellings
addresses a strictly smaller and, arguably, less important slice of the real
problem than the plain-spelling case.

I also confirmed (independently, not from any candidate report) that this is a
**broader, pre-existing pattern**, not limited to these three words. Bare,
correctly-spelled catalog-vocabulary words that resolve to a wrong or
oddly-specific food today, via the identical mechanism:

```
rice       -> Rice crackers                              (should be plain rice)
poultry    -> Spices, poultry seasoning                  (should be a poultry meat record)
chocolate  -> Beverages, chocolate syrup                 (should be plain chocolate/cocoa)
popcorn    -> Snacks, popcorn, cakes                     (should be plain popcorn)
```
This is not confined to bare single words either — I checked a realistic,
everyday two-word query and confirmed the same defect surfaces through
`searchRelaxingPreparation`: **`"grilled chicken"` resolves to `Egg, whole, raw,
fresh` on the shipped code today.** `"grilled"` is a recognized preparation word
(`PREPARATION_WORDS`), so the food-only fallback re-runs `searchExact("chicken")`
after stripping it — hitting the identical bare-`"chicken"`-loses-to-Egg case from
inside a query a real user would plausibly type. The curated data fix (§5b) also
fixes this compound case, for the same reason it fixes the bare word: confirmed
`"grilled chicken"` resolves to `Chicken, broilers or fryers, ...` (EXACT 1000,
`"grilled"` reported as unmatched preparation) once the fix is applied.

while some other bare heads happen to already resolve fine (`corn`, `leeks`,
`artichokes`, `tea`, `pepper` all land on a reasonable record of themselves). This
is consistent with — and independently confirms — one candidate's own finding of
"18 other heads with the exact same structural risk." **No candidate this round
proposes to close this broader pattern**, and I am not recommending anyone try to
in this pass either: the same coverage-formula-blast-radius argument that sank the
LEMMA tier and form-multiplicity candidates applies here too. See §7 for how I
recommend this be recorded rather than silently left to be "discovered" again.

---

## 4. Why the coverage-formula's own design should not be touched (re-confirmed, not re-argued)

The KDoc in `coverageOf` documents that an absolute-token-count alternative was
tried and rejected because it let a record whose synonyms include a bare word like
`rice` claim full credit even when a different, longer display name was the
correct match — i.e., the exact inverse failure of the one this review is about.
I did not re-litigate this; I re-measured the two candidates that touch ranking
code at all (both scoped narrowly, never touching the accounted/formLen ratio
itself in a way that would reintroduce that rejected case), and neither reopens it
(confirmed: `RankingRegressionTest`-equivalent stays ALL PASS under both, and the
`свиней`/`курицы` shapes specifically discussed in that KDoc are unaffected — see §5).

---

## 5. Candidate re-measurement (my own numbers, not the candidates' own)

Of the four candidate investigations, three independently concluded "ship a data
fix, not a ranking change," and the fourth (the FUZZY-tier-only floor + tiebreak)
concluded its own candidate was the best *ranking-only* answer but explicitly
flagged that a sibling data-fix candidate dominates it. I re-measured the two
live contenders myself.

### 5a. Candidate: FUZZY-tier-only coverage floor + qualifying-form-length tiebreak

Mechanism (re-implemented by me from the candidate's own spec, in
`cfd_engine.FuzzyGuardStrategy`): floor the FUZZY per-form denominator to
`max(formLen, 4)` whenever the query does not completely explain the form: `unchanged`
when it does (equivalent to code:
`if (tier == FUZZY && accounted != formLen) denom = max(formLen, 4) else formLen`),
plus a tiebreak on the longest OTHER form of the record that independently
FUZZY-qualifies. EXACT/TOKEN_SUBSET/PREFIX untouched by construction (the extra key
is a hard-coded 0 for every non-FUZZY tier).

**Re-measured on the real catalog:**
```
chiken -> Chicken, broilers or fryers, meat and skin and giblets and neck, raw   FUZZY 250   FIXED
yogert -> Yogurt, plain, whole milk                                              FUZZY 250   FIXED
sammon -> Fish, salmon, chinook, smoked                                         FUZZY 250   FIXED
cafe   -> Cake, angelfood, commercially prepared                                FUZZY 250   NOT FIXED (still wrong food)
```
Matches the candidate's own reported numbers exactly — 3 of 4 confirmed.

**Regression battery, re-run myself:** `RankingRegressionTest`-equivalent ALL PASS;
MealTextCorpus 450/450, 600/600, 80/80, 188/188 (byte-identical to baseline);
RussianPhraseCorpus 162/170, 112/149, 61/93, 60/60 (byte-identical to baseline).
Confirmed zero regression on every committed number.

**But it does not touch the §3 finding at all** — confirmed directly:
```
"chicken"  (this candidate) -> TOKEN_SUBSET 500  Egg, whole, raw, fresh   (unchanged, still wrong)
"yogurt"   (this candidate) -> TOKEN_SUBSET 500  Tofu yogurt              (unchanged, still wrong)
"salmon"   (this candidate) -> TOKEN_SUBSET 333  Fish oil, salmon         (unchanged, still wrong)
```
This is the decisive weakness: even a candidate the task's own review round judged
"technically sound and correctly scoped" cannot fix the plain-spelling version of
the identical bug, because it is fenced to a tier the plain spelling never reaches.

**My own sweep** (independent vocabulary + misspelling sample, not reused from any
candidate's sweep code): 0/450 changes on correctly-spelled catalog vocabulary;
340/1317 (26%) changes on a deterministic 85-seed-word misspelling sweep spanning
en/es/ru, including a repeated raw-ingredient-to-processed-product demotion
pattern across ~15 common staples and several outright wrong-food losses
(shrimp→ostrich, walnut→cheese, beet→pickled pork feet). Full detail in §6b —
this is a materially larger and more mixed cost than "3 of 4 target words fixed,
zero regression" alone suggests.

### 5b. Candidate: curated per-record data fix (add the missing bare word directly)

I simulated this **on the real, parsed 3519-record catalog** by adding one or two
literal bare alternate names to specific records, in memory, via
`cfd_datafix.apply_datafix` — no edit to `mobile/` or `scripts/`, per the hard
constraint. Additions:

- `"chicken"` → all **43** records whose head segment is `chicken`
- `"yogurt"` → all **24** records whose head segment is `yogurt`
- `"salmon"` → all **22** records that are real salmon-as-food (`Fish, salmon, ...`,
  excluding the "Fish oil, salmon" byproduct and the unrelated "Salmon nuggets"/
  "Salmonberries" records)
- `"cafe"` and `"café"` → all **8** real `Beverages, coffee, ...` records
  (excluding the 2 "coffee substitute" records)

**Re-measured result — all four now resolve at full coverage:**
```
chiken -> Chicken, broilers or fryers, meat and skin and giblets and neck, raw   FUZZY  1000
yogert -> Yogurt, plain, whole milk                                             FUZZY  1000
sammon -> Fish, salmon, Atlantic, wild, raw                                     FUZZY  1000
cafe   -> Beverages, coffee, brewed, breakfast blend                            EXACT  1000
café   -> Beverages, coffee, brewed, breakfast blend                            EXACT  1000
```
`cafe`/`café` now resolve at **EXACT tier**, above FUZZY entirely — the "Syrup, Cane"
collision is not merely outranked, it is never reached as a competing tier.

**And critically, this ALSO fixes the plain-spelling §3 defect**, because the new
alternate name is a 1-token form that any tier (EXACT, TOKEN_SUBSET, FUZZY) scores
at full coverage:
```
"chicken" (datafix, no typo) -> EXACT 1000  Chicken, broilers or fryers, ...   FIXED
"yogurt"  (datafix, no typo) -> EXACT 1000  Yogurt, plain, whole milk          FIXED
"salmon"  (datafix, no typo) -> EXACT 1000  Fish, salmon, Atlantic, wild, raw  FIXED
```
This is a strictly larger, more valuable fix than the ranking candidate for
identical implementation risk — it closes both the typo case AND the far more
common plain-spelling case, with the same four table entries.

**Regression battery, re-run on the fixed catalog:** `RankingRegressionTest`-equivalent
ALL PASS (масло/курицы/свиней/паст/птицы shapes all unaffected — none of them share
a canonical id or head with the four touched groups). MealTextCorpus and
RussianPhraseCorpus are **provably unaffected by construction**, not just by
measurement: they run against `corpusCatalog` (300 records) and
`russianPhraseCatalog` (260 records), two entirely separate record sets built by a
different generator pass that share **no canonical ids** with `generatedCatalog` —
confirmed by re-running them anyway: 450/450, 600/600, 80/80, 188/188 and
162/170, 112/149, 61/93, 60/60, byte-identical to baseline.

**Collateral spot-checks within `generatedCatalog` itself** (does adding these four
words break anything ELSE in the same 3519-record catalog?):
```
"chicken egg"/"chicken eggs" -> still Egg, whole, raw, fresh (EXACT 1000)   unaffected
"tofu yogurt"                -> still Tofu yogurt (EXACT 1000)              unaffected
"fish oil salmon"/"fish oil, salmon" -> still Fish oil, salmon              unaffected
"syrup cane"/"cane"          -> still Syrup, Cane                           unaffected
"pollo"/"yogur"              -> unchanged (pre-existing alt names, untouched by this fix)
```
None of the records that already depended on these words for a correct match lost
that match: `TOKEN_SUBSET` still requires every query token accounted for by one
form, and the new one-word forms ("chicken", "yogurt", "salmon", "cafe"/"café")
contain none of the OTHER tokens ("egg", "tofu", "oil", "cane"), so they cannot
satisfy those multi-token queries and cannot steal the match.

---

## 6. Sweep (independent, my own vocabulary + misspelling sample)

Built entirely fresh (`cfd_sweep.py`, `cfd_classify.py`; not adapted from any prior
session's sweep code), against the real 3519-record catalog, using deterministic
typo generation (keyboard-adjacent substitution, adjacent transposition, single
deletion — no `random`, no set-iteration-order dependence) so the results are
reproducible. Two populations, per the task's own request: the catalog's own
vocabulary, and realistic misspellings of common English/Spanish/Russian food
words (85 seed words spanning all three languages, chosen to include every word
named in the four candidate reports plus common produce/protein/dairy staples).

### 6a. Catalog-vocabulary sweep (correctly-spelled words)

450 queries (300 single tokens + 150 adjacent-token bigrams, deterministically
sampled from the catalog's own display names/alternates). **0 of 450 changed**
under the FUZZY-guard candidate — confirms the candidate is provably invisible to
correctly-spelled queries, exactly as its own report claimed, and exactly as
expected from its construction (the extra tiebreak key and the coverage floor
both activate only inside the FUZZY tier, and a correctly-spelled catalog word
almost always resolves at a higher tier first).

### 6b. Misspelling sweep (the population chiken/yogert/sammon/cafe came from)

85 seed-word entries (84 distinct words — `"salmon"` is listed once in the
English set and once in the Spanish set, so its ~17 typo variants are searched
twice; noted, not hidden) → **1317 deterministic typo queries actually searched**
(matches the count independently printed by the separate §6c collateral-sweep
run, which used the identical word list). **340 of 1317 (26%) changed their top
result** under the FUZZY-guard candidate — a far larger footprint than
the four originally-named words, confirming (independently of any candidate's own
claim) that this candidate's mechanism reaches broadly across the whole
misspelling space, not narrowly.

I classified every change with an **objective oracle**, not a subjective
judgment call first: does the *baseline* record or the *candidate* record
literally carry the seed word (or its plural, via the same rule the real matcher
uses) among its own searchable forms, in the real unmodified catalog?

```
WINS (candidate now carries the word, baseline didn't):     7
LOSSES (baseline carried the word, candidate no longer does): 8
NEUTRAL (both or neither literally carry the word):          325
```

The 7/8 split alone would read as a wash, matching the "roughly neutral" shape one
candidate self-reported — but that oracle is blunt: a word like `"rice"` or
`"orange"` is a literal token of both a correct answer *and* a derived-product
answer (`"Rice flour"`, `"Orange juice"` both contain the word), so it scores
"neutral" even when a person would call it a regression. Reading the 325-item
neutral bucket by hand (grouped by seed word, all 340 rows and every distinct
`(baseline → candidate)` pair are in `cfd_classify_run.py`'s output, not sampled)
surfaces a **repeated, systematic pattern**, not scattered noise: for a large
share of common staple words, the correctly-spelled raw/plain form is displaced
by a **derived or processed variant of the same food** — a real nutrition-accuracy
cost for a food-logging app even though it is not a wrong-*food* error:

```
apple      Apples, raw, without skin                -> Apple juice, frozen concentrate, ...
arroz      Rice, white, medium-grain, raw, enriched  -> Rice flour, white, unenriched
cheese     Cheese, blue                              -> Cheese spread, cream cheese base
cream      Cheese, cream                              -> Cream, whipped, cream topping, pressurized
garlic     Garlic, raw                                -> Spices, garlic powder
lemon      Lemon juice, raw (already imperfect)       -> Lemon grass (citronella), raw  [different plant]
manzana    Apples, raw, without skin                  -> Apple juice, canned or bottled, ...
milk       Whey, acid, fluid (already imperfect)      -> Milk dessert, frozen, milk-fat free, chocolate
onion      Spices, onion powder (already imperfect)   -> Soup, onion, canned, condensed
orange     Oranges, raw, all commercial varieties      -> Orange juice, chilled, ...
papaya     Papayas, raw                                -> Papaya, canned, heavy syrup, drained
platano    Bananas, raw (correct)                      -> Plantains, yellow, raw/fried  [WRONG FOOD -- 13 typo variants, all the same shift]
potato     Potato flour (already imperfect)            -> Potato puffs, frozen, unprepared
queso      Cheese, blue                                -> Cheese, dry white, queso seco
sugar      Sugar, turbinado                            -> Cookies, sugar wafer, with creme filling, sugar free
tomato     Tomatoes, green, raw                        -> Tomato products, canned, sauce, with tomato tidbits
```

Roughly 15 of the ~40 distinct affected words show this exact shape. It is the
same qualitative bias this session's own **rejected** form-multiplicity candidate
was measured to have (processed/derived products systematically outrank raw
ingredients) — reappearing here through a *different* mechanism (the coverage
floor + tiebreak, not multiplicity), which is itself a useful, independently
obtained data point: the bias looks like a property of scoring short qualified
FUZZY forms favorably in general, not an artifact of one specific candidate's
formula.

**Real wrong-food losses** (objective oracle confirmed, not just qualitative
reading): `platano` family (all 13 typo variants) → Plantains instead of Bananas;
`limon` → Pinon Nuts / Lemon grass instead of Lemons; `beet` (`"veet"`) → Pork,
cured, feet, pickled instead of Beets; `walnut` (`"walut"`) → Cheese, port de
salut instead of Oil, walnut; `shrimp` (`"shrip"`) → **Ostrich**, inside strip,
raw instead of Crustaceans, shrimp; `сливки`(cream) → Plums instead of Cream (a
Russian near-homograph collision); `lime`(`"line"`) → Bratwurst instead of
Vinegar (baseline was already imperfect there too).

**Real wins** (objective oracle confirmed): the `salmon`-typo family (18 of the
340 rows, spanning both the English and Spanish seed-word lists) consistently
moves from the fish-oil byproduct to a real salmon-as-food record — my oracle
scored these "neutral" because both records literally contain the token
`"salmon"`, but by food identity this is a genuine, repeated fix, corroborating
§5a's finding that the candidate generalizes beyond the one named `"sammon"`
case. `лотось`(a `"лосось"`/salmon typo) fixes a baseline miss (Lotus root →
real salmon). `"pastq"` fixes a baseline miss (Whey → Pasta).

**Net read:** on realistic misspellings of common food words — the exact
population `chiken`/`yogert`/`sammon`/`cafe` came from — this candidate is not a
free win. It reliably fixes salmon-family typos and the four named words (3 of
4), but it also introduces a repeated, identifiable cost (raw-to-processed
demotion across ~15 common staples, plus a handful of outright wrong-food
losses including one alarming one, shrimp→ostrich) that no candidate report
fully quantified this way. This independent measurement is a second, separate
reason (beyond §3's TOKEN_SUBSET finding) not to ship it.

### 6c. Collateral sweep for the data-fix candidate (does it disturb anything ELSE in generatedCatalog?)

Same two populations (450 vocab + 1317 typos of the 24 English words not already
covered by the four target defects — turkey, pork, banana, tomato, orange, peach,
cheese, cream, papaya, milk, apple, butter, avocado, beet, lemon, lime, onion,
potato, garlic, mushroom — plus the four target words themselves), comparing the
**baseline catalog** against the **datafix-applied catalog**, both run with the
unmodified `BaselineStrategy` (no ranking-code change at all — this measures the
data change's own blast radius in isolation):

```
1767 queries (450 vocab + 1317 typos): 48 changed top result
```

Every one of the 48 changes is a typo of one of the four target words
(`chicken`, `salmon`, `yogurt`, `cafe` families — `salmon`'s block appears twice
in the raw output because `"salmon"` happens to be listed once in the English
seed words and again in the Spanish list, so its ~10 typos are counted twice;
that duplication does not change the conclusion) — **zero changes to any of the
other 20 unrelated seed words' typos or to any of the 450 correctly-spelled
vocabulary queries.** This is the clean result the "zero blast radius outside
the four touched groups" claim in §5b/§7 rests on, measured directly rather
than assumed.

Full raw output for both sweeps: `cfd_sweep_missing.out` (chicken/turkey/pork/
salmon/coffee/yogurt/banana/tomato/orange/peach/cheese/cream/papaya/milk/apple/
butter/avocado/beet/lemon/lime/onion/potato/garlic/mushroom typos vs.
FUZZY-guard), the tail of the original `cfd_sweep.py` run (remaining 61 seed
words vs. FUZZY-guard), and `cfd_datafix_sweep.out` (full 1767-query sweep,
baseline catalog vs. datafix catalog) — all under this worktree's shared
scratchpad, not reproduced verbatim here for length.

---

## 7. Recommendation

**Ship the curated per-record data fix**, not a ranking-code change, for all four
named defects. It fixes strictly more (it also closes the plain-spelling
`TOKEN_SUBSET` version of the identical bug, which the ranking candidate cannot
reach at all), and its blast radius is provably zero outside the eight touched
record-groups, both by measurement and by construction (EXACT/TOKEN_SUBSET/FUZZY
all require every query token accounted for by one form, so a 1-token addition can
only ever help a query consisting of exactly that token, never interfere with a
longer one).

### Why this is not a plain `MANUAL_TERMS` entry like масло

I read `scripts/generate-food-synonyms.py` and `scripts/generate-catalog-kotlin.py`
(read-only, as permitted) to ground this in the real generator rather than guessing.
масло worked as a bare `MANUAL_TERMS` addition (concept-level, keyed by AGROVOC
concept id) because it is a *different script* from the English head "Butter" —
`terms_for_head`'s "restates the head" filter (line ~569, drops any term whose
`fold_keys` overlap the head's own `fold_keys`) never saw a collision. `chicken` and
`yogurt` are literally the English head segment itself, so a plain `MANUAL_TERMS`
addition would be **silently dropped again by the same filter that (correctly, in
general) exists to avoid redundant terms** — this filter's assumption ("the head
word already appears in the display name, so a literal copy is redundant") is true
syntactically but false operationally, precisely because of how `coverageOf` scores
per-form fractions rather than per-token presence. `salmon` and `cafe`/`café` have a
different problem: their real records' head segment is `Fish` and `Beverages`
respectively, shared by dozens of unrelated species/drinks, so there is no existing
head-level or concept-level manual-override mechanism (`MANUAL_TERMS`,
`MANUAL_HEAD_CONCEPTS`, `MANUAL_WIKIDATA_LINKS` are all head- or concept-scoped) that
can reach a species-level or drink-level subset without also contaminating every
other fish/beverage record under the same head.

### Exact change to apply

I read `mobile/domain/nutrition/src/commonMain/resources/synonyms/food-synonyms.json`
(read-only) to ground this in the real data rather than guessing, and the fix
splits into two different shapes because the real source data differs between the
two pairs:

**chicken (no data change needed, only a filter carve-out).** The bare word
`"chicken"` already exists in AGROVOC's own source data — confirmed directly:
`agrovoc:c_1540` (display `"chickens"`) has `match: ["chicken", "domestic fowl",
"Gallus gallus domesticus"]`, and `agrovoc:c_24000` (`"chicken meat"`) also lists
`"chicken"`. `terms_for_head` (line ~569 of `generate-catalog-kotlin.py`) drops it
solely because it folds to the same key as the head `"Chicken"`. Fix: a narrow,
reviewed exception set, checked in that one `continue`:

```python
# terms_for_head's restates-head filter is correct in general (a term
# identical to the display name's own head is usually redundant) but is
# operationally wrong for a head whose records have no OTHER short form,
# because CatalogSearch.kt's coverageOf scores a coverage FRACTION per form:
# a 12-token qualified USDA name scores low even on its own perfectly-spelled
# head word, while an unrelated record's 2-token alternate scores high.
# Reviewed and dated per head, the same bar as MANUAL_TERMS:
RESTATES_HEAD_EXCEPTIONS: dict[str, set[str]] = {
    "chicken": {"chicken"},  # 2026-08-03: fan-out 0 in GeneratedCatalog.kt
                              # today; "chicken"/"chiken" both resolve to
                              # Egg's "chicken egg" alternate instead.
}

# inside terms_for_head(head, ...):
    if term_keys & head_keys and normalize_for_search(term) not in RESTATES_HEAD_EXCEPTIONS.get(head, set()):
        stats["dropped_restates_head"] += 1
        continue
```

**yogurt (needs a MANUAL_TERMS addition too — the word does not exist in source
at all).** Confirmed directly: `agrovoc:c_15463`'s entire `"en"` entry is
`{"display": "yoghurt"}` — no `match` list, so the American spelling `"yogurt"`
is not anywhere in the source data (unlike `"yoghurt"`, which survives the filter
today only because it is a *different string* from the head `"Yogurt"`). This
needs both parts: a `MANUAL_TERMS` entry (`c_15463, "en", "yogurt", ...`, exactly
масло's own precedent) **and** the same `RESTATES_HEAD_EXCEPTIONS["yogurt"] =
{"yogurt"}` carve-out, since once added it will exactly equal its own head string.

**salmon and café (need a new dedicated sub-group, not a filter exception).**
Both fail for a structurally different reason: their real records' head segment
is `"Fish"` and `"Beverages"` respectively — shared by every other fish species and
every other beverage — so there is no existing head- or concept-level mechanism
(`MANUAL_TERMS`, `MANUAL_HEAD_CONCEPTS`, `MANUAL_WIKIDATA_LINKS` are all scoped to
a whole head or a whole concept) that can add a term to *only* the ~22 salmon
records or *only* the 8 real coffee records without also joining it to every
other fish or beverage record under the same head. `build_records()` currently
gives every record under one head a single shared `group` object
(`record["group"]`, one `terms` list per head, e.g. all 43 chicken records already
point at the one object holding `['domestic fowl', 'Gallus gallus domesticus',
'pollo', 'gallina', 'gallo', 'gallo domestico']`). The fix is to carve the
affected records out into their **own** dedicated group, keeping every term their
shared head-group already gave them plus the one new word, applied as a small
final pass in `build_records()` right before it returns `records, emitted`:

```python
# Records whose head is shared with unrelated foods, so no head/concept-level
# manual-override table can reach them alone. Each entry is reviewed the same
# way MANUAL_TERMS is: fan-out checked against GeneratedCatalog.kt, dated.
SUBHEAD_OVERRIDES: Tuple[Tuple[str, Callable[[dict], bool], Tuple[str, ...]], ...] = (
    (
        "real-salmon-fish",
        lambda r: canonical_head(r["display"]) == "fish"
        and r["display"].split(",")[1].strip().lower() == "salmon",
        ("salmon",),
        # 2026-08-03: 22 records ("Fish, salmon, ..."), excludes the "Fish oil,
        # salmon" byproduct (head "Fish", 2nd segment "salmon" too, but not a
        # food) and "Salmon nuggets"/"Salmonberries" (head "Salmon", already
        # reachable). Fan-out of bare "salmon" today: 0.
    ),
    (
        "real-coffee",
        lambda r: canonical_head(r["display"]) == "beverages"
        and "coffee" in r["display"].lower()
        and "substitute" not in r["display"].lower(),
        ("cafe", "café"),
        # 2026-08-03: 8 records. cafe/café are unreachable at any tier today
        # (coffee(6) vs cafe(4) fails FUZZY's length-diff bound before edit
        # distance is computed) and collide coincidentally with "Syrup, Cane".
    ),
)


def apply_subhead_overrides(records, groups, used_identifiers):
    """Splits each override's matching records out of their shared head-group
    into a new group carrying the head-group's own terms plus the override's
    new word(s) -- so the unrelated siblings under the same head (every OTHER
    fish species, every OTHER beverage) are completely untouched."""
    for name, predicate, new_terms, in SUBHEAD_OVERRIDES:
        matching = [r for r in records if r["group"] is not None and predicate(r)]
        if not matching:
            continue
        base_terms = matching[0]["group"]["terms"]
        combined = base_terms + [t for t in new_terms if t not in base_terms]
        sub_group = {
            "head": f"{matching[0]['group']['head']} ({name})",
            "concepts": matching[0]["group"]["concepts"],
            "terms": combined,
            "identifier": identifier_for(name, used_identifiers),
            "records": len(matching),
        }
        for r in matching:
            r["group"] = sub_group
        groups[f"__subhead__{name}"] = sub_group
```

Call `apply_subhead_overrides(records, groups, used_identifiers)` in
`build_records()` right before computing `emitted = [group for group in
groups.values() if ...]`, so the new sub-groups are included in emission. Then
regenerate `GeneratedCatalog.kt` through the existing
`scripts/generate-catalog-kotlin.py` pipeline (do not hand-edit the generated
file: its own header says the next generator run overwrites it, and a hand-edit
here would silently revert on the next regen). **No change to `CatalogSearch.kt`
at all** for any of the four.

### What I could not verify (residual risk — read before shipping)

- **I did not run the real generator or Gradle.** The hard constraint for this
  review forbade editing `scripts/` or `mobile/`, or running Gradle. Everything
  above is measured by directly injecting the *intended end state* (the literal
  alternate-name strings on the specific canonical ids) into my parsed, in-memory
  copy of `generatedCatalog`, then running my validated (but still Python) engine
  port. This proves the ranking OUTCOME precisely, conditional on the generator
  change landing correctly; it does not prove the generator code above is free of
  its own bugs (id-list drift, JSON schema mismatch, etc.). The main session must
  implement it for real, regenerate `GeneratedCatalog.kt`, and run
  `qualityCheckJvmAndroid`/the real `RankingRegressionTest`/`MealTextCorpusTest`/
  `RussianPhraseCorpusTest` suites before calling this shipped, per this
  repository's own review→fix→verify loop.
- **My sweep is a sample, not a census** — see §6 for exact counts and method.
- **The salmon/coffee id lists above are illustrative, not exhaustive-verified by
  me against the live TSV** beyond what I printed in §5b (22 and 8 respectively,
  from my own parse of the checked-in `GeneratedCatalog.kt`); the main session
  should re-derive them from the live `usda-sr-legacy.tsv`/`food-synonyms.json`
  pair at generation time, not copy my id list, in case the source has moved.
- I did not attempt to fix or scope the broader §3 "18 other heads" pattern
  (rice/poultry/chocolate/popcorn/margarine/beans/etc.) — recommending that as a
  separate, tracked backlog item below, not silently left undiscovered.

### Document as a known limitation (do this regardless of the above)

Add a committed test in the same file and spirit as `RankingRegressionTest.kt`'s
own `птицы` "accepted loss" test, asserting the CURRENT behavior for a sample of
the broader §3 pattern, so it is a visible, tracked gap rather than something a
future session "fixes" one word at a time without a whole-catalog sweep (this
session's own history: a dedicated LEMMA tier and a form-multiplicity ranking term
were both tried and rejected for exactly that reason):

```kotlin
@Test
fun `bare qualified-name foods elsewhere in the catalog still lose to short alternates -- known limitation, not fixed by the E7.2 coverage review`() {
    // Same failure mode as chicken/yogurt/salmon/cafe (fixed as curated
    // per-record vocabulary gaps, MANUAL_RECORD_TERMS, 2026-08-03): the
    // coverage formula scores accounted/formLen per FORM, so a record's own
    // multi-token qualified USDA name scores low even on a perfectly-spelled
    // bare head word, while an unrelated record's short alternate name scores
    // high. These are NOT fixed here -- recorded so the gap stays visible
    // rather than being patched one word at a time without a whole-catalog
    // sweep, which is exactly what sank the LEMMA-tier and form-multiplicity
    // ranking candidates earlier in this session.
    assertTrue(top("rice").startsWith("Rice crackers"), "\"rice\" answered ${top("rice")}")
    assertTrue(top("poultry").startsWith("Spices, poultry seasoning"), "\"poultry\" answered ${top("poultry")}")
    assertTrue(top("chocolate").startsWith("Beverages, chocolate syrup"), "\"chocolate\" answered ${top("chocolate")}")
    assertTrue(top("popcorn").startsWith("Snacks, popcorn"), "\"popcorn\" answered ${top("popcorn")}")
}
```

---

## 8. What I explicitly did NOT recommend, and why

- **Do not ship the FUZZY-tier-only ranking candidate**, alone or alongside the
  data fix. It is real, correctly scoped, and measured safe on the *committed*
  battery — but it is strictly dominated: it fixes 3 of 4 target words (never
  `cafe`), it cannot touch the §3 plain-spelling defect at all by construction,
  and the data fix achieves a superset of its benefit (full coverage 1000, not a
  250-point win) at the same or lower implementation risk. My own §6b sweep adds
  a third reason beyond dominance: on realistic misspellings of common food
  words it measurably costs more than "3 of 4, zero regression" suggests — a
  repeated raw-to-processed demotion across ~15 common staples and a handful of
  outright wrong-food losses (shrimp→ostrich being the most alarming). Keep it on
  file as the "systemic FUZZY mechanism" option if a future review decides the
  broader §3 pattern needs a ranking-level answer, but it should not ship for
  this specific decision.
- **Do not touch the coverage formula's accounted/formLen ratio itself.** Every
  candidate that tried a formula reweight this round (geometric mean, length-diff
  penalty, form-multiplicity) either does nothing (algebraically a no-op for
  single-token queries) or reopens the exact case the KDoc's rejected
  absolute-count alternative describes, or the case this session's own rejected
  form-multiplicity candidate hit (systematic bias toward processed/derived
  products over raw ingredients, because they accumulate more synonym variants).
  I re-confirmed the KDoc's rejected-alternative reasoning still holds rather than
  re-deriving it: none of this round's reweighting candidates change that.
- **Do not silently patch the §3 pattern's other ~15-18 words in the same pass.**
  That is exactly the "fix N cases you happened to notice, discover 15-53 more
  broke elsewhere" shape this session already burned a cycle on twice. Track it
  as the committed limitation test in §7 instead.
