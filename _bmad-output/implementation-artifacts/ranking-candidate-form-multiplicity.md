# Ranking candidate: FORM MULTIPLICITY

Worktree `~/workspace_ai/SuperNutrition/.claude/worktrees/e7-meal-search-ui`, branch
`feat/e7-meal-search-ui`, HEAD `2dea4187`. No Kotlin, Gradle or `scripts/` file was touched;
everything below is measured in Python against the committed data files.

Model sources (scratchpad, not in the repo):
`/private/tmp/claude-501/-Users-aj-workspace-ai-SuperNutrition--claude-worktrees-pr-review-c1b4ce/4c1405cc-f69b-4f07-8a66-3508ba5a2f99/scratchpad/fm/`
— `ktp.py` (Kotlin tokenizer), `data.py` (loaders + count assertions), `engine.py` (literal
port), `fast.py` (indexed port with variants), `validate.py`, `xval2.py`, `corpval.py`,
`bat2.py`, `premise.py`, `rulecmp.py`, `pairs.py`, `fullsweep.py`, `determinism.py`.

---

## 0. Headline

| | |
|---|---|
| **Premise (form multiplicity) verified?** | **Partly.** True for `масло` and `свиней` and for the `курицы` keep-case. **Structurally inapplicable to all three R3 cases** — the records are not in the tier being ranked, so no ranking term of any kind can reach them. One factual correction to the brief: **Lard carries `свинья` in TWO forms, not one.** |
| **Candidate** | Form multiplicity as a **capped gate above coverage**, applied only where the lemma table can reach the query, **plus** a TOKEN_SUBSET∪PREFIX union under the same gate. |
| **Battery** | MUST FIX **6/6**, MUST KEEP **7/7**, corpora unchanged on every published figure, determinism clean, **0 of 9 849 non-Cyrillic queries change even their full result list**. |
| **Honest cost** | 152 of 16 120 single-token Cyrillic queries change top. By query count that is ~44 wins, ~53 losses, ~55 neutral. **This candidate is not a net win on the sweep.** It buys the two defects the committed test demands, and pays with new wrong answers of exactly the same class. |

---

## 1. Model validation — done before anything was trusted

**Parser.** `GeneratedCatalog.kt` is tokenized, not regexed (record names contain commas and
parentheses inside string literals, and constructors span many lines). The loader asserts the
record count and aborts otherwise.

| Quantity | Model | File's own documented value |
|---|---|---|
| `generatedCatalog` records | **3519** | 3519 (asserted; loader raises otherwise) |
| records carrying alternate names | 1964 | 1964 |
| `russianFormToLemma` keys / lemmas | 13284 / 1112 | 13284 / 1112 |
| `corpusCatalog` / `corpusCases` | 300 / 1478 | 300 / 1478 |
| `russianPhraseCatalog` / `russianPhraseCases` | 260 / 530 | 260 / 530 |

**Behaviour, for the UNMODIFIED code.** `engine.py` is a literal port of `CatalogSearch.kt`,
`TextFragments.kt` and `TextIdentification.kt` (including the `TextIdentification` floor path
the corpus tests actually run through — scoring `CatalogSearch` directly would have measured
the wrong thing).

| Measurement | Published | Model | |
|---|---|---|---|
| `ru-inflected-quantity` | 162/170 | **162/170** | match |
| `ru-inflected-single` | 67/149 | **67/149** | match |
| `ru-inflected-two-item` | 58/93 | **58/93** | match |
| `must-not-resolve` | 60/60 | **60/60** | match |
| MealTextCorpus `exact` | all pass | **450/450** | match |
| MealTextCorpus `alias` | all pass | **600/600** | match |
| MealTextCorpus en two-item+quantity | all pass | **80/80** | match |
| MealTextCorpus negatives | all pass | **188/188** | match |
| MealTextCorpus ru/es pending | 154/160 (printed) | **154/160** | match |

The model also reproduces all four `RankingRegressionTest` states on the real 3519 records:
`масло`→`Oil, soybean, salad or cooking, (partially hydrogenated)`; `свиней`→`Lard`;
`паст`/`спарже`/`корне` with the wanted record **absent**; `курицы`/`курицу`/`курицей`/
`200 граммов курицы`→Chicken and `яйцо`/`яйца`/`яйцо курицы`→Egg.

