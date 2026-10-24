#!/usr/bin/env python3
"""Build-time generator: USDA SR Legacy -> the shippable SuperNutrition catalog.

    python3 scripts/generate-catalog-from-srlegacy.py --download

Dependencies: Python 3.8+ standard library only. No third-party packages are
imported and none are needed; if that ever changes, declare it here rather than
assuming the build host has it.

This script is BUILD-TIME ONLY. It is not wired into Gradle by this change and
nothing at runtime executes it. It reads the USDA archive and writes one data
file; wiring it into the build is separate integration work.


WHY THIS EXISTS
---------------
The shipped catalog was eight hand-written records carrying no nutrient values
at all, because a fabricated calorie count is exactly the failure the grounding
chain exists to prevent. SR Legacy is CC0, so every number below is citable, and
the emitted file carries its own provenance so that a reviewer holding only the
data file can still tell where each number came from.


THE OUTPUT FORMAT, AND WHY IT IS NOT JSON
-----------------------------------------
Line-oriented, tab-delimited, UTF-8, LF line endings.

1. `mobile/domain/nutrition` is a pure KMP module whose only dependencies are
   `:core:config` and `:domain:inference`. It has no kotlinx.serialization
   dependency, so JSON would mean either adding one to a domain module or
   hand-rolling a JSON reader. A TSV parses with `lineSequence()` and
   `split('\t')` — no dependency, and no `Regex`, which `CatalogSearch.kt`
   deliberately avoids so tokenization cannot differ between Kotlin platforms.
2. Size. JSON would repeat seven key names across 7,793 records for no
   information gain. The measured difference is reported by `--report`.
3. Safety is checked, not assumed. Every emitted field is asserted to contain no
   tab, CR, or LF before it is written (see `_assert_delimiter_safe`). All 7,793
   descriptions and 14,449 portion modifiers in the 2018-04 release are pure
   ASCII with none of those characters, but a future re-release is not promised
   to be, so the generator fails closed instead of silently emitting a file with
   a shifted column.

Layout::

    !supernutrition-catalog <version>     format marker, first line
    @key   value                          provenance header, one pair per line
    #...                                  column legend (comment)
    F  id  name  kcal  protein  fat  carb  fiber  sugar  sodium
    P  amount  label  grams               household portion

`P` lines belong to the nearest preceding `F` line. Grouping rather than
repeating the food id on every portion line makes "every portion belongs to a
food in this file" structural instead of a constraint someone has to remember to
check, and saves ~275 KB uncompressed. A `P` line before any `F` line is
malformed input and a reader must reject the file.


DECISIONS A REVIEWER SHOULD BE ABLE TO CHALLENGE
------------------------------------------------
* **The id is the NDB number, never `fdc_id`.** NDB numbers are documented to
  survive record updates; `fdc_id` is a FoodData Central surrogate key. SR
  Legacy is additionally frozen — it is the final Standard Reference release and
  will not be updated — so these ids are permanently stable.

  The CSV stores NDB numbers with leading zeros stripped (2,099 of 7,793 arrive
  four characters wide). The documented canonical form is five digits, where the
  first two are the food group: `9150` is `09150`, lemons, group 09 Fruits. This
  generator restores the padding, and asserts that padding introduces no
  collision (verified: 7,793 distinct before, 7,793 distinct after, and no
  five-character NDB number begins with `0`, so nothing can collide).

* **A missing nutrient is emitted as an empty field, never as zero.** Fibre is
  absent for 562 foods, total sugars for 1,786, sodium for 84. Writing `0` there
  would invent a sourced-looking number, which is the precise failure mode this
  repository treats as fabrication.

* **Nutrient ids are verified at run time, not trusted.** FoodData Central's own
  download page warns that the April 2020 to April 2022 archives were
  re-released "with corrections to the Nutrient ID column". Two ids in this
  release are genuinely surprising and were picked by counting rows, not by
  memory:
    - total sugars is **2000** ("Sugars, Total"), present on 6,007 foods, while
      the id many datasets use, 1063 ("Sugars, Total NLEA"), has **zero** rows;
    - energy is **1008** ("Energy", KCAL) on all 7,793 foods, while the Atwater
      variants 2047 and 2048 have **zero** rows.
  `EXPECTED_NUTRIENTS` pins each id to the name and unit the release must
  declare, and the generator aborts if a future archive disagrees.

* **Household measures come from `modifier`.** In this release all 14,449
  portion rows have `measure_unit_id` 9999 ("undetermined") and a blank
  `portion_description`; the human-readable measure lives entirely in
  `modifier`. A parser that read `portion_description`, or joined
  `measure_unit`, would emit 14,449 blank labels and look like it worked. The
  generator asserts this shape rather than depending on it silently.

* **18 portion rows are dropped, and the file says so.** They carry
  `amount = 0` — "0 cup = 142 g" for prepared rennin, "0 chicken, skin only =
  72 g" for stewed chicken skin. A portion is only usable as the triple
  (amount, label, grams), and an amount of zero makes the triple meaningless.
  The obvious repair, assuming the amount was meant to be 1, is not available
  here: it would invent the quantity a user logs, and the neighbouring rows
  refute it anyway — frozen kale (NDB 11235) carries both "0 package (10 oz) =
  94 g" and "1 package (10 oz) = 284 g", so the missing amount is demonstrably
  not 1. These rows are therefore excluded, counted in `@excluded_portions`,
  and listed by `--report`. Seven foods lose their only household measure as a
  result and keep their nutrients; that is visible in `foods_with_portions`.

* **Display names are USDA's `description`, verbatim.** They are
  inversion-ordered ("Butter, salted"), which matches the existing seed catalog
  ("Beans, black", "Egg, boiled"). Rewriting them would mean asserting a name
  USDA did not publish, and search already normalizes and tokenizes.

* **Nutrient values keep the source's exact digits.** Formatting only removes
  insignificant trailing zeros ("100.0" -> "100"), and every rewrite is asserted
  to parse back to the same float. Nothing is rounded or rescaled: sodium stays
  in milligrams as published, everything else in grams, all per 100 g edible
  portion.


REPRODUCIBILITY
---------------
Byte-identical across runs for a given input archive, except the single
`@generated_on` line. Pass `--generated-on YYYY-MM-DD`, or set
`SOURCE_DATE_EPOCH`, to pin that too.
"""

