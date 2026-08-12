# Deterministic catalog search mechanics

The floor beneath CAP-1. Everything here must hold with no model installed, no network, and the lowest supported device tier. This file exists because the planning corpus currently specifies the search surface (FR-14, UX-DR19, EXPERIENCE) but never its behavior.

## Index

| Property | Decision |
| --- | --- |
| Store | SQLite FTS5 over the bundled/cached country package, built at package activation, not at query time |
| Index unit | One row per canonical catalog record, carrying its namespace-qualified ID |
| Indexed fields | Primary display name, known alternate names and regional synonyms, and a normalized form of each |
| Build integrity | The index is derived from an activated, signed package under AD-19; a package rollback rebuilds or restores the matching index atomically |
| Storage budget | Counted against the country package budget, declared per device tier under NFR-3 |

FTS5 is named rather than left open because `LIKE '%term%'` and a real full-text index produce materially different products at catalog scale, and the choice determines the storage budget.

## Query normalization

Applied identically at index build and query time, so the two cannot drift:

- Unicode NFKC, then case folding.
- Diacritic folding, with the unfolded form also indexed so an accented query still matches exactly.
- Punctuation and separator collapse.
- Morphological normalization is per-language and declared in the language matrix below, not global. A synonym list is sufficient for English plurals; it is not sufficient for a heavily inflected language.

## Match semantics

Evaluated in order. The first tier that returns results is the result set; lower tiers do not dilute a confident match.

| Tier | Rule |
| --- | --- |
| 1 — Exact | Normalized query equals a normalized indexed name |
| 2 — Token subset | Every query token appears as a complete record token; extra record tokens are permitted |
| 3 — Prefix | Every query token is a prefix of some record token, in any order |
| 4 — Fuzzy | Bounded edit distance per token, scaled to token length, applied only when tiers 1–3 are empty |

**Token subset must precede prefix.** A complete token is also a prefix of itself, so ordering prefix first makes the token-subset tier unreachable — it could never match anything the prefix tier had not already taken. An earlier revision of this file had them the other way round; implementation surfaced it. Concretely, `rye` is a complete token of `Bread, rye` and only a prefix of `Ryebread`, and the first ordering loses that distinction.

Fuzzy matching is last and bounded. An unbounded fuzzy tier turns a specific query into a wall of near-misses, which is the failure this ordering exists to prevent.

## Ranking

Within the winning tier, order by:

1. Match tier (exact before prefix before subset before fuzzy).
2. Coverage — fraction of record tokens the query accounted for. A query of "rye bread" ranks the record `bread, rye` above `bread, rye, reduced sodium, toasted`.
3. Record specificity — generic and staple records rank above brand-qualified variants, so an unspecific query lands on an unspecific record.
4. Stable tiebreak on canonical ID, so identical inputs produce identical order across devices and runs.

Ranking is deterministic and contains no personalization, no history, and no learned component. The result order is reproducible from the query, the package version, and nothing else — which is what makes it testable against golden fixtures.

## Presentation and control

- Results appear as the user types, debounced, with a stated maximum result count rather than infinite scroll.
- Each result shows enough to disambiguate without opening it: display name, and the qualifier that distinguishes it from its near-neighbours.
- Source and dataset version are retained on selection and visible in the Source detail sheet, per UX-DR19.
- Selecting a record is a proposal that flows into the existing confirmation step. It never binds nutrition directly — see `failure-modes.md`.

## Multi-item meals on the floor

The floor resolves one record per query, so a multi-item meal is built by adding items one at a time into the same Analysis Session. This is not a lesser path — it is the same per-item confirmation and per-item-to-meal aggregation the photo path already uses (FR-16, AD-19), reached one query at a time instead of one description at a time.

Adding an item never discards already-confirmed items, and the session's aggregate updates as items accumulate. The model path (CAP-2) proposes the whole split at once and the user edits it (CAP-7); the floor arrives at the same end state through repeated search. Both converge on one confirmed item list, so nothing downstream needs to know which produced it.

## No-match path

Numbers stay withheld. The user is offered, at most, three next actions per UX-DR37's interaction budget and the Recovery panel rule: refine the query, change country and catalog, or end the session. "No match" is a terminal, honest state — it never falls back to a nearest guess, and it never invites a retry that cannot change the outcome (FR-20).

## Language

Records are matched in the language of the activated country package. Because AD-12 makes country independent from language, the app language and the catalog language can differ, and the surface states which language it is matching against rather than implying it follows the app.

