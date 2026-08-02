# Data sources and parsing: research findings

Research to close the two limits measured against the live engine: the parser
handles English multi-item text well and collapses on Russian/Spanish phrases,
and the catalog holds 8 foods and 4 dishes.

Everything below is desk research with sources. Licence readings are not legal
advice, and the two that matter most — ODbL share-alike and the Russian recipe
collections — need a lawyer before anything ships.

## The nutrition layer

**USDA SR Legacy is the answer for generic foods.** Public domain / CC0, 7,793
foods, 14,449 household portions with gram weights covering 7,533 of them. An
agent trimmed it to 13 core nutrients and measured the result: **3.91 MB
uncompressed, 1.41 MB gzipped** — shippable on a low-end device without
argument. Frozen at April 2018 and never to be updated, which is a data-quality
caveat and simultaneously makes its identifiers permanently stable. Its NDB
numbers are documented to survive record updates, so they are the join key, not
`fdc_id`.

**Foundation Foods is smaller than its reputation and cannot replace it.** The
December 2025 release has 78,026 rows in `food.csv` of which only **436** are
actual foundation foods; the rest are sampling provenance. Its `input_food.csv`
has a `gram_weight` column with **0 of 5,849 rows populated**. It also still
carries a referential-integrity defect filed as USDA-APIs issue #120 in
September 2022 — 71 foods missing from the type table, three years on.

**Branded Foods is out on size**: 2.9 GB uncompressed. Also label-transcribed
rather than analytical, and USDA's own quality overview concedes missing required
nutrients are a "soft validation" that does not block submission.

## The dish layer — the hard gap, and there is exactly one clean answer

**USDA FNDDS is the only source that clears licence, shape and scale.** CC0,
5,432 foods and beverages, gram weights for 22,046 portions, and roughly
three-quarters of food codes are built by recipe from two or more ingredients.

Five traps, all verified by the agent rather than assumed:

1. **Ingredient weights are pre-moisture-loss and do not sum to the dish weight.**
   Verified on `fdcId 2706592` "Stew, beef": ingredients sum to 100.7 g for 100 g
   of finished food. Moisture change is a separate recipe-level factor.
2. **FNDDS food codes are not stable** — over 1,800 were discontinued between
   consecutive releases. They cannot be canonical IDs. Join to SR Legacy NDB
   numbers instead.
3. Released every two years with WWEIA/NHANES, so it lags.
4. US food supply, English only.
5. Build-time only: extract in CI, ship a compact table.

Everything else fails outright. **RecipeNLG and Recipe1M+ are non-commercial /
research-only** — not a grey area, the terms say so. **TheMealDB** forbids the
free tier in app stores and is API-only. **Open Food Facts** is packaged goods
with percentages, not dishes with grams — a live pull of Nutella showed every
ingredient with `percent: null` and only `percent_estimate` present. **UK CoFID**
calculates by recipe but publishes only the resulting nutrients, not the recipe.
**Wikibooks Cookbook** is 3,895 recipes with free-text quantities.

**Russian normative recipe collections** (Сборник рецептур блюд) have exactly
what is needed — native dish names, gram weights, hundreds of dishes — and an
**unresolved licence**. They are normative documents, which in Russian law may
exclude them from copyright, but that is a question for a Russian IP opinion, not
an assumption. No machine-readable form exists either; ingestion means OCR.

## Multilingual naming

**AGROVOC as the spine, Wikidata as the colloquial layer.**

AGROVOC (FAO) is CC-BY IGO 3.0 and has genuine trilingual parity: all 41,825
concepts carry en/es/ru prefLabels. But the register is wrong on its own — the
agent verified concept `c_24000` "chicken meat" has Russian prefLabel
**курятина** and zero Russian altLabels, so a user typing **курица** gets nothing.

Wikidata is CC0 — the only obligation-free option — and supplies exactly the
consumer words AGROVOC lacks: Q961769 carries `es: aguacate` with alias `palta`,
which is the regional pair this product already handles. Its weakness is the
tail: only ~12% of sampled vegetable concepts carry any Spanish alias.

Open Food Facts taxonomies are licence-encumbered (ODbL) and lopsided —
measured: Spanish 2,162 entries against English 4,783, Russian only 974. And
`palta` appears only as Quechua, not as a Spanish alias.

FoodOn is English-only and is the project that *has* this problem. LanguaL has no
Russian at all. FooDI-ML covers all three languages in consumer register and is
CC BY-NC-SA, so it is unusable.

