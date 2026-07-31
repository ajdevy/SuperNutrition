from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory


SCRIPT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_ROOT))

from verify_ui_test_reports import UiReportError, junit_summary, verify_xcresult_summary  # noqa: E402


class VerifyUiTestReportsTest(unittest.TestCase):
    def test_junit_summary_counts_passing_tests(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "results.xml").write_text(
                "<testsuite><testcase name='one'/><testcase name='two'/></testsuite>",
                encoding="utf-8",
            )

            self.assertEqual(junit_summary(root), (2, 2, 0, 0))

    def test_junit_summary_rejects_missing_testcases(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "manifest.xml").write_text("<manifest />", encoding="utf-8")

            with self.assertRaises(UiReportError):
                junit_summary(root)

    def test_xcresult_summary_rejects_failed_or_empty_run(self) -> None:
        with TemporaryDirectory() as directory:
            path = Path(directory) / "summary.json"
            path.write_text(
                json.dumps(
                    {
                        "result": "Failed",
                        "totalTestCount": 2,
                        "passedTests": 1,
                        "failedTests": 1,
                        "skippedTests": 0,
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaises(UiReportError):
                verify_xcresult_summary(path, "XCTest")


if __name__ == "__main__":
    unittest.main()