from __future__ import annotations

import argparse
import csv
import datetime as _dt
import hashlib
import os
import sys
import urllib.request
import zipfile
from pathlib import Path

# --------------------------------------------------------------------------
# Provenance constants. These describe the upstream release and are emitted
# into the data file so the numbers stay citable once separated from this repo.
# --------------------------------------------------------------------------

SOURCE_NAME = "USDA FoodData Central, SR Legacy Foods"
SOURCE_PAGE = "https://fdc.nal.usda.gov/download-datasets/"
SOURCE_URL = "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip"

# "04/2018" is the release the download page lists for SR Legacy; 2019-04-01 is
# the publication_date every row in food.csv carries. They differ, so both are
# recorded rather than picking whichever looks tidier.
SOURCE_RELEASE = "2018-04"
SOURCE_PUBLICATION_DATE = "2019-04-01"
SOURCE_SHA256 = "b80817294b8850530aaedf2e515c02593b1824f763a0ff356e5c2081643e6fd0"

# Verbatim from https://fdc.nal.usda.gov/api-guide (section "Licensing"),
# retrieved 2026-08-02.
LICENCE = "CC0 1.0 Universal (CC0 1.0) - public domain, not copyrighted"
LICENCE_URL = "https://creativecommons.org/publicdomain/zero/1.0/"
LICENCE_STATEMENT = (
    "USDA FoodData Central data are in the public domain and they are not "
    "copyrighted. They are published under CC0 1.0 Universal (CC0 1.0). No "
    "permission is needed for their use, but we request that users list "
    "FoodData Central as the source of the data."
)
CITATION = (
    "U.S. Department of Agriculture, Agricultural Research Service. "
    "FoodData Central, 2019. fdc.nal.usda.gov."
)