**Indexed engine.** `fast.py` re-implements the same semantics with inverted indexes so a
3519-record sweep finishes. Cross-validated against the literal port on 198 probes (random
catalog tokens, all six battery queries, English phrases, real two-token pairs) comparing the
**full ordered result list** of `(canonicalItemId, tier, coverage)` — **0 mismatches** — and it
independently reproduces every published corpus figure above.

**One discrepancy with the brief, and it is a counting convention, not a disagreement.** The
brief says "Butter falls to rank 62 of 69". Measured: 62 `Oil, …` records sit at coverage 1000
and `Butter, salted` is **rank 63 of 69** at coverage 500. Same fact.

---

## 2. Step 1 — the premise, verified against the real 3519 records

Multiplicity = how many of a record's searchable forms satisfy the tier for this query.

| Query | Tier | Record that must WIN | mult | cov | Record that must LOSE | mult | cov | Premise? |
|---|---|---|---|---|---|---|---|---|
| `масло` | TOKEN_SUBSET | Butter, salted | **3** | 500 | Oil, soybean… | **1** | 1000 | **holds** |
| `свиней` | TOKEN_SUBSET | Pork, fresh, backfat, raw | **3** | 1000 | Lard | **2** | 1000 | **holds, margin 1** |
| `курицы` | TOKEN_SUBSET | Chicken, broilers… | **3** | 1000 | Egg, whole, raw, fresh | **1** | 500 | **holds** |
| `паст` | TOKEN_SUBSET | Parsnips | — | — | Pasta… | 1 | 1000 | **N/A — Parsnips absent** |
| `спарже` | TOKEN_SUBSET | Broccoli | — | — | Asparagus, raw | 2 | 1000 | **N/A — Broccoli absent** |
| `корне` | TOKEN_SUBSET | Celeriac | — | — | Chicory roots, raw | 1 | 500 | **N/A — Celeriac absent** |
| `яйцо курицы` | EXACT | Egg, whole, raw, fresh | 1 | 1000 | — | — | — | unaffected (EXACT admits ≤1 form) |

The exact forms:

```
масло   Butter    сливочное масло | масло коровье | масло сливочное      (3)
        Oil       масла                                                  (1)
свиней  Pork      домашняя свинья | мясо свиней | свинья                 (3)
        Lard      домашняя свинья | свинья                               (2)   <-- NOT "свинья only"
курицы  Chicken   курица | курица как еда | мясо курицы                  (3)
        Egg       яйцо курицы                                            (1)
```

Multiplicity distribution over the whole hit set: `масло` 69 hits → 63×mult 1, 6×mult 3.
`свиней` 21 hits → 1×mult 2 (Lard), 20×mult 3. `курицы` 66 hits → 23×mult 1, 43×mult 3.
`паст` 19 hits → **all mult 1**. `спарже` 7 hits → **all mult 2**. `корне` 2 hits → all mult 1.

### The single most valuable thing in this report

**The main session's Lard claim is wrong.** Lard is not reached through `свинья` alone; it
carries `домашняя свинья` as well, so its multiplicity is 2. The premise still orders `свиней`
correctly, but by one form, and the extra form is *shared with the winner*. Any rule that
treated multiplicity as a boolean ("does it answer to this word under more than one name")
gets `свиней` **wrong** — I measured that: `min(mult,2)` with no third term leaves Lard and Pork
tied at 2 and the tie falls to canonical id, which is how Lard wins today.

**And form multiplicity cannot touch R3 at all.** It is a within-tier statistic and the parsnip,
broccoli and celeriac records are not in the tier. R3 is a tier-*selection* defect: a half-typed
word that is also a complete inflected form of another word makes TOKEN_SUBSET non-empty, and
first-tier-wins then never runs PREFIX. Reported immediately rather than forced.

---

## 3. Step 2 — the design, chosen from measurements

### Where multiplicity sits: it must be ABOVE coverage

Below coverage fixes `свиней` (a 1000/1000 tie) and cannot fix `масло` (Oil 1000 beats Butter 500).
Multiplying is the same story. So the term has to dominate coverage — and that is where the
damage lives, so the question is how to bound it.

