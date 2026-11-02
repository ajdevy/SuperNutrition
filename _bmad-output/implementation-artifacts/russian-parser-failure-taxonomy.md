# Russian phrase corpus: complete failure taxonomy

Worktree `~/workspace_ai/SuperNutrition/.claude/worktrees/e7-meal-search-ui`,
branch `feat/e7-meal-search-ui`, HEAD `2dea4187`, clean.

Written before any fix, per AGENTS.md "Review → fix → verify loop" item 2.

## What was measured, and how

No Gradle ran. The whole pipeline was re-implemented in Python against the real
checked-in data — not a paraphrase of it:

| Kotlin source | modelled |
| --- | --- |
| `TextFragments.kt` | `of`, `separatorAt`, `splitOnWeakSeparators`, `trimSeparators`, `namesNoFood`, `stripQuantityWords`, `isQuantityToken`, `quantifies`, and the three literal vocabularies read out of the file rather than retyped |
| `CatalogSearch.kt` | `normalizeForSearch`, `tokenizeNormalized`, the four tiers in order, `sharesLemma`, `isNearMatch`, `editDistance`, `coverageOf`, the sort, and the search-then-strip-then-relax sequence |
| `TextIdentification.kt` | `fallBack` / `resolveFragment`: resolve whole, split on weak separators, all-or-nothing on the split |
| `GeneratedRussianForms.kt` | all 13 284 forms, expanded through the same lemma/forms line format the Kotlin reader uses |
| `RussianPhraseCorpus.kt` | 260 records, 530 cases |
| `GeneratedCatalog.kt` | 3 519 records with their shared alternate-name lists resolved |

Kotlin literals were parsed with a string-literal-aware paren scanner, not a regex
over a multi-line constructor. Four independent counts came out equal to the
numbers the generated files declare in their own headers — 260 records, 530 cases,
13 284 forms, 1 478 cases in `MealTextCorpus.kt` — which is the check that the
parser is not silently reading a fraction of each file.

**The model reproduces the measured suite exactly, before any conclusion was drawn
from it:**

```
ru-inflected-quantity: 162/170   ru-inflected-single: 67/149   ru-inflected-two-item: 58/93
negatives resolving: 0/60        `без …` positives resolving: 0/58
```

A fifth, stronger check: re-running `scripts/generate-russian-lemmas.py`'s own index
build over the real seed set reproduces the shipped table **byte-for-byte** —
13 284 forms, 175 collisions, `table == russianFormToLemma` is `True`. Every claim
below about *why* a form is missing comes from that reproduction, not from guessing.

## Totals

530 cases. 60 negatives, all passing. **183 cases answer something other than what
they expect**:

* **125** in the measured set (the three `ru-inflected-*` kinds, excluding `без …`)
* **58** `без …` positives, which the suite deliberately excludes from the per-kind
  measurement and prints separately

Of the 125, **123 are silent misses and 2 are confident wrong foods** (below).

## Causes, counted

Each case was assigned the *minimal* subset of candidate repairs that makes it pass,
by re-running the full pipeline under all 15 subsets of four candidate repairs.
The groups are disjoint and sum to 183.

| # | Cause | Cases | Stage that lost it |
| --- | --- | --- | --- |
| 1 | Verbs of eating/preparing are neither quantity words nor separators | **48** | `stripQuantityWords` leaves `ел`/`съел`/`добавил`/`приготовил` in the query; no record satisfies that token at any tier |
| 2 | `из` is neither a quantity word nor a separator | **29** | same |
| 3 | A leading weak separator survives the split | **24** (+1 with cause 4) | `splitOnWeakSeparators` only recognises a weak separator preceded by whitespace, so `с` at offset 0 stays on the first part; `trimSeparators` trims `CONJUNCTIONS` but not `WEAK_SEPARATORS`; the part then fails and the all-or-nothing rule discards the *other*, correctly resolved half too |
| 4 | Lemma table: a paradigm lost to same-lemma parse dedupe | **3** | `russianFormToLemma` has no plural of `сабля`, so `сабель`/`саблями` fold to nothing |
| 5 | Lemma table: form dropped by the collision rule, or mis-folded | **9** | the form belongs to two in-scope lemmas and `resolve_collisions` drops it (or, for `сливов`, keeps the wrong one) |
| 6 | Lemma table: the word is unknown to OpenCorpora | **12** | `analyses_of` requires `parse.is_known`, so a transliterated species name gets no paradigm at all |
| 7 | `без …` — the corpus expectation, not the parser | **58** | nothing; the parser is right to stay silent |