FORMAT_MARKER = "!supernutrition-catalog"
FORMAT_VERSION = "1"

ID_PREFIX = "food:usda-srl-"
NDB_WIDTH = 5

# Column order for `F` lines, after the id and name. Each entry is
# (column name, FDC nutrient id, expected nutrient.csv name, expected unit).
# The expected name and unit are asserted against the archive so a re-released
# nutrient id cannot silently move a value into the wrong column.
EXPECTED_NUTRIENTS = (
    ("kcal", "1008", "Energy", "KCAL"),
    ("protein_g", "1003", "Protein", "G"),
    ("fat_g", "1004", "Total lipid (fat)", "G"),
    ("carbohydrate_g", "1005", "Carbohydrate, by difference", "G"),
    ("fiber_g", "1079", "Fiber, total dietary", "G"),
    ("sugar_g", "2000", "Sugars, Total", "G"),
    ("sodium_mg", "1093", "Sodium, Na", "MG"),
)

NUTRIENT_COLUMNS = tuple(column for column, _, _, _ in EXPECTED_NUTRIENTS)
NUTRIENT_IDS = tuple(nutrient_id for _, nutrient_id, _, _ in EXPECTED_NUTRIENTS)

# Row counts this release is known to carry, cross-checked against the archive's
# own all_downloaded_table_record_counts.csv. A mismatch means the input is not
# the release this generator was verified against, which the operator must see.
EXPECTED_ROW_COUNTS = {"food": 7793, "sr_legacy_food": 7793, "food_portion": 14449}

REQUIRED_TABLES = (
    "food.csv",
    "sr_legacy_food.csv",
    "food_nutrient.csv",
    "nutrient.csv",
    "food_portion.csv",
)

DEFAULT_OUTPUT = Path("mobile/domain/nutrition/src/commonMain/resources/catalog/usda-sr-legacy.tsv")


class GeneratorError(RuntimeError):
    """Raised for any violated assumption. Every one of these fails the build."""


# --------------------------------------------------------------------------
# Input handling
# --------------------------------------------------------------------------


def download_archive(destination: Path) -> Path:
    """Fetch the SR Legacy CSV archive unless it is already cached."""
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists() and destination.stat().st_size > 0:
        print(f"  cached  {destination} ({destination.stat().st_size:,} bytes)")
        return destination
    print(f"  GET     {SOURCE_URL}")
    request = urllib.request.Request(SOURCE_URL, headers={"User-Agent": "SuperNutrition-catalog-generator/1"})
    with urllib.request.urlopen(request, timeout=900) as response, destination.open("wb") as handle:
        while True:
            chunk = response.read(1 << 20)
            if not chunk:
                break
            handle.write(chunk)
    print(f"  saved   {destination} ({destination.stat().st_size:,} bytes)")
    return destination


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_tables(
    source: Path, expected_digest: "str | None" = SOURCE_SHA256
) -> "tuple[dict[str, list[dict[str, str]]], str]":
    """Read the required CSV tables from a zip archive or an extracted directory.

    Returns the tables and the sha256 of the archive, or "" when reading a
    directory (there is no single artefact to digest, and claiming one would be
    provenance theatre).
    """
    if source.is_dir():
        if expected_digest is not None:
            raise GeneratorError("Production imports require the pinned archive, not an extracted directory")
        return _load_from_directory(source), ""
    if zipfile.is_zipfile(source):
        digest = sha256_of(source)
        if expected_digest is not None and digest != expected_digest:
            raise GeneratorError(f"SR Legacy archive SHA-256 mismatch: expected {expected_digest}, got {digest}")
        return _load_from_zip(source), digest
    raise GeneratorError(f"--input must be the SR Legacy zip or an extracted directory, got: {source}")


