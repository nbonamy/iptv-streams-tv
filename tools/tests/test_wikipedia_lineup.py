import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("wikipedia_lineup", Path(__file__).resolve().parents[1] / "update-country-order.py")
wiki = importlib.util.module_from_spec(spec)
spec.loader.exec_module(wiki)


class WikipediaLineupTests(unittest.TestCase):
    def parse(self, html):
        parser = wiki.Tables()
        parser.feed(html)
        return parser

    def test_england_baseline_selects_regional_numbers_and_primary_network(self):
        parser = self.parse("""<h3>General Entertainment</h3><table>
            <tr><th>NorDig LCN (SD)</th><th>DVB name</th></tr>
            <tr><td>3</td><td>ITV1 / STV</td></tr>
            <tr><td>4 (Eng, Scot &amp; NI)</td><td>Channel 4</td></tr>
            <tr><td>4 (Wales)</td><td>S4C</td></tr>
            <tr><td>7 (Scot)</td><td>BBC Alba</td></tr>
            <tr><td>66 (limited)</td><td>TBN UK</td></tr>
            <tr><td>103 (except CI)</td><td>ITV1 HD / STV HD</td></tr></table>""")
        result = wiki.capture_rows(parser, ["General Entertainment"], "Eng", True)
        self.assertEqual(["ITV1", "Channel 4", "TBN UK", "ITV1 HD"], [row["name"] for row in result])

    def test_removed_and_radio_sections_are_not_included(self):
        table = '<table><tr><th>NorDig LCN</th><th>DVB name</th></tr><tr><td>1</td><td>{}</td></tr></table>'
        parser = self.parse('<h3>News</h3>' + table.format('BBC News') + '<h3>Radio stations</h3>' +
                            table.format('Radio 1') + '<h3>2003</h3>' + table.format('Old Channel'))
        self.assertEqual(["BBC News"], [row["name"] for row in wiki.capture_rows(parser, ["News"])])

    def test_rowspan_keeps_channel_numbers_attached_to_the_correct_name(self):
        parser = self.parse("""<h4>Local</h4><table><tr><th>NorDig LCN</th><th>DVB name</th></tr>
            <tr><td rowspan="2">7</td><td>KMTV</td></tr>
            <tr><td>Latest TV</td></tr></table>""")
        result = wiki.capture_rows(parser, ["Local"])
        self.assertEqual([(7, "KMTV"), (7, "Latest TV")], [(row["number"], row["name"]) for row in result])

    def test_plus_one_is_not_lost_when_capturing_a_timeshift(self):
        parser = self.parse('<h3>General Entertainment</h3><table><tr><th>NorDig LCN</th><th>DVB name</th></tr>'
                            '<tr><td>15<sup>[1]</sup></td><td>Channel 4 +1</td></tr></table>')
        self.assertEqual("Channel 4 +1", wiki.capture_rows(parser, ["General Entertainment"])[0]["name"])


if __name__ == "__main__":
    unittest.main()
