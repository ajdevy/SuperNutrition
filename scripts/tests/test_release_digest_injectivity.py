"""release-sha256-v3 must map one digest to exactly one release layout.

v2 does not. `path + NUL + content` is concatenated with no length framing, so
the boundary between one member's content and the next member's path can be
moved without changing the byte stream. These are the two layouts that
demonstrated it, kept as the regression: if v3 ever collides on them, the
framing has been lost.
"""

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))

import release_digest  # noqa: E402


def build(root: Path, members: dict[str, bytes]) -> Path:
    root.mkdir(parents=True, exist_ok=True)
    (root / "compatibility.toml").write_bytes(b'schema = "x"\n')
    for name, body in members.items():
        (root / name).write_bytes(body)
    return root


#: Streamed identically under v2: `a.toml\0xb.toml\0y` either way.
ONE_FILE = {"a.toml": b"xb.toml\x00y"}
TWO_FILES = {"a.toml": b"x", "b.toml": b"y"}


class ReleaseDigestInjectivity(unittest.TestCase):
    def setUp(self) -> None:
        self.work = Path(tempfile.mkdtemp())
        self.one = build(self.work / "one", ONE_FILE)
        self.two = build(self.work / "two", TWO_FILES)

    def test_v2_collides_which_is_why_it_is_retired(self) -> None:
        """Pinned deliberately. If this ever stops colliding, v2 was edited in
        place — and every recorded v2 digest would then be unreproducible."""
        self.assertEqual(
            release_digest.compute(self.one, "release-sha256-v2"),
            release_digest.compute(self.two, "release-sha256-v2"),
        )

    def test_v3_separates_the_two_layouts(self) -> None:
        self.assertNotEqual(
            release_digest.compute(self.one, "release-sha256-v3"),
            release_digest.compute(self.two, "release-sha256-v3"),
        )

    def test_v3_is_stable_for_one_layout(self) -> None:
        self.assertEqual(
            release_digest.compute(self.one, "release-sha256-v3"),
            release_digest.compute(self.one, "release-sha256-v3"),
        )

    def test_a_release_declaring_v3_is_computed_with_v3(self) -> None:
        declared = build(self.work / "declared", {"a.toml": b"x"})
        (declared / "compatibility.toml").write_bytes(
            b'schema = "x"\ndigest_algorithm = "release-sha256-v3"\n'
        )
        self.assertEqual(release_digest.declared_algorithm(declared), "release-sha256-v3")
        self.assertEqual(
            release_digest.compute(declared),
            release_digest.compute(declared, "release-sha256-v3"),
        )

    def test_a_release_declaring_nothing_stays_on_the_published_algorithm(self) -> None:
        legacy = build(self.work / "legacy", {"a.toml": b"x"})
        self.assertEqual(release_digest.declared_algorithm(legacy), "release-sha256-v2")
        self.assertEqual(
            release_digest.compute(legacy),
            release_digest.compute(legacy, "release-sha256-v2"),
        )

    def test_an_unknown_algorithm_is_refused_rather_than_defaulted(self) -> None:
        with self.assertRaises(ValueError):
            release_digest.compute(self.one, "release-sha256-v99")


if __name__ == "__main__":
    unittest.main()
