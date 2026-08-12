import csv
import json
import tempfile
import unittest
from pathlib import Path

from fpb_to_g3_predictions import convert


class FpbToG3PredictionsTest(unittest.TestCase):
    def test_converts_instances_and_aggregates_repeated_labels(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            predictions = root / "predictions.csv"
            with predictions.open("w", newline="", encoding="utf-8") as stream:
                writer = csv.DictWriter(
                    stream,
                    fieldnames=["image_name", "class_id", "xmin", "ymin", "xmax", "ymax", "weight", "confidence"],
                )
                writer.writeheader()
                writer.writerow({
                    "image_name": "plate-a.jpg",
                    "class_id": "1",
                    "xmin": "1",
                    "ymin": "2",
                    "xmax": "3",
                    "ymax": "4",
                    "weight": "120",
                    "confidence": "0.9",
                })
                writer.writerow({
                    "image_name": "plate-a.jpg",
                    "class_id": "1",
                    "xmin": "5",
                    "ymin": "6",
                    "xmax": "7",
                    "ymax": "8",
                    "weight": "30",
                    "confidence": "0.8",
                })

            label_map = root / "labels.json"
            label_map.write_text(json.dumps({"1": "chicken"}), encoding="utf-8")
            fixture_map = root / "fixtures.json"
            fixture_map.write_text(json.dumps({"plate-a.jpg": "steak-with-side"}), encoding="utf-8")

            result = convert(predictions, label_map, fixture_map)

        case = result["cases"]["steak-with-side"]
        self.assertEqual(case["ingredients"], ["chicken"])
        self.assertEqual(case["ingredient_masses_g"], {"chicken": 150.0})
        self.assertEqual(len(case["instances"]), 2)
        self.assertTrue(result["research_only"])


if __name__ == "__main__":
    unittest.main()