### 1. Verbs of eating and preparing — 48 cases

`ел` (6 occurrences), `съел` (15), `добавил` (16), `приготовил` (12) — 49 cases carry
one; 48 are fixed by treating them as scaffolding, the 49th (`ел столовую свёклу`)
is also blocked by cause 5.

```
ел сарганов                     -> []  want [сарганы]
съел тихоокеанских лососей      -> []  want [тихоокеанские лососи]
приготовил фасоль лимскую       -> []  want [фасоль лимская]
```

The generator's own `SINGLE_CONSTRUCTIONS` list is the authority on which verbs the
corpus uses; there are exactly these four.

### 2. `из` — 29 cases

30 occurrences, 29 fixed by stripping it, the 30th (`из личей`) also blocked by
cause 6.

```
из курятины                     -> []  want [курятина]
из камбаловых рыб               -> []  want [камбаловые рыбы]
```

### 3. Leading `с` / `со` — 24 cases (+1)

Verified by direct trace, not inferred:

```
"с оливковым маслом и репой"
  fragments            -> ["с оливковым маслом и репой"]        (one fragment; `и` is weak, not strong)
  whole search         -> []                                     (no tier matches)
  weak split           -> ["с оливковым маслом", "репой"]
  part 1               -> []            no tier matched
  part 2               -> [репа]        TOKEN_SUBSET, coverage 1000
  stripQuantityWords("с оливковым маслом") == "с оливковым маслом"   (unchanged)
  => the split is discarded whole; the meal resolves to nothing
```

This is the most expensive single line of the three: the correctly-resolved half is
thrown away with the broken one.

### 4–6. The lemma table — 24 cases

These are not a vocabulary anyone can type in; they are properties of how the table
was generated. Sub-causes, each confirmed against a faithful re-run of the generator:

**4 — same-lemma parse dedupe (3 cases).** `analyses_of` keeps every top-scoring
parse but then dedupes by `parse.normal_form`. `сабли` has four parses tied at score
0.250, all with normal form `сабля`; the first is a `Sgtm,Geox` reading whose
`lexeme` is singular-only. Dedupe keeps that one, so the plural of `сабля` never
enters the index — `сабель` and `саблями` are absent although they are unambiguous
and in-dictionary.

**5 — collision drops (9 cases).** The form belongs to two in-scope lemmas, and the
table drops it rather than guess. That rule is correct; these are its cost:

| form | claimed by | cases |
| --- | --- | --- |
| `кур` | `кура`, `курица` | 3 |
| `сыром` | `сыр`, `сырой` | 1 |
| `сливками` | `сливка`, `сливки` | 1 |
| `столовую` | `столовая`, `столовый` | 1 |
| `сливами` | `слив`, `слива` | 1 |
| `сливов` | **in the table, folded to `слив`** | 2 |

**6 — unknown to OpenCorpora (12 cases).** `личи`, `коикс`, `каянус`, `луциан`,
`судачий`, `желтохвост`, `корнеклубнеплодный`, `бамбарский`. `parse.is_known` is
`False` for every form of these, so the generator emits nothing and pymorphy3's
guesser is deliberately kept out.

### 7. `без …` — 58 cases

Not a parser defect. See the risk section.

## The two confident wrong foods that exist today

```
"сливов"                        -> искусственные сливки   want сливы
"двух сливов и трех желтохвостов" -> [искусственные сливки, желтохвосты]
                                                          want [сливы, желтохвосты]
```

`сливов` (plums, gen. pl.) reaches *artificial cream* through the FUZZY tier:
`сливов` → `сливок` is edit distance 1 on a 6-character token, and the plum record
`сливы` is at distance 2 and so out of reach. The lemma table also carries
`слив → сливов, сливом`, a lexeme belonging to a different noun entirely, which is
the wrong half of an ambiguity the table's own design says it refuses to resolve.

This is present in the shipped code and none of the proposals below changes it. It
is listed here because it is the only *wrong answer* in the corpus; everything else
is a miss.

## Proposals

### P1 — recommended. One end-strip, reusing the lists that already exist

