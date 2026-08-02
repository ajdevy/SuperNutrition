# Ranking candidate: the literal reading ranks first, and the fold never silences it

Worktree `/Users/aj/workspace_ai/SuperNutrition/.claude/worktrees/e7-meal-search-ui`, branch
`feat/e7-meal-search-ui`, HEAD `2dea4187`. No Gradle was run, nothing under `mobile/` or
`scripts/` was touched. Everything below was measured in Python against the real committed
data by a model whose validation is stated first, because a candidate scored by an
unvalidated model is worth nothing.

Model source (scratchpad, not part of the repo):
`/private/tmp/claude-501/-Users-aj-workspace-ai-SuperNutrition--claude-worktrees-pr-review-c1b4ce/4c1405cc-f69b-4f07-8a66-3508ba5a2f99/scratchpad/altcand/`
— `kparse.py` (token-level Kotlin parser), `engine.py` (transcription of `CatalogSearch.kt`,
`TextFragments.kt`, `TextIdentification.kt`), `candidates.py`, `score.py`, `sweep.py`,
`adjudicate.py`.

---

## 0. Model validation — it reproduces today's numbers exactly

The parser is token-level, not line-level: `CatalogRecord(...)` and `MealTextCase(...)` both
span multiple lines in the generated files and a line regex reads a fraction of them.
Asserted counts, all met:

| asserted | expected | parsed |
|---|---|---|
| `GeneratedCatalog.kt` records | **3519** | **3519** |
| `GeneratedRussianForms.kt` forms | 13284 | 13284 |
| `RussianPhraseCorpus.kt` cases / catalog | 530 / 260 | 530 / 260 |
| `MealTextCorpus.kt` cases / catalog | 1478 / 300 | 1478 / 300 |

Behaviour on the **unmodified** code, through the full `TextIdentification` floor path
(segmenting, quantity stripping, weak-separator splitting), not through a bare
`CatalogSearch.search`:

| measurement | published | model |
|---|---|---|
| RussianPhraseCorpus `quantity` | 162/170 | **162/170** |
| RussianPhraseCorpus `single` | 67/149 | **67/149** |
| RussianPhraseCorpus `two-item` | 58/93 | **58/93** |
| RussianPhraseCorpus `must-not-resolve` | 60/60 | **60/60** |
| MealTextCorpus total | — | 1472/1478, sole failing bucket `(quantity, ru)` 34/40 |
| `exact` / `alias` / en `two-item` / en `quantity` / every negative | all pass | all pass |

It also reproduces all four defects on the real 3519-record catalog: `масло` → *Oil, soybean,
salad or cooking, (partially hydrogenated)* with *Butter, salted* at rank **63 of 69** (the
brief said 62; the substance is identical, and 63 is what the engine model produces);
`свиней` → *Lard*; `паст` / `спарже` / `корне` returning **0** Parsnips / Broccoli / Celeriac
records; and `курицы` → *Chicken* today versus *Egg, whole, raw, fresh* before `bcf034fd`.

**The model matched. Everything below rests on it.**

---

## 1. What the data actually says (two corrections to the brief)

**R2 is not a coverage difference — it is an id tie.** For `свиней`, *Lard* and all 20 *Pork*
records match through the same one-token form `свинья` and **all score coverage 1000**. Pork
is not at 500. `food:usda-srl-04002` (Lard) simply sorts before `food:usda-srl-10001`. What
separates them is available and unused: Pork also carries `мясо свиней`, which contains the
typed word **literally**; Lard reaches the query only through the fold.

**R1 is a missing synonym, not a spurious one.** No record in the catalog carries the bare
word `масло` at all (fan-out 0). Butter carries `сливочное масло`, `масло коровье`, `масло
сливочное`; oils carry the AGROVOC plural `масла`. The matcher is being asked to invent the
colloquial Russian name for butter. Section 5 measures the one-word catalog fix and its
blast radius.

---

## 2. The candidate — one principle, two applications

> A record whose stored name carries the typed word **literally** is backed by evidence.
> A record reached only through the lemma table is backed by an **inference** about which
> word was typed. The inference may rank the evidence; it may not outrank it at equal
> confidence, and where there is no evidence at all it may not silence the other literal
> reading of the same keystrokes — the prefix one.

Concretely, inside `TOKEN_SUBSET` only:

* **(i) fold-free tiebreak.** Split the tier's hits into those that satisfy it with no fold
  at all (exactly the pre-`bcf034fd` `TOKEN_SUBSET` predicate) and those that need the fold.
  Rank by coverage as today, then **fold-free before fold-only**, then canonical id.
