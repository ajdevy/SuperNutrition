#!/usr/bin/env python3
"""Generate the research-report markdown from outline, fields, and JSON results.

This project uses the research skill's `categories` schema rather than the
older `field_categories` name, so the converter accepts both forms.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parent
OUTLINE_PATH = ROOT / "outline.yaml"
FIELDS_PATH = ROOT / "fields.yaml"
RESULTS_DIR = ROOT / "results"
REPORT_PATH = ROOT.parent / "docs" / "food-photo-recognition-report.md"


def slugify(value: str) -> str:
    value = re.sub(r"[^A-Za-z0-9]+", "_", value).strip("_")
    return value


def is_uncertain(value, field_name: str, uncertain_fields: set[str]) -> bool:
    if field_name in uncertain_fields:
        return True
    if isinstance(value, str) and "[uncertain]" in value:
        return True
    return value is None or value == ""


def format_value(value) -> str:
    if isinstance(value, list):
        if not value:
            return ""
        return "<br>".join(format_value(item) for item in value)
    if isinstance(value, dict):
        return "<br>".join(f"{key}: {format_value(item)}" for key, item in value.items())
    text = str(value).strip()
    if len(text) > 100:
        return text.replace(". ", ".<br><br>")
    return text


def load_data():
    outline = yaml.safe_load(OUTLINE_PATH.read_text(encoding="utf-8"))
    fields_data = yaml.safe_load(FIELDS_PATH.read_text(encoding="utf-8"))
    categories = fields_data.get("field_categories") or fields_data.get("categories") or {}
    field_defs: list[tuple[str, str]] = []
    if isinstance(categories, dict):
        for category, fields in categories.items():
            for field in fields:
                field_defs.append((category, field["name"]))
    else:
        for category in categories:
            category_name = category.get("category", "Other")
            for field in category.get("fields", []):
                field_defs.append((category_name, field["name"]))
    results = {}
    for path in sorted(RESULTS_DIR.glob("*.json")):
        try:
            results[path.stem] = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise SystemExit(f"Invalid JSON in {path}: {exc}") from exc
    return outline, field_defs, results


def main() -> None:
    outline, field_defs, results = load_data()
    field_names = {name for _, name in field_defs}
    executive_summary = """## Executive conclusion

The public evidence points to a dominant pattern: photo nutrition apps use a cloud or hybrid recognizer to propose foods, then map the accepted candidates to a proprietary food database and require user correction. A generic local LLM is useful for candidate labels and explanations, but no reviewed local model is a validated single-photo calorie, gram, or hidden-ingredient estimator.

### App/provider matrix

| App | Public provider signal | Local/cloud signal | Confidence |
| --- | --- | --- | --- |
| MyFitnessPal Meal Scan | MFP says models are built/run by MFP; Passio claims the partnership behind Meal Scan | Current APK has no obvious food model; Passio documents historical local mode and current remote photo mode | Partnership high; current path unresolved |
| YAZIO AI | Current privacy policy names OpenAI Ireland for YAZIO AI functions | Photo workflow and policy signal cloud processing; no obvious food model in current XAPK | OpenAI for YAZIO AI high; exact photo route unresolved |
| Lose It Snap It | Current provider not disclosed; Google documents historical custom Snap It model with ML Kit/TFLite | Historical implementation ran quantized model on-device/offline and could update from Firebase | Historical on-device high; current architecture unknown |
| Foodvisor | No current named food-recognition vendor found; early People For AI contribution is historical | Current XAPK exposes api.foodvisor.io and no obvious food model; likely server boundary is an inference, not proof | Current vendor unresolved |
| Cal AI | Privacy notice says photos go to its API/service providers; vendor not named | Strong server-processing signal; no obvious food model in current XAPK | Cloud/API signal high; vendor unknown |
| HealthifyMe | Policy names third-party AI processing category but not a vendor | Policy describes local food/non-food filtering followed by server processing, with some contradictory wording | Hybrid signal high; exact implementation/vendor unknown |

