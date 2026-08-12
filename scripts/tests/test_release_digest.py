"""release-sha256-v2 must hash every member of a release, or refuse to hash."""

import importlib.util
import os
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

_spec = importlib.util.spec_from_file_location(
    "release_digest", REPO_ROOT / "scripts" / "lib" / "release_digest.py"
)
release_digest = importlib.util.module_from_spec(_spec)
sys.modules["release_digest"] = release_digest
_spec.loader.exec_module(release_digest)


def seed_release(root: Path) -> None:
    """A minimal release: a compatibility matrix and one payload file."""
    root.mkdir(parents=True, exist_ok=True)
    (root / "compatibility-matrix.toml").write_text(
        'schema = "demo.v1"\ndigest = "<release-digest>"\n', encoding="utf-8"
    )
    (root / "schema.toml").write_text('schema = "demo.v1"\n', encoding="utf-8")


class ReleaseDigestSymlinkTest(unittest.TestCase):
    """A release member the digest cannot see is a member outside the gate.

    `release_files()` skipped anything `is_file()` reported false, which is every
    broken symlink. A broken symlink added to a published release therefore
    changed no digest at all, so `verify-release-immutability.sh` saw the
    release as untouched and the addition passed without acknowledgement — the
    exact case its A-rule exists to catch.

    A symlink to a regular FILE is a different case and is deliberately still
    hashed: its bytes are readable, so a change to them moves the digest and
    nothing is hidden. Whether a release may contain one is a separate rule that
    the contract verifiers enforce with a proper message; refusing it here would
    abort the digest helper those verifiers call to pin a release before they
    check it.
    """

    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name) / "v1"
        seed_release(self.root)

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def test_hashes_a_plain_release(self) -> None:
        self.assertRegex(release_digest.compute(self.root), r"^[a-f0-9]{64}$")

    def test_refuses_a_broken_symlink(self) -> None:
        (self.root / "dangling.toml").symlink_to("nowhere.toml")

        with self.assertRaises(ValueError) as raised:
            release_digest.compute(self.root)
        self.assertIn("symlink", str(raised.exception))
        self.assertIn("dangling.toml", str(raised.exception))

    def test_hashes_a_symlink_that_resolves_to_a_file(self) -> None:
        # Visible, so the digest moves. Whether it is ALLOWED is the contract
        # verifiers' call, and they reject it by name.
        before = release_digest.compute(self.root)
        (self.root / "alias.toml").symlink_to(self.root / "schema.toml")

        self.assertNotEqual(release_digest.compute(self.root), before)

    def test_refuses_a_symlinked_directory(self) -> None:
        # rglob does not descend into a symlinked directory, so its contents
        # were invisible to hashing even though they sit inside the release.
        (self.root / "fixtures").mkdir()
        (self.root / "fixtures" / "case.toml").write_text("a = 1\n", encoding="utf-8")
        (self.root / "linked").symlink_to(self.root / "fixtures", target_is_directory=True)

        with self.assertRaises(ValueError) as raised:
            release_digest.compute(self.root)
        self.assertIn("linked", str(raised.exception))

    def test_refuses_a_member_that_is_not_a_regular_file(self) -> None:
        if not hasattr(os, "mkfifo"):
            self.skipTest("platform has no FIFOs")
        os.mkfifo(self.root / "pipe")

        with self.assertRaises(ValueError) as raised:
            release_digest.compute(self.root)
        self.assertIn("pipe", str(raised.exception))

    def test_a_directory_is_still_skipped_silently(self) -> None:
        # Directories are not members. Only their contents are, and refusing on
        # them would make every nested release unhashable.
        (self.root / "fixtures").mkdir()
        (self.root / "fixtures" / "case.toml").write_text("a = 1\n", encoding="utf-8")

        self.assertRegex(release_digest.compute(self.root), r"^[a-f0-9]{64}$")


if __name__ == "__main__":
    unittest.main()
