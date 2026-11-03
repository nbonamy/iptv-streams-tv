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

    def handle_starttag(self, tag, attrs):
        if tag == "table":
            if self.depth == 0:
                self.rows = []
            self.depth += 1
        if self.depth == 1:
            if tag == "tr":
                self.row = []
            if tag in ("td", "th"):
                self.cell = []

    def handle_data(self, data):
        if self.depth == 1 and self.cell is not None:
            self.cell.append(data)

    def handle_endtag(self, tag):
        if self.depth == 1:
            if tag in ("td", "th") and self.cell is not None:
                text = re.sub(r"\[[^\]]*\]", "", "".join(self.cell))
                self.row.append(" ".join(text.split()))
                self.cell = None
            if tag == "tr" and self.row:
                self.rows.append(self.row)
            if tag == "table":
                self.tables.append(self.rows)
        if tag == "table":
            self.depth -= 1


def normalized(value):
    return "".join(c for c in unicodedata.normalize("NFD", value).lower() if c.isalnum())


def main():
    args = argparse.ArgumentParser()
    args.add_argument("--country", required=True)
    args.add_argument("--url", required=True)
    args.add_argument("--number-column", default="EPG No.")
    args.add_argument("--name-column", default="Channel")
    args = args.parse_args()
    request = urllib.request.Request(args.url, headers={"User-Agent": "IPTV-channel-lineup/0.1"})
    with urllib.request.urlopen(request, timeout=30) as response:
        html = response.read().decode("utf-8")
    parser = Tables()
    parser.feed(html)
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
