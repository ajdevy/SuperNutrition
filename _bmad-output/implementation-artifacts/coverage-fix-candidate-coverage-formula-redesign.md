# Coverage-formula redesign candidate: FUZZY tier, `chiken`/`yogert`/`sammon`/`cafe`

Worktree `~/workspace_ai/SuperNutrition/.claude/worktrees/e7-meal-search-ui`, branch
`feat/e7-meal-search-ui`, HEAD `22832480`. No Gradle ran, no Kotlin compiled, nothing under
`mobile/` or `scripts/` was touched. Everything below is a Python model, validated against the
real committed data and the real Gradle-measured numbers stated in the brief, before anything
was built on top of it.

Model source (scratchpad, not part of the repo):
`/private/tmp/claude-501/-Users-aj-workspace-ai-SuperNutrition--claude-worktrees-pr-review-c1b4ce/4c1405cc-f69b-4f07-8a66-3508ba5a2f99/scratchpad/covfix/`
— `kparse.py` (balanced-paren Kotlin call parser), `engine.py` (port of `CatalogSearch.kt` +
`TextFragments.kt` + `TextIdentification.kt`), `candidates.py` (coverage-formula candidates,
each scoped to FUZZY only), `fast.py` (indexed single-token-query engine, cross-validated
against `engine.py`), `sweep.py`, `battery.py`, `datafix.py`.

---

## 0. Model validation — reproduces every published number exactly

The parser is a balanced-paren Kotlin call scanner (`kparse.find_balanced_calls` +
`split_top_level_args`), not a line regex. `GeneratedCatalog.kt`'s `CatalogRecord(...)` calls
and `MealTextCorpus.kt`'s `MealTextCase(...)` calls both span multiple lines, and an earlier
draft of the lemma parser (treating each `listOf(...)` *argument* as one form, instead of each
argument as one *space-separated group of forms* — which is how `GeneratedRussianForms.kt`'s
own `putLexemes()` reads it) silently produced 1845 lemma entries instead of 13284, caught only
by the assertion, not by inspection — recorded here because AGENTS.md names this exact class of
mistake and it is worth showing the parser actually tripped over it once.

Every count is asserted before anything is read from the file:

| quantity | asserted | got |
|---|---|---|
| `GeneratedCatalog.kt` records | **3519** | **3519** |
| `russianFormToLemma` forms / lemmas | 13284 / 1112 | **13284 / 1112** |
| `GeneratedRussianScaffolding.kt` surface forms | 54 | **54** |
| `QUANTITY_WORDS` | >100 | **132** |
| `MealTextCorpus.kt` catalog records / cases | 300 / 1478 | **300 / 1478** |
| `RussianPhraseCorpus.kt` catalog records / cases | 260 / 530 | **260 / 530** |
| corpus case kinds | 450 exact / 600 alias / 120 two-item / 120 quantity / 188 must-not-resolve | **450 / 600 / 120 / 120 / 188** |
| phrase case kinds | 180 single / 170 quantity / 120 two-item / 60 must-not-resolve | **180 / 170 / 120 / 60** |

Behaviour on the **unmodified** code, through the full `TextIdentification` floor path (the only
path `RussianPhraseCorpusTest` / `MealTextCorpusTest` exercise — both fix the engine parameter to
`TextEngineOutcome.Unavailable`, so `identify()` always falls straight to `fallBack()`), not
through a bare `CatalogSearch.search`:

| measurement | brief's stated ground truth | model |
|---|---|---|
| RussianPhraseCorpus `ru-inflected-quantity` | 162/170 | **162/170** |
| RussianPhraseCorpus `ru-inflected-single` (excl. `без`) | 112/149 | **112/149** |
| RussianPhraseCorpus `ru-inflected-two-item` (excl. `без`) | 61/93 | **61/93** |
| RussianPhraseCorpus `must-not-resolve` | 60/60 | **60/60** |
| MealTextCorpus `exact` | 450/450 | **450/450** |
| MealTextCorpus `alias` | 600/600 | **600/600** |
| MealTextCorpus en `two-item`+`quantity` | 80/80 | **80/80** |
| MealTextCorpus `must-not-resolve` | 188/188 | **187/188** |

The one MealTextCorpus negative miss is the exact artifact the brief names in advance: the query
is a **lone `\n`** (`MealTextCase("\n", emptyList(), "und", "must-not-resolve", ...)`), which
`TextFragments.of` trims to nothing and the harness's own comparison then treats as an unmatched
empty-vs-empty edge — excluded as instructed, not silently dropped: **187/188, one named,
test-harness artifact, not a matcher defect.**

