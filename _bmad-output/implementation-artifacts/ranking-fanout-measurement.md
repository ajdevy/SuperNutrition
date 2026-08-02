# Fan-out of the terms in the ranking regression

Measured directly against `GeneratedCatalog.kt` (parser validated at exactly 3519
records before any number below was read).

| term | records carrying it | role in the defect |
|---|---:|---|
| `масла` | 62 | fold target; wins today, should not |
| `сливочное масло` | 6 | Butter's own name; loses today, should win |
| `масло` | 0 | what the user types — no record carries it |
| `свинья` | 21 | fold target; elects Lard |
| `мясо свиней` | 20 | matched literally before the fold; elected Pork correctly |
| `курица` | 43 | fold target; wins, and **should** |
| `яйцо курицы` | 23 | loses, and should |

## Why the obvious rule does not work

"Prefer the more discriminating name" is the intuitive fix and it is wrong here.
The winner has the *higher* fan-out in the good case (`курица` 43 beats
`яйцо курицы` 23) and the *lower* fan-out in the bad case (`сливочное масло` 6
must beat `масла` 62). Fan-out orders the two cases in opposite directions, so no
threshold on it separates them.

The two situations are identical on every structural property available to the
matcher: a one-token form reached through the lemma fold at coverage 1000 versus
a two-token form matched literally at coverage 500. Any rule keyed on token
count, on match kind, or on fan-out alone will fix one and break the other.

## Catalog-wide shape

| name shared by | distinct names |
|---|---:|
| 1 record | 272 |
| 2–5 | 457 |
| 6–20 | 270 |
| 21–60 | 118 |
| >60 | 47 |

The most-shared names are concept labels rather than names: `рыба`, `мясо рыбы`,
`свежая рыба`, `сырая рыба`, `fish meat`, `fresh fish`, `wet fish`, `pescado`
each attached to 164 records; `супы` to 121; `drinks` to 116. A term attached to
164 records is a category, and it is currently indistinguishable from a name.
That is a property of `GeneratedCatalog.kt`, not of the matcher — the fold only
made it reachable.

## Confirmed on the real engine, not on a model

`RankingRegressionTest` fails on the shipped code with, verbatim:

- `масло` -> `Oil, soybean, salad or cooking, (partially hydrogenated)`
- `свиней` -> `Lard`
- `паст` -> `Pasta, gluten-free, corn, dry` (all three parsnip records absent)

and its fourth case — that `курицы`/`курицу`/`курицей` still reach Chicken and
`яйцо`/`яйца` still reach Egg — passes. The constraint is genuinely two-sided.

---

# Form multiplicity: the property that does separate them

Measured the same way, parser asserted at 3519 records first. For each record,
how many of its OWN searchable names satisfy the query at TOKEN_SUBSET:

| query | wanted | its forms | rival | rival forms | distribution over all matches |
|---|---|---:|---|---:|---|
| `курицы` | Chicken | 3 | Egg | 1 | `{1: 23, 3: 43}` |
| `масло` | Butter | 3 | Oils | 1 | `{1: 63, 3: 6}` |
| `свиней` | Pork | 3 | Lard | 2 | `{2: 1, 3: 20}` |

Chicken answers to `курица`, `курица как еда` and `мясо курицы`; the egg records
answer only to `яйцо курицы`. Butter answers to `сливочное масло`,
`масло коровье` and `масло сливочное`; the 62 oils answer only to `масла`.

The distributions are bimodal with no overlap, which is what makes this usable:
in every case the correct food sits strictly above its rival, and unlike fan-out
the ordering does not invert between the good case and the bad ones.

Reading: a food that answers to a word under several of its own names really is
that food. A word that appears in exactly one name shared across dozens of
records is a category label wearing a name's clothes.

**One correction to the main session's own claim.** Lard was asserted to carry
`свинья` alone. It carries two forms, `домашняя свинья` and `свинья` — the
direction was right and the number was not. Pork still wins at 3, so the rule
holds, but the margin is one form rather than two.

## Where it must sit relative to coverage

Not below it: `масло` gives Butter coverage 500 against the oils' 1000, so a
tie-break under coverage never runs and R1 stays broken.

Strictly above it is also wrong, and this needs measuring rather than asserting:
a record with three loose five-word synonyms would then outrank a record whose
entire name is the query. The product `multiplicity × coverage` keeps both terms
live and orders all four battery cases correctly by hand-calculation
(Butter 1500 > oils 1000; Chicken 3000 > Egg 500; Pork 3000 > Lard 2000), but it
has not been swept and must not be adopted on the strength of four cases.

## What this does not address

R3 — `паст` losing all parsnip records to Pasta — is not a ranking problem.
Being promoted into TOKEN_SUBSET makes first-tier-wins skip PREFIX entirely, so
parsnip is absent from the result set and no ranking rule can reach it. That
needs its own change.
