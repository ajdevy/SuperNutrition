import importlib.util
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("bundle_generator", Path(__file__).with_name("generate_bundled_catalog.py"))
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class BundledCatalogGeneratorTest(unittest.TestCase):
    def test_wrong_source_pin_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            GENERATOR.require_pin(b"tampered", GENERATOR.TSV_SHA256, "Imported TSV")

    def test_fixture_pin_can_be_verified_without_changing_cli_constants(self):
        GENERATOR.require_pin(b"fixture", GENERATOR.hashlib.sha256(b"fixture").hexdigest(), "fixture")

    def test_food_rows_keep_unknown_cells_and_ignore_household_portions(self):
        source = "@release\t2018-04\nF\tfood:usda-srl-01227\tCheese\t325\t24.51\t24.35\t2.04\t\t0.55\t1808\nP\t1\tcup\t100\n"
        self.assertEqual(source.splitlines()[1][2:] + "\n", GENERATOR.package_rows(source))

    def test_empty_malformed_and_duplicate_food_inventories_fail_closed(self):
        row = "F\tfood:usda-srl-01227\tCheese\t325\t24.51\t24.35\t2.04\t\t0.55\t1808\n"
        for source in ["# empty\n", "F\tmissing fields\n", row + row]:
            with self.assertRaises(ValueError):
                GENERATOR.package_rows(source)

    def test_kotlin_literals_escape_interpolation_quotes_and_unicode(self):
        self.assertEqual('"\\$5 \\"cheese\\" \\u00e9"', GENERATOR.kotlin_string('$5 "cheese" é'))

    def test_generation_is_deterministic_and_constant_chunks_are_bounded(self):
        body = ("food:usda-srl-01227\tCheese\t325\t24.51\t24.35\t2.04\t\t0.55\t1808\n" * 300)
        rendered = GENERATOR.render_kotlin(body)
        self.assertEqual(rendered, GENERATOR.render_kotlin(body))
        self.assertIn("USDA_PART_2", rendered)
        self.assertTrue(all(len(line) <= 120 for line in rendered.splitlines()))


if __name__ == "__main__":
    unittest.main()
