#!/usr/bin/env python3
"""Run the research-only FPB/YOLOv12 checkpoint on complex meal fixtures.

The released FPB checkpoint adds one raw grams channel to a YOLO detection head.
It is AGPL-bound and is never imported by the app. This runner deliberately
keeps its labels outside the SuperNutrition taxonomy: a raw FPB label cannot be
silently promoted to a visible ingredient candidate.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import platform
import time
from pathlib import Path
from typing import Any


FIXTURES = {
    "steak-side-plate": "steak-side-plate.jpg",
    "fish-side-plate": "fish-side-plate.jpg",
    "chopped-salad": "chopped-salad.jpg",
    "buddha-bowl": "buddha-bowl.jpg",
    "salmon-grain-plate": "salmon-grain-plate.jpg",
}

RAW_LABEL_NAMESPACE = "fpb_yolov12_unmapped/"


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _raw_label(label: str) -> str:
    normalized = label.strip().lower().replace(" ", "_")
    if not normalized or any(character not in "abcdefghijklmnopqrstuvwxyz0123456789_-" for character in normalized):
        raise ValueError(f"FPB label cannot be represented safely: {label!r}")
    return f"{RAW_LABEL_NAMESPACE}{normalized}"


def build_g3_predictions(raw_cases: dict[str, dict[str, Any]]) -> dict[str, Any]:
    """Return strict, unmapped labels for the dependency-free G-3 evaluator."""
    cases: dict[str, dict[str, Any]] = {}
    for fixture, case in raw_cases.items():
        detections = case.get("detections")
        if not isinstance(detections, list):
            raise ValueError(f"{fixture}: detections must be a list")
        labels: set[str] = set()
        for detection in detections:
            if not isinstance(detection, dict) or not isinstance(detection.get("label"), str):
                raise ValueError(f"{fixture}: every detection must contain a string label")
            labels.add(_raw_label(detection["label"]))
        cases[fixture] = {
            "ingredients": sorted(labels),
            # The public fixture set has no calibrated weights, and raw FPB
            # regression output must not be relabelled as a verified mass.
            "ingredient_masses_g": {},
        }
    return {
        "schema_version": "g3-predictions/research-v1",
        "source": "fpb-yolov12-foodweight",
        "taxonomy_status": "unmapped-raw-fpb-labels",
        "research_only": True,
        "cases": cases,
    }


def _install_fpb_weight_head_compatibility() -> Any:
    """Adapt current Ultralytics to FPB's extra raw grams channel.

    The function is intentionally process-local. It does not patch an installed
    package and it is not an app/runtime implementation.
    """
    import torch
    from ultralytics.nn.modules.head import Detect

    def forward_head(
        self: Any,
        features: list[Any],
        box_head: Any = None,
        cls_head: Any = None,
    ) -> dict[str, Any]:
        if box_head is None or cls_head is None:
            return {}
        batch = features[0].shape[0]
        box_and_weight = torch.cat(
            [box_head[index](features[index]).view(batch, 4 * self.reg_max + 1, -1) for index in range(self.nl)],
            dim=-1,
        )
        scores = torch.cat(
            [cls_head[index](features[index]).view(batch, self.nc, -1) for index in range(self.nl)],
            dim=-1,
        )
        return {
            "boxes": box_and_weight[:, : 4 * self.reg_max],
            "weights": box_and_weight[:, 4 * self.reg_max : 4 * self.reg_max + 1],
            "scores": scores,
            "feats": features,
        }

    def inference(self: Any, predictions: dict[str, Any]) -> Any:
        decoded_boxes = self._get_decode_boxes(predictions)
        return torch.cat((decoded_boxes, predictions["weights"], predictions["scores"].sigmoid()), dim=1)

    Detect.forward_head = forward_head
    Detect._inference = inference
    return torch


def _predict_one(
    image_path: Path,
    model: Any,
    torch: Any,
    confidence_threshold: float,
    nms_iou: float,
    input_size: int,
) -> dict[str, Any]:
    import cv2
    from torchvision.ops import nms

    image = cv2.imread(str(image_path))
    if image is None:
        raise ValueError(f"Cannot read image: {image_path}")
    height, width = image.shape[:2]
    scale = min(input_size / height, input_size / width)
    resized = cv2.resize(image, (round(width * scale), round(height * scale)))
    remaining_width = input_size - resized.shape[1]
    remaining_height = input_size - resized.shape[0]
    left = remaining_width // 2
    top = remaining_height // 2
    padded = cv2.copyMakeBorder(
        resized,
        top,
        remaining_height - top,
        left,
        remaining_width - left,
        cv2.BORDER_CONSTANT,
        value=(114, 114, 114),
    )
    tensor = torch.from_numpy(padded[:, :, ::-1].copy()).permute(2, 0, 1).unsqueeze(0).float() / 255
    started = time.perf_counter()
    with torch.inference_mode():
        output = model.model(tensor)
    elapsed_ms = (time.perf_counter() - started) * 1000
    prediction = output[0] if isinstance(output, tuple) else output
    if prediction.ndim != 3 or prediction.shape[1] != model.model.model[-1].nc + 5:
        raise ValueError("FPB compatibility adapter did not produce 4 box coordinates, one raw weight, and class scores")

    xywh = prediction[0, :4].transpose(0, 1)
    weights = prediction[0, 4].cpu()
    scores, class_ids = prediction[0, 5:].transpose(0, 1).max(dim=1)
    selected = scores >= confidence_threshold
    xywh, weights, scores, class_ids = xywh[selected], weights[selected], scores[selected], class_ids[selected]
    boxes = torch.empty_like(xywh)
    boxes[:, 0] = xywh[:, 0] - xywh[:, 2] / 2
    boxes[:, 1] = xywh[:, 1] - xywh[:, 3] / 2
    boxes[:, 2] = xywh[:, 0] + xywh[:, 2] / 2
    boxes[:, 3] = xywh[:, 1] + xywh[:, 3] / 2
    kept = nms(boxes, scores, nms_iou)
    detections: list[dict[str, Any]] = []
    for index in kept.tolist():
        box = boxes[index].clone()
        box[0::2].sub_(left).div_(scale).clamp_(0, width)
        box[1::2].sub_(top).div_(scale).clamp_(0, height)
        raw_weight = float(weights[index])
        if not math.isfinite(raw_weight):
            raise ValueError(f"Non-finite raw FPB weight for {image_path.name}")
        detections.append(
            {
                "label": str(model.names[int(class_ids[index])]),
                "class_id": int(class_ids[index]),
                "confidence": round(float(scores[index]), 6),
                "weight_grams_raw": round(raw_weight, 6),
                "box_xyxy": [round(float(value), 2) for value in box],
            }
        )
    return {"inference_ms_cpu": round(elapsed_ms, 3), "detections": detections}


def run(
    checkpoint: Path,
    fixture_root: Path,
    confidence_threshold: float,
    nms_iou: float,
    input_size: int,
) -> tuple[dict[str, Any], dict[str, Any]]:
    if not checkpoint.is_file():
        raise ValueError(f"Missing checkpoint: {checkpoint}")
    if not fixture_root.is_dir():
        raise ValueError(f"Missing fixture directory: {fixture_root}")
    if not 0 < confidence_threshold <= 1 or not 0 < nms_iou <= 1:
        raise ValueError("confidence threshold and NMS IoU must be in (0, 1]")

    torch = _install_fpb_weight_head_compatibility()
    from ultralytics import YOLO, __version__ as ultralytics_version

    model = YOLO(str(checkpoint))
    model.model.eval()
    raw_cases = {
        fixture: _predict_one(fixture_root / filename, model, torch, confidence_threshold, nms_iou, input_size)
        for fixture, filename in FIXTURES.items()
    }
    evidence = {
        "schema_version": "supernutrition.fpb-yolov12-foodweight-composite-evidence.v1",
        "status": "experimental-research-only",
        "research_only": True,
        "run": {
            "checkpoint_sha256": _sha256(checkpoint),
            "input_size": input_size,
            "confidence_threshold": confidence_threshold,
            "nms_iou": nms_iou,
            "runtime": f"PyTorch {torch.__version__}; Ultralytics {ultralytics_version}; {platform.platform()}",
            "compatibility_caveat": "Current Ultralytics required a process-local extra-grams-channel adapter; it is not a production runtime.",
        },
        "mass_quality": {
            "status": "not-evaluable",
            "reason": "The five public composite fixtures have no scale-derived component or total mass.",
        },
        "cases": raw_cases,
    }
    return evidence, build_g3_predictions(raw_cases)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--fixture-root", type=Path, required=True)
    parser.add_argument("--evidence-output", type=Path, required=True)
    parser.add_argument("--predictions-output", type=Path, required=True)
    parser.add_argument("--confidence-threshold", type=float, default=0.25)
    parser.add_argument("--nms-iou", type=float, default=0.7)
    parser.add_argument("--input-size", type=int, default=640)
    args = parser.parse_args()
    evidence, predictions = run(
        checkpoint=args.checkpoint,
        fixture_root=args.fixture_root,
        confidence_threshold=args.confidence_threshold,
        nms_iou=args.nms_iou,
        input_size=args.input_size,
    )
    args.evidence_output.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    args.predictions_output.write_text(json.dumps(predictions, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