def _load_from_directory(root: Path) -> "dict[str, list[dict[str, str]]]":
    tables = {}
    for name in REQUIRED_TABLES:
        matches = sorted(root.rglob(name))
        if not matches:
            raise GeneratorError(f"{name} not found under {root}")
        with matches[0].open(newline="", encoding="utf-8-sig") as handle:
            tables[name] = list(csv.DictReader(handle))
    return tables


def _load_from_zip(archive: Path) -> "dict[str, list[dict[str, str]]]":
    tables = {}
    with zipfile.ZipFile(archive) as bundle:
        members = bundle.namelist()
        for name in REQUIRED_TABLES:
            matches = sorted(m for m in members if m.rsplit("/", 1)[-1] == name)
            if not matches:
                raise GeneratorError(f"{name} not found in {archive}")
            with bundle.open(matches[0]) as raw:
                text = raw.read().decode("utf-8-sig").splitlines()
            tables[name] = list(csv.DictReader(text))
    return tables


# --------------------------------------------------------------------------
# Value formatting
# --------------------------------------------------------------------------


def compact_number(raw: str) -> str:
    """Shortest text that parses back to exactly the source value.

    Removes insignificant trailing zeros only. The result is asserted to parse
    to the same float as the input, so this can shorten a number but can never
    change one. Rounding here would silently alter a sourced nutrition value.
    """
    text = raw.strip()
    if not text:
        raise GeneratorError("compact_number called with an empty value")
    if text.startswith("+"):
        text = text[1:]
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    if text in ("", "-", "-0"):
        text = "0"
    if text.startswith("."):
        text = "0" + text
    elif text.startswith("-."):
        text = "-0" + text[1:]
    if float(text) != float(raw):
        raise GeneratorError(f"number rewrite changed the value: {raw!r} -> {text!r}")
    return text


def _assert_delimiter_safe(fields: "list[str]", context: str) -> None:
    """Reject any field that would corrupt the column structure.

    Checked on every emitted field rather than sampled. The 2018-04 release is
    clean, but a silently shifted column in a nutrition table is a wrong number
    on a user's screen, so this fails the build instead.
    """
    for field in fields:
        if "\t" in field or "\n" in field or "\r" in field:
            raise GeneratorError(f"field contains a tab or newline and would corrupt the file ({context}): {field!r}")


# --------------------------------------------------------------------------
# Build
# --------------------------------------------------------------------------


def verify_nutrient_ids(nutrient_rows: "list[dict[str, str]]") -> None:
    """Pin every nutrient id to the name and unit the archive declares for it."""
    declared = {row["id"]: (row["name"], row["unit_name"]) for row in nutrient_rows}
    for column, nutrient_id, expected_name, expected_unit in EXPECTED_NUTRIENTS:
        if nutrient_id not in declared:
            raise GeneratorError(f"nutrient id {nutrient_id} ({column}) is absent from nutrient.csv")
        name, unit = declared[nutrient_id]
        if name != expected_name or unit != expected_unit:
            raise GeneratorError(
                f"nutrient id {nutrient_id} ({column}) is {name!r} in {unit!r}, "
                f"expected {expected_name!r} in {expected_unit!r}. The archive's nutrient ids have "
                f"changed; re-derive EXPECTED_NUTRIENTS by counting rows before trusting this build."
            )


def canonical_id(ndb_number: str) -> str:
    text = ndb_number.strip()
    if not text.isdigit():
        raise GeneratorError(f"NDB number is not numeric: {ndb_number!r}")
    if len(text) > NDB_WIDTH:
        raise GeneratorError(f"NDB number is wider than {NDB_WIDTH} digits: {ndb_number!r}")
    return ID_PREFIX + text.zfill(NDB_WIDTH)


