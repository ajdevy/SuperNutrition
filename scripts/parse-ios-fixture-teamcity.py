#!/usr/bin/env python3
"""Convert the physical Kotlin/Native TeamCity stream into JUnit XML."""

from __future__ import annotations

import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree


ATTRIBUTE = re.compile(r"([A-Za-z0-9_-]+)='((?:\|.|[^'])*)'")


def unescape(value: str) -> str:
    return (
        value.replace("||", "\0")
        .replace("|n", "\n")
        .replace("|r", "\r")
        .replace("|'", "'")
        .replace("|]", "]")
        .replace("|[", "[")
        .replace("\0", "|")
    )


def event(line: str) -> tuple[str, dict[str, str]] | None:
    if not line.startswith("##teamcity[") or not line.endswith("]"):
        return None
    payload = line[len("##teamcity[") : -1]
    name, _, attributes = payload.partition(" ")
    return name, {key: unescape(value) for key, value in ATTRIBUTE.findall(attributes)}


def write_suite(output_root: Path, suite_name: str, tests: list[dict[str, object]]) -> None:
    failures = sum(1 for test in tests if test["failure"])
    suite = ElementTree.Element(
        "testsuite",
        {
            "name": suite_name,
            "tests": str(len(tests)),
            "skipped": "0",
            "failures": str(failures),
            "errors": "0",
            "timestamp": datetime.now(timezone.utc).isoformat(),
        },
    )
    for test in tests:
        testcase = ElementTree.SubElement(
            suite,
            "testcase",
            {
                "name": str(test["name"]),
                "classname": suite_name,
                "time": f'{float(test["duration_ms"]) / 1000:.3f}',
            },
        )
        if test["failure"]:
            failure = ElementTree.SubElement(testcase, "failure")
            failure.text = str(test["failure"])
    tree = ElementTree.ElementTree(suite)
    ElementTree.indent(tree, space="  ")
    output = output_root / f"TEST-iosArm64Test.{suite_name}.xml"
    tree.write(output, encoding="utf-8", xml_declaration=True)


def main() -> int:
    if len(sys.argv) != 3:
        print(f"usage: {sys.argv[0]} <teamcity-log> <junit-output-directory>", file=sys.stderr)
        return 2

    log_path = Path(sys.argv[1])
    output_root = Path(sys.argv[2])
    output_root.mkdir(parents=True, exist_ok=True)
    suites: dict[str, list[dict[str, object]]] = {}
    active_suite = ""
    active_tests: dict[str, dict[str, object]] = {}

    for raw_line in log_path.read_text(encoding="utf-8", errors="replace").splitlines():
        parsed = event(raw_line.strip())
        if parsed is None:
            continue
        event_name, attributes = parsed
        if event_name == "testSuiteStarted":
            active_suite = attributes["name"]
            suites.setdefault(active_suite, [])
        elif event_name == "testStarted":
            test_name = attributes["name"]
            active_tests[test_name] = {
                "name": test_name,
                "duration_ms": 0,
                "failure": "",
                "suite": active_suite,
            }
        elif event_name == "testFailed":
            test_name = attributes["name"]
            test = active_tests.setdefault(
                test_name,
                {"name": test_name, "duration_ms": 0, "failure": "", "suite": active_suite},
            )
            test["failure"] = attributes.get("message", "physical Kotlin/Native test failed")
        elif event_name == "testFinished":
            test_name = attributes["name"]
            test = active_tests.pop(test_name, None)
            if test is None:
                continue
            test["duration_ms"] = int(attributes.get("duration", "0"))
            suites.setdefault(str(test["suite"]), []).append(test)

    for suite_name, tests in suites.items():
        write_suite(output_root, suite_name, tests)

    fixture_suite = "com.supernutrition.platform.photoios.IosImageNormalizerFixtureTest"
    fixture_tests = suites.get(fixture_suite, [])
    if len(fixture_tests) != 3 or any(test["failure"] for test in fixture_tests):
        print(
            f"physical iOS fixture stream is incomplete: tests={len(fixture_tests)} "
            f"failures={sum(1 for test in fixture_tests if test['failure'])}",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
