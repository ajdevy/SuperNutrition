# Known, measured limitations of typed text search

Produced by a 20-round, 875-scenario multilingual slang/edge-case sweep
(20 parallel scenario-generation agents across en/es/ru, each scenario
verified against a Python model of the real engine that was validated
against Gradle-measured numbers before use). Fixed defects are shipped as
separate commits with their own tests; this file records what was measured,
found real, and deliberately NOT attempted in this pass, with the evidence
for each call.

Every category below is a genuine capability gap, not a bug: the matcher
does exactly what it is designed to do, and the design does not cover these
inputs. Each is comparable in scope to a feature already built this session
(Russian case declension took a generated 13,284-form table and its own
commit series) — none of them is a small patch.

## 1. Code-switching and embedded-word extraction (39 + 35 measured misses)

"I want pollo for dinner", "so I had like some chicken and also a bit of
rice with it and maybe some broccoli too" — a real food word sits inside an
English/Spanish/Russian sentence with no comma, conjunction, or newline
around it. `TextFragments.of()` only segments on those three signals, by
design (`AGENTS.md`: a description is a food-sized list, not free text), so
the whole sentence is handed to `searchExact` as one fragment and fails
because the fragment carries dozens of tokens no record satisfies.

Solving this is a different feature — sliding-window or NER-style extraction
over arbitrary prose — not a matcher fix. Out of scope here.

## 2. Russian transliteration, Latin-script typing (33 measured misses)

`moloko`, `kuritsa`, `khleb` for молоко/курица/хлеб. The catalog and the
lemma table are Cyrillic-only; there is no Latin-to-Cyrillic mapping at any
layer. Building one is a full second morphological subsystem, comparable in
size to the Russian case-declension work already shipped this session, and
carries its own ambiguity problems (`ya` could be `я`/`йа`/`ья`; `x` could be
`х` or "ks").

## 3. Russian and Spanish colloquial diminutives (30 + 27 + 18 measured misses)

`яблочко`, `картошечка`, `курочка` (Russian); `quesito`, `pancito`, `jitomate`
(Mexican Spanish); `palta`/`choclo`/`durazno` (regional Spanish synonyms).
`russianFormToLemma` covers CASE declension, generated from OpenCorpora —
diminutive SUFFIXES are a different morphological category the generator was
never asked to cover, and Spanish has no lemma table at all. Each of these
is its own generation project, not a one-line addition.

## 4. Preparation slang (33 measured misses)

"nuked chicken", "zapped potato", "жарёшка", "печёная картоха". No
PREPARATION_WORDS-style slang table exists for any language; the shipped
one is a fixed, formal English vocabulary (raw, boiled, roasted...).

## 5. Compound-failure stress cases (36 measured misses)

Queries deliberately stacking misspelling + slang + quantity + wrong case in
one string ("2 tamatoes n sum CHIKKEN breest"). These are downstream of
categories 2-4 and the coverage-ranking work (see the separate panel); no
single fix addresses them, and most resolve once their individual component
gaps are closed piecemeal (which none of the above are, in this pass).

## 6. Genuinely unresolvable inputs (correctly returning nothing)

`veggies`, `chow`, `grub`, generic category words; `no onions` and its
family (fixed separately as an exclusion-safety bug, 6b30bbd5); pure brand
names with no single-ingredient equivalent (`cheerios`, `big mac`); bare
single letters and short fragments. These are not gaps -- returning nothing
is correct, and several are asserted as such in `GeneratedCatalogTest`.

## What WAS fixed from this same sweep, for contrast

- `no nuts, allergic` -> logged nuts. Fixed: 6b30bbd5 (NEGATION_WORDS guard).
- `chiken`/`yogert`/`sammon`/`cafe`, and separately bare `chicken`/`milk`/
  `творог` with no typo at all -- a short unrelated form outranking the
  correct long-form record, confirmed to also hit perfectly-typed common
  words via a plain TOKEN_SUBSET tie, not only FUZZY. Under active repair by
  a dedicated measurement panel (wf_089c9da3-f3c); ships separately once the
  candidate is verified on the real engine.

## The curated slang-synonym batch: worked through one at a time (2026-08-03)

`shroom`->mushroom, `taters`->potato, `nanner`->banana, `PB`->peanut butter,
`avo`->avocado, `za`->pizza, `cheez`->cheese, `chkn`->chicken were originally
recorded here as "each a well-scoped MANUAL_TERMS addition on the масло
precedent, not attempted in this pass." Worked through individually, per
term, with an independent fan-out and FUZZY-tier safety check against the
real GeneratedCatalog.kt before each one landed -- some of the eight turned
out not to fit that description at all:

- **Added as MANUAL_TERMS** (scripts/generate-food-synonyms.py, dated
  2026-08-03 entries, applied via `--apply-curated-terms-offline` and
  verified with `SlangSynonymTest.kt`): `shroom` (c_2487), `taters`
  (c_13551), `nanner` (c_806), `cheez` (c_1507), `chkn` (c_1540).
- **Needed no addition**: `avo` already reaches the avocado record via the
  PREFIX tier today. Verifying this surfaced a real, separate defect,
  though -- it was landing on "Oil, avocado" instead, the identical
  short-form-beats-long-form shape as chicken/yogurt/salmon/cafe. Fixed as a
  `RESTATES_HEAD_EXCEPTIONS` carve-out in generate-catalog-kotlin.py (same
  mechanism as the chicken fix: the word already existed in AGROVOC source
  and only needed to stop being dropped as restating its own head), not as
  a MANUAL_TERMS entry.
- **Genuinely not fixable by either established mechanism**: `PB` and `za`.
  `PB` (peanut butter): no AGROVOC concept names peanut butter at all, so
  MANUAL_TERMS has nothing to attach to; SUBHEAD_OVERRIDES cannot originate
  a new alternate-name group for this head either, because
  `apply_subhead_overrides()` only ever splits an ALREADY-populated group
  (its own `record["group"] is not None` guard), and peanut butter's head
  has no AGROVOC join to populate one. `za` (pizza): no AGROVOC concept for
  pizza either, and the only genuinely plain-pizza USDA source records
  (`food:usda-srl-21224`/`21225`/`21226`/... "Pizza, cheese topping, ...")
  are dropped by the generator's own segment/token selection caps
  (`DEFAULT_MAX_SEGMENTS`/`DEFAULT_MAX_NAME_TOKENS`) before either curated-
  term mechanism ever runs -- recovering them is a whole-catalog cap
  tradeoff, not a scoped slang fix. Both remain genuine catalog gaps,
  correctly returning nothing today, same as `cheerios`/`big mac` above.