The model also reproduces every `RankingRegressionTest` state on the real 3519-record catalog:
`масло`→Butter (EXACT, the shipped synonym fix), `масла`→Oil, `свиней`/`200 граммов свиней`→Pork,
`курицы`/`курицу`/`курицей`/`200 граммов курицы`→Chicken, `яйцо`/`яйца`/`яйцо курицы`→Egg,
`птицы`→Poultry (the accepted loss), and `паст`/`спарже`/`корне` all reach
Parsnips/Broccoli/Celeriac via the TOKEN_SUBSET→PREFIX union.

**The model matched on every published figure. Everything below rests on it.**

---

## 1. The four defects, reproduced exactly

```
chiken  -> Egg, whole, raw, fresh                    FUZZY coverage 500
yogert  -> Tofu yogurt                                FUZZY coverage 500
sammon  -> Fish oil, salmon                           FUZZY coverage 333
cafe    -> Syrup, Cane                                FUZZY coverage 500
café    -> Syrup, Cane                                FUZZY coverage 500  (normalizes identically to "cafe")
```

All four match the brief's table exactly, including the coverage numbers, including that
`курицы`'s already-shipped fix (17413c34, TOKEN_SUBSET-only) does not reach any of these — all
four resolve at **FUZZY**, and the fix is FUZZY-only ground.

---

## 2. What actually distinguishes the wrong record from the right one, measured per case

