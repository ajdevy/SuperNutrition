# Coverage-fix candidate: display-name priority

Angle assigned: give a hit through a record's own display name more ranking
weight than a hit that only came through an alternate/synonym name, on the
theory that a record's own name is stronger evidence than a borrowed one.

**Verdict up front, so it is not buried: do not ship this angle as designed.**
Precisely measured, it cannot fix 3 of the 4 target cases at all (it is
mathematically incapable of touching them), fixes the 4th only in a more
aggressive form than the assigned framing describes, and that more aggressive
form opens a systematic new loss class -- wrong answers for plain, common
single-word produce/meat queries -- that is wider than the defect it closes.
Full evidence below.

## Model validation (do this before trusting anything else in this file)

Built on an already-present, independently re-verified Python port
(`engine_model.py` in the scratchpad) of `CatalogSearch.kt` at HEAD
(`22832480`): `IndexedRecord.coverageOf`, the four-tier loop in `searchExact`
including the TOKEN_SUBSET literal-vs-folded tiebreak and the PREFIX union,
`TextFragments` segmentation, `namesNoFood`/`stripNonFoodEdgeWords`, and
`TextIdentification.fallBack`. I added the one piece it explicitly skipped
(`searchRelaxingPreparation`, the third `search()` fallback) for full
fidelity, and independently re-confirmed the two facts the task told me to
re-verify rather than trust:

- **Record count.** `grep -c 'CatalogRecord(' GeneratedCatalog.kt` (which,
  unlike `grep -c 'CatalogRecord("food:usda-srl-'`, also counts the
  multi-line records where the id is not on the same source line) gives
  **3519**, and the parser's own `assert len(records) == 3519` passes.
  Confirmed independently, not just inherited.
- **Parsing pitfalls named in the task.** The catalog parser is a
  parenthesis-balanced scan (handles multi-line `CatalogRecord(...)`
  entries such as `usda-srl-01042`, which a single-line regex would drop),
  and Cyrillic tokenization goes through `re.findall(r"[^\W_]+", s,
  re.UNICODE)`, not a `\b`-based pattern, avoiding the BSD-grep `\b`
  Cyrillic failure the task warned about.

Ran the model **unmodified** against every number the task supplied as
ground truth, before writing any candidate code:

| Corpus | Class | Task's number | Model's number (unmodified code) |
|---|---|---:|---:|
| RussianPhraseCorpus (260 records, 530 cases) | negatives (gated) | 60/60 | **60/60** |
| | ru-inflected-quantity | 162/170 | **162/170** |
| | ru-inflected-single | 112/149 | **112/149** |
| | ru-inflected-two-item | 61/93 | **61/93** |
| MealTextCorpus (300 records, 1478 cases) | exact | 450/450 | **450/450** |
| | alias | 600/600 | **600/600** |
| | en two-item+quantity | 80/80 | **80/80** |
| | negatives | 188/188* | **187/188** |

