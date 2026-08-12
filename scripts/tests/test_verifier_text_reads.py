"""Repository gates must not read text through the host locale.

`Path.read_text()` with no `encoding` decodes using
`locale.getpreferredencoding(False)`. On a host whose locale is not UTF-8, a
contract file containing any non-ASCII byte — a market name, a message string, a
comment — either raises UnicodeDecodeError or, worse, decodes to different
characters than the author wrote.

Either way the gate stops gating: it fails for a reason that has nothing to do
with the contract, or it compares text nobody wrote. Every verifier reads
contract files, so every verifier is exposed.

This covers the `verify-*.sh` gates and the shared library they call. The
negative-fixture scripts have the same pattern and are not covered here; they
run locally rather than as gates, and rewriting python embedded in their shell
heredocs is a larger change than this one.
"""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

# `.read_text()` / `.write_text(x)` with no encoding= anywhere in the call.
BARE_READ = re.compile(r"\.read_text\(\s*\)")
WRITE_WITHOUT_ENCODING = re.compile(r"\.write_text\((?![^()]*encoding=)[^()]*\)")


def gate_scripts() -> list[Path]:
    scripts = sorted((REPO_ROOT / "scripts").glob("verify-*.sh"))
    scripts += sorted((REPO_ROOT / "scripts" / "lib").glob("*.py"))
    return scripts


class VerifierTextReadTest(unittest.TestCase):
    def test_there_are_gates_to_check(self) -> None:
        # Guards against the suite passing because the glob found nothing.
        self.assertGreaterEqual(len(gate_scripts()), 5)

    def test_no_gate_reads_text_through_the_host_locale(self) -> None:
        offenders: list[str] = []
        for script in gate_scripts():
            for number, line in enumerate(
                script.read_text(encoding="utf-8").splitlines(), start=1
            ):
                if BARE_READ.search(line) or WRITE_WITHOUT_ENCODING.search(line):
                    offenders.append(f"{script.relative_to(REPO_ROOT)}:{number}: {line.strip()}")

        self.assertEqual(
            offenders,
            [],
            "these gates decode text using the host locale; pass encoding=\"utf-8\":\n"
            + "\n".join(offenders),
        )


if __name__ == "__main__":
    unittest.main()