* **(ii) fold-only does not suppress PREFIX.** When *no* hit is fold-free, `TOKEN_SUBSET`
  exists only because of the fold. Evaluate `PREFIX` as well and **append** its extra records
  after the `TOKEN_SUBSET` ones.

Nothing else changes: no new tier, `sharesLemma` untouched, `coverageOf` untouched,
`MatchStrength` untouched, `PREFIX` still raw.

`(ii)` is the R3 fix and is what nobody had. It is not "merge the tiers" — merging lets a
`PREFIX` record outrank a `TOKEN_SUBSET` one, which contradicts `MatchStrength` and the
`MealSearchTest` monotonicity invariant. Appending cannot change any top record by
construction, and the sweep confirms it: **0 top-record changes anywhere.**

Two variants of `(i)` were measured, and the difference is the whole trade:

* **CANDIDATE-B** (recommended): fold-free is a **tiebreak**, applied after coverage.
* **CANDIDATE-A**: fold-free is a **dominant sub-tier**, applied before coverage.

---

## 3. Scores

| | MUST FIX | MUST KEEP | RU corpus (neg / qty / single / two) | MTC |
|---|---|---|---|---|
| HEAD | 0/6 | 13/13 | 60/60 · 162/170 · 67/149 · 58/93 | 1472/1478 |
| pre-`bcf034fd` | 6/6 | **10/13** (`курицы`, `курицей`, `200 граммов курицы` fail) | 60/60 · **101/170 · 38/149 · 16/93** | 1472/1478 |
| **CANDIDATE-B** | **5/6** (`масло` fails) | **13/13** | **60/60 · 162/170 · 67/149 · 58/93** | 1472/1478 |
| **CANDIDATE-A** | **6/6** | **13/13** | **60/60 · 162/170 · 67/149 · 58/93** | 1472/1478 |
| B or A **+ one catalog synonym** (§5) | **6/6** | **13/13** | identical | identical |
| half (i) alone | 3/6 (R1,R2 only) | 13/13 | identical | identical |
| half (ii) alone | 3/6 (R3 only) | 13/13 | identical | identical |
| alt: merge TOKEN_SUBSET+PREFIX | 3/6 (R3 only) | 13/13 | identical | identical |
| alt: fold the query only | 1/6 | 13/13 | 60/60 · **133/170 · 54/149 · 40/93** | 1472/1478 |
| alt: fold only tokens ≥ 6 chars | 3/6 | 13/13 | 60/60 · **133/170 · 59/149 · 43/93** | 1472/1478 |

Both candidates keep **every** RussianPhraseCorpus and MealTextCorpus number byte-identical
to HEAD, and both keep every property asserted by the committed `RussianLemmaSearchTest`
(inflected forms still reach a record; `курицу`/`курица`, `свинины`/`свинина`,
`моркови`/`морковь` still answer alike; `брюссельска`/`брюссельс`/`морков`/`свинин` still
match; `морковь`/`свинина`/`курица` still at `EXACT`; `свёкла`≡`свекла`; the eight
counting/joining words still resolve to nothing; the synthetic two-record fixture still
returns `[chicken, egg]`).

The two rejected alternatives the brief named are measured and dead: **folding the query
only** costs 29 quantity, 13 single and 18 two-item cases and fixes only `масло`; **gating
the fold by token length** costs 29/8/15 and fixes only `масло`, `паст`, `корне`.

**Non-Cyrillic is provably unchanged**, not merely measured unchanged. Every key of the lemma
table is Cyrillic, so for an all-Latin query `lemma(t) == t` and no record token can fold onto
it — the fold-free set and the full set are the same set, both switches are inert, and the
code path is HEAD's. Measured too: 0 changes across the non-Cyrillic MealTextCorpus queries
and 0 across every non-Cyrillic query in the sweep below.

---

## 4. The sweep — all 3519 records, wins and losses named separately

Query populations built from the catalog's own searchable forms: **3005** distinct single
tokens, **756** distinct two-token whole names, and **6306** half-typed prefixes (every proper
prefix of length ≥ 4 of every catalog token — the population R3 lives in).

| population | CANDIDATE-B vs HEAD | CANDIDATE-A vs HEAD | HEAD vs pre-`bcf034fd` |
|---|---|---|---|
| single-token (3005) | **16** changed top | 31 changed top | 33 changed top |
| two-token (756) | **0** | **0** | 0 |
| half-typed prefix (6306) | **1** | 2 | — |
| result sets that **grew** | 11 | 11 | — |
| result sets that **shrank** | **0** | **0** | — |

