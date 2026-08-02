# Ranking fix: decision, re-measured

Independent adjudication of the two candidate reports. Everything below was
re-derived from the checked-in data with a fresh parser and a fresh Python port
of `CatalogSearch.kt` / `TextFragments.kt` / `TextIdentification.kt`. Nothing is
taken from either candidate report on trust; where I could not reproduce a
claim, it is listed as a finding.

Worktree `/Users/aj/workspace_ai/SuperNutrition/.claude/worktrees/e7-meal-search-ui`,
branch `feat/e7-meal-search-ui`, HEAD `2dea4187`. No Gradle ran, no Kotlin
compiled, nothing under `mobile/` or `scripts/` was edited.

---

## 0. Model validation, before any candidate was scored

Parser asserts every count before reading anything, and aborts otherwise. All
independently reproduced:

| Quantity | Asserted | Got |
|---|---|---|
| `generatedCatalog` records | 3519 | 3519 |
| `russianFormToLemma` forms / lemmas | 13284 | 13284 / 1112 |
| `corpusCatalog` / `corpusCases` | 300 / 1478 | 300 / 1478 |
| `russianPhraseCatalog` / `russianPhraseCases` | 260 / 530 | 260 / 530 |
| records carrying alternates | — | 1964 |
| records with no alternates | — | 1555 |
| Cyrillic-reachable records with exactly one Russian name | — | 858 |
| names shared by more than 60 records | 47 | 47 |
| lemma-table keys / values that are non-Cyrillic | — | 0 / 0 |
| lemmas that are keys of their own table | — | 0 |

**Published numbers, unmodified code, first run — all matched exactly:**

```
RussianPhraseCorpus:  ru-inflected-quantity 162/170
                      ru-inflected-single    67/149
                      ru-inflected-two-item  58/93
                      must-not-resolve       60/60
MealTextCorpus:       exact 450/450   alias 600/600   en two-item+quantity 80/80
                      negatives 188/188   ru/es pending 154/160   TOTAL 1472/1478
```

The model also reproduces all four `RankingRegressionTest` states on the real
3519 records, and reproduces pre-`bcf034fd` behaviour (`курицы` → *Egg, whole,
raw, fresh*; `масло` → *Butter, salted*; `паст` → *Parsnips, raw*).

A second, indexed engine was written for the sweep and cross-validated against
the literal port on **611 probes × 6 variants = 3666 comparisons of the FULL
ordered result list** `(id, tier, coverage)`: **0 mismatches**.

---

## 1. What the four situations actually are

Measured on the real catalog, not assumed. This is the whole decision.

| Query | Winner today | Coverage | Reached | Correct answer | Coverage | Reached |
|---|---|---|---|---|---|---|
| `свиней` | Lard | **1000** | fold only (`свинья`) | Pork, fresh, backfat | **1000** | **literally** (`мясо свиней`) |
| `масло` | Oil, soybean … | **1000** | fold only (`масла`) | Butter, salted | **500** | **literally** (`сливочное масло`) |
| `курицы` | Chicken (correct) | **1000** | literally (`мясо курицы`) | — | Egg 500 | also literally (`яйцо курицы`) |
| `сыра` | Cheese, blue (correct) | **1000** | fold only (`сыр`/`сыры`) | — | Cheese substitute 500 | literally (`заменитель сыра`) |

Three things fall straight out of this table and they decide everything:

1. **R2 is an ID tie, not a coverage gap.** Lard and all 20 Pork records score
   coverage 1000. Lard wins only because `usda-srl-04002` sorts before `10001`.
   The unused evidence is that Pork carries the typed word *literally*.
   *(The brief describes R2 as the same 1000-vs-500 shape as R1. It is not.)*
2. **`курицы` is not separated by literalness at all** — Chicken and Egg are
   both literal. Coverage separates them, and coverage already gets it right.
   So a literal-vs-folded rule applied **at equal coverage** fixes R2 and cannot
   touch `курицы`.