| rule (sort key, ascending; canonical id always last) | MUST FIX | MUST KEEP | catalog single tokens changed | coverage-drops |
|---|---|---|---|---|
| `today` — `(-cov)` | 0/6 | 7/7 | — | — |
| `(-mult, -cov)` raw count | 3/6 | 7/7 | 56 | 5, incl. `сыра` 1000→500 |
| `(-sumOfCoverages, -cov)` | 3/6 | 7/7 | ~56 | same class |
| **`(-min(mult,2), -cov, -mult)` capped gate** | **3/6** | 7/7 | **53** | **3** |
| `(-cov, -displayNameMatched, -mult)` | 2/6 | 7/7 | — | 0 |
| dedup variants (collapse word-order + plural) | 3/6 | 7/7 | 51 | trades `butter` for `сыра` |

The **capped gate** `(-min(mult,2), -coverage, -multiplicity)` is the pick, and the cap is doing
real work. Raw multiplicity above coverage demotes `сыра`/`сыру` from `Cheese, blue` (coverage
**1000** — the query is the record's whole matched name) to `Cheese substitute, mozzarella`
(coverage 500, five matched forms). The cap stops a 5-name record beating a 2-name one, so
coverage still decides between them; the uncapped `-mult` term returns as the *last* tiebreaker,
which is what keeps `свиней` (Pork 3 vs Lard 2).

### The gate: this term must not touch languages the fold cannot reach

The brief requires **"every non-Cyrillic query byte-identical"**, and the ungated rule violates
it: **32 Latin single-token tops change** and 14 two-token. The worst are not marginal —

```
butter -> Lima beans, immature seeds, raw      (Lima beans carry `butter bean` AND `butter beans`)
cheese -> Cheese substitute, mozzarella
eggs   -> Eggs, scrambled, frozen mixture
orange -> Orange juice, canned, unsweetened
apple  -> Apple juice, canned or bottled
banana -> Bananas, dehydrated, or banana powder
melon  -> Melon, banana (Navajo)
fruit  -> Jackfruit, raw                        (`jack fruit`, `jackfruit-tree fruit`, `nangka fruit`)
```

So the term is gated on **whether the lemma table can reach the query token at all**:

```
token in russianFormToLemma            // a stored inflected form: свиней, курицы, паст, сыра
  || token in russianFormToLemma.values // a lemma some form folds onto: масло, свинья, курица, сыр
```

Two hash probes, no index, no language name in the code, and **provably closed** for any token
the table does not contain — which is every non-Cyrillic token, because the table's keys and
values are Cyrillic-only. Non-Cyrillic ranking is therefore unchanged *by construction*, not by
measurement. (`масло` is not a key — it is the lemma of `масла` — which is exactly why the
predicate has to look at the values too.)

I also measured a stricter gate ("did folding actually widen this query's candidate set"): 16
changed catalog tokens instead of 20, but it needs a second full scan of the records per query,
where the reach predicate is O(1). Not worth it.

### R3: TOKEN_SUBSET ∪ PREFIX, under the same gate

After a non-empty TOKEN_SUBSET tier, PREFIX candidates not already present are **appended below
every TOKEN_SUBSET hit**. This is provably top-preserving: measured over 10 605 queries, the
first hit changed **0 times** and the order of the pre-existing hits was never perturbed. So
nothing that reads `firstOrNull()` — `TextIdentification.resolveFragment`, both corpora, the
dish/food merge in `MealSearch` (which sorts by tier ordinal, so appended PREFIX hits land in
the PREFIX bucket) — can change behaviour.

Ungated, the union is expensive on junk queries: `c` grows by **+2122** results, `s` by +1791
(a one-character token matches a literal `c` at TOKEN_SUBSET and then prefixes half the
catalog). Gated on the same reach predicate this disappears: over catalog tokens and pairs the
worst growth is **+164** (`сыра`), the mean is **+0.017**, and 4 queries grow at all.

Recovered records, all at PREFIX tier below the TOKEN_SUBSET hits:

```
паст    today 19 results, no Parsnips  -> 22 results, all 3 Parsnips records, first at rank 20
спарже  today  7 results, no Broccoli  -> 16 results, all 9 Broccoli records, first at rank  8
корне   today  2 results, no Celeriac  ->  5 results, all 3 Celeriac records, first at rank  3
```

---

## 4. Step 3 — the battery

### MUST FIX — 6/6

| query | today | candidate |
|---|---|---|
| `масло` | Oil, soybean, salad or cooking, (partially hydrogenated) | **Butter, salted** |
| `свиней` | Lard | **Pork, fresh, backfat, raw** |
| `200 граммов свиней` | Lard | **Pork, fresh, backfat, raw** |
| `паст` | Parsnips absent | **Parsnips present** (3 records, rank 20) |
| `спарже` | Broccoli absent | **Broccoli present** (9 records, rank 8) |
| `корне` | Celeriac absent | **Celeriac present** (3 records, rank 3) |

### MUST KEEP — 7/7

`курицы`, `курицу`, `курицей`, `200 граммов курицы` → `Chicken, broilers or fryers, meat and
skin and giblets and neck, raw`. `яйцо`, `яйца`, `яйцо курицы` → `Egg, whole, raw, fresh`.
`морковь`, `свинина`, `курица` still resolve at **EXACT**. `брюссельска` and `морков` still
resolve at PREFIX, `свинин` at TOKEN_SUBSET — all to the same records as today.

### NO REGRESS

| | today | candidate |
|---|---|---|
| ru `must-not-resolve` | 60/60 | **60/60** |
| ru `ru-inflected-quantity` | 162/170 | **162/170** |
| ru `ru-inflected-single` | 67/149 | **67/149** |
| ru `ru-inflected-two-item` | 58/93 | **58/93** |
| MealTextCorpus `exact` | 450/450 | **450/450** |
| MealTextCorpus `alias` | 600/600 | **600/600** |
| MealTextCorpus en two-item/quantity | 80/80 | **80/80** |
| MealTextCorpus negatives | 188/188 | **188/188** |
| MealTextCorpus ru/es pending | 154/160 | **154/160** |
| **non-Cyrillic queries whose full result list differs** | — | **0 of 9 849** |
| two-token queries whose top changes | — | **0 of 7 600** |

**Determinism.** 908 probe queries × 3 different record shuffles × the candidate: **0
order-dependent answers**, comparing full ordered result lists. Nothing in the change reads a
hash iteration order or a construction index; multiplicity is a count, the cap is arithmetic,
the union appends in the same coverage-then-id order, and canonical id is still the final
tiebreaker.

---

## 5. Step 3 (cont.) — the sweep, wins and losses named separately

Query set: **16 120 single-token** queries (all 3005 distinct catalog tokens plus all 13 284
`russianFormToLemma` surface forms, so the fold is actually exercised) and **7 600 two-token**
queries (every adjacent token pair from a real record name). 5 988 singles and 7 574 pairs
resolve to something today.

```
single (16120):  today     vs pre-bcf034fd   1410 tops changed
                 candidate vs today           152 tops changed   (0 non-Cyrillic)
                 candidate vs pre-bcf034fd   1472 tops changed
pair   (7600):   today     vs pre-bcf034fd      0 tops changed
                 candidate vs today             0 tops changed
                 candidate vs pre-bcf034fd      0 tops changed
```

I expected most of the candidate's changes to be *restorations* — the fold displaced a ranking
and multiplicity puts it back — and measured it rather than asserting it. **That expectation was
wrong.** Of the 152 changed singles: **16 restore the pre-bcf034fd answer, 78 change an answer
bcf034fd had not touched at all, and 58 go to a third record neither version produced.** The
term is not undoing the fold; it is a new ranking rule with its own opinions.

The 16 restorations are worth reading, because they cut both ways:

```
restored WIN   масло       Oil, soybean…              -> Butter, salted
restored WIN   свиней      Lard                       -> Pork, fresh, backfat, raw
restored WIN   изделие     Sausage, Berliner…         -> Pasta, gluten-free, corn, dry
restored LOSS  пряность    Spices, allspice, ground   -> Chives, raw
restored LOSS  птицы       Chicken, broilers…         -> Poultry, mechanically deboned…
restored LOSS  лист        Amaranth leaves, raw       -> Beets, raw
restored ~     заменитель  Milk substitutes…          -> Cheese substitute, mozzarella
restored ~     китайская   Kiwifruit, green, raw      -> Cowpeas, young pods…
```

So three of the five losses I name in §5 are not *new* wrong answers — they are the answers the
matcher gave before bcf034fd, which the fold happened to improve, and which this candidate gives
back. That is a materially softer loss story than "the candidate breaks five things", and I only
know it because I checked. It does not make them right: `птица` → *mechanically deboned poultry*
was a poor answer before the fold and is a poor answer after it.

For reference, `pre` here means bcf034fd's `CatalogSearch.kt` (TOKEN_SUBSET on plain equality,
coverage without the lemma clause) with HEAD's `TextFragments.kt`; it is a matcher comparison,
not a whole-commit rewind. Under it the corpus scores drop to quantity 101/170, single 38/149,
two-item 16/93, negatives 60/60 — the fold is carrying real weight and nothing here suggests
reverting it.

