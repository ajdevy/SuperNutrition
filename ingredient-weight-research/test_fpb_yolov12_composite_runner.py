import unittest

from fpb_yolov12_composite_runner import RAW_LABEL_NAMESPACE, build_g3_predictions


class FpbYolov12CompositeRunnerTest(unittest.TestCase):
    def test_keeps_raw_labels_outside_the_supernutrition_taxonomy(self) -> None:
        predictions = build_g3_predictions(
            {
                "meal": {
                    "detections": [
                        {"label": "beef cutlet"},
                        {"label": "beef cutlet"},
                        {"label": "rice"},
                    ]
                }
            }
        )

        self.assertTrue(predictions["research_only"])
        self.assertEqual(
            predictions["cases"]["meal"],
            {
                "ingredients": [
                    f"{RAW_LABEL_NAMESPACE}beef_cutlet",
                    f"{RAW_LABEL_NAMESPACE}rice",
                ],
                "ingredient_masses_g": {},
            },
        )

    def test_rejects_unsafe_raw_label(self) -> None:
        with self.assertRaises(ValueError):
            build_g3_predictions({"meal": {"detections": [{"label": "tomato/sauce"}]}})


if __name__ == "__main__":
    unittest.main()
