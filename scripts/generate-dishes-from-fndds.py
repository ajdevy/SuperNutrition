#!/usr/bin/env python3
"""Build-time generator: USDA FNDDS -> dish records with ingredient gram weights.

Standard library only. No third-party package is required or assumed.

WHAT THIS PRODUCES
------------------
One JSON file describing every FNDDS food that FoodData Central publishes a
recipe for: a display name, the ingredients, and each ingredient's gram weight
as USDA publishes it. Where an ingredient is an SR Legacy food, the NDB-derived
item id is emitted so the dish layer can join to the SR-Legacy-backed catalog.
Where it is not, the record says so instead of guessing.

THREE THINGS THAT WILL BITE WHOEVER EDITS THIS NEXT
---------------------------------------------------
1. INGREDIENT WEIGHTS DO NOT SUM TO THE FINISHED DISH WEIGHT, AND MUST NOT BE
   NORMALISED SO THAT THEY DO. They are the recipe's own batch quantities before
   cooking. Measured on the 2021-2023 release: of 3,829 multi-ingredient
   recipes the sums run from 6.42 g to 7,729.35 g, median 111.50 g, and only
   30.9% land in [99, 101]. "Stew, beef" summing to 100.7 g is a coincidence of
   that one recipe's scale, not evidence of a per-100 g convention.

   The finished weight is the sum adjusted by the recipe-level moisture change
   that FNDDS publishes separately:

       finished_grams = sum(ingredient grams) * (1 + moisture_change_pct / 100)

   That relation is not folklore; --verify re-derives it at build time. Taking
   the 1,352 recipes whose ingredients all resolve to SR Legacy, predicting
   protein per 100 g of finished food and comparing against FNDDS's own
   published value: the moisture-adjusted divisor is within 5% for 93.7% of
   them (median relative error 0.11%), the unadjusted sum for 83.5%, and
   "the recipe is stated per 100 g" for 39.9% (median error 18%). The last
   hypothesis is the one a normaliser would silently assume, and it is wrong.

   So: `grams` is verbatim, `ingredientInputTotalGrams` is the plain sum, and
   `moistureChangePercent` travels beside them. No normalised weight, no
   percentage share, and no derived finished weight is written -- a derived
   number in this file would be read as a measured one within a release or two.

2. FNDDS FOOD CODES ARE NOT STABLE ACROSS RELEASES and are therefore never used
   as canonical ids here. Over 1,800 were discontinued between two consecutive
   releases. The canonical id is a slug of the USDA description
   (`dish:fndds:stew-beef`); the food code and FDC id are carried only under
   `sourceRefsNotCanonical`, and are release-scoped provenance.

3. FoodData Central's own schema is inconsistent between releases. In the survey
   (FNDDS) download, `food_nutrient.nutrient_id` holds `nutrient.nutrient_nbr`;
   in the SR Legacy download the same column holds `nutrient.id`. Joining both
   with one rule silently yields zero rows. --verify depends on this and gets
   it right; anything else added later must too.

USAGE
-----
    python3 scripts/generate-dishes-from-fndds.py            # download + build
    python3 scripts/generate-dishes-from-fndds.py --no-download \
        --work-dir /path/with/extracted/csv
    python3 scripts/generate-dishes-from-fndds.py --no-verify --quiet

Downloads land in --work-dir (default: a cache dir outside the repository) and
are checked against pinned SHA-256 digests. Nothing but --out is written inside
the repository.
"""

from __future__ import annotations

import argparse
import collections
import csv
import datetime
import gzip
import hashlib
import io
import json
import os
import re
import sys
import unicodedata
import urllib.request
import zipfile

GENERATOR_VERSION = "1.0.0"
SCHEMA_VERSION = 1

# --------------------------------------------------------------------------
# Pinned sources. Digests are of the zip as served; a mismatch aborts the build
# rather than producing data whose provenance header would be a lie.
# --------------------------------------------------------------------------
FDC_LANDING = "https://fdc.nal.usda.gov/download-datasets"
FDC_LICENCE_STATEMENT = "https://fdc.nal.usda.gov/api-guide"
FDC_LICENCE = "CC0 1.0 Universal (public domain dedication)"
FDC_LICENCE_URL = "https://creativecommons.org/publicdomain/zero/1.0/"
FDC_CITATION = (
    "U.S. Department of Agriculture, Agricultural Research Service. "
    "FoodData Central, 2019. fdc.nal.usda.gov."
)

