#!/usr/bin/env python3
"""Build the static coverage and source-line dashboard for GitHub Pages."""

from __future__ import annotations

import argparse
import html
import json
import shutil
import subprocess
import sys
import tomllib
import xml.etree.ElementTree as ET
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path


SOURCE_LANGUAGES = {
    ".css": "CSS",
    ".gradle": "Gradle",
    ".html": "HTML",
    ".java": "Java",
    ".js": "JavaScript",
    ".json": "JSON",
    ".jsx": "JavaScript",
    ".kt": "Kotlin",
    ".kts": "Kotlin",
    ".m": "Objective-C",
    ".mm": "Objective-C++",
    ".py": "Python",
    ".sh": "Shell",
    ".swift": "Swift",
    ".ts": "TypeScript",
    ".tsx": "TypeScript",
    ".xml": "XML",
}

EXCLUDED_PARTS = {
    ".agents",
    ".git",
    "_bmad",
    "_bmad-output",
    "build",
    "docs",
    "food-photo-recognition-research",
    "generated",
    "graphify-out",
    "vendor",
}

COMMENT_PREFIXES = {
    "CSS": ("/*", "*", "*/"),
    "Gradle": ("//", "/*", "*", "*/"),
    "HTML": ("<!--", "-->",),
    "Java": ("//", "/*", "*", "*/"),
    "JavaScript": ("//", "/*", "*", "*/"),
    "JSON": (),
    "Kotlin": ("//", "/*", "*", "*/"),
    "Objective-C": ("//", "/*", "*", "*/"),
    "Objective-C++": ("//", "/*", "*", "*/"),
    "Python": ("#",),
    "Shell": ("#",),
    "Swift": ("//", "/*", "*", "*/"),
    "TypeScript": ("//", "/*", "*", "*/"),
    "XML": ("<!--", "-->",),
}


class CoverageSiteError(RuntimeError):
    """Raised when a report needed for the public dashboard is missing."""


@dataclass(frozen=True)
class LineStats:
    files: int
    lines: int
    code: int
    comments: int
    blanks: int


def language_for(path: Path) -> str | None:
    return SOURCE_LANGUAGES.get(path.suffix.lower())


def is_excluded(path: Path) -> bool:
    return bool(EXCLUDED_PARTS.intersection(path.parts))


def count_text(text: str, language: str) -> LineStats:
    lines = code = comments = blanks = 0
    prefixes = COMMENT_PREFIXES[language]
    for line in text.splitlines():
        lines += 1
        stripped = line.strip()
        if not stripped:
            blanks += 1
        elif prefixes and stripped.startswith(prefixes):
            comments += 1
        else:
            code += 1
    return LineStats(1, lines, code, comments, blanks)


def tracked_source_files(repo_root: Path) -> list[Path]:
    result = subprocess.run(
        ["git", "-C", str(repo_root), "ls-files", "-z"],
        check=True,
        capture_output=True,
    )
    files: list[Path] = []
    for raw_path in result.stdout.split(b"\0"):
        if not raw_path:
            continue
        relative = Path(raw_path.decode("utf-8"))
        if is_excluded(relative):
            continue
        if language_for(relative) is not None:
            files.append(relative)
    return files


def count_loc(repo_root: Path) -> dict[str, object]:
    totals = LineStats(0, 0, 0, 0, 0)
    by_language: dict[str, LineStats] = defaultdict(lambda: LineStats(0, 0, 0, 0, 0))
    for relative in tracked_source_files(repo_root):
        language = language_for(relative)
        assert language is not None
        stats = count_text((repo_root / relative).read_text(encoding="utf-8"), language)
        totals = LineStats(
            totals.files + stats.files,
            totals.lines + stats.lines,
            totals.code + stats.code,
            totals.comments + stats.comments,
            totals.blanks + stats.blanks,
        )
        previous = by_language[language]
        by_language[language] = LineStats(
            previous.files + stats.files,
            previous.lines + stats.lines,
            previous.code + stats.code,
            previous.comments + stats.comments,
            previous.blanks + stats.blanks,
        )
    return {
        "definition": (
            "Tracked source files, excluding vendor/docs/generated paths; code is a non-blank "
            "line that is not classified as a full-line comment."
        ),
        "files": totals.files,
        "lines": totals.lines,
        "code_lines": totals.code,
        "comment_lines": totals.comments,
        "blank_lines": totals.blanks,
        "by_language": {
            language: {
                "files": stats.files,
                "lines": stats.lines,
                "code_lines": stats.code,
                "comment_lines": stats.comments,
                "blank_lines": stats.blanks,
            }
            for language, stats in sorted(by_language.items())
        },
    }