Each supported market declares a row in a language matrix. The matrix is data, versioned with the catalog package, not conditionals in search code:

| Per market | Declares |
| --- | --- |
| Catalog language | The language the records are written in |
| Accepted input languages | What a user may type, which may exceed the catalog language |
| Script | The writing system, and whether cross-script entry is accepted |
| Morphology | Which normalization applies — none, light stemming, or full lemmatization |
| Synonyms | Food-name alternates, regional names, and common alternate spellings |

English-only behaviour is not a valid default for a market whose catalog is not in English.

### First three languages

English, Russian, and Spanish. Spanish was dropped on 2026-07-31 and restored on 2026-08-01; the notes below record what its absence had removed, because the reasoning explains why it is worth carrying.

| Language | Script | Morphology | Dominant cost |
| --- | --- | --- | --- |
| English | Latin | None beyond plurals; synonyms suffice | Baseline |
| Spanish | Latin with diacritics | Light stemming | Gender and number agreement, regional variants |
| Russian | Cyrillic | Full lemmatization across cases | Inflection plus cross-script entry |

These three cover the matrix rather than only its ends. English needs nothing beyond plurals and Russian needs full lemmatization, and Spanish sits between them on light stemming with diacritics — the profile that was designed and untested while Spanish was out of scope. Restoring it means every declared morphology profile has a language exercising it.

The diacritic fold in query normalization is load-bearing again. It was close to unexercised with Spanish out of scope — Cyrillic does not use Latin-1 accents — and was recorded then as a deletion candidate. Spanish restores its main justification: *berenjena*, *plátano*, and *jalapeño* all depend on it, and a user typing without accents must still reach the record.

### Input language is not catalog language

This is the cost most easily underestimated. An accepted input language does not imply a catalog in that language: a Russian speaker in the United States searches Cyrillic against English USDA records.

So the synonym layer is keyed per **(catalog, input language)** pair, not per language. Adding Russian input to the US catalog means mapping Russian food names onto that catalog's canonical IDs; it does not mean shipping a Russian catalog. Fixtures are keyed the same way — a language proven against one catalog is not proven against another.

Display always uses the catalog record's own name, so a user who searches in one language and reads results in another sees exactly what the record says rather than a translation the catalog cannot vouch for.

### Alternate naming

One food answers to more than one word, and no amount of normalization or fuzzy matching connects those words — only data does. English alone shows it: *aubergine* and *eggplant*, *coriander* and *cilantro*. Russian adds regional and Soviet-era names alongside its inflected forms.

The resolution is to accept every variant as a synonym onto the same canonical ID rather than selecting one wording per market. Search exists to find things, and inclusiveness costs nothing at query time. Preference belongs to display, which already uses the catalog's own name.

### Inflection

This is the change Russian forces. English gets by on plurals, so a synonym list covers it. Russian nouns inflect across cases — *курица*, *курицы*, *курицу*, *курицей* are all the same chicken — and enumerating every form of every food as a synonym is not maintainable.

So markets whose language is inflected declare full morphological normalization, applied identically at index build and query time like every other normalization step. A user typing an inflected form finds the record; the record does not need a synonym row per case.

### Cross-script

Where a market declares cross-script entry, a record in one script is reachable from the other: a Cyrillic record is findable by Latin-script typing, and the reverse. This matters more than it looks — a bilingual user, a traveller, or anyone on a keyboard that does not match the catalog will type in the script they have, not the script the database uses.

Transliteration is many-to-many and imperfect, so it feeds the fuzzy tier rather than claiming exact matches, and it never outranks a genuine exact match in the record's own script.

### Why this is load-bearing

The UJ-3 cultural-food journey exists to prove SuperNutrition works beyond mainstream US meals. Finding a regional dish is a synonym, inflection, and transliteration problem before it is a ranking problem. A market added to the language matrix without its fixtures is a market where that journey is untested — so each row ships with its own query set, and the Russian set specifically covers inflected forms and Latin-script entry of Cyrillic records.

## Evidence

- Golden fixtures: a documented query set mapped to expected canonical IDs and expected order, executed against a pinned package version.
- Determinism: the same query against the same package version returns byte-identical ordering across platforms and runs.
- Offline: the full fixture set passes with network disabled and every inference engine reporting `unavailable`.
- Device floor: the same set passes on the lowest supported tier within a declared latency budget. Search currently has no performance budget — the existing NFRs cover camera-ready and capture-to-Candidate time only, so one is established here.
