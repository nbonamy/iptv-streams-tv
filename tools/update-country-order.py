#!/usr/bin/env python3
"""Import an explicitly numbered Wikipedia table into generic country-order data."""
import argparse
import datetime
import json
import re
import unicodedata
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


class Tables(HTMLParser):
    def __init__(self):
        super().__init__()
        self.tables = []
        self.depth = 0
        self.rows = []
        self.row = []
        self.cell = None
        self.cells = []
        self.spans = {}
        self.sections = []
        self.section = ""
        self.heading = None
        self.cell_span = (1, 1)

    def handle_starttag(self, tag, attrs):
        if tag in ("h2", "h3", "h4") and self.depth == 0:
            self.heading = []
        if tag == "table":
            if self.depth == 0:
                self.rows = []
                self.spans = {}
                self.sections.append(self.section)
            self.depth += 1
        if self.depth == 1:
            if tag == "tr":
                self.cells = []
            if tag in ("td", "th"):
                self.cell = []
                attrs = dict(attrs)
                self.cell_span = (int(attrs.get("rowspan", "1")), int(attrs.get("colspan", "1")))

    def handle_data(self, data):
        if self.heading is not None:
            self.heading.append(data)
        if self.depth == 1 and self.cell is not None:
            self.cell.append(data)

    def handle_endtag(self, tag):
        if tag in ("h2", "h3", "h4") and self.heading is not None:
            self.section = re.sub(r"\[[^\]]*\]", "", "".join(self.heading)).strip()
            self.heading = None
        if self.depth == 1:
            if tag in ("td", "th") and self.cell is not None:
                text = re.sub(r"\[[^\]]*\]", "", "".join(self.cell))
                self.cells.append((" ".join(text.split()), *self.cell_span))
                self.cell = None
            if tag == "tr" and self.cells:
                positions = {index: value for index, (value, _) in self.spans.items()}
                pending = {index: (value, remaining - 1) for index, (value, remaining) in self.spans.items() if remaining > 1}
                column = 0
                for value, rowspan, colspan in self.cells:
                    while column in positions:
                        column += 1
                    for index in range(column, column + colspan):
                        positions[index] = value
                        if rowspan > 1:
                            pending[index] = (value, rowspan - 1)
                    column += colspan
                self.rows.append([positions.get(index, "") for index in range(max(positions) + 1)])
                self.spans = pending
            if tag == "table":
                self.tables.append(self.rows)
        if tag == "table":
            self.depth -= 1


def normalized(value):
    return "".join(c for c in unicodedata.normalize("NFD", value).lower() if c.isalnum())


def capture_rows(parser, sections, region=None, primary_variant=False):
    rows = []
    for section, table in zip(parser.sections, parser.tables):
        if section not in sections or not table or len(table[0]) < 2:
            continue
        if "LCN" not in table[0][0] or table[0][1] != "DVB name":
            continue
        for row in table[1:]:
            if len(row) < 2:
                continue
            number = re.fullmatch(r"(\d+)(?:\s*\(([^)]*)\))?", row[0])
            if not number:
                continue
            qualifier = number.group(2) or ""
            regions = set(re.findall(r"\b(?:Eng|Scot|Wales|NI|CI|GrLdn|GrMcr)\b", qualifier))
            if region and regions:
                allowed = region not in regions if "except" in qualifier.lower() else region in regions
                if not allowed:
                    continue
            names = row[1].split(" / ")
            if primary_variant:
                names = names[:1]
            for name in names:
                entry = {"number": int(number.group(1)), "name": name, "section": section}
                if qualifier:
                    entry["scope"] = qualifier
                rows.append(entry)
    return rows


def main():
    args = argparse.ArgumentParser()
    args.add_argument("--country", required=True)
    args.add_argument("--url", required=True)
    args.add_argument("--number-column", default="EPG No.")
    args.add_argument("--name-column", default="Channel")
    args.add_argument("--capture", type=Path, help="Save current numbered rows for identity reconciliation instead of changing runtime rules")
    args.add_argument("--section", action="append", default=[])
    args.add_argument("--region", choices=["Eng", "Scot", "Wales", "NI"])
    args.add_argument("--primary-variant", action="store_true")
    args = args.parse_args()
    request = urllib.request.Request(args.url, headers={"User-Agent": "IPTV-channel-lineup/0.1"})
    with urllib.request.urlopen(request, timeout=30) as response:
        html = response.read().decode("utf-8")
    parser = Tables()
    parser.feed(html)
    if args.capture:
        if not args.section:
            raise ValueError("Select current sections explicitly; historical tables must not be imported")
        channels = capture_rows(parser, args.section, args.region, args.primary_variant)
        if not channels:
            raise ValueError("No numbered rows captured")
        revision = re.search(r"(?:oldid=|revision/|\"wgRevisionId\":)(\d+)", html)
        data = {"source": {"url": args.url, "revision": revision.group(1) if revision else None,
                "baseline": args.region or "all regions", "sections": args.section,
                "primaryVariant": args.primary_variant, "retrievedOn": datetime.date.today().isoformat()},
                "channels": channels}
        args.capture.parent.mkdir(parents=True, exist_ok=True)
        args.capture.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
        print(f"Captured {len(channels)} current numbered rows.")
        return
    table = next(rows for rows in parser.tables if rows and rows[0][:2] == [args.number_column, args.name_column])
    metadata = json.loads((ROOT / "app/src/main/assets/channels.json").read_text())
    country = args.country.upper()
    channels = []
    for row in table[1:]:
        if len(row) < 2 or not row[0].isdigit():
            continue  # Ranges and unnamed regional slots are not individual channels.
        name = row[1]
        ids = [item["id"] for item in metadata if item["country"] == country and normalized(item["name"]) == normalized(name)]
        channels.append({"number": int(row[0]), "name": name, "ids": ids})
    if not channels or len({c["number"] for c in channels}) != len(channels):
        raise ValueError("Expected a nonempty, uniquely numbered lineup")
    revision = re.search(r"(?:oldid=|revision/|\"wgRevisionId\":)(\d+)", html)
    path = ROOT / "app/src/main/assets/channel-order.json"
    data = json.loads(path.read_text()) if path.exists() else {"countries": {}}
    data["countries"][country] = {
        "source": {"url": args.url, "revision": revision.group(1) if revision else None,
                   "table": " / ".join(table[0][:2]), "retrievedOn": datetime.date.today().isoformat()},
        "channels": channels,
    }
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(f"Imported {len(channels)} numbered entries for {country}; {sum(bool(c['ids']) for c in channels)} matched provider metadata IDs.")


if __name__ == "__main__":
    main()