SOURCES = {
    "survey": {
        "url": "https://fdc.nal.usda.gov/fdc-datasets/"
               "FoodData_Central_survey_food_csv_2024-10-31.zip",
        "sha256": "5ccc25ec2777a8982fbb61378a42f415316173eb11e48c9a8ba4cb19f5a4f29c",
        "member_dir": "FoodData_Central_survey_food_csv_2024-10-31",
        "release_date": "2024-10-31",
        "dataset": "FNDDS 2021-2023 (FoodData Central survey foods)",
    },
    "sr_legacy": {
        "url": "https://fdc.nal.usda.gov/fdc-datasets/"
               "FoodData_Central_sr_legacy_food_csv_2018-04.zip",
        "sha256": "b80817294b8850530aaedf2e515c02593b1824f763a0ff356e5c2081643e6fd0",
        "member_dir": "FoodData_Central_sr_legacy_food_csv_2018-04",
        "release_date": "2018-04",
        "dataset": "SR Legacy (FoodData Central)",
    },
    "foundation": {
        "url": "https://fdc.nal.usda.gov/fdc-datasets/"
               "FoodData_Central_foundation_food_csv_2025-12-18.zip",
        "sha256": "3850de85effd6d9aa471f48aab2a76c20f9bc5feb3f2f52c8ce693f9cf75d52b",
        "member_dir": "FoodData_Central_foundation_food_csv_2025-12-18",
        "release_date": "2025-12-18",
        "dataset": "Foundation Foods (FoodData Central)",
    },
}

# --------------------------------------------------------------------------
# The ingredient id scheme is NOT this generator's to choose. It is the catalog's,
# and these two constants must match `scripts/generate-catalog-from-srlegacy.py`
# exactly: that script emits `food:usda-srl-01077` -- prefix, then the NDB number
# zero-padded to five digits, because SR Legacy's CSV stores 2,099 of its 7,793
# NDB numbers with the leading zero stripped. Emitting `food:ndb:1077` here
# instead would produce a file that looks joined and joins to nothing, which is
# the failure this repository has already paid for twice by keeping two lists in
# step instead of sharing one. If the catalog's scheme changes, change it here in
# the same commit; `--check-catalog` exists to catch it if nobody does.
CATALOG_ITEM_ID_PREFIX = "food:usda-srl-"
CATALOG_NDB_WIDTH = 5

MOISTURE_ATTRIBUTE_TYPE = "1002"        # "Adjustments made to foods, including moisture changes"
ADDITIONAL_DESCRIPTION_TYPE = "1001"    # "Additional descriptions for the food."
MOISTURE_RE = re.compile(r"Moisture change:\s*(-?\d+(?:\.\d+)?)%")


# --------------------------------------------------------------------------
# Fetching
# --------------------------------------------------------------------------
def log(msg: str, quiet: bool = False) -> None:
    if not quiet:
        print(msg, file=sys.stderr, flush=True)


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def ensure_source(key: str, work_dir: str, allow_download: bool, report: "Report",
                  quiet: bool):
    """Return (csv_directory, sha256_of_archive_or_None), downloading if needed.

    The archive digest is verified on every run that can see the archive, not
    only on the run that downloaded it -- a cached extract beside a pinned digest
    would otherwise let the provenance header describe an archive nobody checked.
    """
    spec = SOURCES[key]
    extracted = os.path.join(work_dir, key, spec["member_dir"])
    zip_path = os.path.join(work_dir, "%s.zip" % key)
    have_extract = os.path.isdir(extracted) and os.path.exists(
        os.path.join(extracted, "food.csv"))

    if not os.path.exists(zip_path):
        if have_extract:
            # Usable, but the digest cannot be asserted, so it is not claimed.
            report.warn(
                "%s was read from an extracted copy at %s with no archive beside it; "
                "its SHA-256 is recorded as null rather than assumed" % (key, extracted)
            )
            log("using cached %s at %s (archive absent, digest unverifiable)"
                % (key, extracted), quiet)
            return extracted, None
        if not allow_download:
            raise SystemExit(
                "missing %s and --no-download was given; expected %s or %s"
                % (key, extracted, zip_path)
            )
        os.makedirs(work_dir, exist_ok=True)
        log("downloading %s -> %s" % (spec["url"], zip_path), quiet)
        with urllib.request.urlopen(spec["url"], timeout=300) as resp, \
                open(zip_path, "wb") as out:
            while True:
                chunk = resp.read(1 << 20)
                if not chunk:
                    break
                out.write(chunk)

    digest = sha256_file(zip_path)
    if digest != spec["sha256"]:
        raise SystemExit(
            "SHA-256 mismatch for %s\n  expected %s\n  actual   %s\n"
            "The pinned release changed or the download is corrupt. Refusing to "
            "emit data whose provenance header would not describe it."
            % (zip_path, spec["sha256"], digest)
        )
    if have_extract:
        log("using cached %s at %s (archive digest verified)" % (key, extracted), quiet)
        return extracted, digest

    dest = os.path.join(work_dir, key)
    log("extracting %s" % zip_path, quiet)
    with zipfile.ZipFile(zip_path) as zf:
        zf.extractall(dest)
    if not os.path.exists(os.path.join(extracted, "food.csv")):
        raise SystemExit("extracted %s but %s/food.csv is absent" % (zip_path, extracted))
    return extracted, digest


# --------------------------------------------------------------------------
# CSV helpers
# --------------------------------------------------------------------------
def read_rows(directory: str, name: str):
    path = os.path.join(directory, name)
    with open(path, newline="", encoding="utf-8-sig") as fh:
        yield from csv.DictReader(fh)