Sources: [MFP FAQ](https://support.myfitnesspal.com/hc/en-us/articles/360045761612-Meal-Scan-FAQ), [Passio MFP guide](https://www.passio.ai/blog/essential-guide-to-myfitnesspal-meal-scan), [YAZIO privacy](https://www.yazio.com/en/privacy), [Google Lose It case study](https://developers.google.com/ml-kit/case-studies/lose-it), [HealthifyMe policy](https://api.healthifyme.com/terms-of-use/), [Cal AI privacy](https://www.calai.app/privacy).

### Cost snapshot (public list prices, checked 2026-07-26)

| Option | Public price signal | Derived photo economics |
| --- | --- | --- |
| Passio Nutrition-AI Hub | $99/$599/$2,999 monthly tiers; $25/$10/$2.50 per 1M refill by tier | Vendor says 20k-30k tokens/photo: about $0.05-$0.075 at Pro refill, $0.20-$0.30 Growth, $0.50-$0.75 Starter; fixed fees/included credits change effective cost |
| LogMeal | Credit-based; one recognition+portion call = 1 credit; nutrition/recipe data add no credit; public trial 30 days/200 queries/5 users | Exact public plan table was not visible without account context |
| Gemini 3.1 Flash-Lite | $0.25/M input image tokens, $1.50/M output | At 1,120 image tokens + 300 output: approximately $0.00073/photo before infrastructure |
| OpenAI GPT-5.4 mini | $0.75/M input, $4.50/M output; image input billed as image tokens | Cost depends on image tokenization/detail and output length; not a YAZIO-specific price |
| AWS Rekognition DetectLabels | Starts at $0.001/image for first 1M, generic labels | Not food/nutrition-specific; needs custom model/database work |

Sources: [Passio pricing](https://www.passio.ai/pricing), [LogMeal pricing](https://logmeal.com/api/pricing/), [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing), [OpenAI GPT-5.4 mini](https://developers.openai.com/api/docs/models/gpt-5.4-mini), [AWS Rekognition pricing](https://aws.amazon.com/rekognition/pricing/).

### Local-model shortlist

- iOS: Core ML/Vision for a compact detector/classifier/segmenter; Apple Foundation Models for an optional semantic fallback on Apple Intelligence hardware.
- Android: LiteRT/MediaPipe for dedicated vision; Gemini Nano via ML Kit on supported devices; Gemma 3n E2B via LiteRT-LM for a high-end open-weight tier.
- Cross-platform research baseline: SmolVLM-256M/500M through ONNX Runtime Mobile; use a custom food classifier for broad device coverage.
- Qwen2.5-VL-3B and current Moondream 3.x are research/high-end candidates, not the first low-risk commercial mobile choice; licensing and memory are material constraints.

Sources: [Gemma 3n](https://ai.google.dev/gemma/docs/gemma-3n), [Apple image prompting](https://developer.apple.com/documentation/FoundationModels/analyzing-images-with-multimodal-prompting), [Android Gemini Nano](https://developer.android.com/ai/gemini-nano), [SmolVLM](https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct), [ONNX Runtime Mobile](https://onnxruntime.ai/docs/tutorials/mobile/).

### Decision for SuperNutrition

Start with a hybrid MVP: local quality gate + compact food detector/classifier, food-specific cloud fallback for ambiguous/recipe-level cases, deterministic nutrition database lookup, visible assumptions/ranges, and mandatory user confirmation for servings, brand/recipe, oil/sauce, and allergen-relevant ingredients. Select Passio vs LogMeal vs a generic VLM only after measuring corrected-meal accuracy, grams/calorie error, latency, privacy, and cost per accepted meal on a weighed, culturally diverse phone-photo set.

""".strip().splitlines()
    lines = [
        f"# {outline['topic']}",
        "",
        "> Generated from the research outline and validated JSON evidence. Uncertain fields are intentionally omitted from the generated field summaries; see the confidence/limits section at the end.",
        "",
        f"Research date: {outline.get('time_range', 'current')}",
        "",
        *executive_summary,
        "",
        "## Contents",
        "",
    ]
    item_paths = {}
    for index, item in enumerate(outline.get("items", []), start=1):
        stem = slugify(item["name"])
        item_paths[item["name"]] = results.get(stem, {})
        data = item_paths[item["name"]]
        summary = data.get("supported_platforms") or data.get("pricing") or "Evidence summary in detailed section"
        summary = format_value(summary).replace("<br>", " ")
        if len(summary) > 180:
            summary = summary[:177].rstrip() + "..."
        lines.append(f"{index}. [{item['name']}](#{slugify(item['name']).lower()}) — {summary}")
    lines.extend(["", "## Detailed findings", ""])

    for index, item in enumerate(outline.get("items", []), start=1):
        name = item["name"]
        data = item_paths[name]
        lines.extend([f"### {index}. {name}", "", f"*{item.get('description', '').strip()}*", ""])
        uncertain_fields = set(data.get("uncertain", [])) if isinstance(data, dict) else set()
        if not data:
            lines.extend(["No validated result file was available for this item.", ""])
            continue
        for category, field_name in field_defs:
            if field_name not in data or is_uncertain(data[field_name], field_name, uncertain_fields):
                continue
            lines.extend([f"#### {category}: {field_name}", "", format_value(data[field_name]), ""])
        extra = [key for key in data if key not in field_names and key not in {"uncertain", "_source_file"}]
        if extra:
            lines.extend(["#### Other info", ""])
            for key in extra:
                if not is_uncertain(data[key], key, uncertain_fields):
                    lines.extend([f"- **{key}:** {format_value(data[key])}"])
            lines.append("")

    lines.extend([
        "## Confidence and limits",
        "",
        "The JSON result files retain an `uncertain` array for fields that are not directly disclosed or are derived from public list prices. The generated field summaries omit those values by design. In particular, a missing model file in an APK does not prove cloud-only inference; a vendor's public integration claim does not prove the current app build uses that exact path; and public API list prices do not reveal private app contracts.",
        "",
        "Static analysis was limited to publicly obtainable Android artifacts. No application was installed or executed, no paid account was used, and no DRM/authentication bypass or network interception was attempted.",
        "",
    ])
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {REPORT_PATH}")


if __name__ == "__main__":
    main()