def counter(report: ET.Element, counter_type: str, project: str) -> tuple[int, int] | None:
    matches = [
        element
        for element in report.findall("./counter")
        if element.get("type") == counter_type
    ]
    if len(matches) != 1:
        raise CoverageSiteError(
            f"{project}: expected one report-level {counter_type} counter, found {len(matches)}"
        )
    element = matches[0]
    missed = int(element.get("missed", "-1"))
    covered = int(element.get("covered", "-1"))
    if missed < 0 or covered < 0:
        raise CoverageSiteError(f"{project}: empty or invalid {counter_type} denominator")
    if missed + covered == 0:
        return None
    return missed, covered


def percent(missed: int, covered: int) -> float:
    return covered * 100.0 / (missed + covered)


def report_for(project_root: Path, project: str, coverage_kind: str) -> Path:
    names = ("reportJvm.xml", "reportAndroid.xml")
    if coverage_kind == "native-equivalent":
        names = ("reportAndroid.xml", "reportJvm.xml")
    for name in names:
        candidate = project_root / "build" / "reports" / "kover" / name
        if candidate.is_file():
            return candidate
    raise CoverageSiteError(f"{project}: no Kover XML report found")


def html_report_for(xml_report: Path, project: str) -> Path:
    report_root = xml_report.parent
    suffix = xml_report.stem.removeprefix("report")
    candidates = [report_root / f"html{suffix}", report_root / "html"]
    for candidate in candidates:
        if (candidate / "index.html").is_file():
            return candidate
    raise CoverageSiteError(f"{project}: no Kover HTML report found beside {xml_report}")


def coverage_data(repo_root: Path, output_dir: Path) -> dict[str, object]:
    inventory_path = repo_root / "mobile" / "config" / "quality" / "coverage.toml"
    inventory = tomllib.loads(inventory_path.read_text(encoding="utf-8"))
    rows: list[dict[str, object]] = []
    host_totals = [0, 0, 0, 0]
    host_line_empty = False
    host_branch_empty = False
    for denominator in inventory.get("denominator", []):
        project = str(denominator["id"])
        source_path = str(denominator["source_path"])
        coverage_kind = str(denominator["coverage_kind"])
        xml_report = report_for(repo_root / source_path, project, coverage_kind)
        report = ET.parse(xml_report).getroot()
        line_counts = counter(report, "LINE", project)
        branch_counts = counter(report, "BRANCH", project)
        line_missed, line_covered = line_counts or (0, 0)
        branch_missed, branch_covered = branch_counts or (0, 0)
        if coverage_kind == "jvm":
            if line_counts is None:
                host_line_empty = True
            else:
                host_totals[0] += line_missed
                host_totals[1] += line_covered
            if branch_counts is None:
                host_branch_empty = True
            else:
                host_totals[2] += branch_missed
                host_totals[3] += branch_covered
        html_report = html_report_for(xml_report, project)
        report_destination = output_dir / "reports" / project
        shutil.copytree(html_report, report_destination, dirs_exist_ok=True)
        rows.append(
            {
                "id": project,
                "source_path": source_path,
                "coverage_kind": coverage_kind,
                "report_variant": xml_report.stem.removeprefix("report"),
                "line_percent": (
                    round(percent(line_missed, line_covered), 2)
                    if line_counts is not None
                    else None
                ),
                "branch_percent": (
                    round(percent(branch_missed, branch_covered), 2)
                    if branch_counts is not None
                    else None
                ),
                "html": f"reports/{project}/index.html",
            }
        )
    return {
        "definition": "Kover report-level counters from the committed mobile coverage inventory.",
        "host_jvm": {
            "line_percent": (
                round(percent(host_totals[0], host_totals[1]), 2)
                if not host_line_empty and host_totals[0] + host_totals[1] > 0
                else None
            ),
            "branch_percent": (
                round(percent(host_totals[2], host_totals[3]), 2)
                if not host_branch_empty and host_totals[2] + host_totals[3] > 0
                else None
            ),
            "line_covered": host_totals[1],
            "line_missed": host_totals[0],
            "branch_covered": host_totals[3],
            "branch_missed": host_totals[2],
        },
        "reports": rows,
    }