## Parsing — the architectural finding

**Move all linguistics to build time; keep the runtime a table-driven matcher in
pure Kotlin.** This is not a preference, it is forced: there is no pure-KMP
morphology stack, and Snowball — the one runtime-shaped candidate, BSD-3 and
otherwise ideal — **has no Kotlin backend** in its compiler. Adopting it means
hand-porting generated Java, which is exactly the kind of translation that
drifts.

**One finding I would act on before adding any vocabulary.** The Open Food Facts
Spanish stopword list contains `y`, `e` *and* `con`. Promoting those to sentence
separators — the obvious fix for `arroz y frijoles negros` — would destroy
**`arroz con leche`**, **`arroz con pollo`** and every other dish whose name
contains the preposition. Spanish `con` is not a separator. The same caution
applies to Russian `с`/`со`.

**Order of operations is a second trap.** "I had a couple of eggs for breakfast"
only resolves if `couple of` is matched as a *quantity* before stopword removal
strips `of`. Quantity vocabulary must run first.

Sources worth taking: **Unicode CLDR** (Unicode License, permissive) for
canonical conjunctions, plural rules and unit display names — but it gives the
canonical form only, not what users type (`+`, `&`, `с`, `да`). **UCUM** for unit
codes, though it has no opinion on `ломтик`, `slice` or `a handful`, which are
portion descriptors resolved per food. **stopwords-iso** (MIT) for filler.
**pymorphy3 + OpenCorpora** (MIT code, CC BY-SA data) as a build-time paradigm
generator for Russian — the share-alike lands on the generated table.

The recipe-ingredient parsers (NYT tagger, `ingredient-parser`, quantulum3) are
English-only, Python, and in recipe register rather than meal-logging register.
Their value is the **label schema** — `{quantity, unit, name, preparation,
comment}` — not the code.

## On-device models, if G-3 ever opens

Not the baseline, but the menu is clearer than expected.

**granite-embedding-97m-multilingual-r2** (IBM, Apache 2.0) is the standout: 97M
params, 384 dims, Russian and Spanish both explicitly in the enhanced-retrieval
set, and smaller *and* better than the 2021–23 generation it replaces. The catch
is integration — there is no official KMP ONNX Runtime artifact, so it means
expect/actual with a JNI-backed AAR and an iOS bridge.

**Model2Vec `potion-multilingual-128M`** (MIT) is the constraint-fit option:
static embeddings, ~91% of teacher quality, but no contextual disambiguation, so
"a slice of rye" and "rye whiskey" sit closer than they should.

**Small quantised LLMs are not viable on mid-range hardware.** Measured on a
Galaxy M55s (Snapdragon 7 Gen 1, 8 GB — better than the low-end floor): Qwen3
0.6B at **3.3 tok/s decode with ~11 s time-to-first-token**. That is not an
interactive food search.

**EmbeddingGemma** is capable but ships under Gemma Terms of Use, not Apache 2.0
— amendable restrictions with a pass-through obligation, which is a materially
different posture from the rest of this repository's dependencies.

**Apple `NLContextualEmbedding` is disqualified on a structural detail**:
English and Spanish live in the Latin-script model and Russian in the
Cyrillic-script model, which are *different models producing vectors in different
spaces*. A Russian query cannot be matched against an English-embedded catalog.

## Proposed sequence

1. **SR Legacy → the catalog**, joined on NDB numbers, built at build time. This
   alone takes the catalog from 8 foods to ~7,800 and is CC0.
2. **FNDDS → the dish layer**, with the moisture caveat recorded next to the
   numbers rather than in a commit message, and FNDDS codes explicitly not used
   as canonical IDs.
3. **AGROVOC + Wikidata → the synonym table**, build-time, per language.
4. **Parser vocabularies as generated data**, quantity-before-stopword ordering,
   and `con`/`с` on a deliberate do-not-split list with a regression test naming
   `arroz con leche`.
5. Models stay behind G-3. Nothing above needs one.

## What needs a lawyer, not an engineer

- ODbL share-alike if Open Food Facts is used at all: the derived database must
  itself be ODbL, and §4.7(b) requires offering an unrestricted copy. Keeping
  OFF-derived data in a physically separate file is the architecture that avoids
  contaminating CC0-derived tables.
- CC BY-SA on a pymorphy3/OpenCorpora-generated form table.
- The Russian recipe collections, which are the only good source of Russian dish
  weights and have no clear licence.
