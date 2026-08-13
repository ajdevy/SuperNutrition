# Dish composition data

`dishes-fndds.json` is **generated**. Do not hand-edit it. Regenerate with:

```
python3 scripts/generate-dishes-from-fndds.py
```

Source, licence, release date, SHA-256 of every input archive, and the
generation date are inside the file under `provenance`. It is USDA FoodData
Central survey foods (FNDDS 2021-2023), CC0 1.0 Universal.

## The one thing to read before touching this

**The ingredient gram weights do not sum to the finished dish weight, and must
not be scaled so that they do.**

They are the recipe's own batch quantities before cooking. Across the 3,829
recipes in this file the sums run from 6.42 g to 7,729.35 g with a median of
111.50 g; only 1,185 of them land between 99 g and 101 g. "Stew, beef" summing
to 100.7 g — the example that started this work — is a property of that one
recipe's scale, not a per-100 g convention.

The finished weight comes from the recipe-level moisture change that FNDDS
publishes separately, and that this file carries as `moistureChangePercent`:

```
finished_grams = ingredientInputTotalGrams * (1 + moistureChangePercent / 100)
```

That relation is verified at build time rather than asserted. The generator
predicts protein per 100 g of finished food from the ingredient weights for
every recipe whose ingredients all resolve to SR Legacy, and compares it with
the value FNDDS publishes for that same food. On the 2021-2023 release, over
1,293 recipes:

| finished-weight assumption | median relative error | within 5% |
| --- | --- | --- |
| `sum * (1 + moisture/100)` | 0.11% | 93.7% |
| `sum` (moisture ignored) | 0.18% | 83.5% |
| "the recipe is per 100 g finished" | 18.0% | 39.9% |

The third row is the assumption a normaliser makes silently. It is wrong by a
factor that would show up as calories.

The result of that check is written into the file under `verification`, so a
future release that breaks the relation is visible in the data rather than only
in a build log.

## Identifiers

`dishId` is a slug of the USDA description (`dish:fndds:stew-beef`).

**FNDDS food codes are not canonical and are not used as ids.** Over 1,800 were
discontinued between two consecutive releases. The food code and the FDC id
appear only under `sourceRefsNotCanonical`; both are release-scoped.

Each ingredient carries a `reference` whose `kind` says exactly how far it could
be resolved:

| kind | meaning |
| --- | --- |
| `sr_legacy_ndb` | carries `canonicalItemId` (`food:usda-srl-01077`) — joins to the SR Legacy catalog |
| `fndds_subrecipe` | the ingredient is itself a recipe in this file; `dishId` names it |
| `ndb_not_in_sr_legacy` | a real USDA NDB number that SR Legacy does not carry (Foundation Foods). **No id is emitted** — one would be a fabricated join |
| `fndds_pseudo_ingredient` | FNDDS bookkeeping, not a food: "Calcium as ingredient" |
| `unresolved` | the code matched no known namespace |

`canonicalItemId` appears **only** on `sr_legacy_ndb`. An absent id means the
join genuinely does not exist; it is not a gap to be filled in with the nearest
plausible food.

**The id scheme belongs to the catalog, not to this file.**
`scripts/generate-catalog-from-srlegacy.py` defines `food:usda-srl-<NDB number,
5 digits, zero-padded>`, and this generator follows it — SR Legacy's own CSV
stores 2,099 of its 7,793 NDB numbers with the leading zero stripped, so an
unpadded id would look joined and join to nothing. If the catalog's scheme
moves, move `CATALOG_ITEM_ID_PREFIX` in the same commit. To check:

```
python3 scripts/generate-dishes-from-fndds.py \
  --check-catalog mobile/domain/nutrition/src/commonMain/resources/catalog/usda-sr-legacy.tsv
```

Last run: 1,674 distinct ingredient ids, 0 absent from the catalog.

## Things in this file that are not what they look like

- **`additionalDescriptions` is not a synonym list.** It is FNDDS's own field,
  verbatim. Some entries are genuine alternate names (`goulash` for
  "Stew, beef") and some are disambiguation notes (`any 2 or more meats`). No
  attempt was made to separate them. Feeding the whole field into a search
  synonym table would put "other or multiple meats" into the matcher.
- **`portions` describe the finished food, not the ingredient sum**, and include
  FNDDS's literal `Quantity not specified` default portion.
- **`kind` is `recipe` or `single-ingredient`.** Only the 3,829 `recipe` entries
  are dishes. The 1,602 `single-ingredient` entries are here because sub-recipe
  references point at them; filter on `kind == "recipe"` for a dish list.
- **`input_food.portion_description` is deliberately absent.** In the source it
  names the portion-table row used for the unit conversion, not the recipe's own
  measure: a celery row reading `amount=2.0 unit=C` carries
  `portion_description` "1 tablespoon" and `gram_weight` 240.0. `grams` is the
  authoritative figure.

## Size

7,488,131 bytes as emitted; 553,649 gzipped. If that has to come down, the
measured costs are `portions` 1.32 MB, `ingredient.sourceDescription` 1.05 MB,
`sourceRefsNotCanonical` 0.39 MB, `ingredient.sourceAmount` 0.37 MB,
`ingredient.sourceUnit` 0.31 MB, `additionalDescriptions` 0.31 MB,
`wweiaCategory` 0.22 MB. Nothing was dropped pre-emptively, because which of
those a consumer needs is a product decision and not one the generator should
make quietly.

## Gate position

This is FNDDS data, not an approved nutrition package. G-2 governs shipping it;
`SeedDishes` remains the fake set the search mechanism is developed against.
Nothing here is wired into the app.