For every one of these four queries, `search()` reduces to a single tokenised query token, and
the winning form on **both** sides of the comparison is chosen by `coverageOf`'s `best over
forms` rule. Reading off the exact matching forms settles what kind of defect this is before any
formula is proposed:

| query | wins today (form, tokens) | should win (form, tokens) | shape |
|---|---|---|---|
| `chiken` | Egg's `chicken egg` (2 tok, 1 accounted) | Chicken's `chicken meat` (2 tok, 1 accounted) | **identical shape** — a true tie, decided by canonical id |
| `yogert` | Tofu yogurt's own display name `tofu yogurt` (2 tok, 1 accounted) | Yogurt's display name `yogurt, plain, whole milk` (4 tok, 1 accounted) | different shape, but **no bare/short "yogurt" form exists anywhere in the catalog** — only `yoghurt`/`yogur`, neither within FUZZY's edit-distance bound of `yogert` |
| `sammon` | the oil byproduct's own display name `fish oil, salmon` (3 tok, 1 accounted) | any real salmon-as-food record's own display name, e.g. `fish, salmon, chinook, raw` (4 tok, 1 accounted) | different shape, but **no record in the whole catalog carries a bare `salmon` token as its own name** — 0 hits for a bare-token search |
| `cafe` | Syrup, Cane's own display name `syrup, cane` (2 tok, 1 accounted, via a coincidental collision with the unrelated word "cane") | no Coffee record is reachable at all | **not a ranking defect** — `coffee`(6) vs `cafe`(4) fails FUZZY's length-diff bound (`2 > allowance 1`) before edit distance is even computed, so no Coffee form is *eligible* at this tier regardless of how coverage is scored |

Two structural facts fall out of this table immediately, and they decide the rest of the report:

1. **`sammon`'s winning wrong form, and `yogert`'s, are each record's own DISPLAY NAME, not an
   alternate.** So is the correct record's form in both cases. A rule of the shape "prefer a
   record's own name over a borrowed synonym" (the 17413c34 tiebreak's shape, generalised) finds
   **no signal here** — both sides of the comparison are equally "the record's own name."
2. **`cafe` cannot be reached by any coverage-formula change**, because the correct answer is
   never a member of the FUZZY-tier candidate pool in the first place — `coverageOf` only ranks
   records that already passed `matchesForm`, and no Coffee record passes it for this query. A
   ranking fix can only reorder eligible records; it cannot make an ineligible one eligible.

---

## 3. The ANGLE, formalized and measured — and why it cannot work for this query population

### 3.1 The proposal, precisely

> Weight coverage by a function of the query's own token count too, not only the form's — a
> 1-token query matching 1 of a form's 2 tokens is weaker evidence than the same query matching
> a 1-token form, because in the 2-token case there is no evidence about the form's other token.

Formalized as **CANDIDATE Q2** (`candidates.make_geometric`, FUZZY-tier only): the geometric mean
of form-recall and query-precision,

```
recall    = accounted / len(formTokens)                              [today's formula]
precision = min(accounted, len(queryTokens)) / len(queryTokens)
coverage  = round(sqrt(recall * precision) * 1000)
```

### 3.2 Why it is a no-op on every one of the four cases, proven before it was run

`matchesForm` for TOKEN_SUBSET/PREFIX/FUZZY requires **every** query token to be satisfied by
*some* form token before a form is even eligible to be scored. That means for any query that
reaches FUZZY, `precision` is a function of `(accounted, len(queryTokens))` where `len(queryTokens)`
is the **same constant for every record being compared** (it is a property of the query, not of
the record) — and for a single-token query, `accounted` is very nearly always exactly 1 for the
winning form on both sides (there is only one query token to account for, and multiple record
tokens simultaneously satisfying it is the rare exception, not the case in any of these four).

So for `chiken`/`yogert`/`sammon`/`cafe` — **all four single-token queries** — `precision`
collapses to the *same* number (1/1 = 1) on every record being compared, which makes it a
constant multiplicative factor that cannot change relative order. **Q2 is provably an
order-preserving rescaling of the original `accounted/formLen` fraction for any single-token
query**, and single-token queries are exactly the population this whole defect class lives in.

Measured, not just asserted, to confirm the algebra:

```
              BASELINE                          Q2 (geometric, FUZZY only)
chiken     -> Egg, whole, raw, fresh   cov=500   -> Egg, whole, raw, fresh   cov=707
yogert     -> Tofu yogurt              cov=500   -> Tofu yogurt              cov=707
sammon     -> Fish oil, salmon         cov=333   -> Fish oil, salmon         cov=577
cafe       -> Syrup, Cane              cov=500   -> Syrup, Cane              cov=707
```

Numbers move (rescaled, as predicted); **winners do not move, on any of the four.** The full
sweep confirms this holds catalog-wide, not just on these four (§5): swept against **2930 of the
catalog's own single-token vocabulary words** (English/Spanish/Russian) and **3094 realistic
single/double-keystroke misspellings** of 210 common EN/ES/RU food words — **0 top-record changes
in either population, for both the vocabulary sweep and the misspelling stress test.** Q2 is
confirmed order-preserving everywhere it was tested, not merely on the four named cases.

### 3.3 The only algebraic direction that could work, and why it is disqualifying

To make `yogert` resolve to the real Yogurt record, a formula would have to score
`(accounted=1, formLen=4)` **higher** than `(accounted=1, formLen=2)` — i.e., **reward a form for
having more tokens the query did not name**, for a fixed amount of accounted evidence. That is
the literal opposite of "complete match beats partial match," which is the property the current
formula exists to implement and which the KDoc's own rejected alternative (absolute count) was
rejected for violating in the other direction. I built this as **CANDIDATE Q3**
(`length_diff_penalty`, `coverage = 1000 * accounted / (accounted + gap)`) expecting it to at
least partially reward the longer form; algebraically `accounted + gap == formLen` always, so
**Q3 is identical to the original ratio** — confirmed by running it: identical output to
baseline on all four queries, to the coverage integer. A version that genuinely rewards longer
forms (e.g. an additive credit per unaccounted-but-plausible token) has no principled way to
decide which unaccounted tokens are "plausible qualifiers of the same food" (`plain`, `whole`,
`milk` — all still about yogurt) versus "an unrelated different food" (`tofu` — a different noun
entirely) without semantic information the catalog does not carry. **No reweighting of
`(accounted, formLen, queryLen)` for the single winning form can fix `yogert` or `sammon` without
either doing nothing (Q2) or reversing the ranking principle the formula is built to encode
(the unbuildable direction).**

### 3.4 The "rice" rejection case, verified explicit

The KDoc's own defended case: a record whose bare single-token synonym is a common staple word
must keep winning outright over a longer, better-qualified record that also happens to contain
that word, and specifically must not lose to the same failure mode `masло`/R1 was — a record
whose synonyms merely *contain* the bare common word. **The word `rice` itself has no bare
single-token record in the current 3519-record catalog** (checked directly: 0 hits for a bare
`rice` search — every `rice`-carrying record is multi-token, `Rice crackers` etc.), so the
literal case named in the KDoc cannot be replayed against this catalog as written; what follows
verifies the *property* the KDoc is protecting, on a real bare single-token record this catalog
does carry, competing against a record that only partially names it — precisely the R1/`masло`
shape, run through every candidate here rather than asserted.

Applying §8.1's `yogurt` synonym addition (which gives Yogurt a genuine bare 1-token form,
`accounted=1, formLen=1`, competing against Tofu yogurt's `accounted=1, formLen=2` for the same
`yogert` typo — reaching FUZZY, not EXACT, so every candidate's FUZZY branch is actually
exercised):

```
baseline                 -> FUZZY  coverage 1000  Yogurt, plain, whole milk
Q2 geometric              -> FUZZY  coverage 1000  Yogurt, plain, whole milk
Q3 length_diff_penalty    -> FUZZY  coverage 1000  Yogurt, plain, whole milk
Q4 multiplicity cap=3     -> FUZZY  coverage 3000  Yogurt, plain, whole milk
```

**The complete, bare match wins outright under every candidate in this report**, confirming
`coverage(accounted=formLen) = max` is preserved as an invariant by construction: Q2's geometric
mean of two ratios both equal to 1 stays 1; Q3 is algebraically identical to the original ratio
for any `accounted`/`formLen` pair (`accounted + gap == formLen` always, not only when
`accounted=1`); Q4 only *adds* a non-negative multiplicity term on top of the same per-form
fraction, so a perfect match can only be beaten by a competitor at a *strictly higher*
multiplicity band, never by one with lower coverage at the same or a lower band. None of the
candidates here reproduce R1/`масло`'s failure mode of letting a record that merely *mentions* the
word outrank the record the word actually names.

---

## 4. The one candidate that DOES move a winner: FUZZY-only form multiplicity — measured, not assumed

Given §3's proof that no per-form reweighting can work, the only remaining lever within
`coverageOf`'s data model is a signal that looks **across a record's own forms**, not just at the
one that won. This is the same *shape* of idea a parallel candidate in this session already
measured and **rejected** for TOKEN_SUBSET/Russian (net 44 wins / 53 losses on that sweep) — it
is reused here deliberately, not reinvented, and re-measured **specifically in the one tier and
language population it was never tried against**: FUZZY, English.

**CANDIDATE Q4** (`candidates.make_multiplicity_fuzzy`), FUZZY-tier only:

```kotlin
// inside coverageOf, FUZZY branch only — EXACT/TOKEN_SUBSET/PREFIX untouched
val multiplicity = formTokens.indices.count { i -> tier.matchesForm(query, queryTokens, forms[i], formTokens[i]) }
val bestCoverage = /* today's best-over-forms accounted/formLen*1000 */
minOf(multiplicity, cap) * 1000 + bestCoverage
```

### 4.1 What it actually does to the four cases

```
cap=2:  chiken -> STILL Egg (Chicken has 5 matching forms, Egg has 2 -- but both saturate the
                  cap at 2, so it is STILL a coverage tie, decided by id again)
cap=3:  chiken -> Chicken, broilers or fryers...   FIXED (Chicken's 5 matching forms outrank
                  Egg's 2 even after both are counted, since 5 > 3 = cap, cap wins at 3 vs 2)
cap>=3: yogert -> STILL Tofu yogurt   (Tofu yogurt mult=1, real Yogurt's best form ALSO mult=1 --
                  neither record has more than one matching FUZZY form for this query, so
                  multiplicity cannot separate them; falls back to the SAME coverage comparison
                  §3 already proved unfixable)
cap>=3: sammon -> STILL Fish oil, salmon   (same reason: mult=1 on both sides)
cap>=3: cafe   -> STILL Syrup, Cane   (Coffee is not even in the FUZZY candidate pool -- §2's
                  tier-eligibility point, unreachable by any coverage term)
```

**Multiplicity fixes exactly 1 of 4 (`chiken`), and only once the cap is raised to at least 3.**
It does nothing for `yogert`/`sammon` because those records genuinely have only one matching
form each — there is no second alternate name to count. It cannot touch `cafe` for the
tier-eligibility reason in §2.

### 4.2 Full battery for Q4 (cap=3), measured against the real corpora

Run via `battery.py`, full `TextIdentification` pipeline, not a raw `CatalogSearch.search`.
Measured (not projected) on the real 3519-record catalog and the real committed corpora:

```
RankingRegressionTest-equivalent: 12/12  (масло/свиней/курицы/яйцо/птицы family, all unaffected --
                                           every one of those resolves at EXACT or TOKEN_SUBSET,
                                           and Q4 only touches FUZZY's coverageOf branch)
свиней tie (Lard vs Pork, same tier+coverage): OK  (untouched -- TOKEN_SUBSET, not FUZZY)
паст/спарже/корне unions: OK / OK / OK        (untouched -- TOKEN_SUBSET->PREFIX union, not FUZZY)
MealTextCorpus exact:  450/450   (unchanged)
MealTextCorpus alias:  600/600   (unchanged)
MealTextCorpus en two-item+quantity: 80/80   (unchanged)
MealTextCorpus negatives: 187/188   (unchanged -- same known \n artifact)
RussianPhraseCorpus must-not-resolve: 60/60   (unchanged)
RussianPhraseCorpus ru-inflected-quantity: 162/170   (unchanged)
RussianPhraseCorpus ru-inflected-single: 112/149   (unchanged)
RussianPhraseCorpus ru-inflected-two-item: 61/93   (unchanged)
```

Every RU/EN corpus number is untouched because **every single one of those ~2000 corpus cases
resolves at EXACT, TOKEN_SUBSET, or the TOKEN_SUBSET→PREFIX union** — none of them reach FUZZY at
all on the small 300/260-record corpus catalogs, so a FUZZY-only change is invisible to every
committed regression number by construction, not by luck. This is the direct benefit of scoping
the candidate to one tier the same way 17413c34 scoped its tiebreak to one tier. The identical
battery run for Q2 (geometric) produces the same all-green result, for the same structural
reason.

### 4.3 The sweep: what Q4 (cap=3) changes elsewhere

Swept via the indexed `FastSearch` (cross-validated against the literal `CatalogSearch` port on
375 probe queries across all 4 candidates, **0/375 mismatches** — §6), over:

- **2930 of the catalog's own single-token vocabulary words** (sampled from all distinct tokens
  across every display name and alternate, English/Spanish/Russian all included, since FUZZY
  applies uniformly to all three);
- **3094 realistic misspellings** — up to 14 sampled single-character substitutions, deletions,
  insertions, and adjacent transpositions per word, drawn from ~100 common English, ~55 Spanish,
  and ~55 Russian food words — the same edit-distance-1/2 population `chiken`/`yogert`/`sammon`/
  `cafe` themselves come from.

Full wins/losses are in §5.

---

## 5. Sweep results

### 5.1 Headline numbers

| population | Q2 geometric | Q4 multiplicity cap=3 | Q4 multiplicity uncapped |
|---|---|---|---|
| 2930 catalog single-token vocab words (en/es/ru) | **0 changed** | **0 changed** | **0 changed** |
| 3094 realistic misspellings (en/es/ru, ≤14 edit-1/2 variants of 210 common food words) | **0 changed** | **293 changed** (2801 unchanged) | **309 changed** (2785 unchanged) |

**Q2 (geometric) changes nothing, anywhere, in either population.** This is the direct empirical
confirmation of §3.2's algebra: no single-token query's top record moves under Q2, not just the
four named ones. A query-length-weighted reformulation of the fraction is a rescaling, not a
ranking change, over the entire tested population.

**Q4 (multiplicity, FUZZY only) changes 293/3094 misspellings at cap=3 — and only a marginal 16
more (309) when the cap is removed entirely**, meaning the cap is doing almost no work in this
population: multiplicity differences here are mostly small (2-3 vs 1), not the wide margins
(mult 5 vs mult 2) `chiken` happens to have. The catalog-vocabulary sweep (2930 REAL, correctly
spelled catalog words) shows **zero** change at any cap — multiplicity only ever fires once a
misspelling has pushed a query past EXACT/TOKEN_SUBSET/PREFIX into FUZZY, exactly as scoped.

### 5.2 Wins and losses, named — cap=3, fully enumerated

Two independent runs of the same sweep (different process invocations; Python's `set` iteration
order is hash-seed-randomized per process, so which 14-of-N misspellings get sampled per word
varies slightly run to run — 293 changed in the first run, 289 in a rerun with fresh sampling,
**98%+ overlap**; the underlying candidate algorithm itself has no such randomness — see §6). The
**289-query rerun is fully categorized below**, every one of its 36 distinct target words judged
by hand against what a person typing that word actually meant:

```
WINS (7 distinct words, 53 of 289 queries, 18%)
  chicken(14)  Egg, whole, raw, fresh                    -> Chicken, broilers or fryers...
  pork(14)     Bologna, pork / Mulberries, raw           -> Pork, fresh, backfat, raw
  turkey(14)   Fat, turkey                                -> Turkey, whole, meat and skin, raw
  avocado(8)   Oil, avocado                                -> Avocados, raw, all commercial varieties
  beet(1)      Bologna, beef                               -> Beets, raw            (typo neighbourhoods of
                                                                                       "beet" and "beef" overlap --
                                                                                       see note below)
  pasta(1,es)  Avocados, raw                                -> Pasta, gluten-free, corn, dry
  орех(1,ru)   Nuts, acorns, raw                            -> Peanuts, all types, raw

LOSSES (21 distinct words, 168 of 289 queries, 58%)
  orange(14)      Oranges, raw                              -> Orange juice, canned, unsweetened
  queso(14,es)    Cheese, blue                               -> Cheese substitute, mozzarella
  cheese(13)      Cheese, blue                               -> Cheese substitute, mozzarella
  butter(13)      Butter, salted                             -> Lima beans, immature seeds, raw
  cream(13)       Cheese, cream                              -> Sour cream, imitation, cultured
  papaya(13)      Papayas, raw                               -> Papaya, canned, heavy syrup, drained
  leche(13,es)    Milk, filled, fluid...                     -> Whey, acid, fluid
  arroz(13,es)    Rice, white, medium-grain, raw             -> Rice flour, white, unenriched
  manzana(13,es)  Apples, raw, without skin                  -> Apple juice, canned or bottled
  pina(13,es)     Pineapple, raw, all varieties               -> Pineapple juice, canned or bottled
  apple(12)       Apples, raw, without skin                  -> Apple juice, canned or bottled
  tomate(12,es)   Tomatoes, green, raw                        -> Tomato juice, canned, with salt added
  beef(2)         Bologna, beef                               -> Beets, raw
  сыр(2,ru)       Cheese, blue                                -> Cheese substitute, mozzarella
  банан(2,ru)     Bananas, raw                                -> Plantains, yellow, raw
  milk(1)         Whey, acid, fluid                           -> Game meat, boar, wild, raw
  lemon(1)        Lemons, raw, without peel                   -> Lemon juice, raw
  salt(1)         Salt, table                                 -> Cereals, whole wheat hot cereal, no salt
  pan(1,es)       Bread, banana, prepared from recipe          -> Breadfruit, raw
  papa(1,es)      Potatoes, flesh and skin, raw                -> Sweet potato, canned, vacuum pack
  fresa(1,es)     Strawberries, raw                            -> Fish, tuna, fresh, bluefin, raw

NEUTRAL / already-arbitrary-either-way (8 distinct words, 68 of 289 queries, 24%)
  flour(14), harina(14,es)  -- both answers are equally-arbitrary specific flour types for a bare
                                "flour" query; neither the before nor the after is more correct
  мука(14,ru)               -- baseline was ALREADY inconsistent (some typos hit Chicken, a clear
                                miss); candidate lands uniformly on Millet flour, a real flour --
                                arguably a partial improvement, counted neutral because a flour
                                type is still an arbitrary pick for the bare word "flour"
  melon(12), banana(11)     -- baseline was inconsistent (some typos of "melon" hit Nectarines;
                                some typos of "banana" hit Pineapple -- already wrong); candidate
                                is at least topically related (an unusual melon variety; dehydrated
                                banana) but not the plain fresh answer either
  lamb(1), limon(1,es)      -- both before and after are wrong for the intended food (Limes vs
                                Lima beans for "lamb"; same pair for "limón"/lemon) -- no signal
                                either way
  chickpea(1)               -- Egg -> Chicken: a coincidental typo-neighbourhood collision between
                                "chickpea" and "chicken"; NEITHER answer is chickpea (a legume),
                                so the change is not a win for the word actually typed
```

**Total: 53 win-queries, 168 loss-queries, 68 neutral-queries — losses outnumber wins roughly
3:1 by query count and 3:1 by distinct-word count (21 vs 7).** This is a *worse* ratio than the
already-rejected TOKEN_SUBSET/Russian multiplicity candidate's 53 losses to 44 wins (roughly
1.2:1) — nothing here supports the hope that FUZZY/English would behave better than
TOKEN_SUBSET/Russian; measured, it behaves distinctly worse.

**`butter` → `Lima beans, immature seeds, raw` is not a new finding — it is the exact, by-name
regression already reported and rejected for the TOKEN_SUBSET/Russian multiplicity candidate**
(`ranking-candidate-form-multiplicity.md` §3: "Lima beans carry `butter bean` AND `butter
beans`"). **It reproduces here, in a completely different tier (FUZZY, not TOKEN_SUBSET) and a
completely different trigger (a misspelling, not a Russian case ending), through the same root
mechanism**: Lima beans has two matching alternate forms (`butter bean`, `butter beans` —
singular and plural, the same word listed twice) that both satisfy FUZZY for a `butter`-shaped
typo, giving it multiplicity 2 against real Butter's multiplicity 1. This is independent
corroboration, not a repeat of the same measurement, that the multiplicity signal is **not
tier-specific or language-specific** — it is a property of the catalog's own name-list
redundancy (plurals and regional variants counted as independent evidence), and it recurs
wherever the signal is applied.

**A structural pattern, not a coincidence, explains most of the 21 losses**: `orange`→juice,
`apple`→juice, `manzana`→juice, `pina`→juice, `tomate`→juice, `papaya`→canned, `cheese`/`queso`/
`сыр`→substitute, `leche`→whey, `arroz`→flour. In every one of these, **the wrong, "won" record is
a *derived/processed* product of the real food** (juice, flour, a canned or cultured variant, a
substitute), and derived products systematically carry MORE alternate-name variants (an English
name, a Spanish name, a plural, a regional name — one per language/market the catalog covers)
than the raw commodity record does. Multiplicity therefore has a **systematic bias toward
processed products over raw ingredients** — precisely backwards from what a food-logging app
should default to when a user types one bare word.

### 5.3 What this means for the recommendation

**The FUZZY-only scoping keeps the damage contained to typo queries, not the committed corpora**
— none of the 289/293 changed queries touch RU/EN corpus cases (§4.2's battery is all-green for
Q4), because none of those corpus cases resolve at FUZZY. But the sweep shows the *general*
multiplicity idea, wherever it is applied, carries the same "more losses than wins" character
this session already measured once for TOKEN_SUBSET/Russian — and this measurement, in a
different tier and a different language, is **numerically worse**, not better. Scoping to FUZZY
changes *where* the damage lands (typos of `butter`/`cheese`/`cream`/`orange`/`apple`, not Russian
case endings); it does not change *whether* it happens.

---

## 6. Determinism and cross-validation

`FastSearch` (the indexed engine used for the large sweep) is cross-validated against the literal,
easier-to-audit `CatalogSearch` port on two separate probe batches, comparing the **full ordered
result list** `(id, tier, coverage)`, for baseline and for every candidate (Q2 geometric, Q3
length-diff-penalty, Q4 multiplicity cap=3):

```
run 1 (375 probes: catalog tokens + mutated near-miss strings):
  baseline: 0/375 mismatches   geometric: 0/375   length_diff_penalty: 0/375   multiplicity_cap3: 0/375
run 2 (283 probes, independent sample):
  baseline: 0/283   geometric: 0/283   length_diff_penalty: 0/283   multiplicity_cap3: 0/283
```

**0 mismatches across both runs, every candidate, 1316 total comparisons of the full ordered
result list.** The fast engine is what the large single-token sweep in §5 runs on; this is the
evidence it is trustworthy before its output was used for anything.

Neither engine iterates a hash-ordered collection for *ranking*: `CatalogSearch`'s Python port
sorts on `(-coverage, ..., canonical_id)` exactly as the Kotlin does, and `canonical_id` is
unique, so no ranking comparison can fall through to construction or iteration order. The one
place hash-seed randomization *does* show up is in this report's own **sweep tooling**, not in the
candidate algorithm: `misspellings_of()` returns a Python `set`, and `random.sample()` over that
set's iteration order (hash-seed-dependent per process, per Python's own `PYTHONHASHSEED`
default) picks a slightly different 14-of-N sample of each word's typo neighbourhood on different
process runs — the source of the 293-vs-289 discrepancy noted in §5.2. This is a property of how
this report samples its test population, not of `CatalogSearch.kt` or its Python port; flagged
here because AGENTS.md's determinism requirement is about the shipped code, not the measurement
tooling, and conflating the two would be a wrong reason to distrust a real result.

---

## 7. Status of this report

Every section's numbers were obtained by direct measurement against the real committed data —
none are projected or backfilled. Two things affected how long that took, both worth recording
honestly rather than silently: (1) this host ran multiple unrelated sibling sessions' Python
processes concurrently for part of the investigation (`scratchpad/coverage/sweep.py`,
`fuzzy_candidate.py`, `dnp_fast.py`, and others visible in `ps`), which slowed the first attempt
at the full misspelling sweep by roughly 4-5x; (2) the literal `CatalogSearch` port (`engine.py`)
is correct but was too slow (~0.2-0.3s/query) to sweep thousands of queries directly, so the
indexed `FastSearch` (§6) was built and cross-validated before being trusted for §5's scale. One
real bug surfaced and was fixed during this process, recorded rather than silently corrected:
`engine.build_engine()` originally passed a coverage_fn *factory* (`candidates.make_geometric`)
directly into `CatalogSearch`'s constructor instead of the bound closure the factory produces —
`battery.py`'s first run crashed with a wrong-arity `TypeError` on the very first FUZZY-tier
lookup, caught immediately rather than silently producing wrong numbers.

---

## 8. What I recommend, and why

§§2–4 establish, by direct measurement plus a proof that does not depend on sweep coverage
(§3.2-3.3), that a coverage-formula redesign is **not** the right tool for three of the four
named defects, and §5's completed sweep (289-293 changed queries, 3:1 losses to wins) settles that
the one candidate that *does* move a winner should not ship either:

- **`yogert` and `sammon` are vocabulary gaps, not ranking defects.** No record in the entire
  3519-record catalog carries a bare `salmon` token as its own name (0 hits), and the only bare
  `yogurt`-family forms present (`yoghurt`, `yogur`) are both outside `yogert`'s FUZZY reach.
  This is the **exact shape of R1/`масло`** from the prior TOKEN_SUBSET session: a missing bare
  colloquial synonym, not a scoring bug — and it was fixed there by a curated, additive synonym
  table entry, not a ranking change, specifically because a ranking rule strong enough to fix
  `масло` was measured to cost `сыра`/`молока`/`груша`/`лимона` elsewhere. The same logic applies
  here: §3 proves no *coverage* formula can fix `yogert`/`sammon` at all without reversing the
  "complete match wins" principle.
- **`cafe` is a tier-eligibility gap, not a ranking defect**, and is provably unreachable by any
  coverage-formula change (§2, point 2) — the only two honest outcomes are "stays unreachable" or
  "add the literal missing word," never "rank it better."
- **`chiken` is the one case a coverage-adjacent change (FUZZY-only multiplicity, cap≥3) can
  fix** — but it is *also* fixed, more cheaply and with zero measured collateral in every spot
  check run so far, by adding a bare `chicken` alternate to the Chicken record, the same
  `масло`-shaped data fix.

### 8.1 The data-fix candidate, measured (`datafix.py` / `datafix_battery.py`)

Adding one bare, literal alternate name each — `chicken` to the Chicken record, `yogurt` to
Yogurt (plain, whole milk), `salmon` to every raw salmon-species record (12 of them, so no single
species is arbitrarily privileged — mirroring `масло`'s "every butter record gets the synonym"
shape), and `café` to every "Beverages, coffee, ..." record (8 of them) — with **zero** change to
`CatalogSearch.kt`:

```
BEFORE  chiken -> Egg, whole, raw, fresh (FUZZY 500)      yogert -> Tofu yogurt (FUZZY 500)
        sammon -> Fish oil, salmon (FUZZY 333)             cafe   -> Syrup, Cane (FUZZY 500)

AFTER   chiken -> Chicken, broilers or fryers... (FUZZY, coverage 1000)
        yogert -> Yogurt, plain, whole milk (FUZZY, coverage 1000)
        sammon -> Fish, salmon, Atlantic, wild, raw (FUZZY, coverage 1000)
        cafe / café -> Beverages, coffee, brewed, breakfast blend (EXACT, coverage 1000 --
                       bypasses FUZZY and the "cane" collision entirely, since EXACT is
                       checked first in the tier loop)
```

Spot collateral checks (adjacent queries that must NOT move) all pass: `chicken egg` still
resolves to Egg, `chicken meat` still resolves to Chicken, `tofu yogurt` and `fish oil salmon`
still resolve to themselves, and the bare correctly-spelled words `chicken`/`yogurt`/`salmon`
resolve to the same records as their typo'd forms. **5/5 MUST FIX** (`cafe` and `café` both
counted), **12/12 RankingRegressionTest-equivalent** (unaffected by construction — every addition
is an English/Spanish token, and the lemma table `sharesLemma` folds through is Cyrillic-only, so
no Russian query's candidate set or ranking can change), and the **full corpus battery, run
exactly like §4.2's, is byte-identical to baseline**:

```
MealTextCorpus exact: 450/450   alias: 600/600   en two-item+quantity: 80/80   negatives: 187/188
RussianPhraseCorpus must-not-resolve: 60/60   quantity: 162/170   single: 112/149   two-item: 61/93
```

**5/5 MUST FIX, with a ranking-formula blast radius of exactly zero, verified against the same
battery §4.2 held the ranking candidates to** — `CatalogSearch.kt` is untouched, so every corpus
number, every RU regression, and every non-Cyrillic query is byte-identical by construction, and
the corpus run confirms it is also byte-identical in measurement.

### 8.2 Recommendation

**Do not ship a coverage-formula redesign for this defect class.** Ship the curated synonym
additions (§8.1) the same way `масло` was shipped — reviewed, dated, in
`scripts/generate-food-synonyms.py`'s `MANUAL_TERMS`-style table, regenerated through the
existing pipeline, not hand-edited into `GeneratedCatalog.kt`.

Both explored formula candidates are now decided by measurement, not left open:

- **Q2 (geometric, the literal "weight by query length" ANGLE) is safe but useless** — 0 changes
  across 6024 swept queries (§5.1) — because it is a proven order-preserving rescaling for the
  entire single-token population this defect lives in (§3.2). There is nothing to gain from
  shipping it.
- **Q4 (FUZZY-only multiplicity) is the one candidate that moves winners, and the completed sweep
  says not to ship it**: 289-293 changed misspellings, **losses outnumber wins roughly 3:1 by
  query count and by distinct-word count** (§5.2) — a worse ratio than the already-rejected
  TOKEN_SUBSET/Russian version of the same idea (1.2:1), with a *structural* reason it will keep
  losing (§5.2's "derived products carry more name variants than raw ingredients" pattern) rather
  than a tunable one. Scoping it to FUZZY only (unlike an unscoped version) does keep every
  committed corpus number and RU regression byte-identical (§4.2), so it is *safe to leave
  unshipped* rather than *needing a revert* — but "safe to not ship" is not a reason to ship it.

Fix `chiken`/`yogert`/`sammon`/`cafe` with §8.1's four synonym additions. Leave `CatalogSearch.kt`
and its coverage formula exactly as 17413c34 left it.