Extend the edge trim that `stripQuantityWords` already performs so it also drops, at
the two ends only, any token that is already a separator word (`CONJUNCTIONS ∪
WEAK_SEPARATORS`, via the existing `isSeparatorWord` predicate) plus a new small
Russian scaffolding set.

Crucially: **do not copy `с`/`со` into a new list.** They are already in
`WEAK_SEPARATORS`; a second list naming the same words is exactly the duplicated
vocabulary AGENTS.md records as having drifted twice in this file already. Consult
the existing predicate.

New vocabulary, as lemmas:

| lemma | part of speech | inflects? |
| --- | --- | --- |
| `из` | preposition | no |
| `есть` | verb | yes |
| `съесть` | verb | yes |
| `добавить` | verb | yes |
| `приготовить` | verb | yes |

`с` / `со` are **not** new vocabulary — they are reused from `WEAK_SEPARATORS`.

Surface forms, expanded with pymorphy3 2.0.6 against OpenCorpora 0.92 rev 417150 —
the same dictionary and version the shipped table was generated from. Participles
(`PRTF`, `PRTS`) and gerunds (`GRND`) are **excluded** on purpose; see the risk
section.

```
из
есть        едим едите едят ел ела ели ело ем ест есть ешь ешьте
съесть      съедим съедимте съедите съедят съел съела съели съело съем съест
            съесть съешь съешьте
добавить    добавил добавила добавили добавило добавим добавимте добавит добавите
            добавить добавишь добавлю добавь добавьте добавят
приготовить приготовил приготовила приготовили приготовило приготовим приготовимте
            приготовит приготовите приготовить приготовишь приготовлю приготовь
            приготовьте приготовят
```

54 forms (1 preposition + 53 verb forms). With `с`/`со` reused, the strip set is:

```
& and con e plus with y и   (existing)   +   из ел съел добавил приготовил …
```

Measured effect (Python model, same run that reproduces the baseline):

```
                     quantity      single       two-item     total
shipped              162/170       67/149       58/93        287/412
P1 + P2              163/170      141/149       87/93        391/412
negatives 0/60 unchanged · `без` 0/58 unchanged · wrong-food answers 2, unchanged
```

Regression check against the *other* corpus — 1 478 cases of `MealTextCorpus.kt`
over its own 300-record catalog, all three languages:

```
alias 600/600  exact 450/450  quantity 114/120  two-item 120/120  must-not-resolve 188/188
regressions: 0        newly answering must-not-resolve cases: 0
```

### P2 — recommended. Union same-lemma parses in the lemma generator

In `scripts/generate-russian-lemmas.py`, `analyses_of` currently keeps the first
parse per `normal_form`. Union the lexemes of same-lemma parses instead of dropping
all but the first. Strictly additive, verified by regenerating the whole table:

```
forms 13 284 -> 13 312   (+28)      changed: 0      lost: 0      collisions: 175 -> 175
```

Fixes 3 cases. Regenerating the table also regenerates the corpus, so this one has
to be landed with a corpus regeneration rather than by hand.

### P3 — not a vocabulary. The remaining 21 lemma gaps

* **9 collision drops.** The dropping rule is right and should stay. One entry
  should be *removed*: `сливов → слив`. It is the wrong half of an ambiguity, it is
  what lets a plum query reach cream, and deleting it turns 2 confident wrong
  answers into 2 honest misses. That is a strict improvement and the only
  table edit recommended here.