def build(
    tables: "dict[str, list[dict[str, str]]]",
) -> "tuple[list[str], dict[str, int], list[tuple[str, str, str, str]]]":
    food_rows = tables["food.csv"]
    legacy_rows = tables["sr_legacy_food.csv"]
    portion_rows = tables["food_portion.csv"]

    for table, expected in EXPECTED_ROW_COUNTS.items():
        actual = len(tables[f"{table}.csv"])
        if actual != expected:
            raise GeneratorError(
                f"{table}.csv has {actual} rows, expected {expected}. This is not the release this "
                f"generator was verified against; re-inspect the archive before shipping its numbers."
            )

    verify_nutrient_ids(tables["nutrient.csv"])

    # fdc_id -> canonical id. The join is 1:1 in this release; assert it.
    descriptions = {}
    for row in food_rows:
        if row["fdc_id"] in descriptions:
            raise GeneratorError(f"duplicate fdc_id in food.csv: {row['fdc_id']}")
        descriptions[row["fdc_id"]] = row["description"]

    ids = {}
    for row in legacy_rows:
        fdc_id = row["fdc_id"]
        if fdc_id not in descriptions:
            raise GeneratorError(f"sr_legacy_food.csv references unknown fdc_id {fdc_id}")
        if fdc_id in ids:
            raise GeneratorError(f"duplicate fdc_id in sr_legacy_food.csv: {fdc_id}")
        ids[fdc_id] = canonical_id(row["NDB_number"])
    missing = set(descriptions) - set(ids)
    if missing:
        raise GeneratorError(f"{len(missing)} foods have no NDB number, so they have no stable id")

    # Zero-padding must not merge two distinct foods into one record.
    if len(set(ids.values())) != len(ids):
        raise GeneratorError("zero-padding NDB numbers produced colliding canonical ids")

    # Nutrients, streamed by (fdc_id, nutrient_id).
    wanted = set(NUTRIENT_IDS)
    nutrients: "dict[str, dict[str, str]]" = {}
    for row in tables["food_nutrient.csv"]:
        nutrient_id = row["nutrient_id"]
        if nutrient_id not in wanted:
            continue
        fdc_id = row["fdc_id"]
        if fdc_id not in descriptions:
            raise GeneratorError(f"food_nutrient.csv references unknown fdc_id {fdc_id}")
        bucket = nutrients.setdefault(fdc_id, {})
        if nutrient_id in bucket:
            raise GeneratorError(f"food {fdc_id} carries nutrient {nutrient_id} twice; no rule picks a winner")
        amount = row["amount"].strip()
        if amount == "":
            continue  # No value is not a zero value; leave the column empty.
        value = float(amount)
        if value < 0:
            raise GeneratorError(f"food {fdc_id} nutrient {nutrient_id} is negative: {amount}")
        bucket[nutrient_id] = compact_number(amount)

    # Portions. In this release the household measure lives only in `modifier`.
    portions: "dict[str, list[tuple[int, str, str, str]]]" = {}
    excluded: "list[tuple[str, str, str, str]]" = []
    for row in portion_rows:
        fdc_id = row["fdc_id"]
        if fdc_id not in descriptions:
            raise GeneratorError(f"food_portion.csv references unknown fdc_id {fdc_id}")
        if row["portion_description"].strip():
            raise GeneratorError(
                f"food_portion.csv row {row['id']} has a non-empty portion_description; this release "
                f"puts the household measure in `modifier` only, so the label logic must be revisited"
            )
        grams = row["gram_weight"].strip()
        if not grams or float(grams) <= 0:
            raise GeneratorError(f"food_portion.csv row {row['id']} has a non-positive gram_weight: {grams!r}")
        label = row["modifier"].strip()
        if not label:
            raise GeneratorError(f"food_portion.csv row {row['id']} has no household measure text")
        amount = row["amount"].strip()
        if not amount:
            raise GeneratorError(f"food_portion.csv row {row['id']} has a blank amount")
        if float(amount) < 0:
            raise GeneratorError(f"food_portion.csv row {row['id']} has a negative amount: {amount!r}")
        if float(amount) == 0:
            # Upstream defect, not a portion. Excluded rather than repaired: see
            # the module docstring. Recorded so the loss is auditable.
            excluded.append((ids[fdc_id], label, grams, descriptions[fdc_id].strip()))
            continue
        seq = int(row["seq_num"])
        portions.setdefault(fdc_id, []).append((seq, compact_number(amount), label, compact_number(grams)))

    # Emit, ordered by canonical id so the file never depends on CSV row order.
    lines: "list[str]" = []
    portion_line_count = 0
    foods_with_portions = 0
    filled = {column: 0 for column in NUTRIENT_COLUMNS}

    for fdc_id in sorted(descriptions, key=lambda key: ids[key]):
        values = nutrients.get(fdc_id, {})
        row = [ids[fdc_id], descriptions[fdc_id].strip()]
        for column, nutrient_id in zip(NUTRIENT_COLUMNS, NUTRIENT_IDS):
            value = values.get(nutrient_id, "")
            if value:
                filled[column] += 1
            row.append(value)
        _assert_delimiter_safe(row, f"food {ids[fdc_id]}")
        lines.append("F\t" + "\t".join(row))

        food_portions = portions.get(fdc_id)
        if not food_portions:
            continue
        foods_with_portions += 1
        for _, amount, label, grams in sorted(food_portions, key=lambda item: item[0]):
            fields = [amount, label, grams]
            _assert_delimiter_safe(fields, f"portion of {ids[fdc_id]}")
            lines.append("P\t" + "\t".join(fields))
            portion_line_count += 1

    counts = {
        "foods": len(descriptions),
        "portions": portion_line_count,
        "foods_with_portions": foods_with_portions,
        "excluded_portions": len(excluded),
        **{f"with_{column}": filled[column] for column in NUTRIENT_COLUMNS},
    }
    return lines, counts, excluded