\* The one negative failure is the query that parses (via my balanced-paren
string extractor) to the raw two-character sequence `\n`; the task named
this exact artifact ("a lone newline character... a known test-harness
parsing artifact") and told me it may be excluded. I did not need to
re-diagnose it -- the model reproduces it exactly (same single failure, same
count, out of 188), which is itself corroborating evidence the port is
faithful. Excluded as instructed; every other number is an **exact match**,
including the RankingRegressionTest.kt shapes (свиней tie at identical
tier+coverage, паст/спарже/корне presence, паст still topping Pasta, птицы
still the accepted Poultry loss, курицы/яйцо/масло/свиней/морковь/свинина/
курица all correct). The model is trusted from here on that basis.

A second, inverted-index engine was built for the sweep (thousands of
queries would take too long on the linear-scan model) and **cross-validated
against the slow model on 509 sampled catalog words with zero mismatches**
before being trusted for the sweep numbers below. (First pass found 3
mismatches from a bug in the fast engine's own lemma-equivalence-class
construction -- unrelated to the candidate, fixed, re-validated to 0/509.)

## The four instances, precisely: is it actually display-vs-alternate?

The assigned angle assumes the wrong winner reaches its coverage through an
*alternate* name while the correct answer's evidence sits in its *own
display* name. The task explicitly asked me to check this asymmetry rather
than assume it. Measured per-form (which searchable form produced the
winning coverage, and is it form index 0 = the record's own display name,
or an alternate):

| query | wins today | via | coverage | should win | via | coverage |
|---|---|---|---:|---|---|---:|
| `chiken` | Egg, whole, raw, fresh | **ALT** `chicken egg` | 500 | Chicken, broilers... | **ALT** `chicken meat` | 500 |
| `yogert` | Tofu yogurt | **OWN** `tofu yogurt` | 500 | Yogurt, plain, whole milk | **OWN** `yogurt, plain, whole milk` | 250 |
| `sammon` | Fish oil, salmon | **OWN** `fish oil, salmon` | 333 | Fish, salmon, chinook, raw | **OWN** `fish, salmon, chinook, raw` | 250 |
| `cafe` | Syrup, Cane | **OWN** `syrup, cane` | 500 | *(no competitor reaches any tier)* | -- | -- |

This kills the assumed pattern outright:

- **chiken**: both the wrong winner and the right answer reach their tied
  coverage through an *alternate* name. Chicken's own display name
  ("Chicken, broilers or fryers, meat only, raw", 7 tokens) also matches,
  but only at coverage 142 -- far below the 500 both sides get through
  their short 2-token alternates ("chicken egg" / "chicken meat"). There is
  no display-vs-alternate asymmetry to lean on; it is a genuine ALT-vs-ALT
  tie, and today's code breaks it by canonical id, where Egg's id
  (`food:usda-srl-01123`) sorts before Chicken's (`food:usda-srl-05011`).
- **yogert** and **sammon**: both sides win through their *own* display
  names. There is no alternate involved in either winner's evidence at all
  (Yogurt's alternates `yoghurt`/`yogur`/`йогурт` don't near-match `yogert`;
  the generic `fish` alternates don't mention salmon). The gap is pure
  **display-name-length magnitude**: a 2-token own name (`tofu yogurt`) and
  a 3-token own name (`fish oil, salmon`) score higher fractions than a
  4-token own name, full stop. A display-vs-alternate preference has
  **nothing to discriminate on** here -- both records are already "own
  evidence."
- **cafe**: `coffee` fails the FUZZY length-diff bound before edit distance
  is even computed (length 4 vs 6, diff 2 > the allowance for tokens this
  short), so no coffee record reaches any tier at all. There is no
  competing candidate for a display-name rule to promote over Syrup, Cane.
  This is a reachability problem the ranking layer never sees, not a
  ranking problem.

**Conclusion of this measurement: a rule that literally does "prefer a hit
through the record's own display name over a hit through an alternate
name" is a no-op for yogert, sammon, and cafe (mathematically -- there is
no case to make it fire), and only a coin-flip-shaped tiebreak candidate
(id-independent, still needs *something else* to pick a winner) for chiken.**
This is exactly the caution the task raised ("it might cut the other way for
some of the four cases") and it was right to raise it.

## What was actually built and measured

Given the above, the only version of "display name priority" that can move
anything is not a display-vs-alternate preference but a same-category,
own-evidence vs. no-own-evidence distinction, applied as a **pure tiebreak
layered beside canonical id** (never overriding coverage magnitude, which
the task correctly flagged: yogert 500 vs 250 and sammon 333 vs 250 are not
ties, and a magnitude override is a much larger, differently-shaped change
this report does not attempt or recommend).

**Candidate primitive**: `matchesOwnDisplayName(tier, query, tokens)` --
does the record's own display-name form (index 0), by itself, satisfy the
tier's match predicate? This is boolean, reuses the tier's own match rule
(no new comparison logic), and is inserted as a tiebreak key ahead of
canonical id (and, in TOKEN_SUBSET, after the existing 17413c34
literal-vs-folded key, so it cannot disturb any case that key already
resolves -- свиней and птицы are unaffected because their tie is already
broken before this key is ever consulted).

Two variants were built and measured:

- **Variant A** -- the primitive alone.
- **Variant B** -- A, plus a second, finer tiebreak layered after it:
  `ownDisplayHeadAccountedFor` -- is the record's own display name's HEAD
  word (token 0; USDA SR Legacy names are `Head, qualifier, qualifier...`)
  itself accounted for by some query token? This exists because variant A
  alone is not enough to fix chiken: "Fat, chicken" (own display literally
  contains "chicken", 2 tokens, coverage 500) ties with every real
  "Chicken, broilers..." record under variant A, and "Fat, chicken"'s id
  (`04542`) sorts before all of them (`05001`+), so variant A's `chiken`
  answer is **"Fat, chicken," not chicken meat** -- a real improvement over
  Egg, but not a fix by the task's own `startsWith("Chicken")` convention.
  Variant B's head check excludes "Fat, chicken" (head = "fat", not
  "chicken") while keeping every real "Chicken, broilers..." record (head =
  "chicken"), and the true chicken-meat record wins.

## MUST FIX

| case | variant A | variant B |
|---|---|---|
| chiken -> Chicken | **partial**: lands on "Fat, chicken" (real chicken product, wrong cut) | **fixed**: "Chicken, broilers or fryers, meat and skin and giblets and neck, raw" |
| yogert -> Yogurt, plain | **not fixed** (proven impossible for this angle, see above) | **not fixed** (same) |
| sammon -> real salmon food | **not fixed** (proven impossible) | **not fixed** (same) |
| cafe -> reaches Coffee or stays unreachable | **unchanged, stays unreachable** (correct/defensible outcome, unaffected either way) | **unchanged, stays unreachable** |

Also checked, not asked for but revealing: variant A does **not** fix `turkey`
(stays "Fat, turkey," same shape as chiken) or `pork` (stays "Bologna,
pork") -- it is not a reliable partial fix in general, only accidentally
adequate when no lower-id "wrong-part-of-the-animal" record exists. Variant
B fixes `chicken`/`chiken`/`chickaen`/`cicken` (chicken meat),
`turkey`/`trkey`/`turkev` (turkey meat), and `pork`/`poruk` (pork itself),
consistently. **This is why variant B, not A, is the candidate carried
through the rest of this report** -- A is strictly weaker while paying the
same cost (below).

**Honest scoring: 1 of 4 fixed (chiken, variant B only). 3 of 4 are a
documented, evidence-backed "cannot fix via this angle," not a
skipped-for-convenience call.** cafe's "stays unreachable" outcome matches
the task's own stated acceptable fallback.

## MUST KEEP -- RankingRegressionTest.kt shapes, variant B

All re-checked directly against the model, not assumed carried over:

| check | result |
|---|---|
| курицы/курицу/курицей/"200 граммов курицы" -> Chicken | unchanged, pass |
| яйцо/яйца/"яйцо курицы" -> Egg | unchanged, pass |
| масло -> Butter, масла -> Oil | unchanged, pass |
| свиней/"200 граммов свиней" -> Pork | unchanged, pass |
| свиней tie: Lard and Pork at identical (tier, coverage) | unchanged -- still a real tie, still TOKEN_SUBSET/1000 both sides |
| паст/спарже/корне reach Parsnips/Broccoli/Celeriac (presence) | unchanged, pass |
| паст still tops Pasta | unchanged, pass |
| морковь/свинина/курица match at EXACT/TOKEN_SUBSET as before | unchanged, pass |
| брюссельска/морков/свинин still resolve | unchanged, pass |
| птицы -> Poultry (accepted loss) | unchanged -- still Poultry, the accepted loss is not disturbed |

Every one of these is resolved before the new tiebreak key is ever consulted
(either a different tier, or the existing literal-vs-folded key already
separates them), which is why zero of them moved.

## NO REGRESS -- full corpora, variant B vs. baseline

| Corpus | Class | Baseline | Variant B |
|---|---|---:|---:|
| RussianPhraseCorpus | negatives (gated) | 60/60 | **60/60** (identical) |
| | ru-inflected-quantity | 162/170 | **162/170** (identical) |
| | ru-inflected-single | 112/149 | **112/149** (identical) |
| | ru-inflected-two-item | 61/93 | **61/93** (identical) |
| MealTextCorpus | exact | 450/450 | **450/450** (identical) |
| | alias | 600/600 | **600/600** (identical) |
| | en two-item+quantity | 80/80 | **80/80** (identical) |
| | negatives | 187/188 | **187/188** (identical, same single known artifact) |

**Every number is bit-for-bit identical, not just "at least as good."**
Negatives stay at exactly 100% (RussianPhraseCorpus) / the one pre-existing
known artifact (MealTextCorpus). Zero non-Cyrillic queries in either curated
corpus change their outcome. This is expected, not surprising: both corpora
mostly test literal display/alias-name matches and curated multi-item
phrases, not the open-vocabulary bare-noun and misspelling space where the
candidate's mechanism (and its cost, below) actually lives -- which is
exactly why the task asked for an independent sweep rather than trusting
corpus-clean as sufficient.

## SWEEP -- catalog vocabulary and realistic misspellings, variant B vs. baseline

Built from the catalog's own vocabulary (2959 distinct alphabetic tokens
across all 3519 records' searchable forms) and a realistic-misspelling
generator (one random single-letter edit -- substitute/delete/insert/swap
-- applied twice independently to each of ~150 common English/Spanish/
Russian food words), run on the fast, cross-validated engine:

| Sample | Size | Top result changed |
|---|---:|---:|
| Single-token catalog vocabulary | 1500 | 20 (1.3%) |
| Two-token catalog forms | 755 | 1 (0.1%) |
| Realistic misspellings (en/es/ru) | 312 | 29 (9.3%) |
| **Total** | **2567** | **50 (1.9%)** |

That 1.9% churn rate sounds small in isolation, and a naive win/loss count
looks favorable -- of the 50 changes, by manual food-identity judgment:
**18 wins, 12 losses, 20 neutral/ambiguous (lateral moves between two
defensible answers, or queries that were not really food terms to begin
with, e.g. "from", "light").** But a flat count hides the shape, which is
the point of reporting wins and losses separately instead of net:

### Wins (representative, real food names)

| query | before | after |
|---|---|---|
| chicken / chiken / chickaen / cicken | Egg, whole, raw, fresh | Chicken, broilers or fryers, meat and skin and giblets and neck, raw |
| turkey / trkey / turkev | Fat, turkey | Turkey, whole, meat and skin, raw |
| pork / poruk | Bologna, pork | Pork, fresh, backfat, raw |
| cooked | Chicken...**raw** (!) | Chicken, gizzard, all classes, **cooked**, simmered |
| agave | Sweetener, syrup, agave | Agave, raw (Southwest) |
| sauce | Horseradish, prepared | Sauce, barbecue |
| veal | Bratwurst, veal, cooked | Veal, ground, raw |
| dill | Spices, dill seed | Dill weed, fresh |
| muwshroom | Gravy, mushroom, canned | Mushrooms, shiitake, raw |
| bulb | Garlic, raw | Fennel, bulb, raw |

### Losses (representative, real food names) -- and their common mechanism

| query | before (correct) | after (wrong) |
|---|---|---|
| peach / peaoch | Peaches, yellow, raw | **Pie, peach** |
| fruit | Apples, raw, without skin | **Fruit syrup** |
| banapa / banmna | Bananas, raw | **Bananas, dehydrated, or banana powder** |
| beal | Lima beans, immature seeds, raw | **Cracker, meal** |
| orage / oranmge | Oranges, raw, all commercial varieties | **Orange juice, canned, unsweetened** |
| tomao / tomto | Tomatoes, green, raw | **Tomato powder** |
| cawne | Pork, fresh, backfat, raw | **Syrup, Cane** (the exact record from the original `cafe` defect) |
| col | Cabbage, raw | **Collards, raw** (Spanish query, English-orthography-coincidence answer) |

**10 of these 12 losses share one exact, verified mechanism -- not twelve
unrelated misses.** Per-form breakdown, e.g. for `peach`:

```
Peaches, yellow, raw   [correct]: TOKEN_SUBSET hit ONLY via ALT "peach fruit" (2 tokens) = 500
                                   own display "peaches, yellow, raw" does NOT satisfy
                                   TOKEN_SUBSET at all -- "peach" != "peaches" and TOKEN_SUBSET
                                   folds Russian lemmas only, never English plurals.
Pie, peach              [wrong]:  TOKEN_SUBSET hit via OWN display "pie, peach" (2 tokens) = 500
```

USDA SR Legacy names almost every raw fruit/vegetable/meat record with a
**plural head noun** ("Bananas, raw", "Peaches, ...", "Oranges, ...",
"Tomatoes, ...", "Lima beans, ..."). TOKEN_SUBSET folds Russian morphology
but never English plurals, and FUZZY's edit-distance-plus-length-diff bound
frequently rejects a genuine singular/plural pair at ordinary word lengths
(`bananas`/`banapa`: edit distance 2, over the allowance of 1 for 6-7
letter tokens). The result: the correct record's own display name **cannot
satisfy the tier at all** for a singular-shaped query, so it depends
entirely on an alternate name to be found -- while this candidate's whole
premise, "trust the record's own name over a borrowed one," is exactly
backwards for that record. Any *other*, often much less relevant, record
whose own name happens to carry the singular form -- a pie, a juice, a
powder, a cracker, dehydrated fruit, an unrelated word ("cane") -- gets
promoted over it. `col` is a distinct, second mechanism: the head-token
refinement (variant B only) applies a raw `startsWith` check independent of
which tier and which language actually produced the hit, so a Spanish query
("col" = cabbage) can be redirected by pure English-spelling coincidence
("collards" happens to start with the same three letters).

I tested one cheap mitigation before writing this off as unfixable within
the angle: loosen `matchesOwnDisplayName` to reuse the same plural-aware
`isNearMatch` predicate `coverageOf` already uses for counting, instead of
requiring the tier's strict all-tokens match. This rescues `peach` exactly
(both sides become "own evidence," falls through to id, correct answer
wins) but **does not rescue 11 of the other 12 losses**, including the
misspelled form of the very case it just fixed (`peaoch` stays wrong): the
underlying gap is that the plural/singular pair fails `isNearMatch`'s
edit-distance bound *at all* for most of these words, not that the gate was
checking too strictly. Closing that would mean folding English plurals into
tier matching itself (TOKEN_SUBSET and/or FUZZY) -- a materially different
change, with its own catalog-wide blast radius, out of scope for a
display-name-priority tiebreak and not attempted here.

## Determinism

Every sort key is one of: coverage (int), a literal-vs-folded boolean
(existing), the new own-display boolean(s), and canonical id (string) --
no hash-order or catalog-construction-order dependency anywhere in the
candidate. Re-running the sweep is exactly reproducible.

## Final verdict on this candidate

**Do not ship.** Scored honestly against the task's own bar:

- Fixes exactly 1 of 4 target instances (chiken, and only the head-token
  variant B gets it fully right; the simpler variant A lands on a
  wrong-but-related answer).
- The other 3 are not corner cases missed by an incomplete implementation --
  they are **provably unreachable by this angle**, because in 2 of the 3
  the "wrong" and "right" records both win through their own display names
  (no display-vs-alternate asymmetry exists to exploit) and in the 3rd,
  no competing record even reaches the tier.
- In exchange, it opens a **systematic, high-frequency** new loss class:
  any bare or lightly-misspelled singular query for a plural-headed produce
  or protein name (bananas, oranges, tomatoes, peaches, beans -- a huge
  share of USDA SR Legacy's raw-food vocabulary) can be redirected to an
  unrelated processed product whose own short name happens to carry the
  singular form. 10 of 12 sweep losses are this one mechanism, not
  independent misses, which means it is predictable and will recur, not a
  rare coincidence -- and it plausibly triggers on *more common* queries
  (people search "banana," "tomato," "orange" constantly) than the
  obscure-alternate-name collisions the original 4 defects came from.
- This is the same shape of surprise the task's own history flags: a fix
  that "looked right on 3-4 examples" (chiken/turkey/pork/cooked all read
  as clean wins in isolation) and broke a wider, only-visible-on-a-full-sweep
  set elsewhere -- confirming the task's own caution was warranted, not
  performative.

If the underlying idea (a record's own identity is meaningful ranking
evidence) is worth pursuing further, it needs to be paired with closing the
specific tier-matching gap that defeats it here -- English plural folding
in TOKEN_SUBSET/FUZZY -- and then re-measured with the same full battery,
not shipped as a tiebreak layered on top of a known hole.

## Files

- Candidate Kotlin change (not applied to the worktree; described precisely
  below for implementation without re-deriving it).
- Python model, fast cross-validated engine, sweep and forensic-probe
  scripts: `/private/tmp/claude-501/-Users-aj-workspace-ai-SuperNutrition--claude-worktrees-pr-review-c1b4ce/4c1405cc-f69b-4f07-8a66-3508ba5a2f99/scratchpad/{engine_model.py,dnp_engine.py,dnp_fast.py,dnp_sweep.py,dnp_probe.py,dnp_loss_probe.py,dnp_classify.py,dnp_validate_baseline.py,dnp_validate_corpora.py,sweep_output.txt}`
  (session scratchpad, not part of the repo).
