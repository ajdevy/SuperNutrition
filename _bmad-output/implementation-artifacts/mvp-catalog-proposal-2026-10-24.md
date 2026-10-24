# Concrete G-2 proposal for the local MVP

Status: proposed; not activated. The user has been asked to decide.

Use the already imported USDA FoodData Central SR Legacy release `2018-04`.
The existing TSV records the upstream archive SHA-256
`b80817294b8850530aaedf2e515c02593b1824f763a0ff356e5c2081643e6fd0`,
source URL, units, CC0 attribution and explicit missing-value semantics.
`GeneratedCatalog` currently emits identity only and deliberately omits these
nutrients under G-2. This proposal does not treat an imported TSV as approval.

The proposed first package represents the US catalog, independent from language,
market jurisdiction and processing region. Keep the current namespaced canonical
IDs. Show the product/preparation match and source before explicit confirmation;
ask for grams rather than guessing ingredient distribution from whole-meal mass.
Only catalog-owned documented unit conversions are permissible. Absent nutrients
remain unknown, never zero. Unsupported dishes require explicit ingredient
selection/quantities or remain identity-only. No server transfer or raw-photo
retention is introduced.

Activation still requires a reproducible, integrity-checked/versioned package,
provenance fixtures, explicit confirmation and serving integration, missing-value
tests and a durable owner decision. The source choice alone does not approve
country launch, privacy jurisdiction or production inference claims.

Primary source verification on 2026-10-24:

- [USDA data documentation](https://fdc.nal.usda.gov/data-documentation/)
  identifies SR Legacy as the final April 2018 release.
- [USDA downloadable datasets](https://fdc.nal.usda.gov/download-datasets/)
  lists that pinned release.
- [USDA API guide](https://fdc.nal.usda.gov/api-guide/)
  states the public-domain/CC0 terms and requests source attribution.
