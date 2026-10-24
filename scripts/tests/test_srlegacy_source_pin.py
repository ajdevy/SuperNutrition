"""Production import authentication; fixture helpers retain explicit synthetic inputs."""

import importlib.util
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

GENERATOR = Path(__file__).resolve().parents[1] / "generate-catalog-from-srlegacy.py"
SPEC = importlib.util.spec_from_file_location("srlegacy_generator", GENERATOR)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SrLegacySourcePinTest(unittest.TestCase):
    def test_wrong_archive_digest_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            archive = self.fixture_archive(Path(temporary))
            with self.assertRaisesRegex(MODULE.GeneratorError, "SHA-256 mismatch"):
                MODULE.load_tables(archive)

    def test_cli_rejects_wrong_archive_without_writing_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = self.fixture_archive(root)
            output = root / "catalog.tsv"
            result = subprocess.run(
                [sys.executable, str(GENERATOR), "--input", str(archive), "--output", str(output)],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(1, result.returncode)
            self.assertIn("SHA-256 mismatch", result.stderr)
            self.assertFalse(output.exists())

    def test_fixture_archive_must_explicitly_supply_its_own_digest(self):
        with tempfile.TemporaryDirectory() as temporary:
            archive = self.fixture_archive(Path(temporary))
            tables, digest = MODULE.load_tables(archive, expected_digest=MODULE.sha256_of(archive))
            self.assertEqual(set(MODULE.REQUIRED_TABLES), set(tables))
            self.assertEqual(MODULE.sha256_of(archive), digest)

    def test_unverified_directory_cannot_enter_production_cli(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(MODULE.GeneratorError, "pinned archive"):
                MODULE.load_tables(Path(temporary))

    def test_extracted_fixture_helper_requires_explicit_unverified_input(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name in MODULE.REQUIRED_TABLES:
                (root / name).write_text("placeholder\n", encoding="utf-8")
            tables, digest = MODULE.load_tables(root, expected_digest=None)
            self.assertEqual(set(MODULE.REQUIRED_TABLES), set(tables))
            self.assertEqual("", digest)

    @staticmethod
    def fixture_archive(root):
        archive = root / "synthetic.zip"
        with zipfile.ZipFile(archive, "w") as handle:
            for name in MODULE.REQUIRED_TABLES:
                handle.writestr(name, "placeholder\n")
        return archive


if __name__ == "__main__":
    unittest.main()