For reference, `pre` here means bcf034fd's `CatalogSearch.kt` (TOKEN_SUBSET on plain equality,
coverage without the lemma clause) with HEAD's `TextFragments.kt`; it is a matcher comparison,
not a whole-commit rewind. Under it the corpus scores drop to quantity 101/170, single 38/149,
two-item 16/93, negatives 60/60 — i.e. the fold is carrying real weight and none of this
suggests reverting it.

The 152 single-token changes, grouped by food and counted by query (an inflected paradigm
contributes many queries for one food):

**WINS — 44 queries**

| n | change | why it is a win |
|---|---|---|
| 17 | `Sausage, Berliner, pork, beef` → **`Pasta, gluten-free, corn, dry`** | `изделие`/`изделия` and their paradigm — `макаронные изделия` is pasta |
| 11 | `Lard` → **`Pork, fresh, backfat, raw`** | the R2 target, whole `свинья` paradigm |
| 8 | `Oil, soybean…` → **`Butter, salted`** | the R1 target, whole `масло` paradigm |
| 8 | `Nuts, …` → **`Peanuts, all types, raw`** | `орех` paradigm; a named nut beats the category record |

**LOSSES — 53 queries**

| n | change | why it is a loss |
|---|---|---|
| 15 | `Amaranth leaves, raw` → **`Beets, raw`** | `лист`/`листья` paradigm. Beets win on `ботва свёклы`/`лист свёклы`/`листья свёклы`; neither answer is a food a user typed, but the leaf vegetable was the better guess |
| 13 | `Soybeans, green, raw` → **`Soy sauce, reduced sodium…`** | `соевый` paradigm. A condiment displaces the bean |
| 10 | `Chicken, broilers or fryers…` → **`Poultry, mechanically deboned, from backs and necks with skin, raw`** | `птица` paradigm. A processing by-product displaces chicken |
| 9 | `Potatoes, flesh and skin, raw` → **`Sweet potato, cooked, boiled, without skin`** | `клубни` paradigm |
| 6 | `Spices, allspice, ground` → **`Chives, raw`** | `пряность` paradigm, and the only *coverage inversion* left in the candidate: 1000 → 333 |