def as_float(text: str):
    text = (text or "").strip()
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def tidy(value: float) -> float:
    """Round away float noise without changing any published figure."""
    return round(value + 0.0, 6)


def catalog_item_id(ndb_number: str) -> str:
    """Build the catalog's id for an NDB number, or refuse to build a wrong one."""
    text = ndb_number.strip()
    if not text.isdigit():
        raise SystemExit("NDB number is not numeric: %r" % ndb_number)
    if len(text) > CATALOG_NDB_WIDTH:
        raise SystemExit(
            "NDB number %r is wider than the catalog's %d-digit id field. Padding "
            "would silently produce an id the catalog cannot carry."
            % (ndb_number, CATALOG_NDB_WIDTH)
        )
    return CATALOG_ITEM_ID_PREFIX + text.zfill(CATALOG_NDB_WIDTH)


def slugify(description: str) -> str:
    s = unicodedata.normalize("NFKD", description)
    s = "".join(ch for ch in s if not unicodedata.combining(ch))
    s = re.sub(r"[^A-Za-z0-9]+", "-", s.lower()).strip("-")
    return s or "unnamed"


# --------------------------------------------------------------------------
# Build
# --------------------------------------------------------------------------
class Report:
    def __init__(self) -> None:
        self.counts: dict = {}
        self.warnings: list = []
        self.unresolved_examples: list = []
        self.verification: dict = {}

    def warn(self, message: str) -> None:
        self.warnings.append(message)


