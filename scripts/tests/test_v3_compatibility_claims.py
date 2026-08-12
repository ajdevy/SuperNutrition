"""P1-11 — a compatibility matrix may only claim consumers that exist.

The v2 matrices marked five consumers `accepted`. Two were real; the remote
path still spoke v1, the evaluation harness had no v2 reference at all, and no
TEXT modality existed anywhere in commonMain. Nothing checked, because a
compatibility matrix is data and the verifiers only compared it to a hardcoded
list that carried the same claim.

This ties the claim to the tree: `accepted` means an implementation is present.
A future consumer that is planned but unbuilt says `not-yet-implemented`, which
is what contracts/market-policy/v1 already does for its backend consumer.
"""

import pathlib
import tomllib
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]

# What each advertised consumer must look like in the tree for `accepted` to be
# a true statement. Deliberately a marker per consumer rather than "some file
# mentions v2": the point is that something IMPLEMENTS the release.
# Two matrices are checked, and their consumer ids name the release they speak:
# inference-evidence/v2 keeps the -v2 ids, inference-engine/v3 uses -v3. The
# evidence marker is the same either way — a consumer is real because the code
# exists, not because of what the row is called. InferenceV2Engine is the
# evidence engine base class and keeps its name; only the row ids moved.
_ENGINE_BASE = "InferenceV2Engine"
_TEXT_MARKER = "InferenceInputModality.TEXT"

CONSUMER_EVIDENCE = {
    "local-android-v2": ("mobile/inference/local-android/src", _ENGINE_BASE),
    "local-ios-v2": ("mobile/inference/local-ios/src", _ENGINE_BASE),
    "remote-v2": ("mobile/inference/remote/src", _ENGINE_BASE),
    "evaluation-v2": ("mobile/evaluation/harness/src", _ENGINE_BASE),
    "text-v2": ("mobile", _TEXT_MARKER),
    "local-text-v2": ("mobile", _TEXT_MARKER),
    "local-android-v3": ("mobile/inference/local-android/src", _ENGINE_BASE),
    "local-ios-v3": ("mobile/inference/local-ios/src", _ENGINE_BASE),
    "remote-v3": ("mobile/inference/remote/src", _ENGINE_BASE),
    "evaluation-v3": ("mobile/evaluation/harness/src", _ENGINE_BASE),
    "local-text-v3": ("mobile", _TEXT_MARKER),
}

MATRICES = (
    "contracts/inference/v3/compatibility-matrix.toml",
    "contracts/inference-evidence/v2/compatibility-matrix.toml",
)


def _implemented(consumer: str) -> bool:
    location, marker = CONSUMER_EVIDENCE[consumer]
    root = REPO_ROOT / location
    if not root.is_dir():
        return False
    for path in root.rglob("*.kt"):
        # Tests can mention an engine without anything shipping it.
        if "Test" in path.name or "/commonTest/" in path.as_posix():
            continue
        if marker in path.read_text(encoding="utf-8", errors="ignore"):
            return True
    return False


class CompatibilityClaims(unittest.TestCase):
    def test_accepted_consumers_are_implemented(self):
        for relative in MATRICES:
            matrix = tomllib.loads((REPO_ROOT / relative).read_text(encoding="utf-8"))
            for pair in matrix.get("supported_pair", []):
                if pair.get("expected") != "accepted":
                    continue
                if pair.get("direction") != "forward":
                    continue
                consumer = pair["consumer"]
                self.assertIn(
                    consumer,
                    CONSUMER_EVIDENCE,
                    f"{relative} accepts {consumer!r}, which this check does not know how "
                    "to look for. Add its evidence marker rather than dropping the check.",
                )
                self.assertTrue(
                    _implemented(consumer),
                    f"{relative} marks {consumer!r} accepted, but nothing in the tree "
                    "implements it. Mark it not-yet-implemented, or build it.",
                )

    def test_every_expected_value_is_one_of_the_three_states(self):
        allowed = {"accepted", "rejected", "not-yet-implemented"}
        for relative in MATRICES:
            matrix = tomllib.loads((REPO_ROOT / relative).read_text(encoding="utf-8"))
            for pair in matrix.get("supported_pair", []):
                self.assertIn(pair.get("expected"), allowed, f"{relative}: {pair}")

    def test_the_unbuilt_consumers_are_still_recorded(self):
        # Marking them not-yet-implemented must not become quietly deleting
        # them: a planned consumer that vanishes from the matrix is a migration
        # nobody can see.
        #
        # Checked per matrix, because the consumer ids name the release they
        # speak: the engine matrix moved from -v2 to -v3 when it was published
        # as inference/v3, while the evidence matrix keeps its -v2 ids. A flat
        # list of names across both would have to be edited on every such
        # rename, which is how the assertion stops meaning anything.
        expected = {
            "contracts/inference/v3/compatibility-matrix.toml":
                {"local-text-v3", "remote-v3", "evaluation-v3"},
            "contracts/inference-evidence/v2/compatibility-matrix.toml":
                {"text-v2", "evaluation-v2"},
        }
        for relative in MATRICES:
            matrix = tomllib.loads((REPO_ROOT / relative).read_text(encoding="utf-8"))
            recorded = {
                pair["consumer"]
                for pair in matrix.get("supported_pair", [])
                if pair.get("expected") == "not-yet-implemented"
            }
            self.assertEqual(recorded, expected[relative], relative)


if __name__ == "__main__":
    unittest.main()