**NEUTRAL — 55 queries** (both answers arbitrary; the query word is an adjective or a category
label, not a food): `Kiwifruit`→`Cowpeas` 13 (`китайский`), `Lard`→`Duck, domesticated` 12
(`домашний`), `Milk substitutes`→`Cheese substitute` 10 (`заменитель`), `Potato flour`→`Millet
flour` 10 (`мука`), `Apple juice`→`Pineapple juice` 10 (`сок`).

**Read plainly: by query count this candidate loses more than it wins** — 44 against 53. It buys
the two defects `RankingRegressionTest` names and pays with five others of the same shape. Both
sides are "shared category label versus specific food"; the rule picks a different side of that
coin and the coin is not consistently weighted in the catalog. (Three of the five losses turn
out to be pre-bcf034fd behaviour rather than newly invented — see the measurement below — which
softens the story but does not make those answers good.)

---

## 6. Step 4 — where it breaks

### 6.1 Multiplicity is partly a data artifact, and the fix runs on the artifact

The three forms that make `масло` reach Butter are:

```
сливочное масло | масло коровье | масло сливочное
```

**Two of the three are the same two words in a different order.** AGROVOC lists word-order
variants as separate terms, so Butter's "answers to this word under three of its names" is
really "under two, one of which is spelled twice". The English regressions run on the identical
artifact:

```
Lima beans          butter bean | butter beans                   (singular and plural, listed twice)
Cheese substitute   cheese alternative | cheese alternatives | cheese analogue | cheese substitute, mozzarella
Jackfruit           jack fruit | jackfruit-tree fruit | nangka fruit
Egg, whole          chicken eggs | fresh eggs | hen eggs
Poultry, mech.      сельскохозяйственная птица | одомашненные птицы
```

