#!/usr/bin/env python3
"""Research-only benchmark for the downloaded public-data sample.

This script deliberately stays outside the mobile runtime. YOLO-World boxes are
converted to a binary foreground mask only so a pixel F1 can be measured; this
is not instance segmentation. MobileSAM uses the same fixed centre-point
prompt as the Android smoke and is class-agnostic. The mass result is a small
RGB-D linear baseline trained on part of the public Nutrition5k sample, not a
released model and not consented/weighed product evidence.
"""

from __future__ import annotations

import argparse
import json
import math
import statistics
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image


COCO_NAMES = [
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
    "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog",
    "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella",
    "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat",
    "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup", "fork",
    "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog",
    "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv",
    "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
    "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush",
]


def resize_rgb(path: Path, size: int) -> np.ndarray:
    with Image.open(path) as image:
        image = image.convert("RGB").resize((size, size), Image.Resampling.BILINEAR)
        return np.asarray(image, dtype=np.float32) / 255.0


def binary_f1(predicted: np.ndarray, truth: np.ndarray) -> tuple[float, int, int, int]:
    predicted = predicted.astype(bool)
    truth = truth.astype(bool)
    tp = int(np.logical_and(predicted, truth).sum())
    fp = int(np.logical_and(predicted, ~truth).sum())
    fn = int(np.logical_and(~predicted, truth).sum())
    return (2.0 * tp / (2 * tp + fp + fn) if tp or fp or fn else 1.0), tp, fp, fn


def iou(left: np.ndarray, right: np.ndarray) -> float:
    x1 = max(float(left[0]), float(right[0]))
    y1 = max(float(left[1]), float(right[1]))
    x2 = min(float(left[2]), float(right[2]))
    y2 = min(float(left[3]), float(right[3]))
    intersection = max(0.0, x2 - x1) * max(0.0, y2 - y1)
    left_area = max(0.0, float(left[2] - left[0])) * max(0.0, float(left[3] - left[1]))
    right_area = max(0.0, float(right[2] - right[0])) * max(0.0, float(right[3] - right[1]))
    union = left_area + right_area - intersection
    return intersection / union if union else 0.0


def yolo_boxes(session: ort.InferenceSession, image: np.ndarray) -> list[tuple[int, np.ndarray, float]]:
    input_name = session.get_inputs()[0].name
    raw = session.run(None, {input_name: np.transpose(image, (2, 0, 1))[None, ...]})[0][0]
    candidates: list[tuple[int, np.ndarray, float]] = []
    for prediction in range(raw.shape[1]):
        class_scores = raw[4:, prediction]
        class_index = int(np.argmax(class_scores))
        confidence = float(class_scores[class_index])
        if confidence < 0.25:
            continue
        centre_x, centre_y, width, height = raw[:4, prediction]
        box = np.array([
            centre_x - width / 2.0,
            centre_y - height / 2.0,
            centre_x + width / 2.0,
            centre_y + height / 2.0,
        ], dtype=np.float32)
        candidates.append((class_index, box, confidence))
    candidates.sort(key=lambda item: item[2], reverse=True)
    kept: list[tuple[int, np.ndarray, float]] = []
    for candidate in candidates:
        if any(candidate[0] == existing[0] and iou(candidate[1], existing[1]) > 0.7 for existing in kept):
            continue
        kept.append(candidate)
    return kept


def boxes_mask(boxes: list[tuple[int, np.ndarray, float]], size: int) -> np.ndarray:
    mask = np.zeros((size, size), dtype=bool)
    for _, box, _ in boxes:
        left, top, right, bottom = np.clip(np.rint(box).astype(int), 0, size)
        if right > left and bottom > top:
            mask[top:bottom, left:right] = True
    return mask


def run_yolo_foodseg(root: Path, model_path: Path) -> dict:
    manifest = json.loads((root / "manifest.json").read_text())
    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    size = 640
    total = [0, 0, 0]
    per_image: list[float] = []
    detections = 0
    for index, record in enumerate(manifest["records"], start=1):
        image = resize_rgb(root / record["image"], size)
        with Image.open(root / record["label"]) as label_image:
            truth = np.asarray(label_image.resize((size, size), Image.Resampling.NEAREST)) > 0
        boxes = yolo_boxes(session, image)
        detections += len(boxes)
        f1, tp, fp, fn = binary_f1(boxes_mask(boxes, size), truth)
        total[0] += tp
        total[1] += fp
        total[2] += fn
        per_image.append(f1)
        if index % 25 == 0:
            print(f"yolo foodseg {index}/{len(manifest['records'])}", file=sys.stderr, flush=True)
    micro_f1 = 2 * total[0] / (2 * total[0] + total[1] + total[2])
    return {
        "candidate": "yolo-world-v2",
        "sample_count": len(per_image),
        "metric": "binary foreground pixel F1 from detected boxes vs FoodSeg103 semantic foreground mask",
        "micro_f1": micro_f1,
        "macro_f1": statistics.fmean(per_image),
        "median_f1": statistics.median(per_image),
        "detections": detections,
        "confidence_floor": 0.25,
        "nms_iou": 0.7,
        "limitation": "box-derived foreground overlap, not class-aware or instance segmentation",
    }


