from __future__ import annotations

import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory


SCRIPT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_ROOT))

from update_readme_metrics import (  # noqa: E402
    END_MARKER,
    START_MARKER,
    ReadmeMetricsError,
    replace_section,
    render_section,
)


class UpdateReadmeMetricsTest(unittest.TestCase):
    def test_render_section_contains_coverage_and_loc_values(self) -> None:
        section = render_section(
            {"files": 12, "code_lines": 3456},
            {
                "commit": "abc1234",
                "host_jvm": {"line_percent": 92.94, "branch_percent": None},
                "reports": [
                    {
                        "id": "domain-nutrition",
                        "coverage_kind": "jvm",
                        "line_percent": 100.0,
                        "branch_percent": None,
                    }
                ],
            },
        )

        self.assertIn("92.94%", section)
        self.assertIn("3,456", section)
        self.assertIn("domain-nutrition", section)
        self.assertIn("n/a", section)

    def test_replace_section_preserves_surrounding_readme(self) -> None:
        readme = f"# Project\n\n{START_MARKER}\nold\n{END_MARKER}\n\n## Next\n"

        result = replace_section(readme, f"{START_MARKER}\nnew\n{END_MARKER}")

        self.assertEqual(result, f"# Project\n\n{START_MARKER}\nnew\n{END_MARKER}\n\n## Next\n")

    def test_replace_section_rejects_missing_or_duplicate_markers(self) -> None:
        with self.assertRaises(ReadmeMetricsError):
            replace_section("# Project\n", "section")
        with self.assertRaises(ReadmeMetricsError):
            replace_section(
                f"{START_MARKER}\n{END_MARKER}\n{START_MARKER}\n{END_MARKER}",
                "section",
            )


if __name__ == "__main__":
    unittest.main()