I built and measured a deduplicating variant (canonical key = order-insensitive set of
lemma-folded, de-pluralised tokens). It **fixes** `butter`→Lima beans and **breaks** `сыра`,
because `сыры` and `сыр` are one word under that key and `Cheese, blue` drops to multiplicity 1.
51 changes instead of 53. A wash, and it adds a second de-pluralisation vocabulary the codebase
does not need.

### 6.2 Records that can never earn the term

- **858 of the 1964 Cyrillic-reachable records carry exactly ONE Russian name.** They can never
  score above multiplicity 1 on a Russian query, so they lose to any 2-name record even from
  coverage 1000. `пряность` is that failure, measured. The whole `Cream, …` family reachable
  only by `сливки`, all `Milk, …` only by `молоко`, `Sour cream` only by `сметана` — every one
  of them is one competitor away from the same fate.
- **1555 of 3519 records carry no alternate names at all.** Under the gate they are structurally
  safe: **no generated-catalog display name contains Cyrillic**, so a bare record can never be
  in a gated contest. Ungated they are not safe — 4 of the 53 ungated changes had a bare record
  on top (`fish`→Fish broth, `juice`→Acerola juice, `pork`→Bologna pork, `wild`→Wild rice).

### 6.3 The gate forfeits a real English defect of the same family

Gating on the lemma table is what makes non-Cyrillic byte-identical, and it costs five Latin
improvements. One of them is not cosmetic:

```
chicken  today -> "Egg, whole, raw, fresh"      ungated candidate -> "Chicken, broilers or fryers, …"
pork     today -> "Bologna, pork"               ungated candidate -> "Pork, fresh, backfat, raw"
turkey   today -> "Fat, turkey"                 ungated candidate -> "Turkey, whole, meat and skin, raw"
fish     today -> "Fish broth"                  ungated candidate -> "Fish, anchovy, european, raw"
sweet    today -> "Whey, acid, fluid"           ungated candidate -> "Whey, sweet, fluid"
```

**The English word `chicken` answers with EGGS today.** That is the `курицы` defect in English,
reachable with no Russian at all, and this candidate can fix it — but only by dropping the gate
and accepting `butter`→Lima beans with it. That is a product decision, not a ranking one, and
it should be made explicitly rather than by whichever variant ships.

### 6.4 Other limits

- `паст` still shows **Pasta first**; Parsnips arrive at rank 20. The committed test asserts
  presence, not rank, so it passes — but four keystrokes into `пастернак` the user still sees
  pasta. If the intent is that a half-typed word should *rank*, the union is not enough.
- **Neither corpus exercises this change.** `flagREACH`, `multTS`, `sumTS`, `dedupTS`,
  `flagdispTS` and the plain union all score **identically** to `today` on all 1478 + 530 cases.
  The corpora catalogs (300 and 260 records) barely contain the shared category labels that make
  the defect exist. Every piece of evidence for and against this candidate comes from the
  3519-record sweep. If this lands, `RankingRegressionTest` plus new cases naming `пряность`,
  `птица`, `соевый`, `клубни`, `лист` as **accepted** losses is the minimum honest test surface.
- The gate reads `russianFormToLemma.values` as a set. It is derived from the one table, not a
  second table, so the AGENTS.md "don't keep two things in step" rule is satisfied — but it must
  be a `by lazy`/build-time `val` beside the map, never recomputed per query.

---

## 7. The Kotlin change (description only — no file under `mobile/` was touched)

All in `mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition/domain/nutrition/CatalogSearch.kt`.

**1. `IndexedRecord.coverageOf` → `evidenceOf`, returning coverage and multiplicity from the
same loop.** The counting body is unchanged; the only addition is `matched++` inside the
existing `if (tier.matchesForm(...))`. No extra pass.

```kotlin
internal class FormEvidence(val coverage: Int, val matchedForms: Int)

fun evidenceOf(tier: MatchTier, query: String, queryTokens: List<String>): FormEvidence {
    var best = 0
    var matched = 0
    forEachForm { formName, formTokens ->
        if (formTokens.isNotEmpty() && tier.matchesForm(query, queryTokens, formName, formTokens)) {
            matched++
            val accounted = formTokens.count { /* unchanged */ }
            val coverage = accounted * COVERAGE_SCALE / formTokens.size
            if (coverage > best) best = coverage
        }
    }
    return FormEvidence(best, matched)
}
```

**2. The reach predicate, beside `sharesLemma`.**