def run_mobile_sam_foodseg(root: Path, encoder_path: Path, decoder_path: Path) -> dict:
    manifest = json.loads((root / "manifest.json").read_text())
    encoder = ort.InferenceSession(str(encoder_path), providers=["CPUExecutionProvider"])
    decoder = ort.InferenceSession(str(decoder_path), providers=["CPUExecutionProvider"])
    size = 1024
    encoder_name = encoder.get_inputs()[0].name
    total = [0, 0, 0]
    per_image: list[float] = []
    for index, record in enumerate(manifest["records"], start=1):
        image = resize_rgb(root / record["image"], size)
        normalized = (image * 255.0 - np.array([123.675, 116.28, 103.53], dtype=np.float32)) / np.array([58.395, 57.12, 57.375], dtype=np.float32)
        embedding = encoder.run(None, {encoder_name: normalized.astype(np.float32)})[0]
        decoder_inputs = {
            "image_embeddings": embedding,
            "point_coords": np.array([[[size / 2.0, size / 2.0], [0.0, 0.0]]], dtype=np.float32),
            "point_labels": np.array([[1.0, -1.0]], dtype=np.float32),
            "mask_input": np.zeros((1, 1, 256, 256), dtype=np.float32),
            "has_mask_input": np.array([0.0], dtype=np.float32),
            "orig_im_size": np.array([size, size], dtype=np.float32),
        }
        masks, iou_scores, _ = decoder.run(None, decoder_inputs)
        best = int(np.argmax(np.asarray(iou_scores).reshape(-1)))
        predicted = np.asarray(masks)[0, best] > 0.0
        with Image.open(root / record["label"]) as label_image:
            truth = np.asarray(label_image.resize((size, size), Image.Resampling.NEAREST)) > 0
        f1, tp, fp, fn = binary_f1(predicted, truth)
        total[0] += tp
        total[1] += fp
        total[2] += fn
        per_image.append(f1)
        if index % 10 == 0:
            print(f"mobile sam foodseg {index}/{len(manifest['records'])}", file=sys.stderr, flush=True)
    micro_f1 = 2 * total[0] / (2 * total[0] + total[1] + total[2])
    return {
        "candidate": "mobile-sam",
        "sample_count": len(per_image),
        "metric": "binary foreground pixel F1 from fixed centre-point prompted mask vs FoodSeg103 semantic foreground mask",
        "micro_f1": micro_f1,
        "macro_f1": statistics.fmean(per_image),
        "median_f1": statistics.median(per_image),
        "prompt": "one centre positive point plus one padding negative point",
        "mask_selection": "highest decoder iou_predictions score",
        "limitation": "class-agnostic prompted mask; prompt is not ground-truth-guided and this is not ingredient segmentation",
    }


def nutrition_features(root: Path, record: dict) -> np.ndarray:
    with Image.open(root / record["rgb_path"]) as rgb_image:
        rgb = np.asarray(rgb_image.convert("RGB"), dtype=np.float32) / 255.0
    with Image.open(root / record["depth_path"]) as depth_image:
        depth = np.asarray(depth_image, dtype=np.float32)
    valid = depth > 0
    depth_values = depth[valid]
    if not len(depth_values):
        depth_values = np.array([0.0], dtype=np.float32)
    return np.array([
        1.0,
        float(rgb.mean()),
        float(rgb.std()),
        float(rgb[..., 0].mean()),
        float(rgb[..., 1].mean()),
        float(rgb[..., 2].mean()),
        float(valid.mean()),
        float(depth_values.mean()),
        float(depth_values.std()),
        float(np.median(depth_values)),
    ], dtype=np.float64)


def run_mass_baseline(root: Path) -> dict:
    manifest = json.loads((root / "manifest.json").read_text())
    records = manifest["records"]
    split = max(1, int(len(records) * 2 / 3))
    features = np.vstack([nutrition_features(root, record) for record in records])
    targets = np.array([float(record["total_mass_grams"]) for record in records], dtype=np.float64)
    coefficients, _, _, _ = np.linalg.lstsq(features[:split], targets[:split], rcond=None)
    predictions = np.maximum(0.0, features[split:] @ coefficients)
    errors = np.abs(predictions - targets[split:])
    median_prediction = float(np.median(targets[:split]))
    median_errors = np.abs(median_prediction - targets[split:])
    return {
        "candidate": "rgb-depth-linear-mass-baseline",
        "sample_count": len(records),
        "fit_count": split,
        "holdout_count": len(records) - split,
        "target": "Nutrition5k total_mass_grams",
        "feature_set": "RGB mean/std/channel means plus depth valid fraction/mean/std/median",
        "split_note": "deterministic split within the downloaded rgb_test_ids sample; not the official train/test protocol",
        "mae_grams": float(errors.mean()),
        "p90_abs_error_grams": float(np.percentile(errors, 90)),
        "median_abs_error_grams": float(np.median(errors)),
        "median_baseline_mae_grams": float(median_errors.mean()),
        "median_baseline_p90_abs_error_grams": float(np.percentile(median_errors, 90)),
        "negative_clamp": True,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--foodseg-root", type=Path, required=True)
    parser.add_argument("--nutrition-root", type=Path, required=True)
    parser.add_argument("--yolo-model", type=Path, required=True)
    parser.add_argument("--mobile-sam-encoder", type=Path, required=True)
    parser.add_argument("--mobile-sam-decoder", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = {
        "schema_version": "supernutrition.public-model-benchmark.v1",
        "research_only": True,
        "foodseg103": run_yolo_foodseg(args.foodseg_root, args.yolo_model),
        "mobile_sam_foodseg103": run_mobile_sam_foodseg(args.foodseg_root, args.mobile_sam_encoder, args.mobile_sam_decoder),
        "nutrition5k": run_mass_baseline(args.nutrition_root),
        "interpretation": {
            "segmentation": "Neither result is class-aware ingredient segmentation: YOLO-World contributes detected boxes and MobileSAM contributes a prompted class-agnostic mask.",
            "mass": "The two ONNX candidates have no mass output. The RGB-D linear result is a reproducible public-data baseline only.",
            "release_gate": "Do not use public-data metrics as consented-corpus, phone-capture, G3 signoff, or production-quality evidence.",
        },
    }
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
