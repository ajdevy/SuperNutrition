#!/usr/bin/env python3
"""Render a readable Markdown report from a research outline and result JSON files."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

import yaml


def load_yaml(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        value = yaml.safe_load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a YAML mapping")
    return value


def load_results(directory: Path) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for path in sorted(directory.glob("*.json")):
        with path.open(encoding="utf-8") as handle:
            item = json.load(handle)
        if not isinstance(item, dict):
            raise ValueError(f"{path} must contain a JSON object")
        item["_source_file"] = path.name
        results.append(item)
    if not results:
        raise ValueError(f"No JSON findings found in {directory}")
    return results


def field_definitions(fields: dict[str, Any]) -> list[tuple[str, str, str]]:
    definitions: list[tuple[str, str, str]] = []
    for category in fields.get("field_categories", []):
        category_name = str(category.get("category", "other")).replace("_", " ").title()
        for field in category.get("fields", []):
            definitions.append(
                (
                    category_name,
                    str(field.get("name", "")),
                    str(field.get("description", "")),
                )
            )
    return definitions


def clean_text(value: Any, limit: int | None = None) -> str:
    if isinstance(value, str):
        text = value
    elif isinstance(value, list):
        text = "; ".join(clean_text(item) for item in value[:3])
    elif isinstance(value, dict):
        text = "; ".join(
            f"{key.replace('_', ' ')}: {clean_text(item)}" for key, item in list(value.items())[:3]
        )
    else:
        text = str(value)
    text = re.sub(r"\s+", " ", text).strip()
    text = re.sub(r"\|", "/", text)
    if limit and len(text) > limit:
        return text[: limit - 1].rstrip() + "…"
    return text or "—"


def has_uncertain_marker(value: Any, uncertain: set[str]) -> bool:
    if isinstance(value, str):
        return "[uncertain]" in value.lower()
    if isinstance(value, list):
        return any(has_uncertain_marker(item, uncertain) for item in value)
    if isinstance(value, dict):
        return any(
            str(key) in uncertain or has_uncertain_marker(item, uncertain)
            for key, item in value.items()
        )
    return False


def markdown_value(value: Any, indent: int = 0) -> str:
    prefix = "  " * indent
    if isinstance(value, str):
        return value.strip() or "—"
    if isinstance(value, list):
        if not value:
            return "—"
        parts: list[str] = []
        for item in value:
            if isinstance(item, (dict, list)):
                parts.append(f"{prefix}- {clean_text(item)}")
            else:
                parts.append(f"{prefix}- {markdown_value(item, indent + 1)}")
        return "\n".join(parts)
    if isinstance(value, dict):
        if not value:
            return "—"
        parts = []
        for key, item in value.items():
            label = str(key).replace("_", " ").title()
            if isinstance(item, (dict, list)):
                parts.append(f"{prefix}- **{label}:**\n{markdown_value(item, indent + 1)}")
            else:
                parts.append(f"{prefix}- **{label}:** {markdown_value(item, indent + 1)}")
        return "\n".join(parts)
    return str(value)


def recommendation_value(item: dict[str, Any]) -> Any:
    value = item.get("recommendation")
    if value is not None:
        return value
    classification = item.get("classification")
    if isinstance(classification, dict):
        return classification.get("recommended_minimal_skill_registry") or classification.get("finding")
    return "—"


def feedback_value(item: dict[str, Any]) -> Any:
    return item.get("public_user_feedback", "No direct public feedback recorded.")


def source_urls(item: dict[str, Any]) -> list[str]:
    sources = item.get("sources", [])
    urls: list[str] = []
    if isinstance(sources, list):
        for source in sources:
            if isinstance(source, dict) and source.get("url"):
                urls.append(str(source["url"]))
            elif isinstance(source, str):
                urls.extend(re.findall(r"https?://[^\s;)]+", source))
    return list(dict.fromkeys(urls))


def title_for(item: dict[str, Any], index: int, outline_names: list[str] | None = None) -> str:
    raw = item.get("item_name")
    if raw:
        return str(raw)
    classification = item.get("classification")
    if isinstance(classification, dict) and classification.get("research_item"):
        return str(classification["research_item"])
    if isinstance(classification, str) and outline_names:
        searchable = set(re.findall(r"[a-z0-9]+", json.dumps(item).lower()))
        ignore = {"and", "for", "the", "with", "use", "agent", "skill", "skills"}
        ranked = []
        for name in outline_names:
            terms = set(re.findall(r"[a-z0-9]+", name.lower())) - ignore
            ranked.append((len(terms & searchable), name))
        score, name = max(ranked, default=(0, ""))
        if score >= 2:
            return name
    return f"Research item {index}"


def render(outline: dict[str, Any], fields: dict[str, Any], results: list[dict[str, Any]]) -> str:
    uncertain = set(fields.get("uncertain", []))
    outline_names = [str(entry.get("name", "")) for entry in outline.get("items", [])]
    names = [
        title_for(item, index, outline_names) for index, item in enumerate(results, start=1)
    ]
    lines = [
        f"# Research report: {outline.get('topic', 'Research')}",
        "",
        "## Scope",
        "",
        str(outline.get("research_scope", "Not specified.")),
        "",
        "This report separates **official maintenance/adoption signals** from **direct public feedback**. "
        "Stars, installs, and forks are not treated as satisfaction ratings.",
        "",
        "## Contents",
        "",
    ]
    for index, name in enumerate(names, start=1):
        anchor = re.sub(r"[^a-z0-9 -]", "", name.lower()).replace(" ", "-")
        lines.append(f"{index}. [{name}](#{anchor})")

    lines.extend(["", "## Decision matrix", "", "| Area | Minimal recommendation | Direct public feedback / operational caveat |", "| --- | --- | --- |"])
    for index, item in enumerate(results, start=1):
        name = title_for(item, index, outline_names)
        lines.append(
            f"| {clean_text(name, 62)} | {clean_text(recommendation_value(item), 240)} | "
            f"{clean_text(feedback_value(item), 240)} |"
        )

    lines.extend(["", "## Recommended minimal stack", ""])
    lines.extend(
        [
            "1. Keep one concise, versioned **AGENTS.md** as the canonical repository guide and a tiny **CLAUDE.md** wrapper that imports it.",
            "2. Add only **android-cli** externally for Android execution; do not install all Android or Kotlin skills.",
            "3. Keep exactly two explicit repo-local skills: **ble-kmp-hil** and **mobile-release**. They load only for Bluetooth/device validation or signing/release work.",
            "4. Treat Kotlin migrations, Xcode execution MCP, Kable, fastlane, Graphify, and a GitHub connector as on-demand tools—not baseline skills.",
            "5. Use scripts and CI as the quality contract. A real-device BLE result and a non-production store delivery are evidence; a successful simulator build is not.",
        ]
    )

    definitions = field_definitions(fields)
    for index, item in enumerate(results, start=1):
        name = title_for(item, index, outline_names)
        lines.extend(["", f"## {name}", "", f"_Evidence source: `{item['_source_file']}`_", ""])
        seen = set()
        for category, field, description in definitions:
            if not field or field in seen or field not in item:
                continue
            seen.add(field)
            value = item[field]
            if has_uncertain_marker(value, uncertain):
                continue
            lines.extend([f"### {field.replace('_', ' ').title()}", ""])
            if description:
                lines.extend([f"_{description}_", ""])
            lines.extend([markdown_value(value), ""])

        extra_keys = [
            key for key in item
            if key not in seen and key not in {"_source_file", "item_name", "uncertain", "sources"}
        ]
        if extra_keys:
            lines.extend(["### Additional findings", ""])
            for key in extra_keys:
                value = item[key]
                if has_uncertain_marker(value, uncertain):
                    continue
                lines.extend([f"#### {key.replace('_', ' ').title()}", "", markdown_value(value), ""])

        urls = source_urls(item)
        if urls:
            lines.extend(["### Primary links", ""])
            lines.extend(f"- {url}" for url in urls)
            lines.append("")

    lines.extend(
        [
            "## Adoption rule",
            "",
            "Add a skill, plugin, connector, or automation only after it has removed repeated work at least twice and its "
            "scope cannot be expressed more cheaply as a script, ADR, or linked reference. Remove stale migration-only skills after the migration.",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--outline", type=Path, default=Path(__file__).with_name("outline.yaml"))
    parser.add_argument("--fields", type=Path, default=Path(__file__).with_name("fields.yaml"))
    parser.add_argument(
        "--results-dir", type=Path, default=Path(__file__).with_name("research_results")
    )
    parser.add_argument("--output", type=Path, default=Path(__file__).with_name("report.md"))
    args = parser.parse_args()

    report = render(load_yaml(args.outline), load_yaml(args.fields), load_results(args.results_dir))
    args.output.write_text(report, encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