3. **R1 and `сыра` are the same situation with opposite correct answers.**
   Both are a coverage-1000 fold-only record against a coverage-500 literal one.
   Any rule that promotes Butter over the oils also promotes *Cheese substitute,
   mozzarella* over *Cheese, blue*. I looked for a separating property and found
   none; fan-out does not do it either (`сыр` 74 records > `масла` 62).

---

## 2. What each candidate does, re-measured

Sweep populations, all built from the shipped data: 3005 single catalog tokens,
14032 lemma-table words not in the catalog, 6306 half-typed prefixes (≥4 chars),
7600 adjacent two-token name pairs — 30 943 queries.

Only queries whose winning tier is TOKEN_SUBSET can move: no candidate changes
`matchesForm`, so tier membership is identical to HEAD everywhere. Asserted, not
assumed — 480 sampled skipped queries × 4 variants, 0 differences.

| | MUST FIX | MUST KEEP | corpora | top records changed | of which restore pre-fold | coverage inversions |
|---|---|---|---|---|---|---|
| union only (agent 2 gate) | 3/6 | 13/13 | identical | **0** | — | none possible |
| CAND-B = literal tiebreak + union | 5/6 | 13/13 | identical | **17** | **17** | **none possible** |
| CAND-A = literal dominates + union | 6/6 | 13/13 | identical | 33 | 33 | **yes** |
| FM = form multiplicity + union | 6/6 | 13/13 | identical | **161** | 17 | **yes** |
| CAND-B + `масло` synonym | **6/6** | **13/13** | identical | 17 (+9 `масло` forms) | 17 | none possible |

**The decisive structural fact.** CAND-B's key is `(-coverage, foldFree, id)`,
so it can only reorder records that already had the *same* coverage: it replaces
an arbitrary tiebreak (canonical id) with a meaningful one. CAND-A and FM both
put their term *above* coverage, and both therefore invert it — measured:

* CAND-A: `сыра` 1000 → 500, `груша` 1000 → 333, `пряность` 1000 → 333.
* FM: `пряность` 1000 → 333 (agent 1's own report says the term must outrank
  coverage or R1 is not fixed, "and that is inherently where the damage lives").

A rule that can demote a record whose whole name the query names, in favour of
one covering a third of a longer name, contradicts the ranking rule the file is
built on and documents at length. That is disqualifying on its own.

### Named foods that get worse

**CAND-B — 16 distinct queries, every one of them the pre-`bcf034fd` answer:**

* `свиней` Lard → **Pork, fresh, backfat, raw** — required fix.
* `изделие` Sausage, Berliner → **Pasta, gluten-free** — improvement (`макаронные изделия` is pasta).
* `птицы` Chicken → **Poultry, mechanically deboned, from backs and necks** — **the one real regression.** Equal coverage (500 vs 500); Poultry carries `одомашненные птицы` literally, Chicken reaches `домашняя птица` only through the fold. Defensible by the rule, ugly for a user.
* `лист` Amaranth leaves → Beets (`лист свёклы`), `листьями` → Endive — leaf records either way.
* Twelve more are bare adjectives and category nouns naming no food, where today's answer is as arbitrary as the new one: `заменитель`, `индийская`, `корень`, `листовая`, `обыкновенная`, `овощная`, `овощной`, `приправы`, `свежая`, `съедобные`, `ягода`.

**CAND-A adds 15 more, and seven are user-visible losses on commoner words:**
`сыра`/`сыру` Cheese, blue → **Cheese substitute, mozzarella**; `молока` Milk → **Milk substitutes**; `груша` — the dictionary form of *pear* — Pears → **Avocados**; `лимона` Lemons → **Lemon juice**; `грейпфрута` Grapefruit → **Grapefruit juice**; `проса` Millet → **Millet flour**; `пряность` Spices → **Chives**.

