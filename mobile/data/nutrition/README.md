# Approved bundled USDA catalog

This adapter implements `grounded-nutrition/v2` with an actual SQLDelight database.
The domain's existing `grounded-nutrition/v1` types and deterministic text matcher
remain intact. It includes all **7,793** USDA FoodData Central SR Legacy foods,
release **2018-04**, for the **US catalog**. Country describes the catalog, not
the user's jurisdiction, language or processing region. FNDDS recipes are absent.

An explicit product confirmation and positive grams are required. Nutrients use
the source's 100 g edible-portion basis. No whole-meal ingredient allocation,
household conversion or photo-derived quantity is assumed. Empty fiber, sugar
and sodium cells remain SQL NULL and typed unknown values. A known source zero
stays zero. The domain shares `value * (grams / 100)` arithmetic with v1; display
rounding is separately HALF_UP to two decimal places on the Double's canonical
decimal representation. Source and calculated values remain unchanged.

## Reproduce and verify

Download the pinned [USDA archive](https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip)
to a local cache outside the repository, then run from the repository root:

```sh
python3 mobile/data/nutrition/tools/generate_bundled_catalog.py \
  --upstream-archive /absolute/path/to/sr_legacy.zip --check
python3 -m unittest discover -s mobile/data/nutrition/tools -p 'test_*.py'
python3 -m unittest scripts.tests.test_srlegacy_source_pin
```

Omit `--check` to regenerate the tracked package TSV, manifest and bounded Kotlin
constant chunks. The CLI has no hash override. It enforces both the archive pin
`b80817294b8850530aaedf2e515c02593b1824f763a0ff356e5c2081643e6fd0`
and imported TSV pin
`1b416249fde1f9a6a0672e71c3ffb216b2a3883f3dbd833a173d3b5332b657eb`.
The frozen numeric package digest is
`a7b422344393e918ca8ab13bbcfa2ae56812c1ed6046a2b3eae0b2908b74d174`.
Generation is byte deterministic with no clock or network dependency.

The existing upstream importer can reproduce the imported TSV byte-for-byte
with the same archive and `--generated-on 2026-08-02`; its production entrypoint
now enforces the archive pin too. Synthetic helpers in tests may explicitly
provide a fixture digest; production CLIs expose no bypass.

## Activation and trust

`openBundledUsdaCatalog(driverFactory, sha256)` receives a **fresh private**
platform database and the platform's SHA-256 implementation. Hosts use Android
`AndroidSqliteDriver` and iOS `inMemoryDriver`; real JDBC is a JVM-test dependency
only. Android/Native drivers create `usdaCatalogSchema` automatically. JDBC
fixtures call `usdaCatalogSchema.create(driver)` explicitly.

The factory returns an adapter-owned `BundledUsdaCatalogSession?`. Its `catalog`
property is the SQL-free `ApprovedNutritionCatalog` port. Retain exactly one session
in the Android ViewModel/SharedKit production session, outside recomposition.
Create it in a background context, then publish it on Main. Serialize queries and
disposal in that owner. Close on `onCleared`/disposal; if initialization finishes
after disposal, close that late result instead of publishing it. `close()` is
idempotent, including after a driver-close failure. A closed source rejects search
and lookup; the domain resolver translates its ordinary lookup failure to
`SOURCE_UNAVAILABLE`. Cancellation during initialization propagates after
best-effort candidate cleanup, even when driver cleanup itself fails.

The adapter verifies the frozen payload checksum and validates its schema,
identities, count, known values and unknown cells before inserting into a private
candidate database. All rows and package metadata are committed in one synchronous
SQLDelight transaction. Only a complete candidate is published through the
read-only domain port. A failed stage rolls back and leaves the previously
published source handle usable. Existing consumers can keep their immutable
prior handle. Failed candidates are closed; a successful handle lives with its
owning application session. There is no user data in this database.

The package is inside the enclosing application's signed payload. Its independent
checksums detect accidental content changes; they are not a detached signature
or signing authority. This MVP has no catalog downloads, remote activation,
independent package signing key or OTA update service. A release/rollback selects
the corresponding complete app and catalog together. Production signature
verification remains a release prerequisite. Local Android evidence is from a
debug-signed APK; local iOS compilation may explicitly disable signing. Neither
proves production release signing or an independent signed-package service.

## Source and licences

USDA numbers and names are CC0 1.0. Attribution requested by USDA:
U.S. Department of Agriculture, Agricultural Research Service. FoodData Central,
2019. [Data documentation](https://fdc.nal.usda.gov/data-documentation/),
[licence statement](https://fdc.nal.usda.gov/api-guide/),
[CC0](https://creativecommons.org/publicdomain/zero/1.0/).

Search joins the unchanged generated identity catalog's multilingual aliases by
canonical ID. The numeric package digest binds its IDs, source labels and nutrient
cells; aliases retain the existing compiled identity catalog's separate provenance
and are included in the enclosing app payload. Aliases rank explicit search choices;
the returned exact product and numeric values always come from SQLDelight.
**Contains information from the AGROVOC Multilingual Thesaurus,
© Food and Agriculture Organization of the United Nations (FAO), licensed under
[CC-BY IGO 3.0](https://creativecommons.org/licenses/by/3.0/igo/).**
[FAO source](https://agrovoc.fao.org/). Additional aliases are from
[Wikidata](https://www.wikidata.org/), CC0. These credits must appear in the shipped
chooser/details acknowledgements, not only here. The port exposes these credits
as structured `NutritionCatalogProvenance` for the host UI.

Russian matching also reuses the existing `GeneratedRussianForms.kt` and
`GeneratedRussianScaffolding.kt` tables derived from OpenCorpora dictionary
**0.92 rev 417150**, via pymorphy3 **2.0.6** and pymorphy3-dicts-ru
**2.4.417150.4580142**. These are selected and filtered inflection/edge-word
subsets, with lowercasing and `ё→е` canonicalization while retaining `ё/е`
lookup variants where applicable. OpenCorpora publishes its source data under
[CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/):
[source downloads](https://opencorpora.org/?page=downloads),
[licensing FAQ](https://opencorpora.org/?page=faq). The structured acknowledgements
include this separate search-data credit; the numeric USDA package remains CC0.

**Release prerequisite:** the generated table headers separately record the
dictionary's CC BY-SA notice and the `pymorphy3-dicts-ru` package's MIT metadata.
Applicability and scope of ShareAlike, redistribution and source-availability
obligations remain unresolved for counsel before release. Adding attribution
does not close that release gate, approve public distribution or determine
downstream licensing.

No color, fat-group, fiber-group, fermentation or other reward classification is
inferred from names. A sourced nutrient profile does not grant those facts.

## Dependencies

SQLDelight plugin/runtime/drivers are pinned to 2.3.2. Runtime is the module's
only external production dependency. JVM JDBC is test-only. Driver/plugin
dependency hashes are recorded from Maven Central artifacts in mobile's strict
verification metadata, with versions in the product-local lockfiles. The
platform hosts own Android/Native driver dependencies and native evidence.