* **12 unknown seeds.** Fixing these means writing paradigms for words OpenCorpora
  does not have. Doing that by rule (pymorphy3's guesser) is exactly the guess the
  generator's docstring refuses. If it is ever done it must be a reviewed,
  per-word, sourced table, and it is out of scope for a vocabulary change.

### P4 — REJECTED. `без`, and the whole exclusion family

Adding `без` to any strip set is measured, not speculated:

```
without `без`:  58 `без …` cases answer nothing        (today's behaviour)
with    `без`:  53 of 58 answer with the excluded food, exactly
                e.g. "без гречневой муки"  -> гречневая мука
                     "без морских собак"   -> морские собаки
                     "без лука-порея"      -> лук-порей
```

53 silent non-answers become 53 confident wrong foods — the parser logging precisely
the food the user said they did not eat. Worse, the corpus counts them as *passes*,
so the score rises as the behaviour degrades, which is what
`RussianPhraseCorpusTest` already warns about in the test that prints them.

The same shape applies to every word in the exclusion/substitution family, none of
which is exercised by this corpus and none of which should be added on the strength
of this one: `не`, `кроме`, `вместо`, `минус`, `исключая`, `без добавления`.

**Participles are the second member of this family.** `приготовленный`,
`добавленный`, `съеденный` are morphologically forms of the four verbs above and
would arrive automatically from a naive pymorphy3 expansion. They must not be
stripped: a participle names *preparation*, preparation moves nutrition per 100 g,
and `CatalogSearch` already has a contract for it — preparation is kept and
reported through `unmatchedPreparation`, never dropped in silence. Stripping a
participle would be answering with numbers for a food the user did not eat, through
a different door. `PRTF`/`PRTS`/`GRND` are excluded from the form list above for
this reason and the exclusion should be enforced in whatever generates it.

## Collision check

Required check, reported whether or not anything was found. For each of the 54
proposed surface forms plus `с`/`со`, does that exact token occur in any display
name or alternate name in **both** `GeneratedCatalog.kt` (3 519 records) and
`russianPhraseCatalog` (260 records)? 3 005 and 772 distinct name tokens respectively.

**Three collisions found. All three are the reused separators, not the new vocabulary.**

| form | where | names |
| --- | --- | --- |
| `из` | GeneratedCatalog | `мука из проса`, `мука из сорго` (3 names) |
| `с` | GeneratedCatalog | `цикорий с гофрированными листьями` (1 name) |
| `со` | russianPhraseCatalog | `овощи со съедобными листьями` (1 name) |

**Zero collisions among the 53 verb forms.**

The harm the check predicts — "a scaffolding word that is also part of a food name
would delete that food from the query" — cannot occur for these three, and this was
verified rather than argued:

1. **The strip is edge-only.** All five colliding names carry the token in the
   interior, and `stripQuantityWords` trims only the leading and trailing runs. Over
   all 3 779 records in both catalogs, **no display name or alternate name begins or
   ends with `из`, `с`, `со`, `ел` or `съел`** — measured, count 0 in every case.
2. **The unstripped query is searched first.** `CatalogSearch.search` runs
   `searchExact(query)` before it strips anything, so a food whose real name carries
   the word still wins outright. That is the same argument the shipped code already
   makes for quantity words.
3. **`с` and `со` are already scaffolding today** — they are in `WEAK_SEPARATORS`
   and the segmenter already splits on them. This collision is pre-existing and
   already accepted by the shipped design; P1 does not introduce it.

If a reviewer prefers the strict reading — drop every colliding form — the cost is
explicit: dropping `из` loses 29 cases, dropping `с`/`со` loses 25. The 53 verb
forms are unaffected either way.

## Missing quantity words

The brief suggested `полтора` and `чашки` are missing from `QUANTITY_WORDS`. Both
claims were checked twice and **neither applies to any failing case**:

* `grep` over `RussianPhraseCorpus.kt`: `полтор` 0 hits, `чашк` 0 hits.
* Token census over all 530 parsed queries (554 distinct tokens): neither appears.

The corpus generator confirms why: its measure phrases are drawn from words already
in `QUANTITY_WORDS`, and its only oblique numerals are `двух`/`трех`/`пяти`/`четырех`,
which already reach `QUANTITY_WORDS` through the lemma fold (`двух → два`, etc.).
**No quantity-word gap is responsible for any of the 183 failures**, and all 8
baseline `ru-inflected-quantity` failures are lemma-table gaps (causes 4–6).

`полтора`/`полутора`/`полторы` and `чашка`/`чашки`/`чашку`/`чашкой`/`чашек` are real
Russian quantity words genuinely absent from `QUANTITY_WORDS`, and none of them is a
token of any name in either catalog, so adding them collides with nothing. But adding
them fixes **0** measured cases and no test here would notice if they were wrong, so
they are listed as an unmeasured, low-risk addition — not as a fix.

## What this taxonomy does not cover

* Nothing was compiled or run through Gradle; the main session owns the build. Every
  number here comes from the Python model, whose fidelity is evidenced by reproducing
  the four measured suite numbers exactly and the generated lemma table byte-for-byte.
* The 12 unknown-seed cases (cause 6) have no proposed fix, only a statement of why a
  vocabulary cannot be one.
* The `сливов → искусственные сливки` wrong answer is diagnosed but its FUZZY-tier
  half is not addressed; tightening the fuzzy allowance was not evaluated here and
  would need its own regression run.
