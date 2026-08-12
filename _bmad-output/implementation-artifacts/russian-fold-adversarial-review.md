# Adversarial review: the Russian lemma fold (bcf034fd) and the quantity fold (2dea4187)

Branch `feat/e7-meal-search-ui`, HEAD `2dea4187`, worktree
`/Users/aj/workspace_ai/SuperNutrition/.claude/worktrees/e7-meal-search-ui`.

Brief: try to refute the two commits. Default to suspicion; a finding that cannot be
demonstrated with a concrete input is not a finding.

## How this was measured, and why you should believe it

No Gradle was run (the main session owns the build). Instead both files were ported to
Python and driven by the **real** generated data parsed straight out of the repository:

| Source | Parsed | Reproduced the file's own documented count |
|---|---|---|
| `GeneratedRussianForms.kt` | 13284 forms over 1112 lemmas | yes ("13284 forms over 1112 lemmas") |
| `GeneratedCatalog.kt` | 3519 records, 4 name objects | yes ("3519 identity records") |
| `RussianPhraseCorpus.kt` | 260 records, 530 cases | yes ("260 of them", "530 cases") |
| `TextFragments.kt` | `QUANTITY_WORDS` (132), `CONJUNCTIONS`, `WEAK_SEPARATORS` | vocabularies parsed, never hand-copied |

Two independent evaluators were written — a literal transcription of the Kotlin and a
posting-list indexed one — and cross-checked against each other on 393 queries with
**0 mismatches**. Every finding below is quoted from the literal transcription and
re-confirmed on the indexed one.

The decisive validation: replaying the 530-case corpus through the ported
`TextIdentification` pipeline reproduces the published numbers **exactly**, in all
three states.

| State | quantity | single | two-item | must-not-resolve | `без` resolving |
|---|---|---|---|---|---|
| before both commits | 90/170 | 38/149 | 16/93 | 0 violations | 0/58 |
| after `bcf034fd` only | 138/170 | 67/149 | 56/93 | 0 violations | 0/58 |
| after both | **162/170** | **67/149** | **58/93** | 0 violations | 0/58 |

Row 2 matches `bcf034fd`'s own message; row 3 matches the brief's "CURRENT MEASURED
STATE" digit for digit. A harness that lands on those three rows by accident is not a
thing that happens.

Search spaces swept:

* **16120 single-token queries** — every one of the 13284 lemma-table forms plus every
  one of the 3005 distinct catalog tokens. Exhaustive: these are the only one-word
  queries on which the fold can act at all.
* **30398 two-token queries** — every adjacent Cyrillic token pair in every catalog
  form (310 distinct pairs), with each side expanded to all of its lemma siblings.
* All 530 corpus cases, through segmentation and the floor, in all three states.

---

## Findings

### F1 — The stated PREFIX protection does not hold. A half-typed word that is itself a valid inflected form deletes the entire PREFIX result set. CONFIRMED

`CatalogSearch.kt:171-174` argues PREFIX must stay raw so that "someone mid-word would
lose a hit that works today", and offers `брюссельска` as the proof. That reasoning
only covers prefixes which are *not* word forms. It says nothing about the case where
the typed prefix **is** a complete inflected form of a different word — and then
`sharesLemma` makes TOKEN_SUBSET non-empty, and `searchExact`'s first-tier-wins loop
returns TOKEN_SUBSET outright. The PREFIX-only records are not demoted. They are gone.

Three concrete inputs, each verified on both evaluators:

| Typing towards | keystroke | before | after | records lost outright |
|---|---|---|---|---|
| `пастернак` (parsnip) | `паст` | Parsnips, raw `PREFIX/1000` | Pasta, gluten-free, corn, dry `TOKEN_SUBSET/1000` | all 3 parsnip records |
| `пастернак` | `пасте` | Parsnips, raw `PREFIX/1000` | Pasta, gluten-free, corn, dry | all 3 parsnip records |
| `спаржевая капуста` (broccoli) | `спарже` | Broccoli, raw `PREFIX/333` | Asparagus, raw `TOKEN_SUBSET/1000` | **all 9 broccoli records** |
| `корневой сельдерей` (celeriac) | `корне` | Celeriac, raw `PREFIX/500` | Chicory roots, raw `TOKEN_SUBSET/500` | all 3 celeriac records |