**FM changes 161 top records over 14 paradigms**, and only 17 restore pre-fold —
the rest are answers neither version ever produced. Its losses: `птица`/`птицы`
→ mechanically deboned Poultry (11 queries), `лист`/`листья` → Beets (19),
`соевый` Soybeans → **Soy sauce** (13), `клубни` Potatoes → **Sweet potato**
(10), `пряность` Spices → **Chives** (6), plus neutral churn (`мука` Potato
flour → Millet flour, `сок` Apple juice → Pineapple juice, `китайский`
Kiwifruit → Cowpeas, `домашняя` Lard → Duck).

### The two unions are not the same union

Agent 1 gates on "some query token is reachable by the lemma table"; agent 2
gates on "no hit was reached literally". Over 3005 catalog tokens agent 1's gate
fires on 3 queries and appends 176 records; agent 2's fires on 0. The worst case
is real: **`сыра` goes from 75 hits to 239** under agent 1's gate, because 164
fish records carry `сырая рыба` and `сырая` starts with `сыра`. Agent 2's gate
cannot do that — where there is literal evidence it never opens. Both fix R3
identically (`паст` 19→22, `спарже` 7→16, `корне` 2→5).

---

## 3. R1 is a vocabulary gap, not a ranking bug

No record carries the string a person types: `масло` has fan-out **0** as a
name. The oils concept `agrovoc:c_5334` has exactly one Russian name, the bare
plural `масла`, no match terms and no Wikidata source — a category label the
fold made reachable from the singular that colloquially means *butter*.

Modelled the fix (add `масло` to concept `agrovoc:c_1162`, butter) on the real
3519 records. The affected query set is **exactly enumerable** — a new
searchable form can only move a query every one of whose tokens can satisfy it —
so this is exhaustive over a 32 134-query universe, not sampled:

* 11 queries can reach the new form; **9 change**.
* `масло` → **Butter, salted** at **EXACT** (69 hits → 6). R1 fixed above every tier.
* `масла` stays **Oil** at EXACT — the oils concept's own stored name.
* **8 more move that agent 2 did not report**: `масл`, `масел`, `масле`,
  `маслом`, `маслу`, `маслам`, `маслами`, `маслах` all flip Oil → Butter.
* RussianPhraseCorpus with the regenerated butter record: **162/170, 67/149,
  58/93, 60/60 — unchanged**.

No unrelated food gets worse. Compare with the ranking route, which costs four
(FM) to eight (CAND-A) named Russian foods. The data fix wins on every axis
except one: `food-synonyms.json` is generated, so this needs a curated additive
term table in `scripts/generate-food-synonyms.py` — the exact shape of the
existing `JUNK_ALIASES` (keyed, reasoned, dated), inverted.

---

## 4. Verdict

**Apply two changes. Do not ship a ranking term that outranks coverage.**

1. `CatalogSearch.kt`: fold-free evidence as a tie-break inside TOKEN_SUBSET,
   and a fold-only TOKEN_SUBSET no longer silencing PREFIX. Fixes R2 and R3.
2. `scripts/generate-food-synonyms.py`: a curated additive term, `масло` on
   `agrovoc:c_1162`. Fixes R1.

