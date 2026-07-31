#!/usr/bin/env python3
"""Synchronize the generated coverage and source-line snapshot into README.md."""

from __future__ import annotations

import argparse
import difflib
import json
import sys
from pathlib import Path
from urllib.parse import quote


START_MARKER = "<!-- coverage-report:start -->"
END_MARKER = "<!-- coverage-report:end -->"
REPOSITORY = "flexsent-labs/SuperNutrition"
WORKFLOWS = {
    "dashboard": "coverage-readme.yml",
    "jvm": "ci-required.yml",
    "android": "android-ui-coverage.yml",
    "ios": "ios-ui-coverage.yml",
}


class ReadmeMetricsError(ValueError):
    """Raised when the README does not contain one replaceable metrics section."""


def format_number(value: object) -> str:
    return f"{int(value):,}"


def format_percent(value: object) -> str:
    return "n/a" if value is None else f"{float(value):.2f}%"


def coverage_badge_color(value: object) -> str:
    if value is None:
        return "lightgrey"
    percent = float(value)
    if percent >= 85:
        return "brightgreen"
    if percent >= 70:
        return "orange"
    return "red"


def workflow_url(workflow: str) -> str:
    return f"https://github.com/{REPOSITORY}/actions/workflows/{workflow}"


def workflow_badge(label: str, workflow: str) -> str:
    badge_url = f"{workflow_url(workflow)}/badge.svg?branch=main"
    return f"[![{label}]({badge_url})]({workflow_url(workflow)})"


def metric_badge(label: str, value: str, color: str) -> str:
    encoded_label = quote(label, safe="")
    encoded_value = quote(value, safe="")
    image_url = (
        f"https://img.shields.io/badge/{encoded_label}-{encoded_value}-{color}"
        "?style=flat-square"
    )
    return f"[![{label}]({image_url})]({workflow_url(WORKFLOWS['dashboard'])})"


def load_metrics(site_dir: Path) -> tuple[dict[str, object], dict[str, object]]:
    loc_path = site_dir / "loc.json"
    coverage_path = site_dir / "coverage.json"
    loc = json.loads(loc_path.read_text(encoding="utf-8"))
    coverage = json.loads(coverage_path.read_text(encoding="utf-8"))
    return loc, coverage


def render_section(loc: dict[str, object], coverage: dict[str, object]) -> str:
    host = coverage["host_jvm"]
    rows = coverage["reports"]
    module_rows = "\n".join(
        f"| `{row['id']}` | {row['coverage_kind']} | "
        f"{format_percent(row['line_percent'])} | {format_percent(row['branch_percent'])} |"
        for row in rows
    )
    workflow_badges = " ".join(
        [
            workflow_badge("Coverage dashboard", WORKFLOWS["dashboard"]),
            workflow_badge("JVM unit coverage", WORKFLOWS["jvm"]),
            workflow_badge("Android UI coverage", WORKFLOWS["android"]),
            workflow_badge("iOS UI coverage", WORKFLOWS["ios"]),
        ]
    )
    metric_badges = " ".join(
        [
            metric_badge(
                "JVM line",
                format_percent(host["line_percent"]),
                coverage_badge_color(host["line_percent"]),
            ),
            metric_badge(
                "JVM branch",
                format_percent(host["branch_percent"]),
                coverage_badge_color(host["branch_percent"]),
            ),
            metric_badge("Source files", format_number(loc["files"]), "5865F2"),
            metric_badge("Code lines", format_number(loc["code_lines"]), "5865F2"),
        ]
    )
    return "\n".join(
        [
            START_MARKER,
            "## Quality snapshot",
            "",
            workflow_badges,
            "",
            metric_badges,
            "",
            "| Metric | Value |",
            "|---|---:|",
            f"| JVM host line coverage | {format_percent(host['line_percent'])} |",
            f"| JVM host branch coverage | {format_percent(host['branch_percent'])} |",
            f"| Tracked source files | {format_number(loc['files'])} |",
            f"| Code lines | {format_number(loc['code_lines'])} |",
            "",
            "### Coverage by denominator",
            "",
            "| Module | Kind | Line | Branch |",
            "|---|---|---:|---:|",
            module_rows,
            "",
            "### Report links",
            "",
            "| Report | Workflow and artifacts |",
            "|---|---|",
            f"| Coverage dashboard | [Open workflow runs and artifacts]({workflow_url(WORKFLOWS['dashboard'])}) |",
            f"| JVM unit coverage | [Open workflow runs and artifacts]({workflow_url(WORKFLOWS['jvm'])}) |",
            f"| Android UI coverage | [Open workflow runs and artifacts]({workflow_url(WORKFLOWS['android'])}) |",
            f"| iOS UI coverage | [Open workflow runs and artifacts]({workflow_url(WORKFLOWS['ios'])}) |",
            "",
            "Generated from the committed coverage inventory by "
            "[`scripts/build_coverage_site.py`](scripts/build_coverage_site.py). "
            "`n/a` means that an optional report is pending or has an empty "
            "denominator; the quality gate remains fail-closed for an empty "
            "denominator.",
            END_MARKER,
        ]
    )


def replace_section(readme: str, section: str) -> str:
    if readme.count(START_MARKER) != 1 or readme.count(END_MARKER) != 1:
        raise ReadmeMetricsError(
            "README must contain exactly one coverage-report:start/end marker pair"
        )
    start = readme.index(START_MARKER)
    end = readme.index(END_MARKER, start) + len(END_MARKER)
    return readme[:start] + section + readme[end:]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--readme", type=Path, default=Path("README.md"))
    parser.add_argument("--site-dir", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    try:
        readme = args.readme.read_text(encoding="utf-8")
        loc, coverage = load_metrics(args.site_dir)
        expected = replace_section(readme, render_section(loc, coverage))
        if expected == readme:
            return 0
        if args.check:
            diff = difflib.unified_diff(
                readme.splitlines(keepends=True),
                expected.splitlines(keepends=True),
                fromfile=str(args.readme),
                tofile="generated README",
            )
            print("README coverage snapshot is stale; update it with:", file=sys.stderr)
            print(
                "python3 scripts/update_readme_metrics.py --site-dir _site",
                file=sys.stderr,
            )
            print("".join(diff), file=sys.stderr, end="")
            return 1
        args.readme.write_text(expected, encoding="utf-8")
        return 0
    except (OSError, json.JSONDecodeError, KeyError, ReadmeMetricsError) as error:
        print(f"readme-metrics: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
