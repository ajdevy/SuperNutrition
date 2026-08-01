# G-2 readiness: what a Nutrition Source package must supply

**Owner:** product data owner + legal. **Blocks:** grounding beyond a fake catalog.

This does not approve anything. It turns the PRD line — *"source/version/license
per reference country, canonical IDs, unit conversions, missing-record policy"* —
into a list someone can tick, and states what the code already assumes so that an
arriving package either satisfies those assumptions or visibly does not.

Everything below is already load-bearing in shipped code. The seed catalog exists
precisely so the mechanism could be built without waiting, and every place it
falls short is a question this gate answers.

## What must be supplied

| Input | Why the code needs it | What happens today without it |
| --- | --- | --- |
| **Source, version, licence per country** | Provenance travels with every result; a record with no source cannot be shown as grounded | `SeedCatalog` is explicitly fake and carries no source |
| **Canonical ID scheme** | `food:egg-boiled` ids are the join between search, dish expansion, and confirmation | Ids are invented locally and will not survive a real package |
| **Per-food weights, with units** | `DishIngredient.grams` is nullable and null everywhere; `DishPortion` is a multiplier waiting for something to multiply | No nutrition number can be produced at all |
| **Unit conversions** | A package in grams and a user thinking in cups need a defined bridge | Quantity words are stripped and the amount re-asked at confirmation |
| **Missing-record policy** | The floor already reports what it could not resolve; the package must say whether "absent" means "not in this market" or "not a food" | Unresolved fragments are surfaced but uninterpreted |
| **Preparation variants** | Records must be reachable by preparation, or `"grilled chicken breast"` degrades to a reported mismatch | Seed records carry preparation in their names; a package that does not will regress this |
| **Per-market attribution for dishes** | `DishLocalePreference` ranks by market; without attribution every dish ranks neutrally | `SeedDishes.marketAttribution` is invented for four dishes |

## What the code already commits to

These are decisions the package must live with, not requests.

- **A record is reachable by every name people use for it.** Alternate names are
  matched and never displayed, so a package supplying one wording per market
  fails UJ-3 regardless of how correct that wording is.
- **Preparation belongs in the searchable name.** Two seed records claimed a
  preparation in their id that no name carried, and plain `"chicken breast"`
  silently returned a raw record. A package repeating that shape hands someone
  raw-chicken numbers for grilled chicken with no signal.
- **A weight without a source is not acceptable.** `DishIngredient.grams` stays
  null until this gate delivers, and a test fails the moment a seed value
  appears. Estimated weights are the fabrication the grounding chain exists to
  prevent.
- **Confidence bands derive from scores**, at 0.5 and 0.8, published in
  `contracts/inference/v2`. A package or engine supplying its own bands is a new
  major release, not a configuration.

## How we will know the gate is satisfied

Not "a package was approved" but:

1. A package validates against a schema that carries every column above.
2. `SeedCatalog` and `SeedDishes` are replaced, and the existing suites pass
   unchanged — they assert behaviour, not seed data, so a real package should not
   need them rewritten. Any test that does need rewriting is a place the seed
   data was load-bearing, and worth knowing.
3. `DishIngredient.grams` is non-null for the package's own dishes, and the test
   forbidding seed weights is retargeted rather than deleted.

## What this gate does not decide

Which markets launch, in what order, and under which privacy jurisdictions. That
is G-1, and the two are independent: a licensed package for a country nobody
launches in is wasted, and a launched country with no package cannot ground
anything. They gate different failures and should not be bundled.
