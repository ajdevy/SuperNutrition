#!/usr/bin/env python3
"""Download and crop a deterministic Nutrition5k RGB sample.

The official overhead RGB frames contain the plate plus rig/background markers.
This script keeps only the canonical plate interior and records the crop box.
The crop is a preprocessing convenience, not a ground-truth food segmentation.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import random
import urllib.parse
import urllib.request
from io import BytesIO
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw


BUCKET = "nutrition5k_dataset"
PREFIX = "nutrition5k_dataset"
STORAGE_ROOT = f"https://storage.googleapis.com/{BUCKET}/{PREFIX}"
API_ROOT = f"https://storage.googleapis.com/storage/v1/b/{BUCKET}/o"
DEFAULT_SEED = 20260803


def fetch_bytes(url: str) -> bytes:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "SuperNutrition-Nutrition5k-benchmark/1.0"},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def fetch_text(url: str) -> str:
    return fetch_bytes(url).decode("utf-8")


def metadata_url(name: str) -> str:
    return f"{STORAGE_ROOT}/{name}"


def list_objects(prefix: str) -> list[dict[str, Any]]:
    page_token: str | None = None
    objects: list[dict[str, Any]] = []
    while True:
        query = {
            "prefix": prefix,
            "maxResults": "1000",
        }
        if page_token:
            query["pageToken"] = page_token
        url = API_ROOT + "?" + urllib.parse.urlencode(query)
        payload = json.loads(fetch_text(url))
        objects.extend(payload.get("items", []))
        page_token = payload.get("nextPageToken")
        if not page_token:
            return objects


def parse_metadata(*texts: str) -> dict[str, dict[str, Any]]:
    records: dict[str, dict[str, Any]] = {}
    for text in texts:
        for row in csv.reader(text.splitlines()):
            if len(row) < 7:
                continue
            dish_id = row[0].strip()
            ingredients: list[dict[str, Any]] = []
            values = row[6:]
            for offset in range(0, len(values) - 6, 7):
                ingredient_id, name, grams, calories, fat, carb, protein = values[offset : offset + 7]
                if not name.strip():
                    continue
                ingredients.append(
                    {
                        "id": ingredient_id.strip(),
                        "name": name.strip(),
                        "grams": float(grams),
                        "calories": float(calories),
                        "fat_g": float(fat),
                        "carb_g": float(carb),
                        "protein_g": float(protein),
                    }
                )
            records[dish_id] = {
                "dish_id": dish_id,
                "total_calories": float(row[1]),
                "total_mass_g": float(row[2]),
                "total_fat_g": float(row[3]),
                "total_carb_g": float(row[4]),
                "total_protein_g": float(row[5]),
                "ingredients": ingredients,
            }
    return records


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def plate_interior_crop(image: Image.Image) -> tuple[Image.Image, list[int]]:
    """Remove the fixed rig border/QR markers from the canonical overhead view.

    Nutrition5k overhead RGB frames are 640x480 in the released sample. The
    inner plate area is stable enough for a deterministic benchmark crop. An
    elliptical interior mask removes QR markers that sit outside the plate.
    The result deliberately keeps plate context around the food; it is not a
    segmentation mask.
    """

    rgb = image.convert("RGB")
    width, height = rgb.size
    left = round(width * 0.25)
    top = round(height * 0.12)
    right = round(width * 0.92)
    bottom = round(height * 0.86)
    box = [left, top, right, bottom]
    crop = rgb.crop((left, top, right, bottom))
    mask = Image.new("L", crop.size, 0)
    margin_x = round(crop.width * 0.025)
    margin_y = round(crop.height * 0.025)
    ImageDraw.Draw(mask).ellipse(
        (margin_x, margin_y, crop.width - margin_x, crop.height - margin_y),
        fill=255,
    )
    plate_background = Image.new("RGB", crop.size, (220, 220, 220))
    return Image.composite(crop, plate_background, mask), box


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("tools/nutrition5k-benchmark/data/nutrition5k-100"),
    )
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument(
        "--exclude-manifest",
        action="append",
        type=Path,
        default=[],
        help="Manifest(s) whose dish IDs must be excluded from this sample.",
    )
    parser.add_argument("--sample-prefix", default="nutrition5k")
    args = parser.parse_args()
    if args.count <= 0:
        raise SystemExit("--count must be positive")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    raw_dir = args.output_dir / "raw"
    crop_dir = args.output_dir / "dish_crops"
    raw_dir.mkdir(exist_ok=True)
    crop_dir.mkdir(exist_ok=True)

    metadata = parse_metadata(
        fetch_text(metadata_url("metadata/dish_metadata_cafe1.csv")),
        fetch_text(metadata_url("metadata/dish_metadata_cafe2.csv")),
    )
    test_ids = [
        line.strip()
        for line in fetch_text(metadata_url("dish_ids/splits/rgb_test_ids.txt")).splitlines()
        if line.strip()
    ]
    overhead_prefix = f"{PREFIX}/imagery/realsense_overhead/"
    available_overhead = {
        item["name"].split("/")[-2]
        for item in list_objects(overhead_prefix)
        if item.get("name", "").endswith("/rgb.png")
    }
    eligible = sorted(
        dish_id
        for dish_id in test_ids
        if dish_id in metadata and dish_id in available_overhead
    )
    excluded_dish_ids = {
        case["dish_id"]
        for manifest_path in args.exclude_manifest
        for case in json.loads(manifest_path.read_text(encoding="utf-8"))["cases"]
    }
    eligible = [dish_id for dish_id in eligible if dish_id not in excluded_dish_ids]
    if len(eligible) < args.count:
        raise SystemExit(
            f"Only {len(eligible)} eligible non-excluded test dishes; requested {args.count}"
        )

    rng = random.Random(args.seed)
    selected = sorted(rng.sample(eligible, args.count))
    cases: list[dict[str, Any]] = []

    for index, dish_id in enumerate(selected, start=1):
        source_name = f"{PREFIX}/imagery/realsense_overhead/{dish_id}/rgb.png"
        source_url = f"{STORAGE_ROOT}/imagery/realsense_overhead/{dish_id}/rgb.png"
        raw_path = raw_dir / f"{dish_id}.png"
        crop_path = crop_dir / f"{dish_id}.jpg"
        if raw_path.is_file():
            raw_bytes = raw_path.read_bytes()
        else:
            raw_bytes = fetch_bytes(source_url)
            raw_path.write_bytes(raw_bytes)
        image = Image.open(BytesIO(raw_bytes))
        crop, crop_box_px = plate_interior_crop(image)
        crop.save(crop_path, format="JPEG", quality=95, optimize=True)
        dish = metadata[dish_id]
        cases.append(
            {
                "case_id": f"{args.sample_prefix}-{index:03d}-{dish_id}",
                "dish_id": dish_id,
                "split": "rgb_test",
                "source_object": source_name,
                "source_url": source_url,
                "source_sha256": sha256(raw_bytes),
                "source_size_px": list(image.size),
                "crop_file": str(crop_path.relative_to(args.output_dir)),
                "crop_box_px": crop_box_px,
                "crop_box_normalized": [
                    crop_box_px[0] / image.width,
                    crop_box_px[1] / image.height,
                    crop_box_px[2] / image.width,
                    crop_box_px[3] / image.height,
                ],
                "crop_type": "canonical_plate_interior_not_food_segmentation",
                "metadata_ingredients": dish["ingredients"],
                "metadata_total_mass_g": dish["total_mass_g"],
                "metadata_total_calories": dish["total_calories"],
            }
        )
        if index % 10 == 0 or index == len(selected):
            print(f"downloaded {index}/{len(selected)}")

    manifest = {
        "schema": "supernutrition.nutrition5k-sample.v1",
        "dataset": "Nutrition5k",
        "dataset_source": "https://github.com/google-research-datasets/Nutrition5k",
        "storage_bucket": f"gs://{BUCKET}",
        "license": "CC BY 4.0",
        "sample_count": len(cases),
        "selection": {
            "split": "rgb_test",
            "seed": args.seed,
            "sampling": "random sample without replacement from sorted eligible test dish IDs",
            "excluded_manifest_paths": [str(path) for path in args.exclude_manifest],
            "excluded_dish_count": len(excluded_dish_ids),
            "excluded_dish_ids": sorted(excluded_dish_ids),
        },
        "crop_policy": {
            "description": "Canonical overhead plate interior crop with an elliptical mask; removes rig border and QR markers while retaining plate context.",
            "normalized_box": [0.25, 0.12, 0.92, 0.86],
            "mask": "ellipse_inside_crop",
            "not_ground_truth_segmentation": True,
        },
        "cases": cases,
    }
    (args.output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {args.output_dir / 'manifest.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