`паст` is the genitive plural of `паста`; `спарже` the dative of `спаржа`; `корне` the
prepositional of `корень`. Each is also a literal prefix of the word the user is
typing. The keystroke walk:

```
пас        Parsnips, raw   PREFIX  ->  Parsnips, raw   PREFIX
паст       Parsnips, raw   PREFIX  ->  Pasta …   TOKEN_SUBSET   <== target lost
пасте      Parsnips, raw   PREFIX  ->  Pasta …   TOKEN_SUBSET   <== target lost
пастер     Parsnips, raw   PREFIX  ->  Parsnips, raw   PREFIX    (recovered)
```

Blast radius, measured rather than guessed: of 16120 single-token queries, 67 are
promoted PREFIX -> TOKEN_SUBSET and 11 of those change the top record. Of the 30398
two-token queries, 117 are promoted and 1 changes the top record (`спарже овощи`, the
same root cause). The hole is transient — it closes within one or two more keystrokes
— which bounds the harm but does not remove it: at `паст` the user is shown *Pasta*,
a fluent, confident, wrong food, which is the FM-1 shape this file elsewhere goes to
great lengths to avoid.

Nine of the eleven single-token top changes are unambiguous improvements
(`ананасов` juice -> pineapple, `рисов` rice flour -> rice, `спарже` broccoli ->
asparagus read the other way). The finding is not that promotion is bad. It is that
the KDoc claims a protection the code does not provide, so the failure it describes
still ships.

### F2 — `масло`, the commonest Russian word for butter, now answers partially hydrogenated soybean oil; Butter falls to rank 62 of 69. CONFIRMED

```
масло   before:  Butter, salted                                   TOKEN_SUBSET/500
        after:   Oil, soybean, salad or cooking, (partially …)     TOKEN_SUBSET/1000
```

Mechanism, and it is worth stating precisely because it indicts the commit's own
justification. 62 records carry the AGROVOC oil vocabulary, whose Russian term is the
**bare single token `масла`** — the catalog holds no record whose form is `масло`. The
fold makes `масло ≡ масла`, so those 62 forms are single-token forms fully accounted:
coverage 1000. Butter's forms are `сливочное масло` and `масло коровье`, two tokens
each: coverage 500. The rule `bcf034fd` cites as its reason for existing — "covering a
whole name outranks covering half of a longer one" — is exactly what elects soybean
oil here.

Attribution: `bcf034fd` (present after commit 1, unchanged by commit 2). Partially
mitigated: `сливочное масло` and `масло сливочное` still resolve to Butter, salted.
Not mitigated for the bare word, which is what a person types into a food diary.

### F3 — `свиней` now answers Lard instead of Pork, through the same canonical-id tiebreak the commit was written to eliminate. CONFIRMED

```
свиней              before:  Pork, fresh, backfat, raw  TOKEN_SUBSET/500
                    after:   Lard                       TOKEN_SUBSET/1000
200 граммов свиней  before:  Pork, fresh, backfat, raw  TOKEN_SUBSET/500
                    after:   Lard                       TOKEN_SUBSET/1000
```

Before, `свиней` matched the literal two-token synonym `мясо свиней` at coverage 500 —
the right food for the right reason. After, 21 records carry the bare one-token
synonym `свинья`, `свиней` folds onto it, and every one of those 21 scores coverage
1000. Lard and Pork tie, and `food:usda-srl-04002` (Lard) sorts before
`food:usda-srl-10004` (Pork).