Reject CAND-A (inverts coverage; breaks `сыра`, `молока`, `груша`). Reject FM
(inverts coverage; 161 changed tops, 144 of them answers no version produced;
tuned cap constant; and by agent 1's own measurement it cannot reach R3 at all).
Do **not** revert `bcf034fd` — its recall gain is +61 quantity, +29 single, +42
two-item corpus cases, re-measured, and CAND-B recovers its ranking damage.

### Exact change — `CatalogSearch.kt`

`IndexedRecord`, new member beside `mentions`:

```kotlin
/**
 * Whether the query lands here without the lemma table's help: every typed
 * token is literally a token of one stored form. This is the pre-fold
 * TOKEN_SUBSET predicate, kept as a named question rather than deleted,
 * because ranking has to tell evidence from inference. A record whose stored
 * name carries the typed word is evidence; one the fold walked to is a guess,
 * and a guess may order evidence but must not outrank it.
 */
fun matchesWithoutFolding(queryTokens: List<String>): Boolean =
    anyForm { _, formTokens -> queryTokens.all { it in formTokens } }
```

`searchExact`, replacing the body of the `for (tier in MatchTier.entries)` loop:

```kotlin
for (tier in MatchTier.entries) {
    val hits = indexed.filter { tier.matches(normalizedQuery, queryTokens, it) }
    if (hits.isEmpty()) continue
    if (tier != MatchTier.TOKEN_SUBSET) return rank(tier, normalizedQuery, queryTokens, hits)

    // Ranked on coverage first, exactly as before, so this can only reorder
    // records that already tied — it replaces canonical id, which is arbitrary,
    // with literal evidence, which is not. `свиней` is that tie: Lard and all
    // twenty pork cuts cover `свинья` completely, and only pork carries the
    // typed word itself, in `мясо свиней`.
    val literal = hits.filter { it.matchesWithoutFolding(queryTokens) }
        .mapTo(mutableSetOf()) { it.canonicalItemId }
    val ranked = hits
        .sortedWith(
            compareByDescending<IndexedRecord> { it.coverageOf(tier, normalizedQuery, queryTokens) }
                .thenBy { if (it.canonicalItemId in literal) 0 else 1 }
                .thenBy { it.canonicalItemId },
        )
        .map { result(tier, normalizedQuery, queryTokens, it) }
    if (literal.isNotEmpty()) return ranked

    // Nothing here was reached literally, so this tier exists only because the
    // fold walked to it — and first-tier-wins would then delete the prefix
    // reading of the same keystrokes. `паст` is four characters into
    // `пастернак` and simultaneously the genitive plural of `паста`, so parsnip
    // was not demoted, it was absent. Appended and never merged: a PREFIX
    // record must not outrank a TOKEN_SUBSET one, and appending cannot move a
    // hit that was already here.
    val matched = hits.mapTo(mutableSetOf()) { it.canonicalItemId }
    val prefixOnly = indexed.filter {
        it.canonicalItemId !in matched && MatchTier.PREFIX.matches(normalizedQuery, queryTokens, it)
    }
    return ranked + rank(MatchTier.PREFIX, normalizedQuery, queryTokens, prefixOnly)
}
```

with the two helpers the loop now shares:

```kotlin
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

private fun result(tier: MatchTier, query: String, tokens: List<String>, record: IndexedRecord) =
    CatalogSearchResult(
        canonicalItemId = record.canonicalItemId,
        displayName = record.displayName,
        strength = MatchStrength(tier, record.coverageOf(tier, query, tokens)),
    )
```

The `MatchTier` KDoc claim that PREFIX staying raw means "someone mid-word would
lose a hit that works today" is now false in one direction and must be corrected:
it only ever covered prefixes that are not themselves word forms, and `паст`,
`спарже` and `корне` are the counterexamples.

### Exact change — `scripts/generate-food-synonyms.py`

New table beside `JUNK_ALIASES`, applied where a concept's per-language `match`
list is assembled:

```python
# --------------------------------------------------------------------------
# Curated additive terms
# --------------------------------------------------------------------------
# The inverse of JUNK_ALIASES, on the same terms: reviewed, reasoned, dated.
# AGROVOC and Wikidata between them do not always carry the bare colloquial
# word, and where the missing word is ALSO an inflected form of some other
# concept's name, the lemma fold sends it to the wrong food with full
# confidence. Verified against the shipped catalog before being added.
MANUAL_TERMS: Tuple[Tuple[str, str, str, str], ...] = (
    (
        "c_1162", "ru", "масло",
        "the ordinary Russian word for butter. AGROVOC has only the qualified "
        "`сливочное масло`, and the oils concept c_5334's sole Russian name is "
        "the bare plural `масла`, which the lemma table folds onto `масло` -- so "
        "without this the commonest word for butter answers soybean oil. "
        "Verified 2026-08-02 against GeneratedCatalog.kt: `масло` has fan-out 0.",
    ),
)
```

Then regenerate `food-synonyms.json`, `GeneratedCatalog.kt`,
`GeneratedRussianForms.kt` and both corpora with the existing generators.

### Tests that must land with it

Neither committed corpus exercises any of this: **every** variant measured here
scores identically to HEAD on all 1478 + 530 cases. `RankingRegressionTest` is
the only thing that moves. The minimum honest surface:

* `RankingRegressionTest` as committed (it passes: 6/6).
* The accepted loss, named: `птицы` now answers *Poultry, mechanically
  deboned…* rather than Chicken, with the reason beside it.
* The tie R2 really is: assert Lard and Pork both reach coverage 1000 for
  `свиней`, so a future change that "fixes" it by moving coverage is caught.
* `масла` still answers an oil after the synonym addition, and the eight oblique
  forms of `масло` now answer butter.
* The union: for `паст`/`спарже`/`корне` the first hit is unchanged and the list
  only grows.

---

## 5. Claims I could not reproduce

* **Brief:** "Butter falls to rank 62 of 69" for `масло`. Measured **63 of 69**;
  62 oil records sit at coverage 1000. Both candidate agents also measured 63.
* **Brief:** R2 is a 1000-vs-500 coverage shape like R1. Measured: **an exact
  tie at coverage 1000** broken by canonical id. This changes what fixes it.
* **Agent 2:** the `масло` synonym addition means "nothing else moves". **Eight
  further queries move** (`масл`, `масел`, `масле`, `маслом`, `маслу`, `маслам`,
  `маслами`, `маслах`, all Oil → Butter). The recommendation survives; the claim
  does not.
* **Agent 2:** CAND-A's named losses are seven plus one marginal. Measured
  **fifteen** changes beyond CAND-B, including an eighth clear loss agent 2 did
  not list — `пряность` *Spices, allspice, ground* → *Chives, raw*, a coverage
  inversion 1000 → 333.
* **Agent 1:** "152 single tops changed" for FM. On my populations FM changes
  **161** tops (20 catalog tokens + 135 lemma-table words + 6 prefixes). Same
  order of magnitude, different population; not a contradiction, but nobody
  should quote either number as *the* number.
* **Agent 1's verdict "take the union unconditionally"** is right about the
  union and wrong about which gate. Under agent 1's gate `сыра` returns 239 hits
  instead of 75.

## 6. Residual risk

* **This is a model.** No Gradle task ran and no Kotlin compiled. The model
  reproduces every published number exactly and both engines agree on full
  ordered result lists, which is the strongest evidence available without a
  build, and it is still not a green test.
* **`паст` still shows Pasta first**; Parsnips arrive at rank 20 of 22. The
  committed test asserts presence, and passes. If the intent is that a
  half-typed word should *rank*, this is not enough.
* **`птицы` is a real regression** and there is no version of the literal rule
  that avoids it — it is the tie the rule exists to break.
* **The synonym route needs network.** `generate-food-synonyms.py` reads live
  SPARQL endpoints. If the endpoints have moved since 2026-08-02 the regenerated
  table will differ in ways unrelated to this fix; diff it before committing.
* **Regenerating the corpora adds cases** built from the new term. I modelled
  the catalog side (numbers unchanged) but could not model the new cases.
* **Determinism verified**: 1012 probes × 3 record shuffles × 5 variants, full
  ordered result lists, **0 order-dependent answers**. `foldFree` is a set used
  only for membership, never iterated; the comparator is total on
  (coverage, literal, canonicalItemId) and ids are unique.
* **Non-Cyrillic is provably unchanged**, not merely measured: every key *and*
  every value of `russianFormToLemma` is Cyrillic (checked), so for an all-Latin
  query `sharesLemma` is plain equality, every hit is fold-free, the tie-break is
  inert and the union never opens. Measured too: 0 non-Cyrillic changes anywhere
  in 30 943 swept queries.
* **`MealSearch` is unaffected**: the returned list stays monotone in tier
  ordinal (0 violations over 1806 probed queries), so its stable
  `sortedBy { tier.ordinal }` is a no-op on food hits and `MealSearchTest`'s
  invariant holds. `CatalogSearchTest`'s "ordered by canonical id, not catalog
  order" also still passes — its records are Latin and therefore all fold-free.
