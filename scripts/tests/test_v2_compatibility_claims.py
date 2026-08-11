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
CONSUMER_EVIDENCE = {
    "local-android-v2": ("mobile/inference/local-android/src", "InferenceV2Engine"),
    "local-ios-v2": ("mobile/inference/local-ios/src", "InferenceV2Engine"),
    "remote-v2": ("mobile/inference/remote/src", "InferenceV2Engine"),
    "evaluation-v2": ("mobile/evaluation/harness/src", "InferenceV2Engine"),
    "text-v2": ("mobile", "InferenceInputModality.TEXT"),
    "local-text-v2": ("mobile", "InferenceInputModality.TEXT"),
}

MATRICES = (
    "contracts/inference/v2/compatibility-matrix.toml",
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


class V2CompatibilityClaims(unittest.TestCase):
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
        recorded = set()
        for relative in MATRICES:
            matrix = tomllib.loads((REPO_ROOT / relative).read_text(encoding="utf-8"))
            recorded.update(
                pair["consumer"]
                for pair in matrix.get("supported_pair", [])
                if pair.get("expected") == "not-yet-implemented"
            )
        for consumer in ("remote-v2", "evaluation-v2"):
            self.assertIn(consumer, recorded)


if __name__ == "__main__":
    unittest.main()
