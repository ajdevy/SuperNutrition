#!/usr/bin/env python3
"""Fail closed when a UI test report is missing, empty, or unsuccessful."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


class UiReportError(ValueError):
    """Raised when a UI test report cannot prove a passing test run."""


def junit_summary(root: Path) -> tuple[int, int, int, int]:
    total = passed = failed = skipped = 0
    report_count = 0
    for path in sorted(root.rglob("*.xml")):
        try:
            document = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        testcases = list(document.iter("testcase"))
        if not testcases:
            continue
        report_count += 1
        for testcase in testcases:
            total += 1
            if testcase.find("failure") is not None or testcase.find("error") is not None:
                failed += 1
            elif testcase.find("skipped") is not None:
                skipped += 1
            else:
                passed += 1
    if report_count == 0:
        raise UiReportError(f"{root}: no JUnit testcases found")
    return total, passed, failed, skipped


def verify_junit(root: Path, label: str) -> None:
    total, passed, failed, skipped = junit_summary(root)
    if total == 0 or passed == 0 or failed or skipped:
        raise UiReportError(
            f"{label}: total={total} passed={passed} failed={failed} skipped={skipped}"
        )
    print(f"{label}: {passed}/{total} JUnit tests passed")


def verify_xcresult_summary(path: Path, label: str) -> None:
    summary = json.loads(path.read_text(encoding="utf-8"))
    total = int(summary.get("totalTestCount", 0))
    passed = int(summary.get("passedTests", 0))
    failed = int(summary.get("failedTests", 0))
    skipped = int(summary.get("skippedTests", 0))
    result = str(summary.get("result", ""))
    if total == 0 or passed == 0 or failed or skipped or result.lower() != "passed":
        raise UiReportError(
            f"{label}: result={result!r} total={total} passed={passed} "
            f"failed={failed} skipped={skipped}"
        )
    print(f"{label}: {passed}/{total} XCTest tests passed")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--junit-root", type=Path)
    parser.add_argument("--xcresult-summary", type=Path)
    parser.add_argument("--label", required=True)
    args = parser.parse_args(argv)
    try:
        if args.junit_root is not None:
            verify_junit(args.junit_root, args.label)
        if args.xcresult_summary is not None:
            verify_xcresult_summary(args.xcresult_summary, args.label)
        if args.junit_root is None and args.xcresult_summary is None:
            raise UiReportError("one report input is required")
    except (OSError, ET.ParseError, UiReportError, ValueError, json.JSONDecodeError) as error:
        print(f"ui-report: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
