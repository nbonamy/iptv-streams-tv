import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("numbered_lineup", Path(__file__).resolve().parents[1] / "import-numbered-lineup.py")
lineup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lineup)


class NumberedLineupTests(unittest.TestCase):
    def test_quality_duplicates_keep_the_lowest_number(self):
        metadata = [{"id": "ESPN.us", "name": "ESPN", "country": "US"}, {"id": "FX.us", "name": "FX", "country": "US"}]
        rows = [{"number": 173, "name": "ESPN HD"}, {"number": 24, "name": "FX"}, {"number": 3, "name": "ESPN"}]
        result = lineup.reconcile(rows, metadata, "US", {})
        self.assertEqual([3, 24], [item["number"] for item in result])
        self.assertEqual(["ESPN.us"], result[0]["ids"])
        self.assertIn("ESPN HD", result[0]["aliases"])

    def test_plus_channels_are_distinct(self):
        metadata = [{"id": "AMC.us", "name": "AMC", "country": "US"}, {"id": "AMCPlus.us", "name": "AMC+", "country": "US"}]
        result = lineup.reconcile([{"number": 43, "name": "AMC"}, {"number": 170, "name": "AMC+"}], metadata, "US", {})
        self.assertEqual([["AMC.us"], ["AMCPlus.us"]], [item["ids"] for item in result])

    def test_ambiguous_names_require_an_explicit_identity(self):
        metadata = [{"id": "one", "name": "News", "country": "US"}, {"id": "two", "name": "News", "country": "US"}]
        rows = [{"number": 5, "name": "News"}]
        self.assertEqual([], lineup.reconcile(rows, metadata, "US", {}))
        self.assertEqual(["two"], lineup.reconcile(rows, metadata, "US", {"News": ["two"]})[0]["ids"])

    def test_foreign_and_missing_ids_cannot_be_explicit_matches(self):
        metadata = [{"id": "one.ca", "name": "One", "country": "CA"}]
        for id_ in ("one.ca", "missing.us"):
            with self.assertRaises(ValueError):
                lineup.reconcile([], metadata, "US", {"One": [id_]})

    def test_quality_suffixes_do_not_remove_regions_or_subchannels(self):
        self.assertEqual("TLC", lineup.channel_name("TLC HD (US)"))
        self.assertEqual("ESPN", lineup.channel_name("ESPN (HD Feed)"))
        self.assertEqual("HBO (Pacific)", lineup.channel_name("HBO (Pacific)"))
        self.assertEqual("WCIU-DT2", lineup.channel_name("WCIU-DT2"))

    def test_uk_metadata_uses_the_app_canonical_gb_country_code(self):
        metadata = [{"id": "BBCOne.uk", "name": "BBC One", "country": "UK"}]
        result = lineup.reconcile([{"number": 1, "name": "BBC One"}], metadata, "GB", {})
        self.assertEqual(["BBCOne.uk"], result[0]["ids"])


if __name__ == "__main__":
    unittest.main()