def git_commit(repo_root: Path) -> str:
    return subprocess.run(
        ["git", "-C", str(repo_root), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def write_site(repo_root: Path, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    loc = count_loc(repo_root)
    coverage = coverage_data(repo_root, output_dir)
    metadata = {
        "commit": git_commit(repo_root),
        "generated_at": datetime.now(timezone.utc).isoformat(),
    }
    (output_dir / "loc.json").write_text(json.dumps(loc, indent=2) + "\n", encoding="utf-8")
    (output_dir / "coverage.json").write_text(
        json.dumps({**metadata, **coverage}, indent=2) + "\n",
        encoding="utf-8",
    )
    (output_dir / "index.html").write_text(
        render_index(metadata, loc, coverage),
        encoding="utf-8",
    )


def format_number(value: int | float) -> str:
    return f"{value:,.2f}" if isinstance(value, float) else f"{value:,}"


def format_percent(value: object) -> str:
    return "n/a" if value is None else f"{float(value):.2f}%"


def render_index(
    metadata: dict[str, str],
    loc: dict[str, object],
    coverage: dict[str, object],
) -> str:
    host = coverage["host_jvm"]
    rows = coverage["reports"]
    table_rows = "\n".join(
        "<tr>"
        f"<td><code>{html.escape(str(row['id']))}</code></td>"
        f"<td>{html.escape(str(row['coverage_kind']))}</td>"
        f"<td>{format_percent(row['line_percent'])}</td>"
        f"<td>{format_percent(row['branch_percent'])}</td>"
        f"<td><a href=\"{html.escape(str(row['html']))}\">Open HTML report</a></td>"
        "</tr>"
        for row in rows
    )
    language_rows = "\n".join(
        "<tr>"
        f"<td>{html.escape(language)}</td>"
        f"<td>{format_number(int(stats['files']))}</td>"
        f"<td>{format_number(int(stats['code_lines']))}</td>"
        "</tr>"
        for language, stats in loc["by_language"].items()
    )
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>SuperNutrition quality dashboard</title>
  <style>
    :root {{ color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, sans-serif; color: #172033; background: #f5f7fb; }}
    body {{ margin: 0; }}
    main {{ max-width: 1080px; margin: 0 auto; padding: 48px 24px 72px; }}
    .eyebrow {{ color: #5263d8; font-size: .8rem; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }}
    h1 {{ margin: 8px 0; font-size: clamp(2rem, 5vw, 3.4rem); }}
    h2 {{ margin-top: 42px; }}
    .muted {{ color: #596579; }}
    .cards {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 16px; margin: 32px 0; }}
    .card {{ background: white; border: 1px solid #e0e5ef; border-radius: 16px; padding: 20px; box-shadow: 0 5px 20px #2936570d; }}
    .card strong {{ display: block; font-size: 1.8rem; margin-top: 8px; }}
    table {{ width: 100%; border-collapse: collapse; background: white; border: 1px solid #e0e5ef; border-radius: 12px; overflow: hidden; }}
    th, td {{ padding: 12px 14px; border-bottom: 1px solid #e8ebf2; text-align: left; }}
    th {{ background: #eef1fb; font-size: .82rem; }}
    tr:last-child td {{ border-bottom: 0; }}
    a {{ color: #4354c7; font-weight: 650; }}
    code {{ font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }}
    .note {{ border-left: 4px solid #8692eb; background: #edf0ff; padding: 14px 16px; margin-top: 16px; }}
  </style>
</head>
<body>
<main>
  <div class="eyebrow">SuperNutrition / quality dashboard</div>
  <h1>Coverage and code size</h1>
  <p class="muted">Commit <code>{html.escape(metadata['commit'])}</code> · generated {html.escape(metadata['generated_at'])}</p>
  <section class="cards" aria-label="Quality summary">
    <div class="card"><span class="muted">JVM host line coverage</span><strong>{format_percent(host['line_percent'])}</strong></div>
    <div class="card"><span class="muted">JVM host branch coverage</span><strong>{format_percent(host['branch_percent'])}</strong></div>
    <div class="card"><span class="muted">Code lines</span><strong>{format_number(int(loc['code_lines']))}</strong><span class="muted">{format_number(int(loc['files']))} tracked source files</span></div>
  </section>
  <p class="note"><strong>Scope:</strong> the headline coverage is the weighted JVM host denominator from the committed inventory. Native-equivalent rows are shown as separate Kover observations and do not imply physical-device or product-wide native coverage. <code>n/a</code> means the report has an empty denominator; the quality gate remains fail-closed for that case.</p>
  <h2>Module coverage</h2>
  <table>
    <thead><tr><th>Module</th><th>Kind</th><th>Line</th><th>Branch</th><th>Report</th></tr></thead>
    <tbody>{table_rows}</tbody>
  </table>
  <h2>Code lines by language</h2>
  <p class="muted">{html.escape(str(loc['definition']))}</p>
  <table>
    <thead><tr><th>Language</th><th>Files</th><th>Code lines</th></tr></thead>
    <tbody>{language_rows}</tbody>
  </table>
</main>
</body>
</html>
"""


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        write_site(args.repo_root.resolve(), args.output_dir.resolve())
    except (CoverageSiteError, OSError, ET.ParseError, ValueError, subprocess.CalledProcessError) as error:
        print(f"coverage-site: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