```kotlin
/** Lemmas the table folds onto. Derived from [russianFormToLemma] once, so it is
 *  one view of one table rather than a second table to keep in step. */
private val russianLemmas: Set<String> = russianFormToLemma.values.toSet()

/** Whether the fold can touch this token at all: a stored inflected form, or a
 *  lemma some stored form reaches. False for every token the table does not
 *  contain, which is every non-Cyrillic token — so ranking outside Russian is
 *  unchanged by construction, not by measurement. `масло` is not a key of the
 *  table (it is the lemma of `масла`), which is why the values are consulted. */
internal fun foldReaches(token: String): Boolean =
    token in russianFormToLemma || token in russianLemmas
```

**3. `searchExact` — one comparator change and one append.** `coverageOf` is currently invoked
twice per record (comparator, then result construction); computing evidence once is a free
efficiency win alongside the behaviour change.

```kotlin
val folds = tier == MatchTier.TOKEN_SUBSET && queryTokens.any(::foldReaches)
val scored = hits.map { it to it.evidenceOf(tier, normalizedQuery, queryTokens) }
val ranked = scored.sortedWith(
    // Multiplicity outranks coverage, capped at two. A food that answers to a
    // word under more than one of its own names really is that food; a word
    // appearing in exactly one shared category label is a label. The cap stops
    // a five-synonym record beating a two-synonym one, which is what keeps a
    // record whose whole name the query accounts for (`сыра` -> Cheese) from
    // losing to one that merely mentions it five times. The uncapped count
    // returns as the last tiebreaker, which is what separates Pork (3) from
    // Lard (2) for `свиней` -- Lard carries `свинья` in TWO forms, not one.
    compareByDescending<Pair<IndexedRecord, FormEvidence>> {
        if (folds) minOf(it.second.matchedForms, MULTIPLICITY_CAP) else 0
    }
        .thenByDescending { it.second.coverage }
        .thenByDescending { if (folds) it.second.matchedForms else 0 }
        .thenBy { it.first.canonicalItemId },
)
```

and, when `folds` and the tier is TOKEN_SUBSET, append the PREFIX hits not already present,
ranked among themselves exactly as PREFIX ranks today and carrying
`MatchStrength(MatchTier.PREFIX, coverage)`. Appending rather than merging is what makes the
change invisible to every `firstOrNull()` caller.

`private const val MULTIPLICITY_CAP = 2`.

---

## 8. Verdict on my own candidate

**Take the union. Think hard before taking the multiplicity term.**

The two halves are separable and should be judged separately.

- **The TOKEN_SUBSET∪PREFIX union is close to free and I recommend it unconditionally.** It
  fixes all three R3 cases, changes the first hit of **zero** queries out of 10 605, perturbs no
  existing ordering, and gated on `foldReaches` it costs +0.017 results per query. It also
  repairs a claim in the code's own KDoc that is currently false: "PREFIX stays raw … so someone
  mid-word would lose a hit that works today" only covers prefixes that are not themselves word
  forms, and `паст` is the counterexample.

- **The multiplicity term does what the brief asked and I am not confident it should ship.** It
  fixes `масло` and `свиней`, which is what `RankingRegressionTest` demands, and my own sweep
  says it makes five other Russian queries worse — `птица` (chicken → mechanically deboned
  poultry) and `пряность` (a coverage-1000 record demoted to a coverage-333 one) are the two I
  would expect a user to notice. 44 wins against 53 losses is not a fix, it is a redistribution.
  The reason it works at all is partly that AGROVOC spells `сливочное масло` twice.

If the goal is to make `RankingRegressionTest` pass, this candidate does it, 6/6 and 7/7, with
the corpora untouched, non-Cyrillic provably byte-identical, and determinism verified. If the
goal is that Russian ranking gets better, the honest answer from these measurements is that
**form multiplicity is the first property found that orders all three known cases the same way,
and it is still not a strong enough signal to carry a ranking rule.** The catalog's real problem
— 47 names shared by more than 60 records each, `рыба`/`мясо рыбы`/`свежая рыба`/`сырая рыба`/
`fish meat`/`fresh fish`/`pescado` all on 164 records — is a data problem that a within-tier
term keeps re-litigating from a different angle. Marking those shared lists as *category* rather
than *name* in `generate-catalog-from-srlegacy.py` would let the matcher stop guessing, and
would be worth an hour of measurement before another ranking rule is committed.