def header_lines(generated_on: str, source_digest: str, counts: "dict[str, int]") -> "list[str]":
    nutrient_map = ";".join(f"{column}={nutrient_id}" for column, nutrient_id, _, _ in EXPECTED_NUTRIENTS)
    pairs = [
        ("generator", "scripts/generate-catalog-from-srlegacy.py"),
        ("source", SOURCE_NAME),
        ("source_url", SOURCE_URL),
        ("source_page", SOURCE_PAGE),
        ("release", SOURCE_RELEASE),
        ("publication_date", SOURCE_PUBLICATION_DATE),
        ("source_sha256", source_digest or "(built from an extracted directory; no archive digest)"),
        ("licence", LICENCE),
        ("licence_url", LICENCE_URL),
        ("licence_statement", LICENCE_STATEMENT),
        ("citation", CITATION),
        ("generated_on", generated_on),
        ("basis", "per 100 g edible portion"),
        ("id_scheme", f"{ID_PREFIX}<NDB number, {NDB_WIDTH} digits, zero-padded>"),
        ("id_note", "derived from the NDB number, never from fdc_id, which is a FoodData Central surrogate key"),
        ("nutrient_ids", nutrient_map),
        ("units", "kcal in kilocalories; sodium in milligrams; all other nutrients in grams"),
        ("missing_values", "an empty nutrient field means the source carries no value; it does NOT mean zero"),
        ("portion_note", "P lines belong to the nearest preceding F line; grams is the weight of amount x label"),
        (
            "excluded_portions",
            f"{counts['excluded_portions']} source portion rows carry amount=0 (e.g. '0 cup = 142 g') and are "
            f"omitted; an amount of zero makes the measure unusable and inferring 1 would invent a quantity",
        ),
        ("counts", ";".join(f"{key}={value}" for key, value in counts.items())),
    ]
    lines = [f"{FORMAT_MARKER}\t{FORMAT_VERSION}"]
    lines += [f"@{key}\t{value}" for key, value in pairs]
    _assert_delimiter_safe([value for _, value in pairs], "provenance header")
    lines.append("#")
    lines.append("# F\tid\tname\t" + "\t".join(NUTRIENT_COLUMNS))
    lines.append("# P\tamount\tlabel\tgrams")
    lines.append("#")
    return lines


