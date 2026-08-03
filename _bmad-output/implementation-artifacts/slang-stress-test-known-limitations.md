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

## What was deliberately NOT attempted: a curated slang-synonym batch

`shroom`->mushroom, `taters`->potato, `nanner`->banana, `PB`->peanut butter,
`avo`->avocado, `za`->pizza, `cheez`->cheese, `chkn`->chicken are common,
unambiguous, and would each be a well-scoped MANUAL_TERMS addition on the
масло precedent (scripts/generate-food-synonyms.py, reviewed and dated).
Not added in this pass: deciding which slang terms are established enough
to earn a permanent catalog entry is a curation judgment call the codebase
already treats as requiring individual review (JUNK_ALIASES' own header:
"found during review... NOT a clean bill of health"), and batching eight-plus
unreviewed entries in one pass is the scope creep AGENTS.md's suppression
guidance warns against, applied to data instead of code. A future pass
should add these one at a time, each with its own fan-out check and dated
justification, the way масло was added.