This is the `курицы`-answers-eggs shape recurring, in the same mechanism, one commit
later: a bare one-token synonym shared across a concept, a coverage tie, and a
canonical-id tiebreak deciding a food question. The commit fixed the instance it was
written for. It did not fix the class. `мясо свиней` and `свинина` are unaffected.

---

## Verdict on each claim as stated

### Claim 1 — "folding can only ADD records to the tier, never remove one" — TRUE on membership, UNSAFE on ranking

The membership half is provable, not merely unfalsified: `sharesLemma` opens with
`left == right`, so `token in formTokens` implies
`formTokens.any { sharesLemma(token, it) }`. Confirmed empirically — across 16120
single-token and 30398 two-token queries **no record ever left** the TOKEN_SUBSET set.

The ranking half is where the brief's suspicion is correct. The fold changes which
record *wins* by two distinct routes:

* **coverage rises for records already in the tier** — the intended fix (`курицы`
  500 -> 1000), and also F2 (`масло`, Butter 500 vs Oil 1000);
* **a newly-added record ties on coverage and takes the top on canonical id** — F3
  (`свиней` -> Lard).

33 of the 63 single-token TOKEN_SUBSET -> TOKEN_SUBSET diffs change the top record.

One inflation mechanism I hunted for does **not** exist here: no catalog form holds two
distinct tokens sharing a lemma (checked every form of all 3519 records; 0 triples), so
one query token can never account for two record tokens and push coverage up
spuriously. That is a property of today's data, not an invariant of the code — a
synonym such as `масло сливочное масла` would start inflating coverage silently, and
nothing asserts against it.

### Claim 2 — "PREFIX is unaffected" — REFUTED

The tier *predicate* is unaffected; *reachability* is not, and the tier loop returns
the first non-empty tier outright. See F1 for concrete inputs and for the exhaustive
counts (67 of 16120 single-token, 117 of 30398 two-token promotions).

### Claim 3 — "coverage gating keeps PREFIX and FUZZY ranking byte-identical" — TRUE

The gate reads `(tier == MatchTier.TOKEN_SUBSET && sharesLemma(...))` and Kotlin `&&`
short-circuits, so for EXACT, PREFIX and FUZZY the third disjunct is never evaluated
and `accounted` is computed exactly as before. Confirmed empirically: in the whole
sweep, no query that stayed at PREFIX or FUZZY changed its record set, its order or its
coverage.

Worth flagging what the claim does *not* say: it guarantees identical ranking **given
the same tier is reached**. The KDoc sentence beside it — "someone mid-word would lose
a hit that works today" — reads as a guarantee about outcomes, and F1 is the case where
PREFIX is never reached at all.

### Claim 4 — "`quantifies` is additive" — TRUE; and the converse is CLEAN, more by luck than by design

Additivity is provable: `word in QUANTITY_WORDS || …` short-circuits. All 132
`QUANTITY_WORDS` still quantify.

The converse — a food token whose lemma lands in `QUANTITY_WORDS`, silently deleting a
word from a food name — was the highest-value item on the list. It is clean, and by
four independent checks:

* 107 forms newly quantify. **Zero** are a token of any catalog name (checked against
  all 3005 distinct catalog tokens).
* **Zero collisions** in the generated table: no surface form is claimed by two
  lexemes, so `putLexemes`' last-write-wins never fires and no food form can inherit a
  quantity lemma.
* **No lemma is a key** of its own table (0 of 1112) — the KDoc asserts this and it
  holds, which is what makes the `?: left` fallback safe.
* Only one query in all 16120 lost a result to commit 2: `семи` (a form of `семь`,
  "seven") used to FUZZY-match *Seeds, breadfruit seeds, raw* at edit distance 1 from
  `семя`. Losing a numeral that resolved to a food is an improvement, not a regression.