def build(survey_dir: str, srl_dir: str, foundation_dir, report: Report, quiet: bool) -> dict:
    # ---- SR Legacy: the join target -------------------------------------
    srl_ndb_to_fdc = {}
    for row in read_rows(srl_dir, "sr_legacy_food.csv"):
        ndb = row["NDB_number"].strip()
        if ndb in srl_ndb_to_fdc:
            report.warn("SR Legacy NDB number %s appears more than once" % ndb)
        srl_ndb_to_fdc[ndb] = row["fdc_id"]
    log("SR Legacy NDB numbers: %d" % len(srl_ndb_to_fdc), quiet)

    # ---- Foundation Foods: a second NDB namespace, reported not merged ---
    foundation_ndb = {}
    if foundation_dir:
        fdn_desc = {r["fdc_id"]: r["description"] for r in read_rows(foundation_dir, "food.csv")}
        for row in read_rows(foundation_dir, "foundation_food.csv"):
            ndb = (row.get("NDB_number") or "").strip()
            if ndb:
                foundation_ndb[ndb] = fdn_desc.get(row["fdc_id"], "")
        log("Foundation NDB numbers: %d" % len(foundation_ndb), quiet)

    # ---- Survey (FNDDS) --------------------------------------------------
    survey_food = {r["fdc_id"]: r for r in read_rows(survey_dir, "food.csv")}
    fndds_meta = {r["fdc_id"]: r for r in read_rows(survey_dir, "survey_fndds_food.csv")}
    categories = {
        r["wweia_food_category"]: r["wweia_food_category_description"]
        for r in read_rows(survey_dir, "wweia_food_category.csv")
    }
    food_code_to_fdc = {}
    for fdc_id, meta in fndds_meta.items():
        code = meta["food_code"].strip()
        if code in food_code_to_fdc:
            report.warn("FNDDS food code %s maps to more than one fdc_id" % code)
        food_code_to_fdc[code] = fdc_id

    # The two code namespaces must not overlap, or resolution order would
    # silently decide meaning. SR Legacy NDB numbers top out in the tens of
    # thousands; FNDDS food codes are eight digits. Assert rather than assume.
    overlap = set(food_code_to_fdc) & set(srl_ndb_to_fdc)
    if overlap:
        raise SystemExit(
            "FNDDS food codes and SR Legacy NDB numbers overlap (%d codes, e.g. %s). "
            "Ingredient resolution order would decide meaning silently; refusing."
            % (len(overlap), sorted(overlap)[:5])
        )

    coverage = sorted({(m["start_date"], m["end_date"]) for m in fndds_meta.values()})

    # moisture change + additional descriptions
    moisture = {}
    additional = collections.defaultdict(list)
    unparsed_adjustments = 0
    for row in read_rows(survey_dir, "food_attribute.csv"):
        kind = row["food_attribute_type_id"]
        if kind == MOISTURE_ATTRIBUTE_TYPE:
            match = MOISTURE_RE.fullmatch((row["value"] or "").strip())
            if match:
                moisture[row["fdc_id"]] = float(match.group(1))
            else:
                unparsed_adjustments += 1
        elif kind == ADDITIONAL_DESCRIPTION_TYPE:
            value = (row["value"] or "").strip()
            if value:
                additional[row["fdc_id"]].append(value)
    if unparsed_adjustments:
        report.warn(
            "%d 'Adjustments' attribute rows did not match 'Moisture change: N%%' and "
            "were dropped; the format may have changed" % unparsed_adjustments
        )

    # portions (of the finished food)
    portions = collections.defaultdict(list)
    for row in read_rows(survey_dir, "food_portion.csv"):
        grams = as_float(row["gram_weight"])
        if grams is None:
            continue
        portions[row["fdc_id"]].append(
            (int(row["seq_num"] or 0), (row["portion_description"] or "").strip(), grams)
        )

    # ---- ingredients -----------------------------------------------------
    inputs = collections.defaultdict(list)
    for row in read_rows(survey_dir, "input_food.csv"):
        inputs[row["fdc_id"]].append(row)

    # Canonical ids: slug of the USDA description. Verify uniqueness rather than
    # trusting it -- a collision would merge two dishes into one id.
    slug_owners = collections.defaultdict(list)
    for fdc_id in inputs:
        slug_owners[slugify(survey_food[fdc_id]["description"])].append(fdc_id)
    dish_id_of = {}
    for slug, owners in slug_owners.items():
        if len(owners) == 1:
            dish_id_of[owners[0]] = "dish:fndds:%s" % slug
            continue
        # Deterministic, and loud: sorted by the USDA description so the ordering
        # does not depend on file order, and reported so a human decides.
        report.warn(
            "description slug %r is shared by %d foods (%s); disambiguated with a "
            "numeric suffix, which is NOT stable across releases -- fix the slug rule"
            % (slug, len(owners), ", ".join(sorted(owners)))
        )
        for index, fdc_id in enumerate(
            sorted(owners, key=lambda f: (survey_food[f]["description"], f)), start=1
        ):
            dish_id_of[fdc_id] = "dish:fndds:%s%s" % (slug, "" if index == 1 else "-%d" % index)

    resolution_counts = collections.Counter()
    unresolved_codes = collections.Counter()
    amount_disagreements = []
    entries = []

    for fdc_id in sorted(inputs, key=lambda f: dish_id_of[f]):
        rows_for_dish = sorted(inputs[fdc_id], key=lambda r: int(r["seq_num"] or 0))
        food = survey_food[fdc_id]
        meta = fndds_meta.get(fdc_id, {})

        ingredients = []
        total = 0.0
        for row in rows_for_dish:
            grams = as_float(row["gram_weight"])
            if grams is None:
                report.warn(
                    "input_food row %s of fdc_id %s has no gram weight and was dropped"
                    % (row["id"], fdc_id)
                )
                continue
            total += grams

            code = row["sr_code"].strip()
            source_amount = as_float(row["amount"])
            source_unit = (row["unit"] or "").strip()
            if source_unit == "GM" and source_amount is not None \
                    and abs(source_amount - grams) > 0.01:
                amount_disagreements.append(
                    "%s (%s: amount=%s GM but gram_weight=%s, portion_code=%s)"
                    % (row["id"], row["sr_description"], source_amount, grams,
                       row["portion_code"])
                )

            # Resolution order is fixed by the namespace assertion above.
            if code in food_code_to_fdc:
                target = food_code_to_fdc[code]
                reference = {
                    "kind": "fndds_subrecipe",
                    "dishId": dish_id_of.get(target),
                }
                if reference["dishId"] is None:
                    reference = {"kind": "unresolved", "reason": "subrecipe_target_has_no_recipe"}
                    resolution_counts["unresolved"] += 1
                    unresolved_codes[(code, row["sr_description"])] += 1
                else:
                    resolution_counts["fndds_subrecipe"] += 1
            elif code in srl_ndb_to_fdc:
                reference = {
                    "kind": "sr_legacy_ndb",
                    "canonicalItemId": catalog_item_id(code),
                    "ndbNumber": code,
                }
                resolution_counts["sr_legacy_ndb"] += 1
            elif code in foundation_ndb:
                # A real USDA NDB number, but not one SR Legacy carries. Emitting a
                # canonicalItemId here would fabricate a join into a catalog built
                # from SR Legacy, so the id is withheld and the number is reported.
                reference = {
                    "kind": "ndb_not_in_sr_legacy",
                    "ndbNumber": code,
                    "ndbSource": "foundation_food",
                }
                resolution_counts["ndb_not_in_sr_legacy"] += 1
                unresolved_codes[(code, row["sr_description"])] += 1
            elif code.startswith("999"):
                # FNDDS fortification / composite pseudo-foods: "Calcium as
                # ingredient", "Beef as ingredient in recipes". Not foods, and
                # deliberately given no id.
                reference = {"kind": "fndds_pseudo_ingredient"}
                resolution_counts["fndds_pseudo_ingredient"] += 1
                unresolved_codes[(code, row["sr_description"])] += 1
            else:
                reference = {"kind": "unresolved", "reason": "code_in_no_known_namespace"}
                resolution_counts["unresolved"] += 1
                unresolved_codes[(code, row["sr_description"])] += 1

            ingredient = {
                "seq": int(row["seq_num"] or 0),
                "sourceDescription": (row["sr_description"] or "").strip(),
                "grams": tidy(grams),
            }
            if source_amount is not None:
                ingredient["sourceAmount"] = tidy(source_amount)
            if source_unit:
                ingredient["sourceUnit"] = source_unit
            retention = (row["retention_code"] or "").strip()
            if retention and retention != "0":
                ingredient["retentionFactorCode"] = retention
            ingredient["reference"] = reference
            ingredients.append(ingredient)

        if not ingredients:
            continue

        entry = {
            "dishId": dish_id_of[fdc_id],
            "kind": "recipe" if len(ingredients) >= 2 else "single-ingredient",
            "displayName": food["description"],
        }
        extra = additional.get(fdc_id)
        if extra:
            entry["additionalDescriptions"] = extra
        category = categories.get(meta.get("wweia_category_number", ""))
        if category:
            entry["wweiaCategory"] = category
        if fdc_id in moisture:
            entry["moistureChangePercent"] = tidy(moisture[fdc_id])
        else:
            entry["moistureChangePercent"] = None
            report.warn("fdc_id %s has ingredients but no moisture-change attribute" % fdc_id)
        entry["ingredientInputTotalGrams"] = tidy(total)
        entry["ingredientCount"] = len(ingredients)
        entry["ingredients"] = ingredients
        dish_portions = [
            {"seq": seq, "description": desc, "grams": tidy(grams)}
            for seq, desc, grams in sorted(portions.get(fdc_id, []))
        ]
        if dish_portions:
            entry["portions"] = dish_portions
        entry["sourceRefsNotCanonical"] = {
            "fnddsFoodCode": meta.get("food_code"),
            "fdcId": fdc_id,
        }
        entries.append(entry)

    if amount_disagreements:
        # Observed once in the 2021-2023 release: amount=50.0 GM of raw bacon with
        # gram_weight=14.109, whose portion row reads "1 oz, raw (yield after
        # cooking)". So `amount` is the raw quantity and `gram_weight` is the
        # cooked yield -- gram_weight is what the recipe contributes, and is what
        # is emitted. Listed individually because a rise in this count would mean
        # the two columns had stopped meaning what they mean here.
        report.warn(
            "%d input_food rows state unit=GM yet amount != gram_weight (a cooked-yield "
            "conversion); gram_weight was emitted. Rows: %s"
            % (len(amount_disagreements), "; ".join(amount_disagreements[:5]))
        )

    recipes = [e for e in entries if e["kind"] == "recipe"]
    singles = [e for e in entries if e["kind"] == "single-ingredient"]
    ingredient_rows = sum(e["ingredientCount"] for e in entries)

    # Reference integrity: a subrecipe pointer that names nothing is a silent hole.
    known_ids = {e["dishId"] for e in entries}
    dangling = 0
    for entry in entries:
        for ing in entry["ingredients"]:
            ref = ing["reference"]
            if ref["kind"] == "fndds_subrecipe" and ref["dishId"] not in known_ids:
                dangling += 1
    if dangling:
        report.warn("%d sub-recipe references point at an entry that was not emitted" % dangling)

    sums = sorted(e["ingredientInputTotalGrams"] for e in recipes)
    near_100 = sum(1 for s in sums if 99.0 <= s <= 101.0)

    report.counts = {
        "fnddsFoodsInRelease": len(survey_food),
        "fnddsFoodsWithARecipe": len(entries),
        "dishesEmitted": len(recipes),
        "singleIngredientEntriesEmitted": len(singles),
        "ingredientsEmitted": ingredient_rows,
        "ingredientsInDishes": sum(e["ingredientCount"] for e in recipes),
        "ingredientsResolvedToSrLegacyNdb": resolution_counts["sr_legacy_ndb"],
        "ingredientsResolvedToAnotherFnddsRecipe": resolution_counts["fndds_subrecipe"],
        "ingredientsWithNdbNotInSrLegacy": resolution_counts["ndb_not_in_sr_legacy"],
        "ingredientsThatAreFnddsPseudoFoods": resolution_counts["fndds_pseudo_ingredient"],
        "ingredientsUnresolved": resolution_counts["unresolved"],
        "distinctUnresolvedSourceCodes": len(unresolved_codes),
        "danglingSubrecipeReferences": dangling,
        "dishWeightSumMinGrams": sums[0] if sums else None,
        "dishWeightSumMedianGrams": sums[len(sums) // 2] if sums else None,
        "dishWeightSumMaxGrams": sums[-1] if sums else None,
        "dishesWhoseIngredientsSumBetween99And101Grams": near_100,
    }
    report.unresolved_examples = [
        {"sourceCode": code, "sourceDescription": desc, "rows": count}
        for (code, desc), count in unresolved_codes.most_common(25)
    ]
    return {"entries": entries, "coverage": coverage}


# --------------------------------------------------------------------------
# Verification of the moisture relation
# --------------------------------------------------------------------------
def verify_moisture_relation(survey_dir: str, srl_dir: str, entries: list, quiet: bool) -> dict:
    """Re-derive the finished-weight relation from published nutrient densities.

    Predicts protein per 100 g of finished food from the ingredient weights under
    three competing divisors and compares each against FNDDS's own published
    value. Only recipes whose ingredients all resolve to SR Legacy can be used.

    NOTE the schema asymmetry: the survey release stores nutrient_nbr in
    food_nutrient.nutrient_id, SR Legacy stores nutrient.id. Using one rule for
    both silently matches nothing.
    """
    log("verifying the moisture relation against published nutrients", quiet)

    survey_protein_nbr = {
        r["nutrient_nbr"] for r in read_rows(survey_dir, "nutrient.csv") if r["name"] == "Protein"
    }
    srl_protein_id = {
        r["id"] for r in read_rows(srl_dir, "nutrient.csv") if r["name"] == "Protein"
    }
    if not survey_protein_nbr or not srl_protein_id:
        return {"ran": False, "reason": "protein nutrient row not found in one of the releases"}

    survey_protein = {}
    for row in read_rows(survey_dir, "food_nutrient.csv"):
        if row["nutrient_id"] in survey_protein_nbr:
            value = as_float(row["amount"])
            if value is not None:
                survey_protein[row["fdc_id"]] = value
    srl_protein = {}
    for row in read_rows(srl_dir, "food_nutrient.csv"):
        if row["nutrient_id"] in srl_protein_id:
            value = as_float(row["amount"])
            if value is not None:
                srl_protein[row["fdc_id"]] = value
    srl_by_ndb = {
        r["NDB_number"].strip(): r["fdc_id"] for r in read_rows(srl_dir, "sr_legacy_food.csv")
    }
    if not survey_protein or not srl_protein:
        return {"ran": False, "reason": "no protein rows matched; the schema may have changed"}

    hypotheses = {
        "ingredientSum": lambda total, moist: total,
        "ingredientSumTimesMoisture": lambda total, moist: total * (1.0 + moist / 100.0),
        "assumePer100gFinished": lambda total, moist: 100.0,
    }
    errors = {name: [] for name in hypotheses}
    considered = 0

    for entry in entries:
        if entry["kind"] != "recipe" or entry["moistureChangePercent"] is None:
            continue
        fdc_id = entry["sourceRefsNotCanonical"]["fdcId"]
        published = survey_protein.get(fdc_id)
        if published is None or published <= 0.05:
            continue
        grams_protein = 0.0
        usable = True
        for ing in entry["ingredients"]:
            ref = ing["reference"]
            if ref["kind"] != "sr_legacy_ndb":
                usable = False
                break
            srl_fdc = srl_by_ndb.get(ref["ndbNumber"])
            density = srl_protein.get(srl_fdc) if srl_fdc else None
            if density is None:
                usable = False
                break
            grams_protein += ing["grams"] / 100.0 * density
        if not usable:
            continue
        considered += 1
        total = entry["ingredientInputTotalGrams"]
        moist = entry["moistureChangePercent"]
        for name, divisor_of in hypotheses.items():
            divisor = divisor_of(total, moist)
            if divisor <= 0:
                continue
            predicted = grams_protein / divisor * 100.0
            errors[name].append(abs(predicted - published) / published)

    result = {
        "ran": True,
        "method": (
            "For every recipe whose ingredients all resolve to SR Legacy, protein per "
            "100 g of finished food was predicted from the ingredient gram weights under "
            "three competing finished-weight divisors and compared with the value FNDDS "
            "publishes for that food. Protein was chosen because FNDDS applies no cooking "
            "retention factor to it."
        ),
        "recipesConsidered": considered,
        "hypotheses": {},
    }
    for name, values in errors.items():
        if not values:
            continue
        values.sort()
        count = len(values)
        result["hypotheses"][name] = {
            "n": count,
            "medianRelativeError": round(values[count // 2], 5),
            "withinFivePercent": round(
                sum(1 for v in values if v <= 0.05) / count, 4),
            "withinTenPercent": round(
                sum(1 for v in values if v <= 0.10) / count, 4),
        }
    return result


# --------------------------------------------------------------------------
# Emit
# --------------------------------------------------------------------------
def provenance(digests: dict, coverage) -> dict:
    survey = SOURCES["survey"]
    srl = SOURCES["sr_legacy"]
    foundation = SOURCES["foundation"]
    return {
        "generatedOnUtc": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d"),
        "generatorScript": "scripts/generate-dishes-from-fndds.py",
        "generatorVersion": GENERATOR_VERSION,
        "primarySource": {
            "name": "USDA FoodData Central - Survey Foods (FNDDS)",
            "dataset": survey["dataset"],
            "surveyCoveragePeriod": ["/".join(c) for c in coverage],
            "releaseDate": survey["release_date"],
            "downloadUrl": survey["url"],
            "landingUrl": FDC_LANDING,
            "sha256": digests.get("survey"),
        },
        "joinSource": {
            "name": "USDA FoodData Central - SR Legacy",
            "dataset": srl["dataset"],
            "releaseDate": srl["release_date"],
            "downloadUrl": srl["url"],
            "sha256": digests.get("sr_legacy"),
            "role": "supplies the NDB numbers that ingredient ids are derived from",
        },
        "secondarySource": {
            "name": "USDA FoodData Central - Foundation Foods",
            "dataset": foundation["dataset"],
            "releaseDate": foundation["release_date"],
            "downloadUrl": foundation["url"],
            "sha256": digests.get("foundation"),
            "role": (
                "used only to identify ingredient codes that are valid NDB numbers "
                "outside SR Legacy; no id is emitted for them"
            ),
        },
        "licence": FDC_LICENCE,
        "licenceUrl": FDC_LICENCE_URL,
        "licenceStatementUrl": FDC_LICENCE_STATEMENT,
        "citation": FDC_CITATION,
        "publisher": "U.S. Department of Agriculture, Agricultural Research Service",
    }


WEIGHT_SEMANTICS = {
    "ingredientWeightsAreNotNormalised": True,
    "ingredientWeightsSumToFinishedWeight": False,
    "whatGramsMeans": (
        "The gram weight of that ingredient in the recipe as USDA publishes it, "
        "before cooking. Recipes are stated at their own batch scale, not per 100 g "
        "of finished food."
    ),
    "finishedWeightFormula": (
        "finished_grams = ingredientInputTotalGrams * (1 + moistureChangePercent / 100)"
    ),
    "whyNotNormalised": (
        "Scaling the ingredient weights so they sum to 100 g, or to the dish's own "
        "finished weight, would discard the moisture change and misstate every "
        "ingredient. It would also be invisible: the numbers would still look like "
        "grams. The raw weights and the moisture factor are kept separate so any "
        "consumer that needs a finished-basis figure computes it explicitly."
    ),
    "notCarried": (
        "input_food.portion_description is deliberately not carried. It names the "
        "portion-table row used for the unit conversion, not the recipe's own "
        "measure: a row reading amount=2.0 unit=C for celery carries "
        "portion_description '1 tablespoon' and gram_weight 240.0. gram_weight is "
        "the authoritative figure."
    ),
    "portionsAre": (
        "Portions describe the FINISHED food, not the ingredient sum. Descriptions "
        "are verbatim from FNDDS and include its 'Quantity not specified' default."
    ),
}

ID_SEMANTICS = {
    "dishIdIsDerivedFrom": "the USDA description, slugified",
    "fnddsFoodCodesAreNotCanonical": (
        "FNDDS food codes are not stable across releases -- over 1,800 were "
        "discontinued between two consecutive releases -- so they appear only under "
        "sourceRefsNotCanonical, together with the equally release-scoped FDC id."
    ),
    "catalogItemIdScheme": (
        CATALOG_ITEM_ID_PREFIX + "<NDB number, %d digits, zero-padded>" % CATALOG_NDB_WIDTH
    ),
    "catalogItemIdSchemeOwner": (
        "scripts/generate-catalog-from-srlegacy.py. This file follows it; it does "
        "not define it. ndbNumber is also carried verbatim so a consumer on a "
        "different scheme can rebuild the id without re-deriving the join."
    ),
    "ingredientReferenceKinds": {
        "sr_legacy_ndb": (
            "carries canonicalItemId in the catalog's scheme. NDB numbers survive "
            "record updates and are the documented join key to an SR-Legacy-backed "
            "catalog; SR Legacy's CSV stores them with leading zeros stripped, so "
            "they are zero-padded to the catalog's width to build the id and also "
            "carried verbatim as ndbNumber."
        ),
        "fndds_subrecipe": (
            "the ingredient is itself an FNDDS recipe in this file; dishId names it. "
            "Every such reference resolves within this file."
        ),
        "ndb_not_in_sr_legacy": (
            "a valid USDA NDB number that SR Legacy does not carry (it is a "
            "Foundation Foods record). No canonicalItemId is emitted, because an id "
            "pointing into a catalog built from SR Legacy would be a fabricated join."
        ),
        "fndds_pseudo_ingredient": (
            "an FNDDS bookkeeping entry rather than a food -- fortification lines "
            "such as 'Calcium as ingredient', and composite placeholders such as "
            "'Beef as ingredient in recipes'. No food exists to point at."
        ),
        "unresolved": "the source code matched no known namespace.",
    },
    "additionalDescriptionsAreNotSynonyms": (
        "additionalDescriptions is FNDDS's own field, verbatim. Some entries are "
        "genuine alternate names ('goulash' for 'Stew, beef') and some are "
        "disambiguation notes ('any 2 or more meats'). No attempt was made to tell "
        "them apart; feeding the field wholesale into a synonym table would put "
        "phrases like 'other or multiple meats' into search."
    ),
}


def main(argv=None) -> int:
    default_work = os.path.join(
        os.environ.get("XDG_CACHE_HOME") or os.path.expanduser("~/.cache"),
        "supernutrition-fndds",
    )
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    default_out = os.path.join(
        here, "mobile", "domain", "nutrition", "src", "commonMain",
        "resources", "dishes", "dishes-fndds.json",
    )

    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--work-dir", default=default_work,
                        help="download/extract cache (default: %(default)s)")
    parser.add_argument("--out", default=default_out,
                        help="output JSON path (default: the nutrition module resources)")
    parser.add_argument("--no-download", action="store_true",
                        help="fail instead of fetching missing sources")
    parser.add_argument("--no-verify", action="store_true",
                        help="skip the moisture-relation verification pass")
    parser.add_argument("--no-foundation", action="store_true",
                        help="skip Foundation Foods; leaves those ingredients unresolved")
    parser.add_argument("--check-catalog", default=None, metavar="PATH",
                        help="verify every emitted canonicalItemId appears in this "
                             "catalog file; exits non-zero if any does not")
    parser.add_argument("--report", default=None,
                        help="also write the build report as JSON to this path")
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args(argv)

    allow_download = not args.no_download
    report = Report()
    digests = {}
    survey_dir, digests["survey"] = ensure_source(
        "survey", args.work_dir, allow_download, report, args.quiet)
    srl_dir, digests["sr_legacy"] = ensure_source(
        "sr_legacy", args.work_dir, allow_download, report, args.quiet)
    foundation_dir = None
    if not args.no_foundation:
        foundation_dir, digests["foundation"] = ensure_source(
            "foundation", args.work_dir, allow_download, report, args.quiet)

    built = build(survey_dir, srl_dir, foundation_dir, report, args.quiet)
    entries = built["entries"]

    if not args.no_verify:
        report.verification = verify_moisture_relation(survey_dir, srl_dir, entries, args.quiet)
    else:
        report.verification = {"ran": False, "reason": "--no-verify"}

    document = {
        "schemaVersion": SCHEMA_VERSION,
        "provenance": provenance(digests, built["coverage"]),
        "weightSemantics": WEIGHT_SEMANTICS,
        "idSemantics": ID_SEMANTICS,
        "counts": report.counts,
        "verification": report.verification,
        "unresolvedIngredientCodes": report.unresolved_examples,
        "buildWarnings": report.warnings,
        "dishes": entries,
    }

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    payload = json.dumps(document, ensure_ascii=False, separators=(",", ":"))
    with open(args.out, "w", encoding="utf-8") as fh:
        fh.write(payload)
        fh.write("\n")

    # Measured after serialising, and deliberately kept OUT of the document's
    # `counts` block: the file cannot state its own size without either being
    # wrong or needing a second pass.
    buffer = io.BytesIO()
    with gzip.GzipFile(fileobj=buffer, mode="wb", mtime=0) as gz:
        gz.write(payload.encode("utf-8"))
    output_stats = {
        "outputBytes": len(payload.encode("utf-8")) + 1,
        "outputBytesGzipped": len(buffer.getvalue()),
    }

    if args.report:
        os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
        with open(args.report, "w", encoding="utf-8") as fh:
            json.dump(
                {
                    "counts": report.counts,
                    "output": output_stats,
                    "verification": report.verification,
                    "unresolvedIngredientCodes": report.unresolved_examples,
                    "buildWarnings": report.warnings,
                },
                fh,
                ensure_ascii=False,
                indent=2,
            )
            fh.write("\n")

    if not args.quiet:
        print("wrote %s" % args.out, file=sys.stderr)
        for key in sorted(report.counts):
            print("  %-46s %s" % (key, report.counts[key]), file=sys.stderr)
        for key in sorted(output_stats):
            print("  %-46s %s" % (key, output_stats[key]), file=sys.stderr)
        if report.verification.get("ran"):
            print("  moisture verification (%d recipes):"
                  % report.verification["recipesConsidered"], file=sys.stderr)
            for name, stats in report.verification["hypotheses"].items():
                print("    %-28s median_rel_err=%.5f within5%%=%.1f%%"
                      % (name, stats["medianRelativeError"], 100 * stats["withinFivePercent"]),
                      file=sys.stderr)
        for warning in report.warnings[:20]:
            print("  WARNING: %s" % warning, file=sys.stderr)
        if len(report.warnings) > 20:
            print("  ... and %d more warnings" % (len(report.warnings) - 20), file=sys.stderr)

    if args.check_catalog:
        wanted = {
            ing["reference"]["canonicalItemId"]
            for entry in entries for ing in entry["ingredients"]
            if ing["reference"]["kind"] == "sr_legacy_ndb"
        }
        present = set()
        with open(args.check_catalog, encoding="utf-8") as fh:
            for line in fh:
                for field in line.rstrip("\n").split("\t"):
                    if field.startswith(CATALOG_ITEM_ID_PREFIX):
                        present.add(field)
        missing = sorted(wanted - present)
        print("catalog check: %d distinct ingredient ids, %d absent from %s"
              % (len(wanted), len(missing), args.check_catalog), file=sys.stderr)
        if missing:
            print("  first absent ids: %s" % ", ".join(missing[:10]), file=sys.stderr)
            print("  The dish layer would join to nothing for these. Either the "
                  "catalog's id scheme moved (see CATALOG_ITEM_ID_PREFIX) or it "
                  "does not carry these foods.", file=sys.stderr)
            return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
