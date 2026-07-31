#!/usr/bin/env python3
"""Convert FPB/YOLOv12-FoodWeight CSV output to the G-3 prediction schema.

This adapter is intentionally dependency-free and research-only. It does not
download the FPB dataset, execute AGPL code, or infer missing food labels.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
from collections import defaultdict
from pathlib import Path
from typing import Any


REQUIRED_COLUMNS = {
    "image_name",
    "class_id",
    "xmin",
    "ymin",
    "xmax",
    "ymax",
    "weight",
    "confidence",
}


def _load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def _finite_float(value: str, field: str, row_number: int) -> float:
    try:
        result = float(value)
    except ValueError as error:
        raise ValueError(f"row {row_number}: {field} must be numeric") from error
    if not math.isfinite(result):
        raise ValueError(f"row {row_number}: {field} must be finite")
    return result


def _class_label(label_map: dict[str, Any], class_id: str, row_number: int) -> str:
    label = label_map.get(class_id, label_map.get(str(int(class_id))))
    if not isinstance(label, str) or not label.strip():
        raise ValueError(f"row {row_number}: class_id {class_id} is missing from label map")
    return label.strip()


def convert(
    prediction_csv: Path,
    label_map_path: Path,
    fixture_map_path: Path,
) -> dict[str, Any]:
    label_map = _load_json(label_map_path)
    fixture_map = _load_json(fixture_map_path)
    if not isinstance(label_map, dict) or not isinstance(fixture_map, dict):
        raise ValueError("label map and fixture map must be JSON objects")

    cases: dict[str, dict[str, Any]] = {}
    aggregate_masses: dict[str, defaultdict[str, float]] = defaultdict(lambda: defaultdict(float))
    with prediction_csv.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        columns = set(reader.fieldnames or [])
        missing = REQUIRED_COLUMNS - columns
        if missing:
            raise ValueError(f"prediction CSV is missing columns: {sorted(missing)}")

        for row_number, row in enumerate(reader, start=2):
            image_name = str(row["image_name"]).strip()
            fixture = fixture_map.get(image_name, fixture_map.get(Path(image_name).name))
            if not isinstance(fixture, str) or not fixture.strip():
                raise ValueError(f"row {row_number}: image_name {image_name!r} is missing from fixture map")
            fixture = fixture.strip()
            class_id = str(row["class_id"]).strip()
            label = _class_label(label_map, class_id, row_number)
            weight = _finite_float(row["weight"], "weight", row_number)
            confidence = _finite_float(row["confidence"], "confidence", row_number)
            if weight < 0:
                raise ValueError(f"row {row_number}: weight must not be negative")
            if confidence < 0 or confidence > 1:
                raise ValueError(f"row {row_number}: confidence must be between 0 and 1")
            box = [
                _finite_float(row[field], field, row_number)
                for field in ("xmin", "ymin", "xmax", "ymax")
            ]
            cases.setdefault(fixture, {"ingredients": [], "ingredient_masses_g": {}, "instances": []})
            cases[fixture]["instances"].append({
                "label": label,
                "class_id": class_id,
                "box": box,
                "confidence": confidence,
                "weight_grams": weight,
            })
            if label not in cases[fixture]["ingredients"]:
                cases[fixture]["ingredients"].append(label)
            aggregate_masses[fixture][label] += weight

    for fixture, masses in aggregate_masses.items():
        cases[fixture]["ingredient_masses_g"] = dict(sorted(masses.items()))
        cases[fixture]["ingredients"].sort()

    return {
        "schema_version": "g3-predictions/research-v1",
        "source": "fpb-yolov12-foodweight",
        "research_only": True,
        "cases": dict(sorted(cases.items())),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--label-map", type=Path, required=True)
    parser.add_argument("--fixture-map", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = convert(args.predictions, args.label_map, args.fixture_map)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
