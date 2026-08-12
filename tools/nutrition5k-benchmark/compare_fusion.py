#!/usr/bin/env python3
"""Compare the PoC raw detector with its current dish-prior fusion policy.

This mirrors the bounded host-visible parts of PocInferenceEngine:
Food-101 top-1 dish classification, filtered YOLO-World COCO proposals,
per-label NMS, and the exact hard top-1 dish prior from
PocDishIngredientPriors.java. MobileSAM is not run because it does not change
ingredient proposal identity and is class-agnostic.

Nutrition5k ingredient metadata is a weak proxy for evaluation: it is recipe
metadata, not per-pixel visible-ingredient ground truth. Scores are limited to
the five detector labels supported by the PoC.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import statistics
from pathlib import Path
from typing import Any

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageOps


COCO_NAMES = [
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic_light",
    "fire_hydrant", "stop_sign", "parking_meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
    "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
    "skis", "snowboard", "sports_ball", "kite", "baseball_bat", "baseball_glove", "skateboard", "surfboard",
    "tennis_racket", "bottle", "wine_glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
    "sandwich", "orange", "broccoli", "carrot", "hot_dog", "pizza", "donut", "cake", "chair", "couch",
    "potted_plant", "bed", "dining_table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard",
    "cell_phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase",
    "scissors", "teddy_bear", "hair_drier", "toothbrush",
]
COMPONENT_LABELS = {"banana", "apple", "orange", "broccoli", "carrot"}
REGION_CONFIDENCE = 0.10
REGION_NMS_IOU = 0.70
MAX_REGIONS = 16

PRIORS = {
    "greek_salad": ["cucumber", "tomato", "red_onion", "bell_pepper", "feta_cheese", "olives", "olive_oil"],
    "caesar_salad": ["romaine_lettuce", "chicken", "parmesan", "croutons", "caesar_dressing"],
    "garden_salad": ["lettuce", "tomato", "cucumber", "carrot", "onion", "bell_pepper"],
    "chopped_salad": ["lettuce", "tomato", "cucumber", "carrot", "corn", "onion"],
    "hot_and_sour_soup": ["tofu", "mushroom", "bamboo_shoots", "pork", "egg", "vinegar"],
    "miso_soup": ["miso", "tofu", "seaweed", "green_onion", "mushroom"],
    "ramen": ["noodles", "pork", "egg", "seaweed", "green_onion", "broth"],
    "pizza": ["dough", "tomato_sauce", "mozzarella", "cheese", "pepperoni", "mushroom", "onion"],
    "pork_chop": ["pork_chop", "pork", "carrot", "potato", "green_beans", "apple_sauce"],
    "steak": ["beef", "potato", "green_beans", "salad", "sauce"],
    "grilled_salmon": ["salmon", "lemon", "rice", "broccoli", "asparagus"],
    "sushi": ["rice", "salmon", "tuna", "avocado", "cucumber", "seaweed"],
    "fried_rice": ["rice", "egg", "peas", "carrot", "green_onion"],
    "bibimbap": ["rice", "beef", "egg", "carrot", "spinach", "mushroom"],
    "chicken_curry": ["chicken", "rice", "onion", "tomato", "coconut_milk"],
    "hamburger": ["beef", "bun", "lettuce", "tomato", "onion", "pickle", "cheese"],
    "lasagna": ["pasta", "tomato_sauce", "beef", "cheese", "ricotta"],
    "spaghetti_bolognese": ["pasta", "beef", "tomato_sauce", "parmesan"],
    "waffles": ["flour", "egg", "milk", "butter", "syrup", "berries"],
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def iou(a: list[float], b: list[float]) -> float:
    left = max(a[0], b[0])
    top = max(a[1], b[1])
    right = min(a[2], b[2])
    bottom = min(a[3], b[3])
    intersection = max(0.0, right - left) * max(0.0, bottom - top)
    area_a = max(0.0, a[2] - a[0]) * max(0.0, a[3] - a[1])
    area_b = max(0.0, b[2] - b[0]) * max(0.0, b[3] - b[1])
    union = area_a + area_b - intersection
    return intersection / union if union else 0.0


def normalize_proposals(raw: np.ndarray) -> np.ndarray:
    value = raw
    while value.ndim > 2 and value.shape[0] == 1:
        value = value[0]
    if value.ndim != 2:
        raise ValueError(f"YOLO output must reduce to rank 2, got {raw.shape}")
    if value.shape[0] >= 84:
        return value
    if value.shape[1] >= 84:
        return value.T
    raise ValueError(f"YOLO output shape is not [84,N] or [N,84]: {raw.shape}")


def region_proposals(session: ort.InferenceSession, image: Image.Image) -> list[dict[str, Any]]:
    resized = image.resize((640, 640), Image.Resampling.BILINEAR).convert("RGB")
    pixels = np.asarray(resized, dtype=np.float32) / 255.0
    tensor = np.transpose(pixels, (2, 0, 1))[None, ...]
    output = session.run(None, {session.get_inputs()[0].name: tensor})[0]
    data = normalize_proposals(np.asarray(output))
    candidates: list[dict[str, Any]] = []
    for column in data.T:
        scores = column[4 : 4 + len(COCO_NAMES)]
        class_index = int(np.argmax(scores))
        confidence = float(scores[class_index])
        label = COCO_NAMES[class_index]
        if confidence < REGION_CONFIDENCE or label not in COMPONENT_LABELS:
            continue
        center_x, center_y, width, height = map(float, column[:4])
        scale = 1.0 if max(abs(center_x), abs(center_y), abs(width), abs(height)) <= 2.0 else 1.0 / 640.0
        box = [
            max(0.0, min(1.0, (center_x - width * 0.5) * scale)),
            max(0.0, min(1.0, (center_y - height * 0.5) * scale)),
            max(0.0, min(1.0, (center_x + width * 0.5) * scale)),
            max(0.0, min(1.0, (center_y + height * 0.5) * scale)),
        ]
        if box[2] <= box[0] or box[3] <= box[1]:
            continue
        candidates.append({"label": label, "confidence": confidence, "box": box})
    candidates.sort(key=lambda item: item["confidence"], reverse=True)
    kept: list[dict[str, Any]] = []
    for candidate in candidates:
        if any(
            existing["label"] == candidate["label"]
            and iou(existing["box"], candidate["box"]) > REGION_NMS_IOU
            for existing in kept
        ):
            continue
        kept.append(candidate)
        if len(kept) == MAX_REGIONS:
            break
    return kept


def dish_candidates(
    session: ort.InferenceSession,
    labels: list[str],
    image: Image.Image,
) -> list[dict[str, Any]]:
    square = ImageOps.fit(image.convert("RGB"), (224, 224), method=Image.Resampling.BILINEAR, centering=(0.5, 0.5))
    pixels = np.asarray(square, dtype=np.float32) / 255.0
    mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
    std = np.array([0.229, 0.224, 0.225], dtype=np.float32)
    tensor = np.transpose((pixels - mean) / std, (2, 0, 1))[None, ...]
    output = np.asarray(session.run(None, {session.get_inputs()[0].name: tensor})[0]).reshape(-1)
    probabilities = np.exp(output - np.max(output))
    probabilities = probabilities / probabilities.sum()
    indices = np.argsort(probabilities)[::-1][:5]
    return [{"label": labels[int(index)], "confidence": float(probabilities[index])} for index in indices]


def weak_targets(metadata_ingredients: list[dict[str, Any]]) -> set[str]:
    targets: set[str] = set()
    for ingredient in metadata_ingredients:
        name = ingredient["name"].lower()
        for label in COMPONENT_LABELS:
            if label in name:
                targets.add(label)
    return targets


def score(expected: set[str], predicted: set[str]) -> tuple[int, int, int]:
    return len(expected & predicted), len(predicted - expected), len(expected - predicted)


def aggregate(rows: list[dict[str, Any]], key: str) -> dict[str, Any]:
    true_positive = false_positive = false_negative = 0
    per_case_f1: list[float] = []
    nonempty_cases = 0
    for row in rows:
        expected = set(row["weak_detector_targets"])
        predicted = set(row[key])
        tp, fp, fn = score(expected, predicted)
        true_positive += tp
        false_positive += fp
        false_negative += fn
        if expected:
            nonempty_cases += 1
            precision = tp / len(predicted) if predicted else 0.0
            recall = tp / len(expected)
            per_case_f1.append(2 * precision * recall / (precision + recall) if precision + recall else 0.0)
    precision = true_positive / (true_positive + false_positive) if true_positive + false_positive else 0.0
    recall = true_positive / (true_positive + false_negative) if true_positive + false_negative else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {
        "micro_true_positive": true_positive,
        "micro_false_positive": false_positive,
        "micro_false_negative": false_negative,
        "micro_precision": precision,
        "micro_recall": recall,
        "micro_f1": f1,
        "macro_f1_on_nonempty_weak_target_cases": statistics.fmean(per_case_f1) if per_case_f1 else None,
        "nonempty_weak_target_cases": nonempty_cases,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--dish-model", type=Path, required=True)
    parser.add_argument("--dish-labels", type=Path, required=True)
    parser.add_argument("--region-model", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    labels = [line.strip() for line in args.dish_labels.read_text(encoding="utf-8").splitlines() if line.strip()]
    options = ort.SessionOptions()
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_EXTENDED
    options.intra_op_num_threads = 4
    dish_session = ort.InferenceSession(str(args.dish_model), options, providers=["CPUExecutionProvider"])
    region_session = ort.InferenceSession(str(args.region_model), options, providers=["CPUExecutionProvider"])

    rows: list[dict[str, Any]] = []
    for index, case in enumerate(manifest["cases"], start=1):
        image_path = args.manifest.parent / case["crop_file"]
        image = Image.open(image_path).convert("RGB")
        dishes = dish_candidates(dish_session, labels, image)
        raw = region_proposals(region_session, image)
        prior = PRIORS.get(dishes[0]["label"], [])
        fusion = raw if not prior else [proposal for proposal in raw if proposal["label"] in prior]
        expected = weak_targets(case["metadata_ingredients"])
        rows.append(
            {
                "case_id": case["case_id"],
                "dish_id": case["dish_id"],
                "dish_top1": dishes[0],
                "dish_top3": dishes[:3],
                "dish_prior": prior,
                "weak_detector_targets": sorted(expected),
                "raw_detector_labels": sorted({proposal["label"] for proposal in raw}),
                "fusion_labels": sorted({proposal["label"] for proposal in fusion}),
                "raw_region_count": len(raw),
                "fusion_region_count": len(fusion),
                "raw_regions": raw,
                "fusion_regions": fusion,
                "metadata_ingredients": [item["name"] for item in case["metadata_ingredients"]],
                "crop_file": case["crop_file"],
            }
        )
        if index % 10 == 0 or index == len(manifest["cases"]):
            print(f"inferred {index}/{len(manifest['cases'])}", flush=True)

    raw_metrics = aggregate(rows, "raw_detector_labels")
    fusion_metrics = aggregate(rows, "fusion_labels")
    raw_regions = sum(row["raw_region_count"] for row in rows)
    fusion_regions = sum(row["fusion_region_count"] for row in rows)
    result = {
        "schema": "supernutrition.nutrition5k-fusion-comparison.v1",
        "dataset": manifest["dataset"],
        "sample_count": len(rows),
        "sample_manifest": str(args.manifest),
        "weak_ground_truth_warning": (
            "Nutrition5k ingredient metadata is recipe-level metadata, not per-pixel visible-ingredient ground truth. "
            "Metrics below only score the five PoC detector labels when their names occur in metadata."
        ),
        "crop_policy": manifest["crop_policy"],
        "runtime": {
            "onnxruntime": ort.__version__,
            "provider": "CPUExecutionProvider",
            "dish_model": str(args.dish_model),
            "dish_model_sha256": sha256_file(args.dish_model),
            "region_model": str(args.region_model),
            "region_model_sha256": sha256_file(args.region_model),
            "dish_prior_source": "tools/g3-benchmark/android-runner/app/src/main/java/com/supernutrition/g3benchmark/poc/PocDishIngredientPriors.java",
            "component_vocabulary": sorted(COMPONENT_LABELS),
            "region_confidence_threshold": REGION_CONFIDENCE,
            "region_nms_iou": REGION_NMS_IOU,
            "max_regions": MAX_REGIONS,
        },
        "strategies": {
            "no_fusion": {
                "description": "Raw filtered YOLO-World proposals after confidence threshold, per-label NMS, and cap; no dish-prior gate.",
                "total_regions": raw_regions,
                "metrics": raw_metrics,
            },
            "hard_top1_dish_prior": {
                "description": "Current PoC fusion: use Food-101 top-1 label, then keep only detector labels in the exact prior; raw proposals are retained only for debug.",
                "total_regions": fusion_regions,
                "metrics": fusion_metrics,
            },
        },
        "fusion_delta": {
            "region_count_delta": fusion_regions - raw_regions,
            "region_keep_rate": fusion_regions / raw_regions if raw_regions else None,
            "micro_f1_delta": fusion_metrics["micro_f1"] - raw_metrics["micro_f1"],
            "micro_recall_delta": fusion_metrics["micro_recall"] - raw_metrics["micro_recall"],
            "micro_precision_delta": fusion_metrics["micro_precision"] - raw_metrics["micro_precision"],
        },
        "cases": rows,
    }
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"wrote {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
