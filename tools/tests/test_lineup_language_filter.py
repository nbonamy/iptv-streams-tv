import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("language_filter", Path(__file__).resolve().parents[1] / "update-lineup-language-filter.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class LanguageFilterTests(unittest.TestCase):
    def test_only_known_spanish_only_channels_are_filtered(self):
        metadata = [{"id": name, "country": "US"} for name in ["spanish", "english", "bilingual", "unknown", "overridden"]]
        metadata.append({"id": "foreign", "country": "CA"})
        feeds = [{"channel": "spanish", "languages": ["spa"]},
                 {"channel": "english", "languages": ["eng"]},
                 {"channel": "bilingual", "languages": ["spa"]},
                 {"channel": "bilingual", "languages": ["eng"]},
                 {"channel": "overridden", "languages": ["eng"]},
                 {"channel": "foreign", "languages": ["spa"]}]
        self.assertEqual(["overridden", "spanish"], module.excluded_ids(metadata, feeds, "US", "spa", {"overridden": ["spa"]}))

    def test_overrides_cannot_filter_unknown_or_foreign_channels(self):
        metadata = [{"id": "foreign", "country": "CA"}]
        for id_ in ["foreign", "missing"]:
            with self.assertRaises(ValueError):
                module.excluded_ids(metadata, [], "US", "spa", {id_: ["spa"]})