def resolve_generated_on(explicit: "str | None") -> str:
    if explicit:
        _dt.date.fromisoformat(explicit)  # Reject a malformed override loudly.
        return explicit
    epoch = os.environ.get("SOURCE_DATE_EPOCH")
    if epoch:
        return _dt.datetime.fromtimestamp(int(epoch), _dt.timezone.utc).date().isoformat()
    return _dt.datetime.now(_dt.timezone.utc).date().isoformat()


def main(argv: "list[str] | None" = None) -> int:
    repo_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, help="the approved, SHA-256-pinned SR Legacy CSV zip")
    parser.add_argument("--download", action="store_true", help="fetch the archive into --cache if absent")
    parser.add_argument(
        "--cache",
        type=Path,
        default=Path(os.environ.get("TMPDIR", "/tmp")) / "supernutrition-srlegacy" / "sr_legacy.zip",
        help="where --download stores the archive (never inside the repository)",
    )
    parser.add_argument("--output", type=Path, default=repo_root / DEFAULT_OUTPUT)
    parser.add_argument("--generated-on", help="pin the @generated_on date (YYYY-MM-DD) for reproducible output")
    parser.add_argument("--report", action="store_true", help="also report the JSON-equivalent size for comparison")
    args = parser.parse_args(argv)

    if not args.input and not args.download:
        parser.error("pass --input <zip>, or --download to fetch the archive")

    try:
        source = args.input if args.input else download_archive(args.cache)
        if args.download and args.input:
            raise GeneratorError("pass either --input or --download, not both")
        print(f"  reading {source}")
        tables, digest = load_tables(Path(source))
        lines, counts, excluded = build(tables)
        body = header_lines(resolve_generated_on(args.generated_on), digest, counts) + lines
        text = "\n".join(body) + "\n"
    except GeneratorError as error:
        print(f"FAILED: {error}", file=sys.stderr)
        return 1

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(text, encoding="utf-8", newline="\n")

    raw = args.output.stat().st_size
    import gzip

    packed = len(gzip.compress(text.encode("utf-8"), 9, mtime=0))
    print()
    print(f"  wrote   {args.output}")
    print(f"  size    {raw:,} bytes uncompressed / {packed:,} bytes gzip -9 ({packed / 1024 / 1024:.2f} MB)")
    print(f"  lines   {len(body):,} total ({len(lines):,} data, {len(body) - len(lines):,} header)")
    for key, value in counts.items():
        print(f"  {key:<22} {value:,}")

    if args.report:
        import json

        print()
        print(f"  excluded portion rows (amount=0), {len(excluded)}:")
        for food_id, label, grams, name in excluded:
            print(f"    {food_id}  '0 {label}' = {grams} g   {name[:58]}")

        records = []
        for line in lines:
            fields = line.split("\t")
            if fields[0] == "F":
                records.append(
                    {
                        "id": fields[1],
                        "name": fields[2],
                        **{c: fields[3 + i] for i, c in enumerate(NUTRIENT_COLUMNS) if fields[3 + i]},
                        "portions": [],
                    }
                )
            else:
                records[-1]["portions"].append({"amount": fields[1], "label": fields[2], "grams": fields[3]})
        blob = json.dumps(records, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        print()
        print(f"  same data as compact JSON: {len(blob):,} bytes / {len(gzip.compress(blob, 9, mtime=0)):,} gzip")
    return 0


if __name__ == "__main__":
    sys.exit(main())