The luck: of the 40 Cyrillic quantity words, 19 produced lexemes and **21 did not**,
and the 21 include precisely the dangerous ones — `пол` (also "floor"/"gender", whose
paradigm contains `поле`, `полы`, `полу`, `полов`) and the abbreviations
`г л ч ст шт мг кг мл`. Had `пол` been lemmatized, `поле` would now be a quantity token
and would be stripped from the end of any query. Nothing in the code, the generator
output, or a test states that this exclusion is required. `ложка`, `кусок`, `порция`,
`стакан`, `штука`, `пара`, `половина` and `ломтик` — the ordinary nouns named in the
brief — did produce lexemes, and are clean only because none of their 107 forms
collides with a catalog word.

Recommendation: assert the empty intersection
(`newly-quantifying forms ∩ catalog tokens == ∅`) as a test, so a future regeneration
that lemmatizes `пол`, or a catalog that adds a food named `пар` or `паре`, fails
loudly instead of quietly eating a word.

### Claim 5 — "no ENGLISH or SPANISH result changes" — TRUE, proved both ways

Structural: **0 of 13284** table keys and **0 of 1112** lemma values contain a
non-Cyrillic character. So for two tokens with no Cyrillic,
`(map[L] ?: L) == (map[R] ?: R)` reduces to `L == R`, which is the old predicate
exactly; and a Cyrillic form folds to a Cyrillic lemma, which can never equal a Latin
token. `quantifies` is identical for the same reason — `russianFormToLemma[latin]` is
always null, and `it != null &&` short-circuits.

Empirical: all **2559 non-Cyrillic queries** in the sweep are byte-identical before and
after, in result set, tier and coverage.

---

## Checked and clean — evidence, not silence

* **All 530 corpus cases, three states.** 0 regressions OLD -> C1, 0 C1 -> NEW, 0
  OLD -> NEW. 0 must-not-resolve violations in every state. 0 of the 58 `без` cases
  resolve in any state, so nothing is logged that the user said they did not eat.
* **The published numbers are honest.** Reproduced exactly, including the intermediate
  state after commit 1 alone.
* **3058 of 16120 single-token queries changed**; exactly **1** lost a result (`семи`,
  an improvement) and 1320 gained one. Transitions: FUZZY -> TOKEN_SUBSET 1607,
  NONE -> TOKEN_SUBSET 1320, PREFIX -> TOKEN_SUBSET 67, within-tier 63.
* **The fold's headline wins are real and large**, not just the one in the commit
  message: `груша` Avocados -> Pears, `малины` Olives -> Raspberries, `бараны`
  Bananas -> Lamb, `сыра` Cheese substitute -> Cheese, `лимона` Lemon juice -> Lemons,
  `спарже` Broccoli -> Asparagus, `пряность` Chives -> Spices.
* **Dishes are not skewed.** `DishExpansion` is built on `CatalogSearch`, so dishes fold
  identically; `MealSearch`'s tier-only stable merge cannot be tilted by foods being
  promoted while dishes are not.
* **Cross-target determinism holds.** `russianFormToLemma` is probed, never iterated, so
  no hash-order dependence; and with 0 collisions the construction order of the seven
  lexeme objects cannot change the resulting map.
* **`stripQuantityWords` end-only trimming is unexploited.** Because `searchExact` runs
  on the raw query first, extra stripping can only act where the raw query already
  failed. No two-token query in the sweep went from a good answer to a worse one this
  way.

## Observations, not defects

* **Cost.** TOKEN_SUBSET went from one `String.equals` per (query token, record token)
  pair — which short-circuits on length — to up to two hash-map probes. Worst case for
  a one-word query over the shipped catalog is 2 × 39841 = 79682 probes, on the hot
  per-keystroke tier. Not measurable from here; worth one device measurement before
  release.
* **`лука` -> Chives, raw** rather than Onions, raw (all coverage 500; Chives' id sorts
  first). Better than before — OLD returned *Potato flour* via a fuzzy `лука`/`мука`
  collision — but still not the food a Russian speaker means.
* **Bare generic words** (`свежая`, `овощной`, `изделие`, `индийская`, `культур`)
  resolve to arbitrary foods before and after. The fold changes *which* arbitrary food.
  Pre-existing; not caused by these commits.
