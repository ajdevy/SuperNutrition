from __future__ import annotations

import json
import subprocess
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory


SCRIPT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_ROOT))

from build_coverage_site import (  # noqa: E402
    CoverageSiteError,
    count_text,
    git_commit,
    tracked_source_files,
    write_site,
)


class BuildCoverageSiteTest(unittest.TestCase):
    def test_count_text_separates_code_comments_and_blanks(self) -> None:
        stats = count_text("fun main() {}\n\n// comment\n", "Kotlin")

        self.assertEqual(stats.files, 1)
        self.assertEqual(stats.lines, 3)
        self.assertEqual(stats.code, 1)
        self.assertEqual(stats.comments, 1)
        self.assertEqual(stats.blanks, 1)

    def test_write_site_builds_dashboard_and_copies_html_report(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            self._init_git(root)
            (root / "mobile/core/config/src/main/kotlin/Foo.kt").parent.mkdir(parents=True)
            (root / "mobile/core/config/src/main/kotlin/Foo.kt").write_text(
                "fun foo() = 1\n", encoding="utf-8"
            )
            (root / "scripts/example.py").parent.mkdir(parents=True)
            (root / "scripts/example.py").write_text("print('ok')\n", encoding="utf-8")
            (root / "mobile/config/quality").mkdir(parents=True)
            (root / "mobile/config/quality/coverage.toml").write_text(
                '[[denominator]]\nid = "core-config"\n'
                'source_path = "mobile/core/config"\ncoverage_kind = "jvm"\n',
                encoding="utf-8",
            )
            report_root = root / "mobile/core/config/build/reports/kover"
            report_root.mkdir(parents=True)
            (report_root / "reportJvm.xml").write_text(
                '<report><counter type="LINE" missed="1" covered="9"/>'
                '<counter type="BRANCH" missed="0" covered="0"/></report>',
                encoding="utf-8",
            )
            (report_root / "htmlJvm").mkdir()
            (report_root / "htmlJvm/index.html").write_text("report", encoding="utf-8")
            subprocess.run(["git", "-C", str(root), "add", "."], check=True)
            subprocess.run(
                ["git", "-C", str(root), "commit", "--quiet", "-m", "fixture"],
                check=True,
            )

            output = root / "site"
            write_site(root, output)

            index = (output / "index.html").read_text(encoding="utf-8")
            loc = json.loads((output / "loc.json").read_text(encoding="utf-8"))
            coverage = json.loads((output / "coverage.json").read_text(encoding="utf-8"))
            self.assertIn("90.00%", index)
            self.assertIn("n/a", index)
            self.assertIn("Code lines", index)
            self.assertEqual(loc["code_lines"], 2)
            self.assertEqual(coverage["host_jvm"]["line_percent"], 90.0)
            self.assertIsNone(coverage["host_jvm"]["branch_percent"])
            self.assertTrue((output / "reports/core-config/index.html").is_file())

    def test_write_site_fails_when_a_report_is_missing(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            self._init_git(root)
            (root / "mobile/config/quality").mkdir(parents=True)
            (root / "mobile/config/quality/coverage.toml").write_text(
                '[[denominator]]\nid = "core-config"\n'
                'source_path = "mobile/core/config"\ncoverage_kind = "jvm"\n',
                encoding="utf-8",
            )
            with self.assertRaises(CoverageSiteError):
                write_site(root, root / "site")

    def test_docker_manifest_and_commit_overrides_work_without_git_metadata(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "mobile/core/config/src/main/kotlin/Foo.kt"
            source.parent.mkdir(parents=True)
            source.write_text("fun foo() = 1\n", encoding="utf-8")
            manifest = root / "source-files.txt"
            manifest.write_text("mobile/core/config/src/main/kotlin/Foo.kt\n", encoding="utf-8")

            self.assertEqual(
                tracked_source_files(root, manifest),
                [source.relative_to(root)],
            )
            self.assertEqual(git_commit(root, "docker-fixture"), "docker-fixture")

    def test_write_site_keeps_missing_native_equivalent_report_pending(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            self._init_git(root)
            (root / "mobile/config/quality").mkdir(parents=True)
            (root / "mobile/config/quality/coverage.toml").write_text(
                '[[denominator]]\nid = "platform-photo-ios"\n'
                'source_path = "mobile/platform/photo-ios"\n'
                'coverage_kind = "native-equivalent"\n',
                encoding="utf-8",
            )
            subprocess.run(["git", "-C", str(root), "add", "."], check=True)
            subprocess.run(
                ["git", "-C", str(root), "commit", "--quiet", "-m", "fixture"],
                check=True,
            )

            output = root / "site"
            write_site(root, output)

            index = (output / "index.html").read_text(encoding="utf-8")
            coverage = json.loads((output / "coverage.json").read_text(encoding="utf-8"))
            self.assertIn("Pending evidence", index)
            self.assertIn("n/a", index)
            self.assertIsNone(coverage["reports"][0]["line_percent"])
            self.assertIsNone(coverage["reports"][0]["html"])

    @staticmethod
    def _init_git(root: Path) -> None:
        subprocess.run(["git", "-C", str(root), "init", "--quiet"], check=True)
        subprocess.run(
            ["git", "-C", str(root), "config", "user.email", "test@example.invalid"],
            check=True,
        )
        subprocess.run(
            ["git", "-C", str(root), "config", "user.name", "Test"],
            check=True,
        )


if __name__ == "__main__":
    unittest.main()