CANDIDATE-A vs pre-`bcf034fd` on single tokens: **2 of 3005**, and one of them is `курицы`
(Egg → Chicken, the intended fix). In other words the fold's 33 top-record changes were
almost all collateral, and the candidate reverts them while keeping every point of the fold's
recall gain (162/67/58 versus pre-fold's 101/38/16).

### 4a. R3, fixed exactly and at zero cost

Across all 6306 half-typed queries, **exactly 11** lose records today and application (ii)
recovers **exactly those 11**, changing **no** top record and shrinking **no** result set:

| query | lemma | hits today → with fix | records recovered |
|---|---|---|---|
| `паст`, `пасте` | `паста` | 19 → 22 | **Parsnips** (3) |
| `спарж`, `спарже` | `спаржа` | 7 → 16 | **Broccoli** (9) |
| `корне` | `корень` | 2 → 5 | **Celeriac** (3) |
| `ананас`, `ананасов` | `ананас` | 10 → 14 | **Pineapple juice** (4) |
| `кукуруз` | `кукуруза` | 9 → 16 | **Corn flour** (7) |
| `культур` | `культура` | 5 → 9 | **Buckwheat flour, Rye flour** (4) |
| `листов` | `лист` | 10 → 21 | **Celery, Collards, Kale** (11) |
| `овощ` | `овощ` | 183 → 189 | **Plantains** (6) |

`ананас` is the whole Russian word for pineapple and it loses Pineapple juice today.
`кукуруз` is four-fifths of `кукуруза` and loses Corn flour. Neither was in the review; both
are the same defect as `паст`.

A structural fact worth recording: **of the 3005 complete words the catalog itself contains,
not one is fold-only at `TOKEN_SUBSET`** — 0 of 3005 single-token and 0 of 756 two-token
queries take the extra `PREFIX` pass. It fires on 84 of 6306 half-typed prefixes. The cost is
paid only where the ambiguity actually exists.

### 4b. CANDIDATE-B's 16 single-token changes, adjudicated

| query | HEAD | CANDIDATE-B | verdict |
|---|---|---|---|
| `свиней` | Lard | **Pork, fresh, backfat, raw** | **WIN — required** |
| `птицы` (of poultry) | Chicken, broilers or fryers | Poultry, mechanically deboned | win (the query says poultry) |
| `заменитель` (substitute) | Milk substitutes | Cheese substitute, mozzarella | neutral — bare noun, both arbitrary |
| `изделие` (article/product) | Sausage, Berliner | Pasta, gluten-free, corn, dry | neutral |
| `индийская` (Indian, fem.) | Turkey, whole | Turnips, raw | neutral — bare adjective |
| `корень` (root) | Chicory roots, raw | Lotus root, raw | neutral — both are "root of X" |
| `лист` / `листьями` (leaf) | Amaranth leaves | Beets, raw / Endive, raw | neutral — all leaf records |
| `листовая` (leafy) | Celery, raw | Collards, raw | neutral — bare adjective |
| `обыкновенная` (common) | Pumpkin, raw | Buckwheat flour | neutral — bare adjective |
| `овощная` / `овощной` (vegetable-) | Plantains, yellow | Rutabagas / Rhubarb | neutral — bare adjective |
| `приправы` (seasonings) | Mustard, prepared | Horseradish, prepared | neutral — both condiments |
| `свежая` (fresh) | Egg, whole, raw, fresh | Fish, anchovy, european, raw | neutral — bare adjective |
| `съедобные` (edible) | Passion-fruit | Mushrooms, shiitake, raw | neutral — bare adjective |
| `ягода` (berry) | Blackberries, raw | Goji berries, dried | neutral — both berries |

**Losses: none.** Fourteen of the sixteen are bare adjectives and category nouns that name no
food, where HEAD's answer is as arbitrary as the candidate's.

### 4c. CANDIDATE-A's 15 extra changes — this is what the extra 1/6 costs

| query | HEAD | CANDIDATE-A | verdict |
|---|---|---|---|
| `масло` | Oil, soybean, (partially hydrogenated) | **Butter, salted** | **WIN — required** |
| `сыра` / `сыру` (of/to cheese) | Cheese, blue | Cheese substitute, mozzarella | **LOSS ×2 — imitation cheese for "cheese"** |
| `молока` (of milk) | Milk, filled, fluid | Milk substitutes, fluid | **LOSS** |
| `груша` (**pear**, dictionary form) | Pears, raw | Avocados, raw | **LOSS — worst of the set** |
| `лимона` (of lemon) | Lemons, raw, without peel | Lemon juice, raw | **LOSS** |
| `грейпфрута` (of grapefruit) | Grapefruit, raw | Grapefruit juice, canned | **LOSS** |
| `проса` (of millet) | Millet, raw | Millet flour | **LOSS** |
| `цикория` (of chicory) | Chicory, witloof, raw | Chicory roots, raw | marginal loss |
| `жир` (fat) | Fat, beef tallow | Lard | neutral |
| `семена` (seeds) | Seeds, breadfruit seeds | Lupins, mature seeds | neutral |
| `пряность` (spice) | Spices, allspice, ground | Chives, raw | neutral |
| `продукты` (products) | Cheese substitute | Snacks, beef jerky | neutral |
| `земляная` / `индийское` (bare adjectives) | Peanuts / Turkey | Jerusalem-artichokes / Jackfruit | neutral |

The loss set has one shape: the literal record is a **derived product named after the food**
— `заменитель сыра` (cheese substitute), `заменители молока` (milk substitutes), `сок лимона`
(lemon juice), `сок грейпфрута`, `мука из проса` (millet flour), `корни цикория`,
`аллигаторова груша` (alligator pear = avocado) — while the base food is reachable only
through the fold, from its plural or nominative (`сыр`, `молоко`, `лимоны`, `грейпфруты`,
`просо`, `груши`).

**And `масло` has exactly that shape too.** Butter's `сливочное масло` scores 500; oils'
`масла` scores 1000 through the fold — identical to Cheese-substitute's 500 against Cheese's
1000. There is no local property that separates them: the same rule that promotes butter
promotes cheese substitute over cheese. **R1 is not fixable by a ranking rule without those
seven named losses.** That is the honest limit of this angle.

---

## 5. R1 — the honest fix is in the catalog, and it is an ADDITION

The brief invited an argument that a term attached to 62 records is a category label to be
dropped. **The data refuses that argument, and I can show why with a count.** Of the 47 names
shared by more than 60 records, 29 are single tokens:

```
164 рыба / pescado      121 супы / sopa        116 напитки / drinks / bebida
 88 снэки / снэк / снек  74 сыры / сыр / queso   73 орехи / nuez
 71 хлеб / pan / bun(s)  62 масла / aceite
```

Every threshold that catches `масла` (62) also catches `хлеб` (71, bread), `орехи` (73, nuts),
`сыр` (74, cheese) and `рыба` (164, fish) — the exact words a person types when they eat
bread, nuts, cheese or fish — and `курица` (43) sits just under it. Dropping the >60 band
would take 29 name strings from 8 concepts and make **fish, soups, drinks, snacks, cheese,
nuts, bread and oils unreachable by their commonest single word in all three languages.**
**I could not find a property that separates a label from a name, and this is the evidence
that a count-based one does not exist.**

The real defect is the other way round. Measured on the real catalog:

| catalog variant | `масло` | `масла` |
|---|---|---|
| today | Oil, soybean | Oil, soybean |
| **add `масло` to the butter concept (6 records, +1 name each)** | **Butter, salted** (`EXACT`) | **Oil, soybean** (`EXACT`) |
| drop `масла` from the oils concept (62 records, −1 name each) | Butter, salted | **Butter, salted** ← oils unreachable |

Adding the bare colloquial `масло` as a butter synonym fixes R1 **on HEAD's code with no
ranking change at all**, leaves `масла` → oils intact because both then resolve at `EXACT`,
and its blast radius is 6 records gaining 1 alternate name and **zero foods becoming
unreachable**. Deleting `масла` is the option that costs a food. The synonym table simply has
AGROVOC's plural `масла` for oils and the qualified `сливочное масло` for butter, and nothing
for the word people type.

That change belongs in `resources/synonyms/food-synonyms.json` and a regeneration, not in
`GeneratedCatalog.kt` by hand.

---

## 6. Recommendation, and the Kotlin change

**Ship CANDIDATE-B (i + ii), and fix `масло` in the synonym table.** That is 6/6 MUST FIX,
13/13 MUST KEEP, every corpus number unchanged, 16 single-token top changes with **no named
loss**, 0 two-token changes, 1 prefix change, 11 result sets recovered, 0 shrunk.

If the synonym addition is out of scope for this branch, CANDIDATE-A reaches 6/6 alone but
pays `сыра`/`сыру` → *Cheese substitute*, `молока` → *Milk substitutes*, `груша` → *Avocados*,
`лимона`/`грейпфрута` → juice, `проса` → *Millet flour*. **I would not take that trade**: those
are the FM-1 shape — fluent, confident, wrong — on words far commoner than `масло`.

**Do not revert `bcf034fd`.** The sweep says the fold's ranking damage is 33 single-token top
records, of which the candidate recovers 31; its recall gain is +61 quantity, +29 single and
+42 two-item corpus cases. Reverting throws away the recall to fix ranking the candidate
fixes for free.

Change shape in `CatalogSearch.kt` (one method plus one helper; no new tier, no
`sharesLemma`/`coverageOf`/`MatchStrength` change):

```kotlin
// IndexedRecord — the pre-bcf034fd TOKEN_SUBSET predicate, kept as a named question.
/** True when one searchable form carries every query token literally, no fold involved. */
fun matchesWithoutFolding(queryTokens: List<String>): Boolean =
    anyForm { _, formTokens -> queryTokens.all { it in formTokens } }

// CatalogSearch.searchExact — inside the tier loop, for TOKEN_SUBSET only:
val foldFree = hits.filter { it.matchesWithoutFolding(queryTokens) }.map { it.canonicalItemId }.toSet()
val ranked = hits.sortedWith(
    compareByDescending<IndexedRecord> { it.coverageOf(tier, normalizedQuery, queryTokens) }
        .thenBy { if (it.canonicalItemId in foldFree) 0 else 1 }   // (i)
        .thenBy { it.canonicalItemId },
)
if (foldFree.isNotEmpty()) return ranked.toResults(tier, ...)
// (ii) Nothing matched literally, so this tier exists only because of the fold, and the
// fold is a guess about which word was typed. It may rank; it may not silence the PREFIX
// reading of the same keystrokes. Appended, never interleaved, so no top record moves and
// MatchStrength still orders the list it is attached to.
val matched = hits.map { it.canonicalItemId }.toSet()
val prefixOnly = indexed.filter {
    it.canonicalItemId !in matched && MatchTier.PREFIX.matches(normalizedQuery, queryTokens, it)
}
return ranked.toResults(tier, ...) + prefixOnly.ranked(MatchTier.PREFIX, ...).toResults(...)
```

**Determinism.** `foldFree` is a set of canonical ids, a pure function of the record's own
forms and the query; it is used only for membership, never iterated. The comparator is total
on `(coverage, foldFree, canonicalItemId)` and canonical ids are unique, so no tie ever falls
through to encounter order. Verified: the model was rebuilt from the same 3519 records in
three different shuffled orders and answered 1256 sampled queries identically under HEAD,
CANDIDATE-A, CANDIDATE-B and the (ii)-only half — **0 mismatches**.

---

## 7. Honest weaknesses

1. **R1 is not fixed by my ranking rule.** CANDIDATE-B leaves `масло` → soybean oil. I fix it
   with a catalog synonym and show the measurement; if the main session wants R1 fixed inside
   `CatalogSearch.kt` alone, the price is the seven named foods in §4c and I recommend against
   paying it.
2. **The 14 "neutral" changes in §4b are my judgement, not a measurement.** They are bare
   adjectives (`свежая`, `овощной`, `съедобные`) and category nouns (`лист`, `ягода`,
   `приправы`) where I assert neither answer is right. A reviewer who thinks *Amaranth leaves*
   is a better answer to `лист` than *Beets* would score four of those as losses.
3. **The sweep's query population is drawn from the catalog's own names.** It cannot contain a
   Russian word the catalog does not know, so it under-samples real oblique user input
   (`курицу`, `свиней`). The RussianPhraseCorpus covers that population — 530 genuinely
   inflected cases — and is unchanged, but it is 530, not 3005.
4. **No Kotlin was compiled and no test was run.** Everything here is a Python model that
   reproduces today's published numbers exactly; it is strong evidence and it is not a green
   gate. The `RankingRegressionTest` and `RussianLemmaSearchTest` claims above are model
   predictions.
5. **`масло` rank 63, not 62.** My model puts *Butter, salted* one place later than the brief
   did. I could not reconcile the off-by-one; it does not affect any conclusion.
6. **Application (ii) makes a second pass over `indexed`** when `TOKEN_SUBSET` is fold-only.
   Measured at 84 of 6306 half-typed prefixes and 0 of 3761 complete words, so it is bounded,
   but it is an extra O(records) scan on a keystroke path and deserves a benchmark before it
   ships.
